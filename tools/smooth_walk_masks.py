"""Smooth jagged walk-mask edges so the hero's foot does not snag on them.

The foot is a circle (hero radius 9 world px). A notch in the outer wall that is
narrower than the foot cannot be entered, but its corners still catch the foot
ring and stop the hero. This fills such notches (morphological closing with a
radius below the foot radius) and removes stray specks, without:
  - joining two separate floor areas (a thin wall stays a wall),
  - filling enclosed obstacles (pillar or prop footprints) larger than a speck.
Run from the repository root. Requires Pillow, numpy and scipy.
  python tools/smooth_walk_masks.py            # rewrite masks in place
  python tools/smooth_walk_masks.py --check    # report only
"""
import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage as nd

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'assets'
TILE = 32
FOOT_RADIUS = 9          # HeroActor.hw
CLOSE_RATIO = 0.75       # closing radius as a share of the foot radius
SPECK_AREA = 80          # enclosed black specks below this many mask pixels are noise
# Spots left exactly as painted (u, v): smoothing there leaves a sliver the
# reachability audit (DungeonWalkabilityTest) reports as isolated floor.
KEEP = {
    'walkmask/joints/exit-R7-R3.png': [(0.820, 0.587)],
    'walkmask/joints/entry-R5-R6.png': [(0.718, 0.752)],
}


def masks():
    """(mask path, world width) for every painting. Smallest placement wins (tighter foot)."""
    m = json.loads((ASSETS / 'data/map.json').read_text(encoding='utf-8'))
    d = json.loads((ASSETS / 'data/natural-room-scenes.json').read_text(encoding='utf-8'))
    d = d['scenes'] if isinstance(d, dict) else d
    width = {}

    def put(path, w):
        width[path] = min(width.get(path, 1e9), w)

    room_w = {r['scene']: r['width'] for r in m['rooms'] if r.get('scene')}
    for s in d:
        if s['id'] in room_w:
            put(s['walkMask'], room_w[s['id']] * TILE)
    pass_w = {}
    for link in m['links']:
        p = link.get('painted')
        if p:
            pass_w[p['scene']] = min(pass_w.get(p['scene'], 1e9), p['width'])
    for s in m['passageScenes']:
        if s['id'] in pass_w:
            put(s['walkMask'], pass_w[s['id']] * TILE)
    for j in m['joints']:
        put(j['walkMask'], j['rect'][2])
    return width


def disk(r):
    r = max(1, int(round(r)))
    y, x = np.ogrid[-r:r + 1, -r:r + 1]
    return x * x + y * y <= r * r


def ring_fit(white, r):
    """Where the game's foot test passes: centre and eight points at radius r (WalkMask.fits)."""
    h, w = white.shape
    fit = white.copy()
    for k in range(8):
        a = k * np.pi / 4
        dx, dy = int(round(np.cos(a) * r)), int(round(np.sin(a) * r))
        shifted = np.zeros_like(white)
        ys, yd = (slice(max(0, dy), h), slice(0, h - max(0, dy))) if dy >= 0 else (slice(0, h + dy), slice(-dy, h))
        xs, xd = (slice(max(0, dx), w), slice(0, w - max(0, dx))) if dx >= 0 else (slice(0, w + dx), slice(-dx, w))
        shifted[yd, xd] = white[ys, xs]
        fit &= shifted
    return fit


def smooth(white, radius):
    pad = int(radius) + 2
    padded = np.pad(white, pad, mode='edge')
    closed = nd.binary_closing(padded, disk(radius))[pad:-pad, pad:-pad]
    added = closed & ~white
    # Only fill the outer wall's notches and small specks, never real obstacles.
    black, n = nd.label(~white)
    edge = set(np.unique(np.concatenate([black[0], black[-1], black[:, 0], black[:, -1]]))) - {0}
    sizes = nd.sum(np.ones_like(black), black, range(1, n + 1))
    keep_black = np.zeros(n + 1, bool)
    for i in range(1, n + 1):
        keep_black[i] = i not in edge and sizes[i - 1] >= SPECK_AREA
    added &= ~keep_black[black]
    specks = np.zeros(n + 1, bool)
    for i in range(1, n + 1):
        specks[i] = i not in edge and sizes[i - 1] < SPECK_AREA
    # Never bridge two floors: drop each added patch that touches more than one.
    floor, _ = nd.label(white)
    patches, _ = nd.label(added, structure=np.ones((3, 3)))
    for p, box in enumerate(nd.find_objects(patches), start=1):
        y0, y1 = max(0, box[0].start - 1), box[0].stop + 1
        x0, x1 = max(0, box[1].start - 1), box[1].stop + 1
        sel = patches[y0:y1, x0:x1] == p
        ring = nd.binary_dilation(sel, np.ones((3, 3), bool))
        if len(set(np.unique(floor[y0:y1, x0:x1][ring])) - {0}) > 1:
            added[y0:y1, x0:x1] &= ~sel
    out = white | added | specks[black]
    # Never create a new standing spot cut off from the old ones (a filled notch
    # can make a tiny pocket fit the foot). Revert the fill around such pockets.
    foot_r = radius / CLOSE_RATIO
    old_fit, new_fit = ring_fit(white, foot_r), ring_fit(out, foot_r)
    fit_lab, fn = nd.label(new_fit)
    orphan = np.zeros(fn + 1, bool)
    hit = set(np.unique(fit_lab[old_fit])) - {0}
    for i in range(1, fn + 1):
        orphan[i] = i not in hit
    if orphan.any():
        near = nd.binary_dilation(orphan[fit_lab], disk(foot_r * 1.5))
        out &= ~(near & ~white)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--check', action='store_true')
    args = ap.parse_args()
    for rel, world_w in sorted(masks().items()):
        path = ASSETS / rel
        im = Image.open(path)
        a = np.asarray(im.convert('L')) >= 128
        radius = FOOT_RADIUS * a.shape[1] / world_w * CLOSE_RATIO
        out = smooth(a, radius)
        for u, v in KEEP.get(rel, []):
            yy, xx = np.ogrid[:a.shape[0], :a.shape[1]]
            r = radius / CLOSE_RATIO * 2.5
            near = (xx - u * a.shape[1]) ** 2 + (yy - v * a.shape[0]) ** 2 <= r * r
            out = np.where(near, a, out)
        changed = int((out != a).sum())
        print(f'{rel:40s} radius {radius:4.1f}px  opened {changed}px')
        if changed and not args.check:
            Image.fromarray(np.where(out, 255, 0).astype(np.uint8)).convert(im.mode).save(path, optimize=True)


if __name__ == '__main__':
    main()
