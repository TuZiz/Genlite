package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.platform.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/** Cursor/selected-slot escrow runs on the next player tick and is refunded on transaction rejection. */
final class ItemDeposits {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final CraftItems items;
    private final Messages messages;
    private final ContainerStock stock;
    private final Set<UUID> busy;
    ItemDeposits(JavaPlugin p,GameTasks t,MachineEngine e,CraftItems i,Messages m,ContainerStock s,Set<UUID> busy) {plugin=p;tasks=t;engine=e;items=i;messages=m;stock=s;this.busy=busy;}
    void deposit(Player p,UUID machine,String id,int requested,BooleanSupplier allowed,Runnable refresh) {
        deposit(p,new ContainerStock.Source(ContainerStock.Kind.COLLECTOR,machine,id),requested,allowed,refresh);
    }
    void deposit(Player p,ContainerStock.Source source,int requested,BooleanSupplier allowed,Runnable refresh) {
        deposit(p,source,requested,-1,allowed,refresh);
    }
    void fromSlot(Player p,ContainerStock.Source source,int requested,int slot,BooleanSupplier allowed,Runnable refresh) {
        if(slot<0 || slot>=36 || source.kind()!=ContainerStock.Kind.FUEL) return;
        deposit(p,source,requested,slot,allowed,refresh);
    }
    private void deposit(Player p,ContainerStock.Source source,int requested,int slot,BooleanSupplier allowed,Runnable refresh) {
        tasks.player(p,() -> {
            if(!engine.ready() || !allowed.getAsBoolean()) return;
            String id=source.item();
            UUID user=p.getUniqueId();ItemStack unit;
            try {unit=items.build(p,id,1,0);} catch(Exception error) {messages.send(p,"unsupported-item");return;}
            if(!busy.add(user)) {messages.send(p,"busy");return;}
            ItemStack cursor=slot<0?p.getItemOnCursor():p.getInventory().getItem(slot);
            if(cursor==null || cursor.isEmpty() || !cursor.isSimilar(unit)) {busy.remove(user);messages.send(p,source.kind()==ContainerStock.Kind.FUEL?"fuel-invalid":"unsupported-item");return;}
            int capacity=source.kind()==ContainerStock.Kind.FUEL?engine.rules().fuelSpace(engine.snapshot().machines().get(source.id())):Integer.MAX_VALUE;
            int count=Math.min(capacity,Math.min(requested,cursor.getAmount()));if(count<=0) {busy.remove(user);messages.send(p,"full");return;}
            ItemStack held=cursor.clone();held.setAmount(count);cursor=cursor.clone();cursor.setAmount(cursor.getAmount()-count);
            if(slot<0) p.setItemOnCursor(cursor);else p.getInventory().setItem(slot,cursor.isEmpty()?null:cursor);
            engine.transact(d -> {stock.deposit(d,user,source,count);return null;}).whenComplete((v,error) -> tasks.player(p,() -> {
                busy.remove(user);
                if(error!=null) {
                    int toCursor=slot<0?Math.min(count,ItemDelivery.cursorCapacity(p,unit)):0;
                    if(toCursor>0) ItemDelivery.cursorGive(p,unit,toCursor);
                    int rest=count-toCursor;if(rest>0) {held.setAmount(rest);p.getInventory().addItem(held).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(),left));}
                    messages.send(p,InventoryTransfers.errorKey(error));
                }
                if(allowed.getAsBoolean()) refresh.run();
            },() -> {
                busy.remove(user);
                if(error!=null) engine.transact(d -> {d.parcel(user,id,count,0);return null;}).exceptionally(failure -> {plugin.getLogger().log(Level.SEVERE,"Item deposit refund needs recovery player="+user+", source="+source+", amount="+count,failure);return null;});
            }));
        },() -> {});
    }
}
