/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;
import java.io.IOException;
import java.util.Arrays;

public final class WakeDetectorCheck {
    private static final class Decoder implements WakeDetector.Decoder {
        String next;
        String finalText = "hey sally";
        int calls, resets, closes, samples;
        short[] received;
        boolean fail;
        public String accept(short[] audio, int count) throws IOException {
            calls++; samples=count; received=Arrays.copyOf(audio,count);
            if (fail) throw new IOException("fixed test failure");
            String result=next; next=null; return result;
        }
        public String finish() { return finalText; }
        public void reset() { resets++; }
        public void close() { closes++; }
    }
    public static void main(String[] args) throws Exception {
        Decoder candidate=new Decoder(), verifier=new Decoder(); int[] created={0};
        WakeDetector detector=new WakeDetector(candidate,()->{created[0]++;return verifier;});
        for(int i=0;i<100;i++) { candidate.next="[unk]"; detector.accept(new short[]{1,2},2); }
        if(created[0]!=0 || verifier.calls!=0) throw new AssertionError("Ordinary speech must not run full decoding");
        candidate.next=""; detector.accept(new short[]{3},1);
        detector.accept(new short[]{4,5},2); candidate.next="hey sally";
        if(!"hey sally".equals(detector.accept(new short[]{6},1))) throw new AssertionError("Verified exact wake must activate");
        if(created[0]!=1 || verifier.calls!=1 || !Arrays.equals(verifier.received,new short[]{3,4,5,6}))
            throw new AssertionError("Keep quiet onset context and current segment; exclude older speech");
        verifier.finalText="they said"; candidate.next="hey sally";
        if(WakePhrase.matches(detector.accept(new short[]{7},1))) throw new AssertionError("Coerced candidate must not activate");
        verifier.finalText="please hey sally now"; candidate.next="hey sally";
        if(WakePhrase.matches(detector.accept(new short[]{8},1))) throw new AssertionError("Embedded wake must not activate");
        if(created[0]!=1 || verifier.resets!=3) throw new AssertionError("Reuse/reset verifier between candidates");
        short[] oversized=new short[WakeDetector.MAX_SAMPLES+1]; candidate.next="hey sally";
        int calls=verifier.calls;
        if(WakePhrase.matches(detector.accept(oversized,oversized.length)) || verifier.calls!=calls)
            throw new AssertionError("Overflow must reject without verifying a truncated segment");
        verifier.finalText="hey sally"; candidate.next="hey sally";
        if(!WakePhrase.matches(detector.accept(new short[]{9},1))) throw new AssertionError("Recover after overflow");
        verifier.fail=true; candidate.next="hey sally";
        try { detector.accept(new short[]{10},1); throw new AssertionError("Verification failure must fail closed"); }
        catch(IOException expected) { }
        verifier.fail=false; candidate.next="hey sally";
        if(!WakePhrase.matches(detector.accept(new short[]{11},1)) || verifier.samples!=1)
            throw new AssertionError("Failed verification must discard previous audio");
        candidate.next="hey";
        if(detector.accept(new short[]{20},1)!=null) throw new AssertionError("Hold a split Hey until the next endpoint");
        candidate.next="sally";
        if(!WakePhrase.matches(detector.accept(new short[]{21},1)) || !Arrays.equals(verifier.received,new short[]{20,21}))
            throw new AssertionError("Verify both sides of a split wake");
        candidate.next="hey"; detector.accept(new short[]{22},1);
        candidate.next="[unk]"; calls=verifier.calls; detector.accept(new short[]{23},1);
        if(verifier.calls!=calls) throw new AssertionError("Uncompleted Hey must not verify unrelated speech");
        candidate.next="hey sally"; detector.accept(new short[]{24},1);
        if(!Arrays.equals(verifier.received,new short[]{24})) throw new AssertionError("Discard uncompleted Hey audio");
        candidate.next="[unk] hey sally [unk]"; verifier.finalText="they said";
        if(WakePhrase.matches(detector.accept(new short[]{25},1))) throw new AssertionError("Candidate noise must not bypass exact verification");
        short[] quiet=new short[20000]; Arrays.fill(quiet,0,4000,(short)30); Arrays.fill(quiet,4000,20000,(short)31);
        candidate.next=""; detector.accept(quiet,quiet.length);
        candidate.next="hey sally"; verifier.finalText="hey sally"; detector.accept(new short[]{32},1);
        if(verifier.samples!=16001 || verifier.received[0]!=31 || verifier.received[16000]!=32)
            throw new AssertionError("Keep exactly the last second of a blank endpoint for speech onset");
        detector.close(); detector.close();
        if(candidate.closes!=1 || verifier.closes!=1) throw new AssertionError("Close decoders exactly once");
        try { detector.accept(new short[]{1},1); throw new AssertionError("Closed detector must reject input"); }
        catch(IllegalStateException expected) { }
        System.out.println("Wake detector: on-demand verification, rejection, buffer bounds, recovery and cleanup passed");
    }
}
