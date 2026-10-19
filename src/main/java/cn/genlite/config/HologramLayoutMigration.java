package cn.genlite.config;

import java.nio.file.Path;

final class HologramLayoutMigration {
    private HologramLayoutMigration() {}
    static void apply(Path file,ConfigDocument doc) throws Exception {
        int version=doc.yaml().getInt("hologram.layout-version",1);
        if(version==2) return;
        if(version!=1) throw doc.invalid("hologram.layout-version");
        double height=doc.decimal("hologram.height",1,4);
        doc.yaml().set("hologram.height",Math.min(height,1.05));doc.yaml().set("hologram.scale",0.65);
        doc.yaml().set("hologram.layout-version",2);ConfigRevisionWriter.write(file,doc.yaml(),".hologram-v1.bak");
    }
}
