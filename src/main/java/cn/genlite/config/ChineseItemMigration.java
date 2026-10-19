package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import java.util.*;

/** Converts only typed item fields, preserving custom data, comments and durable IDs in saves. */
final class ChineseItemMigration {
    private ChineseItemMigration() {}
    static void machine(Path file,ConfigDocument doc,String type,ItemNames names) throws Exception {
        var yaml=doc.yaml();if(current(file,yaml)) return;
        MachineDefinition.rules(doc,type,names); // Refuse bad references and duplicate resolved costs before any write.
        yaml.set("id",names.name("genlite:"+type));
        for(String field:List.of("fuel.item","production.input","production.output"))
            if(yaml.contains(field)) yaml.set(field,names.name(names.resolve(doc.string(field))));
        for(String level:Objects.requireNonNull(yaml.getConfigurationSection("upgrades")).getKeys(false)) {
            String base="upgrades."+level;var costs=yaml.getConfigurationSection(base);
            for(String item:new ArrayList<>(Objects.requireNonNull(costs).getKeys(false))) {
                String next=names.name(names.resolve(item));if(item.equals(next)) continue;
                String source=base+"."+item,target=base+"."+next;
                Object amount=yaml.get(source);var comments=yaml.getComments(source);var inline=yaml.getInlineComments(source);
                yaml.set(source,null);yaml.set(target,amount);yaml.setComments(target,comments);yaml.setInlineComments(target,inline);
            }
        }
        save(file,yaml);
    }
    static void menu(Path file,YamlConfiguration yaml,ItemNames names) throws Exception {
        if(current(file,yaml)) return;
        // Validate all references first, including customized buttons.
        var updates=new LinkedHashMap<String,String>();var elements=yaml.getConfigurationSection("elements");
        if(elements!=null) for(String key:elements.getKeys(false)) {
            String field="elements."+key+".item";
            if(yaml.contains(field)) updates.put(field,names.name(names.resolve(yaml.getString(field))));
        }
        updates.forEach(yaml::set);save(file,yaml);
    }
    private static boolean current(Path file,YamlConfiguration yaml) {
        if(!yaml.contains("item-names-version")) return false;
        if(!yaml.isInt("item-names-version") || yaml.getInt("item-names-version")!=1)
            throw new IllegalArgumentException(file+":item-names-version="+yaml.get("item-names-version")+" unsupported; original preserved");
        return true;
    }
    private static void save(Path file,YamlConfiguration yaml) throws Exception {
        yaml.set("item-names-version",1);ConfigRevisionWriter.write(file,yaml,".item-names-v0.bak");
    }
}
