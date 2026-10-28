/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

import java.util.ArrayList;
import java.util.List;

public final class CarAudioProbeCheck {
    private static final class Device implements CarAudioProbe.Platform {
        boolean permission = true, call, routeAccepted = true, failCapture;
        int routes, captures, phrases, releases, deadlines, deadlineCancels;
        long elapsed, due;
        Runnable deadline;
        Runnable duringRequest;
        final List<String> events = new ArrayList<>();
        public boolean hasPermissions() { return permission; }
        public boolean hasActiveCall() { return call; }
        public void setDeadline(Runnable action, long delay) { deadlines++; deadline = action; due = elapsed + delay; }
        public void cancelDeadline() { deadlineCancels++; deadline = null; }
        public boolean requestCarRoute() { routes++; if (duringRequest != null) duringRequest.run(); return routeAccepted; }
        public void beginCapture() { captures++; if (failCapture) throw new SecurityException(); }
        public void speakTestPhrase() { phrases++; }
        public void releaseOwnedResources() { releases++; }
        public void report(String event) { events.add(event); }
        void advance(long millis) { elapsed += millis; if (deadline != null && elapsed >= due) { Runnable action = deadline; deadline = null; action.run(); } }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        Device device = new Device();
        CarAudioProbe probe = new CarAudioProbe(device);
        probe.start();
        probe.onRouteChanged(false);
        check(!device.events.contains("BLUETOOTH_INPUT_READY") && device.phrases == 0,
                "A preferred car route must not prove that capture actually uses Bluetooth");
        device.advance(29999);
        check(device.releases == 0, "Session ended before its 30-second deadline");
        probe.onRouteChanged(true);
        probe.onRouteChanged(true);
        check(device.events.contains("BLUETOOTH_INPUT_READY") && device.phrases == 1,
                "Actual Bluetooth recording must enable the test phrase once");
        device.advance(1);
        check(device.events.contains("TIME_LIMIT") && device.releases == 1 && device.deadlines == 1,
                "Route callbacks must not extend the original 30-second deadline");
        probe.onRouteChanged(true);
        probe.start();
        probe.stop();
        check(device.captures == 1 && device.phrases == 1 && device.releases == 1,
                "A stopped session and stale route callbacks must not reopen capture");

        device = new Device();
        probe = new CarAudioProbe(device);
        probe.start();
        probe.onRouteChanged(false);
        device.advance(30000);
        check(device.events.contains("CONNECTION_TIMEOUT") && !device.events.contains("TIME_LIMIT")
                        && device.releases == 1 && device.deadline == null,
                "A session that never confirms Bluetooth input must report a connection failure, not ordinary completion");

        for (String ending : List.of("stop", "call", "disconnect", "background", "permission", "error")) {
            device = new Device();
            probe = new CarAudioProbe(device);
            probe.start();
            probe.onRouteChanged(true);
            switch (ending) {
                case "stop": probe.stop(); break;
                case "call": probe.onCallChanged(true); break;
                case "disconnect": probe.onRouteChanged(false); break;
                case "background": probe.onBackgrounded(); break;
                case "permission": device.permission = false; probe.onRouteChanged(true); break;
                case "error": probe.onFailure(); break;
            }
            probe.onDeadline(); probe.stop(); probe.onRouteChanged(true);
            check(device.releases == 1 && device.deadline == null && device.phrases == 1,
                    "Session must clean up once and suppress stale work after " + ending);
        }
        device = new Device(); device.permission = false;
        probe = new CarAudioProbe(device); probe.start();
        check(device.routes == 0 && device.captures == 0 && device.events.contains("PERMISSION_REQUIRED"),
                "Permission denial must not request routing or capture");
        device = new Device(); device.call = true;
        probe = new CarAudioProbe(device); probe.start();
        check(device.routes == 0 && device.captures == 0 && device.events.contains("CALL_ACTIVE"),
                "An existing call must prevent diagnostic capture");
        device = new Device(); device.routeAccepted = false;
        probe = new CarAudioProbe(device); probe.start();
        check(device.captures == 0 && device.events.contains("NO_CAR_ROUTE") && device.releases == 1,
                "Missing car routing must fail explicitly without phone microphone fallback");
        device = new Device(); device.failCapture = true;
        probe = new CarAudioProbe(device); probe.start();
        check(device.events.contains("CAPTURE_ERROR") && device.releases == 1 && device.deadline == null,
                "A capture exception must release routing and cancel the timer");
        device = new Device();
        probe = new CarAudioProbe(device);
        CarAudioProbe interrupted = probe;
        device.duringRequest = () -> interrupted.onCallChanged(true);
        probe.start();
        check(device.captures == 0 && device.releases == 1,
                "A call arriving inside route setup must prevent subsequent capture");
        System.out.println("Car audio session: actual input, 30-second bound, preemption, permission and stale callback checks passed");
    }
}
