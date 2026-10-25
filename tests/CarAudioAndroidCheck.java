/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CarAudioAndroidCheck {
    private static final List<AndroidCarAudioProbe> live = new ArrayList<>();
    private static final List<CarAudioProbeActivity> activities = new ArrayList<>();
    private static final class Screen implements AndroidCarAudioProbe.View {
        final List<String> events = new ArrayList<>();
        int releases;
        public void status(String event) { events.add(event); }
        public void level(int n) { }
        public void released() { releases++; }
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static AndroidCarAudioProbe start(Context context, Screen screen) {
        AndroidCarAudioProbe adapter = new AndroidCarAudioProbe(context, screen);
        live.add(adapter); adapter.session.start(); Handler.advance(0);
        return adapter;
    }
    private static void lifecycle(CarAudioProbeActivity activity, String name) throws Exception {
        Method method = CarAudioProbeActivity.class.getDeclaredMethod(name);
        method.setAccessible(true); method.invoke(activity);
    }
    public static void main(String[] args) throws Exception {
        try {
            Context context = new Context(); Screen screen = new Screen();
            AndroidCarAudioProbe adapter = start(context, screen);
            check(AudioRecord.opens == 1 && !screen.events.contains("BLUETOOTH_INPUT_READY"),
                    "Accepted car output must not claim Bluetooth input while the recorder uses the phone");
            context.audio.inputType = 7; Handler.advance(200);
            check(screen.events.contains("BLUETOOTH_INPUT_READY"), "Real adapter must inspect the running recorder input");
            TextToSpeech.last.init.onInit(0); Handler.advance(0);
            check(TextToSpeech.phrases == 1, "Confirmed Bluetooth input must enable the speaker test");
            TextToSpeech.last.listener.onDone("car-check"); Handler.advance(0);
            check(context.audio.abandons == 0 && adapter.session.isActive(),
                    "Conversation check must retain focus after Sally speaks while listening to the user's reply");
            Handler.advance(5000);
            check(context.audio.abandons == 0 && adapter.session.isActive(),
                    "Music must stay interrupted throughout the reply capture, not only during Sally's phrase");
            context.audio.inputType = 15; Handler.advance(200);
            check(!adapter.session.isActive() && screen.releases == 1 && context.audio.clears == 1
                    && context.audio.normalRequests == 1 && context.audio.abandons == 1
                    && context.receivers.isEmpty() && context.audio.callback == null,
                    "Losing Bluetooth input must release recording, own route/mode and observers once");

            for (String ending : List.of("stop", "deadline", "call", "focus-loss", "tts-error", "background")) {
                context = new Context(); context.audio.inputType = 7; screen = new Screen();
                adapter = start(context, screen);
                TextToSpeech utterance = TextToSpeech.last;
                utterance.init.onInit(0); Handler.advance(0);
                if (!ending.equals("tts-error")) { utterance.listener.onDone("car-check"); Handler.advance(0); }
                check(context.audio.focusRequests == 1 && context.audio.abandons == 0,
                        "Quiet conversation must own focus before ending: " + ending);
                switch (ending) {
                    case "stop" -> adapter.session.stop();
                    case "deadline" -> Handler.advance(30000);
                    case "call" -> adapter.session.onCallChanged(true);
                    case "focus-loss" -> context.audio.lastFocus.listener.onAudioFocusChange(-2);
                    case "tts-error" -> { utterance.listener.onError("car-check"); Handler.advance(0); }
                    case "background" -> adapter.session.onBackgrounded();
                }
                check(!adapter.session.isActive() && screen.releases == 1 && context.audio.abandons == 1,
                        "Conversation ending must release focus exactly once: " + ending);
                utterance.listener.onDone("car-check"); Handler.advance(0); adapter.session.stop();
                check(context.audio.abandons == 1 && screen.releases == 1,
                        "Late speech completion must not release twice or restart after: " + ending);
            }

            context = new Context(); context.audio.inputType = 7; context.audio.focusResult = 0;
            screen = new Screen(); int beforeDeniedFocus = TextToSpeech.phrases;
            adapter = start(context, screen);
            check(!adapter.session.isActive() && screen.events.contains("OUTPUT_ERROR")
                    && TextToSpeech.phrases == beforeDeniedFocus && context.audio.abandons == 0,
                    "Denied focus must close the check without speaking over music or abandoning unowned focus");

            context = new Context(); context.audio.inputType = 7; screen = new Screen();
            adapter = start(context, screen); TextToSpeech lateSpeech = TextToSpeech.last;
            adapter.session.stop(); int phrases = TextToSpeech.phrases;
            lateSpeech.init.onInit(0); Handler.advance(0);
            check(TextToSpeech.phrases == phrases, "Late TTS initialisation must not speak after stop");

            context = new Context(); context.audio.ownCapture = 9; screen = new Screen();
            adapter = start(context, screen); int opens = AudioRecord.opens;
            check(context.audio.mode == 0, "Do not change mode while Sally's old recorder is active");
            context.audio.ownCapture = 0; context.telecom.busy = true; Handler.advance(100);
            check(AudioRecord.opens == opens && !adapter.session.isActive(),
                    "A call during listener shutdown must prevent diagnostic recording");

            context = new Context(); Context callContext = context;
            context.audio.duringRoute = () -> callContext.telecom.busy = true; screen = new Screen();
            adapter = start(context, screen);
            check(AudioRecord.opens == opens && screen.events.contains("CALL_ACTIVE"),
                    "A call arriving inside the Android routing request must prevent recorder creation");

            context = new Context(); context.audio.ownCapture = 9; screen = new Screen();
            adapter = start(context, screen); Handler.advance(30000);
            check(screen.events.contains("CONNECTION_TIMEOUT") && AudioRecord.opens == opens,
                    "Listener shutdown that never completes must time out as a failed connection without creating capture");

            CarAudioProbeActivity activity = new CarAudioProbeActivity();
            activities.add(activity);
            activity.onCreate(null); lifecycle(activity, "onResume");
            activity.status("SPEAKER_TEST_DONE");
            var quietStatusField = CarAudioProbeActivity.class.getDeclaredField("status");
            quietStatusField.setAccessible(true);
            String quietPrompt = ((android.widget.TextView) quietStatusField.get(activity)).text;
            check(quietPrompt.contains("Your turn") && quietPrompt.contains("Music should stay paused"),
                    "The listening phase must explain that music stays paused while the user speaks");
            activity.mic = false; lifecycle(activity, "startCheck");
            check(activity.permissions == 1 && activity.stops == 0 && activity.starts == 0,
                    "Denied microphone permission must not stop Sally or begin a diagnostic");
            activity.mic = true; activity.manager.running = true; activity.audio.inputType = 7;
            lifecycle(activity, "startCheck"); Handler.advance(0);
            check(activity.stops == 1 && activity.starts == 0, "Explicit Start must suspend an existing Sally listener");
            lifecycle(activity, "onPause");
            check(activity.starts == 1 && activity.audio.clears == 1,
                    "Leaving the foreground must close diagnostic routing and restore prior Sally listening once");
            lifecycle(activity, "onResume"); lifecycle(activity, "onPause");
            check(activity.starts == 1, "Lifecycle callbacks must not restart a stopped diagnostic or duplicate restoration");

            activity = new CarAudioProbeActivity(); activities.add(activity); activity.onCreate(null); lifecycle(activity, "onResume");
            activity.audio.available = false; lifecycle(activity, "startCheck"); lifecycle(activity, "onPause");
            check(activity.starts == 0 && activity.stops == 0, "A previously stopped Sally listener must remain stopped");

            activity = new CarAudioProbeActivity(); activities.add(activity); activity.onCreate(null); lifecycle(activity, "onResume");
            lifecycle(activity, "startCheck"); Handler.advance(0); Handler.advance(30000);
            var statusField = CarAudioProbeActivity.class.getDeclaredField("status");
            statusField.setAccessible(true);
            String result = ((android.widget.TextView) statusField.get(activity)).text;
            check(result.contains("never confirmed") && result.contains("failed to connect"),
                    "The real Activity must clearly show failed Bluetooth input setup after the deadline");
            lifecycle(activity, "onPause");
            System.out.println("Car Android boundaries: actual source, TTS/focus cleanup, call preemption and Activity restoration checks passed");
        } finally {
            for (AndroidCarAudioProbe adapter : live) adapter.session.stop();
            for (CarAudioProbeActivity activity : activities) lifecycle(activity, "onPause");
        }
    }
}
