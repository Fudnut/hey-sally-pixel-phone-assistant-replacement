/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.Looper;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/** Reports whether any Wi-Fi network or Bluetooth audio output is connected. Reads no network names or locations. */
final class PlaceMonitor implements AutoCloseable {
    private final Context context;
    private final Consumer<Boolean> changed;
    private final ConnectivityManager connectivity;
    private final AudioManager audio;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Network> wifi = new HashSet<>();
    private boolean started;
    private boolean bluetooth;
    private boolean allowed;
    private final ConnectivityManager.NetworkCallback wifiChanged = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network network) { wifi.add(network); report(); }
        @Override public void onLost(Network network) { wifi.remove(network); report(); }
    };
    private final AudioDeviceCallback devicesChanged = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { refreshBluetooth(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { refreshBluetooth(); }
    };

    PlaceMonitor(Context context, Consumer<Boolean> changed) {
        this.context = context;
        this.changed = changed;
        connectivity = context.getSystemService(ConnectivityManager.class);
        audio = context.getSystemService(AudioManager.class);
    }

    void start() {
        if (started) return;
        started = true;
        // Seed from the current network so start-up does not pause before the first callback.
        Network active = connectivity.getActiveNetwork();
        NetworkCapabilities capabilities = active == null ? null : connectivity.getNetworkCapabilities(active);
        if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi.add(active);
        bluetooth = bluetoothConnected();
        allowed = !wifi.isEmpty() || bluetooth;
        DiagnosticHistory.record(context, "PLACE_STATE wifi=" + !wifi.isEmpty() + " bluetooth=" + bluetooth);
        connectivity.registerNetworkCallback(new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiChanged, main);
        audio.registerAudioDeviceCallback(devicesChanged, main);
    }

    boolean allowed() { return started && allowed; }

    private void refreshBluetooth() {
        if (!started) return;
        bluetooth = bluetoothConnected();
        report();
    }

    private boolean bluetoothConnected() {
        for (AudioDeviceInfo device : audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS))
            if (isBluetoothAudio(device)) return true;
        return false;
    }

    // Car hands-free and media profiles, speakers, headphones and hearing aids all count.
    static boolean isBluetoothAudio(AudioDeviceInfo device) {
        switch (device.getType()) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO:
            case AudioDeviceInfo.TYPE_BLE_HEADSET:
            case AudioDeviceInfo.TYPE_BLE_SPEAKER:
            case AudioDeviceInfo.TYPE_HEARING_AID:
                return true;
            default:
                return false;
        }
    }

    private void report() {
        if (!started) return;
        boolean next = !wifi.isEmpty() || bluetooth;
        if (next == allowed) return;
        allowed = next;
        DiagnosticHistory.record(context, "PLACE_STATE wifi=" + !wifi.isEmpty() + " bluetooth=" + bluetooth);
        changed.accept(allowed);
    }

    @Override public void close() {
        if (!started) return;
        started = false;
        try { connectivity.unregisterNetworkCallback(wifiChanged); } catch (RuntimeException ignored) { }
        try { audio.unregisterAudioDeviceCallback(devicesChanged); } catch (RuntimeException ignored) { }
        wifi.clear();
    }
}
