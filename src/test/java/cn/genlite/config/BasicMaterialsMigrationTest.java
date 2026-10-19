package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class BasicMaterialsMigrationTest {
    @TempDir Path directory;
    private Path legacy(boolean custom) throws Exception {
        var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(Path.of("src/main/resources/genlite/iron_generator.yml").toFile());
        var old=new YamlConfiguration();old.load(Path.of("src/test/resources/legacy-machines.yml").toFile());
        yaml.set("materials-version",null);yaml.set("production.output","minecraft:raw_iron");yaml.set("upgrades",null);
        for(String level:old.getConfigurationSection("upgrades").getKeys(false)) {
            var costs=old.getConfigurationSection("upgrades."+level);
            for(String item:costs.getKeys(false)) yaml.set("upgrades."+level+"."+item,costs.get(item));
        }
        if(custom) yaml.set("upgrades.2.genlite:gear",7);
        Path path=directory.resolve("iron_generator.yml");yaml.save(path.toFile());Files.writeString(path,Files.readString(path)+"\n# 我的注释\ncustom-field: keep-me\n");return path;
    }
    @Test void knownDefaultsMigrateToVanillaWhilePreservingBackupAndComments() throws Exception {
        Path path=legacy(false);String before=Files.readString(path);var doc=ConfigDocument.load(path,"genlite/iron_generator.yml");
        BasicMaterialsMigration.apply(path,doc,"iron_generator");
        assertEquals("minecraft:iron_ingot",doc.yaml().getString("production.output"));assertFalse(doc.yaml().contains("upgrades.2.genlite:gear"));
        assertEquals(16,doc.yaml().getInt("upgrades.2.minecraft:oak_log"));assertTrue(Files.readString(path).contains("我的注释"));
        assertEquals("keep-me",doc.yaml().getString("custom-field"));assertEquals(before,Files.readString(directory.resolve("iron_generator.yml.materials-v1.bak")));
        String once=Files.readString(path);BasicMaterialsMigration.apply(path,doc,"iron_generator");assertEquals(once,Files.readString(path));
    }
    @Test void customizedUpgradeAmountsAreNotSilentlyReplaced() throws Exception {
        Path path=legacy(true);var doc=ConfigDocument.load(path,"genlite/iron_generator.yml");BasicMaterialsMigration.apply(path,doc,"iron_generator");
        assertEquals(7,doc.yaml().getInt("upgrades.2.genlite:gear"));assertEquals(2,doc.yaml().getInt("materials-version"));
    }
}
