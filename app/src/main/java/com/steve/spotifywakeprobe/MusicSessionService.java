/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public class MusicSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(android.os.Bundle args) {
        return new VoiceInteractionSession(this) { };
    }
}
