package cn.genlite.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ItemLoreMigrationTest {
    @TempDir Path directory;
    @Test void addsDetailsOnceAndPreservesCustomLoreNamesCommentsAndUnknownFields() throws Exception {
        Path file=directory.resolve("木材生成器.yml");
        String old="schema-version: 1\n# 用户名称不要覆盖\ndisplay:\n  name: '<italic:false><#123456>我的树木'\n  lore:\n    - '<italic:false><#123456>自定义描述 <level>'\ncustom-option: keep\n";
        Files.writeString(file,old);var doc=ConfigDocument.load(file,"genlite/木材生成器.yml");
        ItemLoreMigration.apply(file,doc,"timber_generator");
        assertEquals(3,doc.yaml().getInt("display.lore-version"));
        assertEquals("<italic:false><#123456>我的树木",doc.yaml().getString("display.name"));
        assertEquals(5,doc.yaml().getStringList("display.lore").size());
        assertTrue(doc.yaml().getStringList("display.lore").get(1).contains("<fuel>"));
        assertEquals("keep",doc.yaml().getString("custom-option"));assertTrue(Files.readString(file).contains("# 用户名称不要覆盖"));
        assertEquals(old,Files.readString(directory.resolve("木材生成器.yml.item-lore-v1.bak")));
        String once=Files.readString(file);ItemLoreMigration.apply(file,doc,"timber_generator");assertEquals(once,Files.readString(file));
    }
    @Test void versionTwoOnlyUpdatesDismantleHintAndRetainsConfiguredFuelLines() throws Exception {
        Path file=directory.resolve("木材生成器.yml");
        String old=Files.readString(Path.of("src/main/resources/genlite/木材生成器.yml"))
                .replace("lore-version: 3","lore-version: 2");
        var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.loadFromString(old);
        var lines=yaml.getStringList("display.lore");
        for(int i=0;i<lines.size();i++) {
            if(lines.get(i).contains("<fuel>")) lines.set(i,"<italic:false><#123456>我的燃料 <fuel>");
            if(lines.get(i).contains("潜行+右键")) lines.set(i,"<italic:false><#A5BBC1>直接破坏可拆走，燃料和内部物品保留");
        }
        yaml.set("display.lore",lines);Files.writeString(file,yaml.saveToString());
        var doc=ConfigDocument.load(file,"genlite/木材生成器.yml");ItemLoreMigration.apply(file,doc,"timber_generator");
        var next=doc.yaml().getStringList("display.lore");assertEquals(lines.size(),next.size());
        assertTrue(next.contains("<italic:false><#123456>我的燃料 <fuel>"));
        assertEquals(1,next.stream().filter(line -> line.contains("潜行+右键")).count());
        assertTrue(Files.exists(directory.resolve("木材生成器.yml.item-lore-v2.bak")));
    }
}
