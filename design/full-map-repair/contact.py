"""QA contact sheets only; no changes to game art."""
from pathlib import Path
from PIL import Image, ImageDraw
import json, sys
src=Path(sys.argv[1]);dst=Path(sys.argv[2]);dst.mkdir(parents=True,exist_ok=True)
sites=json.loads(Path(sys.argv[3]).read_text(encoding='utf-8'))
for start in range(0,len(sites),6):
    board=Image.new('RGB',(1200,860),'#12151c');draw=ImageDraw.Draw(board)
    for i,s in enumerate(sites[start:start+6]):
        f=src/(s['id']+'.png');x=i%3*400;y=i//3*430
        draw.text((x+8,y+8),s['id'],fill='white')
        if f.exists():
            im=Image.open(f).convert('RGB');im.thumbnail((400,580));board.paste(im,(x,y+30))
    board.save(dst/f'page-{start//6+1:02}.jpg',quality=94)
