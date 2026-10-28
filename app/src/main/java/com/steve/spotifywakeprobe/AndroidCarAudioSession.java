/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Owns this client's communication requests; never restores another client's route. */
final class AndroidCarAudioSession implements CarAudioSession.Platform, AutoCloseable {
    private final Context context;
    private final AudioManager audio;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final BooleanSupplier callActive;
    private final Consumer<String> interrupted;
    private final Runnable stopCapture;
    final CarAudioSession session;
    private AudioDeviceInfo target;
    private boolean modeOwned, routeOwned, observing, closing;
    private final AudioDeviceCallback devices=new AudioDeviceCallback(){
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed){
            if(closing||target==null)return;
            for(AudioDeviceInfo d:removed)if(d.getId()==target.getId())session.interrupt("ROUTE_LOST");
        }
    };
    private final Runnable poll=new Runnable(){public void run(){
        if(closing||!session.checkAccess())return;
        if(audio.getMode()!=AudioManager.MODE_IN_COMMUNICATION){session.interrupt("MODE_LOST");return;}
        AudioDeviceInfo current=audio.getCommunicationDevice();
        if(session.isReady()&&(!isBluetooth(current)||current.getId()!=target.getId())){
            session.interrupt("ROUTE_LOST");return;
        }
        main.postDelayed(this,250);
    }};
    AndroidCarAudioSession(Context context,BooleanSupplier callActive,Consumer<String> interrupted){
        this(context,callActive,()->{},interrupted);
    }
    AndroidCarAudioSession(Context context,BooleanSupplier callActive,Runnable stopCapture,Consumer<String> interrupted){
        this.context=context;this.audio=context.getSystemService(AudioManager.class);
        this.callActive=callActive;this.stopCapture=stopCapture;this.interrupted=interrupted;session=new CarAudioSession(this);
    }
    boolean ownsCommunicationMode(){return modeOwned;}
    @Override public boolean hasPermissions(){
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
            &&context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED;
    }
    @Override public boolean hasActiveCall(){return callActive.getAsBoolean();}
    @Override public boolean requestRoute(){
        int count=0;
        for(AudioDeviceInfo d:audio.getAvailableCommunicationDevices())if(isBluetooth(d)){target=d;count++;}
        if(count!=1)return false;
        audio.registerAudioDeviceCallback(devices,main);observing=true;
        // Publish the lease before synchronous mode callbacks reach CallAudioMonitor.
        modeOwned=true;audio.setMode(AudioManager.MODE_IN_COMMUNICATION);
        if(!session.isActive()||closing||hasActiveCall())return false;
        routeOwned=true;
        if(!audio.setCommunicationDevice(target))return false;
        if(!session.isActive()||closing||hasActiveCall())return false;
        main.postDelayed(poll,250);return true;
    }
    @Override public void schedule(Runnable r,long ms){main.postDelayed(r,ms);}
    @Override public void cancel(Runnable r){main.removeCallbacks(r);}
    @Override public void report(String category){
        DiagnosticHistory.record(context,"CAR_AUDIO_"+category);
        if(!session.isActive()&&!closing)interrupted.accept(category);
    }
    @Override public void stopCapture(){stopCapture.run();}
    @Override public void releaseRoute(){
        main.removeCallbacks(poll);
        if(observing){observing=false;try{audio.unregisterAudioDeviceCallback(devices);}catch(RuntimeException ignored){}}
        if(routeOwned){routeOwned=false;try{audio.clearCommunicationDevice();}catch(RuntimeException ignored){}}
        // Keep the lease visible until this process's mode request has been released.
        if(modeOwned){try{audio.setMode(AudioManager.MODE_NORMAL);}catch(RuntimeException ignored){}finally{modeOwned=false;}}
    }
    static boolean isBluetooth(AudioDeviceInfo d){return d!=null&&(d.getType()==AudioDeviceInfo.TYPE_BLUETOOTH_SCO||d.getType()==AudioDeviceInfo.TYPE_BLE_HEADSET);}
    @Override public void close(){closing=true;session.stop();}
}
