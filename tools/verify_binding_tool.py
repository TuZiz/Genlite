"""Offline geometry/material and CE interaction-hook evidence for the binding/dismantling update."""
from pathlib import Path
import json,itertools,yaml
from fit_item_models import bounds

root=Path(__file__).resolve().parents[1]
pack=root/'craftengine/genlite'
model=json.loads((pack/'resourcepack/assets/genlite/models/item/binding_tool.json').read_text())
parts=model['elements']
for a,b in itertools.combinations(parts,2):
    overlap=[min(a['to'][i],b['to'][i])-max(a['from'][i],b['from'][i]) for i in range(3)]
    assert not all(n>0 for n in overlap),('Overlapping solid geometry',a,b)
faces=[('west',0,False),('east',0,True),('down',1,False),('up',1,True),('north',2,False),('south',2,True)]
for a,b in itertools.combinations(parts,2):
    for fa,axis,upper in faces:
        if fa not in a['faces']:continue
        for fb,other_axis,other_upper in faces:
            if fb not in b['faces'] or axis!=other_axis:continue
            if a['to' if upper else 'from'][axis]!=b['to' if other_upper else 'from'][axis]:continue
            assert not all(min(a['to'][i],b['to'][i])>max(a['from'][i],b['from'][i]) for i in range(3) if i!=axis),('Coincident rendered faces',fa,fb)
extent=bounds(model,model['display']['gui']['rotation']);transform=model['display']['gui']
for i,(lo,hi) in enumerate(extent[:2]):
    assert lo*transform['scale'][i]+transform['translation'][i]>=-6.1
    assert hi*transform['scale'][i]+transform['translation'][i]<=6.1
materials=yaml.safe_load((pack/'configuration/materials.yml').read_text(encoding='utf-8'))
assert materials['items']['genlite:binding_tool']['material']=='iron_pickaxe'
carriers={'cobblestone_generator':'cobblestone','timber_generator':'oak_log','iron_generator':'iron_block','resource_collector':'chest','basic_smelter':'furnace'}
for name,carrier in carriers.items():
    data=yaml.safe_load((pack/f'configuration/machines/{name}.yml').read_text(encoding='utf-8'))
    assert data['items']['genlite:'+name]['material']==carrier
    assert data['items']['genlite:'+name]['behavior']['type']=='block_item'
    assert 'minecraft:mineable/pickaxe' in data['blocks']['genlite:'+name]['settings']['tags']
before=json.loads((root/'target/verification/tool-dismantle-before.json').read_text())
backup=Path(before['backup'])/'source-ce'
for p in (pack/'resourcepack/assets/genlite/models/block').glob('*.json'):
    assert p.read_bytes()==(backup/'resourcepack/assets/genlite/models/block'/p.name).read_bytes()
bytecode=(root/'target/verification/ce-item-listener.javap.txt').read_text()
hook=bytecode.index('new           #450')
sneak=bytecode.index('Player.isSneaking',hook)
assert hook<sneak
assert 'fireAndCheckCancel' in bytecode[hook:sneak] and 'PlayerInteractEvent.setCancelled' in bytecode[hook:sneak]
report={'status':'passed','binding_material':'iron_pickaxe','parts':len(parts),'overlapping_solids':0,'coincident_rendered_faces':0,'gui_fits':True,'machine_materials':carriers,'placed_meshes_unchanged':True,'ce_26_9_2_event_before_sneak_item_branch':True,'runtime':'not_run'}
(root/'target/verification/tool-dismantle-assets.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
