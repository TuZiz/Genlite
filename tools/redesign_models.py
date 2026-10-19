"""Compact vanilla-style generator pedestals. Writes actual cuboid models, not billboard sprites."""
from pathlib import Path
import json
import yaml
import sys
from quote_text import quote_file

ROOT=Path(__file__).resolve().parents[1]
PACK=ROOT/'craftengine/genlite'
MODELS=PACK/'resourcepack/assets/genlite/models'
DISPLAY={
    'gui':{'rotation':[30,225,0],'translation':[0,1,0],'scale':[0.78,0.78,0.78]},
    'ground':{'rotation':[0,0,0],'translation':[0,3,0],'scale':[0.4,0.4,0.4]},
    'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[0.65,0.65,0.65]},
    'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,1,0],'scale':[0.5,0.5,0.5]},
    'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,1,0],'scale':[0.5,0.5,0.5]},
    'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[0.45,0.45,0.45]},
    'thirdperson_lefthand':{'rotation':[75,225,0],'translation':[0,2.5,0],'scale':[0.45,0.45,0.45]},
    'head':{'rotation':[0,0,0],'translation':[0,14,0],'scale':[1,1,1]}}

def cube(a,b,texture,top=None):
    x,y,z=a;X,Y,Z=b
    uvs={'north':[16-X,16-Y,16-x,16-y],'south':[x,16-Y,X,16-y],
         'east':[16-Z,16-Y,16-z,16-y],'west':[z,16-Y,Z,16-y],
         'up':[x,z,X,Z],'down':[x,16-Z,X,16-z]}
    return {'from':a,'to':b,'faces':{face:{'texture':'#'+(top if top and face in ('up','down') else texture),'uv':uv} for face,uv in uvs.items()}}

textures={
    'stone':'minecraft:block/cobblestone','trim':'minecraft:block/polished_andesite',
    'dark':'minecraft:block/deepslate_tiles','wood':'minecraft:block/oak_planks',
    'log':'minecraft:block/oak_log','rings':'minecraft:block/oak_log_top',
    'leaves':'minecraft:block/moss_block','iron':'minecraft:block/raw_iron_block',
    'smooth':'minecraft:block/smooth_stone','metal':'minecraft:block/iron_block',
    'furnace':'minecraft:block/furnace_side','mouth':'minecraft:block/furnace_front',
    'mouth_lit':'minecraft:block/furnace_front_on','coal':'minecraft:block/coal_block',
    'lamp':'genlite:block/lamp_paused'}

def geometry(name):
    c=cube
    if name=='cobblestone_generator':
        return [c([0,0,0],[16,2,16],'dark'),c([0,2,0],[16,4,16],'trim'),
                c([2,4,2],[14,7,14],'stone'),c([1,7,1],[15,9,15],'trim'),
                c([3,9,4],[8,13,10],'stone'),c([8,9,3],[13,12,8],'stone'),
                c([8,9,9],[12,14,13],'stone'),c([2,5,1.8],[4,6,2],'lamp')]
    if name=='timber_generator':
        return [c([0,0,0],[16,2,16],'log','rings'),c([1,2,1],[15,4,15],'wood'),
                c([3,4,3],[13,7,13],'log','rings'),c([4,7,4],[12,13,12],'log','rings'),
                c([2,6,7],[5,9,11],'log','rings'),c([11,7,2],[14,10,6],'log','rings'),
                c([11,4,11],[15,6,15],'leaves'),c([2,2.5,0.8],[4,3.5,1],'lamp')]
    if name=='iron_generator':
        return [c([0,0,0],[16,2,16],'dark'),c([0,2,0],[16,4,16],'smooth'),
                c([2,4,2],[14,6,14],'metal'),c([1,6,1],[15,8,15],'smooth'),
                c([3,8,3],[8,12,9],'iron'),c([8,8,5],[13,14,11],'iron'),
                c([4,8,10],[9,11,14],'iron'),c([2,4.5,1.8],[4,5.5,2],'lamp')]
    if name=='resource_collector':
        # A low wooden receiving box with a shallow inset mouth, not a hopper/spout.
        return [c([3,0,3],[13,1,13],'dark'),c([4,1,4],[12,5,12],'wood'),
                c([5,5,5],[11,5.3,11],'coal'),
                c([3,5,3],[13,7,5],'trim'),c([3,5,11],[13,7,13],'trim'),
                c([3,5,5],[5,7,11],'trim'),c([11,5,5],[13,7,11],'trim'),
                c([4,1,3.9],[5,5,4.2],'metal'),c([11,1,3.9],[12,5,4.2],'metal'),
                c([7,2,3.75],[9,3,4],'lamp')]
    return [c([1,0,1],[15,2,15],'dark'),c([2,2,2],[14,12,14],'furnace'),
            c([1,12,1],[15,14,15],'smooth'),c([3,14,3],[13,15,13],'coal'),
            c([4,4,1.5],[12,10,2],'mouth'),c([2,10.5,1.7],[4,11.5,2],'lamp')]

names=[] if '--binding-only' in sys.argv else ['resource_collector'] if '--collector-only' in sys.argv else ['cobblestone_generator','timber_generator','iron_generator','resource_collector','basic_smelter']
for name in names:
    for state in ['paused','running','no_fuel']:
        tex=dict(textures);tex['lamp']='genlite:block/lamp_'+state
        if name=='basic_smelter' and state=='running':tex['mouth']=tex['mouth_lit']
        tex['particle']=tex['wood' if name=='timber_generator' else 'iron' if name=='iron_generator' else 'stone']
        model={'ambientocclusion':True,'gui_light':'side','textures':tex,'display':DISPLAY,'elements':geometry(name)}
        (MODELS/'block'/f'{name}_{state}.json').write_text(json.dumps(model,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    # Explicit item model wrapper inherits full cuboid geometry and display transforms.
    item=MODELS/'item'/f'{name}.json';item.parent.mkdir(exist_ok=True)
    item.write_text(json.dumps({'parent':f'genlite:block/{name}_paused'},indent=2)+'\n',encoding='utf-8')
    config=PACK/'configuration/machines'/f'{name}.yml';data=yaml.safe_load(config.read_text(encoding='utf-8'))
    if name!='basic_smelter':
        for appearance in data['blocks']['genlite:'+name]['states']['appearances'].values(): appearance['auto_state']='non_tintable_leaves'
    definition=data['items']['genlite:'+name];definition.pop('texture',None);definition['model']='genlite:item/'+name
    definition['material']={'cobblestone_generator':'cobblestone','timber_generator':'oak_log','iron_generator':'iron_block','resource_collector':'chest','basic_smelter':'furnace'}[name]
    config.write_text(yaml.safe_dump(data,allow_unicode=True,sort_keys=False,default_flow_style=False),encoding='utf-8')
    quote_file(config)

if '--collector-only' in sys.argv:
    from fit_item_models import fit
    fit()
    print('Wrote 3 compact collector states and complete item model: 10 x 7 x 10 pixels')
    sys.exit(0)

# The binding wrench also has a real 2px-thick body; rotating it on the ground will no longer reveal a single plane.
wrench=[cube([7,1,7],[9,9,9],'metal'),cube([4,9,6],[12,11,10],'dark'),
        cube([4,11,6],[6,15,10],'metal'),cube([10,11,6],[12,15,10],'metal'),cube([6,11,6],[10,12,10],'metal')]
# Joined surfaces are internal. Omit fully covered faces rather than rendering coincident quads.
for part in wrench:
    for face,axis,upper in [('west',0,False),('east',0,True),('down',1,False),('up',1,True),('north',2,False),('south',2,True)]:
        plane=part['to' if upper else 'from'][axis];other_axes=[a for a in range(3) if a!=axis]
        if any(other is not part and other['from' if upper else 'to'][axis]==plane and
               all(other['from'][a]<=part['from'][a] and other['to'][a]>=part['to'][a] for a in other_axes) for other in wrench):
            part['faces'].pop(face,None)
(MODELS/'item/binding_tool.json').write_text(json.dumps({'gui_light':'side','textures':textures,'display':DISPLAY,'elements':wrench},indent=2)+'\n',encoding='utf-8')
path=PACK/'configuration/materials.yml';data=yaml.safe_load(path.read_text(encoding='utf-8'))
definition=data['items']['genlite:binding_tool'];definition.pop('texture',None);definition['model']='genlite:item/binding_tool'
definition['material']='iron_pickaxe'
path.write_text(yaml.safe_dump(data,allow_unicode=True,sort_keys=False,default_flow_style=False),encoding='utf-8')
quote_file(path)
print(f'Wrote {len(names)*3} compact block models, {len(names)} complete item models, and 1 volumetric binding tool')
from fit_item_models import fit
fit()
