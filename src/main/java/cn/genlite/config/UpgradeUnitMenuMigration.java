package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Add the new upgrade item label/tooltip while preserving GUI title, Shape and custom icons. */
final class UpgradeUnitMenuMigration {
    private UpgradeUnitMenuMigration() {}
    static void apply(Path file,String name,YamlConfiguration yaml,InputStream bundled) throws Exception {
        try(bundled) {
            int version=yaml.getInt("upgrade-item-version",0);
            if(version==1) return;
            if(version!=0) throw new IllegalArgumentException(name+":upgrade-item-version="+version+" invalid");
            if(bundled==null) throw new IllegalStateException("Missing bundled upgrade text: "+name);
            var defaults=new YamlConfiguration();defaults.load(new InputStreamReader(bundled,StandardCharsets.UTF_8));
            if(name.equals("lang/zh_cn.yml")) {
                for(String key:java.util.List.of("upgrade-cost-line","item-labels.genlite:basic_upgrade_unit")) {
                    if(!yaml.contains(key)) yaml.set(key,defaults.get(key));
                }
            } else if(name.equals("gui/generator.yml")) {
                var elements=yaml.getConfigurationSection("elements");
                if(elements==null) throw new IllegalArgumentException(name+":elements missing");
                for(String symbol:elements.getKeys(false)) {
                    String key="elements."+symbol+".";
                    if("upgrade".equals(yaml.getString(key+"action")) && !yaml.contains(key+"item")
                            && "EXPERIENCE_BOTTLE".equals(yaml.getString(key+"material"))) {
                        yaml.set(key+"material",null);yaml.set(key+"item","genlite:basic_upgrade_unit");
                    }
                }
            }
            yaml.set("upgrade-item-version",1);
            ConfigRevisionWriter.write(file,yaml,".upgrade-items-v0.bak");
        }
    }
}
