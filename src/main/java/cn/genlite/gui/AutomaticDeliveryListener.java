package cn.genlite.gui;

import cn.genlite.platform.GameTasks;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;

/** Historical refunds/failed deliveries return automatically; no claim buttons are exposed. */
public final class AutomaticDeliveryListener implements Listener {
    private final GameTasks tasks;
    private final InventoryTransfers transfers;
    public AutomaticDeliveryListener(GameTasks tasks,InventoryTransfers transfers) {this.tasks=tasks;this.transfers=transfers;}
    private void retry(Player p) {tasks.player(p,() -> transfers.recover(p),() -> {});}
    @EventHandler public void join(PlayerJoinEvent e) {retry(e.getPlayer());}
    @EventHandler public void close(InventoryCloseEvent e) {if(e.getPlayer() instanceof Player p) retry(p);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void drop(PlayerDropItemEvent e) {retry(e.getPlayer());}
}
