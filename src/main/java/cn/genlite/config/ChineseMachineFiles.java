package cn.genlite.config;

import java.nio.file.*;
import java.util.*;

/** IO executor only. Check all collisions before renaming; retain byte-identical old-name backups. */
final class ChineseMachineFiles {
    private ChineseMachineFiles() {}
    static void migrate(Path directory) throws Exception {
        for(var e:MachineConfigurations.FILES.entrySet()) {
            Path old=directory.resolve(e.getKey()+".yml"),next=directory.resolve(e.getValue());
            if(Files.exists(old) && Files.exists(next)) throw new IllegalArgumentException(directory+": both "+old.getFileName()+" and "+next.getFileName()+" exist; files preserved");
        }
        for(var e:MachineConfigurations.FILES.entrySet()) {
            Path old=directory.resolve(e.getKey()+".yml"),next=directory.resolve(e.getValue());
            if(!Files.exists(old)) continue;
            Path backup=old.resolveSibling(old.getFileName()+".chinese-name-v0.bak");
            try {
                if(!Files.exists(backup)) Files.copy(old,backup);
                Files.move(old,next,StandardCopyOption.ATOMIC_MOVE);
            } catch(Exception error) {throw new java.io.IOException(old+": filename-version=0 -> 1 failed; backup="+backup,error);}
        }
    }
    static String name(String old) {
        return MachineConfigurations.FILES.getOrDefault(old.replaceFirst("\\.yml$",""),old);
    }
}
