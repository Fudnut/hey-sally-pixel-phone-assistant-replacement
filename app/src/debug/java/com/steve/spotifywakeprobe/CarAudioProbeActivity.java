/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

/** Explicit foreground-only diagnostic; never started by a wake or background intent. */
public final class CarAudioProbeActivity extends Activity implements AndroidCarAudioProbe.View {
    private TextView status;
    private TextView level;
    private TextView recovery;
    private ProgressBar meter;
    private Button start;
    private AndroidCarAudioProbe probe;
    private boolean foreground;
    private boolean restoreSally;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(32, 32, 32, 32);
        TextView instructions = new TextView(this);
        instructions.setTextSize(18);
        instructions.setText("Car conversation check\n\nPark before testing. Connect the car's Bluetooth hands-free audio, then start a 30-second check.\n\nSally's normal listener pauses during the check. No audio is saved, no speech is recognised and no calls are placed. Music should stay paused while Sally speaks and while you reply, then return when the check ends.\n");
        content.addView(instructions);
        status = new TextView(this);
        status.setTextSize(18);
        status.setText("Ready to check. Opening this screen does not start capture.");
        content.addView(status);
        meter = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        meter.setMax(100);
        content.addView(meter);
        level = new TextView(this);
        level.setText("Input level: stopped");
        content.addView(level);
        recovery = new TextView(this);
        content.addView(recovery);
        start = new Button(this);
        start.setText("Start 30-second check");
        start.setOnClickListener(view -> startCheck());
        content.addView(start);
        Button stop = new Button(this);
        stop.setText("Stop check");
        stop.setOnClickListener(view -> { if (probe != null) probe.session.stop(); });
        content.addView(stop);
        Button mainApp = new Button(this);
        mainApp.setText("Return to Sally");
        mainApp.setOnClickListener(view -> { startActivity(new Intent(this, MainActivity.class)); finish(); });
        content.addView(mainApp);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        restoreListener();
    }

    private void startCheck() {
        if (!foreground || (probe != null && probe.session.isActive())) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_PHONE_STATE}, 1);
            status.setText("Allow microphone and Phone permissions, then tap Start again. Phone access protects incoming calls during the check.");
            return;
        }
        restoreSally = AndroidCarAudioProbe.isSallyRunning(this);
        if (restoreSally) stopService(new Intent(this, WakeService.class));
        recovery.setText(restoreSally ? "Sally will restart when this check closes." : "Sally was stopped before this check.");
        start.setEnabled(false);
        meter.setProgress(0);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        probe = new AndroidCarAudioProbe(this, this);
        probe.session.start();
    }

    @Override protected void onPause() {
        // Close the probe and request restoration while the Activity is still foreground.
        if (probe != null) probe.session.onBackgrounded();
        restoreListener();
        foreground = false;
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (probe != null) probe.session.stop();
        super.onDestroy();
    }

    @Override public void status(String category) {
        status.setText(switch (category) {
            case "CONNECTING" -> "Opening the car audio route. Waiting for confirmed Bluetooth microphone input...";
            case "BLUETOOTH_INPUT_READY" -> "Bluetooth microphone input confirmed by Android. Listen for Sally's phrase through the car speakers, then speak normally. Music should stay paused until the check ends.";
            case "SPEAKER_TEST_DONE" -> "Your turn: speak normally toward the car microphone. Music should stay paused until the check ends.";
            case "NO_CAR_ROUTE" -> "No single Bluetooth hands-free route is available. Connect the car and disconnect other headsets, then try again.";
            case "CALL_ACTIVE" -> "Check stopped for a phone call or another communication session.";
            case "PERMISSION_REQUIRED" -> "Microphone and Phone permissions are required for this check.";
            case "TIME_LIMIT" -> "30-second conversation check finished. Capture and the test route are closed. Music should now return.";
            case "CONNECTION_TIMEOUT" -> "Bluetooth microphone input was never confirmed within 30 seconds. The check failed to connect; capture and the test route are closed.";
            case "ROUTE_LOST" -> "Bluetooth microphone route was lost. Check stopped.";
            case "BACKGROUND" -> "Check stopped because its screen left the foreground.";
            case "CAPTURE_ERROR", "OUTPUT_ERROR" -> "Audio check could not continue. Capture and the test route are closed.";
            default -> "Check stopped. Capture and the test route are closed.";
        });
    }

    @Override public void level(int percent) {
        meter.setProgress(percent);
        level.setText("Live input level: " + percent + "% RMS. Android's confirmed route is the source check.");
    }

    @Override public void released() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        meter.setProgress(0);
        level.setText("Input level: stopped");
        start.setEnabled(true);
        restoreListener();
    }

    private void restoreListener() {
        if (!restoreSally || !foreground) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recovery.setText("Sally needs microphone permission before listening can restart.");
            return;
        }
        try {
            // WakeService reconciles real call state before opening its microphone.
            startForegroundService(new Intent(this, WakeService.class));
            restoreSally = false;
            recovery.setText("Sally listening requested. Real calls continue to suspend its microphone.");
        } catch (RuntimeException error) {
            recovery.setText("Sally could not restart here. Return to Sally and tap Grant permissions and start probe.");
        }
    }
}
