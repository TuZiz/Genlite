package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** Startup IO executor only; preserve all custom texts and comments. */
final class HomeHologramMessages {
    private HomeHologramMessages() {}
    static void apply(Path path,YamlConfiguration yaml,InputStream bundled) throws Exception {
        try(bundled) {
            int version=yaml.getInt("hologram-settings-version",0);
            if(version==1) return;
            if(version!=0) throw new IllegalArgumentException("lang/zh_cn.yml:hologram-settings-version="+version+" invalid");
            if(bundled==null) throw new IllegalStateException("Missing bundled lang/zh_cn.yml");
            var defaults=new YamlConfiguration();defaults.load(new InputStreamReader(bundled,StandardCharsets.UTF_8));
            for(String suffix:java.util.List.of("enabled","disabled","no-home","failed")) {
                String key="holograms."+suffix;
                if(!yaml.contains(key)) yaml.set(key,defaults.getString(key));
                if(!yaml.isString(key)) throw new IllegalArgumentException("lang/zh_cn.yml:"+key+" must be text");
            }
            yaml.set("hologram-settings-version",1);
            ConfigRevisionWriter.write(path,yaml,".hologram-settings-v0.bak");
        }
    }
}
