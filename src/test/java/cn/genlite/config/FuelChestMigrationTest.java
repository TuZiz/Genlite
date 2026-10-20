package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FuelChestMigrationTest {
    @TempDir Path directory;
    private YamlConfiguration load(Path p) throws Exception {var y=new YamlConfiguration();y.options().parseComments(true);y.load(p.toFile());return y;}
    private void apply(Path p,String name,YamlConfiguration y) throws Exception {
        FuelChestMigration.apply(p,name,y,getClass().getResourceAsStream("/"+name));
    }
    @Test void oldThreeRowFuelViewMigratesOncePreservingCommentsUnknownFieldsAndBackup() throws Exception {
        Path p=directory.resolve("fuel.yml");
        String old="schema-version: 1\n# 玩家自己的注释\ncustom: '保留'\ntitle: '自定义燃料仓'\nshape:\n  - '#########'\n  - '#I##F##W#'\n  - '####B####'\nelements:\n  F:\n    action: deposit\n    custom: '未知字段'\n";
        Files.writeString(p,old);var y=load(p);apply(p,"gui/fuel.yml",y);
        FuelChestMigration.validate(y);
        assertEquals(2,y.getInt("schema-version"));assertEquals("保留",y.getString("custom"));
        assertEquals("未知字段",y.getString("elements.F.custom"));assertEquals("自定义燃料仓",y.getString("title"));
        assertTrue(Files.readString(p).contains("# 玩家自己的注释"));
        assertEquals(old,Files.readString(directory.resolve("fuel.yml.fuel-chest-v0.bak")));
        String once=Files.readString(p);apply(p,"gui/fuel.yml",load(p));assertEquals(once,Files.readString(p));
        String shape=String.join("",y.getStringList("shape"));
        assertEquals(27,shape.chars().filter(c -> "fuel-slot".equals(y.getString("elements."+(char)c+".action"))).count());
        assertEquals(8,shape.chars().filter(c -> "info".equals(y.getString("elements."+(char)c+".action"))).count());
        assertEquals('B',shape.charAt(31));
    }
    @Test void generatorRetainsCustomInstructionsButMovesDynamicFuelDetailsIntoChest() throws Exception {
        Path p=directory.resolve("generator.yml");
        Files.writeString(p,"schema-version: 4\n# 保留注释\ncustom: true\nelements:\n  F:\n    action: fuel\n    name: '<#123456>燃料储备'\n    lore:\n      - '自定义介绍'\n      - '剩余燃料：<fuel>/<fuel_capacity>'\n      - '产出物品：<output_item>'\n      - '产出效率：<seconds>秒<count>个'\n      - '打开燃料填充界面'\n");
        var y=load(p);apply(p,"gui/generator.yml",y);
        assertTrue(y.getBoolean("custom"));assertEquals("<#123456>燃料仓",y.getString("elements.F.name"));
        var lore=y.getStringList("elements.F.lore");assertEquals("自定义介绍",lore.getFirst());
        assertFalse(String.join("",lore).contains("<fuel"));assertFalse(String.join("",lore).contains("<output_item>"));
        assertTrue(Files.readString(p).contains("# 保留注释"));
        String once=Files.readString(p);apply(p,"gui/generator.yml",load(p));assertEquals(once,Files.readString(p));
    }
    @Test void invalidFutureVersionsPreserveOriginalBytes() throws Exception {
        for(String name:List.of("gui/fuel.yml","gui/generator.yml")) {
            Path p=directory.resolve(name.replace('/','-'));
            String old=name.endsWith("fuel.yml")?"schema-version: 3\n":"schema-version: 4\nfuel-chest-version: 2\n";
            Files.writeString(p,old);assertThrows(IllegalArgumentException.class,() -> apply(p,name,load(p)));
            assertEquals(old,Files.readString(p));
        }
    }
    @Test void bottomRowMustHaveEightIdenticalGlassPanesAndCenterReturn() throws Exception {
        var y=new YamlConfiguration();y.load(Path.of("src/main/resources/gui/fuel.yml").toFile());
        FuelChestMigration.validate(y);
        y.set("shape",List.of("FFFFFFFFF","FFFFFFFFF","FFFFFFFFF","#######B#"));
        assertThrows(IllegalArgumentException.class,() -> FuelChestMigration.validate(y));
        y.set("shape",List.of("FFFFFFFFF","FFFFFFFFF","FFFFFFFFF","####B###I"));
        y.set("elements.I.material","GRAY_STAINED_GLASS_PANE");y.set("elements.I.action","info");y.set("elements.I.name","");
        y.set("elements.I.lore",List.of("与其他玻璃不同"));
        assertThrows(IllegalArgumentException.class,() -> FuelChestMigration.validate(y));
    }
}
