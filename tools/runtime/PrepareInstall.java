import cn.genlite.config.OutputConfigurationMigration;
import cn.genlite.config.ContainerConfigurationMigration;
import cn.genlite.config.MachineConfigurations;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;

/** Offline file preparation only: never loads a plugin, connects to the server or accesses saved machine data. */
class PrepareInstall {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);
        for(String name:List.of("lang/zh_cn.yml","gui/generator.yml","gui/collector.yml","gui/warehouse.yml","warehouse.yml")) {
            var yaml=new YamlConfiguration();yaml.options().parseComments(true);Path file=root.resolve(name);yaml.load(file.toFile());
            if(name.equals("lang/zh_cn.yml")||name.equals("gui/generator.yml")) OutputConfigurationMigration.upgrade(file,name,yaml,PrepareInstall.class.getClassLoader().getResourceAsStream(name));
            ContainerConfigurationMigration.upgrade(file,name,yaml,PrepareInstall.class.getClassLoader().getResourceAsStream(name));
            System.out.println(name+": schema="+yaml.getInt("schema-version"));
        }
        var configs=MachineConfigurations.read(root.resolve("genlite"));
        System.out.println("Machine config files ready: "+configs.rules().types().size()+"; no server started");
    }
}
