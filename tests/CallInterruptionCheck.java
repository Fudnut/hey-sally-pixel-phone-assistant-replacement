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
        android.content.Context ownedContext = new android.content.Context();
        boolean[] owned = {true};
        java.util.ArrayList<Boolean> changes = new java.util.ArrayList<>();
        CallAudioMonitor ownMonitor = new CallAudioMonitor(ownedContext, changes::add, () -> owned[0]);
        ownMonitor.start(); ownedContext.audio.mode(AudioManager.MODE_IN_COMMUNICATION);
        check(!ownMonitor.isCallActive() && ownedContext.audio.keys.isEmpty(),
                "Sally's published communication lease must not look like an incoming call or pause music");
        ownedContext.telecom.busy = true;
        check(ownMonitor.isCallActive() && ownedContext.audio.keys.size() == 2,
                "A real Telecom call must preempt even while Sally owns communication mode");
        ownedContext.telecom.busy = false; ownMonitor.isCallActive(); owned[0] = false;
        check(ownMonitor.isCallActive(), "Clearing the lease must expose foreign communication mode immediately");
        ownMonitor.close();
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
        android.content.SharedPreferences.carInput = true;
        service = start(false, true);
        check(WakeMicrophone.lastCar && !WakeMicrophone.lastCommand && service.audio.keys.isEmpty(),
                "Car wake must use owned capture without mistaking its own mode for a phone call");
        int initialCarOpens=WakeMicrophone.opened;
        listen(service, true);
        check(WakeMicrophone.opened==initialCarOpens&&WakeMicrophone.closed==0,
                "Wake-to-command handover must retain the active car recorder so Android keeps its mode owner active");
        check(WakeMicrophone.lastCar && WakeMicrophone.lastCommand && SpeechRecognizer.starts == 0,
                "Car commands must keep caller-owned capture and never open a provider/default microphone");
        check(service.audio.focusRequests == 1 && service.audio.abandons == 0,
                "Car command capture must retain temporary music interruption");
        service.telecom.busy = true; service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        check(WakeMicrophone.opened == WakeMicrophone.closed && service.audio.keys.size() == 2,
                "Real incoming calls must still close owned car capture and pause media");
        service.onDestroy();
        DiagnosticHistory.events.clear();
        service = start(false, false);
        check(WakeMicrophone.opened == 0 && DiagnosticHistory.events.stream().noneMatch(x->x.contains("NullPointerException")),
                "Reentrant denied-permission cleanup must not dereference a removed car session");
        service.onDestroy();
        service = start(false, true); AndroidCarAudioSession.preserveForeignMode = true;
        invoke(service, "closeCarAudio", new Class<?>[]{});
        check(service.audio.keys.size() == 2 && WakeMicrophone.opened == WakeMicrophone.closed,
                "Releasing the lease must reconcile foreign communication mode and suspend capture");
        AndroidCarAudioSession.preserveForeignMode = false;
        service.onDestroy();
        service = start(false, true); listen(service,true);
        Handler.asyncPosts=true;
        service.audio.focus.listener.onAudioFocusChange(-2);
        service.telecom.busy=true;service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        Handler.advance(0);
        check(service.stopsSelf==0,"Focus loss before ringing must not queue a terminal stop that defeats hang-up recovery");
        service.telecom.busy=false;service.broadcast(TelephonyManager.EXTRA_STATE_IDLE);Handler.advance(1000);
        check(WakeMicrophone.opened==WakeMicrophone.closed+1,"Hang-up must recover car wake after focus-first interruption");
        service.onDestroy();Handler.asyncPosts=false;

        service=start(false,true);listen(service,true);WakeService dispatchService=service;
        SpotifyController.duringDispatch=()->dispatchService.audio.focus.listener.onAudioFocusChange(-1);
        command(service);SpotifyController.duringDispatch=null;
        check(service.stopsSelf==0&&service.audio.keys.isEmpty()&&SpotifyController.dispatched==1
                &&WakeMicrophone.opened==WakeMicrophone.closed+1&&service.audio.mode==3,
                "Intentional ordinary playback handoff must not terminate Sally before the player-result callback");
        service.onDestroy();

        service=start(false,true);Handler.asyncPosts=true;
        invoke(service,"fail",new Class<?>[]{Exception.class},new java.io.IOException("capture interrupted"));
        service.telecom.busy=true;service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);Handler.advance(0);
        check(service.stopsSelf==0,"A real call must invalidate queued capture failures from the previous generation");
        service.onDestroy();Handler.asyncPosts=false;

        service=start(false,true);Handler.asyncPosts=true;
        var carField=WakeService.class.getDeclaredField("carAudio");carField.setAccessible(true);
        ((AndroidCarAudioSession)carField.get(service)).session.interrupt("ROUTE_LOST");
        command(service);
        check(SpotifyController.dispatched==0,"An already-closed car route must reject queued commands before error cleanup runs");
        Handler.advance(0);service.onDestroy();Handler.asyncPosts=false;
        service=start(false,true);listen(service,true);
        WakeMicrophone.last.route.accept(false);
        check(WakeMicrophone.opened==WakeMicrophone.closed,
                "Physical route callbacks must remain valid after switching recognition generations");
        service.onDestroy();
        android.content.SharedPreferences.carInput = false;
        System.out.println("Call interruption: pause, cancellation, microphone suspension, recovery, dual-SIM and focus denial checks passed");
    }
}
