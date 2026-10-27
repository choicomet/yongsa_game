"""Move one doorway anchor onto the new stair tread; preserve the painting's world rectangle."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[2]
mapfile=root/'assets/data/map.json';scenefile=root/'assets/data/natural-room-scenes.json';sitefile=root/'design/full-map-repair/sites.json'
m=json.loads(mapfile.read_text(encoding='utf-8'));sc=json.loads(scenefile.read_text(encoding='utf-8'));sites=json.loads(sitefile.read_text(encoding='utf-8'))
s=next(s for s in sites if s['id']=='entry-T3-T5')
door=[s['rect'][0]+s['rect'][2]*.58,s['rect'][1]+s['rect'][3]*.5]
r=next(r for r in m['rooms'] if r['id']=='T3')
scene=next(t for t in sc['scenes'] if t['id']==r['scene'])
portal=next(p for p in scene['portals'] if p['id']=='stairs')
width=r['width']*32;height=width*scene['pixelHeight']/scene['pixelWidth']
r.setdefault('portalFeet',{})['stairs']=[(door[0]-r['origin'][0]*32)/width,1-(door[1]-r['origin'][1]*32)/height]
s['door']=door
# The geometric route is subsequently fully retraced; seed its new endpoint for map validation.
l=next(l for l in m['links'] if l['a']=='T3.stairs')
p=l['painted'];ps=next(t for t in m['passageScenes'] if t['id']==p['scene'])
path=[[p['origin'][0]+(1-u if p.get('flip') else u)*p['width'],p['origin'][1]+(1-v)*p['width']*ps['pixelHeight']/ps['pixelWidth']] for u,v in ps['path']]
if p.get('reverse'):path.reverse()
path[0]=[v/32 for v in door];p['route']=path
for file,data in [(mapfile,m),(scenefile,sc),(sitefile,sites)]:file.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
