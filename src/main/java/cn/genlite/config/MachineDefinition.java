package cn.genlite.config;

import cn.genlite.domain.Rules;
import java.util.*;

/** Reads one complete machine file, with independent progression, fuel, costs and text. */
final class MachineDefinition {
    private MachineDefinition() {}
    static Rules.Type rules(ConfigDocument d,String id) {
        try {return rules(d,id,ItemNames.bundled());}
        catch(java.io.IOException error) {throw new IllegalStateException("物品名称.yml load failed",error);}
        catch(RuntimeException error) {throw error;}
        catch(Exception error) {throw new IllegalStateException("物品名称.yml load failed",error);}
    }
    static Rules.Type rules(ConfigDocument d,String id,ItemNames names) {
        var y=d.yaml();boolean collector=id.equals("resource_collector");
        String output=collector?null:item(d,"production.output",names);
        String input=y.contains("production.input")?item(d,"production.input",names):null;
        if(!collector && !Set.of("minecraft:cobblestone","minecraft:oak_log","minecraft:raw_iron","minecraft:iron_ingot").contains(output)) throw d.invalid("production.output");
        if(input!=null && !input.equals("minecraft:raw_iron")) throw d.invalid("production.input");
        var levels=new HashMap<Integer,Rules.Level>();
        for(int n=1;n<=5;n++) {
            String p="levels."+n+".";
            int slots=collector?d.integer(p+"slots",27,27):27;
            if(slots%9!=0 || (collector && n==1 && slots!=27)) throw d.invalid(p+"slots");
            levels.put(n,new Rules.Level(collector?10:d.integer(p+"interval-seconds",5,1000000),collector?1:d.integer(p+"output-count",1,2),
                    collector?64:d.integer(p+"fuel-capacity-items",1,1000000),collector?64:d.integer(p+"buffer-capacity-items",1,1000000),
                    d.decimal(p+"value-multiplier",0.000001,1000000),slots));
        }
        double base=(double)levels.get(1).count()/levels.get(1).seconds();
        for(var e:levels.entrySet()) if((double)e.getValue().count()/e.getValue().seconds()>base*4.00001)
            throw new IllegalArgumentException(d.file()+":levels."+e.getKey()+" throughput exceeds level 1 x4 cap");
        var upgrades=new HashMap<Integer,Map<String,Integer>>();
        for(int n=2;n<=5;n++) {
            String p="upgrades."+n;var section=y.getConfigurationSection(p);
            if(section==null) throw d.invalid(p);
            var costs=new LinkedHashMap<String,Integer>();
            for(String item:section.getKeys(false)) {
                String resolved;
                try {resolved=names.resolve(item);} catch(IllegalArgumentException error) {throw d.invalid(p+"."+item);}
                if(costs.putIfAbsent(resolved,d.integer(p+"."+item,1,1000000))!=null) throw d.invalid(p+"."+item);
            }
            upgrades.put(n,Map.copyOf(costs));
        }
        Rules.Fuel fuel=null;Rules.Range range=null;
        if(collector) {
            int min=d.integer("collection.min-offset",-7,0),max=d.integer("collection.max-offset",0,7);
            if(max-min!=7) throw d.invalid("collection.max-offset");
            range=new Rules.Range(min,max,d.integer("collection.max-height-difference",0,64));
        } else {
            String item=item(d,"fuel.item",names);
            if(!item.matches("(minecraft|genlite):[a-z0-9_]+")) throw d.invalid("fuel.item");
            fuel=new Rules.Fuel(item,d.integer("fuel.units-per-item",1,1000000));
        }
        return new Rules.Type(input,output,collector?0:d.integer("production.fuel-per-item",1,1000000),
                d.integer("base-value",0,1000000),levels,upgrades,fuel,range);
    }
    private static String item(ConfigDocument doc,String field,ItemNames names) {
        try {return names.resolve(doc.string(field));} catch(IllegalArgumentException error) {throw doc.invalid(field);}
    }
    static MachineDisplay display(ConfigDocument d) {
        return new MachineDisplay(new MachineDisplay.Hologram(
                d.bool("hologram.enabled"),d.decimal("hologram.height",1,4),d.lines("hologram.lines"),
                d.yaml().contains("hologram.scale")?d.decimal("hologram.scale",0.25,1.5):0.65));
    }
}
