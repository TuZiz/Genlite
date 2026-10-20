package cn.genlite.gui;

import java.util.*;

/** Pure projection of durable fuel energy into withdrawable, canonical item stacks. */
final class FuelStacks {
    private FuelStacks() {}
    static List<Integer> visible(int energy,int units,int maxStack,int slots) {
        if(energy<0 || units<=0 || maxStack<=0 || slots<=0) throw new IllegalArgumentException("Invalid fuel view bounds");
        int remaining=energy/units;
        var stacks=new ArrayList<Integer>(slots);
        for(int slot=0;slot<slots;slot++) {
            int count=Math.min(maxStack,remaining);stacks.add(count);remaining-=count;
        }
        return List.copyOf(stacks);
    }
    static int withdrawal(int count,boolean rightClick) {return rightClick?(count+1)/2:count;}
    static int dragAddition(Map<Integer,Integer> before,Map<Integer,Integer> after) {
        return after.entrySet().stream().mapToInt(entry -> Math.max(0,entry.getValue()-before.getOrDefault(entry.getKey(),0))).sum();
    }
}
