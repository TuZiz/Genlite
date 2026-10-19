package cn.genlite.domain;

import java.util.*;

/** Deterministic chest projection. Legacy overflow stays in storage and becomes visible as stacks are removed. */
public final class CollectorStacks {
    public static final int SLOTS=27;
    public record Stack(String item,int amount) {}
    private CollectorStacks() {}
    public static List<Stack> visible(Map<String,Integer> stock) {
        var result=new ArrayList<Stack>();
        for(var entry:new TreeMap<>(stock).entrySet()) {
            int left=entry.getValue();
            while(left>0 && result.size()<SLOTS) {int count=Math.min(64,left);result.add(new Stack(entry.getKey(),count));left-=count;}
            if(result.size()==SLOTS) break;
        }
        return List.copyOf(result);
    }
}
