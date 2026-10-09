package com.steve.spotifywakeprobe;

import java.util.Locale;

final class VoiceCommand {
    enum Kind { OPEN, PAUSE, RESUME, NEXT, PREVIOUS, PLAY, LIST_PLAYLISTS, MORE_PLAYLISTS, PLAY_NUMBER, PLAY_SPECIAL }
    final Kind kind;
    final String query;
    final String type;
    private final String spokenQuery;

    private VoiceCommand(Kind kind, String query, String type) {
        this(kind, query, type, query);
    }

    private VoiceCommand(Kind kind, String query, String type, String spokenQuery) {
        this.spokenQuery = spokenQuery;
        this.kind = kind;
        this.query = query;
        this.type = type;
    }

    static VoiceCommand parse(String spoken) {
        if (spoken == null) return null;
        String words = spoken.toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
        String listing = words.replaceAll("\\p{Punct}", " ").trim().replaceAll("\\s+", " ")
                .replaceAll("\\bplay lists?\\b", "playlists");
        if (listing.matches("(?:please )?list (?:my )?playlists?(?: please)?")) {
            boolean exact = words.equals("list playlists") || words.equals("list my playlists");
            return new VoiceCommand(Kind.LIST_PLAYLISTS, "", exact ? "" : "normalized");
        }
        if (listing.matches("(?:please )?more playlists?(?: please)?"))
            return new VoiceCommand(Kind.MORE_PLAYLISTS, "", words.equals("more playlists") ? "" : "normalized");
        switch (words) {
            case "open spotify": return new VoiceCommand(Kind.OPEN, "", "");
            case "pause": case "pause music": return new VoiceCommand(Kind.PAUSE, "", "");
            case "resume": case "resume music": case "continue":
            // Observed Vosk small-model transcripts for spoken “resume” on the test Pixel.
            case "regime": case "review": return new VoiceCommand(Kind.RESUME, "", "");
            case "next": case "next song": case "next track":
            case "play next song": case "play next track": case "skip": return new VoiceCommand(Kind.NEXT, "", "");
            case "previous": case "previous song": case "previous track":
            case "play previous song": case "play previous track": return new VoiceCommand(Kind.PREVIOUS, "", "");
            default: break;
        }
        if (!words.startsWith("play ")) return null;
        String query = words.substring(5).trim();
        if (query.startsWith("the song ")) query = query.substring(4);
        String type = "auto";
        if (query.startsWith("artist ")) { type = "artist"; query = query.substring(7).trim(); }
        else if (query.startsWith("playlist ")) { type = "playlist"; query = query.substring(9).trim(); }
        else if (query.startsWith("my playlist ")) { type = "playlist"; query = query.substring(12).trim(); }
        else if (query.startsWith("song ")) { type = "song"; query = query.substring(5).trim(); }
        if ("auto".equals(type)) {
            VoiceCommand special = special(query);
            if (special != null) return special;
            Integer number = spokenNumber(query.startsWith("number ") ? query.substring(7) : query);
            if (number != null) return new VoiceCommand(Kind.PLAY_NUMBER, number.toString(), "", query);
            if (query.startsWith("number ")) return null;
        }
        if (query.isEmpty() || (("song".equals(type) || "auto".equals(type)) && query.startsWith("by "))) return null;
        return new VoiceCommand(Kind.PLAY, query, type);
    }

    VoiceCommand forPlaylistContext(boolean browseActive) {
        return kind == Kind.PLAY_NUMBER && !browseActive
                ? new VoiceCommand(Kind.PLAY, spokenQuery, "auto") : this;
    }

    static VoiceCommand selectedPlaylist() { return new VoiceCommand(Kind.PLAY, "", "playlist"); }

    private static VoiceCommand special(String query) {
        query = query.replaceAll("[.!?,]+$", "").trim();
        switch (query) {
            case "liked songs": case "my liked songs": return new VoiceCommand(Kind.PLAY_SPECIAL, "", "liked");
            case "local files": case "my local files": return new VoiceCommand(Kind.PLAY_SPECIAL, "", "local");
            case "dj": case "d j": case "spotify dj": case "the dj":
                return new VoiceCommand(Kind.PLAY_SPECIAL, "", "dj");
            default: break;
        }
        if (query.matches("(?:daily mix|made for you)(?: .*)?")) {
            String number = query.replaceFirst("^(?:daily mix|made for you)\\s*", "")
                    .replaceFirst("^(?:number|zero|oh) ", "");
            Integer parsed = spokenNumber(number);
            return new VoiceCommand(Kind.PLAY_SPECIAL, parsed == null ? "" : parsed.toString(), "mix");
        }
        if (query.equals("radio") || query.startsWith("radio "))
            return new VoiceCommand(Kind.PLAY_SPECIAL, query.substring(5).trim()
                    .replaceFirst("^(?:by |for )", "").replaceFirst("^artist ", ""), "radio");
        if (query.endsWith(" radio"))
            return new VoiceCommand(Kind.PLAY_SPECIAL, query.substring(0, query.length() - 6).trim(), "radio");
        return null;
    }

    boolean acceptsPartial() {
        return kind == Kind.OPEN || kind == Kind.PAUSE || kind == Kind.RESUME
                || kind == Kind.NEXT || kind == Kind.PREVIOUS;
    }

    private static Integer spokenNumber(String text) {
        if (text.matches("[+-]?\\d+")) {
            try { return Integer.parseInt(text); }
            catch (NumberFormatException tooLarge) { return Integer.MAX_VALUE; }
        }
        text = text.replace('-', ' ').trim().replaceAll("\\s+", " ");
        if (text.isEmpty()) return null;
        if (text.equals("one thousand")) return 1000;
        String[] parts = text.split(" ");
        int result = 0;
        int index = 0;
        int first = smallNumber(parts[0]);
        if (parts.length >= 2 && first > 0 && first < 10 && parts[1].equals("hundred")) {
            result = first * 100;
            index = 2;
            if (index == parts.length) return result;
            if (parts[index].equals("and")) index++;
        }
        if (index >= parts.length) return null;
        int value = smallNumber(parts[index++]);
        if (value < 0) return null;
        result += value;
        if (index < parts.length && value >= 20 && value % 10 == 0) {
            int unit = smallNumber(parts[index++]);
            if (unit < 1 || unit > 9) return null;
            result += unit;
        }
        return index == parts.length ? result : null;
    }

    private static int smallNumber(String text) {
        if (text.equals("to") || text.equals("too")) return 2;
        if (text.equals("for")) return 4;
        if (text.equals("won")) return 1;
        String[] names = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
                "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
                "seventeen", "eighteen", "nineteen", "twenty", "thirty", "forty", "fifty",
                "sixty", "seventy", "eighty", "ninety"};
        for (int i = 0; i < names.length; i++) if (names[i].equals(text))
            return i < 20 ? i : (i - 18) * 10;
        return -1;
    }

    @Override public String toString() {
        return kind == Kind.PLAY ? "PLAY " + type + " " + query : kind.name();
    }
}
