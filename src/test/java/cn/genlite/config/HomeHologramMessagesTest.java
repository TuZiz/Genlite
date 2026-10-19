package cn.genlite.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class HomeHologramMessagesTest {
    @TempDir Path directory;
    @Test void addsOnlyMissingTextsAndKeepsCommentsAndUnknownKeysIdempotently() throws Exception {
        Path path=directory.resolve("lang.yml");String old="# 我的文案\nholograms:\n  enabled: '<#123456>我的开启提示'\ncustom: keep\n";
        Files.writeString(path,old);var yaml=new org.bukkit.configuration.file.YamlConfiguration();yaml.options().parseComments(true);yaml.load(path.toFile());
        HomeHologramMessages.apply(path,yaml,getClass().getResourceAsStream("/lang/zh_cn.yml"));
        assertEquals("<#123456>我的开启提示",yaml.getString("holograms.enabled"));assertTrue(yaml.contains("holograms.disabled"));
        assertEquals("keep",yaml.getString("custom"));assertTrue(Files.readString(path).contains("# 我的文案"));
        assertEquals(old,Files.readString(path.resolveSibling("lang.yml.hologram-settings-v0.bak")));
        String once=Files.readString(path);HomeHologramMessages.apply(path,yaml,getClass().getResourceAsStream("/lang/zh_cn.yml"));assertEquals(once,Files.readString(path));
    }
}
