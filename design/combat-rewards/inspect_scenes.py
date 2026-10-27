"""Inspection sheets only; source artwork is never edited."""
from pathlib import Path
import json
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parents[2]
scenes={s['id']:s for s in json.loads((root/'assets/data/natural-room-scenes.json').read_text(encoding='utf-8'))['scenes']}
rooms=json.loads((root/'assets/data/map.json').read_text(encoding='utf-8'))['rooms']
missing=[]
for r in rooms:
    s=scenes[r['scene']]
    if not (root/'assets'/s['image'].replace('.png','-front.png')).exists():missing.append((r,s))
out=root/'build/foreground-inspection';out.mkdir(parents=True,exist_ok=True)
for start in range(0,len(missing),4):
    sheet=Image.new('RGB',(1600,1000),'#111622');d=ImageDraw.Draw(sheet)
    for i,(r,s) in enumerate(missing[start:start+4]):
        im=Image.open(root/'assets'/s['image']).convert('RGB').resize((800,450))
        q=ImageDraw.Draw(im)
        for a in range(1,10):
            x,y=a*80,a*45;q.line((x,0,x,450),fill='#606060');q.line((0,y,800,y),fill='#606060')
            q.text((x+2,2),f'.{a}',fill='white');q.text((2,y+2),f'.{a}',fill='white')
        for p in s.get('blocked',[]):q.line([(p[k]*800,p[k+1]*450) for k in range(0,len(p),2)]+[(p[0]*800,p[1]*450)],fill='red',width=2)
        x,y=(i%2)*800,(i//2)*500;sheet.paste(im,(x,y+30));d.text((x+8,y+8),f"{r['id']} | {s['id']} | {s['image']}",fill='white')
    sheet.save(out/f'page-{start//4}.jpg',quality=95)
(out/'missing.json').write_text(json.dumps([dict(room=r['id'],scene=s['id'],image=s['image'],blocked=s.get('blocked',[])) for r,s in missing],indent=2),encoding='utf-8')
print([(r['id'],s['id']) for r,s in missing])
