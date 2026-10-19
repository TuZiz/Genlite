package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.integration.IslandAccess;
import cn.genlite.platform.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** GUI callbacks only validate, prepare parameters and call transactional services. */
public final class MachineMenus implements Listener {
    private final Map<String,YamlConfiguration> menus;
    private final MachineEngine engine;
    private final MachineActions actions;
    private final IslandAccess dream;
    private final CraftItems items;
    private final Messages messages;
    private final InventoryTransfers transfers;
    private final cn.genlite.display.MachineText text;
    private final ContainerMenus warehouse;
    private final Map<UUID,MenuSession> sessions=new java.util.concurrent.ConcurrentHashMap<>();
    public MachineMenus(Map<String,YamlConfiguration> configs,MachineEngine e,MachineActions a,IslandAccess d,CraftItems i,Messages m,InventoryTransfers t,cn.genlite.display.MachineText text,ContainerMenus warehouse) {
        menus=configs;engine=e;actions=a;dream=d;items=i;messages=m;transfers=t;this.text=text;this.warehouse=warehouse;
    }
    public boolean allowed(Player p,Machine m) {
        return m!=null && !m.removed() && p.hasPermission("genlite.use") && dream.member(m.home(),p.getUniqueId())
                && (!m.locked() || m.owner().equals(p.getUniqueId()))
                && p.getWorld().getUID().equals(m.position().world())
                && p.getLocation().distanceSquared(new org.bukkit.Location(p.getWorld(),m.position().x()+0.5,m.position().y()+0.5,m.position().z()+0.5))<=64;
    }
    public void open(Player p,UUID id,String requested) {
        Machine m=engine.snapshot().machines().get(id);
        if(!engine.ready() || !allowed(p,m)) {messages.send(p,"permission");return;}
        if(m.collectorType()) {warehouse.collector(p,id);return;}
        String menu="generator";
        YamlConfiguration y=menus.get(menu);
        Map<String,Component> vars=variables(p,m);
        MenuSession session=new MenuSession(p.getUniqueId(),id,menu);
        List<String> rows=new ArrayList<>(y.getStringList("shape"));
        var inventory=Bukkit.createInventory(session,rows.size()*9,messages.render(y.getString("title"),vars));
        session.inventory(inventory);
        String shape=String.join("",rows);
        for(int slot=0;slot<shape.length();slot++) {
            char symbol=shape.charAt(slot);if(symbol==' ') continue;
            String path="elements."+symbol+".";
            String action=y.getString(path+"action","none");session.actions.put(slot,action);
            if(action.equals("stock")) continue;
            ItemStack stack=y.contains(path+"item")?items.build(p,y.getString(path+"item"),1,0):new ItemStack(Material.valueOf(y.getString(path+"material")));
            var meta=stack.getItemMeta();
            meta.itemName(messages.render(y.getString(path+"name"),vars));
            List<Component> lore=new ArrayList<>();
            for(String line:y.getStringList(path+"lore")) lore.add(messages.render(line,vars));
            if(action.equals("upgrade") && m.level()<5) engine.rules().upgrade(m).forEach((item,count) ->
                    lore.add(messages.format("upgrade-cost-line",Map.of("item",messages.label(item),"amount",Component.text(count),
                            "owned",Component.text(Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull)
                                    .filter(held -> items.id(held).equals(item)).mapToInt(ItemStack::getAmount).sum())))));
            meta.lore(lore);stack.setItemMeta(meta);inventory.setItem(slot,stack);
        }
        p.openInventory(inventory);sessions.put(p.getUniqueId(),session);
    }
    private Map<String,Component> variables(Player p,Machine m) {
        return text.variables(m,engine.snapshot(),engine.active());
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof MenuSession s)) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p) || sessions.get(p.getUniqueId())!=s || !s.player.equals(p.getUniqueId())) return;
        if(event.getRawSlot()<0 || event.getRawSlot()>=s.getInventory().getSize()) return;
        if(event.getClick()!=ClickType.LEFT && event.getClick()!=ClickType.RIGHT && event.getClick()!=ClickType.SHIFT_RIGHT) return;
        Machine m=engine.snapshot().machines().get(s.machine);
        if(!allowed(p,m)) {p.closeInventory();messages.send(p,"permission");return;}
        String action=s.actions.getOrDefault(event.getRawSlot(),"none");
        UUID user=p.getUniqueId();
        Runnable reopen=() -> {if(sessions.get(user)==s && allowed(p,engine.snapshot().machines().get(m.id()))) open(p,m.id(),s.menu);};
        switch(action) {
            case "refresh" -> reopen.run();
            case "close" -> p.closeInventory();
            case "warehouse" -> warehouse.open(p);
            case "mode" -> {if(event.isRightClick()) warehouse.open(p);else transfers.mutate(p,d -> {actions.toggle(d,m.id(),user,action);return null;},reopen);}
            case "lock","pause" -> transfers.mutate(p,d -> {actions.toggle(d,m.id(),user,action);return null;},reopen);
            case "dismantle" -> transfers.mutate(p,d -> {actions.dismantle(d,m.id(),user);return null;},p::closeInventory);
            case "upgrade" -> {
                if(m.level()>=5) {messages.send(p,"max-level");return;}
                transfers.paid(p,engine.rules().upgrade(m),d -> {actions.upgrade(d,m.id(),user,m.level());return null;},reopen);
            }
            case "fuel","input" -> {
                String item=action.equals("fuel")?engine.rules().fuelItem(m):engine.rules().types().get(m.type()).input();
                if(item==null) {messages.send(p,"unavailable");return;}
                if(event.isRightClick()) transfers.take(p,new ContainerStock.Source(ContainerStock.Kind.FUEL,m.id(),item),64,() -> allowed(p,engine.snapshot().machines().get(m.id())),reopen);
                else {
                    int capacity=action.equals("fuel")?engine.rules().level(m).fuelItems()-((m.fuel()+engine.rules().fuelUnits(m)-1)/engine.rules().fuelUnits(m)):engine.rules().level(m).bufferItems()-m.input();
                    int available=Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull).filter(stack -> items.id(stack).equals(item)).mapToInt(ItemStack::getAmount).sum();
                    int amount=Math.min(64,Math.min(capacity,available));
                    if(amount<=0) {messages.send(p,capacity<=0?"full":"materials");return;}
                    transfers.paid(p,Map.of(item,amount),d -> {actions.deposit(d,m.id(),user,action,amount,m.level());return null;},reopen);
                }
            }
            case "output" -> transfers.take(p,new ContainerStock.Source(ContainerStock.Kind.OUTPUT,m.id(),engine.rules().types().get(m.type()).output()),64,() -> allowed(p,engine.snapshot().machines().get(m.id())),reopen);
            default -> { }
        }
    }
    @EventHandler public void drag(InventoryDragEvent e) {if(e.getView().getTopInventory().getHolder() instanceof MenuSession) e.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent e) {if(e.getInventory().getHolder() instanceof MenuSession s) sessions.remove(e.getPlayer().getUniqueId(),s);}
    @EventHandler public void quit(PlayerQuitEvent e) {sessions.remove(e.getPlayer().getUniqueId());}
    public void clear() {sessions.clear();}
}
