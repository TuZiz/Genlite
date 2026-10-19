from pathlib import Path
import zipfile
import hashlib

root=Path(__file__).resolve().parents[1]
out=root/'dist/Genlite-1.0.0.zip';out.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as archive:
    archive.write(root/'target/Genlite-1.0.0.jar','plugins/Genlite-1.0.0.jar')
    for p in (root/'craftengine/genlite').rglob('*'):
        if p.is_file():archive.write(p,'plugins/CraftEngine/resources/genlite/'+p.relative_to(root/'craftengine/genlite').as_posix())
    archive.write(root/'README.md','README.md')
    archive.write(root/'docs/VALIDATION.md','VALIDATION.md')
    for p in (root/'integrations/invero').glob('*.yml'):
        archive.write(p,'plugins/Invero/workspace/'+p.name)
print(f'{out} ({out.stat().st_size} bytes)')
print(hashlib.sha256(out.read_bytes()).hexdigest())
