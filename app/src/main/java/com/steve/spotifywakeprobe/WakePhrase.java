package com.steve.spotifywakeprobe;

final class WakePhrase {
    private WakePhrase() { }

    static boolean matches(String transcript) {
        return transcript != null && transcript.trim().equalsIgnoreCase("hey spotify");
    }

    public static void main(String[] args) {
        if (!matches("hey spotify") || !matches(" Hey Spotify ")
                || matches("hey music") || matches("please hey spotify now")
                || matches("hey spotty") || matches("spotify"))
            throw new AssertionError("Wake phrase check failed");
    }
}
