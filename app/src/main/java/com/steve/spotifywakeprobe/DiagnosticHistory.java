package com.steve.spotifywakeprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;

import java.time.Instant;

final class DiagnosticHistory {
    static final int MAX_ENTRIES = 1024;
    private static final String PREFS = "diagnostics";
    private static final String KEY = "events";
    private static final WakeResults WAKE_RESULTS = new WakeResults(SystemClock.elapsedRealtime());

    private DiagnosticHistory() { }

    static synchronized void clearLegacyStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("probe", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("statusRedacted", false)) {
            prefs.edit().remove("last").remove("lastAttempt").remove("lastCommand")
                    .putBoolean("statusRedacted", true).apply();
        }
    }

    static synchronized void record(Context context, String event) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String current = prefs.getString(KEY, "");
        prefs.edit().putString(KEY, append(current, Instant.now() + " " + event)).apply();
    }

    static synchronized String recordWakeResult(Context context, String words, long audioMs) {
        String event = WAKE_RESULTS.result(words, audioMs);
        if (event != null) record(context, event);
        return event;
    }

    static synchronized void flushWakeResults(Context context) {
        String event = WAKE_RESULTS.drain(SystemClock.elapsedRealtime());
        if (event != null) record(context, event);
    }

    static synchronized void resetWakeResults() { WAKE_RESULTS.reset(SystemClock.elapsedRealtime()); }

    static synchronized void startTrial(Context context) {
        resetWakeResults();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, Instant.now() + " TRIAL_START\n").apply();
    }

    static synchronized String read(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "No diagnostic events yet.\n");
    }

    static String append(String current, String entry) {
        String[] lines = current.split("\n");
        StringBuilder result = new StringBuilder();
        for (int i = Math.max(0, lines.length - MAX_ENTRIES + 1); i < lines.length; i++) {
            if (!lines[i].isEmpty()) result.append(lines[i]).append('\n');
        }
        return result.append(entry).append('\n').toString();
    }
}
