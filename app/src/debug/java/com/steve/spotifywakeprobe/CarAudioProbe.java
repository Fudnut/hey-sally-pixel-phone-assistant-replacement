/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

/** One foreground diagnostic session. All transitions run on the main thread. */
final class CarAudioProbe {
    interface Platform {
        boolean hasPermissions();
        boolean hasActiveCall();
        void setDeadline(Runnable action, long delayMillis);
        void cancelDeadline();
        boolean requestCarRoute();
        void beginCapture();
        void speakTestPhrase();
        void releaseOwnedResources();
        void report(String category);
    }

    private final Platform platform;
    private boolean started;
    private boolean ended;
    private boolean ready;

    CarAudioProbe(Platform platform) { this.platform = platform; }

    void start() {
        if (started || ended) return;
        started = true;
        if (!checkAccess()) return;
        platform.setDeadline(this::onDeadline, 30000);
        platform.report("CONNECTING");
        try {
            if (!platform.requestCarRoute()) { end("NO_CAR_ROUTE"); return; }
            if (!ended && checkAccess()) platform.beginCapture();
        } catch (RuntimeException error) { end("CAPTURE_ERROR"); }
    }

    void onRouteChanged(boolean bluetoothInput) {
        if (!started || ended || !checkAccess()) return;
        if (!bluetoothInput) {
            if (ready) end("ROUTE_LOST");
            return;
        }
        if (ready) return;
        ready = true;
        platform.report("BLUETOOTH_INPUT_READY");
        try { platform.speakTestPhrase(); }
        catch (RuntimeException error) { end("OUTPUT_ERROR"); }
    }

    void onCallChanged(boolean active) { if (active) end("CALL_ACTIVE"); }
    void onBackgrounded() { end("BACKGROUND"); }
    void onDeadline() { end(ready ? "TIME_LIMIT" : "CONNECTION_TIMEOUT"); }
    void onFailure() { end("CAPTURE_ERROR"); }
    void stop() { end("STOPPED"); }
    boolean isActive() { return started && !ended; }

    private boolean checkAccess() {
        if (!platform.hasPermissions()) { end("PERMISSION_REQUIRED"); return false; }
        if (platform.hasActiveCall()) { end("CALL_ACTIVE"); return false; }
        return true;
    }

    private void end(String reason) {
        if (ended) return;
        ended = true;
        platform.cancelDeadline();
        try { platform.releaseOwnedResources(); }
        finally { platform.report(reason); }
    }
}
