/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import java.util.Arrays;

/** Android boundary for the debug-only probe. No audio is saved or recognised. */
final class AndroidCarAudioProbe implements CarAudioProbe.Platform {
    interface View {
        void status(String category);
        void level(int percent);
        void released();
    }

    private final Context context;
    private final View view;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    final CarAudioProbe session;
    private AudioDeviceInfo target;
    private AudioRecord recorder;
    private TextToSpeech speech;
    private AudioFocusRequest focus;
    private boolean focusHeld;
    private boolean modeOwned;
    private boolean routeOwned;
    private boolean observing;
    private boolean phoneObserving;
    private boolean speaking;
    private boolean phoneBusy;
    private volatile boolean closed;
    private Runnable deadline;

    private final BroadcastReceiver phoneChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (closed || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)
                    || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                phoneBusy = true;
                session.onCallChanged(true);
            }
        }
    };
    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) {
            if (closed || target == null) return;
            for (AudioDeviceInfo device : removed) {
                if (device.getId() == target.getId()) { session.onFailure(); return; }
            }
        }
    };
    private final Runnable captureStart = this::openWhenSallyStopped;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (closed || !session.isActive()) return;
            if (!hasPermissions()) { session.onFailure(); return; }
            if (hasActiveCall()) { session.onCallChanged(true); return; }
            try {
                if (recorder != null && recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    var config = recorder.getActiveRecordingConfiguration();
                    if (config != null && config.isClientSilenced()) { session.onFailure(); return; }
                    session.onRouteChanged(isBluetooth(recorder.getRoutedDevice()));
                }
            } catch (RuntimeException error) { session.onFailure(); return; }
            if (!closed) main.postDelayed(this, 200);
        }
    };

    AndroidCarAudioProbe(Context context, View view) {
        this.context = context;
        this.view = view;
        audio = context.getSystemService(AudioManager.class);
        session = new CarAudioProbe(this);
    }

    @Override public boolean hasPermissions() {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public boolean hasActiveCall() {
        int mode = audio.getMode();
        if (phoneBusy || mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL
                || mode == AudioManager.MODE_CALL_SCREENING
                || (!modeOwned && mode == AudioManager.MODE_IN_COMMUNICATION)) return true;
        if (!hasPermissions()) return true;
        try {
            TelecomManager telecom = context.getSystemService(TelecomManager.class);
            // Without trustworthy call state, don't start a microphone diagnostic.
            return telecom == null || telecom.isInCall();
        } catch (SecurityException | UnsupportedOperationException error) { return true; }
    }

    @Override public void setDeadline(Runnable action, long delayMillis) {
        deadline = action;
        main.postDelayed(action, delayMillis);
    }

    @Override public void cancelDeadline() {
        if (deadline != null) { main.removeCallbacks(deadline); deadline = null; }
    }

    @Override public boolean requestCarRoute() {
        int candidates = 0;
        for (AudioDeviceInfo device : audio.getAvailableCommunicationDevices()) {
            if (isBluetooth(device)) { target = device; candidates++; }
        }
        // Never choose an arbitrary headset when several hands-free devices are connected.
        if (candidates != 1) return false;
        audio.registerAudioDeviceCallback(devices, main);
        observing = true;
        context.registerReceiver(phoneChanges, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                Context.RECEIVER_EXPORTED);
        phoneObserving = true;
        return true;
    }

    @Override public void beginCapture() {
        // Service destruction and release of its recorder are asynchronous. Keep this
        // wait inside the original session deadline; don't change mode while Sally owns capture.
        main.post(captureStart);
    }

    private void openWhenSallyStopped() {
        if (closed || !session.isActive()) return;
        if (!hasPermissions()) { session.onFailure(); return; }
        if (hasActiveCall()) { session.onCallChanged(true); return; }
        try {
            boolean ownCapture = audio.getActiveRecordingConfigurations().stream()
                    .anyMatch(config -> config.getClientAudioSessionId() > 0);
            if (isSallyRunning(context) || ownCapture) {
                main.postDelayed(captureStart, 100);
                return;
            }
            modeOwned = true;
            audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
            routeOwned = true;
            if (!audio.setCommunicationDevice(target)) { session.onFailure(); return; }
            if (hasActiveCall()) { session.onCallChanged(true); return; }
            int minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) { session.onFailure(); return; }
            if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                session.onFailure();
                return;
            }
            recorder = new AudioRecord.Builder().setContext(context)
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setPrivacySensitive(true)
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(16000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                    .setBufferSizeInBytes(Math.max(minimum, 6400)).build();
            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) { session.onFailure(); return; }
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) { session.onFailure(); return; }
            AudioRecord capture = recorder;
            new Thread(() -> measure(capture), "SallyCarAudioLevel").start();
            main.post(poll);
        } catch (RuntimeException error) { session.onFailure(); }
    }

    private void measure(AudioRecord capture) {
        short[] samples = new short[3200];
        try {
            while (!closed) {
                int count = capture.read(samples, 0, samples.length);
                if (count <= 0) { if (!closed) main.post(session::onFailure); return; }
                long squares = 0;
                for (int i = 0; i < count; i++) squares += (long) samples[i] * samples[i];
                int percent = Math.min(100, (int) Math.round(Math.sqrt((double) squares / count) * 100 / 32768));
                Arrays.fill(samples, (short) 0);
                main.post(() -> { if (!closed && session.isActive()) view.level(percent); });
            }
        } catch (RuntimeException error) { if (!closed) main.post(session::onFailure); }
        finally { Arrays.fill(samples, (short) 0); }
    }

    @Override public void speakTestPhrase() {
        if (closed || !session.isActive()) return;
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(speechAttributes())
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0 && !closed) session.onFailure();
                }, main).build();
        focusHeld = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!focusHeld) throw new IllegalStateException("Audio focus unavailable");
        speech = new TextToSpeech(context, status -> main.post(() -> {
            if (closed || !session.isActive()) return;
            if (status != TextToSpeech.SUCCESS || hasActiveCall()) { session.onFailure(); return; }
            try {
                speech.setAudioAttributes(speechAttributes());
                speech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { }
                    @Override public void onDone(String id) { main.post(() -> speechFinished(false)); }
                    @Override public void onError(String id) { main.post(() -> speechFinished(true)); }
                });
                speaking = true;
                if (speech.speak("Sally conversation check. Music should stay paused while I speak and while you reply. When I finish, speak normally toward the car microphone until the check ends.",
                        TextToSpeech.QUEUE_FLUSH, null, "car-check") == TextToSpeech.ERROR) speechFinished(true);
            } catch (RuntimeException error) { speechFinished(true); }
        }));
    }

    private void speechFinished(boolean error) {
        if (closed || !session.isActive() || !speaking) return;
        speaking = false;
        // Keep temporary focus through the user's reply capture. Session cleanup
        // releases it for every completion, interruption and failure path.
        if (error) session.onFailure();
        else view.status("SPEAKER_TEST_DONE");
    }

    private static AudioAttributes speechAttributes() {
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    private void abandonFocus() {
        if (focusHeld) {
            focusHeld = false;
            audio.abandonAudioFocusRequest(focus);
        }
    }

    @Override public void releaseOwnedResources() {
        if (closed) return;
        closed = true;
        main.removeCallbacks(captureStart);
        main.removeCallbacks(poll);
        if (recorder != null) {
            try { recorder.stop(); } catch (RuntimeException ignored) { }
            try { recorder.release(); } catch (RuntimeException ignored) { }
            recorder = null;
        }
        if (speech != null) {
            try { speech.stop(); speech.shutdown(); } catch (RuntimeException ignored) { }
            speech = null;
        }
        if (phoneObserving) {
            try { context.unregisterReceiver(phoneChanges); } catch (RuntimeException ignored) { }
            phoneObserving = false;
        }
        if (observing) {
            try { audio.unregisterAudioDeviceCallback(devices); } catch (RuntimeException ignored) { }
            observing = false;
        }
        // These APIs release this client's requests. Do not restore a saved device,
        // speakerphone flag or call mode over Telecom's higher-priority call owner.
        if (routeOwned) {
            try { audio.clearCommunicationDevice(); } catch (RuntimeException ignored) { }
            routeOwned = false;
        }
        if (modeOwned) {
            try { audio.setMode(AudioManager.MODE_NORMAL); } catch (RuntimeException ignored) { }
            modeOwned = false;
        }
        try { abandonFocus(); } catch (RuntimeException ignored) { }
        view.released();
    }

    @Override public void report(String category) { view.status(category); }

    private static boolean isBluetooth(AudioDeviceInfo device) {
        return device != null && (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || device.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET);
    }

    @SuppressWarnings("deprecation")
    static boolean isSallyRunning(Context context) {
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
            if (WakeService.class.getName().equals(service.service.getClassName())) return true;
        return false;
    }
}
