/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import java.util.List;

final class PlaylistBrowse {
    static final long EXPIRY_MS = 3 * 60 * 1000L;
    static final int PAGE_SIZE = 5;
    static final class Entry {
        final String name;
        final String uri;
        Entry(String name, String uri) { this.name = name; this.uri = uri; }
    }

    private List<Entry> entries = List.of();
    private int shown;
    private long expiresAt;

    void replace(List<Entry> entries, long now) {
        this.entries = List.copyOf(entries);
        shown = 0;
        expiresAt = now + EXPIRY_MS;
    }

    String nextPage(long now) {
        requireActive(now);
        if (entries.isEmpty()) return "No playlists were found in your Spotify library.";
        if (shown == entries.size()) throw new IllegalStateException("That was the last page. Say play and a number to choose a playlist.");
        StringBuilder reply = new StringBuilder();
        int end = Math.min(entries.size(), shown + PAGE_SIZE);
        while (shown < end) {
            Entry entry = entries.get(shown++);
            String name = entry.name.replaceAll("[\\p{Cntrl}]", " ").trim();
            if (name.length() > 120) name = name.substring(0, 120) + "...";
            reply.append(shown).append(". ").append(name).append(". ");
        }
        reply.append("Use the wake phrase, then say play and a number");
        if (shown < entries.size()) reply.append(", or more playlists");
        return reply.append('.').toString();
    }

    Entry select(int number, long now) {
        requireActive(now);
        if (number < 1 || number > shown)
            throw new IllegalStateException("Choose a number from the playlists already read, or say more playlists.");
        return entries.get(number - 1);
    }

    void readCompleted(long now) { if (expiresAt != 0) expiresAt = now + EXPIRY_MS; }
    void clear() { entries = List.of(); shown = 0; expiresAt = 0; }

    boolean isActive(long now) { return expiresAt != 0 && now < expiresAt; }

    private void requireActive(long now) {
        if (!isActive(now)) {
            clear();
            throw new IllegalStateException("Please say list my playlists first. Playlist numbers expire after three minutes.");
        }
    }
}
