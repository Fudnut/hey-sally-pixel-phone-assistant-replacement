package com.steve.spotifywakeprobe;

public final class PlaylistBrowseCheck {
    public static void main(String[] args) {
        for (String phrase : new String[]{"play number two please", "play number two.", "play number two please.", "play two please"})
            command(phrase, "PLAY_NUMBER", "2");
        for (String top : new String[]{"play number tree", "play number free"}) {
            VoiceCommand chosen = VoiceCommand.choose(java.util.List.of(top, "play number two"));
            if (chosen.kind != VoiceCommand.Kind.PLAY_NUMBER || !chosen.query.equals("2"))
                throw new AssertionError("Numeric alternative must replace only the bounded number confusion");
        }
        for (String title : new String[]{"number of the beast", "number one crush", "number 9 dream", "number theory"}) {
            VoiceCommand chosen = VoiceCommand.choose(java.util.List.of("play " + title, "play number two"));
            if (chosen.kind != VoiceCommand.Kind.PLAY || !chosen.query.equals(title))
                throw new AssertionError("N-best arbitration must preserve number-prefixed titles");
        }
        Throwable[] failures = {new SecurityException("private token/title"),
                new java.net.SocketTimeoutException("private host"), new java.io.IOException("private URL"),
                new java.util.NoSuchElementException("private playlist"), new RuntimeException("private SDK error")};
        CommandFailure.Reason[] reasons = {CommandFailure.Reason.AUTH, CommandFailure.Reason.TIMEOUT,
                CommandFailure.Reason.NETWORK, CommandFailure.Reason.NO_MATCH, CommandFailure.Reason.REMOTE};
        for (int i = 0; i < failures.length; i++) {
            if (CommandFailure.reason(failures[i]) != reasons[i]
                    || CommandFailure.reason(new RuntimeException("private wrapper", failures[i])) != reasons[i])
                throw new AssertionError("Failure reasons must follow exception types, including wrapped causes");
            String report = CommandFailure.report(reasons[i]);
            if (CommandFailure.code(report) != reasons[i] || report.contains("private"))
                throw new AssertionError("Failure reports must contain only an allowlisted reason");
        }
        if (CommandFailure.code("Spotify failure: AUTH private title") != CommandFailure.Reason.REMOTE
                || CommandFailure.code("private unstructured error") != CommandFailure.Reason.REMOTE
                || CommandFailure.code(null) != CommandFailure.Reason.REMOTE)
            throw new AssertionError("Untrusted or unknown report detail must not enter diagnostic reason codes");
        for (int i = 0; i < failures.length; i++) {
            if (!CommandFailure.setupDetail(failures[i]).equals(reasons[i].name()))
                throw new AssertionError("Setup detail must use fixed reasons without private exception messages");
        }
        for (int status : new int[]{400, 401, 403, 429, 500, 503, -1}) {
            Throwable error = new CommandFailure.TokenHttpException(status);
            CommandFailure.Reason reason = status == 400 || status == 401 || status == 403
                    ? CommandFailure.Reason.AUTH : CommandFailure.Reason.NETWORK;
            String detail = reason.name() + (status < 100 ? "" : " (HTTP " + status + ")");
            if (CommandFailure.reason(error) != reason || !CommandFailure.setupDetail(error).equals(detail)
                    || !CommandFailure.setupDetail(new RuntimeException("private wrapper", error)).equals(detail)
                    || !CommandFailure.report(CommandFailure.reason(error)).equals("Spotify failure: " + reason))
                throw new AssertionError("Only setup detail should retain a valid HTTP status; ordinary reports keep a fixed code");
        }
        WakeResults wakes = new WakeResults(100);
        for (int i = 0; i < 10000; i++) {
            if (wakes.result("private nearby speech", 200) != null || wakes.result("", 200) != null)
                throw new AssertionError("Routine results must not emit per-result events");
        }
        if (!"WAKE_RESULT class=accepted ms=600".equals(wakes.result("Hey Sally", 600)))
            throw new AssertionError("Accepted wake must still emit immediately");
        if (!"WAKE_RESULTS_SUMMARY other=10000 empty=10000 ms=300000".equals(wakes.drain(300100)))
            throw new AssertionError("Noise burst must become one non-content summary with counts");
        if (wakes.drain(600100) != null)
            throw new AssertionError("Empty windows must not consume diagnostic history");
        wakes.result("earlier private speech", 500);
        wakes.reset(600200);
        if (wakes.drain(600300) != null)
            throw new AssertionError("Fresh trial must discard earlier aggregated results");
        wakes.result("", 200);
        if (!"WAKE_RESULTS_SUMMARY other=0 empty=1 ms=200".equals(wakes.drain(600500)))
            throw new AssertionError("A partial final window must retain its actual duration");

        command("list my playlists", "LIST_PLAYLISTS", "");
        command("list playlists", "LIST_PLAYLISTS", "");
        command("list my play lists", "LIST_PLAYLISTS", "");
        command("list my playlist", "LIST_PLAYLISTS", "");
        command("List my playlists.", "LIST_PLAYLISTS", "");
        command("please list my playlists", "LIST_PLAYLISTS", "");
        command("list my play-list please", "LIST_PLAYLISTS", "");
        if (VoiceCommand.parse("don't list my playlists") != null
                || VoiceCommand.parse("list my playlist named example") != null)
            throw new AssertionError("Listing must still match the whole request");
        command("more playlists", "MORE_PLAYLISTS", "");
        command("more play lists.", "MORE_PLAYLISTS", "");
        for (String two : new String[]{"2", "two", "to", "too", "number two"})
            command("play " + two, "PLAY_NUMBER", "2");
        command("play twenty one", "PLAY_NUMBER", "21");
        command("play forty-five", "PLAY_NUMBER", "45");
        command("play one hundred and two", "PLAY_NUMBER", "102");
        command("play 0", "PLAY_NUMBER", "0");
        command("play 1001", "PLAY_NUMBER", "1001");
        command("next", "NEXT", "");
        command("play next song", "NEXT", "");
        command("open Spotify", "OPEN", "");
        for (String phrase : new String[]{"pause", "pause music", "pause the music", "pause please", "stop", "stop music"})
            command(phrase, "PAUSE", "");
        for (String phrase : new String[]{"resume", "resume music", "resume the music", "continue"})
            command(phrase, "RESUME", "");
        for (String phrase : new String[]{"next song", "next song please", "skip this song", "play the next song", "play a next track"})
            command(phrase, "NEXT", "");
        for (String phrase : new String[]{"previous song", "previous track", "play previous song", "play the previous track"})
            command(phrase, "PREVIOUS", "");
        command("play song Please Please Me", "PLAY", "please please me");
        command("play playlist The Music", "PLAY", "the music");
        command("play song Next Song", "PLAY", "next song");
        command("play music", "PLAY", "music");

        command("play song 2", "PLAY", "2");
        command("play playlist two", "PLAY", "two");
        command("play two hearts", "PLAY", "two hearts");
        for (String title : new String[]{"number of the beast", "number one crush", "number 9 dream"}) {
            command("play " + title, "PLAY", title);
            for (boolean browseActive : new boolean[]{false, true}) {
                VoiceCommand request = VoiceCommand.parse("play " + title).forPlaylistContext(browseActive);
                if (request.kind != VoiceCommand.Kind.PLAY || !request.query.equals(title)
                        || !request.type.equals("auto"))
                    throw new AssertionError("Number-prefixed titles must retain the full search query in either browse context");
            }
        }
        command("play -", "PLAY", "-");
        command("play --", "PLAY", "--");
        command("play song Stand by Me", "PLAY", "stand by me");
        command("play the song Stand by Me", "PLAY", "stand by me");
        if (!VoiceCommand.parse("play the song Stand by Me").type.equals("song"))
            throw new AssertionError("The song prefix must select song search");
        command("play the the", "PLAY", "the the");
        special("play Liked Songs", "liked", "");
        special("play my liked songs.", "liked", "");
        special("play Local Files", "local", "");
        special("play DJ", "dj", "");
        special("play Spotify DJ", "dj", "");
        special("play D J", "dj", "");
        for (String number : new String[]{"2", "02", "two", "to", "too", "zero two", "oh two", "number two"}) {
            special("play Daily Mix " + number, "mix", "2");
            special("play Made For You " + number, "mix", "2");
        }
        special("play made for you 06", "mix", "6");
        special("play daily mix seven", "mix", "7");
        special("play daily mix", "mix", "");
        special("play daily mix nonsense", "mix", "");
        special("play radio Example Group", "radio", "example group");
        special("play Example Group radio", "radio", "example group");
        special("play radio by Example Artist", "radio", "example artist");
        special("play radio", "radio", "");
        if (!SpecialDestination.matches(VoiceCommand.parse("play made for you two"), "Daily Mix 2")
                || SpecialDestination.matches(VoiceCommand.parse("play made for you two"), "Daily Mix 20")
                || !SpecialDestination.matches(VoiceCommand.parse("play radio Cafe"), "Caf\u00e9 Radio")
                || SpecialDestination.matches(VoiceCommand.parse("play radio Example Artist"), "Unrelated Radio")
                || SpecialDestination.matches(VoiceCommand.parse("play liked songs"), "My Liked Songs Copy")
                || SpecialDestination.matches(VoiceCommand.parse("play DJ"), "Best DJ Songs"))
            throw new AssertionError("Special content must match the exact destination, not a fuzzy song result");
        if (SpecialDestination.invalid(VoiceCommand.parse("play daily mix seven")) == null
                || SpecialDestination.invalid(VoiceCommand.parse("play radio")) == null
                || SpecialDestination.invalid(VoiceCommand.parse("play made for you six")) != null)
            throw new AssertionError("Bad special arguments need a spoken correction");
        VoiceCommand unclearRadio = VoiceCommand.parse("play radio unrelated primary");
        java.util.List<String> radioAlternatives = java.util.List.of("Example Artist", "Example Band");
        if (SpecialDestination.matchRank(unclearRadio, "Example Band Radio", radioAlternatives) != 2
                || SpecialDestination.matchRank(unclearRadio, "Example Artist Radio", radioAlternatives) != 1
                || SpecialDestination.matchRank(unclearRadio, "Unrelated Primary Radio", radioAlternatives) != 0
                || SpecialDestination.matchRank(unclearRadio, "Unrelated Radio", radioAlternatives) != Integer.MAX_VALUE
                || SpecialDestination.matchRank(VoiceCommand.parse("play DJ"), "Example Artist Radio", radioAlternatives) != Integer.MAX_VALUE)
            throw new AssertionError("Radio alternatives need ranked exact matching with primary priority and no cross-command fallback");
        SpecialDestination.Matches<String> radioChoices = new SpecialDestination.Matches<>();
        radioChoices.add("native-uri", "native item", 2, false);
        if (!radioChoices.needsLibrary())
            throw new AssertionError("A lower-ranked recommendation must not bypass a better saved-library match");
        radioChoices.add("saved-uri", "saved item", 0, true);
        if (!radioChoices.item().equals("saved item") || !radioChoices.fromLibrary()
                || radioChoices.needsLibrary() || radioChoices.size() != 1)
            throw new AssertionError("The primary library match must replace the native alternative");
        SpecialDestination.Matches<String> nativeFallback = new SpecialDestination.Matches<>();
        nativeFallback.add("same-uri", "original native item", 1, false);
        nativeFallback.add("same-uri", "library duplicate", 1, true);
        nativeFallback.add("lower-uri", "less likely saved item", 2, true);
        if (!nativeFallback.item().equals("original native item") || nativeFallback.fromLibrary() || nativeFallback.size() != 1)
            throw new AssertionError("A winning native item must retain its original playback API and deduplicate URIs");
        nativeFallback.add("different-uri", "equally likely item", 1, true);
        if (nativeFallback.size() != 2) throw new AssertionError("Equal-rank distinct playlists must remain ambiguous");
        command("play song DJ", "PLAY", "dj");
        command("play artist Radioband", "PLAY", "radioband");
        command("play playlist Liked Songs", "PLAY", "liked songs");
        if (VoiceCommand.parse("play song by example band") != null)
            throw new AssertionError("Missing song title must still be rejected");
        if (VoiceCommand.parse("play two").acceptsPartial())
            throw new AssertionError("Number selection must wait for a final transcript");
        PlaylistBrowse numericBrowse = new PlaylistBrowse();
        for (String title : new String[]{"one", "seven", "ten", "2"}) {
            VoiceCommand song = VoiceCommand.parse("play " + title)
                    .forPlaylistContext(numericBrowse.isActive(100));
            if (song.kind != VoiceCommand.Kind.PLAY || !song.type.equals("auto") || !song.query.equals(title))
                throw new AssertionError("Without playlist browsing a numeric title must search using the spoken words");
        }
        for (String phrase : new String[]{"play number two", "play number 2"}) {
            VoiceCommand explicit = VoiceCommand.parse(phrase).forPlaylistContext(numericBrowse.isActive(100));
            if (explicit.kind != VoiceCommand.Kind.PLAY_NUMBER || !explicit.query.equals("2"))
                throw new AssertionError("Explicit number requests must retain playlist intent without a browse");
            try {
                numericBrowse.select(Integer.parseInt(explicit.query), 100);
                throw new AssertionError("Explicit selection without a browse must request a list first");
            } catch (IllegalStateException expected) {
                if (!expected.getMessage().contains("list my playlists first")) throw expected;
            }
        }
        numericBrowse.replace(java.util.List.of(new PlaylistBrowse.Entry("Example", "uri1"),
                new PlaylistBrowse.Entry("Other", "uri2")), 100);
        numericBrowse.nextPage(101);
        VoiceCommand selection = VoiceCommand.parse("play two").forPlaylistContext(numericBrowse.isActive(102));
        if (selection.kind != VoiceCommand.Kind.PLAY_NUMBER
                || !numericBrowse.select(Integer.parseInt(selection.query), 102).uri.equals("uri2"))
            throw new AssertionError("Active playlist browsing must keep numbered selection");
        VoiceCommand expired = VoiceCommand.parse("play two")
                .forPlaylistContext(numericBrowse.isActive(100 + PlaylistBrowse.EXPIRY_MS));
        if (expired.kind != VoiceCommand.Kind.PLAY || !expired.query.equals("two"))
            throw new AssertionError("Expired browse numbers no longer select a playlist");
        VoiceCommand explicitExpired = VoiceCommand.parse("play number two")
                .forPlaylistContext(numericBrowse.isActive(100 + PlaylistBrowse.EXPIRY_MS));
        if (explicitExpired.kind != VoiceCommand.Kind.PLAY_NUMBER)
            throw new AssertionError("Explicit selection must retain playlist intent after expiry");
        session();
        for (String region : new String[]{"US", "AU", "GB", "NZ"}) {
            java.util.Locale phone = java.util.Locale.forLanguageTag("en-" + region);
            if (!CommandLanguage.resolve("phone", phone).equals("en-" + region))
                throw new AssertionError("Phone language must follow the configured English dialect");
            if (!CommandLanguage.resolve("en-US", phone).equals("en-US"))
                throw new AssertionError("An explicit dialect must not change with the phone locale");
        }
        if (!CommandLanguage.resolve("phone", java.util.Locale.FRENCH).equals("en-US")
                || !CommandLanguage.resolve("invalid", java.util.Locale.UK).equals("en-US"))
            throw new AssertionError("Unsupported settings must preserve English commands");
        System.out.println("Command parser, language settings, playlist paging, snapshot and expiry checks passed");
    }

    private static void session() {
        PlaylistBrowse browse = new PlaylistBrowse();
        java.util.ArrayList<PlaylistBrowse.Entry> source = new java.util.ArrayList<>();
        for (int i = 1; i <= 12; i++) source.add(new PlaylistBrowse.Entry("List " + i, "uri" + i));
        browse.replace(source, 100);
        source.clear();
        String first = browse.nextPage(101);
        if (!first.contains("1. List 1.") || !first.contains("5. List 5.") || first.contains("6. List 6."))
            throw new AssertionError("First page must contain five numbered entries");
        if (!browse.select(2, 102).uri.equals("uri2")) throw new AssertionError("Snapshot selection");
        rejects(() -> browse.select(6, 103));
        rejects(() -> browse.select(0, 103));
        String second = browse.nextPage(104);
        if (!second.contains("6. List 6.") || !second.contains("10. List 10."))
            throw new AssertionError("Numbers must continue across pages");
        if (!browse.select(2, 105).uri.equals("uri2")) throw new AssertionError("Numbers changed");
        browse.readCompleted(1000);
        browse.select(2, 1000 + PlaylistBrowse.EXPIRY_MS - 1);
        rejects(() -> browse.select(2, 1000 + PlaylistBrowse.EXPIRY_MS));
        rejects(() -> browse.nextPage(1000 + PlaylistBrowse.EXPIRY_MS));
        browse.replace(java.util.List.of(new PlaylistBrowse.Entry("Replacement", "new")), 200000);
        browse.nextPage(200001);
        if (!browse.select(1, 200002).uri.equals("new")) throw new AssertionError("Fresh list replaces old list");
        rejects(() -> browse.nextPage(200003));
        browse.clear();
        rejects(() -> browse.select(1, 200004));
        browse.replace(java.util.List.of(), 300000);
        if (!browse.nextPage(300001).contains("No playlists")) throw new AssertionError("Empty library reply");
    }

    private static void rejects(Runnable action) {
        try { action.run(); }
        catch (IllegalStateException expected) { return; }
        throw new AssertionError("Invalid or expired selection must be rejected");
    }

    private static void command(String words, String kind, String query) {
        VoiceCommand actual = VoiceCommand.parse(words);
        if (actual == null || !actual.kind.name().equals(kind) || !actual.query.equals(query))
            throw new AssertionError(words + ": expected " + kind + " " + query + ", got " + actual);
    }

    private static void special(String words, String type, String query) {
        command(words, "PLAY_SPECIAL", query);
        VoiceCommand actual = VoiceCommand.parse(words);
        if (!actual.type.equals(type) || actual.acceptsPartial())
            throw new AssertionError("Special destinations need the correct route and a finalized transcript");
    }
}
