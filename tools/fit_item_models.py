"""Independent item transforms. Block meshes and placed-machine sizes are never modified."""
from pathlib import Path
import itertools,json,math

ROOT=Path(__file__).resolve().parents[1]
MODELS=ROOT/'craftengine/genlite/resourcepack/assets/genlite/models'
MACHINES=('cobblestone_generator','timber_generator','iron_generator','resource_collector','basic_smelter')

def rotated(point,angles):
    x,y,z=(n-8 for n in point)
    ax,ay,az=map(math.radians,angles)
    x,y=x*math.cos(az)-y*math.sin(az),x*math.sin(az)+y*math.cos(az)
    x,z=x*math.cos(ay)+z*math.sin(ay),-x*math.sin(ay)+z*math.cos(ay)
    y,z=y*math.cos(ax)-z*math.sin(ax),y*math.sin(ax)+z*math.cos(ax)
    return x,y,z

def bounds(model,rotation):
    vertices=[rotated(corner,rotation) for part in model['elements'] for corner in itertools.product(*zip(part['from'],part['to']))]
    return [(min(v[i] for v in vertices),max(v[i] for v in vertices)) for i in range(3)]

def gui(model,rotation,target=12):
    extent=bounds(model,rotation);scale=round(min(.9,target/max(hi-lo for lo,hi in extent[:2])),4)
    return {'rotation':rotation,'translation':[round(-(lo+hi)*scale/2,4) for lo,hi in extent[:2]]+[0],'scale':[scale]*3}

def machine_display(model):
    return {
        'gui':gui(model,[30,225,0]),
        'ground':{'rotation':[0,0,0],'translation':[0,2,0],'scale':[.25]*3},
        'fixed':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.45]*3},
        'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,.6,-.6],'scale':[.32]*3},
        'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,.6,-.6],'scale':[.32]*3},
        'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,1,0],'scale':[.28]*3},
        'thirdperson_lefthand':{'rotation':[75,225,0],'translation':[0,1,0],'scale':[.28]*3},
        'head':{'rotation':[0,0,0],'translation':[0,10,0],'scale':[.65]*3}}

def fit():
    for name in MACHINES:
        geometry=json.loads((MODELS/'block'/f'{name}_paused.json').read_text())
        item={'parent':f'genlite:block/{name}_paused','display':machine_display(geometry)}
        (MODELS/'item'/f'{name}.json').write_text(json.dumps(item,indent=2)+'\n',encoding='utf-8')
    path=MODELS/'item/binding_tool.json';wrench=json.loads(path.read_text());display=machine_display(wrench)
    display['gui']=gui(wrench,[0,0,-35],11.5)
    for hand,sign in [('righthand',1),('lefthand',-1)]:
        display['firstperson_'+hand]={'rotation':[0,-90*sign,25*sign],'translation':[sign,2.5,1],'scale':[.55]*3}
        display['thirdperson_'+hand]={'rotation':[0,-90*sign,55*sign],'translation':[0,2,.5],'scale':[.42]*3}
    wrench['display']=display;path.write_text(json.dumps(wrench,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':
    fit();print('Fitted 5 independent machine items and binding wrench; placed models unchanged.')
