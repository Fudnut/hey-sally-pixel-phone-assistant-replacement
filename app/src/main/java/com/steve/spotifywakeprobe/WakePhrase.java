package com.steve.spotifywakeprobe;

final class WakePhrase {
    private WakePhrase() { }

    static boolean matches(String transcript) {
        return transcript != null && transcript.trim().equalsIgnoreCase("hey sally");
    }

    public static void main(String[] args) {
        if (!matches("hey sally") || !matches(" Hey Sally ")
                || matches("hey spotify") || matches("hey music") || matches("please hey sally now")
                || matches("hey salad") || matches("sally") || matches("") || matches(null))
            throw new AssertionError("Wake phrase check failed");
    }
}
