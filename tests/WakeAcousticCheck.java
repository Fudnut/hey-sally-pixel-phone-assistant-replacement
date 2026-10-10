/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;
import com.google.gson.*;
import org.vosk.*;
import javax.sound.sampled.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;

public final class WakeAcousticCheck {
    public static void main(String[] args) throws Exception {
        LibVosk.vosk_set_log_level(-1);
        Path corpus=Path.of(args[1]);
        JsonArray records=JsonParser.parseString(Files.readString(corpus).replace("\ufeff", "")).getAsJsonArray();
        JsonArray results=new JsonArray(); int positives=0, negatives=0, falseWakes=0, misses=0; double totalRtf=0;
        try(Model model=new Model(args[0])) {
            for(int index=0;index<records.size();index++) {
                JsonObject row=records.get(index).getAsJsonObject(); boolean expected=row.get("expected").getAsBoolean();
                short[] pcm=load(corpus,row);
                boolean accepted; long start=System.nanoTime();
                try(WakeDetector detector=VoskWakeDecoder.create(model)) { accepted=replay(detector,pcm); }
                double rtf=(System.nanoTime()-start)/1e9/(pcm.length/16000.0);totalRtf+=rtf;
                if(expected) positives++;else negatives++;
                if(accepted&&!expected)falseWakes++; if(!accepted&&expected)misses++;
                JsonObject result=new JsonObject();result.addProperty("sample",index);result.addProperty("expected",expected);
                result.addProperty("accepted",accepted);result.addProperty("rtf",rtf);results.add(result);
            }
            // A long-running listener must recover after silence without losing speech onset.
            for(JsonElement element:records) {
                JsonObject row=element.getAsJsonObject();
                if(!row.get("expected").getAsBoolean()) continue;
                short[] pcm=load(corpus,row);
                try(WakeDetector detector=VoskWakeDecoder.create(model)) {
                    if(replay(detector,new short[16000*30])) throw new AssertionError("Silence activated");
                    for(int silence=0;silence<=5;silence++) {
                        if(replay(detector,new short[16000*silence])) throw new AssertionError("Pause activated");
                        if(!replay(detector,pcm)) throw new AssertionError("Missed wake after silence="+silence);
                    }
                }
            }
            System.out.println("Continuous wake checks: 30-second idle, varied pauses and reused verifier passed");
        }
        if(positives==0||negatives==0)throw new IllegalArgumentException("Need intentional and unrelated speech");
        JsonObject report=new JsonObject();report.addProperty("strategy","candidate plus on-demand full verification");
        report.addProperty("intentional",positives);report.addProperty("unrelated",negatives);report.addProperty("false_wakes",falseWakes);
        report.addProperty("misses",misses);report.addProperty("mean_rtf",totalRtf/records.size());report.add("samples",results);
        if(args.length>2)Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        System.out.printf(Locale.ROOT,"Wake acoustic check: %d intentional, %d unrelated; false wakes=%d, misses=%d, mean RTF=%.3f%n",positives,negatives,falseWakes,misses,totalRtf/records.size());
        if(falseWakes!=0||misses!=0)System.exit(1);
    }
    private static short[] load(Path corpus,JsonObject row) throws Exception {
        Path file=corpus.getParent().resolve(row.get("file").getAsString()); byte[] bytes;
        try(AudioInputStream audio=AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat f=audio.getFormat();
            if(f.getSampleRate()!=16000f || f.getSampleSizeInBits()!=16 || f.getChannels()!=1 || f.isBigEndian()
                    || !f.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) throw new IllegalArgumentException("Invalid corpus format");
            bytes=audio.readAllBytes();
        }
        short[] pcm=new short[bytes.length/2+32000];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm,0,bytes.length/2);
        return pcm;
    }
    private static boolean replay(WakeDetector detector,short[] pcm) throws Exception {
        boolean accepted=false;
        for(int offset=0;offset<pcm.length;offset+=3200) {
            short[] block=Arrays.copyOfRange(pcm,offset,Math.min(pcm.length,offset+3200));
            if(WakePhrase.matches(detector.accept(block,block.length))) accepted=true;
        }
        return accepted;
    }
}
