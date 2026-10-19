package cn.genlite.config;

import cn.genlite.domain.Rules;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;

/** IO-executor only. Publish all bundled/migrated files together; never overwrite existing configuration. */
public record MachineConfigurations(Rules rules, Map<String,MachineDisplay> displays) {
    public static final Map<String,String> FILES=Map.of(
            "cobblestone_generator","原石生成器.yml",
            "timber_generator","木材生成器.yml",
            "iron_generator","铁生成器.yml",
            "resource_collector","普通收集器.yml");
    public MachineConfigurations { displays=Map.copyOf(displays); }
    public static MachineConfigurations load(Path root,Function<String,InputStream> resources) throws Exception {
        Path directory=root.resolve("genlite");
        if(Files.exists(directory)) return read(directory);
        Path stage=Files.createTempDirectory(root,".genlite-migration-");
        try {
            if(Files.exists(root.resolve("machines"))) {
                LegacyMachineDirectory.copy(root.resolve("machines"),stage);
                MachineConfigurations loaded=read(stage);
                Files.move(stage,directory,StandardCopyOption.ATOMIC_MOVE);
                return loaded;
            }
            ConfigDocument legacy=Files.exists(root.resolve("machines.yml"))?ConfigDocument.load(root.resolve("machines.yml"),"machines.yml"):null;
            for(var e:FILES.entrySet()) {
                String resource="genlite/"+e.getValue();Path path=stage.resolve(e.getValue());Files.createDirectories(path.getParent());
                try(InputStream in=resources.apply(resource)) {
                    if(in==null) throw new IllegalStateException("Missing bundled resource "+resource);
                    Files.copy(in,path);
                }
                if(legacy!=null) LegacyMachines.migrate(legacy,e.getKey(),path);
            }
            MachineConfigurations loaded=read(stage);
            Files.move(stage,directory,StandardCopyOption.ATOMIC_MOVE);
            return loaded;
        } catch(Exception error) {
            throw new IllegalStateException("genlite/: schema-version=1 initialization/migration failed; original machines/ or machines.yml preserved; staging="+stage,error);
        } finally {
            if(Files.exists(stage)) try(var paths=Files.walk(stage)) {
                for(Path path:paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    public static MachineConfigurations read(Path directory) throws Exception {
        ChineseMachineFiles.migrate(directory);
        ItemNames names=ItemNames.load(directory.getParent().resolve("物品名称.yml"));
        Path retired=directory.resolve("basic_smelter.yml");
        if(Files.exists(retired)) Files.move(retired,retired.resolveSibling("basic_smelter.yml.retired.bak"));
        Set<String> found=new HashSet<>();
        try(var paths=Files.walk(directory)) {
            for(Path path:paths.filter(p -> p.toString().endsWith(".yml")).toList()) found.add(directory.relativize(path).toString().replace('\\','/'));
        }
        if(!found.equals(Set.copyOf(FILES.values()))) throw new IllegalArgumentException("genlite/: expected files="+FILES.values()+", actual="+found);
        var types=new HashMap<String,Rules.Type>();var displays=new HashMap<String,MachineDisplay>();
        for(var e:FILES.entrySet()) {
            ConfigDocument doc=ConfigDocument.load(directory.resolve(e.getValue()),"genlite/"+e.getValue());
            if(!names.machine(doc.string("id")).equals(e.getKey())) throw doc.invalid("id");
            CollectorCapacityMigration.apply(directory.resolve(e.getValue()),doc,e.getKey());
            BasicMaterialsMigration.apply(directory.resolve(e.getValue()),doc,e.getKey());
            UpgradeUnitMigration.apply(directory.resolve(e.getValue()),doc,e.getKey());
            HologramLayoutMigration.apply(directory.resolve(e.getValue()),doc);
            ItemLoreMigration.apply(directory.resolve(e.getValue()),doc,e.getKey());
            PresentationMigration.machine(directory.resolve(e.getValue()),doc,e.getKey());
            ChineseItemMigration.machine(directory.resolve(e.getValue()),doc,e.getKey(),names);
            types.put(e.getKey(),MachineDefinition.rules(doc,e.getKey(),names));displays.put(e.getKey(),MachineDefinition.display(doc));
        }
        return new MachineConfigurations(new Rules(types),displays);
    }
}
