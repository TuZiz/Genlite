package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.platform.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/** Cursor escrow is removed on the next player tick, persisted asynchronously, and refunded on rejection. */
final class CursorDeposits {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final CraftItems items;
    private final Messages messages;
    private final ContainerStock stock;
    private final Set<UUID> busy;
    CursorDeposits(JavaPlugin p,GameTasks t,MachineEngine e,CraftItems i,Messages m,ContainerStock s,Set<UUID> busy) {plugin=p;tasks=t;engine=e;items=i;messages=m;stock=s;this.busy=busy;}
    void deposit(Player p,UUID machine,String id,int requested,BooleanSupplier allowed,Runnable refresh) {
        tasks.player(p,() -> {
            if(!engine.ready() || !allowed.getAsBoolean()) return;
            UUID user=p.getUniqueId();ItemStack unit;
            try {unit=items.build(p,id,1,0);} catch(Exception error) {messages.send(p,"unsupported-item");return;}
            if(!busy.add(user)) {messages.send(p,"busy");return;}
            ItemStack cursor=p.getItemOnCursor();
            if(!cursor.isSimilar(unit)) {busy.remove(user);messages.send(p,"unsupported-item");return;}
            int count=Math.min(requested,cursor.getAmount());if(count<=0) {busy.remove(user);return;}
            ItemStack held=cursor.clone();held.setAmount(count);cursor=cursor.clone();cursor.setAmount(cursor.getAmount()-count);p.setItemOnCursor(cursor);
            engine.transact(d -> {stock.deposit(d,user,new ContainerStock.Source(ContainerStock.Kind.COLLECTOR,machine,id),count);return null;}).whenComplete((v,error) -> tasks.player(p,() -> {
                busy.remove(user);
                if(error!=null) {
                    int toCursor=Math.min(count,ItemDelivery.cursorCapacity(p,unit));
                    if(toCursor>0) ItemDelivery.cursorGive(p,unit,toCursor);
                    int rest=count-toCursor;if(rest>0) {held.setAmount(rest);p.getInventory().addItem(held).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(),left));}
                    messages.send(p,InventoryTransfers.errorKey(error));
                }
                if(allowed.getAsBoolean()) refresh.run();
            },() -> {
                busy.remove(user);
                if(error!=null) engine.transact(d -> {d.parcel(user,id,count,0);return null;}).exceptionally(failure -> {plugin.getLogger().log(Level.SEVERE,"Cursor refund needs recovery player="+user+", item="+id+", amount="+count,failure);return null;});
            }));
        },() -> {});
    }
}
