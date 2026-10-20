import cn.genlite.config.MachineConfigurations;
import cn.genlite.display.MachineText;
import cn.genlite.domain.*;
import cn.genlite.gui.Messages;
import com.google.gson.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Exports final components from the plugin's actual shared renderer, using real level-three values. */
class VerifyPresentation {
    public static void main(String[] args) throws Exception {
        var loaded=MachineConfigurations.read(Path.of("src/main/resources/genlite"));
        var lang=new YamlConfiguration();lang.load(Path.of("src/main/resources/lang/zh_cn.yml").toFile());
        var messages=new Messages(lang);var text=new MachineText(loaded.rules(),loaded.displays(),messages);
        var output=new JsonArray();
        for(String file:Files.readAllLines(Path.of(args[0]))) {
            if(!file.endsWith(".yml")) continue;
            var yaml=new YamlConfiguration();yaml.load(Path.of(file).toFile());
            String type=loaded.rules().types().keySet().stream().filter(t -> file.contains(t) || file.endsWith(MachineConfigurations.FILES.get(t))).findFirst()
                    .orElse(file.contains("collector")?"resource_collector":"timber_generator");
            var m=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),type,3);
            var values=text.variables(m,Map.of(m.id(),m),Set.of(m.id()));
            values.put("item",messages.label("genlite:basic_upgrade_unit"));
            values.put("amount",Component.text(3));values.put("owned",Component.text(2));
            values.put("fuel_space",Component.text(loaded.rules().fuelSpace(m)));
            values.put("fuel_items",Component.text(m.collectorType()?0:m.fuel()/loaded.rules().fuelUnits(m)));
            for(var entry:yaml.getValues(true).entrySet()) {
                if(entry.getValue() instanceof String s) add(output,file,entry.getKey(),s,messages,values);
                if(entry.getValue() instanceof List<?> lines) for(int i=0;i<lines.size();i++)
                    if(lines.get(i) instanceof String s) add(output,file,entry.getKey()+"."+i,s,messages,values);
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(output));
        System.out.println("Final components exported: "+output.size());
    }
    private static void add(JsonArray output,String file,String field,String template,Messages messages,Map<String,Component> values) throws Exception {
        var entry=new JsonObject();entry.addProperty("path",file);entry.addProperty("field",field);
        entry.addProperty("text_hash",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(template.getBytes(StandardCharsets.UTF_8))));
        entry.addProperty("source","plugin_renderer_probe");entry.addProperty("runtime","Actual Messages.render with MachineText.variables; level 3; offline");
        var placeholders=new JsonArray();values.keySet().stream().filter(k -> template.contains("<"+k+">")).forEach(placeholders::add);
        entry.add("placeholders",placeholders);
        entry.add("component",JsonParser.parseString(GsonComponentSerializer.gson().serialize(messages.render(template,values))));
        output.add(entry);
    }
}
