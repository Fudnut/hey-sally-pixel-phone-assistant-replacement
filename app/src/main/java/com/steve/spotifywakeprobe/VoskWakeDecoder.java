/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.IOException;

/** One closed-vocabulary decoder for wake detection. */
final class VoskWakeDecoder implements WakeDetector.Decoder {
    private final Recognizer recognizer;
    private VoskWakeDecoder(Recognizer recognizer) { this.recognizer = recognizer; }

    static WakeDetector create(Model model) throws IOException {
        return new WakeDetector(new VoskWakeDecoder(new Recognizer(model, 16000f,
                WakeGrammar.json())));
    }

    @Override public String accept(short[] samples, int count) throws IOException {
        // FinalResult also flushes feature state: each buffer is an independent segment.
        return recognizer.acceptWaveForm(samples, count) ? text(recognizer.getFinalResult()) : null;
    }
    @Override public void close() { recognizer.close(); }

    private static String text(String json) throws IOException {
        try {
            JsonElement value = JsonParser.parseString(json).getAsJsonObject().get("text");
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
                throw new IOException("Wake result unavailable");
            return value.getAsString();
        } catch (RuntimeException error) {
            // Do not attach provider JSON/recognized words to a logged exception.
            throw new IOException("Wake result unavailable");
        }
    }
}
