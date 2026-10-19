package cn.genlite.display;

import cn.genlite.platform.CraftItems;
import org.bukkit.event.*;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Apply configured text to CE workbench results and older machine items without changing their identity/PDC. */
public final class MachineItemDisplayListener implements Listener {
    private final CraftItems items;
    public MachineItemDisplayListener(CraftItems items) {this.items=items;}
    @EventHandler(priority=EventPriority.HIGHEST) public void craft(PrepareItemCraftEvent e) {
        var result=e.getInventory().getResult();if(result==null) return;
        e.getInventory().setResult(items.decorate(result));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void pickup(EntityPickupItemEvent e) {
        var item=e.getItem().getItemStack();e.getItem().setItemStack(items.decorate(item));
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        var inventory=e.getPlayer().getInventory();
        for(int slot=0;slot<inventory.getSize();slot++) {
            var item=inventory.getItem(slot);if(item==null) continue;
            inventory.setItem(slot,items.decorate(item));
        }
    }
}
