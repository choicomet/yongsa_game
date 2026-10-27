from pathlib import Path
from PIL import Image
import json,hashlib
root=Path('.')
files=sorted(root.glob('assets/sprites/gate_*.png'))+sorted(root.glob('assets/sprites/skill/icon_*.png'))+[root/'assets/sprites/potion.png']
rows=[]
for p in files:
 im=Image.open(p).convert('RGBA');a=im.getchannel('A');hist=a.histogram()
 rows.append(dict(file=p.as_posix(),width=im.width,height=im.height,alphaZero=hist[0],alphaPartial=sum(hist[1:255]),alphaOpaque=hist[255],bbox=a.getbbox(),sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
 assert hist[0]>0 and sum(hist[1:])>0,p
out=root/'design/art-priority-a/asset-check.json';out.write_text(json.dumps(rows,indent=2),encoding='utf-8')
print(json.dumps(rows,indent=2))
