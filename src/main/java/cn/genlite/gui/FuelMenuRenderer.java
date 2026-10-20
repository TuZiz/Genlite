package cn.genlite.gui;

import cn.genlite.display.MachineText;
import cn.genlite.domain.*;
import cn.genlite.platform.CraftItems;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import java.util.*;

/** Rendering runs on the viewing player's scheduler, through the shared non-italic renderer. */
final class FuelMenuRenderer {
    private final YamlConfiguration config;
    private final MachineEngine engine;
    private final Messages messages;
    private final MachineText text;
    private final CraftItems items;
    FuelMenuRenderer(YamlConfiguration config,MachineEngine engine,Messages messages,MachineText text,CraftItems items) {
        this.config=config;this.engine=engine;this.messages=messages;this.text=text;this.items=items;
    }
    Map<String,Component> variables(Machine m,Rules rules) {
        var vars=text.variables(m,engine==null?Map.of(m.id(),m):engine.snapshot().machines(),engine==null?Set.of():engine.active());
        vars.put("fuel_space",Component.text(rules.fuelSpace(m)));
        vars.put("fuel_items",Component.text(m.fuel()/rules.fuelUnits(m)));
        return vars;
    }
    void draw(FuelSession s,Machine m) {
        var vars=variables(m,engine.rules());
        String shape=String.join("",config.getStringList("shape"));
        if(s.inventory==null) s.inventory=Bukkit.createInventory(s,shape.length(),messages.render(config.getString("title"),vars));
        s.actions.clear();s.counts.clear();s.fuelSlots.clear();
        var unit=items.build(s.viewer,engine.rules().fuelItem(m),1,0);
        int slotCount=(int)shape.chars().filter(c -> "fuel-slot".equals(config.getString("elements."+(char)c+".action"))).count();
        var counts=FuelStacks.visible(m.fuel(),engine.rules().fuelUnits(m),unit.getMaxStackSize(),slotCount);
        int fuelSlot=0;
        for(int slot=0;slot<shape.length();slot++) {
            char symbol=shape.charAt(slot);
            if(symbol==' ') {s.inventory.setItem(slot,null);continue;}
            String path="elements."+symbol+".";
            String action=config.getString(path+"action","none");s.actions.put(slot,action);
            if(action.equals("fuel-slot")) {
                int count=counts.get(fuelSlot);s.fuelSlots.put(slot,fuelSlot++);s.counts.put(slot,count);
                if(count==0) s.inventory.setItem(slot,null);
                else {var fuel=unit.clone();fuel.setAmount(count);s.inventory.setItem(slot,fuel);}
                continue;
            }
            ItemStack stack=new ItemStack(Material.valueOf(config.getString(path+"material")));
            var meta=stack.getItemMeta();meta.itemName(messages.render(config.getString(path+"name",""),vars));
            meta.lore(config.getStringList(path+"lore").stream().map(line -> messages.render(line,vars)).toList());
            stack.setItemMeta(meta);s.inventory.setItem(slot,stack);
        }
    }
}
