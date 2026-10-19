package cn.genlite.platform;

import cn.genlite.domain.*;
import cn.genlite.gui.*;
import cn.genlite.integration.IslandAccess;
import net.momirealms.craftengine.bukkit.api.CraftEngineBlocks;
import net.momirealms.craftengine.bukkit.api.event.*;
import net.momirealms.craftengine.core.entity.player.InteractionHand;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;
import java.util.logging.Level;

public final class MachineListener implements Listener {
    private final JavaPlugin plugin;
    private final GameTasks tasks;
    private final MachineEngine engine;
    private final MachineActions actions;
    private final IslandAccess dream;
    private final CraftItems items;
    private final Messages messages;
    private final MachineMenus menus;
    private final MachineDismantling dismantling;
    public MachineListener(JavaPlugin p,GameTasks t,MachineEngine e,MachineActions a,IslandAccess d,CraftItems i,Messages msg,MachineMenus m,MachineDismantling dismantling) {
        plugin=p;tasks=t;engine=e;actions=a;dream=d;items=i;messages=msg;menus=m;this.dismantling=dismantling;
    }
    private String type(net.momirealms.craftengine.core.block.ImmutableBlockState state) {
        String type=MachinePresence.type(state);return type!=null && (engine.rules().types().containsKey(type) || RetiredSmelters.retired(type))?type:null;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void allowPlace(CustomBlockPlaceEvent e) {
        if(RetiredSmelters.retired(String.valueOf(type(e.blockState())))) {e.setCancelled(true);messages.send(e.player(),"unavailable");return;}
        if(type(e.blockState())==null) return;
        Player p=e.player(); var home=dream.homeId(MachinePresence.position(e.location()));
        if(!engine.ready() || !p.hasPermission("genlite.use") || home==null || !dream.member(home,p.getUniqueId())) {
            e.setCancelled(true);messages.send(p,"world");return;
        }
        if(engine.at(MachinePresence.position(e.location()))!=null) {e.setCancelled(true);messages.send(p,"busy");return;}
        ItemStack held=e.hand()==InteractionHand.MAIN_HAND?p.getInventory().getItemInMainHand():p.getInventory().getItemInOffHand();
        var credentials=items.credentials(held);
        UUID source=credentials.source(),token=credentials.token();
        if(credentials.present()) {
            String reason=MachineShells.rejection(engine.snapshot().machines(),type(e.blockState()),source,token);
            if(reason!=null) {
                e.setCancelled(true);messages.send(p,"invalid-shell");
                plugin.getLogger().info("Machine placement refused before item consumption: reason="+reason+", source="+source+", type="+type(e.blockState())+", owner="+p.getUniqueId()+", at="+MachinePresence.position(e.location()));
            }
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void placed(CustomBlockPlaceEvent e) {
        String type=type(e.blockState());if(type==null) return;
        Location location=e.location();Position pos=MachinePresence.position(location);
        Player player=e.player();UUID owner=player.getUniqueId();
        boolean refund=player.getGameMode()!=org.bukkit.GameMode.CREATIVE;
        ItemStack held=(e.hand()==InteractionHand.MAIN_HAND?player.getInventory().getItemInMainHand():player.getInventory().getItemInOffHand()).clone();held.setAmount(1);
        var credentials=items.credentials(held);
        UUID source=credentials.source(),token=credentials.token();
        int level=items.level(held);
        // The CE place event is followed by CE functions that may still revert placement.
        tasks.region(location,() -> {
            if(!type.equals(MachinePresence.type(CraftEngineBlocks.getCustomBlockState(location.getBlock())))) return;
            var home=dream.homeId(pos);
            if(home==null) {plugin.getLogger().warning("Placed machine waiting for home API at "+pos);return;}
            engine.transact(d -> !credentials.present()?actions.place(d,Machine.create(pos,home,owner,type,level)):
                    actions.unpack(d,pos,home,owner,type,source,token)).whenComplete((m,error) -> {
                if(error==null) {
                    if(!refund && credentials.present()) tasks.player(player,() -> CreativeShellItems.consume(player,items,source,token),() -> {});
                    return;
                }
                if(InventoryTransfers.errorKey(error).equals("invalid-shell"))
                    plugin.getLogger().info("Machine shell rejected during placement transaction: reason="+MachineShells.rejection(engine.snapshot().machines(),type,source,token)+", source="+source+", type="+type+", owner="+owner+", at="+pos);
                else plugin.getLogger().log(Level.WARNING,"Machine placement registration failed at "+pos+", owner="+owner,error);
                tasks.region(location,() -> {
                    if(engine.at(pos)==null && type.equals(MachinePresence.type(CraftEngineBlocks.getCustomBlockState(location.getBlock())))) {
                        CraftEngineBlocks.remove(location.getBlock(),false);
                        tasks.player(player,() -> {
                            if(refund) player.getInventory().addItem(held).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(),left));
                            messages.send(player,InventoryTransfers.errorKey(error));
                        },() -> {if(refund) plugin.getLogger().severe("Placement refund needs recovery: player="+owner+", type="+type+", shell="+source);});
                    }
                });
            });
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void broken(CustomBlockBreakEvent e) {
        if(type(e.blockState())==null) return;
        // CE's normal loot is replaced with a durable packed machine drop, including fuel.
        e.setCancelled(true);
        Player p=e.getPlayer();Machine m=engine.at(MachinePresence.position(e.location()));
        if(!menus.allowed(p,m)) {messages.send(p,m==null?"busy":"permission");return;}
        dismantling.request(p,m,e.location());
    }
    private boolean machine(org.bukkit.block.Block block) {return type(CraftEngineBlocks.getCustomBlockState(block))!=null;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(EntityExplodeEvent e) {e.blockList().removeIf(this::machine);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void explode(BlockExplodeEvent e) {e.blockList().removeIf(this::machine);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void burn(BlockBurnEvent e) {if(machine(e.getBlock())) e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void fade(BlockFadeEvent e) {if(machine(e.getBlock())) e.setCancelled(true);}
    @EventHandler public void unload(WorldUnloadEvent e) {engine.invalidateWorld(e.getWorld().getUID());}
    @EventHandler public void reload(CraftEngineReloadEvent e) {
        for(Machine m:engine.snapshot().machines().values()) engine.lease(m.id(),false);
    }
}
