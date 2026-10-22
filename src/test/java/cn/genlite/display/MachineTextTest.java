package cn.genlite.display;

import cn.genlite.config.*;
import cn.genlite.domain.*;
import cn.genlite.gui.Messages;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineTextTest {
    @Test void headingsShowOneLevelAndChestPanesUseActualFuelValuesInsteadOfOuterButton() throws Exception {
        var loaded=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var language=new YamlConfiguration();language.load(Path.of("src/main/resources/lang/zh_cn.yml").toFile());
        var menu=new YamlConfiguration();menu.load(Path.of("src/main/resources/gui/generator.yml").toFile());
        var fuelMenu=new YamlConfiguration();fuelMenu.load(Path.of("src/main/resources/gui/fuel.yml").toFile());
        var messages=new Messages(language);var text=new MachineText(loaded.rules(),loaded.displays(),messages,id -> Component.text("CE机器"));
        for(String type:loaded.rules().types().keySet()) for(int level=1;level<=5;level++) {
            var m=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),type,level);
            var variables=text.variables(m,Map.of(m.id(),m),Set.of(m.id()));
            String title=plain(messages.render(menu.getString("title"),variables));
            String heading=plain(text.hologram(m,Map.of(m.id(),m),Set.of(m.id()))).split("\n")[0];
            assertEquals(title,heading);
            if(!m.collectorType()) {
                assertFalse(String.join("",menu.getStringList("elements.F.lore")).contains("<fuel"));
                variables.put("fuel_space",Component.text(loaded.rules().fuelSpace(m)));
                variables.put("fuel_items",Component.text(m.fuel()/loaded.rules().fuelUnits(m)));
                String fuel=fuelMenu.getStringList("elements.#.lore").stream().map(s -> messages.render(s,variables))
                        .peek(this::assertNoItalic).map(this::plain).reduce("",String::concat);
                var limits=loaded.rules().level(m);
                assertTrue(fuel.contains("每 "+limits.seconds()+" 秒 "+limits.count()+" 个"));
                assertTrue(fuel.contains("每个燃料："+loaded.rules().fuelUnits(m)+" 能量"));
                assertTrue(fuel.contains(loaded.rules().types().get(type).fuelPerItem()+" 能量 / 个"));
                assertTrue(fuel.contains("可用燃料：普通燃料[煤炭级]"));assertFalse(fuel.contains("<"));
            }
        }
        assertNoItalic(messages.render("<#123456><italic>父<child></italic>",
                Map.of("child",Component.text("子").decorate(TextDecoration.ITALIC))));
    }
    private String plain(Component component) {
        return (component instanceof TextComponent t?t.content():"")+component.children().stream().map(this::plain).reduce("",String::concat);
    }
    private void assertNoItalic(Component c) {
        assertNotEquals(TextDecoration.State.TRUE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::assertNoItalic);
    }
}
