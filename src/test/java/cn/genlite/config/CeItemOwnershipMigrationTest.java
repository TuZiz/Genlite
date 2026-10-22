package cn.genlite.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class CeItemOwnershipMigrationTest {
    @TempDir Path directory;
    @Test void removesOnlyRetiredTextPreservesBusinessAndUnknownFieldsAndIsIdempotent() throws Exception {
        Path path=directory.resolve("machine.yml");
        String old="# 用户注释\nschema-version: 1\ndisplay:\n  name: '旧名称'\n  lore-version: 3\n  lore:\n    - '旧描述'\n  custom: keep\nfuel:\n  units-per-item: 8\nunknown: keep\n";
        Files.writeString(path,old);var doc=ConfigDocument.load(path,"machine.yml");
        CeItemOwnershipMigration.apply(path,doc);
        assertFalse(doc.yaml().contains("display.name"));assertFalse(doc.yaml().contains("display.lore"));
        assertFalse(doc.yaml().contains("display.lore-version"));assertEquals("keep",doc.yaml().getString("display.custom"));
        assertEquals(8,doc.yaml().getInt("fuel.units-per-item"));assertEquals("keep",doc.yaml().getString("unknown"));
        assertTrue(Files.readString(path).contains("# 用户注释"));
        assertEquals(old,Files.readString(directory.resolve("machine.yml.ce-items-v0.bak")));
        String once=Files.readString(path);CeItemOwnershipMigration.apply(path,doc);assertEquals(once,Files.readString(path));
    }
    @Test void unsupportedMigrationVersionDoesNotOverwriteFile() throws Exception {
        Path path=directory.resolve("bad.yml");String old="ce-item-version: 99\ndisplay:\n  name: 'keep'\n";
        Files.writeString(path,old);
        assertThrows(IllegalArgumentException.class,() -> CeItemOwnershipMigration.apply(path,ConfigDocument.load(path,"bad.yml")));
        assertEquals(old,Files.readString(path));
    }
}
