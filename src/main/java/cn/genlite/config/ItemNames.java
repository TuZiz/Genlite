package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Immutable boundary between administrator-facing Chinese names and durable registry IDs. IO-only loading. */
public final class ItemNames {
    private final Map<String,String> ids,names;
    private ItemNames(YamlConfiguration yaml,String file) {
        if(!yaml.isInt("schema-version") || yaml.getInt("schema-version")!=1) throw new IllegalArgumentException(file+":schema-version="+yaml.get("schema-version"));
        var section=Objects.requireNonNull(yaml.getConfigurationSection("items"),file+":items missing");
        var values=new LinkedHashMap<String,String>();var reverse=new HashMap<String,String>();
        for(String name:section.getKeys(false)) {
            String id=section.getString(name);
            if(name.isBlank() || (name.contains(":") || name.contains(".")) || id==null || !id.matches("(genlite|minecraft):[a-z0-9_]+") || reverse.putIfAbsent(id,name)!=null)
                throw new IllegalArgumentException(file+":items."+name+"="+id+" invalid or duplicate ID");
            values.put(name,id);
        }
        ids=Collections.unmodifiableMap(values);names=Map.copyOf(reverse);
    }
    public static ItemNames load(Path file) throws Exception {
        if(!Files.exists(file)) return bundled();
        var yaml=new YamlConfiguration();yaml.load(file.toFile());return new ItemNames(yaml,file.toString());
    }
    static ItemNames bundled() throws Exception {
        try(var stream=ItemNames.class.getResourceAsStream("/物品名称.yml")) {
            if(stream==null) throw new IllegalStateException("Missing bundled 物品名称.yml");
            var yaml=new YamlConfiguration();yaml.load(new InputStreamReader(stream,StandardCharsets.UTF_8));return new ItemNames(yaml,"物品名称.yml");
        }
    }
    public String resolve(String value) {
        String id=ids.get(value);
        if(id!=null) return id;
        if(value!=null && value.matches("(genlite|minecraft):[a-z0-9_]+")) return value;
        throw new IllegalArgumentException("Unknown item name: "+value+"; check 物品名称.yml:items");
    }
    public String name(String id) {return names.getOrDefault(id,id);}
    public String machine(String value) {
        String id=value.contains(":") || ids.containsKey(value)?resolve(value):"genlite:"+value;
        if(!id.startsWith("genlite:")) throw new IllegalArgumentException("Invalid machine: "+value);
        return id.substring(8);
    }
}
