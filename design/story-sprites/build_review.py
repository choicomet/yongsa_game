"""Review only: compose actual render captures and inspect production sprite alpha."""
from pathlib import Path
from PIL import Image, ImageDraw
import json, math

HERE=Path(__file__).resolve().parent
ROOT=HERE.parents[1]
captures=sorted((HERE/'captures').glob('*.png'))
assert len(captures)==22, len(captures)
sheet=Image.new('RGB',(1600,6*246),'#171c26');draw=ImageDraw.Draw(sheet)
for i,path in enumerate(captures):
    im=Image.open(path);im.thumbnail((400,225));x=i%4*400;y=i//4*246
    sheet.paste(im,(x,y+21));draw.text((x+6,y+4),path.stem,fill='white')
sheet.save(HERE/'review-all.jpg',quality=90)
for name in ['boss-atlas','boss-phase1','boss-phase2-left','boss-phase2-right','boss-special',
             'boss-death-start','boss-death-end','hero-sleep','village-trapped','village-free',
             'trapped-baker','free-baker']:
    Image.open(HERE/'captures'/f'{name}.png').convert('RGB').save(HERE/f'{name}.jpg',quality=92)

report=[]
for name,count,cw,ch in [('villager_'+k,3,32,40) for k in ['baker','redhood','child','elder','maid','boy']]+[('crystal_prison',2,44,60),('hero_sleep',1,48,56)]:
    im=Image.open(ROOT/'assets/sprites/story'/f'{name}.png').convert('RGBA')
    assert im.size==(cw*count*4,ch*4)
    boxes=[]
    for i in range(count):
        cell=im.crop((i*cw*4,0,(i+1)*cw*4,ch*4))
        box=cell.getchannel('A').getbbox()
        assert box and box[0]>0 and box[1]>0 and box[2]<cw*4 and box[3]<ch*4,(name,i,box)
        boxes.append(box)
    if name=='crystal_prison':
        # A bright central facet can be opaque; inspect the whole face panel.
        for i in range(2):
            alpha=[im.getpixel((i*176+x,y))[3] for y in range(90,190) for x in range(55,121)]
            assert sum(30<a<220 for a in alpha)/len(alpha)>.7, 'Prison hides its resident'
    report.append(dict(name=name,dimensions=im.size,frames=count,visibleBoxes=boxes))
(HERE/'display-inspection.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('PASS: 22 actual GL captures, 8 display atlases, transparent margins and crystal interiors.')
