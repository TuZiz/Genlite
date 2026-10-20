package cn.genlite.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/** IO-only, idempotent upgrade of the fuel view; machine energy and player items are never migrated. */
public final class FuelChestMigration {
    private FuelChestMigration() {}
    public static void apply(Path path,String name,YamlConfiguration yaml,InputStream resource) throws Exception {
        try(resource) {
            boolean fuel=name.equals("gui/fuel.yml");
            if(!fuel && !name.equals("gui/generator.yml")) throw new IllegalArgumentException("Unexpected fuel chest resource "+name);
            String version=fuel?"schema-version":"fuel-chest-version";
            int current=yaml.getInt(version,fuel?-1:0),expected=fuel?2:1;
            if((yaml.contains(version) && !yaml.isInt(version)) || current<0 || current>expected)
                throw new IllegalArgumentException(path+":"+version+"="+yaml.get(version)+" unsupported; original preserved");
            if(current==expected) return;
            if(resource==null) throw new IOException("Missing bundled resource "+name);
            var defaults=new YamlConfiguration();defaults.options().parseComments(true);
            defaults.load(new InputStreamReader(resource,StandardCharsets.UTF_8));
            if(fuel) {
                if(current!=1) throw new IllegalArgumentException(path+":schema-version="+current+" unsupported; original preserved");
                yaml.set("shape",defaults.getStringList("shape"));
                if(yaml.getString("title","").contains("燃料填充")) yaml.set("title",defaults.getString("title"));
                for(String symbol:List.of("#","F","B")) {
                    String base="elements."+symbol;
                    var section=defaults.getConfigurationSection(base);
                    for(String key:section.getKeys(false)) yaml.set(base+"."+key,section.get(key));
                }
                // Replace only the obsolete shipped instruction, keeping user-authored comments.
                var comments=new ArrayList<>(yaml.getComments("schema-version"));
                comments.removeIf(line -> line!=null && line.contains("燃料放入中央空槽后立即存入机器"));
                comments.addAll(defaults.getComments("schema-version"));yaml.setComments("schema-version",comments);
                yaml.set(version,expected);validate(yaml);
            } else {
                var elements=yaml.getConfigurationSection("elements");
                if(elements!=null) for(String key:elements.getKeys(false)) {
                    String base="elements."+key;
                    if(!yaml.getString(base+".action","").equals("fuel")) continue;
                    String title=yaml.getString(base+".name",defaults.getString("elements.F.name"));
                    yaml.set(base+".name",title.replace("燃料储备","燃料仓"));
                    var lore=new ArrayList<>(yaml.getStringList(base+".lore"));
                    lore.removeIf(line -> line.isBlank() || line.matches(".*<(fuel[a-z_]*|output_item|seconds|count)>.*")
                            || line.contains("打开燃料填充界面") || defaults.getStringList("elements.F.lore").contains(line));
                    lore.addAll(defaults.getStringList("elements.F.lore"));yaml.set(base+".lore",lore);
                }
                yaml.set(version,expected);
            }
            ConfigRevisionWriter.write(path,yaml,".fuel-chest-v0.bak");
        }
    }
    public static void validate(YamlConfiguration yaml) {
        var rows=yaml.getStringList("shape");
        if(rows.size()!=4 || rows.stream().anyMatch(row -> row.length()!=9)) invalid("shape must have four rows of nine slots");
        String shape=String.join("",rows);
        for(int i=0;i<shape.length();i++) {
            String base="elements."+shape.charAt(i),action=yaml.getString(base+".action","");
            if(i<27) {if(!action.equals("fuel-slot")) invalid("first 27 slots must use action fuel-slot");}
            else if(i==31) {if(!action.equals("back")) invalid("slot 31 must use action back");}
            else {
                Material material=Material.matchMaterial(yaml.getString(base+".material",""));
                if(!action.equals("info") || material==null || !material.name().endsWith("GLASS_PANE")) invalid("bottom information slots must be glass panes");
                String reference="elements."+shape.charAt(27);
                if(!Objects.equals(yaml.get(base+".material"),yaml.get(reference+".material"))
                        || !Objects.equals(yaml.get(base+".name"),yaml.get(reference+".name"))
                        || !yaml.getStringList(base+".lore").equals(yaml.getStringList(reference+".lore"))) invalid("all eight panes must share material, name and lore");
            }
        }
    }
    private static void invalid(String reason) {throw new IllegalArgumentException("gui/fuel.yml:"+reason);}
}
