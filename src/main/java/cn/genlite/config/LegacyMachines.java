package cn.genlite.config;

import org.bukkit.configuration.ConfigurationSection;
import java.nio.file.Path;

/** One-way schema-1 layout migration. The original file (including unknown fields/comments) stays byte-for-byte intact. */
final class LegacyMachines {
    private LegacyMachines() {}
    static void migrate(ConfigDocument old,String id,Path target) throws Exception {
        ConfigDocument next=ConfigDocument.load(target,"genlite/"+MachineConfigurations.FILES.get(id));
        var source=old.yaml();var dest=next.yaml();boolean collector=id.equals("resource_collector");
        String p="types."+id+".";
        dest.set("base-value",old.integer(p+"base-value",0,1000000));
        if(collector) {
            if(old.integer("collector.slots",27,27)!=27) throw old.invalid("collector.slots");
            for(String key:new String[]{"min-offset","max-offset","max-height-difference"}) copy(old,next,"collector."+key,"collection."+key);
        } else {
            copy(old,next,"fuel","fuel");
            for(String key:new String[]{"output","fuel-per-item"}) copy(old,next,p+key,"production."+key);
            if(source.contains(p+"input")) copy(old,next,p+"input","production.input");
        }
        for(int n=1;n<=5;n++) {
            String level="levels."+n+".";
            copy(old,next,level+"value-multiplier",level+"value-multiplier");
            if(collector) copy(old,next,level+"collector-slots",level+"slots");
            else for(String key:new String[]{"interval-seconds","output-count","fuel-capacity-items","buffer-capacity-items"}) copy(old,next,level+key,level+key);
        }
        copy(old,next,"upgrades","upgrades");
        dest.set("materials-version",1);
        dest.set("item-names-version",null);
        if(!collector) dest.set("upgrade-item-version",null);
        if(id.equals("resource_collector")) dest.set("storage-version",1);
        dest.options().setHeader(java.util.List.of("从 machines.yml 迁移；原文件及其中文注释和未知字段均已保留，后续只读取 genlite/。"));
        java.nio.file.Files.writeString(target,QuotedYamlText.format(dest.saveToString()),java.nio.charset.StandardCharsets.UTF_8);
    }
    private static void copy(ConfigDocument from,ConfigDocument to,String source,String target) {
        Object value=from.yaml().get(source);
        if(value==null) throw from.invalid(source);
        if(value instanceof ConfigurationSection section) {
            to.yaml().set(target,null);
            to.yaml().createSection(target);
            for(String key:section.getKeys(false)) copy(from,to,source+"."+key,target+"."+key);
        } else to.yaml().set(target,value);
    }
}
