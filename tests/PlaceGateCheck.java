/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

import android.os.Handler;
import android.telephony.TelephonyManager;
import java.lang.reflect.Method;

/** Place-based listening: only capture on Wi-Fi or with Bluetooth audio connected. */
public class PlaceGateCheck {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static boolean listening() { return WakeMicrophone.opened > WakeMicrophone.closed; }
    private static WakeService start(boolean gate, boolean wifi, boolean bluetooth) {
        Handler.timers.clear();
        WakeMicrophone.opened = WakeMicrophone.closed = 0;
        SpotifyController.dispatched = SpotifyController.cancelled = 0;
        DiagnosticHistory.events.clear();
        PlaceMonitor.last = null;
        PlaceMonitor.wifi = wifi;
        PlaceMonitor.bluetooth = bluetooth;
        android.content.SharedPreferences.placeGate = gate;
        WakeService service = new WakeService();
        service.onStartCommand(null, 0, 1);
        return service;
    }
    private static Object invoke(WakeService service, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = WakeService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(service, args);
    }
    private static void listen(WakeService service, boolean command) throws Exception {
        invoke(service, "listen", new Class<?>[]{boolean.class}, command);
    }
    private static void command(WakeService service) throws Exception {
        invoke(service, "executeCommand", new Class<?>[]{VoiceCommand.class}, VoiceCommand.parse("resume"));
    }
    public static void main(String[] args) throws Exception {
        WakeService service = start(false, false, false);
        check(PlaceMonitor.last == null && listening(), "With the setting off, Sally must listen anywhere");
        service.onDestroy();

        service = start(true, false, false);
        check(PlaceMonitor.last != null && PlaceMonitor.last.started, "The setting must start place observation");
        check(!listening() && DiagnosticHistory.events.contains("PLACE_PAUSED"),
                "Away from Wi-Fi and Bluetooth audio, the microphone must stay closed from start-up");
        PlaceMonitor.last.set(true, false);
        check(listening() && DiagnosticHistory.events.contains("PLACE_RESUMED"),
                "Joining Wi-Fi must reopen wake listening at once");

        PlaceMonitor.last.set(false, false);
        Handler.advance(14000);
        check(listening(), "A short drop must not close the microphone before the grace period ends");
        Handler.advance(1000);
        check(!listening(), "Leaving Wi-Fi with no Bluetooth audio must close the microphone after the grace period");

        PlaceMonitor.last.set(false, true);
        check(listening(), "Bluetooth audio alone must reopen wake listening");
        PlaceMonitor.last.set(false, false);
        Handler.advance(5000);
        PlaceMonitor.last.set(true, false);
        Handler.advance(20000);
        check(listening() && WakeMicrophone.closed == 1,
                "Handing over from Bluetooth to Wi-Fi within the grace period must not restart capture");
        service.onDestroy();

        service = start(true, true, false);
        listen(service, true);
        PlaceMonitor.last.set(false, false);
        Handler.advance(16000);
        command(service);
        check(SpotifyController.dispatched == 1 && SpotifyController.cancelled == 0,
                "Leaving during a command must still let that command reach Spotify");
        check(!listening(), "After that command, Sally must pause instead of reopening the microphone");
        service.onDestroy();

        service = start(true, false, false);
        int opens = WakeMicrophone.opened;
        service.telecom.busy = true;
        service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        service.telecom.busy = false;
        service.broadcast(TelephonyManager.EXTRA_STATE_IDLE);
        Handler.advance(1000);
        check(WakeMicrophone.opened == opens, "Call recovery away from Wi-Fi and Bluetooth must not reopen capture");
        service.onDestroy();

        service = start(true, true, false);
        PlaceMonitor monitor = PlaceMonitor.last;
        monitor.set(false, false);
        service.onDestroy();
        int closes = WakeMicrophone.closed;
        Handler.advance(20000);
        check(monitor.closed && WakeMicrophone.closed == closes && Handler.timers.isEmpty(),
                "Service stop must close place observation and cancel a pending pause");

        service = start(true, false, false);
        monitor = PlaceMonitor.last;
        android.content.SharedPreferences.placeGate = false;
        service.onStartCommand(null, 0, 2);
        check(monitor.closed && listening(), "Turning the setting off while paused must resume listening");
        service.onDestroy();

        android.content.SharedPreferences.carInput = true;
        service = start(true, false, false);
        check(WakeMicrophone.lastCar && listening(), "Car-microphone mode must not be gated: it already needs the car");
        service.onDestroy();
        android.content.SharedPreferences.carInput = false;
        android.content.SharedPreferences.placeGate = false;
        System.out.println("Place gate: start-up pause, Wi-Fi/Bluetooth resume, grace period, handover, command completion, call recovery and stop checks passed");
    }
}
