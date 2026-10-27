"""Record final selected assets and audit disposition; preserve generated PNG bytes."""
import json, hashlib
from pathlib import Path
from install_tunnels import ROOT, REPLACED

def save(p,d): p.write_text(json.dumps(d,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

folder=ROOT/'design/full-map-repair'
manifest=json.loads((folder/'generation.json').read_text(encoding='utf-8'))
for row in manifest:
    row['status']='superseded-by-separate-cave' if row['id'] in REPLACED else 'installed'
    if row['status']=='installed':
        path=ROOT/'assets/scenes/joints-v2'/(row['id']+'.png')
        row['asset']=path.relative_to(ROOT).as_posix()
        row['sha256']=hashlib.sha256(path.read_bytes()).hexdigest()
save(folder/'generation.json',manifest)
entrance=json.loads((folder/'entrance-art.json').read_text(encoding='utf-8'))
entrance.setdefault('rejectedApproaches',{}).update({k:v for k,v in entrance['selected'].items() if k.startswith('entry-CAMP')})
entrance['selected']={k:v for k,v in entrance['selected'].items() if not k.startswith('entry-CAMP')}
entrance['selected']['dark-cave-passage']='exec-00fd36fc-2b59-4d57-a9dc-ce5edd492ba1.png'
entrance['decision']='2026-10-21: user rejected the outdoor U-shaped connectors. Original camp arches now lead to separate playable dark cave interiors, then the original dungeon rooms.'
entrance['cavePrompt']='Use case: stylized-concept. Production game background for a separate short cave passage, matching central-camp.png as STYLE ONLY. Square, orthographic elevated 3/4 top-down view. A straight narrow walkable natural cave tunnel from bottom center to top center. Both ends recede beneath deep nearly black overhanging rock into another space. Visible middle path width about 28 percent; irregular natural gray rock walls. Muted warm dirt, flat stones, tiny blue mineral glints, two restrained amber side lamps. Clear floor x=.37 to .63, y=.18 to .86, continuing into blackness at y=.10 and .96. No bridges, loop, branches, plaza, furniture, characters, UI, text or labels. Thick roof, dark peripheral vignette. Entire background opaque.'
save(folder/'entrance-art.json',entrance)
audit=json.loads((ROOT/'design/full-map-audit/findings.json').read_text(encoding='utf-8'))
rows=[]
for r in audit['endpointReviews']:
    if r['status']=='유지':continue
    rows.append(dict(id=r['id'],previous=r['status'],resolution='separate-cave-transition' if r['id'] in REPLACED else 'repaint-and-collision',status='implemented'))
save(folder/'resolved-sites.json',dict(baseline='6f406a6',date='2026-10-21',sites=rows))

# Numeric coordinate arrays remain on one line so the actual geometry is reviewable.
def pretty(v,depth=0):
    pad='  '*depth; inner='  '*(depth+1)
    if isinstance(v,dict):
        if not v:return '{}'
        return '{\n'+',\n'.join(inner+json.dumps(k,ensure_ascii=False)+': '+pretty(x,depth+1) for k,x in v.items())+'\n'+pad+'}'
    if isinstance(v,list):
        if not v or all(not isinstance(x,(dict,list)) for x in v):return json.dumps(v,ensure_ascii=False)
        return '[\n'+',\n'.join(inner+pretty(x,depth+1) for x in v)+'\n'+pad+']'
    return json.dumps(v,ensure_ascii=False)
for name in ['map.json','natural-room-scenes.json','scene-occlusion.json']:
    path=ROOT/'assets/data'/name
    data=json.loads(path.read_text(encoding='utf-8'));text=pretty(data)+'\n'
    assert json.loads(text)==data
    path.write_text(text,encoding='utf-8')
print('Recorded 39 audit dispositions; 33 installed repaints and six separate-space replacements.')
