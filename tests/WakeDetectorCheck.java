/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;
import java.io.IOException;

public final class WakeDetectorCheck {
    private static final class Decoder implements WakeDetector.Decoder {
        String next;
        int calls, closes;
        public String accept(short[] audio, int count) throws IOException {
            calls++;
            String result=next; next=null; return result;
        }
        public void close() { closes++; }
    }
    public static void main(String[] args) throws Exception {
        Decoder decoder=new Decoder();
        WakeDetector detector=new WakeDetector(decoder);
        check(detector,decoder,"hey sally",true);
        check(detector,decoder,"hey salad",false);
        check(detector,decoder,"sally",false);
        check(detector,decoder,"hey sally now",false);
        check(detector,decoder,"please hey sally",false);
        check(detector,decoder,"",false);
        check(detector,decoder,null,false);

        decoder.next="hey";
        if(detector.accept(new short[]{1},1)!=null) throw new AssertionError("Hold exact Hey for one result");
        decoder.next="sally";
        if(!WakePhrase.matches(detector.accept(new short[]{2},1)))
            throw new AssertionError("Accept exact Hey then Sally across endpoints");

        decoder.next="hey"; detector.accept(new short[]{3},1);
        decoder.next="salad";
        if(WakePhrase.matches(detector.accept(new short[]{4},1))) throw new AssertionError("Reject wrong split completion");
        decoder.next="sally";
        if(WakePhrase.matches(detector.accept(new short[]{5},1))) throw new AssertionError("Clear hold after one result");

        decoder.next="hey"; detector.accept(new short[]{6},1);
        decoder.next=""; detector.accept(new short[]{7},1);
        decoder.next="sally";
        if(WakePhrase.matches(detector.accept(new short[]{8},1)))
            throw new AssertionError("A blank result must consume the one-result hold");

        decoder.next="hey"; detector.accept(new short[]{9},1);
        decoder.next="hey sally";
        if(!WakePhrase.matches(detector.accept(new short[]{10},1)))
            throw new AssertionError("A stray held Hey must not reject a complete Hey Sally");

        detector.close(); detector.close();
        if(decoder.closes!=1) throw new AssertionError("Close decoder exactly once");
        try { detector.accept(new short[]{1},1); throw new AssertionError("Closed detector must reject input"); }
        catch(IllegalStateException expected) { }
        System.out.println("Wake detector: exact closed-vocabulary results, split endpoint and cleanup passed");
    }

    private static void check(WakeDetector detector,Decoder decoder,String text,boolean expected) throws Exception {
        decoder.next=text;
        boolean actual=WakePhrase.matches(detector.accept(new short[]{1},1));
        if(actual!=expected) throw new AssertionError("Unexpected exact-match decision");
    }
}
