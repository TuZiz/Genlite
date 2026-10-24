package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class StatusFuelMigrationTest {
    @TempDir Path directory;
    private YamlConfiguration load(Path path) throws Exception {
        var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(path.toFile());return yaml;
    }
    private void apply(Path path,YamlConfiguration yaml) throws Exception {
        StatusFuelMigration.apply(path,yaml,getClass().getResourceAsStream("/gui/generator.yml"));
    }
    @Test void upgradeRetainsCustomLayoutTextAndCommentsAndIsIdempotent() throws Exception {
        Path path=directory.resolve("generator.yml");
        String old="schema-version: 4\n# 保留注释\ncustom: true\nelements:\n  Z:\n    action: refresh\n    lore:\n      - '<#123456>价值：<value>'\n      - '自定义说明'\n  F:\n    action: fuel\n    lore:\n      - '原有入口'\n";
        Files.writeString(path,old);var yaml=load(path);apply(path,yaml);
        String lore=String.join("",yaml.getStringList("elements.Z.lore"));
        assertTrue(lore.contains("<fuel_output>"));assertTrue(lore.contains("<output_item>"));assertTrue(lore.contains("自定义说明"));
        assertEquals("原有入口",yaml.getStringList("elements.F.lore").getFirst());
        assertTrue(yaml.getBoolean("custom"));assertTrue(Files.readString(path).contains("# 保留注释"));
        assertEquals(old,Files.readString(directory.resolve("generator.yml.status-fuel-v0.bak")));
        String once=Files.readString(path);apply(path,load(path));assertEquals(once,Files.readString(path));
    }
    @Test void unsupportedVersionDoesNotOverwriteFile() throws Exception {
        Path path=directory.resolve("future.yml");String old="status-fuel-version: 2\n";Files.writeString(path,old);
        assertThrows(IllegalArgumentException.class,() -> apply(path,load(path)));assertEquals(old,Files.readString(path));
    }
}
