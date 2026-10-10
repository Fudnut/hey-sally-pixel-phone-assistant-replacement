/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

/** Closed vocabulary with ordinary alternatives for unrelated speech. */
final class WakeGrammar {
    private static final String[] DECOY_WORDS = {
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "i", "it", "for", "not", "on", "with",
            "he", "as", "you", "do", "at", "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "or", "an", "will", "my", "one", "all", "would", "there", "their", "what", "so", "up", "out", "if",
            "about", "who", "get", "which", "go", "me", "when", "make", "can", "like", "time", "no", "just", "him",
            "know", "take", "people", "into", "year", "your", "good", "some", "could", "them", "see", "other", "than",
            "then", "now", "look", "only", "come", "its", "over", "think", "also", "back", "after", "use", "two", "how",
            "our", "work", "first", "well", "way", "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
            "is", "are", "was", "were", "been", "has", "had", "did", "does", "said", "says", "went", "made", "got", "saw",
            "came", "took", "used", "asked", "found", "thought", "told", "left", "called", "tried", "need", "feel", "leave",
            "put", "mean", "keep", "let", "begin", "seem", "help", "talk", "turn", "start", "show", "hear", "play", "run",
            "move", "live", "believe", "hold", "bring", "happen", "write", "provide", "sit", "stand", "lose", "pay", "meet",
            "include", "continue", "set", "learn", "change", "lead", "understand", "watch", "follow", "stop", "create", "speak",
            "read", "allow", "add", "spend", "grow", "open", "walk", "win", "offer", "remember", "love", "consider", "appear",
            "buy", "wait", "serve", "die", "send", "expect", "build", "stay", "fall", "cut", "reach", "kill", "remain",
            "suggest", "raise", "pass", "sell", "require", "report", "decide", "pull",
            "hello", "hi", "hey", "okay", "yes", "please", "thanks", "sorry", "right",
            "today", "tomorrow", "yesterday", "morning", "night", "week", "month",
            "home", "house", "office", "meeting", "call", "email", "project", "team", "plan", "problem", "question", "answer",
            "number", "money", "business", "company", "school", "student", "teacher", "family", "friend", "mother", "father",
            "john", "mary", "sarah", "david", "michael",
            "sally", "sadly", "salad", "silly", "sale", "seller", "cell", "hay", "sad", "sat"
    };

    private WakeGrammar() { }

    static String json() {
        StringBuilder json = new StringBuilder("[\"hey sally\",\"[unk]\"");
        for (String word : DECOY_WORDS) appendQuoted(json.append(','), word);
        return json.append(']').toString();
    }

    private static void appendQuoted(StringBuilder json, String value) {
        json.append('\"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\"' || character == '\\') json.append('\\');
            json.append(character);
        }
        json.append('\"');
    }
}
