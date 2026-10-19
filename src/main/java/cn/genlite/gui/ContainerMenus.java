package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.integration.IslandAccess;
import cn.genlite.platform.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Collector chest and aggregate warehouse sessions; rendering and click behavior are separate. */
public final class ContainerMenus implements Listener {
    private final Map<String,YamlConfiguration> configs;
    private final MachineEngine engine;
    private final IslandAccess islands;
    private final CraftItems items;
    private final Messages messages;
    private final InventoryTransfers transfers;
    private final ContainerStock actions;
    private final ContainerMenuRenderer renderer;
    private final CollectorClicks collectorClicks;
    private final GameTasks tasks;
    private final Map<UUID,ContainerSession> sessions=new ConcurrentHashMap<>();
    public ContainerMenus(Map<String,YamlConfiguration> configs,MachineEngine engine,IslandAccess islands,CraftItems items,Messages messages,InventoryTransfers transfers,GameTasks tasks,cn.genlite.display.MachineText text) {
        this.tasks=tasks;
        this.configs=configs;this.engine=engine;this.islands=islands;this.items=items;this.messages=messages;this.transfers=transfers;actions=new ContainerStock(engine.rules(),islands::member);renderer=new ContainerMenuRenderer(engine,items,messages,text);collectorClicks=new CollectorClicks(engine,items,messages,transfers,actions);
    }
    private UUID home(Player p) {return islands.homeId(MachinePresence.position(p.getLocation()));}
    private boolean allowed(Player p,ContainerSession s) {
        if(!engine.ready() || !p.hasPermission("genlite.use") || !s.world.equals(p.getWorld().getUID())) return false;
        if(s.kind==ContainerStock.Kind.WAREHOUSE) return s.home.equals(home(p))&&islands.member(s.home,p.getUniqueId());
        Machine m=engine.snapshot().machines().get(s.home);
        return m!=null&&!m.removed()&&islands.member(m.home(),p.getUniqueId())&&(!m.locked()||m.owner().equals(p.getUniqueId()))
                && p.getLocation().distanceSquared(new Location(p.getWorld(),m.position().x()+.5,m.position().y()+.5,m.position().z()+.5))<=64;
    }
    public void open(Player p) {UUID home=home(p);if(home==null) {messages.send(p,"world");return;}open(p,home,ContainerStock.Kind.WAREHOUSE);}
    public void collector(Player p,UUID machine) {open(p,machine,ContainerStock.Kind.COLLECTOR);}
    private void open(Player p,UUID id,ContainerStock.Kind kind) {
        var s=new ContainerSession(id,p.getUniqueId(),p.getWorld().getUID(),kind);
        s.viewer=p;
        if(!allowed(p,s)) {messages.send(p,"permission");return;}
        renderer.draw(p,s,configs.get(kind==ContainerStock.Kind.WAREHOUSE?"warehouse":"collector"));
        p.openInventory(s.inventory);sessions.put(p.getUniqueId(),s);transfers.recover(p);
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!(event.getView().getTopInventory().getHolder() instanceof ContainerSession s)) return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p) || sessions.get(p.getUniqueId())!=s) return;
        if(!allowed(p,s)) {p.closeInventory();messages.send(p,"permission");return;}
        if(s.kind==ContainerStock.Kind.COLLECTOR) {collectorClicks.click(event,p,s,() -> sessions.get(p.getUniqueId())==s&&allowed(p,s),() -> reopen(p,s));return;}
        if(event.getRawSlot()>=s.inventory.getSize()) {
            if(!event.isShiftClick() || event.getCurrentItem()==null) return;
            var stack=event.getCurrentItem();String item=items.id(stack);
            if(!item.matches("(minecraft|genlite):[a-z0-9_]+")) {messages.send(p,"unsupported-item");return;}
            var canonical=items.build(p,item,1,0);
            // ID-based storage accepts canonical resources, never silently strips enchantments, damage or custom metadata.
            if(!canonical.isSimilar(stack) || Arrays.stream(p.getInventory().getStorageContents()).filter(Objects::nonNull).anyMatch(i -> items.id(i).equals(item)&&!canonical.isSimilar(i))) {messages.send(p,"unsupported-item");return;}
            int count=stack.getAmount();
            transfers.paid(p,Map.of(item,count),d -> {actions.deposit(d,p.getUniqueId(),new ContainerStock.Source(s.kind,s.home,item),count);return null;},() -> reopen(p,s));return;
        }
        String action=s.actions.getOrDefault(event.getRawSlot(),"none");
        if(action.equals("close")) {p.closeInventory();return;}
        int amount=switch(event.getClick()) {case LEFT -> 1;case RIGHT -> 10;case SHIFT_LEFT -> 64;case SHIFT_RIGHT -> Integer.MAX_VALUE;default -> 0;};
        String item=s.stock.get(event.getRawSlot());if(item==null || amount==0) return;
        if(event.getClick()==ClickType.SHIFT_LEFT) amount=items.build(p,item,1,0).getMaxStackSize();
        transfers.take(p,new ContainerStock.Source(s.kind,s.home,item),amount,() -> allowed(p,s),() -> reopen(p,s));
    }
    private void reopen(Player p,ContainerSession s) {if(sessions.get(p.getUniqueId())!=s||!allowed(p,s)) return;if(s.kind==ContainerStock.Kind.COLLECTOR) renderer.draw(p,s,configs.get("collector"));else open(p,s.home,s.kind);}
    @EventHandler public void drag(InventoryDragEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof ContainerSession s)) return;e.setCancelled(true);
        if(e.getWhoClicked() instanceof Player p && s.kind==ContainerStock.Kind.COLLECTOR && sessions.get(p.getUniqueId())==s && allowed(p,s))
            collectorClicks.drag(e,p,s,() -> sessions.get(p.getUniqueId())==s&&allowed(p,s),() -> reopen(p,s));
    }
    @EventHandler public void close(InventoryCloseEvent e) {if(e.getInventory().getHolder() instanceof ContainerSession s) sessions.remove(e.getPlayer().getUniqueId(),s);}
    @EventHandler public void quit(PlayerQuitEvent e) {sessions.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void unload(WorldUnloadEvent e) {sessions.entrySet().removeIf(entry -> entry.getValue().world.equals(e.getWorld().getUID()));}
    public void clear() {sessions.clear();}
    public void refresh() {
        for(var s:sessions.values()) tasks.player(s.viewer,() -> {
            if(sessions.get(s.player)!=s) return;
            if(!allowed(s.viewer,s)) {s.viewer.closeInventory();return;}
            renderer.draw(s.viewer,s,configs.get(s.kind==ContainerStock.Kind.COLLECTOR?"collector":"warehouse"));
        },() -> sessions.remove(s.player,s));
    }
}
