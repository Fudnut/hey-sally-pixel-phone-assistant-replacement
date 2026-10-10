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
    private record Condition(String name,double gain,double snrDb,boolean gating) { }
    private static final Condition[] CONDITIONS = {
            new Condition("clean",1.0,0,true),
            new Condition("20 dB",1.0,20,true),
            new Condition("10 dB",1.0,10,true),
            new Condition("5 dB",1.0,5,false),
            new Condition("quiet + 10 dB",0.25,10,false)
    };

    public static void main(String[] args) throws Exception {
        LibVosk.vosk_set_log_level(-1);
        Path corpus=Path.of(args[1]);
        JsonArray records=JsonParser.parseString(Files.readString(corpus).replace("\ufeff", "")).getAsJsonArray();
        int positives=0,negatives=0;
        for(JsonElement element:records)
            if(element.getAsJsonObject().get("expected").getAsBoolean()) positives++; else negatives++;
        if(positives!=4||negatives!=42) throw new IllegalArgumentException("Need 4 intentional and 42 unrelated clips");
        JsonArray conditions=new JsonArray(); boolean passed=true;
        try(Model model=new Model(args[0])) {
            for(Condition condition:CONDITIONS) {
                int detected=0,falseWakes=0;
                for(int index=0;index<records.size();index++) {
                    JsonObject row=records.get(index).getAsJsonObject();
                    boolean expected=row.get("expected").getAsBoolean();
                    short[] pcm=degrade(load(corpus,row),condition.gain(),condition.snrDb(),0x5a11L+index);
                    boolean accepted;
                    try(WakeDetector detector=VoskWakeDecoder.create(model)) { accepted=replay(detector,pcm); }
                    if(expected&&accepted) detected++;
                    if(!expected&&accepted) falseWakes++;
                }
                JsonObject result=new JsonObject();
                result.addProperty("condition",condition.name());
                result.addProperty("intentional_detected",detected);
                result.addProperty("intentional_total",positives);
                result.addProperty("unrelated_false_wakes",falseWakes);
                result.addProperty("unrelated_total",negatives);
                conditions.add(result);
                System.out.printf(Locale.ROOT,"%-14s intentional detected %d/%d, unrelated falsely woken %d/%d%n",
                        condition.name(),detected,positives,falseWakes,negatives);
                if(condition.gating()) passed&=detected==positives&&falseWakes==0;
                else passed&=falseWakes<=1;
            }
        }
        JsonObject report=new JsonObject();
        report.addProperty("strategy","closed vocabulary with decoy words");
        report.add("conditions",conditions);
        if(args.length>2)Files.writeString(Path.of(args[2]),new GsonBuilder().setPrettyPrinting().create().toJson(report)+"\n");
        if(!passed)System.exit(1);
    }

    private static short[] degrade(short[] input,double gain,double snrDb,long seed) {
        double squares=0;
        for(short sample:input) squares+=(double)sample*sample;
        double rms=Math.sqrt(squares/input.length);
        double noiseRms=snrDb==0?0:rms/Math.pow(10,snrDb/20.0);
        Random random=new Random(seed); short[] output=new short[input.length];
        for(int index=0;index<input.length;index++) {
            double sample=(input[index]+(noiseRms==0?0:random.nextGaussian()*noiseRms))*gain;
            output[index]=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,Math.round(sample)));
        }
        return output;
    }

    private static short[] load(Path corpus,JsonObject row) throws Exception {
        Path file=corpus.getParent().resolve(row.get("file").getAsString()); byte[] bytes;
        try(AudioInputStream audio=AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat format=audio.getFormat();
            if(format.getSampleRate()!=16000f||format.getSampleSizeInBits()!=16||format.getChannels()!=1
                    ||format.isBigEndian()||!format.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED))
                throw new IllegalArgumentException("Invalid corpus format");
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
