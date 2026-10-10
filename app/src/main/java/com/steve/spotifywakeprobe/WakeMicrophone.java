/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ApplicationInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioRecordingConfiguration;
import android.media.MediaRecorder;
import android.media.audiofx.AudioEffect;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.android.RecognitionListener;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** Wake-only capture with explicit Android input privacy; no audio is saved. */
final class WakeMicrophone implements AutoCloseable {
    private static final int SAMPLE_RATE = 16000;
    private static final int READ_SAMPLES = 3200;
    private final Context context;
    private final WakeDetector detector;
    private final boolean debugCapture;
    private final boolean abProbe;
    private final AudioRecord recorder;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean closed;
    private Thread worker;
    private Boolean lastSilenced;
    private boolean inputReported;
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
        debugCapture = (this.context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw new SecurityException("Microphone permission required");
        int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IOException("Wake microphone format unavailable");
        // A/B experiment only: files/ab_source in a debuggable build selects the capture source.
        String abSource = debugCapture ? readAbSource(this.context) : null;
        abProbe = abSource != null;
        int source = "communication".equals(abSource)
                ? MediaRecorder.AudioSource.VOICE_COMMUNICATION : MediaRecorder.AudioSource.VOICE_RECOGNITION;
        recorder = new AudioRecord.Builder().setContext(context)
                .setAudioSource(source)
                .setPrivacySensitive(true)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(minimum, READ_SAMPLES * 2)).build();
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
                    DiagnosticHistory.record(context, "WAKE_CAPTURE_READY private=" + recorder.isPrivacySensitive()
                            + (abProbe ? " ab=" + (recorder.getAudioSource() == MediaRecorder.AudioSource.VOICE_COMMUNICATION
                                    ? "communication" : "recognition") + effects(recorder.getActiveRecordingConfiguration()) : ""));
                    ready.run();
                });
                short[] buffer = new short[READ_SAMPLES];
                long resultSamples = 0, levelSamples = 0, levelSquares = 0;
                int levelPeak = 0, levelZeros = 0, levelReports = 0;
                long abSquares = 0, abSamples = 0;
                int abPeak = 0;
                // Three 30 s probes normally; ten-second probes for the whole A/B run.
                int maxReports = abProbe ? 1000 : 3;
                long levelWindow = SAMPLE_RATE * (abProbe ? 10L : 30L);
                while (!closed) {
                    int count = recorder.read(buffer, 0, buffer.length, AudioRecord.READ_BLOCKING);
                    if (closed) break;
                    if (count < 0) throw new IOException("Wake microphone read failed");
                    if (count > 0) resultSamples += count;
                    if (abProbe) for (int i = 0; i < count; i++) {
                        int value = buffer[i];
                        abSquares += (long) value * value;
                        abPeak = Math.max(abPeak, Math.abs(value));
                        abSamples++;
                    }
                    String words = count > 0 ? detector.accept(buffer, count) : null;
                    if (abProbe && words != null) {
                        // Category and level only, never the recognized words.
                        String kind = words.isEmpty() ? "empty" : WakePhrase.matches(words) ? "wake" : "other";
                        String line = "AB_RESULT kind=" + kind + " audioMs=" + abSamples * 1000 / SAMPLE_RATE
                                + " rms=" + Math.round(Math.sqrt(abSquares / (double) Math.max(1, abSamples)))
                                + " peak=" + abPeak;
                        abSquares = 0; abSamples = 0; abPeak = 0;
                        main.post(() -> { if (!closed) DiagnosticHistory.record(context, line); });
                    }
                    if (debugCapture && levelReports < maxReports && count > 0) {
                        for (int i = 0; i < count; i++) {
                            int value = buffer[i];
                            levelSquares += (long) value * value;
                            levelPeak = Math.max(levelPeak, Math.abs(value));
                            if (value == 0) levelZeros++;
                        }
                        levelSamples += count;
                        if (levelSamples >= levelWindow) {
                            String probe = "WAKE_INPUT_LEVEL samples=" + levelSamples
                                    + " rms=" + Math.round(Math.sqrt(levelSquares / (double) levelSamples))
                                    + " peak=" + levelPeak + " zeros=" + levelZeros
                                    + " finals=" + detector.candidateFinals + " candidateFlags=" + detector.candidateFlags;
                            main.post(() -> { if (!closed) DiagnosticHistory.record(context, probe); });
                            levelReports++; levelSamples = 0; levelSquares = 0; levelPeak = 0; levelZeros = 0;
                        }
                    }
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

    private static String readAbSource(Context context) {
        try {
            return new String(Files.readAllBytes(new File(context.getFilesDir(), "ab_source").toPath()),
                    StandardCharsets.UTF_8).trim();
        } catch (IOException | RuntimeException missing) { return null; }
    }

    // Platform pre-processing the audio framework reports as active on this capture.
    private static String effects(AudioRecordingConfiguration config) {
        boolean aec = false, ns = false, agc = false;
        if (config != null) for (AudioEffect.Descriptor effect : config.getEffects()) {
            aec |= AudioEffect.EFFECT_TYPE_AEC.equals(effect.type);
            ns |= AudioEffect.EFFECT_TYPE_NS.equals(effect.type);
            agc |= AudioEffect.EFFECT_TYPE_AGC.equals(effect.type);
        }
        return " aec=" + aec + " ns=" + ns + " agc=" + agc;
    }

    // Captured audio since recording began or the preceding finalized result.
    long resultAudioMs() { return resultAudioMs; }

    private void reportCapture(AudioRecordingConfiguration config) {
        if (closed || config == null) return;
        if (debugCapture && !inputReported) {
            inputReported = true;
            int type = config.getAudioDevice() == null ? -1 : config.getAudioDevice().getType();
            DiagnosticHistory.record(context, "WAKE_INPUT_CONFIG type=" + type
                    + " source=" + config.getClientAudioSource() + " clientRate=" + config.getClientFormat().getSampleRate()
                    + " deviceRate=" + config.getFormat().getSampleRate());
        }
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
