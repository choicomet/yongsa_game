import json,re
from shapely.geometry import Polygon
from shapely.ops import unary_union
ROOT='assets/data/'
c=json.load(open('cands.json'))
skip={'central-camp','citadel-ore-a'}
# manual: tower-ore-a dirt landing west of the floor
s=json.load(open(ROOT+'natural-room-scenes.json'))
t=[x for x in s['scenes'] if x['id']=='tower-ore-a'][0]
def poly(f): return Polygon([(f[i],f[i+1]) for i in range(0,len(f),2)])
add=[0.31,0.45,0.33,0.58,0.27,0.57,0.21,0.53,0.19,0.48,0.21,0.46]
u=unary_union([poly(t['walkable']),poly(add).buffer(0.003)]); u=Polygon(u.exterior).simplify(0.001)
flat=[]
for x,y in list(u.exterior.coords)[:-1]: flat+=[round(x,4),round(y,4)]
c['tower-ore-a']={'kind':'room','walkable':flat}
def fmt(v): 
    s=('%.4f'%v).rstrip('0').rstrip('.'); return s if s not in ('-0',) else '0'
for fn,kinds in (('natural-room-scenes.json',('room',)),('map.json',('joint','passage'))):
    lines=open(ROOT+fn,encoding='utf-8').read().split('\n')
    cur=None; changed=[]
    for i,l in enumerate(lines):
        m=re.match(r'      "id": "([^"]+)"',l)
        if m: cur=m.group(1)
        if '"walkable": [' in l and cur in c and cur not in skip and c[cur]['kind'] in kinds:
            ind=l[:l.index('"walkable"')]; end=',' if l.rstrip().endswith(',') else ''
            lines[i]=ind+'"walkable": ['+', '.join(fmt(v) for v in c[cur]['walkable'])+']'+end
            changed.append(cur)
    open(ROOT+fn,'w',encoding='utf-8').write('\n'.join(lines))
    print(fn,changed)
