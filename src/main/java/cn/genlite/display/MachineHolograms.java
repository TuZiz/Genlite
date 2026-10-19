package cn.genlite.display;

import cn.genlite.domain.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** One nonpersistent TextDisplay per loaded machine. Update/remove on its owning region only. */
public final class MachineHolograms implements Listener,AutoCloseable {
    private final JavaPlugin plugin;
    private final MachineEngine engine;
    private final MachineText text;
    private final NamespacedKey marker;
    private final Map<UUID,TextDisplay> displays=new ConcurrentHashMap<>();
    private volatile boolean closed;
    public MachineHolograms(JavaPlugin plugin,MachineEngine engine,MachineText text) {
        this.plugin=plugin;this.engine=engine;this.text=text;marker=new NamespacedKey(plugin,"hologram");
    }
    public void update(Machine m,Location block,boolean present) {
        var config=text.display(m.type()).hologram();
        if(closed || m.removed() || !present || !config.enabled() || !engine.snapshot().hologramsEnabled(m.home()) || config.lines().isEmpty()) {remove(m.id());return;}
        TextDisplay display=displays.get(m.id());
        if(display==null || !display.isValid()) {
            Location position=block.clone().add(0.5,config.height(),0.5);
            display=block.getWorld().spawn(position,TextDisplay.class,entity -> {
                entity.setPersistent(false);entity.setGravity(false);entity.setInvulnerable(true);
                entity.setBillboard(Display.Billboard.CENTER);entity.setShadowed(false);entity.setSeeThrough(false);
                entity.setDefaultBackground(false);entity.setBackgroundColor(Color.fromARGB(0,0,0,0));
                entity.setViewRange(0.5f);entity.setLineWidth(256);
                entity.setTransformationMatrix(new org.joml.Matrix4f().scaling((float)config.scale()));
                entity.getPersistentDataContainer().set(marker,PersistentDataType.STRING,m.id().toString());
            });
            displays.put(m.id(),display);
        }
        var rendered=text.hologram(m,engine.snapshot(),engine.active());
        if(!rendered.equals(display.text())) display.text(rendered);
    }
    public void remove(UUID machine) {
        TextDisplay display=displays.remove(machine);
        if(display==null) return;
        if(Bukkit.isOwnedByCurrentRegion(display)) display.remove();
        else display.getScheduler().execute(plugin,display::remove,null,1);
    }
    @EventHandler public void unload(EntitiesUnloadEvent event) {
        for(Entity entity:event.getEntities()) {
            String machine=entity.getPersistentDataContainer().get(marker,PersistentDataType.STRING);
            if(machine==null) continue;
            displays.remove(UUID.fromString(machine),entity);entity.remove();
        }
    }
    @Override public void close() {
        closed=true;int deferred=0;
        for(TextDisplay display:displays.values()) {
            if(Bukkit.isOwnedByCurrentRegion(display)) display.remove();
            else deferred++;
        }
        displays.clear();
        // Folia disable runs outside owning regions: these entities are never saved and leave with their chunks/world.
        if(deferred>0) plugin.getLogger().info("Holograms outside shutdown ownership: "+deferred+"; nonpersistent, discarded with chunk/world unload. Hot unload is unsupported.");
    }
}
