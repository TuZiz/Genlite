package cn.genlite.domain;

import java.util.UUID;

/** Durable withdrawal. SENDING is quarantined after a crash, never automatically replayed. */
public record Parcel(UUID id, UUID player, String item, int amount, int machineLevel, String phase,
                     UUID shellSource, UUID shellToken, Position dropPosition) {
    public static Parcel waiting(UUID player, String item, int amount, int level) {
        return new Parcel(UUID.randomUUID(), player, item, amount, level, "WAITING",null,null,null);
    }
    public static Parcel shell(Machine m) {
        return new Parcel(UUID.randomUUID(),m.owner(),"genlite:"+m.type(),1,m.level(),"WAITING",m.id(),m.shellToken(),m.position());
    }
    public static Parcel production(Machine m,String item,int amount) {
        return new Parcel(UUID.randomUUID(),m.owner(),item,amount,0,"WAITING",null,null,m.position());
    }
    public Parcel sending() { return new Parcel(id, player, item, amount, machineLevel, "SENDING",shellSource,shellToken,dropPosition); }
    public Parcel waiting() { return new Parcel(id, player, item, amount, machineLevel, "WAITING",shellSource,shellToken,dropPosition); }
    public Parcel amount(int count) {return new Parcel(id,player,item,count,machineLevel,phase,shellSource,shellToken,dropPosition);}
}
