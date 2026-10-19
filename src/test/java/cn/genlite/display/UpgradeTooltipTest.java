package cn.genlite.display;

import cn.genlite.gui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeTooltipTest {
    @Test void actualRendererShowsUnitNameRequiredAndOwnedCountsWithoutItalics() throws Exception {
        var yaml=new YamlConfiguration();yaml.load(Path.of("src/main/resources/lang/zh_cn.yml").toFile());
        var messages=new Messages(yaml);
        var c=messages.format("upgrade-cost-line",Map.of("item",messages.label("genlite:basic_upgrade_unit"),
                "amount",Component.text(3),"owned",Component.text(2)));
        String result=plain(c);assertTrue(result.contains("初级升级单元"));assertTrue(result.contains("需 3 个"));assertTrue(result.contains("拥有: 2"));
        nonItalic(c);
    }
    private String plain(Component c) {return (c instanceof TextComponent t?t.content():"")+c.children().stream().map(this::plain).reduce("",String::concat);}
    private void nonItalic(Component c) {assertNotEquals(TextDecoration.State.TRUE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::nonItalic);}
}
