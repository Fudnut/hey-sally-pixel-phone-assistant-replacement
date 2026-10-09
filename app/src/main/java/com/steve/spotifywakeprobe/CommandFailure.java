package com.steve.spotifywakeprobe;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.NoSuchElementException;

/** Safe diagnostic categories and setup detail; never copy exception messages. */
final class CommandFailure {
    enum Reason { AUTH, TIMEOUT, NETWORK, NO_MATCH, REMOTE }
    private static final String PREFIX = "Spotify failure: ";

    static final class TokenHttpException extends IOException {
        final int status;
        TokenHttpException(int status) {
            super("Spotify token request failed (HTTP " + status + ")");
            this.status = status;
        }
    }

    static Reason reason(Throwable error) {
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof TokenHttpException) {
                int status = ((TokenHttpException) error).status;
                return status == 400 || status == 401 || status == 403 ? Reason.AUTH : Reason.NETWORK;
            }
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

    static String setupDetail(Throwable error) {
        String detail = reason(error).name();
        for (int depth = 0; error != null && depth < 8; depth++, error = error.getCause()) {
            if (error instanceof TokenHttpException) {
                int status = ((TokenHttpException) error).status;
                return status >= 100 && status <= 599 ? detail + " (HTTP " + status + ")" : detail;
            }
        }
        return detail;
    }

    static String report(Reason reason) { return PREFIX + reason; }

    static Reason code(String report) {
        if (report == null || !report.startsWith(PREFIX)) return Reason.REMOTE;
        try { return Reason.valueOf(report.substring(PREFIX.length())); }
        catch (IllegalArgumentException error) { return Reason.REMOTE; }
    }
}
