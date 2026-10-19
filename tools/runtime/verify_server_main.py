"""Offline text/configuration evidence, using the installed Adventure libraries."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys
import yaml

ROOT = Path(__file__).resolve().parents[2]
DSB = Path('D:/codex/DreamSkyblock')
SERVER = Path('C:/Users/32633/Desktop/leaf')
JAVA = 'C:/Program Files/Zulu/zulu-25/bin/java.exe'
sys.path.insert(0, 'C:/Users/32633/Desktop/MC MCP/mcp')
from engineering.text_validation import yaml_texts, component_issues
from engineering.component_probe import render_components


def main():
    path = 'src/main/resources/gui/island.yml'
    text = (DSB / path).read_text(encoding='utf-8-sig')
    entries, issues = yaml_texts(path, text)
    assert not issues
    entries = [entry for entry in entries if entry['text'].strip()]
    values = {'owner': 'TestPlayer', 'level': '1', 'border': '48', 'members': '1',
              'limit': '4', 'state': '<#34D399>可用', 'experience': '0',
              'next_threshold': '100', 'remaining_value': '100'}
    payloads = []
    for entry in entries:
        names = re.findall(r'<([a-z_]+)>', entry['text'])
        payloads.append({'text': entry['text'], 'placeholders': {name: values[name] for name in names if name in values}})
    cp = str(DSB / 'target/classes') + ';' + (DSB / 'target/verification/diagnostics/arc-classpath.txt').read_text(encoding='utf-8-sig').strip()
    run = subprocess.run([JAVA, '-Dfile.encoding=UTF-8', '--class-path', cp,
                          str(DSB / 'src/test/probe/DreamMenuTextProbe.java')],
                         input='\n'.join(json.dumps(p, ensure_ascii=False) for p in payloads) + '\n',
                         text=True, encoding='utf-8', capture_output=True, check=True)
    components = [json.loads(line) for line in run.stdout.splitlines() if line.strip()]
    assert len(components) == len(entries)
    exported = []
    for entry, payload, component in zip(entries, payloads, components):
        assert not component_issues(component, rgb=True, no_italic=True, item=entry['item']), entry
        exported.append({'path': path, 'field': entry['field'], 'text_hash': hashlib.sha256(entry['text'].encode()).hexdigest(),
                         'component': component, 'runtime': 'DreamSkyblock MessageService.componentRaw / Java 25',
                         'source': 'plugin_renderer', 'placeholders': list(payload['placeholders'])})
    out = DSB / 'target/verification/neuron/server-main-components.json'
    out.write_text(json.dumps(exported, ensure_ascii=False, indent=2), encoding='utf-8')

    # The checker discovers gui/ paths; use that alias only for rendering this external Invero YAML.
    actual = 'integrations/invero/server-main.yml'
    source = (ROOT / actual).read_text(encoding='utf-8-sig')
    assert yaml.safe_load(source)['bindings']['command'] == ['servermenu', '菜单', 'cd']
    rendered = render_components([{'path': 'gui/server-main.yml', 'content': source}], cp, java=JAVA)
    for component in rendered:
        component['path'] = actual
        assert not component_issues(component['component'], rgb=True, no_italic=True, item=component['field'] != 'title')
    (ROOT / 'target/verification/server-main-components.json').write_text(json.dumps(rendered, ensure_ascii=False, indent=2), encoding='utf-8')
    installed = yaml.safe_load((SERVER / 'plugins/DreamSkyblock/gui/island.yml').read_text(encoding='utf-8-sig'))
    defaults = yaml.safe_load(text)
    assert installed['components']['holograms'] == defaults['components']['holograms']
    assert installed['Shape'][3] == '#   G   #'
    for name in ('invero.yml', 'arcmenus.yml'):
        assert yaml.safe_load((SERVER / 'plugins/DreamSkyblock' / name).read_text(encoding='utf-8-sig'))['enabled'] is False
    assert (SERVER / 'plugins/Invero/workspace/server-main.yml').read_bytes() == (ROOT / actual).read_bytes()
    assert list((SERVER / 'plugins/Invero/workspace').rglob('*.yml')) == [SERVER / 'plugins/Invero/workspace/server-main.yml']
    print(json.dumps({'dreamskyblock_rendered': len(exported), 'invero_rendered': len(rendered),
                      'deployed_configuration': 'passed', 'server_runtime': 'not_run'}, ensure_ascii=False))


if __name__ == '__main__':
    main()
