"""Contact sheets of GL screenshots only; no game image is edited."""
from pathlib import Path
from PIL import Image, ImageDraw
import sys
captures=Path(sys.argv[1]); out=Path(sys.argv[2]);out.mkdir(parents=True,exist_ok=True)
foreground='--foreground' in sys.argv
for prefix in (['foreground'] if foreground else ['entry','exit','link']):
    files=sorted(captures.glob('*.png' if foreground else prefix+'-*.png'))
    assert len(files)==(30 if foreground else 23),(prefix,len(files))
    sheet=Image.new('RGB',(1600,246*((len(files)+3)//4)),'#10141e');draw=ImageDraw.Draw(sheet)
    for i,p in enumerate(files):
        im=Image.open(p);im.thumbnail((400,225));x=i%4*400;y=i//4*246
        sheet.paste(im,(x,y+21));draw.text((x+6,y+4),p.stem,fill='white')
    sheet.save(out/f'{prefix}.jpg',quality=90)
