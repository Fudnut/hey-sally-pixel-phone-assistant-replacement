package com.steve.spotifywakeprobe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.NoSuchElementException;

/** Fixed diagnostic categories; never copy exception messages into reports. */
final class CommandFailure {
    enum Reason { AUTH, TIMEOUT, NETWORK, NO_MATCH, REMOTE }
    private static final String PREFIX = "Spotify failure: ";

    static Reason reason(Throwable error) {
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            String type = error.getClass().getName();
            if (error instanceof SecurityException
                    || type.equals("com.spotify.android.appremote.api.error.UserNotAuthorizedException")
                    || type.equals("com.spotify.android.appremote.api.error.AuthenticationFailedException")
                    || type.equals("com.spotify.android.appremote.api.error.NotLoggedInException")) return Reason.AUTH;
            if (error instanceof SocketTimeoutException) return Reason.TIMEOUT;
            if (error instanceof IOException
                    || type.equals("com.spotify.android.appremote.api.error.OfflineModeException")) return Reason.NETWORK;
            if (error instanceof NoSuchElementException) return Reason.NO_MATCH;
        }
        return Reason.REMOTE;
    }

    static String report(Reason reason) { return PREFIX + reason; }

    static Reason code(String report) {
        if (report == null || !report.startsWith(PREFIX)) return Reason.REMOTE;
        try { return Reason.valueOf(report.substring(PREFIX.length())); }
        catch (IllegalArgumentException error) { return Reason.REMOTE; }
    }
}
