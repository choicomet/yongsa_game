"""QA route overlays for an image-edit reference; not shipped as game artwork."""
from PIL import Image, ImageDraw
from pathlib import Path
root=Path(__file__).resolve().parents[4]/'outputs/full-map-repair/approach-sources'
routes={
 'entry-CAMP-L1':[(.833,.667),(.833,.43),(.833,.23),(.60,.23),(.38,.28),(.18,.45),(.06,.60),(0,.66)],
 'entry-CAMP-R1':[(.167,.667),(.167,.43),(.167,.23),(.40,.23),(.63,.37),(.83,.61),(1,.87)]
}
for name,line in routes.items():
    im=Image.open(root/(name+'.png')).convert('RGB')
    d=ImageDraw.Draw(im)
    points=[(u*im.width,v*im.height) for u,v in line]
    d.line(points,fill='#19ffd8',width=12,joint='curve')
    for x,y in points:d.ellipse((x-8,y-8,x+8,y+8),fill='#fffad1')
    im.save(root/(name+'-route-guide.png'))
