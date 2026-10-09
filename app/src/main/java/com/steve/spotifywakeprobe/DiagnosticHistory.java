package com.steve.spotifywakeprobe;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Instant;

final class DiagnosticHistory {
    static final int MAX_ENTRIES = 1024;
    private static final String PREFS = "diagnostics";
    private static final String KEY = "events";

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

    static synchronized void startTrial(Context context) {
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
