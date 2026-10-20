package cn.genlite.gui;

import cn.genlite.display.MachineText;
import cn.genlite.domain.*;
import cn.genlite.platform.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.*;

/** Fuel input is an immediate transactional deposit, never a temporary chest that can lose items on close. */
public final class FuelMenus implements Listener {
    private final MachineEngine engine;
    private final CraftItems items;
    private final Messages messages;
    private final InventoryTransfers transfers;
    private final GameTasks tasks;
    private final BiPredicate<Player,Machine> permission;
    private final BiConsumer<Player,UUID> back;
    private final FuelMenuRenderer renderer;
    private final Map<UUID,FuelSession> sessions=new ConcurrentHashMap<>();
    public FuelMenus(YamlConfiguration config,MachineEngine engine,CraftItems items,Messages messages,InventoryTransfers transfers,
                     GameTasks tasks,MachineText text,BiPredicate<Player,Machine> permission,BiConsumer<Player,UUID> back) {
        this.engine=engine;this.items=items;this.messages=messages;this.transfers=transfers;this.tasks=tasks;
        this.permission=permission;this.back=back;renderer=new FuelMenuRenderer(config,engine,messages,text);
    }
    private boolean allowed(Player p,FuelSession s) {
        Machine m=engine.snapshot().machines().get(s.machine);
        return engine.ready() && m!=null && !m.collectorType() && permission.test(p,m);
    }
    private boolean current(Player p,FuelSession s) {
        return sessions.get(p.getUniqueId())==s && p.getOpenInventory().getTopInventory().getHolder()==s && allowed(p,s);
    }
    public void open(Player p,UUID machine) {
        var s=new FuelSession(p,machine);
        if(!allowed(p,s)) {messages.send(p,"permission");return;}
        renderer.draw(s,engine.snapshot().machines().get(machine));
        p.openInventory(s.inventory);sessions.put(p.getUniqueId(),s);
    }
    private void redraw(Player p,FuelSession s) {if(current(p,s)) renderer.draw(s,engine.snapshot().machines().get(s.machine));}
    private boolean validFuel(Player p,Machine m,org.bukkit.inventory.ItemStack stack) {
        if(stack==null || stack.isEmpty()) return false;
        if(!items.build(p,engine.rules().fuelItem(m),1,0).isSimilar(stack)) {messages.send(p,"fuel-invalid");return false;}
        return true;
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof FuelSession s)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !current(p,s)) return;
        int raw=e.getRawSlot();if(raw<0) return;
        if(raw>=s.inventory.getSize()) {
            if(e.getClick()==ClickType.LEFT || e.getClick()==ClickType.RIGHT) {e.setCancelled(false);return;}
            if(e.getClick()!=ClickType.SHIFT_LEFT && e.getClick()!=ClickType.SHIFT_RIGHT) return;
            int slot=e.getSlot();if(slot<0 || slot>=36) return;
            Machine m=engine.snapshot().machines().get(s.machine);
            if(validFuel(p,m,e.getCurrentItem())) transfers.fuelFromSlot(p,m.id(),engine.rules().fuelItem(m),slot,() -> current(p,s),() -> redraw(p,s));
            return;
        }
        String action=s.actions.getOrDefault(raw,"none");
        if(action.equals("back") && (e.getClick()==ClickType.LEFT || e.getClick()==ClickType.RIGHT)) {
            tasks.player(p,() -> {if(current(p,s)) back.accept(p,s.machine);},() -> {});return;
        }
        Machine m=engine.snapshot().machines().get(s.machine);
        if(action.equals("deposit") && (e.getClick()==ClickType.LEFT || e.getClick()==ClickType.RIGHT)) {
            if(!validFuel(p,m,p.getItemOnCursor())) return;
            transfers.fuelFromCursor(p,m.id(),engine.rules().fuelItem(m),e.isRightClick()?1:p.getItemOnCursor().getAmount(),
                    () -> current(p,s),() -> redraw(p,s));
        } else if(action.equals("withdraw")) {
            if(e.getClick()!=ClickType.LEFT && e.getClick()!=ClickType.RIGHT && e.getClick()!=ClickType.SHIFT_LEFT && e.getClick()!=ClickType.SHIFT_RIGHT) return;
            boolean single=e.isRightClick(),toCursor=!e.isShiftClick();
            tasks.player(p,() -> {
                if(!current(p,s)) return;
                int amount=single?1:items.build(p,engine.rules().fuelItem(m),1,0).getMaxStackSize();
                transfers.take(p,new ContainerStock.Source(ContainerStock.Kind.FUEL,m.id(),engine.rules().fuelItem(m)),amount,
                        () -> current(p,s),() -> redraw(p,s),toCursor);
            },() -> {});
        }
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof FuelSession s)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !current(p,s)) return;
        if(e.getRawSlots().stream().allMatch(slot -> slot>=s.inventory.getSize())) {e.setCancelled(false);return;}
        // Mixed player/top drags stay cancelled; no partial vanilla movement can race the escrow.
        if(e.getRawSlots().stream().anyMatch(slot -> !"deposit".equals(s.actions.get(slot)))) return;
        Machine m=engine.snapshot().machines().get(s.machine);
        if(!validFuel(p,m,e.getOldCursor())) return;
        int amount=e.getNewItems().values().stream().mapToInt(org.bukkit.inventory.ItemStack::getAmount).sum();
        transfers.fuelFromCursor(p,m.id(),engine.rules().fuelItem(m),amount,() -> current(p,s),() -> redraw(p,s));
    }
    @EventHandler public void close(InventoryCloseEvent e) {if(e.getInventory().getHolder() instanceof FuelSession s) sessions.remove(e.getPlayer().getUniqueId(),s);}
    @EventHandler public void quit(PlayerQuitEvent e) {sessions.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void unload(WorldUnloadEvent e) {
        sessions.entrySet().removeIf(entry -> {Machine m=engine.snapshot().machines().get(entry.getValue().machine);return m==null || m.position().world().equals(e.getWorld().getUID());});
    }
    public void refresh() {
        for(var s:sessions.values()) tasks.player(s.viewer,() -> {
            if(sessions.get(s.viewer.getUniqueId())!=s) return;
            if(!current(s.viewer,s)) {s.viewer.closeInventory();sessions.remove(s.viewer.getUniqueId(),s);return;}
            redraw(s.viewer,s);
        },() -> sessions.remove(s.viewer.getUniqueId(),s));
    }
    public void clear() {sessions.clear();}
}
