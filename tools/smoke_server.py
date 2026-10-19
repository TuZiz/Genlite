"""仅启动隔离测试服，不触碰用户在线世界、配置或 JAR。"""
from pathlib import Path
import shutil
import subprocess
import json
import time
import threading
import queue

ROOT=Path(__file__).resolve().parents[1]
SOURCE=Path('C:/Users/32633/Desktop/leaf')
TARGET=ROOT/'target/verification/server'
TARGET.mkdir(parents=True,exist_ok=True)
for directory in ('cache','versions','libraries'):
    shutil.copytree(SOURCE/directory,TARGET/directory,dirs_exist_ok=True)
(TARGET/'plugins').mkdir(exist_ok=True)
for filename in ('craft-engine-paper-plugin-26.9.2.jar','DreamSkyblock-1.0-SNAPSHOT.jar'):
    shutil.copy2(SOURCE/'plugins'/filename,TARGET/'plugins'/filename)
shutil.copy2(ROOT/'target/Genlite-1.0.0.jar',TARGET/'plugins/Genlite-1.0.0.jar')
ce=TARGET/'plugins/CraftEngine'
shutil.copytree(SOURCE/'plugins/CraftEngine/libs',ce/'libs',dirs_exist_ok=True)
shutil.copytree(SOURCE/'plugins/CraftEngine/resources',ce/'resources',dirs_exist_ok=True)
shutil.copytree(ROOT/'craftengine/genlite',ce/'resources/genlite',dirs_exist_ok=True)
(TARGET/'eula.txt').write_text('eula=true\n',encoding='utf-8')
(TARGET/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25579\nonline-mode=false\nlevel-name=smoke\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\nmax-players=1\nenable-rcon=false\n',encoding='utf-8')
java='C:/Program Files/Zulu/zulu-25/bin/java.exe'
classpath=(ROOT/'target/verification/classpath.txt').read_text(encoding='utf-8').strip()
probe=ROOT/'target/verification/probe';probe.mkdir(exist_ok=True)
subprocess.run(['C:/Program Files/Zulu/zulu-25/bin/javac.exe','-encoding','UTF-8','-cp',str(ROOT/'target/classes')+';'+classpath,'-d',str(probe),str(ROOT/'tools/runtime/GenliteSmoke.java')],check=True)
(probe/'plugin.yml').write_text('name: GenliteSmoke\nversion: 1.0\nmain: GenliteSmoke\napi-version: "26.3"\ndepend:\n  - Genlite\n  - CraftEngine\n  - DreamSkyblock\n',encoding='utf-8')
subprocess.run(['C:/Program Files/Zulu/zulu-25/bin/jar.exe','cf',str(TARGET/'plugins/GenliteSmoke.jar'),'-C',str(probe),'.'],check=True)
command=[java,'-Xms512M','-Xmx1536M','-Dhttp.proxyHost=127.0.0.1','-Dhttp.proxyPort=7897','-Dhttps.proxyHost=127.0.0.1','-Dhttps.proxyPort=7897','-jar',str(SOURCE/'leaf-26.3-4.jar'),'nogui']
process=subprocess.Popen(command,cwd=TARGET,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,encoding='utf-8',errors='replace',creationflags=subprocess.CREATE_NO_WINDOW)
output=queue.Queue()
def reader():
    for line in process.stdout: output.put(line)
threading.Thread(target=reader,daemon=True).start()
log=[];done=False;deadline=time.monotonic()+150;stopping=False
while time.monotonic()<deadline:
    try: line=output.get(timeout=0.2)
    except queue.Empty:
        if process.poll() is not None:break
        continue
    log.append(line)
    if 'GENLITE_SMOKE_PASSED' in line:done=True
    if process.poll() is not None:break
if process.poll() is None:
    process.stdin.write('stop\n');process.stdin.flush()
    try:process.wait(timeout=20)
    except subprocess.TimeoutExpired:process.terminate()
while not output.empty():log.append(output.get_nowait())
path=ROOT/'target/verification/logs/smoke.log';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(''.join(log),encoding='utf-8')
errors=[line.strip() for line in log if ('Genlite' in line or 'genlite' in line or 'GENLITE_SMOKE_FAILED' in line) and ('ERROR' in line or 'SEVERE' in line or 'Exception' in line)]
result={'server_started':done,'exit_code':process.poll(),'genlite_errors':errors,'log':str(path)}
print(json.dumps(result,ensure_ascii=False))
for line in log:
    if any(word in line for word in ('Genlite','genlite','CraftEngine','DreamSkyblock')):print(line.rstrip())
raise SystemExit(0 if done and not errors else 1)
