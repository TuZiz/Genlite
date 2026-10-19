package cn.genlite.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** IO executor only. Add three-mode text/menu defaults while preserving custom entries, unknown keys and comments. */
public final class OutputConfigurationMigration {
    private OutputConfigurationMigration() {}
    public static void upgrade(Path file,String name,YamlConfiguration yaml,InputStream defaults) throws Exception {
        int version=yaml.getInt("schema-version");
        if(version==3 || version==4) {if(defaults!=null) defaults.close();return;}
        if(version!=1 && version!=2) {if(defaults!=null) defaults.close();throw new IllegalArgumentException(name+":schema-version="+version+" unsupported");}
        var bundled=new YamlConfiguration();
        try(var reader=new InputStreamReader(Objects.requireNonNull(defaults,"Missing resource "+name),StandardCharsets.UTF_8)) {bundled.load(reader);}
        if(name.equals("lang/zh_cn.yml")) {
            for(String key:List.of("mode.drop","mode.warehouse")) if(!yaml.contains(key)) yaml.set(key,bundled.get(key));
            if("<#68B9B1>连接收集器".equals(yaml.getString("mode.collector"))) yaml.set("mode.collector",bundled.get("mode.collector"));
            if("<#D7AF65>/genlite claim 领取物品；/genlite give 物品ID [数量] 发放物品（管理权限）。".equals(yaml.getString("usage"))) yaml.set("usage",bundled.get("usage"));
            if(yaml.getString("usage")!=null) yaml.set("usage",yaml.getString("usage").replace("<物品ID>","[物品ID]"));
            var labels=bundled.getConfigurationSection("item-labels");
            for(String id:Objects.requireNonNull(labels).getKeys(false)) if(!yaml.contains("item-labels."+id)) yaml.set("item-labels."+id,labels.get(id));
            Map<String,String> names=Map.ofEntries(Map.entry("精密齿轮","铁环"),Map.entry("齿轮","铁环"),Map.entry("电磁铜线圈","铜丝"),
                    Map.entry("铜线圈","铜丝"),Map.entry("机械零件","铁片"),Map.entry("扩容仓储模块","木箱"),Map.entry("仓储模块","木箱"),
                    Map.entry("工业集成芯片","晶片"),Map.entry("工业芯片","晶片"),Map.entry("精炼高强合金","合金"),Map.entry("精炼合金","合金"),Map.entry("✦ 远古动力核心","远古核心"));
            for(String id:Objects.requireNonNull(yaml.getConfigurationSection("item-labels")).getKeys(false)) {
                String key="item-labels."+id,value=yaml.getString(key);
                if(value!=null) {for(String old:names.keySet().stream().sorted(Comparator.comparingInt(String::length).reversed()).toList()) value=value.replace(old,names.get(old));yaml.set(key,value);}
            }
        } else if(name.equals("gui/generator.yml")) {
            List<String> rows=yaml.getStringList("shape");
            for(int i=0;i<rows.size();i++) if(bundled.contains("elements.W") && rows.get(i).equals("#U#####D#")) rows.set(i,"#U##W##D#");
            yaml.set("shape",rows);
            if(String.join("",rows).contains("W") && !yaml.contains("elements.W")) copy(bundled,yaml,"elements.W");
            for(String symbol:List.of("M","O")) {
                String key="elements."+symbol+".lore";var lines=yaml.getStringList(key);
                for(int i=0;i<lines.size();i++) {
                    String line=lines.get(i);
                    if(line.equals("<italic:false><#A5BBC1>点击切换内部缓存 / 连接收集器")) lines.set(i,bundled.getStringList("elements.O.lore").getFirst());
                    if(line.equals("<italic:false><#A5BBC1>点击取出最多 64 个至待领取列表")) lines.set(i,bundled.getStringList("elements.O.lore").getFirst());
                }
                yaml.set(key,lines);
            }
            if("<italic:false><#D7AF65>产物缓存".equals(yaml.getString("elements.O.name"))) {
                for(String field:List.of("name","material","lore","action")) yaml.set("elements.O."+field,bundled.get("elements.O."+field));
            }
            if("input".equals(yaml.getString("elements.I.action"))) {
                yaml.set("shape",yaml.getStringList("shape").stream().map(row -> row.replace('I',yaml.contains("elements.#")?'#':' ')).toList());
                yaml.set("elements.I",null);
            }
        } else throw new IllegalArgumentException("Unsupported output migration file="+name);
        yaml.set("schema-version",3);
        Path temporary=file.resolveSibling(file.getFileName()+".migrating");
        try {
            Path backup=file.resolveSibling(file.getFileName()+".v"+version+".bak");if(!Files.exists(backup)) Files.copy(file,backup);
            Files.writeString(temporary,QuotedYamlText.format(yaml.saveToString()),StandardCharsets.UTF_8);
            var check=new YamlConfiguration();check.load(temporary.toFile());
            if(check.getInt("schema-version")!=3) throw new IllegalArgumentException("Invalid migrated output schema");
            Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception error) {throw new IOException(name+":schema-version="+version+"->3 migration failed; original/backup preserved",error);}
    }
    private static void copy(YamlConfiguration from,YamlConfiguration to,String path) {
        var value=from.get(path);
        if(value instanceof ConfigurationSection section) for(String child:section.getKeys(false)) copy(from,to,path+"."+child);
        else to.set(path,value);
    }
}
