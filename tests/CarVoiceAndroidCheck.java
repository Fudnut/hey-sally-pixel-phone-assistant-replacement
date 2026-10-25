/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;
import android.content.Context;
import android.os.Handler;
import android.media.AudioRecord;
import java.util.*;

public final class CarVoiceAndroidCheck {
    static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
    static void waitRead(int n)throws Exception{for(int i=0;i<200&&AudioRecord.last.reads<n;i++)Thread.sleep(5);check(AudioRecord.last.reads>=n,"Capture worker did not reach expected read");}
    public static void main(String[] args)throws Exception{
        Context c=new Context();List<String> ends=new ArrayList<>();
        Context initial=c;
        AndroidCarAudioSession s=new AndroidCarAudioSession(c,()->initial.telecom.busy,ends::add);
        s.session.start();check(s.ownsCommunicationMode()&&c.audio.mode==3,"Owned route must publish its communication lease");
        s.session.onInput(true);c.audio.mode=0;Handler.advance(250);
        check(!s.session.isActive()&&ends.contains("MODE_LOST")&&c.audio.clears==1,"Foreign mode change must end the real Android session");
        c=new Context();Context current=c;s=new AndroidCarAudioSession(c,()->current.telecom.busy,ends::add);
        s.session.start();s.session.onInput(true);c.audio.communicationType=2;Handler.advance(250);
        check(!s.session.isActive()&&c.audio.clears==1,"Output route loss must close the session");
        c=new Context();c.telecom.busy=true;Context busy=c;s=new AndroidCarAudioSession(c,()->busy.telecom.busy,ends::add);s.session.start();
        check(c.audio.mode==0&&c.audio.clears==0,"Existing call must block route mutations");

        Context captureContext=new Context();List<Boolean> routes=new ArrayList<>();int[] ready={0},results={0},errors={0};
        WakeMicrophone mic=new WakeMicrophone(captureContext,new org.vosk.Model(),true,true,routes::add);
        org.vosk.android.RecognitionListener listener=new org.vosk.android.RecognitionListener(){
            public void onPartialResult(String x){}public void onResult(String x){results[0]++;}
            public void onFinalResult(String x){}public void onError(Exception x){errors[0]++;}public void onTimeout(){}
        };
        try{
            mic.start(listener,()->ready[0]++);waitRead(1);
            AudioRecord.last.offer();waitRead(2);Handler.advance(0);
            check(ready[0]==0&&org.vosk.Recognizer.accepts==0&&routes.equals(List.of(false)),
                    "Actual built-in input must be discarded before readiness or decoder input");
            captureContext.audio.inputType=7;AudioRecord.last.offer();waitRead(3);Handler.advance(0);
            check(ready[0]==1&&results[0]==1&&org.vosk.Recognizer.accepts==1&&routes.get(1),
                    "Only confirmed Bluetooth PCM may reach local command recognition");
            check(AudioRecord.last.source==7,"Car capture must use voice communication source");
            AudioRecord held=AudioRecord.last;
            mic.pause();
            check(!held.stopped&&!held.released,"Pausing decoding for a prompt must retain the physical car recorder");
            mic.resume(listener,()->ready[0]++,true);
            AudioRecord.last.offer();waitRead(4);Handler.advance(0);
            check(AudioRecord.last==held&&!held.stopped&&ready[0]==2&&results[0]==2,
                    "Wake/command phase changes must reuse capture without a communication-mode inactivity gap");
            captureContext.audio.silenced=true;AudioRecord.last.offer();
            for(int i=0;i<200&&errors[0]==0;i++){Thread.sleep(5);Handler.advance(0);}
            check(errors[0]==1,"Silenced car capture must fail rather than decode unavailable input");
        }finally{mic.close();}
        check(AudioRecord.last.stopped,"Closing real capture must unblock its read worker");
        int closedBefore=VoskWakeDecoder.closes;
        org.vosk.Recognizer.failConstruction=true;
        try {
            new WakeMicrophone(new Context(),new org.vosk.Model(),true,true,x->{});
            throw new AssertionError("Expected decoder setup failure");
        }catch(IllegalStateException expected){
            check(VoskWakeDecoder.closes==closedBefore+1&&AudioRecord.last.released,
                    "Failed command-decoder setup must release the already-created wake decoder and recorder");
        }finally{org.vosk.Recognizer.failConstruction=false;}
        System.out.println("Car Android: owned routes, foreign preemption, actual PCM gate, local commands and silencing passed");
    }
}
