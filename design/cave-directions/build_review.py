"""Diagnostic contact sheets only. Production image bytes are never modified."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,shutil
ROOT=Path(__file__).resolve().parents[2]
RAW=ROOT.parent.parent/'outputs/cave-directions'
OUT=ROOT/'design/cave-directions/review';OUT.mkdir(parents=True,exist_ok=True)
for p in (RAW/'after').glob('*.png'):shutil.copyfile(p,OUT/p.name)
for name in ['camp-overview','camp-left','camp-right','camp-top']:
    board=Image.new('RGB',(1280,396),'#11151c');d=ImageDraw.Draw(board)
    for i,version in enumerate(['before','after']):
        d.text((i*640+12,10),version,fill='white')
        with Image.open(RAW/version/(name+'.png')) as im:
            im.thumbnail((640,360));board.paste(im,(i*640,32))
    board.save(OUT/(name+'-comparison.jpg'),quality=92)
data=json.loads((ROOT/'assets/data/natural-room-scenes.json').read_text(encoding='utf-8'))
for s in data['scenes']:
    if not s['id'].startswith('cave-'):continue
    with Image.open(ROOT/'assets'/s['image']) as source:
        im=source.convert('RGB');im.thumbnail((960,640))
    d=ImageDraw.Draw(im);w,h=im.size;p=s['walkable']
    points=[(p[i]*w,p[i+1]*h) for i in range(0,len(p),2)]
    d.line(points+[points[0]],fill='#45ff9b',width=2)
    for portal in s['portals']:
        x,y=portal['foot'][0]*w,portal['foot'][1]*h
        d.ellipse((x-5,y-5,x+5,y+5),fill='#ffff70');d.text((x+8,y),portal['id'],fill='white')
    im.save(OUT/(s['id']+'-geometry.jpg'),quality=92)
print('Prepared runtime captures, four before/after comparisons and three geometry guides.')
