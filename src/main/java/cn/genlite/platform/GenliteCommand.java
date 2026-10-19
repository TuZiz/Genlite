package cn.genlite.platform;

import cn.genlite.domain.MachineEngine;
import cn.genlite.gui.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.List;

public final class GenliteCommand implements CommandExecutor,TabCompleter {
    private final CraftItems items;private final Messages messages;private final InventoryTransfers transfers;private final MachineEngine engine;
    private final ContainerMenus warehouse;
    private final HomeHologramControl holograms;
    public GenliteCommand(CraftItems i,Messages m,InventoryTransfers t,MachineEngine e,ContainerMenus warehouse,HomeHologramControl holograms) {items=i;messages=m;transfers=t;engine=e;this.warehouse=warehouse;this.holograms=holograms;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(!(sender instanceof Player p)) {sender.sendMessage("Genlite: run this command as a player.");return true;}
        if(!p.hasPermission("genlite.use")) {messages.send(p,"permission");return true;}
        if(args.length>=1 && args[0].equalsIgnoreCase("holograms")) {holograms.toggle(p,args.length==2 && args[1].equalsIgnoreCase("menu"));return true;}
        if(args.length==1 && args[0].equalsIgnoreCase("claim")) {transfers.claim(p);return true;}
        if(args.length==1 && args[0].equalsIgnoreCase("warehouse")) {warehouse.open(p);return true;}
        if(args.length>=2 && args[0].equalsIgnoreCase("give") && p.hasPermission("genlite.admin")) {
            String requested=args[1];
            int amount=1;
            try {
                String id;
                try {id=items.resolve(requested);} catch(IllegalArgumentException unknown) {id=items.resolve("genlite:"+requested);}
                if(args.length>2) amount=Integer.parseInt(args[2]);
                if(!id.startsWith("genlite:") || amount<1 || amount>64) throw new IllegalArgumentException();
                if(id.equals("genlite:basic_smelter")) {messages.send(p,"unavailable");return true;}
                boolean machine=engine.rules().types().containsKey(id.substring(8));
                var item=items.build(p,id,machine?1:amount,machine?1:0);
                p.getInventory().addItem(item).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(),left));
                messages.send(p,"done");
            } catch(Exception e) {messages.send(p,"usage");}
            return true;
        }
        messages.send(p,"usage");return true;
    }
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args) {
        if(args.length==1) return s.hasPermission("genlite.admin")?List.of("warehouse","holograms","give"):List.of("warehouse","holograms");
        if(args.length==2 && args[0].equals("give") && s.hasPermission("genlite.admin")) return java.util.stream.Stream.concat(engine.rules().types().keySet().stream(),java.util.stream.Stream.of("binding_tool","fuel","basic_upgrade_unit")).map(x -> items.name("genlite:"+x)).filter(x -> x.startsWith(args[1])).toList();
        return List.of();
    }
}
