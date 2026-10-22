package cn.genlite.config;

import java.nio.file.*;

/** Offline execution of the same idempotent migration used at plugin startup. */
public final class MigrateCeItemOwnership {
    public static void main(String[] args) throws Exception {
        Path directory=Path.of(args[0]);
        for(String filename:MachineConfigurations.FILES.values()) {
            Path path=directory.resolve(filename);
            CeItemOwnershipMigration.apply(path,ConfigDocument.load(path,"genlite/"+filename));
            String once=Files.readString(path);
            CeItemOwnershipMigration.apply(path,ConfigDocument.load(path,"genlite/"+filename));
            if(!once.equals(Files.readString(path))) throw new IllegalStateException("Non-idempotent migration: "+path);
            System.out.println("Migrated and checked: "+path);
        }
    }
}
