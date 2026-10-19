package cn.genlite.platform;

import cn.genlite.domain.*;
import cn.genlite.gui.*;
import cn.genlite.integration.IslandAccess;
import net.momirealms.craftengine.bukkit.api.event.*;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player/owning region thread only. Sneak dismantling takes precedence over binding and menus. */
public final class MachineInteractions implements Listener {
    private final MachineEngine engine;
    private final MachineActions actions;
    private final IslandAccess dream;
    private final CraftItems items;
    private final Messages messages;
    private final MachineMenus menus;
    private final InventoryTransfers transfers;
    private final MachineDismantling dismantling;
    private final Map<UUID,UUID> selection=new ConcurrentHashMap<>();
    public MachineInteractions(MachineEngine e,MachineActions a,IslandAccess d,CraftItems i,Messages msg,MachineMenus m,InventoryTransfers t,MachineDismantling dismantling) {
        engine=e;actions=a;dream=d;items=i;messages=msg;menus=m;transfers=t;this.dismantling=dismantling;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void interact(CustomBlockInteractEvent e) {
        String type=MachinePresence.type(e.blockState());
        if(type==null || !engine.rules().types().containsKey(type) || e.hand()!=InteractionHand.MAIN_HAND
                || e.action()!=CustomBlockInteractEvent.Action.RIGHT_CLICK) return;
        e.setCancelled(true);var p=e.player();Machine m=engine.at(MachinePresence.position(e.location()));
        if(!menus.allowed(p,m)) {messages.send(p,m==null?"busy":"permission");return;}
        // CE emits this event before its vanilla sneak/item-use branch, even when both hands are empty.
        if(p.isSneaking()) {
            selection.remove(p.getUniqueId());dismantling.request(p,m,e.location());
            return;
        }
        if(!items.id(p.getInventory().getItemInMainHand()).equals("genlite:binding_tool")) {menus.open(p,m.id(),null);return;}
        UUID selected=selection.get(p.getUniqueId());Machine first=selected==null?null:engine.snapshot().machines().get(selected);
        if(first==null || first.removed() || first.id().equals(m.id()) || first.collectorType()==m.collectorType()) {
            selection.put(p.getUniqueId(),m.id());messages.send(p,"selected");return;
        }
        if(!dream.member(first.home(),p.getUniqueId())) {messages.send(p,"permission");return;}
        Machine generator=first.collectorType()?m:first,collector=first.collectorType()?first:m;
        transfers.mutate(p,d -> {actions.bind(d,generator.id(),collector.id(),p.getUniqueId());return null;},() -> {
            selection.remove(p.getUniqueId());messages.send(p,"bound");
        });
    }
    @EventHandler public void quit(PlayerQuitEvent e) {selection.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void reload(CraftEngineReloadEvent e) {clear();}
    public void clear() {selection.clear();}
}
