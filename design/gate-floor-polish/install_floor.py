"""Refine the bridge's exposed paving, leaving the parapet/cliff outside the walk polygon."""
from pathlib import Path
import json,re
ROOT=Path(__file__).resolve().parents[2]
# Top-left normalized coordinates traced on the source tower-bridge.png.
# The old straight lower edge excluded a broad strip of paving beside the parapet.
FLOOR=[(0,.20),(.13,.28),(.26,.36),(.38,.41),(.47,.36),(.57,.37),(.65,.38),(.76,.31),(.88,.23),(1,.17),
       (1,.32),(.88,.43),(.75,.525),(.64,.595),(.59,.64),(.54,.67),(.46,.68),(.41,.665),(.36,.62),(.28,.585),(.14,.47),(0,.35)]
path=ROOT/'assets/data/map.json';data=json.loads(path.read_text(encoding='utf-8'))
next(s for s in data['passageScenes'] if s['id']=='tower-bridge')['walkable']=[v for p in FLOOR for v in p]
encoded=json.dumps(data,ensure_ascii=False,indent=2)
encoded=re.sub(r'\[\s*([-\d.e+\s,]+)\s*\]',lambda m:'['+', '.join(v.strip() for v in m[1].split(','))+']',encoded)
path.write_text(encoded+'\n',encoding='utf-8')
print('Updated both placements of tower-bridge; room layout and route anchors unchanged.')
