package cn.genlite.config;

import cn.genlite.domain.Machine;
import cn.genlite.domain.Position;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeUnitMigrationTest {
    @TempDir Path root;
    private Path legacy(String id,boolean custom) throws Exception {
        Path path=root.resolve(id+".yml");Files.copy(Path.of("src/main/resources/genlite/"+id+".yml"),path);
        var doc=ConfigDocument.load(path,id);doc.yaml().set("upgrade-item-version",null);doc.yaml().set("upgrades",null);
        BasicMaterialsMigration.VANILLA.forEach((level,costs) -> costs.forEach((item,n) -> doc.yaml().set("upgrades."+level+"."+item,n)));
        if(custom) doc.yaml().set("upgrades.2.minecraft:oak_log",17);
        Files.writeString(path,QuotedYamlText.format(doc.yaml().saveToString())+"\n# 管理员注释\ncustom-field: keep\n");return path;
    }
    @Test void vanillaGeneratorCostsMigrateOnceWithBackupsAndComments() throws Exception {
        for(String id:List.of("cobblestone_generator","timber_generator","iron_generator")) {
            Path p=legacy(id,false);String before=Files.readString(p);var d=ConfigDocument.load(p,id);
            UpgradeUnitMigration.apply(p,d,id);
            var expected=id.equals("iron_generator")?List.of(3,9,24,60):List.of(2,6,16,40);
            for(int n=2;n<=5;n++) assertEquals(Map.of("genlite:basic_upgrade_unit",expected.get(n-2)),d.yaml().getConfigurationSection("upgrades."+n).getValues(false));
            assertEquals(before,Files.readString(p.resolveSibling(p.getFileName()+".upgrade-items-v0.bak")));
            assertTrue(Files.readString(p).contains("管理员注释"));assertEquals("keep",d.yaml().getString("custom-field"));
            String once=Files.readString(p);UpgradeUnitMigration.apply(p,d,id);assertEquals(once,Files.readString(p));
        }
    }
    @Test void customCostsAndCollectorArePreserved() throws Exception {
        Path p=legacy("iron_generator",true);var d=ConfigDocument.load(p,"iron_generator");
        UpgradeUnitMigration.apply(p,d,"iron_generator");assertEquals(17,d.yaml().getInt("upgrades.2.minecraft:oak_log"));assertEquals(1,d.yaml().getInt("upgrade-item-version"));
        Path collector=legacy("resource_collector",false);String before=Files.readString(collector);
        UpgradeUnitMigration.apply(collector,ConfigDocument.load(collector,"collector"),"resource_collector");assertEquals(before,Files.readString(collector));
    }
    @Test void unsupportedVersionAndInvalidCustomCostsDoNotRewriteTheFile() throws Exception {
        Path p=legacy("iron_generator",false);var d=ConfigDocument.load(p,"iron_generator");d.yaml().set("upgrade-item-version",2);
        String before=Files.readString(p);assertThrows(IllegalArgumentException.class,() -> UpgradeUnitMigration.apply(p,d,"iron_generator"));assertEquals(before,Files.readString(p));
        d.yaml().set("upgrade-item-version",null);d.yaml().set("upgrades.2.minecraft:oak_log",-1);
        assertThrows(IllegalArgumentException.class,() -> UpgradeUnitMigration.apply(p,d,"iron_generator"));assertEquals(before,Files.readString(p));
    }
    @Test void failedAtomicPublishKeepsOriginalFileAndBackup() throws Exception {
        Path p=legacy("iron_generator",false);String before=Files.readString(p);var d=ConfigDocument.load(p,"iron_generator");
        Files.createDirectory(p.resolveSibling(p.getFileName()+".migrating"));
        assertThrows(java.io.IOException.class,() -> UpgradeUnitMigration.apply(p,d,"iron_generator"));assertEquals(before,Files.readString(p));
        assertEquals(before,Files.readString(p.resolveSibling(p.getFileName()+".upgrade-items-v0.bak")));
    }
    @Test void bundledCostsUseOnlyUpgradeUnitsAtEveryGeneratorLevel() throws Exception {
        var rules=MachineConfigurations.read(Path.of("src/main/resources/genlite")).rules();
        for(String id:List.of("cobblestone_generator","timber_generator","iron_generator")) for(int level=1;level<5;level++) {
            Machine m=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),id,level);
            assertEquals(Set.of("genlite:basic_upgrade_unit"),rules.upgrade(m).keySet());
        }
    }
    @Test void oldestDefaultLayoutTraversesBothMigrationsToUpgradeUnits() throws Exception {
        Files.copy(Path.of("src/test/resources/legacy-machines.yml"),root.resolve("machines.yml"));
        var loaded=MachineConfigurations.load(root,key -> getClass().getResourceAsStream("/"+key));
        for(String id:List.of("cobblestone_generator","timber_generator","iron_generator")) {
            assertEquals(Set.of("genlite:basic_upgrade_unit"),loaded.rules().types().get(id).upgrades().get(2).keySet());
        }
        assertTrue(Files.exists(root.resolve("machines.yml")));
    }
}
