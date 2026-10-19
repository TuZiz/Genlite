package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.util.*;

/** Immutable settings loaded on the configuration IO executor. */
public record CollectorParticleSettings(boolean enabled, int durationTicks, Map<String,Integer> colors, float size) {
    public CollectorParticleSettings {colors=Map.copyOf(colors);}
    public static CollectorParticleSettings read(YamlConfiguration yaml) {
        var doc=new ConfigDocument("collector-particles.yml",yaml);
        doc.integer("schema-version",1,1);
        Map<String,Integer> colors=new HashMap<>();
        for(String type:List.of("cobblestone_generator","timber_generator","iron_generator")) {
            String path="colors."+type,color=doc.string(path);
            if(!color.matches("#[0-9a-fA-F]{6}")) throw doc.invalid(path);
            colors.put(type,Integer.parseInt(color.substring(1),16));
        }
        return new CollectorParticleSettings(doc.bool("enabled"),doc.integer("duration-ticks",4,40),
                colors,(float)doc.decimal("size",0.1,2.0));
    }
}
