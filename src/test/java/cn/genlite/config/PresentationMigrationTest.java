package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PresentationMigrationTest {
    @TempDir Path directory;
    @Test void machineUpgradePreservesCommentsCustomDataAndIsIdempotent() throws Exception {
        Path path=directory.resolve("collector.yml");
        var yaml=new YamlConfiguration();yaml.options().parseComments(true);
        yaml.load(Path.of("src/main/resources/genlite/普通收集器.yml").toFile());
        yaml.set("presentation-version",null);
        yaml.set("display.name","<#89F7FE>✦ <#78FFD6><bold>资源收集器</bold> <#FFD700>[Lv.<level>]");
        yaml.set("hologram.lines",List.of("<#89F7FE>✦ <machine> <#7F8C8D>[<#FFD700>Lv.<level><#7F8C8D>]","<#123456>自定义状态"));
        yaml.setComments("display",List.of("保留中文注释"));yaml.set("custom",42);
        Files.writeString(path,yaml.saveToString());String before=Files.readString(path);
        var doc=ConfigDocument.load(path,"collector.yml");PresentationMigration.machine(path,doc,"resource_collector");
        assertTrue(doc.yaml().getString("display.name").contains("资源收集器"));
        assertEquals(List.of("<machine>","<#123456>自定义状态"),doc.yaml().getStringList("hologram.lines"));
        assertEquals(42,doc.yaml().getInt("custom"));assertTrue(Files.readString(path).contains("保留中文注释"));
        assertEquals(before,Files.readString(directory.resolve("collector.yml.presentation-v0.bak")));
        String once=Files.readString(path);PresentationMigration.machine(path,doc,"resource_collector");assertEquals(once,Files.readString(path));
    }
    @Test void customNamesAndHeadingsAreRetained() throws Exception {
        Path path=directory.resolve("custom.yml");
        Files.writeString(path,"schema-version: 1\ndisplay:\n  name: '<#123456>我的收集器'\nhologram:\n  lines:\n    - '<#123456>家园 <machine> Lv.<level>'\n");
        var doc=ConfigDocument.load(path,"custom.yml");PresentationMigration.machine(path,doc,"resource_collector");
        assertEquals("<#123456>我的收集器",doc.yaml().getString("display.name"));
        assertEquals("<#123456>家园 <machine> Lv.<level>",doc.yaml().getStringList("hologram.lines").getFirst());
    }
    @Test void fuelDetailsExtendCustomButtonAndUnknownVersionNeverWrites() throws Exception {
        Path path=directory.resolve("generator.yml");
        Files.writeString(path,"schema-version: 4\nelements:\n  Z:\n    action: fuel\n    name: '<#123456>我的燃料'\n    lore:\n      - '<#123456>原有提示'\n");
        var yaml=new YamlConfiguration();yaml.load(path.toFile());
        PresentationMigration.menu(path,"gui/generator.yml",yaml,getClass().getResourceAsStream("/gui/generator.yml"));
        assertEquals("<#123456>我的燃料",yaml.getString("elements.Z.name"));
        assertTrue(yaml.getStringList("elements.Z.lore").contains("<#123456>原有提示"));
        assertEquals(1,yaml.getStringList("elements.Z.lore").size());
        String once=Files.readString(path);
        PresentationMigration.menu(path,"gui/generator.yml",yaml,getClass().getResourceAsStream("/gui/generator.yml"));
        assertEquals(once,Files.readString(path));
        yaml.set("presentation-version",99);
        assertThrows(IllegalArgumentException.class,() -> PresentationMigration.menu(path,"gui/generator.yml",yaml,getClass().getResourceAsStream("/gui/generator.yml")));
        assertEquals(once,Files.readString(path));
    }
}
