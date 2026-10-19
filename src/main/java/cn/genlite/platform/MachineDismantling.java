package cn.genlite.platform;

import cn.genlite.domain.*;
import cn.genlite.gui.InventoryTransfers;
import cn.genlite.gui.Messages;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Player/region entry; durable retirement immediately wakes region cleanup instead of waiting for polling. */
public final class MachineDismantling {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final MachineActions actions;
    private final MachinePresence presence;
    private final Messages messages;
    private final Set<UUID> pending=ConcurrentHashMap.newKeySet();
    public MachineDismantling(JavaPlugin p,GameTasks t,MachineEngine e,MachineActions a,MachinePresence presence,Messages messages) {
        plugin=p;tasks=t;engine=e;actions=a;this.presence=presence;this.messages=messages;
    }
    public void request(Player player,Machine machine,Location location) {
        if(!pending.add(machine.id())) return;
        UUID user=player.getUniqueId();Location block=location.clone();
        engine.transact(d -> {actions.dismantle(d,machine.id(),user);return null;}).whenComplete((v,error) -> {
            pending.remove(machine.id());
            if(error==null) {presence.refresh(machine.id(),block);return;}
            String key=InventoryTransfers.errorKey(error);
            if(key.equals("unavailable")) plugin.getLogger().log(Level.WARNING,"Machine dismantle failed: machine="+machine.id()+", player="+user+", at="+machine.position(),error);
            tasks.player(player,() -> messages.send(player,key),() -> {});
        });
    }
    public void clear() {pending.clear();}
}
