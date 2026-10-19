package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChineseConfigurationTest {
    @TempDir Path root;
    @Test void oldFilesAndReferencesMigrateWithCommentsUnknownFieldsAndBackups() throws Exception {
        Path directory=root.resolve("genlite");Files.createDirectories(directory);
        Map<Path,String> originals=new HashMap<>();
        for(var e:MachineConfigurations.FILES.entrySet()) {
            var yaml=new YamlConfiguration();yaml.options().parseComments(true);
            yaml.load(Path.of("src/main/resources/genlite").resolve(e.getValue()).toFile());
            yaml.set("item-names-version",null);yaml.set("id",e.getKey());
            if(!e.getKey().equals("resource_collector")) {
                yaml.set("fuel.item","genlite:fuel");yaml.set("production.output",ItemNames.bundled().resolve(yaml.getString("production.output")));
            }
            yaml.set("upgrades.2",null);yaml.set("upgrades.2.genlite:mechanical_parts",2);yaml.set("upgrades.2.genlite:copper_coil",2);
            yaml.setComments("upgrades.2.genlite:mechanical_parts",List.of("自定义数量及注释"));yaml.set("unknown-data","保留");
            Path file=directory.resolve(e.getKey()+".yml");Files.writeString(file,yaml.saveToString());originals.put(file,Files.readString(file));
        }
        var loaded=MachineConfigurations.read(directory);
        for(var e:MachineConfigurations.FILES.entrySet()) {
            Path file=directory.resolve(e.getValue());String text=Files.readString(file);
            assertFalse(Files.exists(directory.resolve(e.getKey()+".yml")));
            assertEquals(originals.get(directory.resolve(e.getKey()+".yml")),Files.readString(directory.resolve(e.getKey()+".yml.chinese-name-v0.bak")));
            assertTrue(text.contains("自定义数量及注释"));assertTrue(text.contains("unknown-data"));
            assertTrue(text.contains("铁片: 2"));assertTrue(text.contains("铜丝: 2"));
            assertEquals(Map.of("genlite:mechanical_parts",2,"genlite:copper_coil",2),loaded.rules().types().get(e.getKey()).upgrades().get(2));
            assertEquals(1,YamlConfiguration.loadConfiguration(file.toFile()).getInt("item-names-version"));
        }
        Map<Path,String> once=new HashMap<>();try(var paths=Files.list(directory)) {for(Path p:paths.toList()) once.put(p,Files.readString(p));}
        MachineConfigurations.read(directory);for(var e:once.entrySet()) assertEquals(e.getValue(),Files.readString(e.getKey()));
    }
    @Test void duplicateFilenamesFailBeforeRenamingAnyFile() throws Exception {
        for(var e:MachineConfigurations.FILES.entrySet()) Files.writeString(root.resolve(e.getKey()+".yml"),"keep");
        Files.writeString(root.resolve("普通收集器.yml"),"custom");
        assertThrows(IllegalArgumentException.class,() -> ChineseMachineFiles.migrate(root));
        for(String id:MachineConfigurations.FILES.keySet()) assertEquals("keep",Files.readString(root.resolve(id+".yml")));
        assertEquals("custom",Files.readString(root.resolve("普通收集器.yml")));
    }
    @Test void unknownAndDuplicateAliasesNeverRewriteMachineConfiguration() throws Exception {
        Path file=root.resolve("原石生成器.yml");Files.copy(Path.of("src/main/resources/genlite/原石生成器.yml"),file);
        var doc=ConfigDocument.load(file,file.toString());doc.yaml().set("item-names-version",null);
        doc.yaml().set("upgrades.2.genlite:basic_upgrade_unit",2);
        Files.writeString(file,doc.yaml().saveToString());String before=Files.readString(file);
        assertThrows(IllegalArgumentException.class,() -> ChineseItemMigration.machine(file,doc,"cobblestone_generator",ItemNames.bundled()));
        assertEquals(before,Files.readString(file));
        doc.yaml().set("upgrades.2.genlite:basic_upgrade_unit",null);doc.yaml().set("fuel.item","不存在的燃料");
        assertThrows(IllegalArgumentException.class,() -> ChineseItemMigration.machine(file,doc,"cobblestone_generator",ItemNames.bundled()));
        assertEquals(before,Files.readString(file));
    }
    @Test void customChineseAliasesAndEnglishIdsResolveToIdenticalDurableItems() throws Exception {
        Path file=root.resolve("物品名称.yml");Files.copy(Path.of("src/main/resources/物品名称.yml"),file);
        Files.writeString(file,Files.readString(file)+"  自定义材料: other:invalid\n");
        assertThrows(IllegalArgumentException.class,() -> ItemNames.load(file));
        Files.writeString(file,Files.readString(file).replace("other:invalid","genlite:custom_material"));
        var names=ItemNames.load(file);
        assertEquals("genlite:custom_material",names.resolve("自定义材料"));
        assertEquals(names.resolve("铁片"),names.resolve("genlite:mechanical_parts"));
        assertEquals("cobblestone_generator",names.machine("原石生成器"));
        assertEquals("cobblestone_generator",names.machine("cobblestone_generator"));
        assertThrows(IllegalArgumentException.class,() -> names.resolve("错误名称"));
    }
    @Test void guiItemNamesMigrateWithoutChangingLayoutOrCustomName() throws Exception {
        Path file=root.resolve("generator.yml");
        var yaml=new YamlConfiguration();yaml.load(Path.of("src/main/resources/gui/generator.yml").toFile());
        yaml.set("item-names-version",null);yaml.set("elements.U.item","genlite:basic_upgrade_unit");
        yaml.set("elements.U.name","<#123456>自定义按钮");Files.writeString(file,yaml.saveToString());
        ChineseItemMigration.menu(file,yaml,ItemNames.bundled());
        assertEquals("初级升级单元",yaml.getString("elements.U.item"));assertEquals("<#123456>自定义按钮",yaml.getString("elements.U.name"));
        assertEquals(List.of("####S####","##F###O##","###U#P###","####X####"),yaml.getStringList("shape"));
    }
}
