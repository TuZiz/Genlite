package cn.genlite.config;

import java.nio.file.Path;
import org.bukkit.configuration.file.YamlConfiguration;

/** Offline language preparation only; never reads machine/player data or starts the server. */
public final class PrepareHomeHolograms {
    public static void main(String[] args) throws Exception {
        Path path=Path.of(args[0]).resolve("lang/zh_cn.yml");
        var yaml=new YamlConfiguration();yaml.options().parseComments(true);yaml.load(path.toFile());
        HomeHologramMessages.apply(path,yaml,PrepareHomeHolograms.class.getResourceAsStream("/lang/zh_cn.yml"));
        for(String key:java.util.List.of("enabled","disabled","no-home","failed"))
            if(!yaml.isString("holograms."+key)) throw new IllegalStateException("Missing holograms."+key);
        System.out.println("Home hologram language prepared; no runtime data accessed.");
    }
}
