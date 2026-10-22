package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FuelInformationMigrationTest {
    @TempDir Path directory;
    private YamlConfiguration load(Path p) throws Exception {
        var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(p.toFile());return yaml;
    }
    private void apply(Path p,String name,YamlConfiguration yaml) throws Exception {
        FuelInformationMigration.apply(p,name,yaml,getClass().getResourceAsStream("/"+name));
    }
    @Test void existingFuelGuiGainsEstimateOnceAndKeepsCustomText() throws Exception {
        Path p=directory.resolve("fuel.yml");Files.copy(Path.of("src/main/resources/gui/fuel.yml"),p);
        var yaml=load(p);yaml.set("fuel-info-version",null);
        var lore=new ArrayList<>(yaml.getStringList("elements.#.lore"));
        lore.removeIf(line -> line.contains("<fuel_output>") || line.contains("仅按当前燃料估算"));
        lore.add("自定义介绍");yaml.set("elements.#.lore",lore);yaml.set("custom",true);
        Files.writeString(p,yaml.saveToString());String before=Files.readString(p);
        apply(p,"gui/fuel.yml",yaml);
        FuelChestMigration.validate(yaml);
        assertTrue(yaml.getBoolean("custom"));assertTrue(yaml.getStringList("elements.#.lore").contains("自定义介绍"));
        assertEquals(1,yaml.getStringList("elements.#.lore").stream().filter(line -> line.contains("<fuel_output>")).count());
        assertEquals(before,Files.readString(directory.resolve("fuel.yml.fuel-info-v0.bak")));
        String once=Files.readString(p);apply(p,"gui/fuel.yml",load(p));assertEquals(once,Files.readString(p));
    }
    @Test void shippedLabelRenamesButCustomLabelIsPreserved() throws Exception {
        for(String label:List.of("<#FFB75E>燃料","<#123456>定制燃料")) {
            Path p=directory.resolve(label.contains("定制")?"custom.yml":"language.yml");
            Files.writeString(p,"# 自定义注释\nschema-version: 4\nitem-labels:\n  genlite:fuel: '"+label+"'\n");
            var yaml=load(p);apply(p,"lang/zh_cn.yml",yaml);
            assertEquals(label.contains("定制")?label:"<#FFB75E>普通燃料[煤炭级]",yaml.getString("item-labels.genlite:fuel"));
            assertTrue(Files.readString(p).contains("# 自定义注释"));
            String once=Files.readString(p);apply(p,"lang/zh_cn.yml",load(p));assertEquals(once,Files.readString(p));
        }
    }
    @Test void unsupportedVersionPreservesOriginal() throws Exception {
        Path p=directory.resolve("future.yml");String before="fuel-info-version: 2\n";Files.writeString(p,before);
        assertThrows(IllegalArgumentException.class,() -> apply(p,"gui/fuel.yml",load(p)));
        assertEquals(before,Files.readString(p));
    }
}
