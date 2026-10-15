"""Inspect native passage assets; never modifies raster data."""
import json, hashlib
from pathlib import Path
from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[2]
records = []
for p in sorted((ROOT / 'assets/scenes/passages').glob('*.png')):
    im = Image.open(p).convert('RGBA')
    row = dict(file=p.relative_to(ROOT).as_posix(), width=im.width, height=im.height,
               alphaRange=im.getchannel('A').getextrema(), sha256=hashlib.sha256(p.read_bytes()).hexdigest())
    if p.stem.endswith('ground'):
        rgb = im.convert('RGB')
        def diff(a,b):
            return round(sum(ImageStat.Stat(ImageChops.difference(a,b)).mean)/3, 2)
        row['seamMeanAbsoluteRGB'] = dict(horizontal=diff(rgb.crop((0,0,1,im.height)),rgb.crop((im.width-1,0,im.width,im.height))),
                                         vertical=diff(rgb.crop((0,0,im.width,1)),rgb.crop((0,im.height-1,im.width,im.height))))
    records.append(row)
assert len(records) == 16
(Path(__file__).parent/'inspection.json').write_text(json.dumps(records,indent=2)+'\n',encoding='utf-8')
print('Inspected 16 native PNG files; no raster changes.')
