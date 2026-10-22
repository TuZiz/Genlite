package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;

/** IO-only migration of fuel presentation, preserving custom text and unknown fields. */
public final class FuelInformationMigration {
    private FuelInformationMigration() {}
    public static void apply(Path path,String name,YamlConfiguration yaml,InputStream resource) throws Exception {
        try(resource) {
            if(!name.equals("gui/fuel.yml") && !name.equals("lang/zh_cn.yml"))
                throw new IllegalArgumentException("Unexpected fuel information resource "+name);
            String version="fuel-info-version";
            int current=yaml.getInt(version,0);
            if((yaml.contains(version) && !yaml.isInt(version)) || current<0 || current>1)
                throw new IllegalArgumentException(path+":"+version+"="+yaml.get(version)+" unsupported; original preserved");
            if(current==1) return;
            if(resource==null) throw new IOException("Missing bundled resource "+name);
            var defaults=new YamlConfiguration();defaults.load(new InputStreamReader(resource,StandardCharsets.UTF_8));
            if(name.equals("gui/fuel.yml")) {
                var elements=yaml.getConfigurationSection("elements");
                for(String symbol:elements.getKeys(false)) {
                    String base="elements."+symbol;
                    if(!yaml.getString(base+".action","").equals("info")) continue;
                    var lore=new ArrayList<>(yaml.getStringList(base+".lore"));
                    if(lore.stream().noneMatch(line -> line.contains("<fuel_output>"))) {
                        int index=0;
                        for(int i=0;i<lore.size();i++) if(lore.get(i).contains("<fuel_per_item>")) index=i+1;
                        var additions=defaults.getStringList("elements.#.lore").stream()
                                .filter(line -> line.contains("<fuel_output>") || line.contains("仅按当前燃料估算")).toList();
                        lore.addAll(index,additions);yaml.set(base+".lore",lore);
                    }
                }
                FuelChestMigration.validate(yaml);
            } else {
                String key="item-labels.genlite:fuel";
                String old=yaml.getString(key,"");
                if(old.matches("(?:<[^>]+>)*燃料")) yaml.set(key,defaults.getString(key));
            }
            yaml.set(version,1);
            ConfigRevisionWriter.write(path,yaml,".fuel-info-v0.bak");
        }
    }
}
