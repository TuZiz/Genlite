package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FuelMenuMigrationTest {
    @TempDir Path directory;
    @Test void migratesInstructionsPreservingCustomTextCommentsAndOriginalBackup() throws Exception {
        var path=directory.resolve("generator.yml");
        String old="schema-version: 4\n# 自定义注释\ncustom: '保留'\nelements:\n  F:\n    action: fuel\n    lore:\n      - '自定义介绍'\n      - ' <#FFDF00>▶ <#FFF9C4>[左键] <#E0F7FA>从背包存入最多 64 个燃料'\n      - ' <#FFDF00>▶ <#FFF9C4>[右键] <#E0F7FA>取出完整燃料物品'\n";
        Files.writeString(path,old);var yaml=load(path);
        FuelMenuMigration.apply(path,"gui/generator.yml",yaml,getClass().getResourceAsStream("/gui/generator.yml"));
        assertEquals(old,Files.readString(directory.resolve("generator.yml.fuel-menu-v0.bak")));
        assertEquals("保留",yaml.getString("custom"));assertTrue(Files.readString(path).contains("# 自定义注释"));
        var lore=yaml.getStringList("elements.F.lore");assertEquals(2,lore.size());assertEquals("自定义介绍",lore.getFirst());assertTrue(lore.getLast().contains("打开燃料填充界面"));
        var once=Files.readString(path);FuelMenuMigration.apply(path,"gui/generator.yml",load(path),getClass().getResourceAsStream("/gui/generator.yml"));assertEquals(once,Files.readString(path));
    }
    @Test void addsLanguageKeyWithoutReplacingCustomValuesAndRejectsFutureVersion() throws Exception {
        var path=directory.resolve("lang.yml");Files.writeString(path,"schema-version: 4\nfuel-invalid: '自定义错误'\n");var yaml=load(path);
        FuelMenuMigration.apply(path,"lang/zh_cn.yml",yaml,getClass().getResourceAsStream("/lang/zh_cn.yml"));assertEquals("自定义错误",yaml.getString("fuel-invalid"));
        yaml.set("fuel-menu-version",2);assertThrows(IllegalArgumentException.class,() -> FuelMenuMigration.apply(path,"lang/zh_cn.yml",yaml,getClass().getResourceAsStream("/lang/zh_cn.yml")));
        Files.writeString(path,"schema-version: 4\n");var missing=load(path);
        FuelMenuMigration.apply(path,"lang/zh_cn.yml",missing,getClass().getResourceAsStream("/lang/zh_cn.yml"));assertTrue(missing.getString("fuel-invalid").contains("燃料"));
    }
    @Test void inputLayoutHasExactlyOneEmptyDepositSlotAndAllSymbolsMapped() throws Exception {
        var yaml=new YamlConfiguration();yaml.load(Path.of("src/main/resources/gui/fuel.yml").toFile());FuelMenuMigration.validate(yaml);
        assertEquals(3,yaml.getStringList("shape").size());
        for(String row:yaml.getStringList("shape")) {assertEquals(9,row.length());for(char c:row.toCharArray()) assertTrue(yaml.isConfigurationSection("elements."+c));}
        yaml.set("shape",List.of("FFFF#####"));assertThrows(IllegalArgumentException.class,() -> FuelMenuMigration.validate(yaml));
    }
    private YamlConfiguration load(Path path) throws Exception {var y=new YamlConfiguration();y.options().parseComments(true);y.load(path.toFile());return y;}
}
