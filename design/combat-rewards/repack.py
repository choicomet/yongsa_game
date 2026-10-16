"""Pack generated transparent art into the user-approved fixed sprite grids.

Only crop/scale/align the source sprites; preserve generated alpha and source PNGs.
The game uses 4x display sheets and keeps the legacy 1x sheets as fallbacks.
"""
from pathlib import Path
import json
from PIL import Image

root = Path(__file__).resolve().parent
assets = root.parents[1] / 'assets'
report = {}
for name, cols, rows, cell in [('chest_left',3,1,(44,40)),('chest_right',3,1,(44,40)),
                              ('chest_top',3,1,(44,40)),('demon_eye',2,2,(48,32))]:
    src = Image.open(root/'sources'/f'{name}.png').convert('RGBA')
    assert src.getchannel('A').getextrema()[0] == 0, f'{name}: missing transparency'
    frames, bounds = [], []
    for i in range(cols*rows):
        x,y=i%cols,i//cols
        box=(round(x*src.width/cols),round(y*src.height/rows),round((x+1)*src.width/cols),round((y+1)*src.height/rows))
        frame=src.crop(box)
        bbox=frame.getchannel('A').point(lambda v:255 if v>8 else 0).getbbox()
        assert bbox and bbox[0]>0 and bbox[1]>0 and bbox[2]<frame.width and bbox[3]<frame.height, (name,i,bbox)
        frames.append(frame.crop(bbox));bounds.append({'sourceCell':box,'content':bbox})
    w,h=cell[0]*4,cell[1]*4
    # Stable apparent width; the tallest open lid must fit on the same baseline.
    width=min(w-16, min((h-16)*f.width/f.height for f in frames))
    sheet=Image.new('RGBA',(w*len(frames),h))
    for i,f in enumerate(frames):
        size=(round(width),round(f.height*width/f.width))
        resized=f.resize(size,Image.Resampling.LANCZOS)
        px=round((w-size[0])/2)
        py=round((h-size[1])/2) if name=='demon_eye' else h-12-size[1]
        sheet.alpha_composite(resized,(i*w+px,py))
        bounds[i]['destination']=[i*w+px,py,*size]
    display=assets/'sprites/rewards'/f'{name}.png'
    display.parent.mkdir(parents=True,exist_ok=True);sheet.save(display)
    legacy=assets/('sprites' if name=='demon_eye' else 'team/sunwoo')/f'{name}.png'
    sheet.resize((cell[0]*len(frames),cell[1]),Image.Resampling.LANCZOS).save(legacy)
    report[name]={'sourceSize':src.size,'displaySize':sheet.size,'legacySize':[cell[0]*len(frames),cell[1]],'frames':bounds}
(root/'packing.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print('Packed 3 chest sheets and 4 eye frames; 4x display and legacy fallback grids.')
