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
        // Car conversations (phone wake, then a verified car route) are checked in CarConversationCheck.
        service=start(false,true);Handler.asyncPosts=true;
        invoke(service,"fail",new Class<?>[]{Exception.class},new java.io.IOException("capture interrupted"));
        service.telecom.busy=true;service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);Handler.advance(0);
        check(service.stopsSelf==0,"A real call must invalidate queued capture failures from the previous generation");
        service.onDestroy();Handler.asyncPosts=false;
        // Spoken "Yes" before the system recognizer opens, so it never hears Sally.
        TextToSpeech.autoInit = true; TextToSpeech.autoSpeak = false; TextToSpeech.cues.clear();
        android.media.ToneGenerator.beeps = 0;
        service = start(false, true); Handler.advance(0);
        listen(service, true);
        check(TextToSpeech.cues.equals(List.of("Yes")) && SpeechRecognizer.starts == 0 && android.media.ToneGenerator.beeps == 0,
                "A phone command must say Yes before the recognizer opens");
        check(Float.valueOf(1.0f).equals(TextToSpeech.cueVolume) && TextToSpeech.cueUsage == android.media.AudioAttributes.USAGE_MEDIA,
                "The ready cue must use full TTS volume and the replies' usage");
        TextToSpeech.cueSpeaker.progress.onDone(TextToSpeech.cueId);
        check(SpeechRecognizer.starts == 1 && android.media.ToneGenerator.beeps == 0, "The recognizer must open once Yes is done");
        Handler.advance(2000);
        check(SpeechRecognizer.starts == 1 && android.media.ToneGenerator.beeps == 0, "A finished cue must not fall back later");
        service.onDestroy();

        service = start(false, true); Handler.advance(0);
        int ttsStops = TextToSpeech.stops;
        listen(service, true);
        Handler.advance(1499);
        check(SpeechRecognizer.starts == 0, "Listening must wait up to 1.5 seconds for the spoken cue");
        Handler.advance(1);
        check(android.media.ToneGenerator.beeps == 1 && SpeechRecognizer.starts == 1 && TextToSpeech.stops > ttsStops,
                "A slow cue must be stopped and replaced by the beep before listening");
        TextToSpeech.cueSpeaker.progress.onDone(TextToSpeech.cueId);
        check(SpeechRecognizer.starts == 1, "A late cue completion must not open a second recognizer");
        service.onDestroy();

        TextToSpeech.autoInit = false; android.media.ToneGenerator.beeps = 0;
        service = start(false, true);
        TextToSpeech.last.init.onInit(TextToSpeech.ERROR);
        listen(service, true);
        check(android.media.ToneGenerator.beeps == 1 && SpeechRecognizer.starts == 1, "Failed TTS must beep at once, then listen");
        service.onDestroy();

        TextToSpeech.autoInit = true; android.media.ToneGenerator.beeps = 0;
        service = start(false, true); Handler.advance(0);
        listen(service, true);
        service.telecom.busy = true; service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        TextToSpeech.cueSpeaker.progress.onDone(TextToSpeech.cueId);
        Handler.advance(2000);
        check(SpeechRecognizer.starts == 0 && android.media.ToneGenerator.beeps == 0,
                "A call during the cue must cancel it without opening a recognizer");
        service.onDestroy();

        android.content.pm.PackageManager.noRecognizer = true; org.vosk.android.SpeechService.starts = 0;
        TextToSpeech.cues.clear();
        service = start(false, true); Handler.advance(0);
        listen(service, true);
        check(org.vosk.android.SpeechService.starts == 0 && TextToSpeech.cues.size() == 1,
                "Local Vosk commands must also wait for the spoken cue");
        TextToSpeech.cueSpeaker.progress.onDone(TextToSpeech.cueId);
        check(org.vosk.android.SpeechService.starts == 1 && DiagnosticHistory.events.contains("COMMAND_READY VOSK"),
                "Local Vosk listening must start after Yes");
        service.onDestroy();
        android.content.pm.PackageManager.noRecognizer = false; TextToSpeech.autoInit = false;

        // Network categories at each heard command (no names or addresses).
        android.net.ConnectivityManager.active = new android.net.Network();
        android.net.NetworkCapabilities cellular = new android.net.NetworkCapabilities();
        cellular.transports.add(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);
        android.net.ConnectivityManager.capabilities = cellular;
        TelephonyManager.level = 2;
        DiagnosticHistory.events.clear();
        service = start(false, true);
        command(service);
        check(DiagnosticHistory.events.contains("COMMAND_HEARD RESUME net=cellular validated=false metered=true signal=2"),
                "COMMAND_HEARD must record transport, validation, metering and signal level");
        service.onDestroy();
        android.net.ConnectivityManager.active = null; TelephonyManager.level = -1; DiagnosticHistory.events.clear();
        service = start(false, true);
        command(service);
        check(DiagnosticHistory.events.contains("COMMAND_HEARD RESUME net=none signal=unknown"),
                "COMMAND_HEARD must say when there is no network");
        service.onDestroy();
        android.net.ConnectivityManager.capabilities = null;
        System.out.println("Call interruption: pause, cancellation, microphone suspension, recovery, dual-SIM and focus denial checks passed");
    }
}
