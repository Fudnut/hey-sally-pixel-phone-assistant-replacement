package com.steve.spotifywakeprobe;

import android.Manifest;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.IBinder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class WakeService extends Service {
    private static final String TAG = "SpotifyWakeProbe";
    private static final int NOTIFICATION_ID = 1;
    private static final int ERROR_NOTIFICATION_ID = 2;
    private static final long HEARTBEAT_INTERVAL_MS = 30 * 60 * 1000L;
    private SpeechService speech;
    private WakeMicrophone wakeMicrophone;
    private Recognizer recognizer;
    private Model model;
    private boolean loading;
    private boolean destroyed;
    private boolean historyStarted;
    private long lastWake;
    private int generation;
    private boolean awaitingCommand;
    private String lastCommandHypothesis = "";
    private String partialCandidate = "";
    private long partialSince;
    private SpeechRecognizer deviceCommand;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable deviceTimeout;
    private long commandStartedAt;
    private long speechStartedAt;
    private AudioFocusRequest focusRequest;
    private boolean focusHeld;
    private boolean focusRequested;
    private boolean holdFocusForCommand;
    private Runnable focusLimit;
    private Runnable retry;
    private int listenFailures;
    private long listenReadyAt;
    private final PlaylistBrowse playlists = new PlaylistBrowse();
    private int commandSequence;
    private boolean replying;
    private boolean pagePending;
    private boolean feedbackReply;
    private String replyLabel;
    private TextToSpeech spokenReply;
    private Runnable replyTimeout;
    private Thread playlistFetch;
    private Runnable cancelSpecialPlayback;
    private boolean specialPlaybackDispatched;
    private final Runnable heartbeat = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            DiagnosticHistory.record(WakeService.this, "HEARTBEAT");
            handler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        DiagnosticHistory.clearLegacyStatus(this);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "NO_MICROPHONE_PERMISSION");
            DiagnosticHistory.record(this, "NO_MICROPHONE_PERMISSION");
            stopSelf();
            return START_NOT_STICKY;
        }
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("wake", "Wake probe", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIFICATION_ID, notification("Loading offline model"),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        getSystemService(NotificationManager.class).cancel(ERROR_NOTIFICATION_ID);
        if (!historyStarted) {
            historyStarted = true;
            DiagnosticHistory.record(this, "SERVICE_START");
            handler.postDelayed(heartbeat, HEARTBEAT_INTERVAL_MS);
        }
        if (!loading && speech == null && model == null) {
            loading = true;
            StorageService.unpack(this, "model-en-us", "model", loaded -> {
                loading = false;
                if (destroyed) { loaded.close(); return; }
                model = loaded;
                try {
                    listen(false);
                } catch (IOException | RuntimeException error) {
                    fail(error);
                }
            }, this::fail);
        }
        return START_STICKY;
    }

    private Notification notification(String message) {
        return new Notification.Builder(this, "wake")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Spotify Wake Probe")
                .setContentText(message)
                .setContentIntent(openApp())
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .build();
    }

    private PendingIntent openApp() {
        return PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void update(String message) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(message));
    }

    private void fail(Exception error) {
        handler.post(() -> {
            if (destroyed || retry != null) return;
            String type = error.getClass().getSimpleName();
            Log.e(TAG, "PROBE_ERROR " + type);
            DiagnosticHistory.record(this, "SERVICE_ERROR " + type);
            getSharedPreferences("probe", MODE_PRIVATE).edit()
                    .putString("last", Instant.now() + " ERROR: " + type).apply();
            commandSequence++;
            stopReply();
            playlists.clear();
            suspendRecognition();
            finishCommandFocus();
            if (model != null) {
                if (listenReadyAt > 0 && SystemClock.elapsedRealtime() - listenReadyAt > 60000)
                    listenFailures = 0;
                listenReadyAt = 0;
                if (listenFailures < 3) {
                    int attempt = ++listenFailures;
                    DiagnosticHistory.record(this, "LISTEN_RETRY " + attempt);
                    update("Listening interrupted; retrying");
                    retry = () -> {
                        retry = null;
                        try { listen(false); } catch (IOException | RuntimeException failed) { fail(failed); }
                    };
                    handler.postDelayed(retry, attempt * 2000L);
                    return;
                }
            }
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel("wake_errors", "Listener errors",
                    NotificationManager.IMPORTANCE_DEFAULT));
            try {
                manager.notify(ERROR_NOTIFICATION_ID, new Notification.Builder(this, "wake_errors")
                        .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                        .setContentTitle("Spotify Wake Probe stopped")
                        .setContentText("Open app to restart listening")
                        .setContentIntent(openApp())
                        .setAutoCancel(true)
                        .setVisibility(Notification.VISIBILITY_SECRET)
                        .build());
            } finally { stopSelf(); }
        });
    }

    private void suspendRecognition() {
        generation++;
        awaitingCommand = false;
        if (wakeMicrophone != null) { wakeMicrophone.close(); wakeMicrophone = null; }
        if (speech != null) { speech.cancel(); speech.shutdown(); speech = null; }
        if (recognizer != null) { recognizer.close(); recognizer = null; }
        if (deviceTimeout != null) { handler.removeCallbacks(deviceTimeout); deviceTimeout = null; }
        if (deviceCommand != null) { deviceCommand.cancel(); deviceCommand.destroy(); deviceCommand = null; }
    }

    private void listen(boolean forCommand) throws IOException {
        suspendRecognition();
        if (forCommand) {
            takeAudioFocus();
            commandStartedAt = SystemClock.elapsedRealtime();
            speechStartedAt = 0;
        }
        else if (!holdFocusForCommand) releaseAudioFocus();
        awaitingCommand = forCommand;
        lastCommandHypothesis = "";
        partialCandidate = "";
        int current = generation;
        if (forCommand && SpeechRecognizer.isRecognitionAvailable(this)) {
            startDeviceCommand(current);
            return;
        }
        recognizer = forCommand ? new Recognizer(model, 16000f)
                : new Recognizer(model, 16000f, "[\"hey sally\", \"[unk]\"]");
        RecognitionListener listener = new RecognitionListener() {
            @Override public void onPartialResult(String hypothesis) { heard(current, hypothesis, "partial", false); }
            @Override public void onResult(String hypothesis) { heard(current, hypothesis, "text", false); }
            @Override public void onFinalResult(String hypothesis) { heard(current, hypothesis, "text", true); }
            @Override public void onError(Exception error) { if (current == generation) fail(error); }
            @Override public void onTimeout() {
                if (current == generation && awaitingCommand) {
                    DiagnosticHistory.record(WakeService.this, "COMMAND_TIMEOUT VOSK");
                    Log.i(TAG, "COMMAND_TIMEOUT");
                    VoiceCommand fallback = VoiceCommand.parse(lastCommandHypothesis);
                    if (fallback != null && fallback.acceptsPartial()) {
                        try { executeCommand(fallback); } catch (IOException error) { fail(error); }
                    } else {
                        recognitionFailed("I didn't catch that. Say Hey Sally to try again.");
                    }
                }
            }
        };
        if (!forCommand) {
            wakeMicrophone = new WakeMicrophone(this, recognizer);
            wakeMicrophone.start(listener, () -> {
                if (current != generation || destroyed) return;
                listenReadyAt = SystemClock.elapsedRealtime();
                update("Listening for Hey Sally");
                Log.i(TAG, "LISTENING_READY");
                DiagnosticHistory.record(this, "LISTENING_READY");
            });
            return;
        }
        speech = new SpeechService(recognizer, 16000f);
        speech.startListening(listener, 20000);
        commandReadyBeep();
        update("Heard Hey Sally; say a command");
        Log.i(TAG, "COMMAND_READY");
        DiagnosticHistory.record(this, "COMMAND_READY VOSK");
    }

    private void startDeviceCommand(int current) {
        deviceCommand = SpeechRecognizer.createSpeechRecognizer(this);
        deviceCommand.setRecognitionListener(new android.speech.RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                if (current != generation) return;
                Log.i(TAG, "COMMAND_DEVICE_READY");
                DiagnosticHistory.record(WakeService.this, "COMMAND_READY SYSTEM");
                commandReadyBeep();
            }
            @Override public void onBeginningOfSpeech() {
                if (current != generation) return;
                if (speechStartedAt == 0) {
                    speechStartedAt = SystemClock.elapsedRealtime();
                    DiagnosticHistory.record(WakeService.this, "COMMAND_SPEECH_BEGIN");
                }
                // Let the recognizer detect the end of speech. The whole attempt still
                // has a 20-second watchdog, including time waiting for final results.
            }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() {
                if (current == generation) recordCommandTiming("END");
            }
            @Override public void onError(int error) {
                if (current != generation) return;
                Log.i(TAG, "COMMAND_DEVICE_ERROR " + error);
                DiagnosticHistory.record(WakeService.this, "COMMAND_ERROR SYSTEM " + error);
                getSharedPreferences("probe", MODE_PRIVATE).edit()
                        .putString("lastAttempt", "System recognizer error " + error).apply();
                recordCommandTiming("ERROR");
                String message = switch (error) {
                    case SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            "I didn't catch that. Say Hey Sally to try again.";
                    case SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                            SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
                            "Speech recognition couldn't connect. Please try again.";
                    case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            "That speech language is unavailable. Change command language in the app.";
                    default -> "Speech recognition failed. Please try again.";
                };
                recognitionFailed(message);
            }
            @Override public void onResults(Bundle results) {
                if (current != generation) return;
                recordCommandTiming("RESULT");
                ArrayList<String> options = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                int count = options == null ? 0 : options.size();
                Log.i(TAG, "COMMAND_DEVICE_OPTIONS count=" + count);
                getSharedPreferences("probe", MODE_PRIVATE).edit()
                        .putString("lastAttempt", "System recognizer returned " + count + " options").apply();
                VoiceCommand command = null;
                ArrayList<String> playlistNames = new ArrayList<>();
                ArrayList<String> radioNames = new ArrayList<>();
                if (options != null) for (String option : options) {
                    VoiceCommand candidate = VoiceCommand.parse(option);
                    if (candidate == null) continue;
                    if (candidate.kind == VoiceCommand.Kind.PLAY
                            && "playlist".equals(candidate.type)) playlistNames.add(candidate.query);
                    if (candidate.kind == VoiceCommand.Kind.PLAY_SPECIAL
                            && "radio".equals(candidate.type) && !candidate.query.isBlank()) radioNames.add(candidate.query);
                    if (command == null) command = candidate;
                    if ((candidate.kind == VoiceCommand.Kind.PLAY_SPECIAL
                            || candidate.kind == VoiceCommand.Kind.PLAY && !"auto".equals(candidate.type))
                            && command.kind == VoiceCommand.Kind.PLAY
                            && "auto".equals(command.type)) {
                        command = candidate;
                    }
                }
                try {
                    if (command != null) executeCommand(command,
                            command.kind == VoiceCommand.Kind.PLAY_SPECIAL && "radio".equals(command.type)
                                    ? radioNames : playlistNames);
                    else {
                        DiagnosticHistory.record(WakeService.this, "COMMAND_UNRECOGNIZED");
                        recognitionFailed("I didn't understand that command. Say Hey Sally to try again.");
                    }
                } catch (IOException failed) { fail(failed); }
            }
            @Override public void onPartialResults(Bundle partialResults) {
                if (current != generation) return;
                ArrayList<String> options = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (options != null && !options.isEmpty()) lastCommandHypothesis = options.get(0);
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        String language = CommandLanguage.resolve(getSharedPreferences("probe", MODE_PRIVATE)
                .getString("commandLanguage", "en-US"), java.util.Locale.getDefault());
        DiagnosticHistory.record(this, "COMMAND_LANGUAGE " + language);
        Intent command = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS,
                        new ArrayList<>(java.util.List.of("play playlist", "play my playlist",
                                "play next song", "pause", "resume", "previous song",
                                "list my playlists", "more playlists", "play number two",
                                "play liked songs", "play local files", "play DJ", "play Daily Mix one",
                                "play Made For You two", "play radio")))
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        deviceCommand.startListening(command);
        deviceTimeout = () -> {
            if (current != generation) return;
            Log.i(TAG, "COMMAND_DEVICE_TIMEOUT");
            DiagnosticHistory.record(this, "COMMAND_TIMEOUT SYSTEM");
            getSharedPreferences("probe", MODE_PRIVATE).edit()
                    .putString("lastAttempt", "System recognizer timeout").apply();
            recordCommandTiming("TIMEOUT");
            VoiceCommand fallback = VoiceCommand.parse(lastCommandHypothesis);
            try {
                if (fallback != null && fallback.acceptsPartial())
                    executeCommand(fallback);
                else recognitionFailed("I didn't catch that. Say Hey Sally to try again.");
            } catch (IOException failed) { fail(failed); }
        };
        handler.postDelayed(deviceTimeout, 20000);
        update("Heard Hey Sally; say a command");
        Log.i(TAG, "COMMAND_DEVICE_STARTED");
    }

    private void heard(int current, String json, String key, boolean complete) {
        if (current != generation || destroyed) return;
        try {
            String words = new JSONObject(json).optString(key);
            if (awaitingCommand) {
                if ("partial".equals(key)) {
                    if (!words.isEmpty()) {
                        lastCommandHypothesis = words;
                        VoiceCommand candidate = VoiceCommand.parse(words);
                        if (candidate != null && candidate.acceptsPartial()) {
                            if (words.equals(partialCandidate)
                                    && System.currentTimeMillis() - partialSince >= 200) {
                                executeCommand(candidate);
                            } else if (!words.equals(partialCandidate)) {
                                partialCandidate = words;
                                partialSince = System.currentTimeMillis();
                            }
                        } else partialCandidate = "";
                    }
                    return;
                }
                Log.i(TAG, "COMMAND_TEXT_RECEIVED");
                VoiceCommand command = VoiceCommand.parse(words);
                if (command != null) executeCommand(command);
                else if (complete) {
                    VoiceCommand fallback = VoiceCommand.parse(lastCommandHypothesis);
                    if (fallback != null && fallback.acceptsPartial())
                        executeCommand(fallback);
                    else {
                        DiagnosticHistory.record(this, "COMMAND_UNRECOGNIZED");
                        recognitionFailed("I didn't understand that command. Say Hey Sally to try again.");
                    }
                }
                return;
            }
            if ("partial".equals(key) || !WakePhrase.matches(words)
                    || System.currentTimeMillis() - lastWake < 5000) return;
            lastWake = System.currentTimeMillis();
            listenFailures = 0;
            boolean locked = getSystemService(KeyguardManager.class).isKeyguardLocked();
            boolean screenOn = getSystemService(PowerManager.class).isInteractive();
            String event = Instant.now() + " WAKE_DETECTED locked=" + locked + " screenOn=" + screenOn;
            Log.i(TAG, event);
            getSharedPreferences("probe", MODE_PRIVATE).edit().putString("last", event).apply();
            DiagnosticHistory.record(this, "WAKE_DETECTED locked=" + locked + " screenOn=" + screenOn);
            listen(true);
        } catch (IOException error) { fail(error); }
        catch (Exception error) { fail(error); }
    }

    private void commandReadyBeep() {
        try {
            ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            handler.postDelayed(tone::release, 200);
        } catch (RuntimeException error) {
            DiagnosticHistory.record(this, "COMMAND_BEEP_ERROR");
        }
    }

    private void recordCommandTiming(String stage) {
        long now = SystemClock.elapsedRealtime();
        DiagnosticHistory.record(this, "COMMAND_CAPTURE " + stage
                + " elapsedMs=" + (now - commandStartedAt)
                + " speechMs=" + (speechStartedAt == 0 ? 0 : now - speechStartedAt));
    }

    private void recognitionFailed(String message) {
        int request = ++commandSequence;
        if (beginReply(request, "RECOGNITION", true)) speakReply(request, message, false);
    }

    private void executeCommand(VoiceCommand command) throws IOException {
        executeCommand(command, List.of());
    }

    private void executeCommand(VoiceCommand command, List<String> playlistNames) throws IOException {
        command = command.forPlaylistContext(playlists.isActive(SystemClock.elapsedRealtime()));
        int request = ++commandSequence;
        if (focusLimit != null) { handler.removeCallbacks(focusLimit); focusLimit = null; }
        String commandLabel = command.kind + (command.kind == VoiceCommand.Kind.PLAY || command.kind == VoiceCommand.Kind.PLAY_SPECIAL
                ? " " + command.type : "");
        Log.i(TAG, "COMMAND_HEARD " + commandLabel);
        DiagnosticHistory.record(this, "COMMAND_HEARD " + commandLabel);
        getSharedPreferences("probe", MODE_PRIVATE).edit()
                .putString("lastCommand", Instant.now() + " " + commandLabel).apply();
        if ("normalized".equals(command.type))
            DiagnosticHistory.record(this, "COMMAND_NORMALIZED " + command.kind);
        if (command.kind == VoiceCommand.Kind.PLAY_SPECIAL) {
            if (!beginReply(request, commandLabel, true)) return;
            update("Finding Spotify collection");
            cancelSpecialPlayback = SpotifySpecialPlayback.start(this, command, playlistNames, () -> {
                // Spotify must receive focus for playback. A normal reply still retains focus.
                specialPlaybackDispatched = true;
                finishCommandFocus();
            }, error -> {
                if (!isReplying(request)) return;
                if (error != null) {
                    DiagnosticHistory.record(this, "COMMAND_RESULT ERROR");
                    specialPlaybackDispatched = false;
                    if (beginReply(request, commandLabel, true)) speakReply(request, error, false);
                } else {
                    stopReply();
                    commandStatus(commandLabel, "Spotify: special collection");
                    try { listen(false); } catch (IOException | RuntimeException failed) { fail(failed); }
                }
            });
            return;
        }
        if (command.kind == VoiceCommand.Kind.LIST_PLAYLISTS) {
            playlists.clear();
            if (!beginReply(request, commandLabel, false)) return;
            playlistFetch = SpotifyController.fetchPlaylists(this, entries -> {
                if (!isReplying(request)) return;
                DiagnosticHistory.record(this, "PLAYLISTS_READY count=" + entries.size());
                playlists.replace(entries, SystemClock.elapsedRealtime());
                readPlaylistPage(request);
            }, message -> {
                if (!isReplying(request)) return;
                DiagnosticHistory.record(this, "PLAYLIST_LOOKUP_ERROR");
                speakReply(request, message, false);
            });
            return;
        }
        if (command.kind == VoiceCommand.Kind.MORE_PLAYLISTS) {
            if (beginReply(request, commandLabel, false)) readPlaylistPage(request);
            return;
        }
        PlaylistBrowse.Entry selected = null;
        if (command.kind == VoiceCommand.Kind.PLAY_NUMBER) {
            try {
                selected = playlists.select(Integer.parseInt(command.query), SystemClock.elapsedRealtime());
                DiagnosticHistory.record(this, "PLAYLIST_SELECTED number=" + command.query);
            } catch (IllegalStateException error) {
                if (beginReply(request, commandLabel, false)) speakReply(request, error.getMessage(), false);
                return;
            }
        }
        holdFocusForCommand = focusRequested;
        listen(false);
        if (holdFocusForCommand) {
            focusLimit = () -> {
                if (request == commandSequence && holdFocusForCommand) {
                    Log.w(TAG, "COMMAND_FOCUS_LIMIT");
                    finishCommandFocus();
                }
            };
            handler.postDelayed(focusLimit, 15000);
        }
        java.util.function.Consumer<String> report = message -> handler.post(() -> {
            if (destroyed || request != commandSequence) return;
            if (!commandStatus(commandLabel, message) && beginReply(request, commandLabel, true))
                speakReply(request, "I couldn't do that. Check Spotify and its authorization in the app.", false);
        });
        if (selected != null) SpotifyController.playPlaylist(this, selected.uri, report);
        else SpotifyController.execute(this, command, playlistNames, report);
    }

    private boolean isReplying(int request) {
        return !destroyed && replying && request == commandSequence;
    }

    private boolean beginReply(int request, String label, boolean feedback) {
        suspendRecognition();
        replying = true;
        pagePending = false;
        replyLabel = label;
        feedbackReply = feedback;
        takeAudioFocus();
        holdFocusForCommand = focusHeld;
        if (!focusHeld) {
            recordReplyEvent("FOCUS_DENIED");
            finishReply(request, false);
            return false;
        }
        update(feedbackReply ? "Preparing command feedback" : "Preparing playlist reply");
        setReplyTimeout(request, 30000);
        return true;
    }

    private void setReplyTimeout(int request, long delay) {
        if (replyTimeout != null) handler.removeCallbacks(replyTimeout);
        replyTimeout = () -> {
            if (!isReplying(request)) return;
            recordReplyEvent("REPLY_TIMEOUT");
            finishReply(request, false);
        };
        handler.postDelayed(replyTimeout, delay);
    }

    private void readPlaylistPage(int request) {
        try { speakReply(request, playlists.nextPage(SystemClock.elapsedRealtime()), true); }
        catch (IllegalStateException error) { speakReply(request, error.getMessage(), false); }
    }

    private void speakReply(int request, String text, boolean commandOk) {
        if (!isReplying(request)) return;
        pagePending = commandOk;
        setReplyTimeout(request, feedbackReply ? 10000 : 90000);
        update(feedbackReply ? "Speaking command feedback" : "Reading playlist reply");
        try {
            spokenReply = new TextToSpeech(this, status -> handler.post(
                    () -> startSpeech(request, text, commandOk, status)));
        } catch (RuntimeException error) {
            recordReplyEvent("SPEECH_INIT_ERROR");
            finishReply(request, false);
        }
    }

    private void startSpeech(int request, String text, boolean commandOk, int status) {
        if (!isReplying(request)) return;
        if (status != TextToSpeech.SUCCESS) {
            recordReplyEvent("SPEECH_INIT_ERROR");
            finishReply(request, false);
            return;
        }
        try {
            spokenReply.setAudioAttributes(new AudioAttributes.Builder()
                        // Follow Spotify's media volume, including Bluetooth's per-route setting.
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            spokenReply.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        handler.post(() -> {
                            if (isReplying(request)) recordReplyEvent("SPEECH_START");
                        });
                    }
                    @Override public void onDone(String id) {
                        handler.post(() -> {
                            if (!isReplying(request)) return;
                            recordReplyEvent("SPEECH_DONE");
                            finishReply(request, commandOk);
                        });
                    }
                    @Override public void onError(String id) {
                        handler.post(() -> {
                            if (!isReplying(request)) return;
                            recordReplyEvent("SPEECH_ERROR");
                            finishReply(request, false);
                        });
                    }
                });
            if (spokenReply.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply-" + request)
                    == TextToSpeech.ERROR) finishReply(request, false);
        } catch (RuntimeException error) {
            recordReplyEvent("SPEECH_INIT_ERROR");
            finishReply(request, false);
        }
    }

    private void finishReply(int request, boolean success) {
        if (!isReplying(request)) return;
        if (pagePending) {
            if (success) playlists.readCompleted(SystemClock.elapsedRealtime());
            else playlists.clear();
        }
        String label = replyLabel;
        stopReply();
        if (feedbackReply) {
            finishCommandFocus();
            update("Listening for Hey Sally");
        } else commandStatus(label, success ? "Spotify: playlist reply" : "Playlist reply failed");
        // Leave a short gap for speaker/Bluetooth audio to drain before reopening the microphone.
        handler.postDelayed(() -> {
            if (destroyed || request != commandSequence) return;
            try { listen(false); } catch (IOException | RuntimeException error) { fail(error); }
        }, 400);
    }

    private void recordReplyEvent(String event) {
        DiagnosticHistory.record(this, (feedbackReply ? "COMMAND_FEEDBACK_" : "PLAYLIST_") + event);
    }

    private void stopReply() {
        replying = false;
        specialPlaybackDispatched = false;
        if (cancelSpecialPlayback != null) { cancelSpecialPlayback.run(); cancelSpecialPlayback = null; }
        if (replyTimeout != null) { handler.removeCallbacks(replyTimeout); replyTimeout = null; }
        if (playlistFetch != null) { playlistFetch.interrupt(); playlistFetch = null; }
        if (spokenReply != null) { spokenReply.stop(); spokenReply.shutdown(); spokenReply = null; }
    }

    private boolean commandStatus(String commandLabel, String message) {
        boolean success = message.startsWith("Spotify: ") || "Opening Spotify".equals(message);
        String status = commandLabel + (success ? " completed" : " failed");
        if (!success && message.contains("Authorize")) status += "; authorize Spotify in the app";
        Log.i(TAG, "COMMAND_STATUS " + status);
        DiagnosticHistory.record(this, success ? "COMMAND_RESULT OK" : "COMMAND_RESULT ERROR");
        getSharedPreferences("probe", MODE_PRIVATE).edit()
                .putString("lastCommand", Instant.now() + " " + status).apply();
        finishCommandFocus();
        update(success ? "Listening for Hey Sally" : "Command failed; open app");
        return success;
    }

    private void finishCommandFocus() {
        holdFocusForCommand = false;
        if (focusLimit != null) { handler.removeCallbacks(focusLimit); focusLimit = null; }
        releaseAudioFocus();
    }

    private void takeAudioFocus() {
        if (focusRequest == null) focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (change == AudioManager.AUDIOFOCUS_GAIN) {
                        if (focusRequested) focusHeld = true;
                    } else if (change < 0) {
                        focusHeld = false;
                        // Google Speech Services temporarily takes exclusive focus. Keep our
                        // request underneath it so Spotify cannot resume before the reply.
                        if (change == AudioManager.AUDIOFOCUS_LOSS) focusRequested = false;
                        if (replying && !specialPlaybackDispatched) {
                            recordReplyEvent("FOCUS_LOST");
                            finishReply(commandSequence, false);
                        }
                    }
                }, handler).build();
        if (focusHeld) return;
        focusHeld = getSystemService(AudioManager.class).requestAudioFocus(focusRequest)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (focusHeld) focusRequested = true;
        Log.i(TAG, "COMMAND_AUDIO_FOCUS " + focusHeld);
    }

    private void releaseAudioFocus() {
        if (focusRequested) getSystemService(AudioManager.class).abandonAudioFocusRequest(focusRequest);
        focusHeld = false;
        focusRequested = false;
    }

    @Override public void onDestroy() {
        destroyed = true;
        handler.removeCallbacks(heartbeat);
        if (retry != null) handler.removeCallbacks(retry);
        DiagnosticHistory.record(this, "SERVICE_STOP");
        commandSequence++;
        stopReply();
        playlists.clear();
        suspendRecognition();
        if (focusLimit != null) handler.removeCallbacks(focusLimit);
        releaseAudioFocus();
        if (model != null) { model.close(); model = null; }
        Log.i(TAG, "LISTENING_STOPPED");
        super.onDestroy();
    }
}
