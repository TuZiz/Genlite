package cn.genlite.gui;

import cn.genlite.config.*;
import cn.genlite.display.MachineText;
import cn.genlite.domain.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContainerMenuRendererTest {
    @Test void actualContainerParametersResolveCollectorNameAndKeepWarehouseCounts() throws Exception {
        var config=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var language=load("lang/zh_cn.yml");var messages=new Messages(language);
        var text=new MachineText(config.rules(),config.displays(),messages,id -> Component.text("CE收集器"));
        var renderer=new ContainerMenuRenderer(null,null,messages,text);
        var collector=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),"resource_collector",1);
        Component title=messages.render(load("gui/collector.yml").getString("title"),renderer.variables(collector,27,1));
        assertEquals("CE收集器 · 1/27",plain(title));nonItalic(title);
        assertFalse(plain(title).contains("<machine>"));
        String output=System.getProperty("genlite.component-output");
        if(output!=null) {
            var sample=new com.google.gson.JsonObject();
            sample.addProperty("path","src/main/resources/gui/collector.yml");sample.addProperty("field","title");
            sample.addProperty("text_hash",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(load("gui/collector.yml").getString("title").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            sample.addProperty("source","plugin_test");sample.addProperty("runtime","ContainerMenuRenderer.variables -> Messages.render; real collector title regression test");
            sample.add("placeholders",new com.google.gson.Gson().toJsonTree(List.of("machine","used_slots","slots")));
            sample.add("component",com.google.gson.JsonParser.parseString(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(title)));
            java.nio.file.Files.writeString(Path.of(output),"["+sample+"]");
        }
        var warehouse=messages.render(load("gui/warehouse.yml").getString("title"),renderer.variables(null,54,2));
        assertEquals("✦ 中央仓库 · 2/54",plain(warehouse));nonItalic(warehouse);
    }
    @Test void collectorTitleFollowsCustomConfiguredNameAndLevel() throws Exception {
        var config=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var messages=new Messages(load("lang/zh_cn.yml"));var displays=new HashMap<>(config.displays());
        var original=displays.get("resource_collector");

        var renderer=new ContainerMenuRenderer(null,null,messages,new MachineText(config.rules(),displays,messages,id -> Component.text("CE自定义收集器")));
        var collector=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),"resource_collector",3);
        var title=messages.render(load("gui/collector.yml").getString("title"),renderer.variables(collector,27,0));
        assertEquals("CE自定义收集器 · 0/27",plain(title));nonItalic(title);
    }
    private YamlConfiguration load(String name) throws Exception {
        var yaml=new YamlConfiguration();yaml.load(Path.of("src/main/resources/"+name).toFile());return yaml;
    }
    private String plain(Component c) {return (c instanceof TextComponent t?t.content():"")+c.children().stream().map(this::plain).reduce("",String::concat);}
    private void nonItalic(Component c) {assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC));c.children().forEach(this::nonItalic);}
}
