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

/** Fuel chest session lifecycle. Contents are a live projection of the existing transactional machine stock. */
public final class FuelMenus implements Listener {
    private final MachineEngine engine;
    private final Messages messages;
    private final GameTasks tasks;
    private final BiPredicate<Player,Machine> permission;
    private final BiConsumer<Player,UUID> back;
    private final FuelMenuRenderer renderer;
    private final FuelChestClicks clicks;
    private final Map<UUID,FuelSession> sessions=new ConcurrentHashMap<>();
    public FuelMenus(YamlConfiguration config,MachineEngine engine,CraftItems items,Messages messages,InventoryTransfers transfers,
                     GameTasks tasks,MachineText text,BiPredicate<Player,Machine> permission,BiConsumer<Player,UUID> back) {
        this.engine=engine;this.messages=messages;this.tasks=tasks;
        this.permission=permission;this.back=back;renderer=new FuelMenuRenderer(config,engine,messages,text,items);
        clicks=new FuelChestClicks(engine,items,messages,transfers,tasks);
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
    @EventHandler public void click(InventoryClickEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof FuelSession s)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !current(p,s)) return;
        int raw=e.getRawSlot();if(raw<0) return;
        String action=s.actions.getOrDefault(raw,"none");
        if(action.equals("back") && (e.getClick()==ClickType.LEFT || e.getClick()==ClickType.RIGHT)) {
            tasks.player(p,() -> {if(current(p,s)) back.accept(p,s.machine);},() -> {});return;
        }
        clicks.click(e,p,s,() -> current(p,s),() -> redraw(p,s));
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if(!(e.getView().getTopInventory().getHolder() instanceof FuelSession s)) return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !current(p,s)) return;
        clicks.drag(e,p,s,() -> current(p,s),() -> redraw(p,s));
    }
    @EventHandler public void close(InventoryCloseEvent e) {
        if(e.getInventory().getHolder() instanceof FuelSession s) {
            e.getInventory().clear();sessions.remove(e.getPlayer().getUniqueId(),s);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e) {
        var s=sessions.remove(e.getPlayer().getUniqueId());if(s!=null) s.inventory.clear();
    }
    @EventHandler public void unload(WorldUnloadEvent e) {
        for(var s:sessions.values()) {
            Machine m=engine.snapshot().machines().get(s.machine);
            if(m!=null && !m.position().world().equals(e.getWorld().getUID())) continue;
            tasks.player(s.viewer,() -> {
                if(sessions.get(s.viewer.getUniqueId())!=s) return;
                s.inventory.clear();
                if(s.viewer.getOpenInventory().getTopInventory().getHolder()==s) s.viewer.closeInventory();
                sessions.remove(s.viewer.getUniqueId(),s);
            },() -> sessions.remove(s.viewer.getUniqueId(),s));
        }
    }
    public void refresh() {
        for(var s:sessions.values()) tasks.player(s.viewer,() -> {
            if(sessions.get(s.viewer.getUniqueId())!=s) return;
            if(!current(s.viewer,s)) {s.viewer.closeInventory();sessions.remove(s.viewer.getUniqueId(),s);return;}
            redraw(s.viewer,s);
        },() -> sessions.remove(s.viewer.getUniqueId(),s));
    }
    public void clear() {
        int unowned=0;
        for(var s:List.copyOf(sessions.values())) {
            if(org.bukkit.Bukkit.isOwnedByCurrentRegion(s.viewer)) {
                s.inventory.clear();
                if(s.viewer.getOpenInventory().getTopInventory().getHolder()==s) s.viewer.closeInventory();
            } else unowned++;
        }
        sessions.clear();
        // Normal server shutdown closes player views first. Folia hot unload has no valid player scheduler here.
        if(unowned>0) org.bukkit.Bukkit.getLogger().warning("[Genlite] Fuel views outside shutdown ownership: "+unowned+"; Folia hot unload is unsupported. Close player menus before disabling.");
    }
}
