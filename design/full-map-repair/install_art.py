"""Copy generated originals without cropping, resizing or painting pixels."""
from pathlib import Path
import json, shutil
from install_tunnels import REPLACED
from PIL import Image
root=Path(__file__).resolve().parents[2]
records=json.loads((root/'design/full-map-repair/generation.json').read_text(encoding='utf-8'))
dst=root/'assets/scenes/joints-v2';dst.mkdir(exist_ok=True,parents=True)
for record in records:
    if record['id'] in REPLACED: continue
    source=Path(record['source']);target=dst/(record['id']+'.png')
    with Image.open(source) as im:
        assert im.width==im.height, (record['id'],im.size)
    if not target.exists() or source.read_bytes()!=target.read_bytes():shutil.copyfile(source,target)
print('Installed original square PNGs:',len(records))
