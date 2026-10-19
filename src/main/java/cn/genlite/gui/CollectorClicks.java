package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.platform.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Player owner thread. Normal stacks: left whole, right half; shift transfers to backpack. */
final class CollectorClicks {
    private final MachineEngine engine;
    private final CraftItems items;
    private final Messages messages;
    private final InventoryTransfers transfers;
    private final ContainerStock stock;
    CollectorClicks(MachineEngine e,CraftItems i,Messages m,InventoryTransfers t,ContainerStock s) {engine=e;items=i;messages=m;transfers=t;stock=s;}
    void click(InventoryClickEvent event,Player p,ContainerSession s,BooleanSupplier allowed,Runnable refresh) {
        int slot=event.getRawSlot();if(slot<0) return;
        if(slot>=s.inventory.getSize()) {
            if(!event.isShiftClick()) {if(event.getClick()==ClickType.LEFT || event.getClick()==ClickType.RIGHT) event.setCancelled(false);return;}
            var stack=event.getCurrentItem();if(stack==null || stack.isEmpty()) return;
            String item=items.id(stack);if(!item.matches("(minecraft|genlite):[a-z0-9_]+")) {messages.send(p,"unsupported-item");return;}var unit=items.build(p,item,1,0);
            if(!unit.isSimilar(stack) || Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull).anyMatch(i -> items.id(i).equals(item)&&!unit.isSimilar(i))) {messages.send(p,"unsupported-item");return;}
            Machine machine=engine.snapshot().machines().get(s.home);
            int count=Math.min(stack.getAmount(),engine.rules().capacityFor(machine.inventory(),item,27));
            if(count<=0) {messages.send(p,"full");return;}
            transfers.paid(p,Map.of(item,count),d -> {stock.deposit(d,p.getUniqueId(),new ContainerStock.Source(s.kind,s.home,item),count);return null;},refresh);return;
        }
        String action=s.actions.getOrDefault(slot,"none");if(action.equals("close")) {p.closeInventory();return;}
        if(!action.equals("stock")) return;
        var cursor=p.getItemOnCursor();String item=s.stock.get(slot);int count=s.counts.getOrDefault(slot,0);
        if(!cursor.isEmpty() && !event.isShiftClick()) {
            String id=items.id(cursor);if(!id.matches("(minecraft|genlite):[a-z0-9_]+") || (item!=null && !id.equals(item))) return;
            int amount=event.getClick()==ClickType.RIGHT&&count<64?1:event.getClick()==ClickType.LEFT?Math.min(cursor.getAmount(),64-count):0;
            if(amount>0) transfers.fromCursor(p,s.home,id,amount,allowed,refresh);return;
        }
        if(item==null) return;
        int amount=switch(event.getClick()) {case LEFT,SHIFT_LEFT,SHIFT_RIGHT -> count;case RIGHT -> (count+1)/2;default -> 0;};
        if(amount>0) transfers.take(p,new ContainerStock.Source(s.kind,s.home,item),amount,allowed,refresh,!event.isShiftClick());
    }
    void drag(InventoryDragEvent event,Player p,ContainerSession s,BooleanSupplier allowed,Runnable refresh) {
        if(event.getRawSlots().stream().allMatch(slot -> slot>=s.inventory.getSize())) {event.setCancelled(false);return;}
        String id=items.id(event.getOldCursor());if(!id.matches("(minecraft|genlite):[a-z0-9_]+")) return;int amount=0;
        for(var entry:event.getNewItems().entrySet()) {
            int slot=entry.getKey();if(slot>=s.inventory.getSize()) return;
            if(!"stock".equals(s.actions.get(slot)) || (s.stock.containsKey(slot)&&!id.equals(s.stock.get(slot))) || entry.getValue().getAmount()>64) return;
            amount+=entry.getValue().getAmount()-s.counts.getOrDefault(slot,0);
        }
        if(amount>0) transfers.fromCursor(p,s.home,id,amount,allowed,refresh);
    }
}
