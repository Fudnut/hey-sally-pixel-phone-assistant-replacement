package com.steve.spotifywakeprobe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;

import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.types.Empty;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.io.IOException;
import java.util.NoSuchElementException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

final class SpotifyController {
    private SpotifyController() {}

    static void authorizeRemote(Activity activity, String clientId, Consumer<String> report) {
        AtomicBoolean finished = new AtomicBoolean();
        connect(activity, clientId, true, remote -> {
            if (finished.compareAndSet(false, true)) report.accept("Spotify App Remote authorized");
            SpotifyAppRemote.disconnect(remote);
        }, message -> report.accept("Spotify playback authorization failed. Check your Client ID and Spotify, then try again."), finished);
    }

    static void execute(Context context, VoiceCommand command, List<String> playlistNames,
                        Consumer<String> report) {
        if (command.kind == VoiceCommand.Kind.OPEN) {
            Intent launch = context.getPackageManager().getLaunchIntentForPackage("com.spotify.music");
            if (launch == null) { report.accept(CommandFailure.report(CommandFailure.Reason.REMOTE)); return; }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { context.startActivity(launch); report.accept("Opening Spotify"); }
            catch (RuntimeException error) { report.accept(CommandFailure.report(CommandFailure.reason(error))); }
            return;
        }
        String clientId = context.getSharedPreferences("spotify", Context.MODE_PRIVATE)
                .getString("clientId", "").trim();
        if (clientId.isEmpty()) { report.accept(CommandFailure.report(CommandFailure.Reason.AUTH)); return; }
        if (command.kind != VoiceCommand.Kind.PLAY) {
            play(context, clientId, command, null, report);
            return;
        }
        new Thread(() -> {
            try {
                String uri = resolve(context, clientId, command, playlistNames);
                new Handler(Looper.getMainLooper()).post(() -> play(context, clientId, command, uri, report));
            } catch (Exception error) {
                new Handler(Looper.getMainLooper()).post(() -> report.accept(
                        CommandFailure.report(CommandFailure.reason(error))));
            }
        }, "SpotifySearch").start();
    }

    static void playPlaylist(Context context, String uri, Consumer<String> report) {
        String clientId = context.getSharedPreferences("spotify", Context.MODE_PRIVATE)
                .getString("clientId", "").trim();
        if (clientId.isEmpty()) { report.accept(CommandFailure.report(CommandFailure.Reason.AUTH)); return; }
        play(context, clientId, VoiceCommand.selectedPlaylist(), uri, report);
    }

    static Thread fetchPlaylists(Context context, Consumer<List<PlaylistBrowse.Entry>> ready,
                               Consumer<String> failed) {
        Handler main = new Handler(Looper.getMainLooper());
        Thread worker = new Thread(() -> {
            try {
                String clientId = context.getSharedPreferences("spotify", Context.MODE_PRIVATE)
                        .getString("clientId", "").trim();
                String token = SpotifyOAuth.accessToken(context, clientId);
                ArrayList<PlaylistBrowse.Entry> playlists = new ArrayList<>();
                int offset = 0;
                while (offset < 1000 && !Thread.currentThread().isInterrupted()) {
                    JSONObject page = playlistPage(token, offset);
                    JSONArray items = page.getJSONArray("items");
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;
                        String uri = item.optString("uri");
                        if (!uri.matches("spotify:playlist:[a-zA-Z0-9]+")) continue;
                        String name = item.optString("name").trim();
                        playlists.add(new PlaylistBrowse.Entry(name.isEmpty() ? "Untitled playlist" : name, uri));
                    }
                    offset += items.length();
                    if (items.length() == 0 || page.isNull("next") || offset >= page.optInt("total", 0)) break;
                }
                if (!Thread.currentThread().isInterrupted()) main.post(() -> ready.accept(playlists));
            } catch (Exception error) {
                // Never include a server response, token, or playlist title in a spoken/logged error.
                main.post(() -> failed.accept("I couldn't load your playlists. Check your connection and Spotify authorization in the app."));
            }
        }, "SpotifyPlaylists");
        worker.start();
        return worker;
    }

    private static JSONObject playlistPage(String token, int offset) throws Exception {
        Uri url = Uri.parse("https://api.spotify.com/v1/me/playlists").buildUpon()
                .appendQueryParameter("limit", "50")
                .appendQueryParameter("offset", Integer.toString(offset)).build();
        return get(token, url);
    }

    private static void play(Context context, String clientId, VoiceCommand command,
                             String uri, Consumer<String> report) {
        AtomicBoolean finished = new AtomicBoolean();
        connect(context, clientId, false, remote -> {
            if (command.kind == VoiceCommand.Kind.PREVIOUS) {
                previous(remote, report, finished);
                return;
            }
            CallResult<Empty> result;
            switch (command.kind) {
                case PAUSE: result = remote.getPlayerApi().pause(); break;
                case RESUME: result = remote.getPlayerApi().resume(); break;
                case NEXT: result = remote.getPlayerApi().skipNext(); break;
                case PLAY: result = remote.getPlayerApi().play(uri); break;
                default: SpotifyAppRemote.disconnect(remote); return;
            }
            result.setResultCallback(ignored -> {
                if (command.kind == VoiceCommand.Kind.PLAY
                        || command.kind == VoiceCommand.Kind.RESUME) {
                    // Android 17 muted cold App Remote playback until Spotify received a media Play event.
                    dispatchMediaPlay(context);
                }
                if (finished.compareAndSet(false, true)) report.accept("Spotify: " + command);
                SpotifyAppRemote.disconnect(remote);
            });
            result.setErrorCallback(error -> {
                if (finished.compareAndSet(false, true))
                    report.accept(CommandFailure.report(CommandFailure.reason(error)));
                SpotifyAppRemote.disconnect(remote);
            });
        }, report, finished);
    }

    static void dispatchMediaPlay(Context context) {
        AudioManager audio = context.getSystemService(AudioManager.class);
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY));
        audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY));
        Log.i("SpotifyWakeProbe", "MEDIA_PLAY_KEY_DISPATCHED");
    }

    private static void previous(SpotifyAppRemote remote, Consumer<String> report,
                                 AtomicBoolean finished) {
        remote.getPlayerApi().getPlayerState().setResultCallback(state -> {
            int skips = state.playbackPosition >= 3000 ? 2 : 1;
            Log.i("SpotifyWakeProbe", "PREVIOUS_POSITION " + state.playbackPosition
                    + " skips=" + skips);
            skipPrevious(remote, skips, report, finished);
        }).setErrorCallback(error -> {
            if (finished.compareAndSet(false, true))
                report.accept(CommandFailure.report(CommandFailure.reason(error)));
            SpotifyAppRemote.disconnect(remote);
        });
    }

    private static void skipPrevious(SpotifyAppRemote remote, int remaining,
                                     Consumer<String> report, AtomicBoolean finished) {
        remote.getPlayerApi().skipPrevious().setResultCallback(ignored -> {
            if (remaining > 1) skipPrevious(remote, remaining - 1, report, finished);
            else {
                if (finished.compareAndSet(false, true)) report.accept("Spotify: PREVIOUS");
                SpotifyAppRemote.disconnect(remote);
            }
        }).setErrorCallback(error -> {
            if (finished.compareAndSet(false, true))
                report.accept(CommandFailure.report(CommandFailure.reason(error)));
            SpotifyAppRemote.disconnect(remote);
        });
    }

    private static void connect(Context context, String clientId, boolean showAuthorization,
                                Consumer<SpotifyAppRemote> ready, Consumer<String> report,
                                AtomicBoolean finished) {
        ConnectionParams params = new ConnectionParams.Builder(clientId)
                .setRedirectUri(SpotifyOAuth.REDIRECT).showAuthView(showAuthorization).build();
        Handler main = new Handler(Looper.getMainLooper());
        AtomicBoolean connecting = new AtomicBoolean(true);
        long startedAt = SystemClock.elapsedRealtime();
        Runnable timeout = () -> {
            if (connecting.compareAndSet(true, false) && finished.compareAndSet(false, true)) {
                connectionStatus(context, startedAt, showAuthorization, "TIMEOUT");
                Log.w("SpotifyWakeProbe", "SPOTIFY_CONNECT_TIMEOUT");
                report.accept(CommandFailure.report(CommandFailure.Reason.TIMEOUT));
            }
        };
        // Manual authorization needs time for consent; ordinary playback fails promptly.
        main.postDelayed(timeout, showAuthorization ? 120000 : 12000);
        Connector.ConnectionListener listener = new Connector.ConnectionListener() {
            @Override public void onConnected(SpotifyAppRemote remote) {
                main.post(() -> {
                    if (!connecting.compareAndSet(true, false) || finished.get()) {
                        connectionStatus(context, startedAt, showAuthorization, "IGNORED");
                        SpotifyAppRemote.disconnect(remote);
                        return;
                    }
                    main.removeCallbacks(timeout);
                    connectionStatus(context, startedAt, showAuthorization, "CONNECTED");
                    try { ready.accept(remote); }
                    catch (RuntimeException error) {
                        SpotifyAppRemote.disconnect(remote);
                        onFailure(error);
                    }
                });
            }
            @Override public void onFailure(Throwable error) {
                main.post(() -> {
                    connecting.set(false);
                    main.removeCallbacks(timeout);
                    if (!finished.compareAndSet(false, true)) return;
                    connectionStatus(context, startedAt, showAuthorization, "ERROR");
                    Log.e("SpotifyWakeProbe", "SPOTIFY_CONNECT_ERROR");
                    report.accept(CommandFailure.report(CommandFailure.reason(error)));
                });
            }
        };
        try { SpotifyAppRemote.connect(context, params, listener); }
        catch (RuntimeException error) { listener.onFailure(error); }
    }

    private static void connectionStatus(Context context, long startedAt, boolean authorization, String outcome) {
        String event = "SPOTIFY_CONNECT_RESULT " + outcome + " ms="
                + Math.max(0, SystemClock.elapsedRealtime() - startedAt)
                + " mode=" + (authorization ? "AUTH" : "PLAYBACK");
        Log.i("SpotifyWakeProbe", event);
        DiagnosticHistory.record(context, event);
    }

    private static String resolve(Context context, String clientId, VoiceCommand command,
                                  List<String> playlistNames) throws Exception {
        String token = SpotifyOAuth.accessToken(context, clientId);
        if ("playlist".equals(command.type)) {
            ArrayList<String> names = new ArrayList<>(playlistNames);
            if (!names.contains(command.query)) names.add(command.query);
            return libraryPlaylist(token, names);
        }
        String recognizedName = command.query;
        if ("artist".equals(command.type)) return artistTrack(token, recognizedName);
        if (!"song".equals(command.type)) {
            String artist = exactArtist(token, recognizedName);
            if (artist != null) return artistTrack(token, artist);
        }
        String query = command.query;
        int by = query.lastIndexOf(" by ");
        JSONArray plain = null;
        if (by > 0) {
            // The full phrase might be a title such as "Stand by Me".
            plain = search(token, query, "track", 10);
            for (int i = 0; i < plain.length(); i++) {
                JSONObject track = plain.getJSONObject(i);
                if (sameName(track.optString("name"), query)) return track.getString("uri");
            }
            query = "track:" + query.substring(0, by) + " artist:" + query.substring(by + 4);
        }
        JSONArray items = search(token, query, "track", 1);
        if (items.length() == 0 && plain != null) items = plain;
        if (items.length() == 0) throw new NoSuchElementException("No matching song");
        return items.getJSONObject(0).getString("uri");
    }

    private static String libraryPlaylist(String token, List<String> names) throws Exception {
        int offset = 0;
        JSONObject best = null;
        int bestDistance = Integer.MAX_VALUE;
        int secondDistance = Integer.MAX_VALUE;
        while (offset < 1000) {
            JSONObject page = playlistPage(token, offset);
            JSONArray items = page.getJSONArray("items");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String savedName = item.optString("name");
                for (String name : names) {
                    if (sameName(savedName, name)) {
                        Log.i("SpotifyWakeProbe", "PLAYLIST_RESOLVED exact");
                        return item.getString("uri");
                    }
                    int distance = distance(normalizedName(savedName).replace(" ", ""),
                            normalizedName(name).replace(" ", ""));
                    if (distance < bestDistance) {
                        secondDistance = bestDistance;
                        bestDistance = distance;
                        best = item;
                    } else if (distance < secondDistance && best != item) {
                        secondDistance = distance;
                    }
                }
            }
            offset += items.length();
            if (items.length() == 0 || page.isNull("next") || offset >= page.optInt("total", 0)) break;
        }
        if (best != null && bestDistance <= Math.max(2,
                (int) Math.ceil(normalizedName(best.optString("name")).replace(" ", "").length() * 0.4))
                && bestDistance < secondDistance) {
            Log.i("SpotifyWakeProbe", "PLAYLIST_RESOLVED fuzzy distance=" + bestDistance);
            return best.getString("uri");
        }
        throw new NoSuchElementException("No clear playlist match");
    }

    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++)
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            previous = current;
        }
        return previous[b.length()];
    }

    private static String exactArtist(String token, String name) throws Exception {
        JSONArray artists = search(token, name, "artist", 5);
        for (int i = 0; i < artists.length(); i++) {
            String found = artists.getJSONObject(i).optString("name");
            if (sameName(found, name)) return found;
        }
        return null;
    }

    private static String artistTrack(String token, String artist) throws Exception {
        JSONArray tracks = search(token, "artist:" + artist, "track", 10);
        int[] matching = new int[tracks.length()];
        int count = 0;
        for (int i = 0; i < tracks.length(); i++) {
            JSONObject track = tracks.getJSONObject(i);
            JSONArray artists = track.getJSONArray("artists");
            for (int j = 0; j < artists.length(); j++) {
                if (sameName(artists.getJSONObject(j).optString("name"), artist)) {
                    matching[count++] = i;
                    break;
                }
            }
        }
        if (count == 0) throw new NoSuchElementException("No songs by that artist");
        return tracks.getJSONObject(matching[ThreadLocalRandom.current().nextInt(count)])
                .getString("uri");
    }

    private static boolean sameName(String a, String b) {
        return normalizedName(a).equals(normalizedName(b));
    }

    private static String normalizedName(String name) {
        String value = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "")
                .trim().replaceAll("\\s+", " ");
        return value;
    }

    private static JSONArray search(String token, String query, String type, int limit) throws Exception {
        Uri url = Uri.parse("https://api.spotify.com/v1/search").buildUpon()
                .appendQueryParameter("q", query)
                .appendQueryParameter("type", type)
                .appendQueryParameter("limit", Integer.toString(limit)).build();
        return get(token, url).getJSONObject(type + "s").getJSONArray("items");
    }

    private static JSONObject get(String token, Uri url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url.toString()).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        try {
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new SecurityException(
                    "Access denied. Reauthorize named music and private playlists in the app");
            if (status != 200) throw new IOException("Spotify request failed");
            try (InputStream in = connection.getInputStream()) {
                return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }
}
