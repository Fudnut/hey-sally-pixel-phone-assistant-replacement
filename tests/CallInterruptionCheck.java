/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

import android.media.AudioManager;
import android.os.Handler;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import java.lang.reflect.Method;
import java.util.List;

public class CallInterruptionCheck {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static WakeService start(boolean busy, boolean permission) {
        Handler.timers.clear();
        WakeMicrophone.opened = WakeMicrophone.closed = 0;
        SpeechRecognizer.starts = 0;
        SpotifyController.dispatched = SpotifyController.cancelled = 0;
        WakeService service = new WakeService();
        service.phonePermission = permission;
        service.telecom.busy = busy;
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
        WakeService service = start(false, true);
        check(WakeMicrophone.opened == 1, "Idle service must listen normally");
        command(service);
        service.telecom.busy = true;
        service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        check(WakeMicrophone.opened == WakeMicrophone.closed,
                "Incoming call must release Sally's microphone immediately");
        check(SpotifyController.cancelled == 1, "Incoming call must cancel pending Spotify work");
        check(service.audio.keys.equals(List.of("0:127", "1:127")),
                "Incoming call must send a PAUSE key down/up, never a play/pause toggle");
        int opens = WakeMicrophone.opened;
        listen(service, false);
        listen(service, true);
        command(service);
        Handler.advance(20000);
        check(WakeMicrophone.opened == opens && SpeechRecognizer.starts == 0 && SpotifyController.dispatched == 1,
                "Retry, command capture and playback must stay blocked throughout a call");
        service.broadcast(TelephonyManager.EXTRA_STATE_OFFHOOK);
        service.broadcast(TelephonyManager.EXTRA_STATE_IDLE);
        check(WakeMicrophone.opened == opens && service.audio.keys.size() == 2,
                "Duplicate events or one SIM going idle must not interrupt another active call");
        service.telecom.busy = false;
        service.broadcast(TelephonyManager.EXTRA_STATE_IDLE);
        Handler.advance(1000);
        check(WakeMicrophone.opened == opens + 1, "Hang-up must restore wake listening");
        check(service.audio.keys.size() == 2 && SpotifyController.dispatched == 1,
                "Hang-up must not issue an explicit Spotify resume");
        service.onDestroy();
        check(service.receivers.isEmpty() && service.audio.listener == null, "Service stop must remove call observers");

        service = start(false, true);
        check((boolean) invoke(service, "beginReply", new Class<?>[]{int.class, String.class, boolean.class},
                0, "LIST_PLAYLISTS", false), "Playlist readout must acquire audio focus");
        invoke(service, "speakReply", new Class<?>[]{int.class, String.class, boolean.class}, 0, "A playlist", true);
        int stopped = TextToSpeech.stops;
        service.telecom.busy = true;
        service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        check(TextToSpeech.stops == stopped + 1 && service.audio.abandons == 1,
                "Incoming call must stop spoken replies and abandon assistant audio focus");
        check(!(boolean) invoke(service, "beginReply", new Class<?>[]{int.class, String.class, boolean.class},
                1, "RECOGNITION", true), "A delayed recognition error must not start a reply during a call");
        service.onDestroy();

        service = start(true, true);
        check(WakeMicrophone.opened == 0 && service.audio.keys.size() == 2,
                "Service recovery during a call must pause media without opening the microphone");
        service.onDestroy();

        service = start(false, true);
        service.telecom.probes.addAll(List.of(false, true));
        command(service);
        check(SpotifyController.dispatched == 0,
                "Call detected while restoring wake capture must cancel the command before Spotify dispatch");
        service.onDestroy();

        service = start(false, true);
        service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        listen(service, false);
        check(WakeMicrophone.opened == WakeMicrophone.closed,
                "A ringing broadcast must block capture even before Telecom catches up");
        service.telecom.busy = true;
        service.broadcast(TelephonyManager.EXTRA_STATE_IDLE);
        service.telecom.busy = false;
        Handler.advance(500);
        Handler.advance(400);
        check(WakeMicrophone.opened == WakeMicrophone.closed + 1,
                "Call-state reconciliation must recover if Telecom still reported busy on the idle broadcast");
        service.onDestroy();

        service = start(false, false);
        service.audio.mode(AudioManager.MODE_IN_COMMUNICATION);
        check(WakeMicrophone.opened == WakeMicrophone.closed && service.audio.keys.size() == 2,
                "Call audio mode must interrupt Sally even when Phone permission is denied");
        service.audio.mode(AudioManager.MODE_NORMAL);
        Handler.advance(1000);
        check(WakeMicrophone.opened == 2, "Returning to normal audio mode must restore listening");
        service.onDestroy();

        service = start(false, true);
        service.audio.focusResult = 0;
        listen(service, true);
        check(SpeechRecognizer.starts == 0, "Denied audio focus must not start command recognition");
        service.onDestroy();
        System.out.println("Call interruption: pause, cancellation, microphone suspension, recovery, dual-SIM and focus denial checks passed");
    }
}
