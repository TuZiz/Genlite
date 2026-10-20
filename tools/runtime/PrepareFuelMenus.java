package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.List;

/** Offline configuration-only migration. Never reads machine or player saves. */
public final class PrepareFuelMenus {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        for(String name:List.of("gui/generator.yml","lang/zh_cn.yml")) {
            Path path=root.resolve(name);var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(path.toFile());
            FuelMenuMigration.apply(path,name,yaml,PrepareFuelMenus.class.getResourceAsStream("/"+name));
        }
        Path path=root.resolve("gui/fuel.yml");
        if(!Files.exists(path)) {Files.createDirectories(path.getParent());try(var stream=PrepareFuelMenus.class.getResourceAsStream("/gui/fuel.yml")) {
            if(stream==null) throw new IllegalStateException("Missing bundled gui/fuel.yml");Files.copy(stream,path);
        }}
        var gui=new YamlConfiguration();gui.load(path.toFile());FuelMenuMigration.validate(gui);
        System.out.println("Fuel menus prepared; no machine/player saves accessed.");
    }
}
