package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** IO executor only. Preserve the prior revision before atomically publishing a validated YAML document. */
final class ConfigRevisionWriter {
    private ConfigRevisionWriter() {}
    static void write(Path path,YamlConfiguration yaml,String suffix) throws Exception {
        Path backup=path.resolveSibling(path.getFileName()+suffix);
        if(!Files.exists(backup)) Files.copy(path,backup);
        Path temporary=path.resolveSibling(path.getFileName()+".migrating");
        try {
            Files.writeString(temporary,QuotedYamlText.format(yaml.saveToString()),StandardCharsets.UTF_8);
            var check=new YamlConfiguration();check.load(temporary.toFile());
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } catch(Exception error) {throw new java.io.IOException(path+": revision="+suffix+" migration failed; original/backup preserved",error);}
    }
}
