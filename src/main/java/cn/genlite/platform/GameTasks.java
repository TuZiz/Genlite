package cn.genlite.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.atomic.AtomicBoolean;

/** Paper implements these ownership APIs too; no generic async Bukkit calls. */
public final class GameTasks implements AutoCloseable {
    private final JavaPlugin plugin;
    private final AtomicBoolean alive=new AtomicBoolean(true);
    public GameTasks(JavaPlugin plugin) { this.plugin=plugin; }
    public boolean alive() { return alive.get() && plugin.isEnabled(); }
    public void global(Runnable action) {
        if (alive()) plugin.getServer().getGlobalRegionScheduler().execute(plugin,() -> { if (alive()) action.run(); });
    }
    public ScheduledTask repeat(Runnable action) {
        return plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin,t -> { if (alive()) action.run(); },20,20);
    }
    public void region(Location location,Runnable action) {
        if (alive()) plugin.getServer().getRegionScheduler().execute(plugin,location,() -> { if (alive()) action.run(); });
    }
    public boolean player(Player player,Runnable action,Runnable retired) {
        if (!alive()) { retired.run(); return false; }
        AtomicBoolean completed=new AtomicBoolean();
        Runnable retirement=() -> {if(completed.compareAndSet(false,true)) retired.run();};
        boolean scheduled=player.getScheduler().execute(plugin,() -> {
            if(alive() && player.isOnline()) {if(completed.compareAndSet(false,true)) action.run();}
            else retirement.run();
        },retirement,1);
        if(!scheduled) retirement.run();
        return scheduled;
    }
    @Override public void close() {
        alive.set(false);
        plugin.getServer().getGlobalRegionScheduler().cancelTasks(plugin);
        plugin.getServer().getAsyncScheduler().cancelTasks(plugin);
        // Region/player tasks check alive; they never apply old-instance callbacks after disable.
    }
}
