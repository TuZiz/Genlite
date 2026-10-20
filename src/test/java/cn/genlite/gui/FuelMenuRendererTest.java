package cn.genlite.gui;

import cn.genlite.config.MachineConfigurations;
import cn.genlite.display.MachineText;
import cn.genlite.domain.*;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FuelMenuRendererTest {
    @Test void actualFuelParametersRenderNameCapacityAndProductionWithoutItalic() throws Exception {
        var loaded=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var lang=load("lang/zh_cn.yml");var gui=load("gui/fuel.yml");var messages=new Messages(lang);
        var renderer=new FuelMenuRenderer(gui,null,messages,new MachineText(loaded.rules(),loaded.displays(),messages),null);
        for(String type:List.of("cobblestone_generator","timber_generator","iron_generator")) {
            var m=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),type,3).contents(7,0,0,Map.of());
            var vars=renderer.variables(m,loaded.rules());
            assertEquals(Integer.toString(loaded.rules().fuelSpace(m)),plain(vars.get("fuel_space")));
            var title=messages.render(gui.getString("title"),vars);assertFalse(plain(title).contains("<machine>"));assertTrue(plain(title).contains("燃料仓"));
            check(title);
            for(String line:gui.getStringList("elements.#.lore")) {
                var rendered=messages.render(line,vars);check(rendered);
                assertFalse(plain(rendered).matches(".*<[a-z_]+>.*"),plain(rendered));
            }
        }
    }
    private YamlConfiguration load(String path) throws Exception {var y=new YamlConfiguration();y.load(Path.of("src/main/resources/"+path).toFile());return y;}
    private String plain(Component c) {return (c instanceof TextComponent t?t.content():"")+c.children().stream().map(this::plain).reduce("",String::concat);}
    private void check(Component c) {assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::check);}
}
