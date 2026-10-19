package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CollectorParticleSettingsTest {
    YamlConfiguration defaults() throws Exception {
        var yaml=new YamlConfiguration();yaml.load(new File("src/main/resources/collector-particles.yml"));return yaml;
    }
    @Test void bundledConfigDefinesDistinctColorsForEachGenerator() throws Exception {
        var settings=CollectorParticleSettings.read(defaults());
        assertTrue(settings.enabled());assertEquals(12,settings.durationTicks());assertEquals(0.7f,settings.size());
        assertEquals(Map.of("cobblestone_generator",0x7DD3FC,"timber_generator",0x6EE7A0,"iron_generator",0xFBBF6A),settings.colors());
        assertEquals(3,new java.util.HashSet<>(settings.colors().values()).size());
    }
    @Test void invalidValuesIdentifyFileAndField() throws Exception {
        for(var entry:Map.<String,Object>of("schema-version",2,"colors.cobblestone_generator","cyan","duration-ticks",3,"size",2.1,"enabled","yes").entrySet()) {
            var yaml=defaults();yaml.set(entry.getKey(),entry.getValue());
            var error=assertThrows(IllegalArgumentException.class,() -> CollectorParticleSettings.read(yaml));
            assertTrue(error.getMessage().contains("collector-particles.yml:"+entry.getKey()+"="));
        }
    }
    @Test void everyGeneratorRequiresItsOwnValidColor() throws Exception {
        for(String type:java.util.List.of("cobblestone_generator","timber_generator","iron_generator")) {
            for(Object invalid:new Object[]{null,"#12345Z",123456}) {
                var yaml=defaults();yaml.set("colors."+type,invalid);
                var error=assertThrows(IllegalArgumentException.class,() -> CollectorParticleSettings.read(yaml));
                assertTrue(error.getMessage().contains("collector-particles.yml:colors."+type+"="));
            }
        }
    }
}
