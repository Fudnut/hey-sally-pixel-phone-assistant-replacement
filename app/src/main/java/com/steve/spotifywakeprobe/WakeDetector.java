/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import java.io.IOException;

/** Exact wake matching over one closed-vocabulary decoder. */
final class WakeDetector implements AutoCloseable {
    interface Decoder extends AutoCloseable {
        String accept(short[] samples, int count) throws IOException;
        @Override void close();
    }
    private final Decoder decoder;
    private boolean pendingHey;
    private boolean closed;
    // Fixed keyword flags/counts for bounded private debug probes; never transcripts.
    int candidateFinals, candidateFlags;

    WakeDetector(Decoder decoder) {
        this.decoder = decoder;
    }

    String accept(short[] samples, int count) throws IOException {
        if (closed) throw new IllegalStateException("Wake detector closed");
        if (count < 0 || count > samples.length) throw new IllegalArgumentException("Invalid audio count");
        String text = decoder.accept(samples, count);
        if (text == null) return null;
        candidateFinals++;
        candidateFlags |= keywordFlags(text);
        // A held Hey lasts one result: a split Sally completes it, any other result is judged alone.
        boolean held = pendingHey;
        pendingHey = false;
        if (held && text.trim().equalsIgnoreCase("sally")) return "hey sally";
        if (text.trim().equalsIgnoreCase("hey")) {
            pendingHey = true;
            return null;
        }
        return WakePhrase.matches(text) ? "hey sally" : text;
    }

    private static int keywordFlags(String text) {
        int flags = 0;
        for (String word : text.trim().split("\\s+")) {
            if (word.equalsIgnoreCase("hey")) flags |= 1;
            if (word.equalsIgnoreCase("sally")) flags |= 2;
            if (word.equalsIgnoreCase("hay")) flags |= 4;
        }
        return flags;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        pendingHey = false;
        decoder.close();
    }
}
