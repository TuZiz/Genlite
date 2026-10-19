package cn.genlite.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** Migrate the user's layout in place: no claims, one output control, bottom-center close. */
public final class ContainerConfigurationMigration {
    private ContainerConfigurationMigration() {}
    public static void upgrade(Path file,String name,YamlConfiguration yaml,InputStream resource) throws Exception {
        int expected=(name.equals("lang/zh_cn.yml")||name.equals("gui/generator.yml"))?4:name.equals("gui/collector.yml")?3:2;
        int current=yaml.getInt("schema-version");
        if(current==expected) {if(resource!=null) resource.close();return;}
        if(current<1 || current>expected) {if(resource!=null) resource.close();throw new IllegalArgumentException(name+":schema-version="+current+" unsupported");}
        var defaults=new YamlConfiguration();
        try(var reader=new InputStreamReader(Objects.requireNonNull(resource),StandardCharsets.UTF_8)) {defaults.load(reader);}
        switch(name) {
            case "gui/generator.yml" -> generator(yaml,defaults);
            case "gui/collector.yml","gui/warehouse.yml" -> {
                yaml.set("shape",defaults.getStringList("shape"));yaml.set("elements",null);copy(defaults,yaml,"elements");
            }
            case "lang/zh_cn.yml" -> {for(String key:List.of("queued","usage","unsupported-item","inventory-full","empty")) yaml.set(key,defaults.getString(key));}
            case "warehouse.yml" -> { /* Slot count now describes resource types, keeping the configured value. */ }
            default -> throw new IllegalArgumentException("Unexpected container configuration "+name);
        }
        yaml.set("schema-version",expected);ConfigRevisionWriter.write(file,yaml,".container-v"+current+".bak");
    }
    private static void generator(YamlConfiguration yaml,YamlConfiguration defaults) {
        var rows=new ArrayList<>(yaml.getStringList("shape"));
        if(rows.equals(List.of("#########","#F#S#O###","#B#M#L#P#","#U##W##D#","####C####"))) rows=new ArrayList<>(defaults.getStringList("shape"));
        Set<Character> remove=new HashSet<>();
        var elements=yaml.getConfigurationSection("elements");
        if(elements==null) throw new IllegalArgumentException("gui/generator.yml:elements missing");
        for(String key:elements.getKeys(false)) if(key.length()==1 && "claim".equals(elements.getString(key+".action"))) remove.add(key.charAt(0));
        boolean seen=false;
        for(int i=0;i<rows.size();i++) {
            var row=new StringBuilder(rows.get(i));
            for(int j=0;j<row.length();j++) {
                char c=row.charAt(j);
                if(remove.contains(c)) row.setCharAt(j,'#');
                if(c=='O'||c=='M') {row.setCharAt(j,seen?'#':'O');seen=true;}
            }
            rows.set(i,row.toString());
        }
        if(rows.isEmpty()) throw new IllegalArgumentException("gui/generator.yml:shape empty");
        var last=new StringBuilder(rows.getLast());last.setCharAt(4,'X');rows.set(rows.size()-1,last.toString());
        yaml.set("shape",rows);copy(defaults,yaml,"elements.O");copy(defaults,yaml,"elements.X");yaml.set("elements.M",null);
        for(char c:remove) yaml.set("elements."+c,null);
    }
    private static void copy(YamlConfiguration from,YamlConfiguration to,String path) {
        Object value=from.get(path);
        if(value instanceof ConfigurationSection section) for(String key:section.getKeys(false)) copy(from,to,path+"."+key);
        else to.set(path,value);
    }
}
