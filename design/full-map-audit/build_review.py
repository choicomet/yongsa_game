"""Contact sheets of captured frames only. Does not edit game artwork."""
from pathlib import Path
from PIL import Image,ImageDraw
import json,sys
src=Path(sys.argv[1]);out=Path(__file__).resolve().parent/'review';out.mkdir(parents=True,exist_ok=True)
if '--partial' in sys.argv:
    inventory=[]
    for f in sorted(src.glob('*.png'),key=lambda f:f.stat().st_mtime):
        name=f.stem
        category='floor' if name.endswith('-floor') else 'room' if name.startswith('room-') else 'passage' if name.startswith('link-') else 'endpoint'
        inventory.append(dict(name=name,category=category,footFree=True))
else:
    inventory=json.loads((src/'inventory.json').read_text(encoding='utf-8'))
    assert len(inventory)==140
for category in ['endpoint','floor','room','passage']:
    frames=[q for q in inventory if q['category']==category]
    for page in range((len(frames)+5)//6):
        chunk=frames[page*6:(page+1)*6]
        board=Image.new('RGB',(1280,386*((len(chunk)+1)//2)),'#10141e');d=ImageDraw.Draw(board)
        for i,q in enumerate(chunk):
            im=Image.open(src/(q['name']+'.png'));im.thumbnail((640,360));x=i%2*640;y=i//2*386
            board.paste(im,(x,y+26));d.text((x+8,y+7),q['name']+(' [INVALID FOOT]' if not q['footFree'] else ''),fill='white')
        board.save(out/f'{category}-{page+1:02d}.jpg',quality=92)
if '--partial' not in sys.argv:(out.parent/'inventory.json').write_text(json.dumps(inventory,ensure_ascii=False,indent=2),encoding='utf-8')
print('frames',len(inventory),'invalid feet',[q['name'] for q in inventory if not q['footFree']])
