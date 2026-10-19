package cn.genlite.platform;

import cn.genlite.domain.*;
import cn.genlite.gui.Messages;
import cn.genlite.integration.IslandAccess;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Player callbacks own inventory/commands; the engine alone owns persistence. */
public final class HomeHologramControl {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final IslandAccess islands;
    private final MachinePresence presence;
    private final Messages messages;
    private final Set<UUID> pending=ConcurrentHashMap.newKeySet();
    public HomeHologramControl(JavaPlugin plugin,GameTasks tasks,MachineEngine engine,IslandAccess islands,MachinePresence presence,Messages messages) {
        this.plugin=plugin;this.tasks=tasks;this.engine=engine;this.islands=islands;this.presence=presence;this.messages=messages;
    }
    public boolean enabled(UUID player) {
        UUID home=islands.memberHome(player);
        return home!=null && engine.ready() && engine.snapshot().hologramsEnabled(home);
    }
    public void toggle(Player player,boolean reopen) {
        if(!engine.ready()) {messages.send(player,"unavailable");return;}
        UUID user=player.getUniqueId(),home=islands.memberHome(user);
        var inventory=player.getOpenInventory().getTopInventory();
        if(home==null || !islands.member(home,user)) {messages.send(player,"holograms.no-home");return;}
        if(!pending.add(user)) {messages.send(player,"busy");return;}
        engine.transact(d -> HomeHologramSettings.toggle(d,home,user,islands::member)).whenComplete((enabled,error) -> {
            pending.remove(user);
            if(error!=null) plugin.getLogger().log(Level.WARNING,"Home hologram setting failed: home="+home+", player="+user,error);
            else tasks.global(presence::refresh);
            tasks.player(player,() -> {
                if(!home.equals(islands.memberHome(user))) return;
                messages.send(player,error!=null?"holograms.failed":enabled?"holograms.enabled":"holograms.disabled");
                if(reopen && player.getOpenInventory().getTopInventory()==inventory && inventory.getType()==org.bukkit.event.inventory.InventoryType.CRAFTING)
                    player.performCommand("dreamskyblock:home gui");
            },() -> {});
        });
    }
}
