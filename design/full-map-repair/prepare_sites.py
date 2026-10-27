"""Persist export coordinates needed to reproduce the authored geometry."""
from pathlib import Path
import json
root=Path(__file__).resolve().parents[2]
out=root.parent.parent/'outputs/full-map-repair'
sites=json.loads((out/'sources/sites.json').read_text(encoding='utf-8'))
approaches={s['id']:s for s in json.loads((out/'approach-sources/sites.json').read_text(encoding='utf-8'))}
for s in sites:
    if s['id'] in approaches:
        s['rect']=approaches[s['id']]['rect']
        s['direction']=[0,1]
    s.pop('floorSamples',None)
(root/'design/full-map-repair/sites.json').write_text(json.dumps(sites,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
path=root/'assets/data/natural-room-scenes.json'
data=json.loads(path.read_text(encoding='utf-8'))
camp=next(s for s in data['scenes'] if s['id']=='central-camp')
for p in camp['portals']:
    if p['id'] in ['LEFT','RIGHT']:p['facing']='UP'
path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
