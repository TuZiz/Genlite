package cn.genlite.gui;

import cn.genlite.domain.*;
import cn.genlite.platform.*;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import java.math.BigInteger;
import java.util.*;

/** Player owner thread only. Collector contents are normal stacks; warehouse entries show aggregate totals. */
final class ContainerMenuRenderer {
    private final MachineEngine engine;
    private final CraftItems items;
    private final Messages messages;
    private final cn.genlite.display.MachineText text;
    ContainerMenuRenderer(MachineEngine engine,CraftItems items,Messages messages,cn.genlite.display.MachineText text) {this.engine=engine;this.items=items;this.messages=messages;this.text=text;}
    // Shared by the inventory title and controls; null machine denotes the island warehouse.
    Map<String,Component> variables(Machine machine,int capacity,int used) {
        Map<String,Component> values=new HashMap<>(Map.of("slots",Component.text(capacity),"used_slots",Component.text(used)));
        if(machine!=null) {
            values.put("machine",text.name(machine.type(),machine.level()));
            values.put("level",Component.text(machine.level()));
        }
        return values;
    }
    void draw(Player p,ContainerSession s,YamlConfiguration config) {
        boolean collector=s.kind==ContainerStock.Kind.COLLECTOR;
        Machine machine=collector?engine.snapshot().machines().get(s.home):null;
        int capacity=collector?27:engine.rules().warehouseSlots();
        int used=collector?engine.rules().usedSlots(machine.inventory()):engine.snapshot().warehouses().getOrDefault(s.home,Map.of()).size();
        Map<String,Component> vars=variables(machine,capacity,used);
        String shape=String.join("",config.getStringList("shape"));
        if(s.inventory==null) s.inventory=Bukkit.createInventory(s,shape.length(),messages.render(config.getString("title"),vars));
        s.inventory.clear();s.actions.clear();s.stock.clear();s.counts.clear();
        var chest=collector?CollectorStacks.visible(machine.inventory()).iterator():List.<CollectorStacks.Stack>of().iterator();
        var warehouse=collector?Collections.<Map.Entry<String,BigInteger>>emptyIterator():new TreeMap<>(engine.snapshot().warehouses().getOrDefault(s.home,Map.of())).entrySet().iterator();
        for(int slot=0;slot<shape.length();slot++) {
            char symbol=shape.charAt(slot);if(symbol==' ') continue;
            String path="elements."+symbol+".",action=config.getString(path+"action","none");s.actions.put(slot,action);
            if(action.equals("stock")) {
                String item;int amount;BigInteger count;
                if(collector) {if(!chest.hasNext()) continue;var entry=chest.next();item=entry.item();amount=entry.amount();count=BigInteger.valueOf(amount);}
                else {if(!warehouse.hasNext()) continue;var entry=warehouse.next();item=entry.getKey();amount=1;count=entry.getValue();}
                var stack=items.build(p,item,amount,0);
                if(!collector) {
                    var values=new HashMap<>(vars);values.put("count",Component.text(count.toString()));
                    stack.editMeta(meta -> meta.lore(config.getStringList(path+"lore").stream().map(line -> messages.render(line,values)).toList()));
                }
                s.inventory.setItem(slot,stack);s.stock.put(slot,item);s.counts.put(slot,amount);
            } else {
                var stack=config.contains(path+"item")?items.build(p,config.getString(path+"item"),1,0):new org.bukkit.inventory.ItemStack(Material.valueOf(config.getString(path+"material")));
                stack.editMeta(meta -> meta.itemName(messages.render(config.getString(path+"name"),vars)));s.inventory.setItem(slot,stack);
            }
        }
    }
}
