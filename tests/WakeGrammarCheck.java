/*
 * Copyright (c) 2026 Spotify Wake Probe contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional permission under GPLv3 section 7: see LINKING_EXCEPTION.md.
 */
package com.steve.spotifywakeprobe;
import java.util.*;

public final class WakeGrammarCheck {
    public static void main(String[] args) {
        String json=WakeGrammar.json();
        if(!json.startsWith("[\"hey sally\",\"[unk]\"")) throw new AssertionError("Wake entries must come first");
        if(!json.endsWith("]")) throw new AssertionError("Grammar must end with an array close");
        List<String> words=parse(json);
        if(words.size()<200 || words.size()>400) throw new AssertionError("Unexpected grammar size: "+words.size());
        if(new HashSet<>(words).size()!=words.size()) throw new AssertionError("Grammar contains duplicates");
        if(!words.get(0).equals("hey sally")||!words.get(1).equals("[unk]"))
            throw new AssertionError("Unexpected leading grammar items");
        for(int index=2;index<words.size();index++)
            if(!words.get(index).matches("[a-z]+")) throw new AssertionError("Decoys must be lowercase tokens");
        for(String required:new String[]{"hey","sally","hay","sadly","salad","silly","sale","seller",
                "cell","sad","sat","say","said","says","sell"})
            if(!words.contains(required)) throw new AssertionError("Missing required confuser: "+required);
        System.out.println("Wake grammar: structure, size, uniqueness and confusers passed ("+words.size()+" entries)");
    }

    private static List<String> parse(String json) {
        List<String> items=new ArrayList<>();
        int index=1;
        while(index<json.length()-1) {
            if(json.charAt(index)!='\"') throw new AssertionError("Expected quoted item at "+index);
            StringBuilder item=new StringBuilder();
            for(index++;index<json.length();index++) {
                char current=json.charAt(index);
                if(current=='\"') break;
                if(current=='\\') {
                    if(++index>=json.length()) throw new AssertionError("Incomplete escape");
                    char escaped=json.charAt(index);
                    if(escaped!='\"' && escaped!='\\') throw new AssertionError("Unexpected escape");
                    current=escaped;
                }
                item.append(current);
            }
            if(index>=json.length()) throw new AssertionError("Unclosed item");
            items.add(item.toString());
            index++;
            if(index==json.length()-1) break;
            if(json.charAt(index)!=',') throw new AssertionError("Expected comma at "+index);
            index++;
        }
        return items;
    }
}
