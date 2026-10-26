/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** Car conversations: phone-microphone wake, then one verified car recorder per conversation. */
public class CarConversationCheck {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static WakeService start(boolean permission) {
        Handler.timers.clear();
        Handler.asyncPosts = false;
        WakeMicrophone.opened = WakeMicrophone.closed = 0;
        WakeMicrophone.last = null;
        WakeMicrophone.holdBluetooth = true;
        WakeMicrophone.trace.clear();
        SpeechRecognizer.starts = 0;
        SpotifyController.dispatched = SpotifyController.cancelled = 0;
        SpotifyController.lastReport = null;
        SpotifyController.duringDispatch = null;
        ToneGenerator.beeps = 0;
        TextToSpeech.created = 0;
        AndroidCarAudioSession.requests = 0;
        AndroidCarAudioSession.noRoute = false;
        AndroidCarAudioSession.preserveForeignMode = false;
        AndroidCarAudioSession.last = null;
        DiagnosticHistory.events.clear();
        android.content.SharedPreferences.carInput = true;
        WakeService service = new WakeService();
        service.phonePermission = permission;
        service.onStartCommand(null, 0, 1);
        return service;
    }
    private static Object invoke(WakeService service, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = WakeService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(service, args);
    }
    private static Object field(WakeService service, String name) throws Exception {
        Field field = WakeService.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(service);
    }
    private static void command(WakeService service) throws Exception {
        invoke(service, "executeCommand", new Class<?>[]{VoiceCommand.class}, VoiceCommand.parse("next"));
    }
    private static void say(WakeMicrophone microphone, String words) {
        microphone.listener.onResult("{\"text\": \"" + words + "\"}");
    }
    /** Say Hey Sally to the open phone recorder; returns the recorder opened for the car conversation. */
    private static WakeMicrophone wake(WakeService service) throws Exception {
        Field debounce = WakeService.class.getDeclaredField("lastWake");
        debounce.setAccessible(true);
        debounce.setLong(service, 0);
        WakeMicrophone phone = WakeMicrophone.last;
        check(phoneWake(), "Hey Sally must be heard by the phone wake recorder");
        say(phone, "hey sally");
        return WakeMicrophone.last;
    }
    private static boolean phoneWake() {
        WakeMicrophone microphone = WakeMicrophone.last;
        return microphone != null && !microphone.car && !microphone.command && !microphone.isClosed
                && microphone.listener != null && WakeMicrophone.opened == WakeMicrophone.closed + 1;
    }
    private static boolean focusFree(WakeService service) throws Exception {
        return !(boolean) field(service, "focusRequested");
    }
    private static boolean stereo(WakeService service) throws Exception {
        return service.audio.mode == AudioManager.MODE_NORMAL && field(service, "carAudio") == null;
    }

    public static void main(String[] args) throws Exception {
        WakeService service = start(true);
        check(phoneWake() && AndroidCarAudioSession.requests == 0 && service.audio.mode == AudioManager.MODE_NORMAL
                        && service.audio.focusRequests == 0,
                "Idle car-conversation mode must wait on the phone microphone with no hands-free route request");

        WakeMicrophone phone = WakeMicrophone.last;
        WakeMicrophone.trace.clear();
        WakeMicrophone car = wake(service);
        List<String> trace = WakeMicrophone.trace;
        check(phone.isClosed && trace.indexOf("close phone") >= 0
                        && trace.indexOf("close phone") < trace.indexOf("route request"),
                "Accepted Hey Sally must fully close the phone recorder before requesting the car route");
        check(car != phone && car.car && car.command && !car.isClosed && service.audio.mode == AudioManager.MODE_IN_COMMUNICATION,
                "The car route must be requested and one required-Bluetooth command recorder opened");
        check(service.audio.focusRequests == 1 && !focusFree(service),
                "Music must stay paused through the route transition");
        check(ToneGenerator.beeps == 0 && !DiagnosticHistory.events.contains("COMMAND_READY CAR_LOCAL"),
                "No ready beep before the car recorder delivers actual Bluetooth input");
        say(car, "next");
        command(service);
        check(SpotifyController.dispatched == 0 && SpeechRecognizer.starts == 0,
                "No command may be accepted, or taken from another microphone, before actual Bluetooth input");

        car.input(true);
        check(ToneGenerator.beeps == 1 && DiagnosticHistory.events.contains("COMMAND_READY CAR_LOCAL"),
                "Verified Bluetooth input must give one ready beep");
        WakeMicrophone.trace.clear();
        say(car, "next");
        check(SpotifyController.dispatched == 1 && SpotifyController.cancelled == 0,
                "Graceful car release after a command must not cancel the pending Spotify request");
        check(car.isClosed && stereo(service) && phoneWake() && focusFree(service),
                "After the command, car capture and route must be released and phone wake restored");
        trace = WakeMicrophone.trace;
        check(trace.indexOf("close car") < trace.indexOf("route release")
                        && trace.indexOf("route release") < trace.indexOf("open phone")
                        && trace.indexOf("open phone") < trace.indexOf("dispatch"),
                "Close car capture, then release the route, then reopen phone wake, before Spotify resumes");

        int speech = TextToSpeech.created;
        SpotifyController.lastReport.accept(CommandFailure.report(CommandFailure.Reason.REMOTE));
        check(TextToSpeech.created == speech && AndroidCarAudioSession.requests == 1 && phoneWake(),
                "A late Spotify failure after release must not speak through the handset or reopen the route");
        check(DiagnosticHistory.events.contains("COMMAND_RESULT ERROR reason=REMOTE"),
                "A late Spotify failure must still be recorded");

        var oldFocus = service.audio.focus.listener;
        WakeMicrophone oldCar = car;
        car = wake(service);
        car.input(true);
        oldFocus.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        oldCar.input(false);
        check(!car.isClosed && service.audio.mode == AudioManager.MODE_IN_COMMUNICATION && AndroidCarAudioSession.requests == 2,
                "Focus and route callbacks from an earlier conversation must not end the current one");

        int opens = WakeMicrophone.opened;
        car.listener.onTimeout();
        check(!car.isClosed && service.audio.mode == AudioManager.MODE_IN_COMMUNICATION && TextToSpeech.created == speech + 1,
                "A spoken prompt must use the car route while retaining the same car recorder");
        TextToSpeech.last.init.onInit(TextToSpeech.SUCCESS);
        TextToSpeech.last.progress.onDone("reply");
        check(!car.isClosed && WakeMicrophone.opened == opens, "The reply's drain gap must keep the car recorder");
        check(!focusFree(service), "Music must stay paused until the car route is released, so it resumes in stereo");
        Handler.advance(400);
        check(car.isClosed && stereo(service) && phoneWake() && focusFree(service),
                "After the reply, the conversation must end and phone wake resume");

        int beeps = ToneGenerator.beeps;
        car = wake(service);
        Handler.advance(9999);
        check(!car.isClosed, "The car route has 10 seconds to deliver input");
        Handler.advance(1);
        check(car.isClosed && stereo(service) && ToneGenerator.beeps == beeps && SpeechRecognizer.starts == 0
                        && focusFree(service) && WakeMicrophone.opened == WakeMicrophone.closed,
                "Connection timeout must end the conversation with no beep and no phone-microphone command");
        Handler.advance(1000);
        check(phoneWake() && service.stopsSelf == 0, "Connection timeout must recover to phone wake");

        car = wake(service);
        car.input(true);
        Handler.advance(89999);
        check(!car.isClosed, "A conversation may last 90 seconds");
        Handler.advance(1);
        check(car.isClosed && stereo(service) && focusFree(service), "The 90-second bound must end the conversation");
        Handler.advance(1000);
        check(phoneWake() && service.stopsSelf == 0, "The conversation bound must recover to phone wake");

        int dispatched = SpotifyController.dispatched;
        car = wake(service);
        car.input(true);
        car.input(false);
        say(car, "next");
        check(car.isClosed && stereo(service) && SpotifyController.dispatched == dispatched,
                "Car route loss must close capture and reject later results");
        Handler.advance(1000);
        check(phoneWake() && service.stopsSelf == 0, "Route loss must recover to phone wake");

        car = wake(service);
        car.input(true);
        service.audio.focus.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        check(car.isClosed && stereo(service), "Focus loss must end the car conversation");
        Handler.advance(1000);
        check(phoneWake() && service.stopsSelf == 0, "Focus loss must recover to phone wake");

        car = wake(service);
        car.input(true);
        service.telecom.busy = true;
        service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        check(car.isClosed && stereo(service) && WakeMicrophone.opened == WakeMicrophone.closed
                        && service.audio.keys.equals(List.of("0:127", "1:127")),
                "A real call must close car capture, release the route and pause media");
        service.telecom.busy = false;
        service.broadcast(TelephonyManager.EXTRA_STATE_IDLE);
        Handler.advance(1000);
        check(phoneWake() && service.audio.keys.size() == 2 && SpotifyController.dispatched == dispatched,
                "Hang-up must return to phone wake without explicitly resuming Spotify");

        int requests = AndroidCarAudioSession.requests;
        service.audio.focusResult = 0;
        wake(service);
        check(AndroidCarAudioSession.requests == requests && SpeechRecognizer.starts == 0 && stereo(service),
                "Denied music focus must not request the car route or another microphone");
        service.audio.focusResult = 1;
        Handler.advance(400);
        check(phoneWake(), "Denied focus must return to phone wake");

        AndroidCarAudioSession.noRoute = true;
        opens = WakeMicrophone.opened;
        wake(service);
        check(WakeMicrophone.opened == opens && SpeechRecognizer.starts == 0 && stereo(service) && focusFree(service),
                "An unavailable car route must not fall back to a phone-microphone command");
        Handler.advance(1000);
        check(phoneWake() && service.stopsSelf == 0, "An unavailable car route must recover to phone wake");
        AndroidCarAudioSession.noRoute = false;

        car = wake(service);
        car.input(true);
        car.listener.onError(new java.io.IOException("Car microphone silenced"));
        check(car.isClosed && stereo(service), "A failed car recorder must release the route");
        Handler.advance(2000);
        check(phoneWake() && service.stopsSelf == 0, "A failed car recorder must recover to phone wake");

        car = wake(service);
        car.input(true);
        WakeService dispatchService = service;
        dispatched = SpotifyController.dispatched;
        SpotifyController.duringDispatch = () -> dispatchService.audio.focus.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS);
        say(car, "next");
        SpotifyController.duringDispatch = null;
        check(service.stopsSelf == 0 && service.audio.keys.size() == 2 && SpotifyController.dispatched == dispatched + 1
                        && phoneWake() && stereo(service),
                "The player's intentional focus handoff must not disturb phone wake after the conversation");

        car = wake(service);
        car.input(true);
        service.onDestroy();
        check(car.isClosed && stereo(service) && focusFree(service) && WakeMicrophone.opened == WakeMicrophone.closed,
                "Service stop must close the car conversation and release the route");

        service = start(true);
        car = wake(service);
        car.input(true);
        Handler.asyncPosts = true;
        service.audio.focus.listener.onAudioFocusChange(-2);
        service.telecom.busy = true;
        service.broadcast(TelephonyManager.EXTRA_STATE_RINGING);
        Handler.advance(0);
        check(service.stopsSelf == 0, "Focus loss before ringing must not queue a terminal stop");
        service.telecom.busy = false;
        service.broadcast(TelephonyManager.EXTRA_STATE_IDLE);
        Handler.advance(1000);
        check(phoneWake(), "Hang-up must recover phone wake after a focus-first interruption");
        service.onDestroy();
        Handler.asyncPosts = false;

        service = start(true);
        car = wake(service);
        car.input(true);
        Handler.asyncPosts = true;
        AndroidCarAudioSession.last.session.interrupt("ROUTE_LOST");
        command(service);
        check(SpotifyController.dispatched == 0, "A closed car route must reject queued commands before cleanup runs");
        Handler.advance(1000);
        Handler.asyncPosts = false;
        Handler.advance(1000);
        check(phoneWake() && service.stopsSelf == 0, "Queued route-loss cleanup must recover to phone wake");
        service.onDestroy();

        service = start(true);
        car = wake(service);
        car.input(true);
        AndroidCarAudioSession.preserveForeignMode = true;
        say(car, "next");
        check(service.audio.keys.size() == 2 && WakeMicrophone.opened == WakeMicrophone.closed && SpotifyController.dispatched == 0,
                "Foreign communication mode left after release must count as a call and cancel the command");
        AndroidCarAudioSession.preserveForeignMode = false;
        service.audio.mode(AudioManager.MODE_NORMAL);
        Handler.advance(1000);
        check(phoneWake(), "Clearing that foreign mode must restore phone wake");
        service.onDestroy();

        service = start(false);
        check(phoneWake(), "Phone wake needs no Phone permission");
        opens = WakeMicrophone.opened;
        wake(service);
        check(WakeMicrophone.opened == opens && stereo(service)
                        && DiagnosticHistory.events.stream().noneMatch(x -> x.contains("NullPointerException")),
                "Missing Phone permission must refuse the car conversation without a null session");
        Handler.advance(1000);
        check(phoneWake() && service.stopsSelf == 0, "Missing Phone permission must recover to phone wake");
        service.onDestroy();

        android.content.SharedPreferences.placeGate = true;
        PlaceMonitor.wifi = false;
        PlaceMonitor.bluetooth = true;
        service = start(true);
        car = wake(service);
        car.input(true);
        PlaceMonitor.last.set(false, false);
        Handler.advance(16000);
        check(!car.isClosed && service.audio.mode == AudioManager.MODE_IN_COMMUNICATION,
                "The place grace period must not end an active car conversation");
        say(car, "next");
        check(SpotifyController.dispatched == 1 && SpotifyController.cancelled == 0 && stereo(service)
                        && WakeMicrophone.opened == WakeMicrophone.closed && DiagnosticHistory.events.contains("PLACE_PAUSED"),
                "Leaving during a car conversation must finish the command, then pause phone wake");
        service.onDestroy();
        android.content.SharedPreferences.placeGate = false;
        PlaceMonitor.bluetooth = false;

        WakeMicrophone.holdBluetooth = false;
        android.content.SharedPreferences.carInput = false;
        System.out.println("Car conversation: stereo phone wake, verified car route, graceful release, bounds, interruption recovery and stale callbacks passed");
    }
}
