package cn.genlite.config;

import cn.genlite.domain.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MachineConfigurationsTest {
    @TempDir Path root;
    private MachineConfigurations load() throws Exception {return MachineConfigurations.load(root,path -> getClass().getClassLoader().getResourceAsStream(path));}
    private Machine machine(String type) {return Machine.create(new Position(UUID.randomUUID(),"test:world",0,64,0),UUID.randomUUID(),UUID.randomUUID(),type,1);}
    @Test void freshInstallCreatesFiveFlatFilesAndUsesIndependentRules() throws Exception {
        load();assertFalse(Files.exists(root.resolve("machines.yml")));
        try(var files=Files.list(root.resolve("genlite"))) {assertEquals(4,files.filter(Files::isRegularFile).count());}
        Path path=root.resolve("genlite/铁生成器.yml");
        var y=new YamlConfiguration();y.load(path.toFile());
        y.set("levels.1.interval-seconds",9);y.set("fuel.units-per-item",9);y.set("upgrades.2.minecraft:oak_log",23);y.save(path.toFile());
        var rules=load().rules();var iron=machine("iron_generator");var cobble=machine("cobblestone_generator");
        assertEquals(9,rules.level(iron).seconds());assertEquals(10,rules.level(cobble).seconds());
        assertEquals(9,rules.fuelUnits(iron));assertEquals(8,rules.fuelUnits(cobble));
        assertEquals(23,rules.upgrade(iron).get("minecraft:oak_log"));assertEquals(2,rules.upgrade(cobble).get("genlite:basic_upgrade_unit"));
    }
    @Test void migrationPreservesLegacyValuesOriginalCommentsAndIsIdempotent() throws Exception {
        String legacy=Files.readString(Path.of("src/test/resources/legacy-machines.yml"))
                .replace("interval-seconds: 10","interval-seconds: 9").replace("base-value: 21","base-value: 27")
                .replace("genlite:gear: 4","genlite:gear: 7")+"\n# 管理员的注释\ncustom-field: keep-me\n";
        Files.writeString(root.resolve("machines.yml"),legacy);
        var result=load();assertEquals(27,result.rules().value(machine("iron_generator")));
        assertEquals(9,result.rules().level(machine("timber_generator")).seconds());
        assertEquals(7,result.rules().upgrade(machine("iron_generator")).get("genlite:gear"));
        assertEquals(legacy,Files.readString(root.resolve("machines.yml")));
        Map<Path,String> before=new HashMap<>();
        try(var paths=Files.walk(root.resolve("genlite"))) {for(Path p:paths.filter(Files::isRegularFile).toList()) before.put(p,Files.readString(p));}
        load();for(var e:before.entrySet()) assertEquals(e.getValue(),Files.readString(e.getKey()));
    }
    @Test void invalidMigrationDoesNotPublishPartOfTheDirectoryOrTouchOriginal() throws Exception {
        String legacy=Files.readString(Path.of("src/test/resources/legacy-machines.yml")).replace("interval-seconds: 10","interval-seconds: 1");
        Files.writeString(root.resolve("machines.yml"),legacy);
        Exception error=assertThrows(Exception.class,this::load);
        assertTrue(error.getCause().getMessage().contains("levels.1.interval-seconds=1"));
        assertFalse(Files.exists(root.resolve("genlite")));assertEquals(legacy,Files.readString(root.resolve("machines.yml")));
        try(var paths=Files.list(root)) {assertEquals(1,paths.count());}
    }
    @Test void invalidNewFileReportsPathAndLeavesUserTextIntact() throws Exception {
        load();Path file=root.resolve("genlite/普通收集器.yml");
        String invalid=Files.readString(file).replaceAll("height: [^\\r\\n]+","height: .nan");Files.writeString(file,invalid);
        var error=assertThrows(IllegalArgumentException.class,this::load);
        assertTrue(error.getMessage().contains("genlite/普通收集器.yml:hologram.height="));
        assertEquals(invalid,Files.readString(file));
    }
    @Test void duplicateUnknownOrMissingDefinitionsAreNotSilentlyIgnored() throws Exception {
        load();Path duplicate=root.resolve("genlite/duplicate.yml");
        Files.copy(root.resolve("genlite/铁生成器.yml"),duplicate);
        assertThrows(IllegalArgumentException.class,this::load);Files.delete(duplicate);
        Files.delete(root.resolve("genlite/铁生成器.yml"));
        assertThrows(IllegalArgumentException.class,this::load);
    }
    @Test void oldClassifiedDirectoryIsFlattenedWithoutChangingCommentsOrUnknownFields() throws Exception {
        var originals=new HashMap<Path,String>();
        for(var entry:MachineConfigurations.FILES.entrySet()) {
            String category=entry.getKey().equals("resource_collector")?"collectors":entry.getKey().equals("basic_smelter")?"processing":"generators";
            Path source=root.resolve("machines/"+category+"/"+entry.getValue());Files.createDirectories(source.getParent());
            String text=Files.readString(Path.of("src/main/resources/genlite").resolve(entry.getValue()))+"\n# 保留的管理员注释\ncustom-field: keep-me\n";
            if(entry.getKey().equals("iron_generator")) text=text.replace("base-value: 21","base-value: 29");
            Files.writeString(source,text);originals.put(source,text);
        }
        var loaded=load();assertEquals(29,loaded.rules().value(machine("iron_generator")));
        for(var entry:originals.entrySet()) {
            assertEquals(entry.getValue(),Files.readString(entry.getKey()));
            assertEquals(entry.getValue(),Files.readString(root.resolve("genlite").resolve(entry.getKey().getFileName())));
        }
        try(var files=Files.list(root.resolve("genlite"))) {assertTrue(files.allMatch(Files::isRegularFile));}
    }
    @Test void existingGenliteDirectoryTakesPrecedenceOverOldSources() throws Exception {
        load();Path file=root.resolve("genlite/铁生成器.yml");
        String text=Files.readString(file).replace("base-value: 21","base-value: 31");Files.writeString(file,text);
        Files.createDirectories(root.resolve("machines/generators"));
        Files.writeString(root.resolve("machines/generators/铁生成器.yml"),"invalid: legacy backup\n");
        Files.writeString(root.resolve("machines.yml"),"invalid: older backup\n");
        assertEquals(31,load().rules().value(machine("iron_generator")));assertEquals(text,Files.readString(file));
    }
}
