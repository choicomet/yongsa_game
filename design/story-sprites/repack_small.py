"""Fit generated fixed sprite sheets to the game's existing cell sizes.
User approved resizing fixed sprites on2026-10-15. Original RGBA sources retained.
"""
from pathlib import Path
from PIL import Image, ImageDraw
import json, statistics, hashlib
ROOT=Path(__file__).resolve().parents[2]; HERE=Path(__file__).parent
specs=[('villager_'+n,3,32,40,h,28,38,'team/sunwoo') for n,h in
 [('baker',35),('redhood',35),('child',29),('elder',35),('maid',35),('boy',31)]]
specs += [('crystal_prison',2,44,60,52,38,56,'team/sunwoo'),('hero_sleep',1,48,56,52,44,54,'sprites')]
records=[]
for name,count,cw,ch,height,maxw,bottom,directory in specs:
 src=Image.open(HERE/'sources'/f'{name}.png').convert('RGBA')
 frames=[src.crop((round(i*src.width/count),0,round((i+1)*src.width/count),src.height)) for i in range(count)]
 boxes=[f.getchannel('A').point(lambda a:255 if a>8 else 0).getbbox() for f in frames]
 assert all(boxes)
 scale=min(height/statistics.median(b[3]-b[1] for b in boxes[:min(2,count)]),
           maxw/max(b[2]-b[0] for b in boxes))
 out=Image.new('RGBA',(cw*count,ch)); data=[]
 for i,(frame,box) in enumerate(zip(frames,boxes)):
  # Crop transparent margins, then apply SAME scale to all frames of one character.
  art=frame.crop(box); size=(max(1,round(art.width*scale)),max(1,round(art.height*scale)))
  art=art.resize(size,Image.Resampling.LANCZOS)
  assert size[0]<=cw-2 and size[1]<=ch-2,(name,size)
  x=(cw-size[0])//2; y=bottom-size[1]
  assert y>=1 and bottom<ch
  out.alpha_composite(art,(i*cw+x,y))
  data.append(dict(frame=i,sourceBox=box,size=size,position=[x,y]))
 dest=ROOT/'assets'/directory/f'{name}.png';dest.parent.mkdir(exist_ok=True,parents=True);out.save(dest)
 # Keep a 4x display atlas from the original, not an enlargement of the tiny fallback.
 hd=Image.new('RGBA',(cw*count*4,ch*4))
 for i,(frame,box) in enumerate(zip(frames,boxes)):
  art=frame.crop(box);size=(max(1,round(art.width*scale*4)),max(1,round(art.height*scale*4)))
  art=art.resize(size,Image.Resampling.LANCZOS)
  hd.alpha_composite(art,(i*cw*4+(cw*4-size[0])//2,bottom*4-size[1]))
 hd_dest=ROOT/'assets/sprites/story'/f'{name}.png';hd_dest.parent.mkdir(exist_ok=True,parents=True);hd.save(hd_dest)
 for i in range(count):
  cell=out.crop((i*cw,0,(i+1)*cw,ch))
  b=cell.getchannel('A').getbbox()
  assert b and b[0]>0 and b[1]>0 and b[2]<cw and b[3]<ch,(name,i,b)
 records.append(dict(file=dest.relative_to(ROOT).as_posix(),sourceSize=src.size,targetSize=out.size,scale=scale,
                     interpolation='LANCZOS',frames=data,sha256=hashlib.sha256(dest.read_bytes()).hexdigest(),
                     displayFile=hd_dest.relative_to(ROOT).as_posix(),displaySize=hd.size,
                     displaySha256=hashlib.sha256(hd_dest.read_bytes()).hexdigest()))
# Verify prison has real interior alpha instead of an opaque drawn glass effect.
prison=Image.open(ROOT/'assets/team/sunwoo/crystal_prison.png').convert('RGBA')
for x in (22,66):
 assert 30<prison.getpixel((x,32))[3]<220
(HERE/'inspection-small.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
# Diagnostic contact sheet only: native frames magnified4x, and overlay over trapped pose.
preview=Image.new('RGB',(900,850),(46,49,60)); draw=ImageDraw.Draw(preview)
for idx,(name,*_) in enumerate(specs[:6]):
 sheet=Image.open(ROOT/'assets/team/sunwoo'/f'{name}.png').convert('RGBA')
 y=20+idx*135
 draw.text((10,y+15),name,fill='white')
 preview.paste(sheet.resize((384,160),Image.Resampling.NEAREST),(170,y-15),sheet.resize((384,160),Image.Resampling.NEAREST))
 trapped=Image.new('RGBA',(44,60))
 trapped.alpha_composite(sheet.crop((64,0,96,40)),(6,14))
 trapped.alpha_composite(prison.crop((0,0,44,60)))
 enlarged=trapped.resize((132,180),Image.Resampling.NEAREST)
 preview.paste(enlarged,(600,y-30),enlarged)
preview.save(HERE/'preview-npcs.png')
sleep=Image.open(ROOT/'assets/sprites/hero_sleep.png')
sleep.resize((240,280),Image.Resampling.NEAREST).save(HERE/'preview-sleep.png')
print('PASS: six96x40 NPCs,88x60 translucent crystal,48x56 hero; cell margins verified.')
