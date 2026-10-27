"""Install separate, bidirectional cave interiors; keep the original camp/dungeon paintings."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPLACED = {f'{end}-CAMP-{room}' for end in ('entry', 'exit') for room in ('L1','R1','T1')}

def save(path, data):
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def install():
    path = ROOT/'assets/data/map.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    data['joints'] = [j for j in data['joints'] if j['id'] not in REPLACED]
    if not any(r['type']=='TUNNEL' for r in data['rooms']):
        for index, (direction, first, theme) in enumerate([('LEFT','L1','LEFT'),('RIGHT','R1','RIGHT'),('UP','T1','TOP')]):
            entry = next(l for l in data['links'] if l['a']==f'CAMP.{direction}')
            destination, gate = entry['b'], entry.get('gate')
            tunnel = 'CAVE_'+theme
            data['rooms'].append(dict(id=tunnel, type='TUNNEL', dungeon=theme,
                cx=0,cy=0,scene='dark-cave-passage',width=20,origin=[20+index*40,8]))
            entry.clear()
            entry.update(a=f'CAMP.{direction}', b=tunnel+'.south', transition=True)
            if gate: entry['gate']=gate
            data['links'].append(dict(a=tunnel+'.north',b=destination,transition=True))
    save(path,data)
    path = ROOT/'assets/data/scene-occlusion.json'
    data=json.loads(path.read_text(encoding='utf-8'))
    data['scenes']['dark-cave-passage']=[
        dict(id='near-left-rim',depth=1,polygon=[0,.55,.28,.55,.33,.68,.32,.76,.35,.83,.38,.90,.42,.96,.44,1,0,1]),
        dict(id='near-right-rim',depth=1,polygon=[.78,.50,1,.50,1,1,.56,1,.60,.94,.63,.88,.69,.79,.72,.66]),
        dict(id='far-dark-roof',depth=.10,polygon=[.42,0,.58,0,.58,.045,.56,.075,.44,.075,.42,.045])]
    camp_masks=data['scenes']['central-camp']
    camp_masks[:]=[m for m in camp_masks if not m['id'].startswith('entrance-')]
    camp_masks.extend([
        dict(id='entrance-blue-arch',depth=.50,polygon=[0,.28,.018,.25,.028,.22,.06,.21,.085,.22,.105,.25,.12,.29,.132,.49,.10,.49,.096,.35,.089,.32,.075,.29,.06,.28,.042,.29,.024,.33,.024,.47,0,.48]),
        dict(id='entrance-red-arch',depth=.63,polygon=[.88,.33,.896,.30,.925,.29,.95,.30,.976,.33,.99,.36,1,.50,1,.63,.982,.63,.978,.42,.964,.39,.945,.37,.927,.37,.911,.40,.903,.45,.907,.57,.89,.56]),
        dict(id='entrance-purple-arch',depth=.23,polygon=[.446,.01,.565,.01,.577,.19,.55,.20,.544,.096,.533,.057,.509,.043,.487,.055,.48,.08,.473,.19,.448,.19])])
    save(path,data)
    path = ROOT/'assets/data/natural-room-scenes.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    camp = next(s for s in data['scenes'] if s['id']=='central-camp')
    for p in camp['portals']:
        if p['id'] in ('LEFT','RIGHT','UP'): p['facing']='UP'
    data['scenes'] = [s for s in data['scenes'] if s['id']!='dark-cave-passage']
    data['scenes'].append(dict(id='dark-cave-passage',title='어두운 동굴 통로',
        image='scenes/natural-v1/dark-cave-passage.png',pixelWidth=1254,pixelHeight=1254,
        suggestedWidthTiles=20,
        walkable=[.435,.02,.565,.02,.59,.12,.60,.22,.65,.32,.64,.43,.65,.53,
                  .68,.65,.68,.76,.64,.85,.60,.93,.56,.99,.44,.99,
                  .40,.93,.36,.85,.34,.75,.34,.65,.34,.55,.35,.44,.36,.34,.39,.22,.41,.12],
        blocked=[],playerSpawn=[.5,.79],enemySpawns=[],
        portals=[dict(id='south',kind='cave-mouth',foot=[.5,.93],facing='DOWN'),
                 dict(id='north',kind='cave-mouth',foot=[.5,.08],facing='UP')],facilities=[]))
    save(path,data)

if __name__=='__main__': install()
