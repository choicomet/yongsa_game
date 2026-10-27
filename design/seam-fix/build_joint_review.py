"""Side-by-side QA screenshots; this does not process game art."""
from pathlib import Path
from PIL import Image,ImageDraw
import sys
before,after,out=map(Path,sys.argv[1:4]);out.mkdir(parents=True,exist_ok=True)
for name,shot in [('north','exit-L3-L5'),('side','entry-L1-L2')]:
    board=Image.new('RGB',(1200,552),'#10141e');d=ImageDraw.Draw(board)
    for i,folder in enumerate([before,after]):
        im=Image.open(folder/(shot+'.png')).crop((340,120,940,640))
        board.paste(im,(i*600,32));d.text((i*600+12,10),'BEFORE / PR16 de41d26' if i==0 else 'AFTER / REPAINTED CONNECTION',fill='white')
    board.save(out/(name+'-repaint.jpg'),quality=94)
walk=after.parent/'walk'
files=sorted(walk.glob('*.png'))
board=Image.new('RGB',(1600,246*((len(files)+3)//4)),'#10141e');d=ImageDraw.Draw(board)
for i,f in enumerate(files):
    im=Image.open(f);im.thumbnail((400,225));x=i%4*400;y=i//4*246
    board.paste(im,(x,y+21));d.text((x+6,y+4),f.stem,fill='white')
board.save(out/'joint-walk.jpg',quality=92)
