package cn.genlite.display;

import cn.genlite.config.CollectorParticleSettings;
import cn.genlite.domain.*;
import cn.genlite.platform.GameTasks;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.momirealms.craftengine.bukkit.api.event.CraftEngineReloadEvent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Global receipt dispatch; only the viewer's entity scheduler sends particle packets. */
public final class CollectorParticles implements Listener,AutoCloseable {
    private record Key(UUID viewer,UUID generator) {}
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final CollectorParticleSettings settings;
    private final Map<String,Particle.DustOptions> dust;
    private final Map<Key,Flight> flights=new ConcurrentHashMap<>();
    private volatile boolean closed;
    private volatile long generation;
    public CollectorParticles(JavaPlugin plugin,GameTasks tasks,MachineEngine engine,CollectorParticleSettings settings) {
        this.plugin=plugin;this.tasks=tasks;this.engine=engine;this.settings=settings;
        var palette=new HashMap<String,Particle.DustOptions>();
        settings.colors().forEach((type,rgb) -> palette.put(type,new Particle.DustOptions(Color.fromRGB(rgb),settings.size())));
        dust=Map.copyOf(palette);
    }
    /** Called on global scheduler; no world/block state is accessed here. */
    public void refresh() {
        var receipts=engine.drainCollectorTransfers();
        if(closed || !settings.enabled() || receipts.isEmpty()) return;
        long epoch=generation;
        for(Player viewer:plugin.getServer().getOnlinePlayers()) tasks.player(viewer,() -> {
            for(var receipt:receipts) start(viewer,receipt,epoch);
        },() -> {});
    }
    private synchronized void start(Player viewer,CollectorTransfer receipt,long epoch) {
        if(closed || epoch!=generation || !tasks.alive() || !valid(viewer,receipt)) return;
        Machine source=engine.snapshot().machines().get(receipt.generator());
        if(source==null || !dust.containsKey(source.type())) return;
        Key key=new Key(viewer.getUniqueId(),receipt.generator());
        Flight previous=flights.get(key);
        if(previous!=null) previous.stop();
        if(flights.size()>=512 || flights.keySet().stream().filter(k -> k.viewer().equals(key.viewer())).count()>=32) return;
        Flight flight=new Flight(key,viewer,receipt,dust.get(source.type()));flights.put(key,flight);
        ScheduledTask task=viewer.getScheduler().runAtFixedRate(plugin,t -> flight.frame(),flight::stop,1,1);
        flight.task=task;
        if(task==null || flight.cancelled || closed) flight.stop();
    }
    /** Viewer-owned reads plus immutable domain snapshots; never probes a foreign region. */
    private boolean valid(Player viewer,CollectorTransfer r) {
        if(!engine.ready() || !viewer.isOnline() || !viewer.getWorld().getUID().equals(r.from().world())
                || !r.from().world().equals(r.to().world())) return false;
        var state=engine.snapshot();var source=state.machines().get(r.generator());var target=state.machines().get(r.collector());
        if(source==null || target==null || source.removed() || target.removed() || source.paused() || target.paused()
                || source.mode()!=OutputMode.COLLECTOR || !r.collector().equals(source.collector())
                || !source.position().equals(r.from()) || !target.position().equals(r.to())
                || !engine.active().contains(source.id()) || !engine.active().contains(target.id())) return false;
        Location eye=viewer.getLocation();
        double dx=eye.getX()-(r.from().x()+0.5),dy=eye.getY()-(r.from().y()+0.75),dz=eye.getZ()-(r.from().z()+0.5);
        return dx*dx+dy*dy+dz*dz<=32*32;
    }
    private final class Flight {
        final Key key;final Player viewer;final CollectorTransfer receipt;
        final Particle.DustOptions color;
        volatile ScheduledTask task;volatile boolean cancelled;int tick;
        Flight(Key key,Player viewer,CollectorTransfer receipt,Particle.DustOptions color) {this.key=key;this.viewer=viewer;this.receipt=receipt;this.color=color;}
        void frame() {
            if(cancelled || closed || !tasks.alive() || !valid(viewer,receipt)) {stop();return;}
            double head=(double)++tick/settings.durationTicks();
            Position from=receipt.from(),to=receipt.to();
            // Moving head with a short trailing beam; final head reaches the collector's top.
            for(int n=0;n<5;n++) {
                double progress=Math.max(0,head-n*0.025);
                viewer.spawnParticle(Particle.DUST,from.x()+0.5+(to.x()-from.x())*progress,
                        from.y()+0.75+(to.y()-from.y()-0.3)*progress,
                        from.z()+0.5+(to.z()-from.z())*progress,1,0,0,0,0,color);
            }
            if(tick>=settings.durationTicks()) stop();
        }
        void stop() {cancelled=true;flights.remove(key,this);ScheduledTask current=task;if(current!=null) current.cancel();}
    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        flights.values().stream().filter(f -> f.key.viewer().equals(event.getPlayer().getUniqueId())).forEach(Flight::stop);
    }
    @EventHandler(ignoreCancelled=true) public void unload(WorldUnloadEvent event) {
        flights.values().stream().filter(f -> f.receipt.from().world().equals(event.getWorld().getUID())).forEach(Flight::stop);
    }
    @EventHandler public void reload(CraftEngineReloadEvent event) {clear();}
    private synchronized void clear() {generation++;flights.values().forEach(Flight::stop);flights.clear();engine.drainCollectorTransfers();}
    @Override public synchronized void close() {closed=true;clear();}
}
