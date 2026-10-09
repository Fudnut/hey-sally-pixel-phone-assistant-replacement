package com.steve.spotifywakeprobe;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

public class MusicVoiceService extends VoiceInteractionService {
    @Override public void onReady() {
        super.onReady();
        Log.i("SpotifyWakeProbe", "ASSISTANT_READY");
        if (VoiceInteractionService.isActiveService(this, new ComponentName(this, MusicVoiceService.class))
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startForegroundService(new Intent(this, WakeService.class));
    }

    @Override public void onShutdown() {
        stopService(new Intent(this, WakeService.class));
        Log.i("SpotifyWakeProbe", "ASSISTANT_SHUTDOWN");
        super.onShutdown();
    }
}
