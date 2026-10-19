import cn.genlite.GenlitePlugin;
import cn.genlite.domain.*;
import cn.genlite.platform.CraftItems;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.CraftEngineItems;
import net.momirealms.craftengine.core.block.property.Property;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import ym.dreamSkyblock.api.DreamSkyblockApi;
import java.util.*;

/** Isolated-server probe only. Not included in the delivered plugin JAR. */
public final class GenliteSmoke extends JavaPlugin {
    @Override public void onEnable() {
        getServer().getGlobalRegionScheduler().runDelayed(this,task -> {
            try {
                GenlitePlugin plugin=(GenlitePlugin)getServer().getPluginManager().getPlugin("Genlite");
                check(plugin!=null && plugin.isEnabled(),"Genlite enabled");
                var field=GenlitePlugin.class.getDeclaredField("engine");field.setAccessible(true);
                MachineEngine engine=(MachineEngine)field.get(plugin);check(engine!=null && engine.ready(),"engine ready");
                DreamSkyblockApi api=getServer().getServicesManager().load(DreamSkyblockApi.class);
                check(api!=null && api.isReady(),"DreamSkyblock API ready");
                var itemField=GenlitePlugin.class.getDeclaredField("items");itemField.setAccessible(true);
                CraftItems items=(CraftItems)itemField.get(plugin);
                var hologramField=GenlitePlugin.class.getDeclaredField("holograms");hologramField.setAccessible(true);
                cn.genlite.display.MachineHolograms holograms=(cn.genlite.display.MachineHolograms)hologramField.get(plugin);
                for(String name:engine.rules().types().keySet()) {
                    check(CraftEngineBlocks.byId(Key.of("genlite:"+name))!=null,"block "+name);
                    check(items.id(items.build(null,"genlite:"+name,1,1)).equals("genlite:"+name),"CE item identity "+name);
                }
                check(items.id(items.build(null,"genlite:binding_tool",1,0)).equals("genlite:binding_tool"),"binding tool identity");
                Machine packed=Machine.create(new Position(UUID.randomUUID(),"minecraft:test",1,64,1),UUID.randomUUID(),UUID.randomUUID(),"cobblestone_generator",3).contents(71,0,9,Map.of()).retirement(true);
                var shell=items.parcel(null,Parcel.shell(packed));
                check(items.source(shell).equals(packed.id()) && items.token(shell).equals(packed.shellToken()) && items.level(shell)==3,"packed shell PDC");
                check(shell.getItemMeta().hasLore(),"configured machine lore");
                check(CraftEngineBlocks.byId(Key.of("genlite:warehouse_core"))==null,"warehouse removed");
                check(CraftEngineBlocks.byId(Key.of("genlite:greenhouse_controller"))==null,"greenhouse removed");
                var world=getServer().getWorlds().getFirst();
                Location location=new Location(world,0,100,0);
                getServer().getRegionScheduler().execute(this,location,() -> {
                    try {
                        var previous=location.getBlock().getState();
                        for(String name:engine.rules().types().keySet()) {
                            var block=CraftEngineBlocks.byId(Key.of("genlite:"+name));
                            var state=block.defaultState();Property<Integer> mode=state.getProperty("mode");
                            for(int n=0;n<3;n++) {
                                check(CraftEngineBlocks.place(location,state.with(mode,n),false),"CE region place "+name);
                                var placed=CraftEngineBlocks.getCustomBlockState(location.getBlock());
                                check(placed!=null && placed.get(mode)==n,"CE mode "+name+"="+n);
                            }
                            check(CraftEngineBlocks.remove(location.getBlock(),false),"CE region remove "+name);
                        }
                        previous.update(true,false);
                        Machine shown=Machine.create(new Position(world.getUID(),world.getKey().toString(),0,100,0),UUID.randomUUID(),UUID.randomUUID(),"cobblestone_generator",3).contents(71,0,9,Map.of());
                        holograms.update(shown,location,true);
                        var displays=world.getNearbyEntities(location,2,4,2).stream().filter(e -> e instanceof org.bukkit.entity.TextDisplay).toList();
                        check(displays.size()==1,"one hologram per machine");
                        var display=(org.bukkit.entity.TextDisplay)displays.getFirst();
                        check(!display.isPersistent() && display.text().toString().contains("71"),"nonpersistent hologram fuel text");
                        holograms.update(shown.contents(55,0,12,Map.of()),location,true);
                        check(display.text().toString().contains("55"),"hologram refresh");
                        check(world.getNearbyEntities(location,2,4,2).stream().filter(e -> e instanceof org.bukkit.entity.TextDisplay).count()==1,"no duplicate hologram");
                        holograms.update(shown.retirement(true),location,false);check(!display.isValid(),"hologram removed on dismantle");
                        getLogger().info("GENLITE_SMOKE_PASSED: API ready, 5 CE items/blocks x3 states, binding tool, packed shell PDC, configured lore, TextDisplay spawn/update/no-duplicate/removal");
                    } catch(Throwable error) {getLogger().log(java.util.logging.Level.SEVERE,"GENLITE_SMOKE_FAILED",error);}
                    finally {getServer().getGlobalRegionScheduler().execute(this,() -> getServer().shutdown());}
                });
            } catch(Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE,"GENLITE_SMOKE_FAILED",error);
                getServer().shutdown();
            }
        },100);
    }
    private void check(boolean result,String name) {if(!result) throw new IllegalStateException(name);}
}
