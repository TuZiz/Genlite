package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** IO-only, one-time migration of shipped fuel instructions, retaining custom fields and comments. */
public final class FuelMenuMigration {
    private FuelMenuMigration() {}
    public static void apply(Path path,String name,YamlConfiguration yaml,InputStream resource) throws Exception {
        try(resource) {
            if(yaml.contains("fuel-menu-version") && (!yaml.isInt("fuel-menu-version") || yaml.getInt("fuel-menu-version")!=1))
                throw new IllegalArgumentException(path+":fuel-menu-version="+yaml.get("fuel-menu-version")+" unsupported; original preserved");
            if(yaml.getInt("fuel-menu-version")==1) return;
            if(resource==null) throw new IOException("Missing bundled resource "+name);
            var defaults=new YamlConfiguration();defaults.load(new InputStreamReader(resource,StandardCharsets.UTF_8));
            if(name.equals("lang/zh_cn.yml")) {
                if(!yaml.contains("fuel-invalid")) yaml.set("fuel-invalid",defaults.getString("fuel-invalid"));
            } else if(name.equals("gui/generator.yml")) {
                var elements=yaml.getConfigurationSection("elements");
                if(elements!=null) for(String key:elements.getKeys(false)) {
                    String base="elements."+key;
                    if(!yaml.getString(base+".action","").equals("fuel")) continue;
                    var lore=new ArrayList<>(yaml.getStringList(base+".lore"));
                    boolean changed=lore.removeIf(line -> line.equals(" <#FFDF00>▶ <#FFF9C4>[左键] <#E0F7FA>从背包存入最多 64 个燃料")
                            || line.equals(" <#FFDF00>▶ <#FFF9C4>[右键] <#E0F7FA>取出完整燃料物品"));
                    if(changed) lore.add(defaults.getStringList("elements.F.lore").getFirst());
                    yaml.set(base+".lore",lore);
                }
            }
            yaml.set("fuel-menu-version",1);
            ConfigRevisionWriter.write(path,yaml,".fuel-menu-v0.bak");
        }
    }
    static void validate(YamlConfiguration yaml) {
        FuelChestMigration.validate(yaml);
    }
}
