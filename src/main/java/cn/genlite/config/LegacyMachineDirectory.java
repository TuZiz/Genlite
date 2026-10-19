package cn.genlite.config;

import java.nio.file.*;
import java.util.HashSet;

/** Flatten the old directory with byte-identical copies; retain the original as a backup. IO executor only. */
final class LegacyMachineDirectory {
    private LegacyMachineDirectory() {}
    static void copy(Path source,Path stage) throws Exception {
        var expected=new HashSet<>(MachineConfigurations.FILES.values());
        var seen=new HashSet<String>();
        try(var paths=Files.walk(source)) {
            for(Path path:paths.filter(p -> p.toString().endsWith(".yml")).toList()) {
                String file=ChineseMachineFiles.name(path.getFileName().toString());
                if(file.equals("basic_smelter.yml")) continue;
                if(!expected.contains(file) || !seen.add(file)) throw new IllegalArgumentException("machines/: unknown or duplicate file="+path);
                Files.copy(path,stage.resolve(file));
            }
        }
        if(!seen.equals(expected)) throw new IllegalArgumentException("machines/: expected files="+expected+", actual="+seen);
    }
}
