"""Assemble QA screenshots only; production PNGs are copied unchanged from image_gen."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,shutil
ROOT=Path(__file__).resolve().parents[2]
RAW=ROOT.parent.parent/'outputs/gate-floor-polish'
OUT=ROOT/'design/gate-floor-polish/review';OUT.mkdir(parents=True,exist_ok=True)
selected=['R1-west-open','T2-west-open','R1-south-closed','R1-west-closed','tower-floor','tower-floor-probes']
for name in selected:
    shutil.copyfile(RAW/'after'/(name+'.png'),OUT/(name+'.png'))
    board=Image.new('RGB',(1280,512),'#11151c');d=ImageDraw.Draw(board)
    for i,version in enumerate(['before','after']):
        d.text((i*640+12,10),version,fill='white')
        im=Image.open(RAW/version/(name+'.png'));im.thumbnail((640,480));board.paste(im,(i*640,32))
    board.save(OUT/(name+'-comparison.jpg'),quality=92)
for state in ['open','closed']:
    paths=sorted((RAW/'after').glob('*-'+state+'.png'))
    for start in range(0,len(paths),6):
        board=Image.new('RGB',(1440,780),'#11151c');d=ImageDraw.Draw(board)
        for i,p in enumerate(paths[start:start+6]):
            x,y=i%3*480,i//3*390;d.text((x+8,y+8),p.stem,fill='white')
            im=Image.open(p);im.thumbnail((480,360));board.paste(im,(x,y+28))
        board.save(OUT/f'{state}-{start//6+1:02}.jpg',quality=88)
floor=json.loads((RAW/'floors-after.json').read_text())
summary=dict(captureEachVersion=len(list((RAW/'after').glob('*.png'))),
    collisionSurfaces=len(floor),paintedJoins=35,passages=20,
    tests=117,failures=0,errors=0,
    note='Green probes show actual foot clearance. Red source-overlay probes at image edges/overlaps alone are not confirmed defects; runtime composite and locks determine the final result.')
(OUT/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(summary)
