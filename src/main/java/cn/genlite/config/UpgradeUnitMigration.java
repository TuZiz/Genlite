package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/** IO executor only: migrate shipped generator costs once, keeping custom costs and collector settings. */
final class UpgradeUnitMigration {
    private UpgradeUnitMigration() {}
    static void apply(Path file,ConfigDocument doc,String id) throws Exception {
        if(id.equals("resource_collector")) return;
        int version=doc.yaml().getInt("upgrade-item-version",0);
        if(version==1) return;
        if(version!=0) throw doc.invalid("upgrade-item-version");
        boolean shipped=BasicMaterialsMigration.VANILLA.entrySet().stream().allMatch(e -> {
            var costs=doc.yaml().getConfigurationSection("upgrades."+e.getKey());
            return costs!=null && costs.getValues(false).equals(e.getValue());
        });
        if(shipped) {
            var defaults=new YamlConfiguration();
            try(var input=UpgradeUnitMigration.class.getResourceAsStream("/genlite/"+id+".yml")) {
                if(input==null) throw new IllegalStateException("Missing bundled generator: genlite/"+id+".yml");
                defaults.load(new InputStreamReader(input,StandardCharsets.UTF_8));
            }
            doc.yaml().set("upgrades",null);
            var upgrades=defaults.getConfigurationSection("upgrades");
            for(String level:java.util.Objects.requireNonNull(upgrades).getKeys(false)) {
                var costs=upgrades.getConfigurationSection(level);
                for(String item:java.util.Objects.requireNonNull(costs).getKeys(false)) doc.yaml().set("upgrades."+level+"."+item,costs.get(item));
            }
        }
        doc.yaml().set("upgrade-item-version",1);
        MachineDefinition.rules(doc,id);MachineDefinition.display(doc);
        ConfigRevisionWriter.write(file,doc.yaml(),".upgrade-items-v0.bak");
    }
}
