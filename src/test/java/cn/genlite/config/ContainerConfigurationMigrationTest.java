package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContainerConfigurationMigrationTest {
    @TempDir Path directory;
    @Test void modifiedGeneratorLayoutKeepsPositionsCombinesModesAndClosesInBottomCenter() throws Exception {
        Path path=directory.resolve("generator.yml");var y=new YamlConfiguration();y.options().parseComments(true);
        y.load(Path.of("src/main/resources/gui/generator.yml").toFile());y.set("schema-version",3);y.set("shape",List.of("########D","#F#S#O#M#","#U###L#P#","####C####"));
        y.set("elements.C.action","claim");y.set("custom-field","preserved");y.save(path.toFile());
        ContainerConfigurationMigration.upgrade(path,"gui/generator.yml",y,getClass().getResourceAsStream("/gui/generator.yml"));
        String shape=String.join("",y.getStringList("shape"));assertEquals('D',shape.charAt(8));assertEquals(1,shape.chars().filter(c -> c=='O').count());
        assertEquals('X',y.getStringList("shape").getLast().charAt(4));assertEquals("close",y.getString("elements.X.action"));
        assertFalse(y.contains("elements.C"));assertFalse(y.contains("elements.M"));assertEquals("preserved",y.getString("custom-field"));
        String once=Files.readString(path);ContainerConfigurationMigration.upgrade(path,"gui/generator.yml",y,null);assertEquals(once,Files.readString(path));
    }
    @Test void collectorAndWarehouseHaveOnlyStockAndCloseActions() throws Exception {
        for(String name:List.of("collector","warehouse")) {
            Path path=directory.resolve(name+".yml");var y=new YamlConfiguration();y.load(Path.of("src/main/resources/gui/"+name+".yml").toFile());y.set("schema-version",1);y.save(path.toFile());
            ContainerConfigurationMigration.upgrade(path,"gui/"+name+".yml",y,getClass().getResourceAsStream("/gui/"+name+".yml"));
            Set<String> actions=new HashSet<>();for(String key:y.getConfigurationSection("elements").getKeys(false)) actions.add(y.getString("elements."+key+".action"));
            assertEquals(Set.of("stock","close"),actions);assertEquals('X',y.getStringList("shape").getLast().charAt(4));
        }
    }
}
