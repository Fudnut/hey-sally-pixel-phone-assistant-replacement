/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

/**
 * Says "Yes" before command capture opens, so Sally never hears herself. One warm engine is
 * kept for the service's lifetime; the old beep is the fallback when speech fails or is slow.
 */
final class ReadyCue implements AutoCloseable {
    private static final long LIMIT_MS = 1500;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech speaker;
    private boolean speakerReady, speakerFailed, car;
    private int cue;
    private Runnable next;
    private final Runnable limit = () -> finish(cue, "TIMEOUT");

    ReadyCue(Context context) {
        this.context = context;
        try { speaker = new TextToSpeech(context, status -> main.post(() -> initialized(status))); }
        catch (RuntimeException error) { speakerFailed = true; }
    }

    // Replies and the cue follow Spotify's media volume; a car conversation uses the hands-free channel.
    static AudioAttributes replyAttributes(boolean car) {
        return new AudioAttributes.Builder()
                .setUsage(car ? AudioAttributes.USAGE_VOICE_COMMUNICATION : AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
    }

    /** Runs {@code then} once, after "Yes" finishes or after the beep; a later play or cancel drops it. */
    void play(boolean car, Runnable then) {
        cancel();
        this.car = car;
        next = then;
        main.postDelayed(limit, LIMIT_MS);
        if (speakerFailed) finish(cue, "UNAVAILABLE");
        else if (speakerReady) speak();
        // Otherwise initialization speaks it, or the limit beeps.
    }

    void cancel() {
        cue++;
        main.removeCallbacks(limit);
        if (next != null) { next = null; stopSpeaking(); }
    }

    private void initialized(int status) {
        if (speaker == null) return;
        if (status != TextToSpeech.SUCCESS) {
            speakerFailed = true;
            finish(cue, "INIT_ERROR");
            return;
        }
        speakerReady = true;
        speaker.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { main.post(() -> finish(cueOf(id), null)); }
            @Override public void onError(String id) { main.post(() -> finish(cueOf(id), "SPEECH_ERROR")); }
        });
        if (next != null) speak();
    }

    private void speak() {
        int current = cue;
        try {
            speaker.setAudioAttributes(replyAttributes(car));
            Bundle params = new Bundle();
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
            if (speaker.speak("Yes", TextToSpeech.QUEUE_FLUSH, params, "cue-" + current) == TextToSpeech.ERROR)
                finish(current, "SPEECH_ERROR");
        } catch (RuntimeException error) { finish(current, "SPEECH_ERROR"); }
    }

    private static int cueOf(String id) {
        try { return id != null && id.startsWith("cue-") ? Integer.parseInt(id.substring(4)) : -1; }
        catch (NumberFormatException error) { return -1; }
    }

    private void finish(int which, String failure) {
        if (which != cue || next == null) return;
        Runnable then = next;
        next = null;
        main.removeCallbacks(limit);
        if (failure != null) {
            // Silence a late "Yes" before listening starts.
            stopSpeaking();
            beep();
        }
        DiagnosticHistory.record(context, failure == null ? "READY_CUE SPOKEN" : "READY_CUE BEEP " + failure);
        then.run();
    }

    private void stopSpeaking() {
        if (!speakerReady) return;
        try { speaker.stop(); } catch (RuntimeException ignored) { }
    }

    private void beep() {
        try {
            ToneGenerator tone = new ToneGenerator(car ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_NOTIFICATION, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            main.postDelayed(tone::release, 200);
        } catch (RuntimeException error) {
            DiagnosticHistory.record(context, "COMMAND_BEEP_ERROR");
        }
    }

    @Override public void close() {
        cancel();
        if (speaker != null) {
            try { speaker.shutdown(); } catch (RuntimeException ignored) { }
            speaker = null;
        }
        speakerReady = false;
    }
}
