package cn.genlite.domain;

import java.util.*;

/** Retired machines keep their historical record; physical cleanup/refunds run after durable retirement. */
public final class RetiredSmelters {
    private RetiredSmelters() {}
    public static boolean retired(String type) {return type.equals("basic_smelter");}
    public static WorldState migrate(WorldState state) {
        var draft=state.draft();
        for(Machine m:state.machines().values()) if(retired(m.type()) && !m.removed()) draft.machines.put(m.id(),m.retirement(false));
        return draft.freeze();
    }
    public static void refund(WorldState.Draft draft,Machine m) {
        draft.parcel(m.owner(),"minecraft:furnace",1,0);
        grant(draft,m,"genlite:fuel",m.fuel()/8);
        grant(draft,m,"minecraft:raw_iron",m.input());grant(draft,m,"minecraft:iron_ingot",m.buffer());
        m.inventory().forEach((item,count) -> grant(draft,m,item,count));
        // Preserve the historical machine record, including any fractional fuel, for manual reconciliation.
    }
    private static void grant(WorldState.Draft draft,Machine m,String item,int count) {
        while(count>0) {int amount=Math.min(64,count);draft.parcel(m.owner(),item,amount,0);count-=amount;}
    }
}
