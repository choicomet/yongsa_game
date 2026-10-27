"""Install the complete audited set, preserving unrelated map data and the earlier two repairs."""
import json
from geometry import ROOT, build
from install_tunnels import REPLACED

build()
path=ROOT/'assets/data/map.json'
data=json.loads(path.read_text(encoding='utf-8'))
joints=[j for j in json.loads((ROOT/'design/full-map-repair/geometry.json').read_text(encoding='utf-8'))['joints'] if j['id'] not in REPLACED]
assert len(joints)==33, f'Incomplete: {len(joints)}/33'
for j in joints:assert (ROOT/'assets'/j['image']).is_file(),j['id']
previous=[j for j in data['joints'] if not j['image'].startswith('scenes/joints-v2/')]
assert len(previous)==2
data['joints']=previous+joints
path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('Installed',len(data['joints']),'joint paintings and geometry')
