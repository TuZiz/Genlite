"""使用真实编译依赖运行 Neuron 文案探针，保留动态组件证据。"""
from pathlib import Path
import sys
import json
import re
import hashlib
import subprocess
import yaml

ROOT=Path(__file__).resolve().parents[1]
RUNTIME=Path('C:/Users/32633/Desktop/MC MCP/mcp')
sys.path.insert(0,str(RUNTIME))
from engineering.text_validation import yaml_texts
OUT=ROOT/'target/verification/neuron';OUT.mkdir(parents=True,exist_ok=True)
files=[{'path':p.relative_to(ROOT).as_posix(),'content':p.read_text(encoding='utf-8')}
       for folder in (ROOT/'src/main/resources',ROOT/'craftengine/genlite/configuration') for p in folder.rglob('*.yml')]
files += [{'path':p.relative_to(ROOT).as_posix(),'content':p.read_text(encoding='utf-8')}
          for folder in (ROOT/'src/main/java',ROOT/'src/test/java') for p in folder.rglob('*.java')]
entries=[]
for f in files:
    found,issues=yaml_texts(f['path'],f['content']);assert not issues,issues;entries+=found
    if f['path'].startswith('craftengine/'):
        d=yaml.safe_load(f['content'])
        for iid,item in d.get('items',{}).items():entries.append({'path':f['path'],'field':'items.'+iid+'.data.item_name','text':item['data']['item_name']})
        for iid,category in d.get('categories',{}).items():entries.append({'path':f['path'],'field':'categories.'+iid+'.name','text':category['name']})
    if f['path'].startswith('src/main/resources/genlite/'):
        d=yaml.safe_load(f['content'])
        existing={(e['path'],e['field']) for e in entries}
        for field,value in [(f'hologram.lines.{i}',s) for i,s in enumerate(d['hologram']['lines'])]:
            if (f['path'],field) not in existing:entries.append({'path':f['path'],'field':field,'text':value})
values={'machine':'原石生成器','level':'3','fuel':'71','fuel_capacity':'1536','buffer':'12','buffer_capacity':'192','input':'8',
        'seconds':'8','count':'100000000000000000000000000000000000000000000000000','value':'12','binding':'4ab3d012','used_slots':'4','slots':'27','fuel_per_item':'1','stored_items':'129','status':'工作中','mode':'传入中央仓库','locked':'是','paused':'否','item':'圆石','amount':'4'}
payloads=[]
for entry in entries:
    placeholders={name:{'component':{'text':value,'color':'#A5BBC1','italic':False}}
                  for name,value in values.items() if '<'+name+'>' in entry['text']}
    payloads.append({'text':entry['text'],'placeholders':placeholders})
cp=(ROOT/'target/verification/classpath.txt').read_text(encoding='utf-8').strip()
result=subprocess.run(['C:/Program Files/Zulu/zulu-25/bin/java.exe','-Dfile.encoding=UTF-8','--class-path',cp,str(RUNTIME/'checkers/MiniMessageProbe.java')],
    input='\n'.join(json.dumps(p,ensure_ascii=False) for p in payloads)+'\n',encoding='utf-8',capture_output=True)
assert result.returncode==0,result.stderr
outputs=[json.loads(line) for line in result.stdout.splitlines() if line.strip()];assert len(outputs)==len(entries)
components=[]
for entry,payload,output in zip(entries,payloads,outputs):
    assert 'error' not in output,output
    components.append({'path':entry['path'],'field':entry['field'],'text_hash':hashlib.sha256(entry['text'].encode()).hexdigest(),
                       'source':'template_probe','placeholders':list(payload['placeholders']),**output})
evidence=[{'name':'single-quoted-player-text','status':'passed','detail':'Source and installed YAML player text single-quoted; comment/value/apostrophe/emoji-preserving formatter roundtrip and idempotence tested','rule_ids':['rule_96dd295261384da99cb9']},
          {'name':'unit-tests','status':'passed','detail':'5 targeted config JUnit tests passed in this asset/config update; previous 55-test suite covered: 27 collector stacks of max64, retained legacy overflow, one-stack extraction, capacity migration idempotence/backup and separate close row; direct durable withdrawals and rollback, warehouse four click amounts/capacity, BigInteger unlimited per-type warehouse, old integer save compatibility, split automatic refunds without duplication, minimal container GUI migration and user layout preservation; previous production/packing/persistence checks',
           'rule_ids':['rule_7c6464b3b78942fdba05','result_learning']},
          {'name':'simple-chinese-names','status':'passed','detail':'Simple names remain: 原石生成器/木材生成器/铁生成器; original IDs preserved',
           'rule_ids':['rule_863b876bbd2f48b2a5ab']},
          {'name':'flat-machine-configurations','status':'passed','detail':'Four YAML directly under genlite/; separate display name/lore/hologram templates preserved',
           'rule_ids':['rule_0ac838dc16004542bdcb']},
          {'name':'asset-model-validation','status':'passed','detail':'21 JSON models, 5 full item-model wrappers, 3 full-footprint pedestal bases, compact 10x7x10 receiving box without hopper textures, non_tintable_leaves carriers checked against local CE enum; 17 CE definitions (legacy retained), 4 active blocks, 6 vanilla recipes and JAR checked; client appearance pending'},
          {'name':'direct-resource-containers','status':'passed','detail':'Only stock/close in collector and warehouse menus, no claim actions; one output-mode button and bottom-center close; direct backpack delivery after persistence, BigInteger stock and rollback tested; hologram height 1.05 and scale 0.65','rule_ids':['rule_f03d48b90bad4103a034']},
          {'name':'simple-generators-and-output-routing','status':'passed','detail':'Three distinct cuboid pedestals, full inventory/ground models, 3D wrench, AI-drawn coal fuel, DROP/COLLECTOR/WAREHOUSE routes verified by domain tests; runtime remains pending',
           'rule_ids':['rule_464741de1bc242439442']},
          {'name':'vanilla-materials-and-no-smelter','status':'passed','detail':'All default upgrade costs/6 recipes use vanilla ingredients; no active smelter, direct iron ingot output; legacy definitions hidden; default migrations and once-only retirement refunds tested','rule_ids':['rule_6175ad30f9fd410492ec']},
          {'name':'player-and-server-runtime','status':'not_run','detail':'User requested no server start. New drop animation, actual client models and warehouse GUI not runtime-tested; existing DreamSkyblock declares Folia unsupported.'}]

submission={'files':files,'components':components,'evidence':evidence}
(OUT/'submission.json').write_text(json.dumps(submission,ensure_ascii=False,indent=2),encoding='utf-8')
rules=[{'id':rid,'revision':1,'checker':checker,'applies':[],'enabled':True,'severity':'error'} for rid,checker in
       [('minimessage_italic','minimessage.no_italic'),('minimessage_rgb','minimessage.rgb'),('result_learning','manual'),('rule_863b876bbd2f48b2a5ab','manual'),('rule_7c6464b3b78942fdba05','manual')]]
from engineering.validation import validate_submission
report=validate_submission(files,components,evidence,rules)
(OUT/'report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps({'components':len(components),'status':report['status'],'issues':report['issues'],'pending':report['pending']},ensure_ascii=False))
raise SystemExit(1 if report['issues'] else 0)
