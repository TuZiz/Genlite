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
        var messages=new Messages(language);var text=new MachineText(loaded.rules(),loaded.displays(),messages);
        for(String type:loaded.rules().types().keySet()) for(int level=1;level<=5;level++) {
            var m=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),type,level);
            var variables=text.variables(m,Map.of(m.id(),m),Set.of(m.id()));
            String title=plain(messages.render(menu.getString("title"),variables));
            String heading=plain(text.hologram(m,Map.of(m.id(),m),Set.of(m.id()))).split("\n")[0];
            if(m.collectorType()) {
                assertEquals("普通收集器[初级]",title);assertEquals(title,heading);
            } else {
                assertEquals(1,title.split("Lv\\.",-1).length-1);assertEquals(title,heading);
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
    @Test void packedLoreUsesDurableFuelWithoutObsoleteStorageAndDefaultsNewItemsToZero() throws Exception {
        var loaded=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var language=new YamlConfiguration();language.load(Path.of("src/main/resources/lang/zh_cn.yml").toFile());
        var text=new MachineText(loaded.rules(),loaded.displays(),new Messages(language));
        var packed=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),"timber_generator",3)
                .contents(71,0,9,Map.of()).output(OutputMode.COLLECTOR).retirement(true);
        var state=new WorldState(1,Map.of(packed.id(),packed),Map.of());
        var lore=text.lore(packed.type(),1,packed.id(),packed.shellToken(),state);
        String tooltip=lore.stream().map(this::plain).reduce("",(a,b)->a+"\n"+b);
        assertTrue(tooltip.contains("71 / 1536"));assertFalse(tooltip.contains("内部产物"));
        assertTrue(tooltip.contains("每 8 秒 2 个"));assertTrue(tooltip.contains("耗能 1/个"));
        assertTrue(tooltip.contains(plain(new Messages(language).key("mode.drop"))));
        assertFalse(tooltip.contains("<fuel>"));lore.forEach(this::assertNoItalic);
        String fresh=text.lore("timber_generator",1).stream().map(this::plain).reduce("",String::concat);
        assertTrue(fresh.contains("0 / 512"));assertFalse(fresh.contains("0 / 64"));
        String stale=text.lore(packed.type(),1,packed.id(),UUID.randomUUID(),state).stream().map(this::plain).reduce("",String::concat);
        assertTrue(stale.contains("0 / 512"));assertFalse(stale.contains("71 /"));
        String consumed=text.lore(packed.type(),1,packed.id(),packed.shellToken(),new WorldState(2,Map.of(packed.id(),packed.consumeShell()),Map.of()))
                .stream().map(this::plain).reduce("",String::concat);
        assertTrue(consumed.contains("0 / 512"));
        for(String type:List.of("cobblestone_generator","timber_generator","iron_generator")) {
            var generator=Machine.create(packed.position(),packed.home(),packed.owner(),type,1);
            String hologram=plain(text.hologram(generator,Map.of(generator.id(),generator),Set.of(generator.id())));
            assertTrue(hologram.contains("燃料"));assertFalse(hologram.contains("仓储"));
            assertFalse(hologram.contains("<buffer"));
        }
    }
    @Test void collectorLoreCountsStacksAndTotalWithoutFuelOrProduction() throws Exception {
        var loaded=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var language=new YamlConfiguration();language.load(Path.of("src/main/resources/lang/zh_cn.yml").toFile());
        var text=new MachineText(loaded.rules(),loaded.displays(),new Messages(language));
        var packed=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),"resource_collector",1)
                .contents(0,0,0,Map.of("minecraft:cobblestone",65,"minecraft:oak_log",64)).retirement(true);
        var lore=text.lore(packed.type(),1,packed.id(),packed.shellToken(),new WorldState(1,Map.of(packed.id(),packed),Map.of()));
        String tooltip=lore.stream().map(this::plain).reduce("",String::concat);
        assertTrue(tooltip.contains("内部物品：129 个"));assertTrue(tooltip.contains("3 / 27"));
        assertFalse(tooltip.contains("内部燃料"));lore.forEach(this::assertNoItalic);
    }
    @Test void actualRendererSharesConfiguredNamesAndRefreshesDynamicStateWithoutItalics() throws Exception {
        var loaded=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var language=new YamlConfiguration();language.load(Path.of("src/main/resources/lang/zh_cn.yml").toFile());
        var displays=new HashMap<>(loaded.displays());var original=displays.get("cobblestone_generator");
        displays.put("cobblestone_generator",new MachineDisplay("<italic:false><#D7AF65>原石设备 <level>",
                List.of("<italic:false><#A5BBC1>等级 <level>"),original.hologram()));
        var text=new MachineText(loaded.rules(),displays,new Messages(language));
        var m=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),"cobblestone_generator",3).contents(71,0,9,Map.of());
        String name=plain(text.name(m.type(),3));assertEquals("原石设备 3",name);
        assertEquals("等级 3",plain(text.lore(m.type(),3).getFirst()));
        var hologram=text.hologram(m,Map.of(m.id(),m),Set.of(m.id()));
        assertTrue(plain(hologram).contains(name));assertTrue(plain(hologram).contains("71"));assertTrue(plain(hologram).contains("1536"));
        var empty=m.contents(0,0,0,Map.of());
        assertTrue(plain(text.hologram(empty,Map.of(m.id(),empty),Set.of(m.id()))).contains(plain(new Messages(language).key("status.no_fuel"))));
        var paused=m.settings(m.level(),true,false,false,null);
        assertTrue(plain(text.hologram(paused,Map.of(m.id(),paused),Set.of(m.id()))).contains(plain(new Messages(language).key("status.paused"))));
        assertFalse(plain(hologram).contains("<fuel>"));assertNoItalic(hologram);
    }
    private String plain(Component component) {
        return (component instanceof TextComponent t?t.content():"")+component.children().stream().map(this::plain).reduce("",String::concat);
    }
    private void assertNoItalic(Component c) {
        assertNotEquals(TextDecoration.State.TRUE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::assertNoItalic);
    }
}
