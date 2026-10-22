"""结构、配置路径、配方闭包、打包内容的最小验证。"""
from pathlib import Path
import json
import yaml
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'craftengine/genlite'
MATERIAL_PACK=ROOT/'craftengine/genlite-materials'
PACKS=(PACK,MATERIAL_PACK)
ASSETS=PACK/'resourcepack/assets/genlite'
items={};blocks={};recipes={}
item_names=yaml.safe_load((ROOT/'src/main/resources/物品名称.yml').read_text(encoding='utf-8'))['items']
machine_files={'cobblestone_generator':'原石生成器','timber_generator':'木材生成器','iron_generator':'铁生成器','resource_collector':'普通收集器'}
def item_id(value): return item_names.get(value,value)
for path in [p for pack in PACKS for p in pack.rglob('*.yml')]+list((ROOT/'src/main/resources').rglob('*.yml')):
    text=path.read_text(encoding='utf-8'); assert '\t' not in text,path
    for token in yaml.scan(text):
        assert not isinstance(token,(yaml.tokens.FlowMappingStartToken,yaml.tokens.FlowSequenceStartToken)),path
    d=yaml.safe_load(text)
    def check_quotes(node):
        if isinstance(node,yaml.MappingNode):
            for _,value in node.value:check_quotes(value)
        elif isinstance(node,yaml.SequenceNode):
            for value in node.value:check_quotes(value)
        elif node.tag=='tag:yaml.org,2002:str' and node.value.startswith('<'):
            assert node.style=="'",(path,node.value)
    check_quotes(yaml.compose(text))
    if any(path.is_relative_to(pack) for pack in PACKS):
        for key,target in (('items',items),('blocks',blocks),('recipes',recipes)):
            for k,v in d.get(key,{}).items():assert k not in target;target[k]=v
    elif path.name!='plugin.yml':assert d['schema-version']==(4 if path.as_posix().endswith(('lang/zh_cn.yml','gui/generator.yml')) else 3 if path.as_posix().endswith('gui/collector.yml') else 2 if path.name in ('warehouse.yml','fuel.yml') else 1)
    if path.parent.name=='gui' and path.is_relative_to(ROOT/'src/main/resources'):
        assert 1<=len(d['shape'])<=6
        for row in d['shape']:
            assert len(row)==9
            assert all(c==' ' or c in d['elements'] for c in row)
        for entry in d['elements'].values():
            assert entry.get('action')!='claim'
            if 'item' in entry:assert item_id(entry['item']) in items
        if path.name in ('collector.yml','warehouse.yml'):
            assert {e.get('action') for e in d['elements'].values()}=={'stock','close'}
        if path.name=='collector.yml':
            assert d['shape']==['OOOOOOOOO','OOOOOOOOO','OOOOOOOOO','    X    ']
            assert 'lore' not in d['elements']['O']
        if path.name=='generator.yml':
            assert d['shape'][-1][4]=='X' and d['elements']['X']['action']=='close'
            assert sum(e.get('action')=='mode' for e in d['elements'].values())==1
assert len(items)==18 and len(blocks)==7 and len(recipes)==6
assert 'genlite:basic_smelter' not in recipes
active=set(yaml.safe_load((PACK/'configuration/categories.yml').read_text(encoding='utf-8'))['categories']['genlite:content']['list'])
assert active=={'genlite:cobblestone_generator','genlite:timber_generator','genlite:iron_generator','genlite:resource_collector','genlite:binding_tool','genlite:basic_upgrade_unit'}
materials=yaml.safe_load((MATERIAL_PACK/'configuration/categories.yml').read_text(encoding='utf-8'))['categories']['genlite:materials']['list']
assert len(materials)==11 and len(set(materials))==11
assert set(materials)==set(yaml.safe_load((MATERIAL_PACK/'configuration/materials.yml').read_text(encoding='utf-8'))['items'])
assert not active.intersection(materials)
for mid in ('cobblestone_generator','timber_generator','iron_generator'):
    item=items['genlite:'+mid]
    assert item['material']=='paper',('Client prediction requires a non-block item material',mid)
    assert item['behavior']=={'type':'block_item','block':'genlite:'+mid}
    assert item['model']=='genlite:item/'+mid
    machine=yaml.safe_load((ROOT/'src/main/resources/genlite'/f'{machine_files[mid]}.yml').read_text(encoding='utf-8'))
    assert machine['upgrade-item-version']==1
    assert all({item_id(item) for item in costs}=={'genlite:basic_upgrade_unit'} for costs in machine['upgrades'].values())
assert all(ingredient.startswith('minecraft:') for recipe in recipes.values() for ingredient in recipe['ingredients'].values())
assert 'genlite:warehouse_core' not in items and 'genlite:greenhouse_controller' not in items
for key,item in items.items():
    assert item['data']['item_name'].startswith('<italic:false><#')
    if 'texture' in item:assert sum((pack/'resourcepack/assets/genlite/textures'/(item['texture'].split(':')[1]+'.png')).is_file() for pack in PACKS)==1
    if 'model' in item:assert sum((pack/'resourcepack/assets/genlite/models'/(item['model'].split(':')[1]+'.json')).is_file() for pack in PACKS)==1
for key,block in blocks.items():
    states=block['states']
    if key in ('genlite:machine_frame','genlite:storage_module'):
        assert items[key]['material']=='paper'
        assert items[key]['behavior']=={'type':'block_item','block':key}
        assert len(states['variants'])==4
        assert block['loot']['pools'][0]['entries']==[{'type':'item','item':key}]
        assert all(a['auto_state']=='non_tintable_leaves' for a in states['appearances'].values())
        assert all((MATERIAL_PACK/'resourcepack/assets/genlite/models'/(a['model']['path'].split(':')[1]+'.json')).is_file() for a in states['appearances'].values())
        continue
    assert len(states['variants'])==12
    if key=='genlite:resource_collector':
        assert len({a['state'] for a in states['appearances'].values()})==12
        assert all('half=bottom,open=false' in a['state'] for a in states['appearances'].values())
    elif key!='genlite:basic_smelter':
        assert all(appearance['auto_state']=='non_tintable_leaves' for appearance in states['appearances'].values())
    assert states['properties']['mode']['default']==0
    for variant,value in states['variants'].items():
        model=states['appearances'][value['appearance']]['model']['path']
        assert (ASSETS/'models'/(model.split(':')[1]+'.json')).is_file()
for path in [p for pack in PACKS for p in (pack/'resourcepack/assets/genlite/models').rglob('*.json')]:
    model=json.loads(path.read_text(encoding='utf-8'))
    if 'parent' in model and not model['parent'].startswith('minecraft:'):
        assert any((pack/'resourcepack/assets/genlite/models'/(model['parent'].split(':')[1]+'.json')).is_file() for pack in PACKS)
    for texture in model.get('textures',{}).values():
        assert texture.startswith('minecraft:block/') or any((pack/'resourcepack/assets/genlite/textures'/(texture.split(':')[1]+'.png')).is_file() for pack in PACKS)
    for part in model.get('elements',[]):assert all(0<=a<b<=16 for a,b in zip(part['from'],part['to']))
for mid in ('cobblestone_generator','timber_generator','iron_generator','resource_collector','basic_smelter'):
    item=items['genlite:'+mid];assert 'texture' not in item
    wrapper=json.loads((ASSETS/'models'/(item['model'].split(':')[1]+'.json')).read_text())
    geometry=json.loads((ASSETS/'models'/(wrapper['parent'].split(':')[1]+'.json')).read_text())
    assert geometry['gui_light']=='side' and len(geometry['elements'])>=5
    for context in ('gui','ground','fixed','firstperson_righthand','firstperson_lefthand','thirdperson_righthand'):
        assert min(geometry['display'][context]['scale'])>0
    if mid in ('cobblestone_generator','timber_generator','iron_generator'):
        assert any(e['from']==[0,0,0] and e['to'][0]==16 and e['to'][2]==16 for e in geometry['elements'])
    if mid=='resource_collector':
        assert all(3<=e['from'][0]<e['to'][0]<=13 and 3<=e['from'][2]<e['to'][2]<=13 and e['to'][1]<=7 for e in geometry['elements'])
        assert not any('hopper' in texture for texture in geometry['textures'].values())
        assert not any(e['from'][0]<=8<=e['to'][0] and e['from'][2]<=8<=e['to'][2] and e['to'][1]>=7 for e in geometry['elements'])
for path in (ASSETS/'textures').rglob('*.png'):
    with Image.open(path) as im:assert im.size in ((16,16),(32,32)) or (path.name in ('generator_background.png','collector_background.png') and im.size==(704,744)) or (path.name=='warehouse_background.png' and im.size==(704,888));im.verify()
for recipe in recipes.values():
    assert set(''.join(recipe['pattern']))-{' '}==set(recipe['ingredients'])
    assert recipe['result']['id'] in items
    assert all(i.startswith('minecraft:') or i in items for i in recipe['ingredients'].values())
jar=ROOT/'target/Genlite-1.0.0.jar'
with zipfile.ZipFile(jar) as z:
    names=z.namelist()
    for source in (ROOT/'src/main/java').rglob('*.java'):
        expected=source.relative_to(ROOT/'src/main/java').with_suffix('.class').as_posix()
        assert expected in names,('Missing project class',expected)
    assert not any(n.startswith(('org/bukkit/','io/papermc/','net/momirealms/','ym/dreamSkyblock/','org/junit/')) for n in names)
    assert 'machines.yml' not in names
    assert not any(n.startswith('machines/') for n in names)
    assert len([n for n in names if n.startswith('genlite/') and n.endswith('.yml')])==4
    assert all(n in names for n in ('genlite/原石生成器.yml','genlite/普通收集器.yml','warehouse.yml','gui/warehouse.yml','gui/generator.yml','gui/collector.yml','gui/fuel.yml','lang/zh_cn.yml','plugin.yml'))
    assert 'gui/storage.yml' not in names
    assert not any(n.endswith(('WarehouseMenus.class','WarehouseSession.class','WarehouseActions.class')) for n in names)
with Image.open(MATERIAL_PACK/'resourcepack/assets/genlite/textures/item/fuel.png') as im:
    assert im.size==(32,32) and im.mode=='RGBA' and im.getchannel('A').getextrema()[0]==0 and im.getchannel('A').getextrema()[1]>=250
result={'status':'passed','items':len(items),'blocks':len(blocks),'active_blocks':6,'legacy_smelter_only':True,'states':68,'models':len(list((ASSETS/'models').rglob('*.json'))),'recipes':len(recipes),'jar_bytes':jar.stat().st_size}
path=ROOT/'target/verification/assets.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result))
