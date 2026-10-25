/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.telecom.TelecomManager;
import android.telephony.TelephonyManager;
import android.view.KeyEvent;
import java.util.function.Consumer;
import java.util.function.BooleanSupplier;

/** Call interruption only; never changes call routing, volume or explicitly resumes media. */
final class CallAudioMonitor implements AutoCloseable {
    private final Context context;
    private final AudioManager audio;
    private final Consumer<Boolean> changed;
    private final BooleanSupplier ownCommunicationMode;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean started;
    private boolean receiverRegistered;
    private boolean phoneBusy;
    private boolean active;
    private final AudioManager.OnModeChangedListener modeChanged = mode -> isCallActive();
    private final BroadcastReceiver phoneChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context ignored, Intent intent) {
            if (!started || !TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)
                    || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) phoneBusy = true;
            else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) phoneBusy = false;
            else return;
            isCallActive();
        }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!started) return;
            if (isCallActive()) main.postDelayed(this, 500);
        }
    };

    CallAudioMonitor(Context context, Consumer<Boolean> changed) {
        this(context, changed, () -> false);
    }

    CallAudioMonitor(Context context, Consumer<Boolean> changed, BooleanSupplier ownCommunicationMode) {
        this.context = context;
        this.changed = changed;
        this.ownCommunicationMode = ownCommunicationMode;
        audio = context.getSystemService(AudioManager.class);
    }

    void start() {
        if (!started) {
            started = true;
            audio.addOnModeChangedListener(context.getMainExecutor(), modeChanged);
        }
        if (!receiverRegistered && hasPhonePermission()) {
            // PHONE_STATE is a protected system broadcast, including calls on either SIM.
            context.registerReceiver(phoneChanged, new IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED),
                    Context.RECEIVER_EXPORTED);
            receiverRegistered = true;
        }
        isCallActive();
    }

    boolean isCallActive() {
        if (!started) return false;
        int mode = audio.getMode();
        boolean callMode = mode == AudioManager.MODE_RINGTONE || mode == AudioManager.MODE_IN_CALL
                || (mode == AudioManager.MODE_IN_COMMUNICATION && !ownCommunicationMode.getAsBoolean())
                || mode == AudioManager.MODE_CALL_SCREENING;
        if (!hasPhonePermission()) phoneBusy = false;
        boolean next = phoneBusy || telecomBusy() || callMode;
        if (next != active) {
            active = next;
            main.removeCallbacks(poll);
            // Cancel Sally's pending playback/microphone work before sending media pause.
            changed.accept(active);
            if (active) {
                try {
                    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
                    audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE));
                    DiagnosticHistory.record(context, "CALL_MEDIA_PAUSE_DISPATCHED");
                } catch (RuntimeException error) {
                    DiagnosticHistory.record(context, "CALL_MEDIA_PAUSE_ERROR");
                }
                main.postDelayed(poll, 500);
            }
        }
        return active;
    }

    private boolean hasPhonePermission() {
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean telecomBusy() {
        if (!hasPhonePermission()) return false;
        try {
            TelecomManager telecom = context.getSystemService(TelecomManager.class);
            // Includes ringing/held calls across subscriptions, and Telecom-integrated VoIP.
            return telecom != null && telecom.isInCall();
        } catch (SecurityException | UnsupportedOperationException error) {
            // Permission can be revoked while running; call audio mode still protects capture.
            return false;
        }
    }

    @Override public void close() {
        if (!started) return;
        started = false;
        main.removeCallbacks(poll);
        if (receiverRegistered) { context.unregisterReceiver(phoneChanged); receiverRegistered = false; }
        audio.removeOnModeChangedListener(modeChanged);
    }
}
