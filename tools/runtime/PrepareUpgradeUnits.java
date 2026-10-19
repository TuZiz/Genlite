package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.List;

/** Offline config preparation only; does not load plugins, start the server or read machine/player data. */
public final class PrepareUpgradeUnits {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        for(String id:List.of("cobblestone_generator","timber_generator","iron_generator")) {
            Path file=root.resolve("genlite/"+MachineConfigurations.FILES.get(id));var doc=ConfigDocument.load(file,"genlite/"+MachineConfigurations.FILES.get(id));
            UpgradeUnitMigration.apply(file,doc,id);
            System.out.println(id+": "+doc.yaml().getConfigurationSection("upgrades").getValues(true));
        }
        for(String name:List.of("lang/zh_cn.yml","gui/generator.yml")) {
            Path file=root.resolve(name);var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(file.toFile());
            UpgradeUnitMenuMigration.apply(file,name,yaml,PrepareUpgradeUnits.class.getClassLoader().getResourceAsStream(name));
            System.out.println(name+": upgrade-item-version="+yaml.getInt("upgrade-item-version"));
        }
    }
}
