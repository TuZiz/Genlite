package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import java.util.List;

/** Offline configuration-only preparation; never accesses machine or player saves. */
public final class PreparePresentation {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        MachineConfigurations.read(root.resolve("genlite"));
        for(String name:List.of("gui/generator.yml","gui/collector.yml","lang/zh_cn.yml")) {
            Path path=root.resolve(name);var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(path.toFile());
            PresentationMigration.menu(path,name,yaml,PreparePresentation.class.getResourceAsStream("/"+name));
        }
        System.out.println("Presentation configuration prepared; no runtime data accessed.");
    }
}
