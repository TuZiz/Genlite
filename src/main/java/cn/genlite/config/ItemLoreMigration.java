package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Configuration IO executor only; add content details without replacing custom names or existing lore. */
final class ItemLoreMigration {
    private ItemLoreMigration() {}
    static void apply(Path file,ConfigDocument doc,String type) throws Exception {
        int version=doc.yaml().getInt("display.lore-version",1);
        if(version==3) return;
        if(version!=1 && version!=2) throw doc.invalid("display.lore-version");
        var bundled=new YamlConfiguration();
        try(var stream=ItemLoreMigration.class.getResourceAsStream("/genlite/"+type+".yml")) {
            if(stream==null) throw new IllegalStateException("Missing bundled lore: genlite/"+type+".yml");
            bundled.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }
        var lines=new ArrayList<>(doc.lines("display.lore"));
        var tags=List.of("<fuel>","<buffer>","<seconds>","<fuel_per_item>","<mode>","<stored_items>","<used_slots>");
        int index=Math.min(1,lines.size());
        for(String line:bundled.getStringList("display.lore")) {
            if(version==1 && tags.stream().anyMatch(line::contains) && !lines.contains(line)) lines.add(index++,line);
        }
        String hint=bundled.getStringList("display.lore").stream().filter(line -> (line.contains("Shift +") || line.contains("潜行+右键"))).findFirst().orElseThrow();
        boolean replaced=false;
        for(int i=0;i<lines.size();i++) if(lines.get(i).contains("直接挖掘") || lines.get(i).contains("直接破坏可拆走")) {
            lines.set(i,hint);replaced=true;
        }
        if(!replaced && lines.stream().noneMatch(line -> (line.contains("Shift +") || line.contains("潜行+右键")))) lines.add(hint);
        doc.yaml().set("display.lore",lines);doc.yaml().set("display.lore-version",3);
        ConfigRevisionWriter.write(file,doc.yaml(),".item-lore-v"+version+".bak");
    }
}
