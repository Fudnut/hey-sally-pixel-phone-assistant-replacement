/* Copyright (c) 2026 Spotify Wake Probe contributors. SPDX-License-Identifier: GPL-3.0-only */
package com.steve.spotifywakeprobe;

/** Main-thread lifetime of one owned car route, including a bounded conversation. */
final class CarAudioSession {
    interface Platform {
        boolean hasPermissions(); boolean hasActiveCall(); boolean requestRoute(); void releaseRoute();
        default void stopCapture() { }
        void schedule(Runnable action,long delay); void cancel(Runnable action); void report(String category);
    }
    private final Platform platform;
    private boolean started, closed, ready, conversation;
    private final Runnable connectionDeadline=()->end("CONNECTION_TIMEOUT");
    private final Runnable conversationDeadline=()->end("CONVERSATION_TIMEOUT");
    CarAudioSession(Platform platform){this.platform=platform;}
    void start(){
        if(started||closed)return;
        started=true;
        if(!checkAccess())return;
        platform.schedule(connectionDeadline,10000);
        try { if(!platform.requestRoute())end("NO_CAR_ROUTE"); }
        catch(RuntimeException error){end("ROUTE_ERROR");}
    }
    boolean checkAccess(){
        if(!isActive())return false;
        if(!platform.hasPermissions()){end("PERMISSION_REQUIRED");return false;}
        if(platform.hasActiveCall()){end("CALL_ACTIVE");return false;}
        return isActive();
    }
    void onInput(boolean bluetooth){
        if(!checkAccess())return;
        if(!bluetooth){if(ready)end("ROUTE_LOST");return;}
        if(!ready){ready=true;platform.cancel(connectionDeadline);platform.report("INPUT_READY");}
    }
    void beginConversation(){
        if(!ready||conversation||!checkAccess())return;
        conversation=true;platform.schedule(conversationDeadline,90000);
    }
    void finishConversation(){conversation=false;platform.cancel(conversationDeadline);}
    void interrupt(String reason){end(reason);}
    void stop(){end("STOPPED");}
    boolean isActive(){return started&&!closed;}
    boolean isReady(){return isActive()&&ready;}
    private void end(String reason){
        if(closed)return;
        closed=true;platform.cancel(connectionDeadline);platform.cancel(conversationDeadline);
        try{platform.stopCapture();}
        finally{try{platform.releaseRoute();}finally{platform.report(reason);}}
    }
}
