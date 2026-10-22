package cn.genlite.display;

import cn.genlite.config.MachineDisplay;
import cn.genlite.domain.*;
import cn.genlite.gui.Messages;
import net.kyori.adventure.text.Component;
import java.util.*;

/** Shared rendering for machine items, menu placeholders and multiline holograms; no Bukkit/world reads. */
public final class MachineText {
    private final Rules rules;
    private final Map<String,MachineDisplay> displays;
    private final Messages messages;
    public MachineText(Rules rules,Map<String,MachineDisplay> displays,Messages messages) {
        this.rules=rules;this.displays=Map.copyOf(displays);this.messages=messages;
    }
    public MachineDisplay display(String type) { return displays.get(type); }
    public Component name(String type,int level) {
        return messages.render(display(type).name(),Map.of("level",Component.text(level)));
    }
    public List<Component> lore(String type,int level) {
        return lore(type,level,null,null,WorldState.empty());
    }
    /** Packed contents come from the durable snapshot, never from a stale item tooltip or item counters. */
    public List<Component> lore(String type,int level,UUID source,UUID token,WorldState state) {
        Machine packed=source==null?null:state.machines().get(source);
        if(packed==null || !packed.removed() || !packed.cleared() || token==null
                || !token.equals(packed.shellToken()) || !type.equals(packed.type())) packed=null;
        int actualLevel=packed==null?level:packed.level();
        Rules.Type definition=rules.types().get(type);Rules.Level limits=definition.levels().get(actualLevel);
        int fuel=packed==null?0:packed.fuel(),buffer=packed==null?0:packed.buffer();
        var inventory=packed==null?Map.<String,Integer>of():packed.inventory();
        Map<String,Component> values=new HashMap<>();
        values.put("machine",name(type,actualLevel));
        Map.ofEntries(Map.entry("level",actualLevel),Map.entry("fuel",fuel),
                Map.entry("fuel_capacity",limits.fuelItems()*(definition.fuel()==null?0:definition.fuel().units())),
                Map.entry("buffer",buffer),Map.entry("buffer_capacity",limits.bufferItems()),
                Map.entry("seconds",limits.seconds()),Map.entry("count",limits.count()),
                Map.entry("fuel_per_item",definition.fuelPerItem()),Map.entry("used_slots",rules.usedSlots(inventory)),
                Map.entry("slots",limits.collectorSlots()),
                Map.entry("stored_items",inventory.values().stream().mapToLong(Integer::longValue).sum()))
                .forEach((key,value) -> values.put(key,Component.text(value.toString())));
        // Unpacking intentionally starts in DROP mode, irrespective of the previous binding/output mode.
        values.put("mode",messages.key("mode.drop"));
        return display(type).lore().stream().map(line -> messages.render(line,values)).toList();
    }
    public Map<String,Component> variables(Machine m,Map<UUID,Machine> machines,Set<UUID> active) {
        Rules.Level l=rules.level(m);Map<String,Component> v=new HashMap<>();
        v.put("machine",name(m.type(),m.level()));
        Map.ofEntries(Map.entry("level",m.level()),Map.entry("fuel",m.fuel()),Map.entry("fuel_capacity",l.fuelItems()*rules.fuelUnits(m)),
                Map.entry("buffer",m.buffer()),Map.entry("buffer_capacity",l.bufferItems()),Map.entry("input",m.input()),
                Map.entry("seconds",l.seconds()),Map.entry("count",l.count()),Map.entry("used_slots",rules.usedSlots(m.inventory())),
                Map.entry("slots",l.collectorSlots()),Map.entry("value",rules.value(m))).forEach((k,n) -> v.put(k,Component.text(n.toString())));
        if (!m.collectorType()) {
            v.put("fuel_item",messages.label(rules.fuelItem(m)));
            v.put("output_item",messages.label(rules.types().get(m.type()).output()));
            v.put("fuel_units",Component.text(rules.fuelUnits(m)));
            v.put("fuel_per_item",Component.text(rules.types().get(m.type()).fuelPerItem()));
            long batchEnergy=(long)rules.types().get(m.type()).fuelPerItem()*l.count();
            v.put("fuel_output",Component.text(m.fuel()/batchEnergy*l.count()));
        }
        v.put("status",messages.key("status."+Production.status(m,rules,machines,active)));
        v.put("mode",messages.key("mode."+m.mode().key()));
        v.put("locked",messages.key(m.locked()?"yes":"no"));v.put("paused",messages.key(m.paused()?"yes":"no"));
        v.put("binding",m.collector()==null?messages.key("none"):Component.text(m.collector().toString().substring(0,8)));
        return v;
    }
    public Map<String,Component> variables(Machine m,WorldState state,Set<UUID> active) {
        var variables=variables(m,state.machines(),active);
        variables.put("status",messages.key("status."+Production.status(m,rules,state,active)));
        return variables;
    }
    public Component hologram(Machine m,Map<UUID,Machine> machines,Set<UUID> active) {
        Map<String,Component> variables=variables(m,machines,active);
        return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.newline()),
                display(m.type()).hologram().lines().stream().map(line -> messages.render(line,variables)).toList())
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false);
    }
    public Component hologram(Machine m,WorldState state,Set<UUID> active) {
        var values=variables(m,state,active);
        return Component.join(net.kyori.adventure.text.JoinConfiguration.separator(Component.newline()),
                display(m.type()).hologram().lines().stream().map(line -> messages.render(line,values)).toList())
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false);
    }
}
