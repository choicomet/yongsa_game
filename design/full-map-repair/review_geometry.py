"""QA overlays only. Source game PNGs remain untouched."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
from geometry import ROOT, OUT, build

build()
dest=OUT/'geometry-review';dest.mkdir(exist_ok=True,parents=True)
joints=json.loads((ROOT/'design/full-map-repair/geometry.json').read_text(encoding='utf-8'))['joints']
for j in joints:
    source=ROOT/'assets'/j['image']
    if not source.exists():continue
    im=Image.open(source).convert('RGB');im.thumbnail((800,800))
    w,h=im.size;draw=ImageDraw.Draw(im,'RGBA')
    def points(p):return [(p[i]*w,p[i+1]*h) for i in range(0,len(p),2)]
    draw.polygon(points(j['walkable']),fill=(30,255,80,45),outline=(80,255,100,255),width=2)
    for p in j['blocked']:draw.polygon(points(p),fill=(255,0,40,120))
    for p in j['foregrounds']:draw.line(points(p['polygon'])+[points(p['polygon'])[0]],fill=(50,200,255,255),width=2)
    dx=(j['door'][0]-j['rect'][0])/j['rect'][2]*w
    dy=(1-(j['door'][1]-j['rect'][1])/j['rect'][3])*h
    draw.ellipse((dx-4,dy-4,dx+4,dy+4),fill=(255,255,255,255))
    im.save(dest/(j['id']+'.png'))
print('Geometry QA overlays:',len(joints))
