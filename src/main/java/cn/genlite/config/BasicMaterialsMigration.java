package cn.genlite.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Replace only the known shipped industrial costs. Custom costs/unknown keys/comments remain intact. */
final class BasicMaterialsMigration {
    private BasicMaterialsMigration() {}
    private static final Map<String,Map<String,Integer>> OLD=Map.of(
            "2",Map.of("minecraft:cobblestone",64,"genlite:gear",4),
            "3",Map.of("minecraft:iron_ingot",32,"genlite:copper_coil",8,"genlite:mechanical_parts",16),
            "4",Map.of("genlite:storage_module",4,"genlite:industrial_chip",4,"genlite:refined_alloy",8),
            "5",Map.of("genlite:refined_alloy",16,"genlite:industrial_chip",8,"genlite:ancient_core",1));
    // Historical v2 destination must not change when current bundled upgrade costs change.
    static final Map<String,Map<String,Integer>> VANILLA=Map.of(
            "2",Map.of("minecraft:cobblestone",64,"minecraft:oak_log",16),
            "3",Map.of("minecraft:iron_ingot",32,"minecraft:oak_log",32,"minecraft:cobblestone",128),
            "4",Map.of("minecraft:iron_ingot",96,"minecraft:chest",4,"minecraft:cobblestone",256),
            "5",Map.of("minecraft:iron_ingot",192,"minecraft:chest",8,"minecraft:cobblestone",512));
    static void apply(Path path,ConfigDocument doc,String id) throws Exception {
        var yaml=doc.yaml();int version=yaml.getInt("materials-version",1);
        if(version==2) return;
        if(version!=1) throw doc.invalid("materials-version");
        boolean original=OLD.entrySet().stream().allMatch(e -> {
            var section=yaml.getConfigurationSection("upgrades."+e.getKey());
            return section!=null && section.getValues(false).equals(e.getValue());
        });
        if(original) {
            yaml.set("upgrades",null);
            VANILLA.forEach((level,costs) -> costs.forEach((item,count) -> yaml.set("upgrades."+level+"."+item,count)));
        }
        if(id.equals("iron_generator") && "minecraft:raw_iron".equals(yaml.getString("production.output"))) yaml.set("production.output","minecraft:iron_ingot");
        yaml.set("materials-version",2);
        MachineDefinition.rules(doc,id);MachineDefinition.display(doc);
        Path backup=path.resolveSibling(path.getFileName()+".materials-v1.bak");
        if(!Files.exists(backup)) Files.copy(path,backup);
        Path temp=path.resolveSibling(path.getFileName()+".migrating");
        Files.writeString(temp,QuotedYamlText.format(yaml.saveToString()),StandardCharsets.UTF_8);
        Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
}
