package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;

/** IO-only additive migration of generator status Lore. */
public final class StatusFuelMigration {
    private StatusFuelMigration() {}
    public static void apply(Path path,YamlConfiguration yaml,InputStream resource) throws Exception {
        try(resource) {
            String version="status-fuel-version";
            int current=yaml.getInt(version,0);
            if((yaml.contains(version) && !yaml.isInt(version)) || current<0 || current>1)
                throw new IllegalArgumentException(path+":"+version+"="+yaml.get(version)+" unsupported; original preserved");
            if(current==1) return;
            if(resource==null) throw new IOException("Missing bundled gui/generator.yml");
            var defaults=new YamlConfiguration();defaults.load(new InputStreamReader(resource,StandardCharsets.UTF_8));
            var additions=defaults.getStringList("elements.S.lore").stream()
                    .filter(line -> line.contains("<fuel>") || line.contains("<fuel_output>") || line.contains("仅按当前燃料估算")).toList();
            var elements=yaml.getConfigurationSection("elements");
            if(elements==null) throw new IllegalArgumentException(path+":elements missing");
            for(String symbol:elements.getKeys(false)) {
                String base="elements."+symbol;
                if(!yaml.getString(base+".action","").equals("refresh")) continue;
                var lore=new ArrayList<>(yaml.getStringList(base+".lore"));
                int index=0;
                for(int i=0;i<lore.size();i++) if(lore.get(i).contains("<value>")) index=i+1;
                for(String line:additions) {
                    String marker=line.contains("<fuel_output>")?"<fuel_output>":line.contains("<fuel>")?"<fuel>":"仅按当前燃料估算";
                    if(lore.stream().noneMatch(existing -> existing.contains(marker))) lore.add(index++,line);
                }
                yaml.set(base+".lore",lore);
            }
            yaml.set(version,1);
            ConfigRevisionWriter.write(path,yaml,".status-fuel-v0.bak");
        }
    }
}
