package cn.genlite.platform;

import cn.genlite.domain.*;
import cn.genlite.integration.IslandAccess;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.core.block.ImmutableBlockState;
import net.momirealms.craftengine.core.block.property.Property;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Region probes give short leases; unloaded chunks are never loaded for production. */
public final class MachinePresence {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final MachineActions actions;
    private final IslandAccess dream;
    private final cn.genlite.display.MachineHolograms holograms;
    private final ShellDrops drops;
    private final Set<UUID> probing=ConcurrentHashMap.newKeySet();
    private final Set<UUID> normalized=ConcurrentHashMap.newKeySet();
    public MachinePresence(JavaPlugin p,GameTasks t,MachineEngine e,MachineActions a,IslandAccess d,cn.genlite.display.MachineHolograms h,ShellDrops drops) { plugin=p;tasks=t;engine=e;actions=a;dream=d;holograms=h;this.drops=drops; }
    public static Position position(Location l) { return new Position(l.getWorld().getUID(),l.getWorld().getKey().toString(),l.getBlockX(),l.getBlockY(),l.getBlockZ()); }
    public static String type(ImmutableBlockState state) {
        if (state==null) return null;
        String id=state.owner().value().id().toString();
        return id.startsWith("genlite:") ? id.substring(8) : null;
    }
    public void refresh() {
        for (Machine snapshot:engine.snapshot().machines().values()) {
            if (snapshot.removed() && snapshot.cleared()) continue;
            World world=plugin.getServer().getWorld(snapshot.position().world());
            if (world==null) { engine.lease(snapshot.id(),false); continue; }
            Position p=snapshot.position();
            Location location=new Location(world,p.x(),p.y(),p.z());
            probe(snapshot.id(),location,true);
        }
    }
    /** Safe to call from a completed IO transaction; platform reads remain on this location's region. */
    public void refresh(UUID id,Location location) {
        probe(id,location,false);
    }
    private void probe(UUID id,Location location,boolean periodic) {
        Machine snapshot=engine.snapshot().machines().get(id);
        if(snapshot==null || (snapshot.removed() && snapshot.cleared()) || (periodic && !probing.add(id))) return;
        Position p=snapshot.position();World world=location.getWorld();
        tasks.region(location,() -> {
            try {
                Machine m=engine.snapshot().machines().get(snapshot.id());
                if (m==null || !world.isChunkLoaded(p.x()>>4,p.z()>>4)) { engine.lease(snapshot.id(),false); return; }
                var state=CraftEngineBlocks.getCustomBlockState(location.getBlock());
                boolean match=m.type().equals(type(state));
                if (m.removed()) {
                    holograms.remove(m.id());
                    normalized.remove(m.id());
                    engine.lease(m.id(),false);
                    if (!m.cleared()) {
                        // CE five-argument API: movedByPiston=false, dropLoot=false, sendLevelEvent=true.
                        if (match && !CraftEngineBlocks.remove(location.getBlock(),null,false,false,true)) return;
                        engine.transact(d -> actions.cleared(d,m.id())).whenComplete((parcel,error) -> {
                            if(error!=null) plugin.getLogger().log(Level.WARNING,"Machine removal commit failed: "+m.id()+" at "+p,error);
                            else drops.deliver(parcel,location);
                        });
                    }
                    return;
                }
                if(!dream.active(m)) {engine.lease(m.id(),false);holograms.remove(m.id());return;}
                boolean space=m.collectorType() || m.mode()!=OutputMode.DROP || GeneratorDropSpace.available(
                        location.clone().add(0.5,1.05,0.5),engine.rules().types().get(m.type()).output(),engine.rules().level(m).count());
                engine.lease(m.id(),match && space);
                holograms.update(m,location,match);
                if (!match) return;
                if(!normalized.contains(m.id()) && CraftEngineBlocks.place(location,state,false)) normalized.add(m.id());
                String status=Production.status(m,engine.rules(),engine.snapshot(),engine.active());
                int mode=switch(status) { case "running" -> 1; case "no_fuel" -> 2; default -> 0; };
                Property<Integer> property=state.getProperty("mode");
                if (property!=null && state.get(property)!=mode) CraftEngineBlocks.place(location,state.with(property,mode),false);
            } catch (Exception e) { engine.lease(snapshot.id(),false);plugin.getLogger().log(Level.WARNING,"Machine probe failed: "+snapshot.id()+" at "+p,e); }
            finally { if(periodic) probing.remove(snapshot.id()); }
        });
    }
    public void clear() { probing.clear();normalized.clear(); }
}
