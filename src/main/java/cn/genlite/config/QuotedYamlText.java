package cn.genlite.config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.*;
import java.io.StringReader;
import java.util.*;

/** Preserve YAML comments and all scalar values while single-quoting player text after migration. */
final class QuotedYamlText {
    private QuotedYamlText() {}
    private record Edit(int start,int end,String text) {}
    static String format(String document) {
        var edits=new ArrayList<Edit>();collect(new Yaml().compose(new StringReader(document)),document,edits,false);
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        var result=new StringBuilder(document);
        for(Edit edit:edits) result.replace(edit.start(),edit.end(),edit.text());
        return result.toString();
    }
    private static void collect(Node node,String document,List<Edit> edits,boolean shape) {
        if(node instanceof MappingNode map) for(var pair:map.getValue()) collect(pair.getValueNode(),document,edits,pair.getKeyNode() instanceof ScalarNode key && key.getValue().equals("shape"));
        else if(node instanceof SequenceNode list) for(Node child:list.getValue()) collect(child,document,edits,shape);
        else if(node instanceof ScalarNode scalar && Tag.STR.equals(scalar.getTag())) {
            String value=scalar.getValue();
            if(value.contains("\n") || !(shape || value.startsWith("<") || value.codePoints().anyMatch(c -> Character.UnicodeScript.of(c)==Character.UnicodeScript.HAN))) return;
            int start=document.offsetByCodePoints(0,node.getStartMark().getIndex()),end=document.offsetByCodePoints(0,node.getEndMark().getIndex());
            edits.add(new Edit(start,end,"'"+value.replace("'","''")+"'"));
        }
    }
}
