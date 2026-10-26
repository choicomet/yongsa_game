"""Contact sheets from real game captures only; never processes production artwork."""
from pathlib import Path
from PIL import Image, ImageDraw
import json
ROOT=Path(__file__).resolve().parents[2]
RAW=ROOT.parent.parent/'outputs/full-map-repair'
OUT=ROOT/'design/full-map-repair/review'
OUT.mkdir(parents=True,exist_ok=True)
groups={
    'rooms':sorted((RAW/'after').glob('room-*.png')),
    'open':sorted(p for p in (RAW/'after').glob('*.png')
                  if p.name.startswith(('entry-','exit-')) and 'floor-' not in p.name and not p.stem.endswith('-floor')),
    'closed':sorted((RAW/'closed').glob('*.png')),
}
for name, paths in groups.items():
    for start in range(0,len(paths),6):
        board=Image.new('RGB',(1440,600),'#11151c')
        draw=ImageDraw.Draw(board)
        for i,p in enumerate(paths[start:start+6]):
            x,y=i%3*480,i//3*300
            draw.text((x+8,y+8),p.stem,fill='white')
            with Image.open(p) as im:
                im=im.convert('RGB');im.thumbnail((480,270));board.paste(im,(x+(480-im.width)//2,y+28))
        board.save(OUT/f'{name}-{start//6+1:02}.jpg',quality=90)
inventory=json.loads((RAW/'after/inventory.json').read_text(encoding='utf-8'))
(OUT/'capture-summary.json').write_text(json.dumps(dict(captures=len(inventory),
    groups={k:len(v) for k,v in groups.items()},
    blockedProbes=[r['name'] for r in inventory if not r['footFree']]),indent=2)+'\n',encoding='utf-8')
print({k:len(v) for k,v in groups.items()})
