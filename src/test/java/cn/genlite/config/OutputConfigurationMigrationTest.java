package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class OutputConfigurationMigrationTest {
    @TempDir Path directory;
    private YamlConfiguration load(Path path) throws Exception {
        var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(path.toFile());return yaml;
    }
    @Test void oldLanguageAddsModesAndKeepsCustomFieldsCommentsAndOriginalBackup() throws Exception {
        Path path=directory.resolve("zh_cn.yml");
        String old="# 自定义中文注释\nschema-version: 1\nmode:\n  collector: '<#68B9B1>连接收集器'\ncustom-field: keep-me\n";
        Files.writeString(path,old);var yaml=load(path);
        OutputConfigurationMigration.upgrade(path,"lang/zh_cn.yml",yaml,getClass().getClassLoader().getResourceAsStream("lang/zh_cn.yml"));
        assertEquals(3,yaml.getInt("schema-version"));assertTrue(yaml.contains("mode.drop"));assertTrue(yaml.contains("mode.warehouse"));
        assertTrue(Files.readString(path).contains("自定义中文注释"));assertEquals("keep-me",yaml.getString("custom-field"));
        assertEquals(old,Files.readString(directory.resolve("zh_cn.yml.v1.bak")));
        String once=Files.readString(path);OutputConfigurationMigration.upgrade(path,"lang/zh_cn.yml",yaml,null);assertEquals(once,Files.readString(path));
    }
    @Test void oldMenuEndsWithOneOutputAndCloseAfterBothMigrationStages() throws Exception {
        Path path=directory.resolve("generator.yml");
        Files.copy(Path.of("src/main/resources/gui/generator.yml"),path);var yaml=load(path);
        yaml.set("schema-version",1);yaml.set("shape",List.of("#########","#F#S#O###","#B#M#L#P#","#U#####D#","####C####"));yaml.save(path.toFile());
        OutputConfigurationMigration.upgrade(path,"gui/generator.yml",yaml,getClass().getClassLoader().getResourceAsStream("gui/generator.yml"));
        ContainerConfigurationMigration.upgrade(path,"gui/generator.yml",yaml,getClass().getClassLoader().getResourceAsStream("gui/generator.yml"));
        assertEquals(4,yaml.getInt("schema-version"));assertEquals("mode",yaml.getString("elements.O.action"));
        assertEquals("close",yaml.getString("elements.X.action"));assertFalse(yaml.contains("elements.M"));
    }
}
