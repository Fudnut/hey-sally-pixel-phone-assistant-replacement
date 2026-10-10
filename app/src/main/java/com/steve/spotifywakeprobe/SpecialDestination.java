/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import java.text.Normalizer;
import java.util.Locale;

/** Exact special-destination matching; never send these requests to song search. */
final class SpecialDestination {
    private SpecialDestination() { }

    static final class Matches<T> {
        private final java.util.LinkedHashMap<String, T> items = new java.util.LinkedHashMap<>();
        private final java.util.HashSet<String> libraryUris = new java.util.HashSet<>();
        int rank = Integer.MAX_VALUE;

        void add(String uri, T item, int candidateRank, boolean fromLibrary) {
            if (candidateRank == Integer.MAX_VALUE || candidateRank > rank) return;
            if (candidateRank < rank) { items.clear(); libraryUris.clear(); rank = candidateRank; }
            if (!items.containsKey(uri)) {
                items.put(uri, item);
                if (fromLibrary) libraryUris.add(uri);
            }
        }

        boolean needsLibrary() { return items.isEmpty() || rank > 0; }
        int size() { return items.size(); }
        T item() { return items.values().iterator().next(); }
        boolean fromLibrary() { return libraryUris.contains(items.keySet().iterator().next()); }
    }

    static int matchRank(VoiceCommand command, String title, java.util.List<String> alternatives) {
        if (matches(command, title)) return 0;
        if (command.type.equals("radio")) {
            for (int i = 0; i < alternatives.size(); i++)
                if (normalize(alternatives.get(i) + " Radio").equals(normalize(title))) return i + 1;
        }
        return Integer.MAX_VALUE;
    }

    static boolean matches(VoiceCommand command, String title) {
        String expected;
        switch (command.type) {
            case "liked": expected = "Liked Songs"; break;
            case "local": expected = "Local Files"; break;
            case "dj": expected = "DJ"; break;
            case "mix": expected = "Daily Mix " + command.query; break;
            case "radio": expected = command.query + " Radio"; break;
            default: return false;
        }
        return normalize(expected).equals(normalize(title));
    }

    static String invalid(VoiceCommand command) {
        if (command.type.equals("mix") && !command.query.matches("[1-6]"))
            return "Say play Daily Mix, followed by a number from one to six.";
        if (command.type.equals("radio") && command.query.isBlank())
            return "Say play radio, followed by the artist's name.";
        return null;
    }

    static String unavailable(VoiceCommand command) {
        switch (command.type) {
            case "local": return "Local Files isn't available here. Put those tracks in a Spotify playlist named Local Files, then try again.";
            case "radio":
                String heard = command.query.replaceAll("[\\p{Cntrl}]", " ");
                if (heard.length() > 48) heard = heard.substring(0, 48);
                // Spoken only: the caller logs outcome codes, never this recognized name.
                return "I couldn't find radio for " + heard + ". Try the name again, or save that station in Spotify.";
            case "mix": return "That Daily Mix isn't available. Open Made For You in Spotify, or save the mix to your library, then try again.";
            case "liked": return "Spotify isn't exposing Liked Songs right now. Open it in Spotify, then try again.";
            default: return "Spotify isn't exposing DJ right now. Open DJ in Spotify, then try again.";
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String name = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", "")
                .trim().replaceAll("\\s+", " ");
        return name;
    }
}
