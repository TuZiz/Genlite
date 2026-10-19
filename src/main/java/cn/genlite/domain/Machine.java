package cn.genlite.domain;

import java.util.Map;
import java.util.UUID;

/** Domain state only: never contains Bukkit objects or mutable inventory references. */
public record Machine(UUID id, Position position, UUID home, UUID owner, String type, int level,
                      int fuel, int input, int buffer, boolean paused, boolean locked,
                      boolean connected, UUID collector, Map<String, Integer> inventory,
                      long revision, long acknowledged, boolean removed, boolean cleared, UUID shellToken, OutputMode outputMode) {
    public Machine { inventory = Map.copyOf(inventory); }
    public static Machine create(Position p, UUID home, UUID owner, String type, int level) {
        return new Machine(UUID.randomUUID(), p, home, owner, type, level, 0, 0, 0,
                false, false, false, null, Map.of(), 1, 0, false, false, null,OutputMode.DROP);
    }
    public boolean collectorType() { return type.equals("resource_collector"); }
    /** Old saves used connected=false for internal output; migrate it to physical drops. */
    public OutputMode mode() {return outputMode!=null?outputMode:connected?OutputMode.COLLECTOR:OutputMode.DROP;}
    public Machine contents(int fuel, int input, int buffer, Map<String, Integer> inventory) {
        return new Machine(id, position, home, owner, type, level, fuel, input, buffer, paused, locked,
                connected, collector, inventory, revision, acknowledged, removed, cleared, shellToken,mode());
    }
    public Machine settings(int level, boolean paused, boolean locked, boolean connected, UUID collector) {
        return new Machine(id, position, home, owner, type, level, fuel, input, buffer, paused, locked,
                connected, collector, inventory, revision + (level == this.level ? 0 : 1), acknowledged, removed, cleared, shellToken,
                connected!=this.connected?(connected?OutputMode.COLLECTOR:OutputMode.DROP):mode());
    }
    public Machine output(OutputMode mode) {
        return new Machine(id,position,home,owner,type,level,fuel,input,buffer,paused,locked,mode==OutputMode.COLLECTOR,
                collector,inventory,revision,acknowledged,removed,cleared,shellToken,mode);
    }
    public Machine retirement(boolean cleared) {
        return new Machine(id, position, home, owner, type, level, fuel, input, buffer, paused, locked,
                connected, collector, inventory, revision + (removed ? 0 : 1), acknowledged, true, cleared,
                removed ? shellToken : UUID.randomUUID(),mode());
    }
    public Machine ack(long revision) {
        return new Machine(id, position, home, owner, type, level, fuel, input, buffer, paused, locked,
                connected, collector, inventory, this.revision, revision, removed, cleared, shellToken,mode());
    }
    public boolean empty() { return fuel == 0 && input == 0 && buffer == 0 && inventory.isEmpty(); }
    public Machine consumeShell() {
        return new Machine(id,position,home,owner,type,level,0,0,0,paused,locked,false,null,Map.of(),revision,acknowledged,true,true,null,OutputMode.DROP);
    }
    public Machine unpack(Position p,UUID home,UUID player) {
        return new Machine(UUID.randomUUID(),p,home,player,type,level,fuel,input,buffer,paused,locked,
                false,null,inventory,1,0,false,false,null,OutputMode.DROP);
    }
}
