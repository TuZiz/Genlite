"""Prepare the agreed two-button menu without starting or reloading the server."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import tempfile
import yaml

ROOT = Path(__file__).resolve().parents[2]
DSB = Path('D:/codex/DreamSkyblock')
SERVER = Path('C:/Users/32633/Desktop/leaf')
BACKUP = ROOT / 'backups/20261019-server-main-menu'


def install(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile(dir=path.parent, delete=False) as stream:
        temp = Path(stream.name)
        stream.write(data)
        stream.flush()
        os.fsync(stream.fileno())
    try:
        os.replace(temp, path)
    finally:
        temp.unlink(missing_ok=True)
    assert path.read_bytes() == data, path


def backup(path, relative):
    target = BACKUP / relative
    if path.exists() and not target.exists():
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(path, target)
        assert target.read_bytes() == path.read_bytes()


def main():
    workspace = SERVER / 'plugins/Invero/workspace'
    source = ROOT / 'integrations/invero/server-main.yml'
    menu = yaml.safe_load(source.read_text(encoding='utf-8-sig'))
    assert menu['rows'] == 3 and len(menu['layout']) == 3
    assert set(key for key, value in menu['items'].items() if 'action' in value) == {'H', 'I'}
    assert menu['bindings']['command'] == ['servermenu', '菜单', 'cd']
    for name in ('dreamskyblock-main.yml', 'dreamskyblock-island.yml'):
        old = workspace / name
        backup(old, 'server/Invero-workspace/' + name)
        old.unlink(missing_ok=True)
    backup(workspace / source.name, 'server/previous-server-main.yml')
    install(workspace / source.name, source.read_bytes())
    assert sorted(p.name for p in workspace.rglob('*.yml')) == ['server-main.yml']

    for name in ('invero.yml', 'arcmenus.yml'):
        path = SERVER / 'plugins/DreamSkyblock' / name
        backup(path, 'server/' + name)
        text = path.read_text(encoding='utf-8-sig')
        text, count = re.subn(r'^enabled: (?:true|false)\s*$', 'enabled: false', text, flags=re.M)
        assert count == 1, path
        text = text.replace('# 两个主界面交给 Invero；成员、设置、邀请与确认等子菜单仍由本插件处理。',
                            '# Invero 只提供服务器主菜单；空岛使用原箱子菜单。')
        text = text.replace('# 两个主界面使用 ArcMenu；不可用时保留原箱子入口。',
                            '# ArcMenu 接管关闭；空岛使用原箱子菜单。')
        assert yaml.safe_load(text)['enabled'] is False
        install(path, text.encode('utf-8'))

    path = SERVER / 'plugins/DreamSkyblock/gui/island.yml'
    backup(path, 'server/island.yml')
    text = path.read_text(encoding='utf-8-sig')
    existing = yaml.safe_load(text)
    if 'holograms' not in existing['components']:
        assert existing['Shape'][3] == '#       #', 'Custom fourth row needs an explicit placement'
        assert not any(component.get('symbol') == 'G' for component in existing['components'].values())
        text, count = re.subn(r'^  - [\"\u0027]#       #[\"\u0027]\s*$', "  - '#   G   #'", text, count=1, flags=re.M)
        assert count == 1
        defaults = (DSB / 'src/main/resources/gui/island.yml').read_text(encoding='utf-8-sig')
        section = defaults[defaults.index('  holograms:'):defaults.index('  close:')]
        text += '\n' + section
    installed = yaml.safe_load(text)
    assert installed['Shape'][3] == '#   G   #'
    assert installed['components']['holograms']['action'] == 'holograms'
    assert installed['components']['teleport']['action'] == 'teleport'
    install(path, text.encode('utf-8'))

    jar = DSB / 'target/DreamSkyblock-1.0-SNAPSHOT.jar'
    target = SERVER / 'plugins/update/DreamSkyblock-1.0-SNAPSHOT.jar'
    backup(target, 'server/update/DreamSkyblock-1.0-SNAPSHOT.jar')
    install(target, jar.read_bytes())
    hashes = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in (source, workspace / source.name, jar, target)}
    report = {'backup': str(BACKUP), 'menus': [source.name], 'jar_sha256': hashes,
              'dreamskyblock_invero_enabled': False, 'dreamskyblock_arc_enabled': False,
              'server_started': False, 'server_reloaded': False,
              'machine_or_player_data_accessed': False}
    install(ROOT / 'target/verification/server-main-deployment.json', json.dumps(report, ensure_ascii=False, indent=2).encode('utf-8'))
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
