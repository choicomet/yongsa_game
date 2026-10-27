"""Put L2's east gate in the narrow throat, not across the adjacent vertical room floor."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[2]
mf=root/'assets/data/map.json';sf=root/'design/full-map-repair/sites.json'
m=json.loads(mf.read_text(encoding='utf-8'));sites=json.loads(sf.read_text(encoding='utf-8'))
scenes=json.loads((root/'assets/data/natural-room-scenes.json').read_text(encoding='utf-8'))
s=next(s for s in sites if s['id']=='exit-L1-L2')
door=[s['rect'][0]+s['rect'][2]*.61,s['rect'][1]+s['rect'][3]*.44]
r=next(r for r in m['rooms'] if r['id']=='L2')
scene=next(t for t in scenes['scenes'] if t['id']==r['scene'])
w=r['width']*32;h=w*scene['pixelHeight']/scene['pixelWidth']
u=(door[0]-r['origin'][0]*32)/w
r.setdefault('portalFeet',{})['west2']=[1-u if r.get('flip') else u,1-(door[1]-r['origin'][1]*32)/h]
s['door']=door
for j in m['joints']:
    if j['id']==s['id']:j['door']=door
next(l for l in m['links'] if l['b']=='L2.west2')['painted']['route'][-1]=[v/32 for v in door]
for p,d in [(mf,m),(sf,sites)]:p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
