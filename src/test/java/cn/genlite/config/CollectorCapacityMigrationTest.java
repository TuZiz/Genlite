package cn.genlite.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectorCapacityMigrationTest {
    @TempDir Path directory;
    @Test void oldCapacityBecomes27AtEveryLevelAndPreservesOriginalBackup() throws Exception {
        Path file=directory.resolve("resource_collector.yml");String old=Files.readString(Path.of("src/main/resources/genlite/resource_collector.yml")).replace("storage-version: 2","storage-version: 1").replace("slots: 27","slots: 54")+"\n# 用户备注\ncustom-setting: keep\n";Files.writeString(file,old);
        var doc=ConfigDocument.load(file,"genlite/resource_collector.yml");CollectorCapacityMigration.apply(file,doc,"resource_collector");
        for(int n=1;n<=5;n++) assertEquals(27,doc.yaml().getInt("levels."+n+".slots"));
        assertEquals(old,Files.readString(directory.resolve("resource_collector.yml.storage-v1.bak")));
        assertTrue(Files.readString(file).contains("# 用户备注"));assertEquals("keep",doc.yaml().getString("custom-setting"));
        String once=Files.readString(file);CollectorCapacityMigration.apply(file,doc,"resource_collector");assertEquals(once,Files.readString(file));
    }
    @Test void collectorMenuHas27StockCellsAndSeparateFooter() throws Exception {
        var y=new org.bukkit.configuration.file.YamlConfiguration();y.load(Path.of("src/main/resources/gui/collector.yml").toFile());
        String grid=String.join("",y.getStringList("shape"));assertEquals(36,grid.length());assertEquals(27,grid.chars().filter(c->c=='O').count());assertEquals('O',grid.charAt(13));assertEquals('X',grid.charAt(31));
        assertFalse(y.contains("elements.O.lore"));
    }
}
