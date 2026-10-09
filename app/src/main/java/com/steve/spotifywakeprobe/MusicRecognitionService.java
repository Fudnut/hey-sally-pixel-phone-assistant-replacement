package com.steve.spotifywakeprobe;

import android.content.Intent;
import android.os.RemoteException;
import android.speech.RecognitionService;
import android.speech.SpeechRecognizer;

// The framework requires this service in assistant metadata. This probe uses local Vosk directly.
public class MusicRecognitionService extends RecognitionService {
    @Override protected void onStartListening(Intent intent, Callback listener) {
        try { listener.error(SpeechRecognizer.ERROR_CLIENT); }
        catch (RemoteException ignored) { }
    }
    @Override protected void onStopListening(Callback listener) { }
    @Override protected void onCancel(Callback listener) { }
}
