"""Projected GUI bounds and independent held-model regression check; no game process required."""
from pathlib import Path
import json,hashlib,yaml
from fit_item_models import MODELS,MACHINES,bounds

root=Path(__file__).resolve().parents[1]
before=json.loads((root/'target/verification/remove-ui-before.json').read_text(encoding='utf-8'))
result=[]
for name in (*MACHINES,'binding_tool'):
    item=json.loads((MODELS/'item'/f'{name}.json').read_text())
    geometry=item if name=='binding_tool' else json.loads((MODELS/'block'/f'{name}_paused.json').read_text())
    transform=item['display']['gui'];extent=bounds(geometry,transform['rotation'])
    projected=[(lo*transform['scale'][i]+transform['translation'][i],hi*transform['scale'][i]+transform['translation'][i]) for i,(lo,hi) in enumerate(extent[:2])]
    assert all(lo>=-6.1 and hi<=6.1 for lo,hi in projected),(name,projected)
    assert all(abs(lo+hi)<.01 for lo,hi in projected),(name,projected)
    if name!='binding_tool':
        assert item['display']['firstperson_righthand']['scale']==[.32]*3
        assert item['display']['thirdperson_righthand']['scale']==[.28]*3
    result.append({'item':name,'gui_bounds':projected,'gui_scale':transform['scale'][0]})
for p in (MODELS/'block').glob('*.json'):assert hashlib.sha256(p.read_bytes()).hexdigest()==before['block_hashes'][p.name]
for name,title in before['source_titles'].items():
    y=yaml.safe_load((root/f'src/main/resources/gui/{name}.yml').read_text(encoding='utf-8'));assert y['title']==title
    actual=yaml.safe_load(Path(f'C:/Users/32633/Desktop/leaf/plugins/Genlite/gui/{name}.yml').read_text(encoding='utf-8'))
    assert '<font:genlite:' not in actual['title'] and not any('\ue100'<=c<='\ue1ff' for c in actual['title'])
    assert all(not e.get('item','').startswith(('genlite:menu_','genlite:gui_')) for e in actual['elements'].values())
report={'status':'passed','items':result,'placed_block_meshes_unchanged':True,'source_titles_unchanged':True,'three_gui_skins_removed':True,'client_runtime':'not_run'}
(root/'target/verification/item-fit.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report))
