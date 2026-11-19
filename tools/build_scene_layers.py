"""Reproduce full-canvas RGBA scene layers from reviewed source contours.

Never modifies source artwork. No resizing, feathering, recolouring, or inferred
pixels: each opaque output pixel is copied verbatim from the source PNG.
Run from the repository root. Requires Pillow. --check verifies committed output.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]


def raster(size, polygons):
    mask = Image.new('L', size)
    draw = ImageDraw.Draw(mask)
    for flat in polygons:
        if len(flat) < 6 or len(flat) % 2 or not all(0 <= n <= 1 for n in flat):
            raise ValueError('Invalid source contour')
        xy = [(round(flat[i] * size[0]), round(flat[i + 1] * size[1]))
              for i in range(0, len(flat), 2)]
        draw.polygon(xy, fill=255)
    return mask


def png_bytes(image):
    out = io.BytesIO()
    image.save(out, format='PNG', optimize=True)
    return out.getvalue()


def build(check=False):
    contours = json.loads((ROOT / 'docs/scene-layers/contours.json').read_text(encoding='utf-8'))
    manifest = {'version': 1, 'scenes': {}}
    report = []
    outputs = {}
    for key, scene in contours['scenes'].items():
        source_path = ROOT / 'assets' / scene['source']
        if hashlib.sha256(source_path.read_bytes()).hexdigest() != scene['sha256']:
            raise ValueError(f'Source artwork changed: {key}; retrace before rebuilding')
        source = Image.open(source_path).convert('RGBA')
        if list(source.size) != scene['size']:
            raise ValueError(f'Source dimensions changed: {key}')
        entry = {'objects': []}
        occupied = Image.new('L', source.size)
        # Nearest object owns any shared outline pixel. No double-painted layers.
        masks = {}
        for obj in reversed(scene['objects']):
            mask = raster(source.size, obj['polygons'])
            mask = ImageChops.subtract(mask, raster(source.size, obj.get('holes', [])))
            mask = ImageChops.subtract(mask, occupied)
            occupied = ImageChops.lighter(occupied, mask)
            masks[obj['id']] = mask

        def emit(name, mask):
            if mask.getbbox() is None:
                raise ValueError(f'Empty layer {key}/{name}')
            result = Image.new('RGBA', source.size, (0, 0, 0, 0))
            result.paste(source, (0, 0), mask)
            path = f'layers/{key}/{name}.png'
            outputs[ROOT / 'assets' / path] = png_bytes(result)
            report.append({'scene': key, 'file': path, 'canvas': list(source.size),
                           'bounds': list(mask.getbbox()), 'pixels': mask.histogram()[255]})
            return path

        for obj in scene['objects']:
            record = {k: obj[k] for k in ('id', 'foot', 'footLine', 'block') if k in obj}
            record['image'] = emit(obj['id'], masks[obj['id']])
            entry['objects'].append(record)
        if scene['front']:
            mask = ImageChops.subtract(raster(source.size, scene['front']), occupied)
            entry['front'] = emit('front', mask)
        manifest['scenes'][key] = entry

        # Apply local corrections on top of the team's existing movement mask.
        # Painting walkable first and solid last makes the operation idempotent.
        if scene.get('walkBlock') or scene.get('walkOpen'):
            walk_path = ROOT / 'assets/walkmask' / (key + '.png')
            walk = Image.open(walk_path).convert('L')
            corrected = ImageChops.lighter(walk, raster(walk.size, scene.get('walkOpen', [])))
            corrected = ImageChops.subtract(corrected, raster(walk.size, scene.get('walkBlock', [])))
            if ImageChops.difference(walk, corrected).getbbox():
                if check:
                    raise ValueError(f'Out-of-date movement mask: {key}')
                corrected.save(walk_path)

    outputs[ROOT / 'assets/data/scene-layers.json'] = (json.dumps(manifest, indent=2) + '\n').encode()
    outputs[ROOT / 'docs/scene-layers/inventory.json'] = (json.dumps(report, indent=2) + '\n').encode()
    for path, content in outputs.items():
        if check:
            if path.suffix == '.png':
                # Pillow/zlib versions may encode identical pixels differently.
                with Image.open(path) as current, Image.open(io.BytesIO(content)) as expected:
                    same = (current.mode == expected.mode and current.size == expected.size
                            and current.tobytes() == expected.tobytes())
            else:
                same = path.exists() and path.read_text(encoding='utf-8') == content.decode()
            if not same:
                raise ValueError(f'Out-of-date output: {path.relative_to(ROOT)}')
        elif not path.exists() or path.read_bytes() != content:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(content)
    print(f'{"Verified" if check else "Built"} {len(report)} layers in {len(manifest["scenes"])} scenes')


if __name__ == '__main__':
    args = argparse.ArgumentParser()
    args.add_argument('--check', action='store_true')
    build(args.parse_args().check)
