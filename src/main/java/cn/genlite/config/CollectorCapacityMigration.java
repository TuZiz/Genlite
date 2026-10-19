package cn.genlite.config;

import java.nio.file.Path;

/** IO executor only. Changes configuration, never edits the live machine save. */
final class CollectorCapacityMigration {
    private CollectorCapacityMigration() {}
    static void apply(Path path,ConfigDocument doc,String id) throws Exception {
        if(!id.equals("resource_collector")) return;
        int version=doc.yaml().getInt("storage-version",1);
        if(version==2) return;
        if(version!=1) throw doc.invalid("storage-version");
        for(int level=1;level<=5;level++) doc.yaml().set("levels."+level+".slots",27);
        doc.yaml().setComments("levels",doc.yaml().getComments("levels").stream().map(line -> line==null?null:line.replace("仓储空间必须是 9 的倍数，一级固定 27 槽，最多 54 槽。","仓储固定 27 个普通物品格，每格最多 64 个。")).toList());
        doc.yaml().set("storage-version",2);ConfigRevisionWriter.write(path,doc.yaml(),".storage-v1.bak");
    }
}
