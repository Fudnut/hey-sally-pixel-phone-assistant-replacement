package com.steve.spotifywakeprobe;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.function.Consumer;

import javax.crypto.Cipher;
import javax.crypto.AEADBadTagException;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SpotifyOAuth {
    static final String REDIRECT = "http://127.0.0.1:8765/callback";
    private static final String KEY = "spotify_tokens";
    private SpotifyOAuth() {}

    static void authorize(Activity activity, String clientId, Consumer<String> report) {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(8765, 1, InetAddress.getByName("127.0.0.1"))) {
                server.setSoTimeout(120000);
                String verifier = random(48);
                String state = random(24);
                String challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)),
                        Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
                Uri auth = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
                        .appendQueryParameter("response_type", "code")
                        .appendQueryParameter("client_id", clientId)
                        .appendQueryParameter("redirect_uri", REDIRECT)
                        .appendQueryParameter("scope", "app-remote-control playlist-read-private")
                        .appendQueryParameter("state", state)
                        .appendQueryParameter("code_challenge_method", "S256")
                        .appendQueryParameter("code_challenge", challenge).build();
                activity.runOnUiThread(() -> activity.startActivity(new Intent(Intent.ACTION_VIEW, auth)));
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    String request = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                            StandardCharsets.US_ASCII)).readLine();
                    if (request == null || !request.startsWith("GET /callback?"))
                        throw new CommandFailure.SetupException(CommandFailure.SetupReason.CALLBACK);
                    Uri callback = Uri.parse("http://127.0.0.1:8765" + request.split(" ")[1]);
                    if (!state.equals(callback.getQueryParameter("state")))
                        throw new CommandFailure.SetupException(CommandFailure.SetupReason.STATE_MISMATCH);
                    String code = callback.getQueryParameter("code");
                    String message = code == null ? "Spotify authorization was declined" :
                            "Spotify authorization received. Return to the app.";
                    byte[] html = ("<html><body>" + message + "</body></html>")
                            .getBytes(StandardCharsets.UTF_8);
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\n" +
                            "Content-Length: " + html.length + "\r\nConnection: close\r\n\r\n")
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().write(html);
                    if (code == null) throw new CommandFailure.SetupException(CommandFailure.SetupReason.DECLINED);
                    JSONObject token = tokenRequest("grant_type=authorization_code&code=" + enc(code) +
                            "&redirect_uri=" + enc(REDIRECT) + "&client_id=" + enc(clientId) +
                            "&code_verifier=" + enc(verifier));
                    save(activity, token);
                    post(report, "Spotify search and private playlists authorized");
                }
            } catch (Exception error) {
                post(report, "Spotify authorization failed: " + CommandFailure.setupDetail(error));
            }
        }, "SpotifyOAuth").start();
    }

    static synchronized String accessToken(Context context, String clientId) throws Exception {
        var prefs = context.getSharedPreferences(KEY, Context.MODE_PRIVATE);
        String token;
        try {
            token = decrypt(prefs.getString("access", null));
        } catch (AEADBadTagException | IllegalArgumentException error) {
            prefs.edit().remove("access").remove("refresh").remove("expires").apply();
            throw new SecurityException("Authorize named music and private playlists again", error);
        }
        if (token != null && System.currentTimeMillis() < prefs.getLong("expires", 0) - 60000)
            return token;
        String refresh;
        try {
            refresh = decrypt(prefs.getString("refresh", null));
        } catch (AEADBadTagException | IllegalArgumentException error) {
            prefs.edit().remove("access").remove("refresh").remove("expires").apply();
            throw new SecurityException("Authorize named music and private playlists again", error);
        }
        if (refresh == null) throw new SecurityException("Authorize Spotify search in the app first");
        JSONObject updated = tokenRequest("grant_type=refresh_token&refresh_token=" + enc(refresh) +
                "&client_id=" + enc(clientId));
        save(context, updated);
        return updated.getString("access_token");
    }

    private static JSONObject tokenRequest(String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://accounts.spotify.com/api/token")
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try {
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            if (status != 200) throw new CommandFailure.TokenHttpException(status);
            try (var in = connection.getInputStream()) {
                return new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }

    private static void save(Context context, JSONObject token) throws Exception {
        var prefs = context.getSharedPreferences(KEY, Context.MODE_PRIVATE);
        var edit = prefs.edit().putString("access", encrypt(token.getString("access_token")))
                .putLong("expires", System.currentTimeMillis() + token.getLong("expires_in") * 1000);
        if (token.has("refresh_token"))
            edit.putString("refresh", encrypt(token.getString("refresh_token")));
        edit.apply();
    }

    private static SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey key = (SecretKey) store.getKey(KEY, null);
        if (key != null) return key;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private static String encrypt(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] text = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] packet = new byte[cipher.getIV().length + text.length];
        System.arraycopy(cipher.getIV(), 0, packet, 0, cipher.getIV().length);
        System.arraycopy(text, 0, packet, cipher.getIV().length, text.length);
        return Base64.encodeToString(packet, Base64.NO_WRAP);
    }

    private static String decrypt(String packet) throws Exception {
        if (packet == null) return null;
        byte[] bytes = Base64.decode(packet, Base64.DEFAULT);
        if (bytes.length <= 12) throw new IllegalArgumentException("Invalid Spotify token data");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, bytes, 0, 12));
        return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
    }

    private static String random(int bytes) {
        byte[] value = new byte[bytes];
        new SecureRandom().nextBytes(value);
        return Base64.encodeToString(value, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    private static String enc(String text) throws Exception {
        return URLEncoder.encode(text, StandardCharsets.UTF_8.name());
    }

    private static void post(Consumer<String> report, String message) {
        new Handler(Looper.getMainLooper()).post(() -> report.accept(message));
    }
}
