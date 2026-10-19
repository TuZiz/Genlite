package cn.genlite.integration;

import cn.genlite.domain.*;
import org.bukkit.plugin.java.JavaPlugin;
import ym.dreamSkyblock.api.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** All API discovery/read calls occur from the global scheduler; callbacks only submit domain IO. */
public final class DreamBridge implements IslandAccess {
    private final JavaPlugin plugin;
    private volatile Map<UUID,HomeView> homes=Map.of();
    private final Set<UUID> sending=ConcurrentHashMap.newKeySet();
    public DreamBridge(JavaPlugin plugin) { this.plugin=plugin; }
    public HomeView home(Position position) {
        return homes.values().stream().filter(h -> h.getWorldKey().equals(position.worldKey()) && h.getStatus().equals("ACTIVE")).findFirst().orElse(null);
    }
    public UUID homeId(Position position) {HomeView h=home(position);return h==null?null:h.getHomeId();}
    public boolean member(UUID home,UUID player) {
        HomeView h=homes.get(home);
        return h!=null && h.getStatus().equals("ACTIVE") && h.getMembers().contains(player);
    }
    public UUID memberHome(UUID player) {
        return homes.values().stream().filter(h -> h.getStatus().equals("ACTIVE") && h.getMembers().contains(player))
                .map(HomeView::getHomeId).findFirst().orElse(null);
    }
    public boolean active(Machine m) { HomeView h=homes.get(m.home()); return h!=null && h.getStatus().equals("ACTIVE") && h.getWorldKey().equals(m.position().worldKey()); }
    public void refresh(MachineEngine engine) {
        DreamSkyblockApi api=plugin.getServer().getServicesManager().load(DreamSkyblockApi.class);
        if (api==null || !api.isReady()) { homes=Map.of(); return; }
        Map<UUID,HomeView> next=new HashMap<>();
        for (HomeView h:api.allHomes()) next.put(h.getHomeId(),h);
        homes=Map.copyOf(next);
        if (!engine.ready()) return;
        for (Machine m:engine.snapshot().machines().values()) {
            if (m.revision()<=m.acknowledged() || !active(m) || !sending.add(m.id())) continue;
            HomeValueKey key=new HomeValueKey("genlite",m.id().toString());
            api.setValue(m.home(),key,engine.rules().value(m),m.revision()).whenComplete((result,error) -> {
                if (error!=null) {
                    sending.remove(m.id());
                    plugin.getLogger().log(Level.WARNING,"DreamSkyblock value retry: machine="+m.id()+", home="+m.home()+", revision="+m.revision(),error);
                } else if (result.getStatus()==HomeValueStatus.STALE) {
                    sending.remove(m.id());
                    plugin.getLogger().warning("DreamSkyblock STALE; reconciliation required: machine="+m.id()+", revision="+m.revision());
                } else {
                    engine.transact(d -> {
                        Machine current=d.machines.get(m.id());
                        if (current!=null && current.revision()==m.revision()) d.machines.put(m.id(),current.ack(m.revision()));
                        return null;
                    }).whenComplete((v,e) -> sending.remove(m.id()));
                }
            });
        }
    }
    public void clear() { homes=Map.of(); sending.clear(); }
}
