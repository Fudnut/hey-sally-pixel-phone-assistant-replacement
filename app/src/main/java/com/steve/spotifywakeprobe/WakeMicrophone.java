/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.android.RecognitionListener;
import java.io.IOException;
import java.util.List;

/** Wake-only capture with explicit Android input privacy; no audio is saved. */
final class WakeMicrophone implements AutoCloseable {
    private static final int SAMPLE_RATE = 16000;
    private static final int READ_SAMPLES = 3200;
    private final Context context;
    private final WakeDetector detector;
    private final AudioRecord recorder;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private Thread worker;
    private Boolean lastSilenced;
    private long resultAudioMs;
    private final AudioManager.AudioRecordingCallback recordingChanges = new AudioManager.AudioRecordingCallback() {
        @Override public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            if (closed) return;
            for (AudioRecordingConfiguration config : configs)
                if (config.getClientAudioSessionId() == recorder.getAudioSessionId()) reportCapture(config);
        }
    };

    WakeMicrophone(Context context, Model model) throws IOException {
        this.context = context.getApplicationContext();
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Microphone permission required");
        int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IOException("Wake microphone format unavailable");
        recorder = new AudioRecord.Builder().setContext(context)
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setPrivacySensitive(true)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                // Queue capture while a bounded candidate is verified on this worker.
                .setBufferSizeInBytes(Math.max(minimum, WakeDetector.MAX_SAMPLES * 2)).build();
        try {
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED)
                throw new IOException("Wake microphone initialization failed");
            recorder.registerAudioRecordingCallback(context.getMainExecutor(), recordingChanges);
            detector = VoskWakeDecoder.create(model);
        } catch (IOException | RuntimeException error) {
            try { recorder.unregisterAudioRecordingCallback(recordingChanges); }
            finally { recorder.release(); }
            throw error;
        }
    }

    void start(RecognitionListener listener, Runnable ready) {
        if (closed || worker != null) throw new IllegalStateException("Wake microphone already used");
        worker = new Thread(() -> {
            try {
                recorder.startRecording();
                if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
                    throw new IOException("Wake microphone did not start");
                main.post(() -> {
                    if (closed) return;
                    reportCapture(recorder.getActiveRecordingConfiguration());
                    DiagnosticHistory.record(context, "WAKE_CAPTURE_READY private=" + recorder.isPrivacySensitive());
                    ready.run();
                });
                short[] buffer = new short[READ_SAMPLES];
                long resultSamples = 0;
                while (!closed) {
                    int count = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                    if (closed) break;
                    if (count < 0) throw new IOException("Wake microphone read failed");
                    if (count > 0) resultSamples += count;
                    String words = count > 0 ? detector.accept(buffer, count) : null;
                    if (words != null) {
                        String result = new JSONObject().put("text", words).toString();
                        long audioMs = resultSamples * 1000 / SAMPLE_RATE;
                        resultSamples = 0;
                        main.post(() -> {
                            if (closed) return;
                            resultAudioMs = audioMs;
                            listener.onResult(result);
                        });
                    }
                }
            } catch (Exception error) {
                main.post(() -> { if (!closed) listener.onError(error); });
            } finally {
                try { recorder.stop(); } catch (IllegalStateException ignored) { }
            }
        }, "WakeMicrophone");
        worker.start();
    }

    // Captured audio since recording began or the preceding finalized result.
    long resultAudioMs() { return resultAudioMs; }

    private void reportCapture(AudioRecordingConfiguration config) {
        if (closed || config == null) return;
        boolean silenced = config.isClientSilenced();
        if (lastSilenced == null || lastSilenced != silenced) {
            lastSilenced = silenced;
            DiagnosticHistory.record(context, "WAKE_MIC_SILENCED " + silenced);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        recorder.unregisterAudioRecordingCallback(recordingChanges);
        // Stop unblocks reads; join before closing decoders or their shared model.
        try { recorder.stop(); } catch (IllegalStateException ignored) { }
        boolean interrupted = false;
        if (worker != null) while (worker.isAlive()) {
            try { worker.join(); } catch (InterruptedException ignored) { interrupted = true; }
        }
        try { detector.close(); }
        finally {
            recorder.release();
            if (interrupted) Thread.currentThread().interrupt();
        }
    }
}
