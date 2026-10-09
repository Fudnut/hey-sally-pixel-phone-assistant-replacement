package com.steve.spotifywakeprobe;

/** Non-content wake counters; only accepted wakes produce immediate events. */
final class WakeResults {
    private int other;
    private int empty;
    private long windowStart;

    WakeResults(long now) { reset(now); }

    String result(String words, long audioMs) {
        if (WakePhrase.matches(words)) return "WAKE_RESULT class=accepted ms=" + audioMs;
        if (words == null || words.trim().isEmpty()) empty++;
        else other++;
        return null;
    }

    String drain(long now) {
        String event = other == 0 && empty == 0 ? null
                : "WAKE_RESULTS_SUMMARY other=" + other + " empty=" + empty
                + " ms=" + Math.max(0, now - windowStart);
        reset(now);
        return event;
    }

    void reset(long now) { other = 0; empty = 0; windowStart = now; }
}
