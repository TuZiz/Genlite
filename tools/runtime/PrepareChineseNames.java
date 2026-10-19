package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.List;

/** Offline configuration-only migration; never reads machine/player storage or starts a server. */
public final class PrepareChineseNames {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]),catalog=root.resolve("物品名称.yml");
        if(!Files.exists(catalog)) try(var stream=PrepareChineseNames.class.getResourceAsStream("/物品名称.yml")) {
            if(stream==null) throw new IllegalStateException("Missing bundled 物品名称.yml");
            Files.copy(stream,catalog);
        }
        var names=ItemNames.load(catalog);
        MachineConfigurations.read(root.resolve("genlite"));
        for(String name:List.of("generator","collector","warehouse")) {
            Path file=root.resolve("gui/"+name+".yml");
            var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(file.toFile());
            ChineseItemMigration.menu(file,yaml,names);
        }
        System.out.println("Chinese names and filenames prepared; no machine/player data accessed.");
    }
}
