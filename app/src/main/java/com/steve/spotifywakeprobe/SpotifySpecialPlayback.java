package com.steve.spotifywakeprobe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.spotify.android.appremote.api.ConnectionParams;
import com.spotify.android.appremote.api.Connector;
import com.spotify.android.appremote.api.ContentApi;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.types.ListItem;
import com.spotify.protocol.types.ListItems;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.client.CallResult;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

/** One cancellable, bounded lookup/play operation using Spotify's own content items. */
final class SpotifySpecialPlayback {
    private static final int MAX_PAGES = 50;
    private final Context context;
    private final VoiceCommand command;
    private final List<String> alternatives;
    private final Consumer<String> completed; // null means playback succeeded; otherwise a safe spoken error
    private final Runnable beforePlay;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ArrayDeque<Page> pending = new ArrayDeque<>();
    private final HashSet<String> seen = new HashSet<>();
    private final SpecialDestination.Matches<ListItem> matches = new SpecialDestination.Matches<>();
    private final Runnable timeout = () -> finish("Spotify took too long. Please try again.");
    private SpotifyAppRemote remote;
    private Thread libraryFetch;
    private int pages;
    private boolean finished;

    private SpotifySpecialPlayback(Context context, VoiceCommand command, List<String> alternatives, Runnable beforePlay, Consumer<String> completed) {
        this.context = context.getApplicationContext();
        this.command = command;
        this.alternatives = List.copyOf(alternatives);
        this.completed = completed;
        this.beforePlay = beforePlay;
    }

    static Runnable start(Context context, VoiceCommand command, List<String> alternatives, Runnable beforePlay, Consumer<String> completed) {
        SpotifySpecialPlayback request = new SpotifySpecialPlayback(context, command, alternatives, beforePlay, completed);
        // Post so the caller receives its cancellation handle before even a synchronous error.
        request.main.post(request::connect);
        return request::cancel;
    }

    private void connect() {
        if (finished) return;
        String invalid = SpecialDestination.invalid(command);
        if (invalid != null) { finish(invalid); return; }
        String client = context.getSharedPreferences("spotify", Context.MODE_PRIVATE).getString("clientId", "").trim();
        if (client.isEmpty()) { finish("Set up Spotify in the assistant app first."); return; }
        main.postDelayed(timeout, 25000);
        ConnectionParams params = new ConnectionParams.Builder(client)
                .setRedirectUri(SpotifyOAuth.REDIRECT).showAuthView(false).build();
        SpotifyAppRemote.connect(context, params, new Connector.ConnectionListener() {
            @Override public void onConnected(SpotifyAppRemote connected) {
                if (finished) { SpotifyAppRemote.disconnect(connected); return; }
                remote = connected;
                remote.getContentApi().getRecommendedContentItems(ContentApi.ContentType.DEFAULT)
                        .setResultCallback(items -> { scan(items); next(); })
                        .setErrorCallback(error -> libraryOrUnavailable());
            }
            @Override public void onFailure(Throwable error) {
                finish("I couldn't connect to Spotify. Check your connection and Spotify authorization in the app.");
            }
        });
    }

    private void scan(ListItems page) {
        if (finished || page.items == null) return;
        for (ListItem item : page.items) {
            String uri = item.uri == null ? "" : item.uri;
            // Ignore songs/albums with names such as DJ; these commands target collections.
            boolean collection = uri.startsWith("spotify:playlist:") || uri.startsWith("spotify:collection:")
                    || (uri.startsWith("spotify:user:") && uri.contains(":collection"));
            if (item.playable && collection) consider(item, false);
            if (item.hasChildren && seen.add(item.id + "|" + uri) && pending.size() < MAX_PAGES)
                pending.add(new Page(item, 0));
        }
    }

    private void consider(ListItem item, boolean fromLibrary) {
        int rank = SpecialDestination.matchRank(command, item.title, alternatives);
        matches.add(item.uri, item, rank, fromLibrary);
    }

    private void next() {
        if (finished) return;
        if (pending.isEmpty() || pages >= MAX_PAGES) {
            DiagnosticHistory.record(context, "SPECIAL_LOOKUP " + command.type + " matches=" + matches.size() + " pages=" + pages);
            if (matches.needsLibrary()) libraryOrUnavailable();
            else playMatch();
            return;
        }
        Page page = pending.remove();
        pages++;
        remote.getContentApi().getChildrenOfItem(page.item, 50, page.offset).setResultCallback(items -> {
            if (finished) return;
            scan(items);
            int count = items.items == null ? 0 : items.items.length;
            if (count > 0 && page.offset + count < items.total && pending.size() < MAX_PAGES)
                pending.add(new Page(page.item, page.offset + count));
            next();
        }).setErrorCallback(error -> next());
    }

    private void libraryOrUnavailable() {
        if (finished) return;
        if (!(command.type.equals("radio") || command.type.equals("mix") || command.type.equals("local"))) {
            finish(SpecialDestination.unavailable(command));
            return;
        }
        libraryFetch = SpotifyController.fetchPlaylists(context, entries -> {
            if (finished) return;
            for (PlaylistBrowse.Entry entry : entries) {
                consider(new ListItem(entry.uri, entry.uri, null, entry.name, "", true, false), true);
            }
            DiagnosticHistory.record(context, "SPECIAL_LIBRARY " + command.type + " matches=" + matches.size());
            if (matches.size() == 0) finish(SpecialDestination.unavailable(command));
            else playMatch();
        }, this::finish);
    }

    private void playMatch() {
        if (finished) return;
        if (matches.size() != 1) {
            finish("Several playlists match. Give your chosen playlist a unique name, then say play playlist followed by that name.");
            return;
        }
        ListItem item = matches.item();
        DiagnosticHistory.record(context, "SPECIAL_MATCH_OPTION rank=" + matches.rank);
        beforePlay.run();
        if (finished) return;
        CallResult<Empty> play = matches.fromLibrary() ? remote.getPlayerApi().play(item.uri)
                : remote.getContentApi().playContentItem(item);
        play.setResultCallback(ignored -> {
            if (finished) return;
            SpotifyController.dispatchMediaPlay(context);
            finish(null);
        }).setErrorCallback(error -> finish("Spotify couldn't play that collection. Check it in Spotify, then try again."));
    }

    private void finish(String error) {
        if (finished) return;
        cancel();
        completed.accept(error);
    }

    private void cancel() {
        if (finished) return;
        finished = true;
        main.removeCallbacks(timeout);
        if (libraryFetch != null) libraryFetch.interrupt();
        if (remote != null) SpotifyAppRemote.disconnect(remote);
        pending.clear();
    }

    private static final class Page {
        final ListItem item;
        final int offset;
        Page(ListItem item, int offset) { this.item = item; this.offset = offset; }
    }
}
