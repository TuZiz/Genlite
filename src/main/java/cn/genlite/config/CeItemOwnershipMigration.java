package cn.genlite.config;

import java.nio.file.Path;

/** IO executor only; remove retired item presentation, preserving business data and unknown fields. */
final class CeItemOwnershipMigration {
    private CeItemOwnershipMigration() {}
    static void apply(Path path,ConfigDocument doc) throws Exception {
        var yaml=doc.yaml();
        if(yaml.contains("ce-item-version") && (!yaml.isInt("ce-item-version") || yaml.getInt("ce-item-version")!=1))
            throw doc.invalid("ce-item-version");
        boolean legacy=yaml.contains("display.name") || yaml.contains("display.lore") || yaml.contains("display.lore-version");
        if(yaml.getInt("ce-item-version")==1 && !legacy) return;
        yaml.set("display.name",null);yaml.set("display.lore",null);yaml.set("display.lore-version",null);
        var display=yaml.getConfigurationSection("display");
        if(display!=null && display.getKeys(false).isEmpty()) yaml.set("display",null);
        yaml.set("ce-item-version",1);
        ConfigRevisionWriter.write(path,yaml,".ce-items-v0.bak");
    }
}
