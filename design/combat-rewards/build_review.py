"""Compose QA sheets from real renderer captures; do not edit game artwork."""
from pathlib import Path
from PIL import Image,ImageDraw
root=Path(__file__).resolve().parent; repo=root.parents[1]
capture=repo/'build/rewards-captures'
names=[f'{r}-{s}' for r in ['L6','R6','T6'] for s in ['closed','opening','open','fade']]+[f'eye-{s}' for s in ['closed','slit','half','trigger']]
sheet=Image.new('RGB',(1600,900),'#111622');d=ImageDraw.Draw(sheet)
for i,n in enumerate(names):
    im=Image.open(capture/f'{n}.png').crop((384,168,896,552)).resize((300,225))
    x,y=(i%4)*400,(i//4)*225;sheet.paste(im,(x+85,y));d.text((x+4,y+8),n,fill='white')
sheet.save(root/'reward-review.jpg',quality=94)
capture=repo/'build/foreground-captures'
names=['anvil-behind','anvil-front','pillar-behind','mirrored-stairs','lamp-behind','lamp-front','tree-edge','market-behind']
for start in [0,4]:
    sheet=Image.new('RGB',(1280,4*330),'#111622');d=ImageDraw.Draw(sheet)
    for j,n in enumerate(names[start:start+4]):
        for k,mode in enumerate(['before','after']):
            f=capture/f'{n}-{mode}.png'
            if not f.exists():continue
            im=Image.open(f).crop((320,160,960,464));sheet.paste(im,(k*640,j*330+26));d.text((k*640+8,j*330+7),f'{n} | {mode}',fill='white')
    sheet.save(root/f'foreground-comparison-{start//4}.jpg',quality=94)
names=['CAMP','L8','L7','R2','R7','R3','R4','R5','T1','T2','T4','T5','T7','V']
sheet=Image.new('RGB',(1600,5*250),'#111622');d=ImageDraw.Draw(sheet)
for i,n in enumerate(names):
    f=capture/f'room-{n}.png'
    if not f.exists():continue
    im=Image.open(f).resize((400,225));x,y=(i%4)*400,(i//4)*250;sheet.paste(im,(x,y+25));d.text((x+8,y+7),n,fill='white')
sheet.crop((0,0,1600,4*250)).save(root/'foreground-rooms.jpg',quality=92)
print('Updated reward and foreground review sheets.')
