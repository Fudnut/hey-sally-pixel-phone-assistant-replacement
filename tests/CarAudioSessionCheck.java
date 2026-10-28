/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;
import java.util.*;

public final class CarAudioSessionCheck {
    static final class Platform implements CarAudioSession.Platform {
        boolean permission=true, call, accepted=true; int opens, closes, captureStops;
        Map<Runnable,Long> timers=new LinkedHashMap<>(); List<String> events=new ArrayList<>();
        public boolean hasPermissions(){return permission;} public boolean hasActiveCall(){return call;}
        public boolean requestRoute(){opens++;return accepted;} public void stopCapture(){captureStops++;} public void releaseRoute(){check(captureStops==1,"Capture must stop before releasing mode/device");closes++;}
        public void schedule(Runnable r,long ms){timers.put(r,ms);} public void cancel(Runnable r){timers.remove(r);}
        public void report(String s){events.add(s);}
        void fire(long ms){for(var e:new ArrayList<>(timers.entrySet()))if(e.getValue()==ms){timers.remove(e.getKey());e.getKey().run();}}
    }
    static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    public static void main(String[] args){
        Platform p=new Platform(); CarAudioSession s=new CarAudioSession(p); s.start();
        check(s.isActive()&&!s.isReady()&&p.timers.containsValue(10000L),"Route request must wait at most ten seconds for actual input");
        s.onInput(false); check(!s.isReady(),"Built-in actual input must never be accepted");
        s.onInput(true); check(s.isReady()&&p.timers.isEmpty(),"Confirmed Bluetooth input completes setup");
        s.beginConversation(); s.beginConversation();
        check(p.timers.size()==1&&p.timers.containsValue(90000L),"Conversation has one fixed overall deadline");
        s.finishConversation(); check(s.isReady()&&p.timers.isEmpty(),"Finishing conversation keeps passive car wake available");
        s.onInput(false); s.onInput(true); s.stop();
        check(!s.isActive()&&p.closes==1&&p.events.contains("ROUTE_LOST"),"Lost route closes once and stale input cannot revive it");
        for(String end:List.of("timeout","call","permission","stop","conversation")){
            p=new Platform();s=new CarAudioSession(p);s.start();
            switch(end){
                case "timeout" -> p.fire(10000);
                case "call" -> {p.call=true;s.checkAccess();}
                case "permission" -> {p.permission=false;s.checkAccess();}
                case "stop" -> s.stop();
                case "conversation" -> {s.onInput(true);s.beginConversation();p.fire(90000);}
            }
            check(!s.isActive()&&p.closes==1&&p.timers.isEmpty(),"Every terminal path releases once: "+end);
            s.onInput(true);s.beginConversation();s.stop();check(p.closes==1,"Late callbacks must stay inert");
        }
        p=new Platform();p.call=true;s=new CarAudioSession(p);s.start();
        check(p.opens==0&&!s.isActive(),"Existing call blocks routing before mutations");
        p=new Platform();p.accepted=false;s=new CarAudioSession(p);s.start();
        check(!s.isActive()&&p.events.contains("NO_CAR_ROUTE"),"Unavailable or ambiguous route fails closed");
        System.out.println("Car session: actual input, deadlines, conversation lifetime, access and stale callbacks passed");
    }
}
