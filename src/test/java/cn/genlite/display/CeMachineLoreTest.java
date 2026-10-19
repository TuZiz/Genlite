package cn.genlite.display;

import cn.genlite.config.MachineConfigurations;
import cn.genlite.gui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CeMachineLoreTest {
    @Test void ceCatalogueLoreMatchesRealLevelOneMachineLoreWithoutUnresolvedPlaceholders() throws Exception {
        var loaded=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var language=new YamlConfiguration();language.load(Path.of("src/main/resources/lang/zh_cn.yml").toFile());
        var messages=new Messages(language);var text=new MachineText(loaded.rules(),loaded.displays(),messages);
        for(String type:loaded.rules().types().keySet()) {
            var ce=new YamlConfiguration();ce.load(Path.of("craftengine/genlite/configuration/machines/"+type+".yml").toFile());
            var lore=ce.getStringList("items.genlite:"+type+".data.lore");assertFalse(lore.isEmpty(),type);
            var rendered=lore.stream().map(s -> messages.render(s,Map.of())).toList();
            assertEquals(text.lore(type,1).stream().map(this::plain).toList(),rendered.stream().map(this::plain).toList(),type);
            rendered.forEach(this::nonItalic);
        }
    }
    private String plain(Component c) {return (c instanceof TextComponent t?t.content():"")+c.children().stream().map(this::plain).reduce("",String::concat);}
    private void nonItalic(Component c) {assertNotEquals(TextDecoration.State.TRUE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::nonItalic);}
}
