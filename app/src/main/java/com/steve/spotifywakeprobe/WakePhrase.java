/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

final class WakePhrase {
    private WakePhrase() { }

    static boolean matches(String transcript) {
        return transcript != null && transcript.trim().equalsIgnoreCase("hey sally");
    }

    public static void main(String[] args) {
        if (!matches("hey sally") || !matches(" Hey Sally ")
                || matches("hey spotify") || matches("hey music") || matches("please hey sally now")
                || matches("hey salad") || matches("sally") || matches("") || matches(null))
            throw new AssertionError("Wake phrase check failed");
    }
}
