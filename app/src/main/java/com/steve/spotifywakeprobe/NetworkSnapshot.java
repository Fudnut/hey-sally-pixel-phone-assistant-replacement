/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

/** Network categories for diagnostics; never network names, addresses or location. */
final class NetworkSnapshot {
    private NetworkSnapshot() { }

    /** " net=wifi|cellular|other|none|unknown [validated= metered=] signal=0-4|unknown". */
    static String describe(Context context) {
        StringBuilder text = new StringBuilder(" net=");
        try {
            NetworkCapabilities capabilities = active(context);
            if (capabilities == null) text.append("none");
            else text.append(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "wifi"
                            : capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "cellular" : "other")
                    .append(" validated=").append(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
                    .append(" metered=").append(!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED));
        } catch (RuntimeException error) { text.append("unknown"); }
        text.append(" signal=");
        try {
            SignalStrength signal = context.getSystemService(TelephonyManager.class).getSignalStrength();
            text.append(signal == null ? "unknown" : String.valueOf(signal.getLevel()));
        } catch (RuntimeException error) { text.append("unknown"); }
        return text.toString();
    }

    /** A connected network Android has not validated: its internet is not working. */
    static boolean unvalidated(Context context) {
        try {
            NetworkCapabilities capabilities = active(context);
            return capabilities != null && !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException error) { return false; }
    }

    private static NetworkCapabilities active(Context context) {
        ConnectivityManager connectivity = context.getSystemService(ConnectivityManager.class);
        Network network = connectivity.getActiveNetwork();
        return network == null ? null : connectivity.getNetworkCapabilities(network);
    }
}
