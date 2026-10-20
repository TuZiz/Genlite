package cn.genlite.config;

import cn.genlite.domain.Rules;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

/** Loaded once on the IO executor; does not save or discard user comments/unknown fields. */
public record PluginFiles(Rules rules, Map<String,MachineDisplay> displays, Path storage, YamlConfiguration language,
                          Map<String,YamlConfiguration> menus, CollectorParticleSettings collectorParticles,ItemNames itemNames) {
    public static PluginFiles load(JavaPlugin plugin) throws Exception {
        Path root=plugin.getDataFolder().toPath();
        Map<String,YamlConfiguration> files=new HashMap<>();
        for (String name : List.of("物品名称.yml","config.yml","warehouse.yml","collector-particles.yml","lang/zh_cn.yml","gui/generator.yml","gui/collector.yml","gui/warehouse.yml","gui/fuel.yml")) {
            Path path=root.resolve(name); Files.createDirectories(path.getParent());
            if (!Files.exists(path)) {
                try (InputStream in=plugin.getResource(name)) {
                    if (in==null) throw new IllegalStateException("Missing bundled resource "+name);
                    Files.copy(in,path);
                }
            }
            YamlConfiguration yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(path.toFile());
            boolean outputConfig=name.equals("lang/zh_cn.yml") || name.equals("gui/generator.yml");
            if(outputConfig) OutputConfigurationMigration.upgrade(path,name,yaml,plugin.getResource(name));
            if(outputConfig || name.equals("warehouse.yml") || name.equals("gui/collector.yml") || name.equals("gui/warehouse.yml")) ContainerConfigurationMigration.upgrade(path,name,yaml,plugin.getResource(name));
            if(outputConfig) UpgradeUnitMenuMigration.apply(path,name,yaml,plugin.getResource(name));
            if(name.equals("lang/zh_cn.yml")) HomeHologramMessages.apply(path,yaml,plugin.getResource(name));
            if(outputConfig || name.equals("gui/collector.yml")) PresentationMigration.menu(path,name,yaml,plugin.getResource(name));
            if(outputConfig) FuelMenuMigration.apply(path,name,yaml,plugin.getResource(name));
            if(name.equals("gui/generator.yml") || name.equals("gui/fuel.yml")) FuelChestMigration.apply(path,name,yaml,plugin.getResource(name));
            int expected=outputConfig?4:name.equals("gui/collector.yml")?3:(name.equals("warehouse.yml") || name.startsWith("gui/"))?2:1;
            if (yaml.getInt("schema-version")!=expected) throw new IllegalArgumentException(name+":schema-version="+yaml.get("schema-version")+" unsupported; original preserved");
            files.put(name,yaml);
        }
        ItemNames itemNames=ItemNames.load(root.resolve("物品名称.yml"));
        for(String name:List.of("gui/generator.yml","gui/collector.yml","gui/warehouse.yml"))
            ChineseItemMigration.menu(root.resolve(name),files.get(name),itemNames);
        MachineConfigurations machines=MachineConfigurations.load(root,plugin::getResource);
        String filename=files.get("config.yml").getString("storage-file");
        if (filename==null || !filename.matches("[a-zA-Z0-9_.-]+\\.json")) throw new IllegalArgumentException("config.yml:storage-file="+filename);
        Map<String,YamlConfiguration> menus=new HashMap<>();
        for (String menu:List.of("generator","collector","warehouse","fuel")) {
            YamlConfiguration gui=files.get("gui/"+menu+".yml");
            List<String> shape=gui.getStringList("shape");
            if (shape.isEmpty() || shape.size()>6 || shape.stream().anyMatch(row -> row.length()!=9)) throw new IllegalArgumentException("gui/"+menu+".yml:shape");
            for (char c:String.join("",shape).toCharArray()) if (c!=' ' && !gui.isConfigurationSection("elements."+c)) throw new IllegalArgumentException("gui/"+menu+".yml:elements."+c);
            if(menu.equals("fuel")) FuelChestMigration.validate(gui);
            menus.put(menu,gui);
        }
        int slots=new ConfigDocument("warehouse.yml",files.get("warehouse.yml")).integer("slots",9,45);
        if(slots%9!=0) throw new IllegalArgumentException("warehouse.yml:slots="+slots+"; expected multiple of 9");
        return new PluginFiles(new Rules(machines.rules().types(),slots),machines.displays(),root.resolve(filename),files.get("lang/zh_cn.yml"),Map.copyOf(menus),CollectorParticleSettings.read(files.get("collector-particles.yml")),itemNames);
    }
}
