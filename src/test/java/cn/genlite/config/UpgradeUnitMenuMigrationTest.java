package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeUnitMenuMigrationTest {
    @TempDir Path root;
    @Test void languageAddsUpgradeLabelAndOwnedPlaceholderWithoutOverwritingCustomText() throws Exception {
        var y=new YamlConfiguration();y.options().parseComments(true);y.set("schema-version",4);y.set("done","custom");
        y.set("upgrade-cost-line","<italic:false><#AABBCC>自定义 <owned>");Path p=root.resolve("lang.yml");y.save(p.toFile());
        UpgradeUnitMenuMigration.apply(p,"lang/zh_cn.yml",y,getClass().getResourceAsStream("/lang/zh_cn.yml"));
        assertEquals("custom",y.getString("done"));assertEquals("<italic:false><#AABBCC>自定义 <owned>",y.getString("upgrade-cost-line"));
        assertTrue(y.getString("item-labels.genlite:basic_upgrade_unit").contains("初级升级单元"));
        String once=Files.readString(p);UpgradeUnitMenuMigration.apply(p,"lang/zh_cn.yml",y,null);assertEquals(once,Files.readString(p));
    }
    @Test void menuKeepsTitleShapeAndCustomIconButMigratesDefaultIcon() throws Exception {
        var y=new YamlConfiguration();y.set("schema-version",4);y.set("title","管理员标题");y.set("shape",List.of("    A   B"));
        y.set("elements.A.action","upgrade");y.set("elements.A.material","EXPERIENCE_BOTTLE");
        y.set("elements.B.action","upgrade");y.set("elements.B.item","custom:special");Path p=root.resolve("gui.yml");y.save(p.toFile());
        UpgradeUnitMenuMigration.apply(p,"gui/generator.yml",y,getClass().getResourceAsStream("/gui/generator.yml"));
        assertEquals("管理员标题",y.getString("title"));assertEquals(List.of("    A   B"),y.getStringList("shape"));
        assertEquals("genlite:basic_upgrade_unit",y.getString("elements.A.item"));assertFalse(y.contains("elements.A.material"));
        assertEquals("custom:special",y.getString("elements.B.item"));
    }
    @Test void futureVersionDoesNotWriteAnything() throws Exception {
        var y=new YamlConfiguration();y.set("upgrade-item-version",2);Path p=root.resolve("gui.yml");y.save(p.toFile());String before=Files.readString(p);
        assertThrows(IllegalArgumentException.class,() -> UpgradeUnitMenuMigration.apply(p,"gui/generator.yml",y,getClass().getResourceAsStream("/gui/generator.yml")));
        assertEquals(before,Files.readString(p));
    }
}
