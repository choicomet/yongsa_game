"""Install the three authored cave paintings and their traced floor/portal coordinates."""
from pathlib import Path
import json, shutil, hashlib
from PIL import Image

ROOT=Path(__file__).resolve().parents[2]
GENERATED=Path.home()/'.codex/generated_images/01a1213b-bf3c-7df2-bfb8-9c217048be23'
OUTPUT=ROOT/'assets/scenes/natural-v1'

def flat(points): return [n for p in points for n in p]
def save(path,data):
    def pretty(v,depth=0):
        pad='  '*depth; inner='  '*(depth+1)
        if isinstance(v,dict):
            return '{}' if not v else '{\n'+',\n'.join(inner+json.dumps(k,ensure_ascii=False)+': '+pretty(x,depth+1) for k,x in v.items())+'\n'+pad+'}'
        if isinstance(v,list):
            if not v or all(not isinstance(x,(dict,list)) for x in v): return json.dumps(v,ensure_ascii=False)
            return '[\n'+',\n'.join(inner+pretty(x,depth+1) for x in v)+'\n'+pad+']'
        return json.dumps(v,ensure_ascii=False)
    path.write_text(pretty(data)+'\n',encoding='utf-8')

mine_upper=[(.035,.39),(.095,.39),(.12,.425),(.19,.405),(.245,.365),(.29,.38),(.36,.41),(.40,.395),(.47,.42),(.55,.43),(.63,.47),(.72,.48),(.82,.495),(.87,.50),(.90,.495),(.91,.475),(.97,.475)]
mine_lower=[(.97,.535),(.94,.56),(.91,.57),(.89,.61),(.84,.645),(.77,.64),(.70,.65),(.66,.64),(.61,.645),(.57,.62),(.52,.63),(.46,.60),(.43,.615),(.39,.59),(.32,.59),(.29,.57),(.23,.565),(.20,.56),(.17,.54),(.13,.54),(.10,.52),(.075,.50),(.035,.47)]
citadel_upper=[(.035,.43),(.10,.43),(.145,.46),(.21,.44),(.28,.445),(.34,.425),(.39,.43),(.44,.425),(.50,.445),(.56,.46),(.63,.475),(.68,.49),(.73,.49),(.78,.515),(.83,.53),(.88,.51),(.91,.47),(.975,.47)]
citadel_lower=[(.975,.555),(.92,.61),(.84,.63),(.75,.65),(.70,.66),(.65,.64),(.58,.65),(.52,.63),(.48,.64),(.44,.62),(.41,.64),(.36,.60),(.32,.61),(.28,.60),(.24,.59),(.21,.58),(.19,.57),(.14,.55),(.10,.52),(.065,.51),(.035,.49)]
tower_floor=[(.45,.02),(.57,.02),(.59,.10),(.63,.17),(.63,.22),(.60,.25),(.59,.29),(.62,.33),(.67,.37),(.68,.43),(.69,.50),(.70,.59),(.70,.67),(.69,.75),(.68,.82),(.65,.89),(.61,.95),(.60,.99),(.43,.99),(.41,.95),(.39,.88),(.34,.82),(.31,.75),(.30,.67),(.32,.59),(.32,.51),(.34,.44),(.36,.38),(.37,.32),(.39,.27),(.38,.22),(.39,.16),(.42,.11)]

variants=[
    dict(theme='LEFT',id='cave-mine',title='광산으로 이어지는 갱도',source='exec-283644d4-02ab-4238-a0ba-2b199aebbfe9.png',width=24,
         floor=mine_upper+mine_lower,spawn=[.82,.53],entry=[.935,.52],entryDir='RIGHT',exit=[.065,.435],exitDir='LEFT',
         masks=[dict(id='near-rock-rim',depth=1,polygon=flat(list(reversed(mine_lower))+[(1,1),(0,1)]))]),
    dict(theme='RIGHT',id='cave-citadel',title='성채로 이어지는 지하 통로',source='exec-f7bb255c-cae9-4420-aa53-908d28762b1d.png',width=24,
         floor=citadel_upper+citadel_lower,spawn=[.18,.50],entry=[.065,.475],entryDir='LEFT',exit=[.94,.52],exitDir='RIGHT',
         masks=[dict(id='near-rock-rim',depth=1,polygon=flat(list(reversed(citadel_lower))+[(1,1),(0,1)]))]),
    dict(theme='TOP',id='cave-tower',title='탑으로 이어지는 수정 동굴',source='exec-934bfff4-4a5f-4482-bddf-776f0c10d330.png',width=20,
         floor=tower_floor,spawn=[.51,.81],entry=[.51,.94],entryDir='DOWN',exit=[.51,.07],exitDir='UP',
         masks=[dict(id='near-left-rock',depth=1,polygon=[0,.55,.29,.55,.29,.70,.33,.80,.38,.88,.41,.96,.43,1,0,1]),
                dict(id='near-right-rock',depth=1,polygon=[.73,.53,1,.53,1,1,.60,1,.63,.95,.68,.88,.72,.73])])]

def install():
    mpath=ROOT/'assets/data/map.json';spath=ROOT/'assets/data/natural-room-scenes.json';opath=ROOT/'assets/data/scene-occlusion.json'
    m=json.loads(mpath.read_text(encoding='utf-8'));s=json.loads(spath.read_text(encoding='utf-8'));o=json.loads(opath.read_text(encoding='utf-8'))
    names={'dark-cave-passage',*(v['id'] for v in variants)}
    s['scenes']=[scene for scene in s['scenes'] if scene['id'] not in names]
    for name in names: o['scenes'].pop(name,None)
    manifest=[]
    for v in variants:
        dest=OUTPUT/(v['id']+'.png')
        if (GENERATED/v['source']).exists():shutil.copyfile(GENERATED/v['source'],dest)
        elif not dest.exists():raise FileNotFoundError(v['source'])
        with Image.open(dest) as im: w,h=im.size
        room='CAVE_'+v['theme']
        r=next(r for r in m['rooms'] if r['id']==room);r.update(scene=v['id'],width=v['width'])
        for link in m['links']:
            if link['a']==room+'.north':link['a']=room+'.exit'
            if link['b']==room+'.south':link['b']=room+'.entry'
        s['scenes'].append(dict(id=v['id'],title=v['title'],image=dest.relative_to(ROOT/'assets').as_posix(),pixelWidth=w,pixelHeight=h,
            suggestedWidthTiles=v['width'],walkable=flat(v['floor']),blocked=[],playerSpawn=v['spawn'],enemySpawns=[],
            portals=[dict(id='entry',kind='cave-mouth',foot=v['entry'],facing=v['entryDir']),dict(id='exit',kind='cave-mouth',foot=v['exit'],facing=v['exitDir'])],facilities=[]))
        o['scenes'][v['id']]=v['masks']
        manifest.append(dict(theme=v['theme'],asset=dest.relative_to(ROOT).as_posix(),source=v['source'],size=[w,h],sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
    for p,d in [(mpath,m),(spath,s),(opath,o)]:save(p,d)
    save(ROOT/'design/cave-directions/generation.json',dict(generator='built-in image_gen',pixelProcessing='Original generated PNGs copied byte-for-byte.',assets=manifest))
    print('Installed three directional cave scenes; generated PNG bytes preserved.')

if __name__=='__main__':install()
