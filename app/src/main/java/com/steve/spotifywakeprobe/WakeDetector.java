/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import java.io.IOException;
import java.util.Arrays;

/** Cheap candidates, verified only on demand. Audio stays bounded in RAM. */
final class WakeDetector implements AutoCloseable {
    static final int MAX_SAMPLES = 16000 * 10;
    private static final int QUIET_TAIL_SAMPLES = 16000;
    interface Decoder extends AutoCloseable {
        String accept(short[] samples, int count) throws IOException;
        String finish() throws IOException;
        void reset();
        @Override void close();
    }
    interface Factory { Decoder create() throws IOException; }
    private final Decoder candidate;
    private final Factory factory;
    private final short[] audio = new short[MAX_SAMPLES];
    private Decoder verifier;
    private int length;
    private boolean overflow;
    private boolean pendingHey;
    private boolean closed;

    WakeDetector(Decoder candidate, Factory factory) {
        this.candidate = candidate;
        this.factory = factory;
    }

    String accept(short[] samples, int count) throws IOException {
        if (closed) throw new IllegalStateException("Wake detector closed");
        if (count < 0 || count > samples.length) throw new IllegalArgumentException("Invalid audio count");
        int copied = Math.min(count, audio.length - length);
        System.arraycopy(samples, 0, audio, length, copied);
        length += copied;
        if (copied != count) overflow = true;
        String text = candidate.accept(samples, count);
        if (text == null) return null;
        boolean hey = false, sally = false;
        for (String word : text.trim().split("\\s+")) {
            if (word.equalsIgnoreCase("hey")) hey = true;
            if (word.equalsIgnoreCase("sally")) sally = true;
        }
        // A silence endpoint can cut between the two words. Hold at most one result.
        if (hey && !sally && !pendingHey && !overflow) {
            pendingHey = true;
            return null;
        }
        try {
            if (!(sally && (hey || pendingHey))) return text;
            // Never verify an incomplete tail of a longer utterance.
            if (overflow || length == 0) return "[unk]";
            if (verifier == null) verifier = factory.create();
            try {
                String verified = verifier.accept(audio, length);
                if (verified == null) verified = verifier.finish();
                return WakePhrase.matches(verified) ? "hey sally" : "[unk]";
            } finally { verifier.reset(); }
        } finally { clearAudio(text.isEmpty() && !pendingHey); }
    }

    private void clearAudio(boolean keepQuietTail) {
        // A blank endpoint can arrive just as speech begins. Keep its last second.
        int tail = keepQuietTail && !overflow ? Math.min(QUIET_TAIL_SAMPLES, length) : 0;
        if (tail > 0) System.arraycopy(audio, length - tail, audio, 0, tail);
        Arrays.fill(audio, tail, length, (short) 0);
        length = tail;
        overflow = false;
        pendingHey = false;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        clearAudio(false);
        try { candidate.close(); }
        finally { if (verifier != null) verifier.close(); }
    }
}
