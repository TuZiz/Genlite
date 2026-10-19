"""Quote YAML player text without reserializing the document or losing comments."""
from pathlib import Path
import sys
import yaml

def quote_file(path):
    source=path.read_text(encoding='utf-8');node=yaml.compose(source);edits=[]
    def walk(node,shape=False):
        if isinstance(node,yaml.MappingNode):
            for key,value in node.value:walk(value,key.value=='shape')
        elif isinstance(node,yaml.SequenceNode):
            for child in node.value:walk(child,shape)
        elif node.tag=='tag:yaml.org,2002:str' and '\n' not in node.value and (shape or node.value.startswith('<') or any('\u4e00'<=c<='\u9fff' for c in node.value)):
            edits.append((node.start_mark.index,node.end_mark.index,"'"+node.value.replace("'","''")+"'"))
    if node:walk(node)
    result=source
    for a,b,text in sorted(edits,reverse=True):result=result[:a]+text+result[b:]
    assert yaml.safe_load(source)==yaml.safe_load(result),path
    if source!=result:path.write_text(result,encoding='utf-8')

if __name__=='__main__':
    for folder in map(Path,sys.argv[1:]):
        for path in folder.rglob('*.yml'):quote_file(path)
