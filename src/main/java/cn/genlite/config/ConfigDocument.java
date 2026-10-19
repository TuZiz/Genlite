package cn.genlite.config;

import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Path;
import java.util.List;

/** Strict reads report the filename, field and invalid value instead of silently using zero. */
record ConfigDocument(String file, YamlConfiguration yaml) {
    static ConfigDocument load(Path path,String filename) throws Exception {
        var yaml=new YamlConfiguration();yaml.options().parseComments(true);
        try {yaml.load(path.toFile());} catch(Exception e) {throw new IllegalArgumentException(filename+":schema-version=1 YAML read failed; original preserved",e);}
        var doc=new ConfigDocument(filename,yaml);
        if(doc.integer("schema-version",1,1)!=1) throw doc.invalid("schema-version");
        return doc;
    }
    IllegalArgumentException invalid(String key) { return new IllegalArgumentException(file+":"+key+"="+yaml.get(key)+" invalid"); }
    int integer(String key,int min,int max) {
        Object raw=yaml.get(key);
        if(!(raw instanceof Number n) || n.doubleValue()!=n.intValue() || n.intValue()<min || n.intValue()>max) throw invalid(key);
        return n.intValue();
    }
    double decimal(String key,double min,double max) {
        Object raw=yaml.get(key);
        if(!(raw instanceof Number n) || !Double.isFinite(n.doubleValue()) || n.doubleValue()<min || n.doubleValue()>max) throw invalid(key);
        return n.doubleValue();
    }
    String string(String key) {
        if(!(yaml.get(key) instanceof String s) || s.isBlank()) throw invalid(key);
        return s;
    }
    boolean bool(String key) {
        if(!(yaml.get(key) instanceof Boolean b)) throw invalid(key);
        return b;
    }
    List<String> lines(String key) {
        if(!(yaml.get(key) instanceof List<?> list) || list.stream().anyMatch(v -> !(v instanceof String))) throw invalid(key);
        return yaml.getStringList(key);
    }
}
