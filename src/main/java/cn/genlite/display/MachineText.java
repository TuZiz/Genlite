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
    private final java.util.function.Function<String,Component> itemNames;
    public MachineText(Rules rules,Map<String,MachineDisplay> displays,Messages messages,java.util.function.Function<String,Component> itemNames) {
        this.rules=rules;this.displays=Map.copyOf(displays);this.messages=messages;this.itemNames=itemNames;
    }
    public MachineDisplay display(String type) {return displays.get(type);}
    /** A CE-provided name used in menus/holograms; never applied to a physical item. */
    public Component name(String type,int level) {return itemNames.apply("genlite:"+type);}
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
