package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.platform.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import java.util.function.BooleanSupplier;

/** Player scheduler only; cancel vanilla mutations and translate chest gestures into existing durable transfers. */
final class FuelChestClicks {
    private final MachineEngine engine;
    private final CraftItems items;
    private final Messages messages;
    private final InventoryTransfers transfers;
    private final GameTasks tasks;
    FuelChestClicks(MachineEngine engine,CraftItems items,Messages messages,InventoryTransfers transfers,GameTasks tasks) {
        this.engine=engine;this.items=items;this.messages=messages;this.transfers=transfers;this.tasks=tasks;
    }
    private boolean validFuel(Player p,Machine m,ItemStack stack) {
        if(stack==null || stack.isEmpty()) return false;
        if(!items.build(p,engine.rules().fuelItem(m),1,0).isSimilar(stack)) {messages.send(p,"fuel-invalid");return false;}
        return true;
    }
    void click(InventoryClickEvent e,Player p,FuelSession s,BooleanSupplier current,Runnable refresh) {
        int raw=e.getRawSlot();if(raw<0) return;
        Machine m=engine.snapshot().machines().get(s.machine);
        if(raw>=s.inventory.getSize()) {
            if(e.getClick()==ClickType.LEFT || e.getClick()==ClickType.RIGHT) {e.setCancelled(false);return;}
            if(!shift(e.getClick())) return;
            int slot=e.getSlot();if(slot<0 || slot>=36) return;
            if(validFuel(p,m,e.getCurrentItem())) transfers.fuelFromSlot(p,m.id(),engine.rules().fuelItem(m),slot,current,refresh);
            return;
        }
        if(!"fuel-slot".equals(s.actions.get(raw))) return;
        boolean shift=shift(e.getClick());
        if(!shift && e.getClick()!=ClickType.LEFT && e.getClick()!=ClickType.RIGHT) return;
        ItemStack cursor=p.getItemOnCursor();
        if(!shift && cursor!=null && !cursor.isEmpty()) {
            if(!validFuel(p,m,cursor)) return;
            int space=cursor.getMaxStackSize()-s.counts.getOrDefault(raw,0);
            int count=Math.min(space,e.isRightClick()?1:cursor.getAmount());
            if(count>0) transfers.fuelFromCursor(p,m.id(),engine.rules().fuelItem(m),count,current,refresh);
            return;
        }
        int displayed=s.counts.getOrDefault(raw,0);
        if(displayed<=0) return;
        int requested=FuelStacks.withdrawal(displayed,e.getClick()==ClickType.RIGHT);
        tasks.player(p,() -> {
            if(!current.getAsBoolean()) return;
            Machine latest=engine.snapshot().machines().get(s.machine);
            var unit=items.build(p,engine.rules().fuelItem(latest),1,0);
            var counts=FuelStacks.visible(latest.fuel(),engine.rules().fuelUnits(latest),unit.getMaxStackSize(),s.fuelSlots.size());
            int limit=Math.min(requested,counts.get(s.fuelSlots.get(raw)));
            if(limit<=0) {refresh.run();return;}
            transfers.take(p,new ContainerStock.Source(ContainerStock.Kind.FUEL,m.id(),engine.rules().fuelItem(latest)),limit,current,refresh,!shift);
        },() -> {});
    }
    void drag(InventoryDragEvent e,Player p,FuelSession s,BooleanSupplier current,Runnable refresh) {
        if(e.getRawSlots().stream().allMatch(slot -> slot>=s.inventory.getSize())) {e.setCancelled(false);return;}
        // A mixed drag remains cancelled: escrow never races partial vanilla movement in the player's backpack.
        if(e.getRawSlots().stream().anyMatch(slot -> !"fuel-slot".equals(s.actions.get(slot)))) return;
        Machine m=engine.snapshot().machines().get(s.machine);
        if(!validFuel(p,m,e.getOldCursor())) return;
        var after=new java.util.HashMap<Integer,Integer>();
        e.getNewItems().forEach((slot,stack) -> after.put(slot,stack.getAmount()));
        int count=FuelStacks.dragAddition(s.counts,after);
        if(count>0) transfers.fuelFromCursor(p,m.id(),engine.rules().fuelItem(m),count,current,refresh);
    }
    private boolean shift(ClickType type) {return type==ClickType.SHIFT_LEFT || type==ClickType.SHIFT_RIGHT;}
}
