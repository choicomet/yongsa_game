"""Diagnostic overlays only; never writes production artwork."""
from pathlib import Path
from PIL import Image, ImageDraw
import json, sys
ROOT=Path(__file__).resolve().parents[2]
RAW=ROOT.parent.parent/'outputs/gate-floor-polish'
phase=sys.argv[1]
out=RAW/('floor-'+phase);out.mkdir(parents=True,exist_ok=True)
data=json.loads((RAW/('floors-'+phase+'.json')).read_text())
for row in data:
    im=Image.open(ROOT/'assets'/row['image']).convert('RGB');im.thumbnail((800,800))
    w,h=im.size;draw=ImageDraw.Draw(im)
    for u,v,free,floor,owner in row['samples']:
        if free: color='#50ff91'
        elif floor: color='#ff3355'
        else: continue
        x,y=u*w,v*h;draw.ellipse((x-2,y-2,x+2,y+2),fill=color)
    im.save(out/(row['id']+'.jpg'),quality=92)
for i in range(0,len(data),6):
    board=Image.new('RGB',(1440,1020),'#101820');d=ImageDraw.Draw(board)
    for n,row in enumerate(data[i:i+6]):
        x,y=n%3*480,n//3*510;d.text((x+8,y+8),row['id'],fill='white')
        im=Image.open(out/(row['id']+'.jpg'));im.thumbnail((480,480));board.paste(im,(x,y+28))
    board.save(out/f'sheet-{i//6+1}.jpg',quality=90)
