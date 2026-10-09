package com.steve.spotifywakeprobe;

public final class PlaylistBrowseCheck {
    public static void main(String[] args) {
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
        command("play song 2", "PLAY", "2");
        command("play playlist two", "PLAY", "two");
        command("play two hearts", "PLAY", "two hearts");
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
        special("play radio Fleetwood Mac", "radio", "fleetwood mac");
        special("play Fleetwood Mac radio", "radio", "fleetwood mac");
        special("play radio by Ocie Elliott", "radio", "ocie elliott");
        special("play radio", "radio", "");
        if (!SpecialDestination.matches(VoiceCommand.parse("play made for you two"), "Daily Mix 2")
                || SpecialDestination.matches(VoiceCommand.parse("play made for you two"), "Daily Mix 20")
                || !SpecialDestination.matches(VoiceCommand.parse("play radio Beyonce"), "Beyoncé Radio")
                || SpecialDestination.matches(VoiceCommand.parse("play radio Sting"), "Interesting Radio")
                || SpecialDestination.matches(VoiceCommand.parse("play liked songs"), "My Liked Songs Copy")
                || SpecialDestination.matches(VoiceCommand.parse("play DJ"), "Best DJ Songs"))
            throw new AssertionError("Special content must match the exact destination, not a fuzzy song result");
        if (SpecialDestination.invalid(VoiceCommand.parse("play daily mix seven")) == null
                || SpecialDestination.invalid(VoiceCommand.parse("play radio")) == null
                || SpecialDestination.invalid(VoiceCommand.parse("play made for you six")) != null)
            throw new AssertionError("Bad special arguments need a spoken correction");
        for (String artist : new String[]{"Ocie Elliot", "Ocie Elliott", "Aussie Elliott", "Ossie Elliot"})
            if (!SpecialDestination.matches(VoiceCommand.parse("play radio " + artist), "Ocie Elliott Radio"))
                throw new AssertionError("Radio should reuse the known Ocie Elliott dictation variants");
        VoiceCommand unclearRadio = VoiceCommand.parse("play radio unrelated primary");
        java.util.List<String> radioAlternatives = java.util.List.of("Sting", "Ocie Elliott");
        if (SpecialDestination.matchRank(unclearRadio, "Ocie Elliott Radio", radioAlternatives) != 2
                || SpecialDestination.matchRank(unclearRadio, "Sting Radio", radioAlternatives) != 1
                || SpecialDestination.matchRank(unclearRadio, "Unrelated Primary Radio", radioAlternatives) != 0
                || SpecialDestination.matchRank(unclearRadio, "Interesting Radio", radioAlternatives) != Integer.MAX_VALUE
                || SpecialDestination.matchRank(VoiceCommand.parse("play DJ"), "Sting Radio", radioAlternatives) != Integer.MAX_VALUE)
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
        command("play artist Radiohead", "PLAY", "radiohead");
        command("play playlist Liked Songs", "PLAY", "liked songs");
        if (VoiceCommand.parse("play song by the beatles") != null)
            throw new AssertionError("Missing song title must still be rejected");
        if (VoiceCommand.parse("play two").acceptsPartial())
            throw new AssertionError("Number selection must wait for a final transcript");
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
