package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** IO executor only: migrate the stock presentation, retaining custom text, fields and comments. */
final class PresentationMigration {
    private PresentationMigration() {}
    static void machine(Path path,ConfigDocument doc,String type) throws Exception {
        var yaml=doc.yaml();
        if (current(path,yaml)) return;
        var lines=new ArrayList<>(yaml.getStringList("hologram.lines"));
        // Only the duplicate stock heading is replaced; custom secondary lines remain intact.
        if (!lines.isEmpty() && List.of(
                "<#FFB75E>✦ <machine> <#7F8C8D>[<#89F7FE>Lv.<level><#7F8C8D>]",
                "<#89F7FE>✦ <machine> <#7F8C8D>[<#FFD700>Lv.<level><#7F8C8D>]").contains(lines.getFirst()))
            lines.set(0,"<machine>");
        yaml.set("hologram.lines",lines);
        save(path,yaml);
    }
    static void menu(Path path,String name,YamlConfiguration yaml,InputStream resource) throws Exception {
        try (resource) {
            if (current(path,yaml)) return;
            var defaults=new YamlConfiguration();
            if (resource==null) throw new IllegalStateException("Missing resource "+name);
            defaults.load(new InputStreamReader(resource,StandardCharsets.UTF_8));
            if (name.equals("lang/zh_cn.yml")) {
                for (String key:List.of("collector-required","item-labels.genlite:fuel"))
                    if (!yaml.contains(key)) yaml.set(key,defaults.get(key));
            } else {
                String title=yaml.getString("title","");
                if (title.equals("<#FFB75E>✦ <#FFD700><bold><machine></bold> <#5C6B73>· <#89F7FE>Lv.<level>")
                        || name.equals("gui/collector.yml") && title.equals("<#D7AF65>收集器 · <used_slots>/<slots>"))
                    yaml.set("title",defaults.getString("title"));
                var elements=yaml.getConfigurationSection("elements");
                if (elements!=null) for (String key:elements.getKeys(false)) {
                    String base="elements."+key;
                    if (yaml.getString(base+".action","").equals("fuel")) {
                        var lore=new ArrayList<>(yaml.getStringList(base+".lore"));
                        var additions=new ArrayList<String>();
                        for (String line:defaults.getStringList("elements.F.lore")) {
                            String tag=List.of("<fuel_item>","<output_item>","<seconds>","<fuel_per_item>").stream()
                                    .filter(line::contains).findFirst().orElse(null);
                            if (tag!=null && lore.stream().noneMatch(s -> s.contains(tag)))
                                additions.add(line);
                        }
                        lore.addAll(Math.min(1,lore.size()),additions);
                        yaml.set(base+".lore",lore);
                    }
                    if (key.equals("#") && yaml.getString(base+".action","").equals("none")
                            && yaml.getString(base+".name","").equals("<#34474F> ")) yaml.set(base+".name","");
                }
            }
            save(path,yaml);
        }
    }
    private static boolean current(Path path,YamlConfiguration yaml) {
        if (yaml.contains("presentation-version") && !yaml.isInt("presentation-version"))
            throw new IllegalArgumentException(path+":presentation-version="+yaml.get("presentation-version")+" invalid; original preserved");
        int version=yaml.getInt("presentation-version",0);
        if (version!=0 && version!=1) throw new IllegalArgumentException(path+":presentation-version="+version+" unsupported; original preserved");
        return version==1;
    }
    private static void save(Path path,YamlConfiguration yaml) throws Exception {
        yaml.set("presentation-version",1);
        ConfigRevisionWriter.write(path,yaml,".presentation-v0.bak");
    }
}
