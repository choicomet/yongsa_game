"""Place intact paintings by their measured floor anchors; never rewrite the PNGs.

Run from the repository root. Coordinates below were traced on the source art,
not generated from a desired rectangular corridor. QA overlays are separate files.
"""
import json, math, re
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'design/painted-map-links'
MAP = ROOT / 'assets/data/map.json'

# All points use normalized image coordinates (top-left origin).
TRACES = {
 'citadel-gallery': (
  [( .70,.025),(.70,.13),(.69,.23),(.64,.32),(.54,.40),(.43,.47),(.34,.55),(.28,.65),(.23,.77),(.23,.86),(.25,.975)],
  [(.656,0),(.762,0),(.762,.15),(.79,.23),(.77,.31),(.70,.38),(.60,.44),(.49,.49),(.40,.56),(.36,.62),(.30,.70),(.276,.80),(.28,.90),(.306,1),(.194,1),(.184,.91),(.175,.82),(.16,.77),(.19,.68),(.24,.61),(.245,.54),(.30,.47),(.40,.41),(.52,.35),(.61,.28),(.635,.20),(.656,.13)]),
 'citadel-crossing': (
  [(.025,.20),(.13,.27),(.25,.35),(.38,.43),(.51,.49),(.62,.51),(.72,.57),(.83,.66),(.975,.77)],
  [(0,.14),(.12,.20),(.24,.27),(.37,.36),(.49,.43),(.60,.43),(.70,.49),(.82,.58),(1,.70),(1,.84),(.88,.80),(.76,.71),(.64,.62),(.53,.60),(.40,.54),(.26,.45),(.14,.36),(0,.25)]),
 'mine-traverse': (
  [(.025,.24),(.12,.32),(.23,.40),(.36,.47),(.50,.55),(.63,.63),(.71,.62),(.81,.56),(.90,.49),(.975,.46)],
  [(0,.19),(.10,.25),(.22,.33),(.35,.40),(.45,.43),(.46,.31),(.49,.28),(.52,.33),(.56,.38),(.60,.40),(.56,.45),(.60,.49),(.70,.55),(.80,.48),(.90,.43),(1,.40),(1,.53),(.91,.57),(.82,.66),(.70,.76),(.61,.74),(.48,.65),(.35,.58),(.24,.50),(.12,.40),(0,.28)]),
 'mine-descent': (
  [(.49,.02),(.57,.09),(.54,.17),(.42,.24),(.40,.29),(.49,.35),(.63,.40),(.66,.44),(.59,.49),(.47,.54),(.39,.61),(.39,.68),(.49,.74),(.59,.80),(.58,.87),(.55,.98)],
  [(.37,0),(.60,0),(.66,.08),(.65,.15),(.56,.22),(.52,.27),(.57,.31),(.71,.36),(.78,.41),(.76,.47),(.67,.53),(.55,.58),(.50,.63),(.50,.68),(.59,.72),(.70,.77),(.70,.84),(.65,.91),(.65,1),(.44,1),(.44,.90),(.47,.83),(.45,.80),(.34,.75),(.29,.70),(.28,.63),(.30,.57),(.38,.51),(.50,.46),(.55,.42),(.48,.39),(.35,.35),(.29,.30),(.30,.24),(.40,.17),(.46,.11),(.42,.06)]),
 'mine-rail-bend': (
  [(.13,.025),(.16,.14),(.22,.24),(.32,.31),(.45,.38),(.56,.46),(.65,.56),(.74,.70),(.83,.84),(.916,.975)],
  [(.075,0),(.19,0),(.23,.12),(.28,.20),(.39,.26),(.51,.32),(.62,.40),(.72,.49),(.81,.64),(.89,.79),(1,.96),(1,1),(.85,1),(.78,.89),(.69,.77),(.60,.64),(.51,.55),(.43,.48),(.31,.42),(.22,.36),(.13,.26),(.09,.15)]),
 'tower-bridge': (
  [(.025,.27),(.15,.37),(.28,.46),(.40,.50),(.51,.49),(.64,.46),(.77,.39),(.89,.31),(.975,.25)],
  [(0,.20),(.13,.28),(.26,.36),(.38,.41),(.47,.36),(.57,.37),(.65,.38),(.76,.31),(.88,.23),(1,.17),(1,.32),(.88,.40),(.75,.48),(.64,.55),(.54,.62),(.41,.63),(.28,.57),(.14,.47),(0,.35)]),
 'tower-ascent': (
  [(.30,.02),(.33,.08),(.43,.13),(.51,.18),(.55,.25),(.58,.30),(.56,.34),(.48,.38),(.40,.42),(.39,.49),(.43,.54),(.53,.59),(.61,.64),(.65,.70),(.69,.78),(.73,.86),(.77,.98)],
  [(.225,0),(.375,0),(.40,.06),(.48,.10),(.55,.14),(.59,.20),(.65,.25),(.68,.30),(.65,.35),(.58,.39),(.49,.42),(.48,.47),(.49,.51),(.58,.56),(.68,.60),(.74,.66),(.76,.73),(.79,.80),(.83,.87),(.90,1),(.61,1),(.61,.90),(.60,.84),(.57,.77),(.54,.72),(.51,.67),(.45,.63),(.36,.59),(.30,.54),(.28,.48),(.29,.42),(.32,.38),(.39,.35),(.46,.32),(.48,.28),(.46,.23),(.42,.19),(.35,.15),(.28,.11),(.24,.07)]),
 'tower-stair-bend': (
  [(.025,.19),(.15,.23),(.28,.30),(.40,.36),(.49,.47),(.56,.59),(.64,.73),(.70,.85),(.728,.92)],
  [(0,.14),(.13,.17),(.26,.23),(.39,.29),(.48,.37),(.54,.46),(.61,.57),(.69,.70),(.75,.80),(.82,1),(.68,1),(.62,.86),(.57,.78),(.50,.66),(.45,.56),(.39,.48),(.34,.43),(.24,.38),(.12,.29),(0,.24)]),
 'camp-trail': (
  [(.49,.025),(.49,.11),(.55,.18),(.64,.25),(.59,.31),(.48,.37),(.39,.44),(.39,.50),(.49,.55),(.59,.62),(.58,.69),(.49,.75),(.46,.81),(.50,.89),(.51,.975)],
  [(.415,0),(.575,0),(.58,.10),(.64,.16),(.75,.23),(.75,.28),(.67,.34),(.56,.41),(.50,.46),(.50,.49),(.59,.53),(.71,.59),(.72,.66),(.65,.73),(.59,.78),(.57,.82),(.60,.89),(.63,1),(.39,1),(.38,.91),(.33,.84),(.34,.77),(.43,.71),(.47,.65),(.44,.61),(.32,.56),(.27,.51),(.28,.44),(.37,.37),(.49,.30),(.54,.26),(.53,.23),(.45,.18),(.40,.12)])
}

# scene, width in tiles, reverse path, mirror X. Uniform scale only.
PLACEMENTS = [
 ('mine-traverse',38,True,False),('mine-traverse',38,True,False),
 ('mine-descent',22,False,False),('mine-descent',22,False,True),
 ('mine-rail-bend',36,True,False),('mine-descent',30,False,False),
 ('mine-traverse',38,True,False),('mine-descent',22,True,False),
 ('citadel-crossing',36,False,False),('citadel-gallery',32,False,False),
 ('citadel-gallery',32,False,True),('citadel-crossing',36,False,False),
 ('citadel-gallery',32,False,False),('citadel-crossing',36,False,False),
 ('citadel-gallery',32,True,False),
 ('tower-ascent',22,True,False),('tower-bridge',36,False,False),
 ('tower-ascent',22,True,True),('tower-ascent',22,True,True),
 ('tower-stair-bend',36,True,False),('tower-bridge',36,True,False),
 ('tower-ascent',22,True,False),('camp-trail',22,False,False)
]

def main():
 data=json.loads(MAP.read_text(encoding='utf-8'))
 room_scenes={s['id']:s for s in json.loads((ROOT/'assets/data/natural-room-scenes.json').read_text(encoding='utf-8'))['scenes']}
 rooms={r['id']:r for r in data['rooms']}
 passages={}
 for id,(path,poly) in TRACES.items():
  im=Image.open(ROOT/f'assets/scenes/links-v2/{id}.png')
  passages[id]=dict(id=id,image=f'scenes/links-v2/{id}.png',pixelWidth=im.width,pixelHeight=im.height,walkable=[v for p in poly for v in p],path=path)
  # Diagnostics only: the source PNG remains untouched.
  im.thumbnail((900,900));overlay=Image.new('RGBA',im.size);d=ImageDraw.Draw(overlay)
  xy=lambda p:(p[0]*im.width,p[1]*im.height)
  d.polygon([xy(p) for p in poly],fill=(50,220,180,45),outline=(50,255,180,230))
  d.line([xy(p) for p in path],fill=(255,230,100,255),width=3)
  for i,p in enumerate(path):
   x,y=xy(p);d.ellipse((x-3,y-3,x+3,y+3),fill='white');d.text((x+5,y),str(i),fill='white')
  Image.alpha_composite(im.convert('RGBA'),overlay).convert('RGB').save(OUT/f'trace-{id}.jpg')
 def dims(r):
  s=room_scenes[r['scene']];return r['width'],r['width']*s['pixelHeight']/s['pixelWidth']
 def foot(ref):
  id,portal=ref.split('.');r=rooms[id];s=room_scenes[r['scene']];p=next(p for p in s['portals'] if p['id']==portal)['foot'];w,h=dims(r)
  return ((1-p[0] if r.get('flip') else p[0])*w,(1-p[1])*h)
 for r in rooms.values():
  r.pop('origin',None);r.pop('offsetX',None);r.pop('offsetY',None)
 rooms['CAMP']['origin']=[0,0]
 for link,config in zip([l for l in data['links'] if not l.get('oneWay')],PLACEMENTS):
  id,w,rev,flip=config;s=passages[id];h=w*s['pixelHeight']/s['pixelWidth'];line=list(reversed(s['path'])) if rev else s['path']
  def local(p):return ((1-p[0] if flip else p[0])*w,(1-p[1])*h)
  a=rooms[link['a'].split('.')[0]];b=rooms[link['b'].split('.')[0]]
  fa=foot(link['a']);fb=foot(link['b']);pa,pb=local(line[0]),local(line[-1])
  origin=[a['origin'][i]+fa[i]-pa[i] for i in range(2)]
  b['origin']=[origin[i]+pb[i]-fb[i] for i in range(2)]
  link['painted']=dict(scene=id,origin=origin,width=w,reverse=rev,flip=flip)
 # The final encounter remains reachable only through the camp seal.
 rooms['F']['origin']=[min(r['origin'][0] for r in rooms.values() if 'origin' in r)-65,100]
 rects=[(*r['origin'],*dims(r),r['id']) for r in rooms.values()]
 for l in data['links']:
  if 'painted' not in l:continue
  p=l['painted'];s=passages[p['scene']];rects.append((*p['origin'],p['width'],p['width']*s['pixelHeight']/s['pixelWidth'],l['a']+'>'+l['b']))
 shift=[math.ceil(12-min(r[i] for r in rects)) for i in range(2)]
 for r in rooms.values():r['origin']=[round(r['origin'][i]+shift[i],5) for i in range(2)]
 for l in data['links']:
  if 'painted' in l:l['painted']['origin']=[round(l['painted']['origin'][i]+shift[i],5) for i in range(2)]
 data['minCellX']=data['minCellY']=0
 data['maxCellX']=math.ceil((max(r[0]+r[2] for r in rects)+shift[0]+12)/data['slotW'])-1
 data['maxCellY']=math.ceil((max(r[1]+r[3] for r in rects)+shift[1]+12)/data['slotH'])-1
 data['passageScenes']=list(passages.values())
 data['_note']='그림을 먼저 배치하고 출입구를 맞춘 맵. origin은 왼쪽 아래 절대 타일 좌표. painted는 자르거나 반복하지 않는 길목 그림 한 장이며, walkable/path는 원본 위에서 직접 잰 경계/중앙선이다. design/painted-map-links/build_layout.py로 재현한다.'
 encoded=json.dumps(data,ensure_ascii=False,indent=2)
 encoded=re.sub(r'\[\s*([-\d.e+\s,]+)\s*\]',lambda m:'['+', '.join(v.strip() for v in m[1].split(','))+']',encoded)
 MAP.write_text(encoded+'\n',encoding='utf-8')
 # Layout QA with footprints and the actual centerlines.
 scale=4;W=(data['maxCellX']+1)*data['slotW'];H=(data['maxCellY']+1)*data['slotH']
 im=Image.new('RGB',(W*scale,H*scale),'#10141e');d=ImageDraw.Draw(im)
 def xy(x,y):return (x*scale,(H-y)*scale)
 for r in rooms.values():
  x,y=r['origin'];w,h=dims(r);d.rectangle((*xy(x,y+h),*xy(x+w,y)),outline='#64748b');d.text(xy(x+w/2,y+h/2),r['id'],fill='white')
 for l in data['links']:
  if 'painted' not in l:continue
  p=l['painted'];s=passages[p['scene']];x,y=p['origin'];w=p['width'];h=w*s['pixelHeight']/s['pixelWidth']
  d.rectangle((*xy(x,y+h),*xy(x+w,y)),outline='#24443e')
  d.line([xy(x+(1-u if p['flip'] else u)*w,y+(1-v)*h) for u,v in s['path']],fill='#ddc989',width=2)
 im.save(OUT/'layout.png')
 # Report unconnected paintings touching; all must be reviewed before shipping.
 for i,a in enumerate(rects):
  for b in rects[:i]:
   if a[0]<b[0]+b[2] and a[0]+a[2]>b[0] and a[1]<b[1]+b[3] and a[1]+a[3]>b[1]:
    if '>' not in a[4] and '>' not in b[4]:print('ROOM OVERLAP',a[4],b[4])
 print(f'Map: {W} x {H} tiles; {len(passages)} paintings, {len(PLACEMENTS)} passages')

if __name__=='__main__': main()
