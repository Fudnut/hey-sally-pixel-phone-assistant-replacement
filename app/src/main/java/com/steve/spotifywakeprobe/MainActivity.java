package com.steve.spotifywakeprobe;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.speech.RecognizerIntent;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.text.InputType;
import android.util.Log;
import android.service.voice.VoiceInteractionService;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

public class MainActivity extends Activity {
    private static final int PERMISSIONS = 1;
    private TextView status;
    private TextView history;
    private EditText clientId;
    private String message = "";
    private SpeechRecognizer languageCheck;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        DiagnosticHistory.clearLegacyStatus(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        status = new TextView(this);
        layout.addView(status);
        TextView languageLabel = new TextView(this);
        languageLabel.setText("Command language (takes effect on the next wake)");
        layout.addView(languageLabel);
        Spinner language = new Spinner(this);
        ArrayAdapter<String> choices = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CommandLanguage.LABELS);
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        language.setAdapter(choices);
        String savedLanguage = getSharedPreferences("probe", MODE_PRIVATE).getString("commandLanguage", "en-US");
        for (int i = 0; i < CommandLanguage.VALUES.length; i++)
            if (CommandLanguage.VALUES[i].equals(savedLanguage)) language.setSelection(i);
        language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = CommandLanguage.VALUES[position];
                if (selected.equals(getSharedPreferences("probe", MODE_PRIVATE)
                        .getString("commandLanguage", "en-US"))) return;
                getSharedPreferences("probe", MODE_PRIVATE).edit().putString("commandLanguage", selected).apply();
                checkLanguageSupport();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        layout.addView(language);
        addButton(layout, "Check command language support", v -> checkLanguageSupport());
        clientId = new EditText(this);
        clientId.setSingleLine(true);
        clientId.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        clientId.setHint("Spotify Developer Client ID");
        clientId.setText(getSharedPreferences("spotify", MODE_PRIVATE).getString("clientId",
                ""));
        layout.addView(clientId);
        addButton(layout, "Save Client ID", v -> saveClientId());
        addButton(layout, "Authorize Spotify playback", v -> {
            String id = saveClientId();
            if (!id.isEmpty()) SpotifyController.authorizeRemote(this, id, this::showMessage);
        });
        addButton(layout, "Authorize named music and private playlists", v -> {
            String id = saveClientId();
            if (!id.isEmpty()) SpotifyOAuth.authorize(this, id, this::showMessage);
        });
        addButton(layout, "Grant microphone and start probe", v -> startProbe());
        addButton(layout, "Open Default apps for assistant setup", v -> openDefaultApps());
        addButton(layout, "Refresh status", v -> refresh());
        addButton(layout, "Start fresh diagnostic trial", v -> {
            DiagnosticHistory.startTrial(this);
            refresh();
        });
        addButton(layout, "Copy diagnostic history", v -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(
                    ClipData.newPlainText("Spotify Wake Probe diagnostics", DiagnosticHistory.read(this)));
            showMessage("Diagnostic history copied");
        });
        addButton(layout, "Stop probe", v -> { stopService(new Intent(this, WakeService.class)); refresh(); });
        history = new TextView(this);
        layout.addView(history);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout);
        setContentView(scroll);
        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private String commandLanguage() {
        return CommandLanguage.resolve(getSharedPreferences("probe", MODE_PRIVATE)
                .getString("commandLanguage", "en-US"), java.util.Locale.getDefault());
    }

    private void checkLanguageSupport() {
        if (languageCheck != null) languageCheck.destroy();
        languageCheck = null;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showMessage("System recognition unavailable; the offline fallback uses its US English model.");
            return;
        }
        String language = commandLanguage();
        try {
            SpeechRecognizer checker = SpeechRecognizer.createSpeechRecognizer(this);
            languageCheck = checker;
            showMessage("Checking " + language + " speech support…");
            checker.checkRecognitionSupport(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false), getMainExecutor(),
                    new RecognitionSupportCallback() {
                        @Override public void onSupportResult(RecognitionSupport support) {
                            boolean online = support.getOnlineLanguages().contains(language);
                            boolean installed = support.getInstalledOnDeviceLanguages().contains(language);
                            boolean pending = support.getPendingOnDeviceLanguages().contains(language);
                            boolean downloadable = support.getSupportedOnDeviceLanguages().contains(language);
                            finishLanguageCheck(checker, "LANGUAGE_SUPPORT " + language + " online=" + online
                                    + " installed=" + installed + " pending=" + pending + " downloadable=" + downloadable,
                                    language + ": online " + (online ? "supported" : "not listed")
                                            + "; on-device " + (installed ? "installed" : pending ? "download pending"
                                            : downloadable ? "download available" : "not listed")
                                            + ". Confirm accuracy with a spoken test.");
                        }
                        @Override public void onError(int error) {
                            finishLanguageCheck(checker, "LANGUAGE_SUPPORT_ERROR " + language + " code=" + error,
                                    error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT
                                            ? "This recognizer cannot report language support. Test " + language + " with a spoken command."
                                            : "Could not verify " + language + " support. Try a spoken command.");
                        }
                    });
            status.postDelayed(() -> finishLanguageCheck(checker, "LANGUAGE_SUPPORT_TIMEOUT " + language,
                    "Speech support check timed out. Try a spoken command."), 10000);
        } catch (RuntimeException error) {
            if (languageCheck != null) languageCheck.destroy();
            languageCheck = null;
            showMessage("Speech support check unavailable. Try a spoken command.");
        }
    }

    private void finishLanguageCheck(SpeechRecognizer checker, String event, String result) {
        if (checker != languageCheck) return;
        checker.destroy();
        languageCheck = null;
        DiagnosticHistory.record(this, event);
        showMessage(result);
    }

    @Override protected void onDestroy() {
        if (languageCheck != null) languageCheck.destroy();
        languageCheck = null;
        super.onDestroy();
    }

    private void addButton(LinearLayout layout, String label, View.OnClickListener action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(action);
        layout.addView(button);
    }

    private void startProbe() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS);
            return;
        }
        startForegroundService(new Intent(this, WakeService.class));
        refresh();
    }

    private String saveClientId() {
        String id = clientId.getText().toString().trim();
        if (!id.matches("[a-zA-Z0-9]{32}")) {
            showMessage("Enter the 32-character Spotify Client ID");
            return "";
        }
        getSharedPreferences("spotify", MODE_PRIVATE).edit().putString("clientId", id).apply();
        showMessage("Spotify Client ID saved");
        return id;
    }

    private void showMessage(String next) {
        Log.i("SpotifyWakeProbe", "SETUP_STATUS " + next);
        runOnUiThread(() -> { message = next; refresh(); });
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == PERMISSIONS && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startProbe();
        else refresh();
    }

    private void openDefaultApps() {
        try { startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)); }
        catch (ActivityNotFoundException error) {
            status.setText("Open Settings → Apps → Default apps → Digital assistant app.\n" + status.getText());
        }
    }

    private void refresh() {
        boolean assistant = VoiceInteractionService.isActiveService(this,
                new ComponentName(this, MusicVoiceService.class));
        String last = getSharedPreferences("probe", MODE_PRIVATE).getString("last", "No wake event yet");
        String lastCommand = getSharedPreferences("probe", MODE_PRIVATE)
                .getString("lastCommand", "No command yet");
        String lastAttempt = getSharedPreferences("probe", MODE_PRIVATE)
                .getString("lastAttempt", "No system command attempt yet");
        status.setText((message.isEmpty() ? "" : message + "\n\n")
                + "Wake phrase: Hey Sally\nDefault assistant: " + assistant
                + "\nSystem command recognition: "
                + SpeechRecognizer.isRecognitionAvailable(this)
                + "\nCommand language: " + commandLanguage()
                + "\nLast wake: " + last + "\nLast command: " + lastCommand
                + "\nLast attempt: " + lastAttempt
                + "\n\nAfter setup, lock the phone, say 'Hey Sally', wait for the beep, then say a command. "
                + "Try 'pause', 'resume', 'next', 'previous', 'open Spotify', or 'play song/artist/playlist ...'."
                + "\nSay 'list my playlists' to hear five numbered names. After the reply, use 'Hey Sally' again, "
                + "then 'play two' or 'more playlists'. Numbers expire three minutes after the last page. "
                + "Wait until the spoken reply finishes before speaking.");
        history.setText("\nDiagnostic history (latest " + DiagnosticHistory.MAX_ENTRIES
                + " events):\n" + DiagnosticHistory.read(this));
    }
}
