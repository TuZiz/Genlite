import cn.genlite.config.MachineConfigurations;
import cn.genlite.display.MachineText;
import cn.genlite.domain.*;
import cn.genlite.gui.Messages;
import com.google.gson.*;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Offline configuration migration and real item renderer probe. Run against staging copies, never live state. */
class VerifyItemLore {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);var configs=MachineConfigurations.read(root.resolve("genlite"));
        var language=new YamlConfiguration();language.load(root.resolve("lang/zh_cn.yml").toFile());
        var text=new MachineText(configs.rules(),configs.displays(),new Messages(language));
        var output=new JsonArray();
        for(String type:MachineConfigurations.FILES.keySet()) {
            Machine packed=Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),type,3)
                    .contents(type.equals("resource_collector")?0:71,0,9,type.equals("resource_collector")?Map.of("minecraft:cobblestone",65,"minecraft:oak_log",64):Map.of()).retirement(true);
            var components=text.lore(type,3,packed.id(),packed.shellToken(),new WorldState(1,Map.of(packed.id(),packed),Map.of()));
            var templates=configs.displays().get(type).lore();
            for(int i=0;i<templates.size();i++) {
                String template=templates.get(i);var entry=new JsonObject();
                entry.addProperty("path",args[2]+"/genlite/"+MachineConfigurations.FILES.get(type));
                entry.addProperty("field","display.lore."+i);
                entry.addProperty("text_hash",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(template.getBytes(StandardCharsets.UTF_8))));
                entry.addProperty("source","plugin_renderer_probe");entry.addProperty("runtime","Actual MachineText/Messages; Adventure 5.2.0");
                var placeholders=new JsonArray();var matcher=Pattern.compile("<(level|machine|fuel|fuel_capacity|buffer|buffer_capacity|seconds|count|fuel_per_item|mode|stored_items|used_slots|slots)>").matcher(template);
                var names=new LinkedHashSet<String>();while(matcher.find()) names.add(matcher.group(1));names.forEach(placeholders::add);
                entry.add("placeholders",placeholders);entry.add("component",JsonParser.parseString(GsonComponentSerializer.gson().serialize(components.get(i))));output.add(entry);
            }
        }
        Files.writeString(Path.of(args[1]),new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(output),StandardCharsets.UTF_8);
        System.out.println("Machine lore components rendered: "+output.size());
    }
}
