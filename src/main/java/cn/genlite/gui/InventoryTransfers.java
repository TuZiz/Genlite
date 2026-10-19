package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.platform.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;

/** Bridges player inventory to domain transactions. All ItemStack access stays on player threads. */
public final class InventoryTransfers {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final CraftItems items;
    private final Messages messages;
    private final Set<UUID> busy=ConcurrentHashMap.newKeySet();
    private final DirectWithdrawals direct;
    private final CursorDeposits cursor;
    public InventoryTransfers(JavaPlugin p,GameTasks t,MachineEngine e,CraftItems i,Messages m,java.util.function.BiPredicate<UUID,UUID> member) {
        plugin=p;tasks=t;engine=e;items=i;messages=m;
        direct=new DirectWithdrawals(p,t,e,i,m,new ContainerStock(e.rules(),member),busy);
        cursor=new CursorDeposits(p,t,e,i,m,new ContainerStock(e.rules(),member),busy);
    }
    public void take(Player p,ContainerStock.Source source,int limit,java.util.function.BooleanSupplier allowed,Runnable refresh) {direct.take(p,source,limit,allowed,refresh);}
    public void take(Player p,ContainerStock.Source source,int limit,java.util.function.BooleanSupplier allowed,Runnable refresh,boolean toCursor) {direct.take(p,source,limit,allowed,refresh,toCursor);}
    public void fromCursor(Player p,UUID machine,String item,int amount,java.util.function.BooleanSupplier allowed,Runnable refresh) {cursor.deposit(p,machine,item,amount,allowed,refresh);}
    public void recover(Player p) {direct.recover(p);}

    public void mutate(Player player,Function<WorldState.Draft,Void> action,Runnable after) {
        paid(player,Map.of(),action,after);
    }
    public void paid(Player player,Map<String,Integer> costs,Function<WorldState.Draft,Void> action,Runnable after) {
        if (!engine.ready()) { messages.send(player,"unavailable"); return; }
        UUID user=player.getUniqueId();
        if (!busy.add(user)) { messages.send(player,"busy"); return; }
        Map<String,Integer> available=new HashMap<>();
        for (ItemStack item:player.getInventory().getStorageContents()) if (item!=null) available.merge(items.id(item),item.getAmount(),Integer::sum);
        if (costs.entrySet().stream().anyMatch(e -> available.getOrDefault(e.getKey(),0)<e.getValue())) {
            busy.remove(user);messages.send(player,"materials");return;
        }
        Map<String,Integer> pending=new HashMap<>(costs);
        List<ItemStack> escrow=new ArrayList<>();
        for (int slot=0;slot<36;slot++) {
            ItemStack item=player.getInventory().getItem(slot);
            String id=items.id(item); int need=pending.getOrDefault(id,0);
            if (need<=0 || item==null) continue;
            int count=Math.min(need,item.getAmount());
            ItemStack held=item.clone();held.setAmount(count);escrow.add(held);
            item.setAmount(item.getAmount()-count);player.getInventory().setItem(slot,item.isEmpty()?null:item);
            pending.put(id,need-count);
        }
        engine.transact(action).whenComplete((value,error) -> {
            if (error!=null) plugin.getLogger().log(Level.WARNING,"Player machine transaction rejected: player="+user+", refund="+costs,error);
            tasks.player(player,() -> {
                busy.remove(user);
                if (error!=null) {
                    for (ItemStack item:escrow) player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(),left));
                    messages.send(player,errorKey(error));
                } else { messages.send(player,"done");after.run(); }
            },() -> {
                busy.remove(user);
                if (error!=null && !costs.isEmpty()) engine.transact(d -> { costs.forEach((id,n) -> d.parcel(user,id,n,0));return null; })
                        .exceptionally(e -> { plugin.getLogger().log(Level.SEVERE,"REFUND REQUIRES RECOVERY player="+user+", items="+costs,e);return null; });
            });
        });
    }
    public void claim(Player player) {direct.recover(player);}
    public static String errorKey(Throwable error) {
        while (error.getCause()!=null) error=error.getCause();
        String key=error.getMessage();
        return Set.of("permission","locked","missing","busy","full","empty","max-level","range","invalid-shell","inventory-full","unsupported-item","collector-required").contains(key==null?"":key)?key:"unavailable";
    }
    public void clear() { busy.clear(); }
}
