"""Pack generated poses into padded cells; preserve source alpha and one scale per sheet."""
from pathlib import Path
import json
import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
OUT = HERE.parents[1] / 'assets/sprites/hero-motion'
OUT.mkdir(parents=True, exist_ok=True)
CELL = (256, 288)
BASELINE = 272
ROLL = [
 [(44,47,274,298),(338,112,548,301),(624,115,847,301),(908,99,1161,301),(1205,99,1443,301),(1524,15,1730,301)],
 [(42,354,273,604),(361,402,564,605),(612,407,870,592),(904,402,1163,591),(1219,394,1453,604),(1524,307,1736,603)],
 [(38,619,270,871),(337,673,575,871),(633,650,854,861),(936,650,1152,866),(1214,650,1461,871),(1521,604,1730,879)]
]

def spans(on):
    e = np.diff(np.r_[False, on, False].astype(int))
    return list(zip(np.flatnonzero(e == 1), np.flatnonzero(e == -1)))

report = {}
for action in ['walk', 'roll']:
    src = Image.open(HERE / 'sources' / f'hero-{action}.png').convert('RGBA')
    a = np.array(src)[:, :, 3] > 32
    boxes = ROLL
    if action == 'walk':
        boxes = []
        for y0, y1 in [(0,300),(300,583),(583,887)]:
            row = []
            for x0,x1 in spans(a[y0:y1].sum(axis=0)>1):
                ys,xs = np.where(a[y0:y1,x0:x1])
                row.append((int(x0+xs.min()),int(y0+ys.min()),int(x0+xs.max()+1),int(y0+ys.max()+1)))
            assert len(row) == 6
            boxes.append(row)
    # Uniform scale preserves the real crouch/somersault height; never enlarge individual poses.
    reference = [row[2 if action == 'walk' else 5] for row in boxes]
    scale = 58*4 / float(np.median([b[3]-b[1] for b in reference]))
    atlas = Image.new('RGBA',(CELL[0]*6,CELL[1]*3))
    frames = []
    for row, bb in enumerate(boxes):
        for col, box in enumerate(bb):
            crop = src.crop(box)
            anchor = crop.width / 2
            if action == 'walk':
                head = np.array(crop)[:int(crop.height*.33),:,3]>32
                xs = np.where(head)[1]
                anchor = (int(xs.min())+int(xs.max())+1)/2
            dest = crop.resize((round(crop.width*scale),round(crop.height*scale)),Image.Resampling.LANCZOS)
            dx = round(CELL[0]/2-anchor*scale)
            dy = BASELINE-dest.height
            assert dx>=4 and dx+dest.width<=CELL[0]-4 and dy>=4
            atlas.alpha_composite(dest,(col*CELL[0]+dx,row*CELL[1]+dy))
            frames.append({'row':row,'column':col,'source':box,'dest':[dx,dy,dest.width,dest.height]})
    atlas.save(OUT/f'{action}.png')
    report[action]={'sourceSize':src.size,'scale':scale,'cell':CELL,'baseline':BASELINE,'frames':frames}
(HERE/'packing.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print('Packed 36 transparent poses into 1536x864 sheets; common foot baseline, no per-pose stretching.')
