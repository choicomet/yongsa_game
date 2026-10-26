"""Install traced collision data for the two authored paintings; never edits images."""
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[2]
source=root/'assets/data/painted-joints.json'
data=json.loads((root/'assets/data/map.json').read_text(encoding='utf-8-sig'))
joints=json.loads(source.read_text(encoding='utf-8-sig'))['joints'] if source.exists() else data['joints']
for j in joints:
    if j['id']=='mine-north':
        j['walkable']=[.30,0,.38,.15,.41,.25,.43,.35,.42,.43,.40,.48,.42,.55,.43,.60,.41,.65,.34,.70,.23,.80,.03,1,
                       .78,1,.85,.87,.77,.80,.66,.72,.61,.65,.59,.60,.60,.55,.61,.48,.63,.40,.65,.35,.70,.25,.76,.15,.85,0]
        j['blocked']=[ [.29,.64,.33,.62,.367,.644,.362,.675,.325,.687,.287,.665],
                       [.348,.611,.377,.598,.409,.615,.40,.64,.365,.645] ]
    else:
        j['walkable']=[0,.58,.15,.56,.22,.54,.32,.50,.38,.50,.43,.47,.48,.44,.55,.435,.63,.42,.70,.43,.75,.48,.83,.48,.87,.45,1,.36,
                       1,.89,.90,.84,.80,.77,.70,.72,.60,.68,.55,.64,.49,.59,.42,.59,.35,.66,.30,.70,.20,.77,0,.85]
        j['blocked']=[]
data['joints']=joints
encoded=json.dumps(data,ensure_ascii=False,indent=2)
encoded=re.sub(r'\[\s*([-\d.e+\s,]+)\s*\]',lambda m:'['+', '.join(v.strip() for v in m[1].split(','))+']',encoded)
(root/'assets/data/map.json').write_text(encoded+'\n',encoding='utf-8')
