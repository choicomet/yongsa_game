"""Compile the November review into collision PNGs and cropped occlusion masks.

Python + Pillow required. Run from any directory. Original scene artwork is never
modified. Replay uses the pinned Git collision baseline, so repeated runs cannot
accumulate brush strokes or undo subsequent refinements in refinements.json.
"""
import hashlib
import io
import json
import subprocess
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'assets'
REVIEW = ROOT / 'docs/layer-review'
BASE = '123438a899a56999439da1e6c2212ee880a210a2'


def polygon(size, flat):
    image = Image.new('L', size)
    ImageDraw.Draw(image).polygon([(flat[i] * size[0], flat[i+1] * size[1])
                                  for i in range(0, len(flat), 2)], fill=255)
    return image


def stroke(size, s):
    if 'polygon' in s:
        return polygon(size, s['polygon'])
    image = Image.new('L', size)
    d = ImageDraw.Draw(image)
    pts = [(u * size[0], v * size[1]) for u, v in s['points']]
    width = max(1, round(s['width']))
    d.line(pts, fill=255, width=width, joint='curve')
    radius = width / 2
    for x, y in pts:
        d.ellipse((x-radius, y-radius, x+radius, y+radius), fill=255)
    return image


def compile_review():
    feedback = json.loads((REVIEW / 'feedback.json').read_text(encoding='utf8'))
    refine = json.loads((REVIEW / 'refinements.json').read_text(encoding='utf8'))
    objects = json.loads((REVIEW / 'objects.json').read_text(encoding='utf8'))['scenes']
    world = json.loads((ASSETS / 'data/map.json').read_text(encoding='utf8'))
    rooms = json.loads((ASSETS / 'data/natural-room-scenes.json').read_text(encoding='utf8'))['scenes']
    occlusion = json.loads((ASSETS / 'data/scene-occlusion.json').read_text(encoding='utf8'))['scenes']
    runtime = {f'{kind}/{item["id"]}': item for kind, items in
               [('rooms', rooms), ('passages', world['passageScenes']), ('joints', world['joints'])]
               for item in items}
    # Validate all inputs before writing anything. A later art/geometry update needs a new review.
    for art in feedback['artwork']:
        key = art['key']
        current = runtime[key]
        if current['image'] != art['image'] or current['walkMask'] != art['mask']:
            raise ValueError(f'Runtime painting mapping changed: {key}')
        image = ASSETS / art['image']
        if hashlib.sha256(image.read_bytes()).hexdigest() != art['artSha256']:
            raise ValueError(f'Artwork changed; re-review {key}')
        with Image.open(image) as picture:
            if picture.size != tuple(art['size']):
                raise ValueError(f'Artwork dimensions changed: {key}')
        baseline = occlusion.get(current['id'], []) if key.startswith('rooms/') else current.get('foregrounds', [])
        reviewed = feedback['layerDefinitions'][key]['polygons']
        fields = ('id', 'depth', 'polygon')
        if [{k: p[k] for k in fields} for p in baseline] != [{k: p[k] for k in fields} for p in reviewed]:
            raise ValueError(f'Original foregrounds changed; re-review {key}')
    manifest = {'version': 1, 'scenes': {}}
    stats = []
    for art in feedback['artwork']:
        key = art['key']
        review = feedback['reviews'][key]
        edits = refine.get(key, {})
        size = tuple(art['size'])
        collision = review['strokes'] if review['done'] else []
        if collision:
            original = subprocess.check_output(['git', 'show', f"{BASE}:assets/{art['mask']}"], cwd=ROOT)
            floor = Image.open(io.BytesIO(original)).convert('L')
            for i, s in enumerate(collision + edits.get('movementExtra', [])):
                s = {**s, **edits.get('movement', {}).get(str(i), {})}
                if s.get('disabled'):
                    continue
                mark = stroke(size, s)
                floor.paste(255 if s['mode'] == 'open' else 0, mask=mark)
            floor.convert('1', dither=Image.Dither.NONE).save(ASSETS / art['mask'], optimize=True)

        layers = review['layers']
        if key not in objects and (not layers['done'] or not (layers['strokes'] or layers['overrides'])):
            continue
        definition = feedback['layerDefinitions'][key]
        pieces = []
        base_layers = list(definition['polygons'])
        if definition.get('raster'):
            base_layers.append(definition['raster'])
        for original in base_layers:
            region = {**original, **layers['overrides'].get(original['id'], {})}
            if region.get('disabled'):
                continue
            if 'polygon' in region:
                mask = polygon(size, region['polygon'])
            else:
                mask = Image.open(ASSETS / region['image']).convert('RGBA').getchannel('A')
                if region.get('exclude'):
                    mask = ImageChops.subtract(mask, polygon(size, region['exclude']))
            pieces.append([region['mode'], region['depth'], mask, original['id']])
        for i, original in enumerate(layers['strokes']):
            s = {**original, **edits.get('layers', {}).get(str(i), {})}
            if s.get('disabled'):
                continue
            mask = stroke(size, s)
            if s['mode'] == 'erase-layer':
                for piece in pieces:
                    piece[2] = ImageChops.subtract(piece[2], mask)
            else:
                pieces.append([s['mode'], s['depth'], mask, f'stroke-{i:02d}'])
        if key in objects:
            # These are traced silhouettes, not annotation brush bounds. Keep every
            # prop independent even when two objects happen to share a foot depth.
            pieces = []
            for obj in objects[key]:
                mask = Image.new('L', size)
                for poly in obj['polygons']:
                    mask = ImageChops.lighter(mask, polygon(size, poly))
                for hole in obj.get('holes', []):
                    mask = ImageChops.subtract(mask, polygon(size, hole))
                pieces.append([obj['mode'], obj['depth'], mask, obj['id']])
        record = {'image': art['image'], 'size': list(size), 'pieces': []}
        for mode, depth, mask, object_id in pieces:
            box = mask.getbbox()
            if box is None:
                continue
            relative = f'occlusion/review/{key}/{object_id}.png'
            destination = ASSETS / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            mask.crop(box).save(destination, optimize=True)
            record['pieces'].append({'id': object_id, 'mask': relative, 'rect': [box[0], box[1], box[2]-box[0], box[3]-box[1]],
                                     'mode': mode, 'depth': depth})
            definition = next((o for o in objects.get(key, []) if o['id'] == object_id), {})
            if 'depthLine' in definition:
                record['pieces'][-1]['depthLine'] = definition['depthLine']
        manifest['scenes'][key] = record
        stats.append((key, len(record['pieces'])))
    (ASSETS / 'data/reviewed-occlusion.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf8')
    print(f'Compiled {len(stats)} scenes, {sum(n for _, n in stats)} cropped masks; artwork unchanged.')


if __name__ == '__main__':
    compile_review()
