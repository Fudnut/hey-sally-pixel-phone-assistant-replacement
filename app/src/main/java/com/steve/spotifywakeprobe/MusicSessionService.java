package com.steve.spotifywakeprobe;

import android.service.voice.VoiceInteractionSession;
import android.service.voice.VoiceInteractionSessionService;

public class MusicSessionService extends VoiceInteractionSessionService {
    @Override public VoiceInteractionSession onNewSession(android.os.Bundle args) {
        return new VoiceInteractionSession(this) { };
    }
}
