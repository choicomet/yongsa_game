#!/usr/bin/env python3
"""
직접 만든 보스 시트(art_source/bosses/*.webp)를 게임용 시트로 바꾼다.

원본: 흰 바탕에 자세 5개가 가로로 놓인 큰 그림 (평소, 공격1 예고, 공격1 타격, 공격2 예고, 공격2 타격).
하는 일: 흰 바탕을 지우고, 자세별로 잘라, 발을 칸 바닥·가운데에 맞춰 줄이고, 땅을 걷는 보스는 걷는 동작 4장을 덧붙여,
        assets/sprites/boss/<키>.png (가로 5칸) 와 assets/data/sprites_bosses.json 을 만든다.
어떤 공격이 어떤 자세를 쓰는지는 balance.json 의 bossAttacks[].anim ("<이름>_tele" / "<이름>_hit").
사용법: python3 tools/import_boss_sheets.py   (numpy, scipy 필요)
"""
import json
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art_source", "bosses")
OUT = os.path.join(ROOT, "assets", "sprites", "boss")

# 키, 원본 파일, 자세 이름(공격1, 공격2), 평소 자세의 게임 내 높이(px), 떠 있는 정도,
# 시트에서 (평소, 공격1 예고, 공격1 타격, 공격2 예고, 공격2 타격) 이 놓인 칸
BOSSES = [
    ("boss_mine", "golem.webp", ("slam", "swing"), 150, 0, (0, 1, 2, 4, 3)),  # 4번째가 내지른 주먹, 5번째가 팔을 당긴 자세
    ("boss_fortress", "knight.webp", ("smash", "bash"), 168, 0, (0, 1, 2, 3, 4)),
    ("boss_temple", "lich.webp", ("cast", "summon"), 176, 8, (0, 1, 2, 3, 4)),
]


def cut_background(img):
    """가장자리와 이어진 흰 바탕을 투명하게 만든다 (그림 안의 흰 반짝임은 남긴다)."""
    img = img.convert("RGBA")
    w, h = img.size
    px = img.load()
    near = bytearray(w * h)
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 40 or (min(r, g, b) > 232 and max(r, g, b) - min(r, g, b) < 18):
                near[y * w + x] = 1
    seen = bytearray(w * h)
    stack = [(x, y) for x in range(w) for y in (0, h - 1)] + [(x, y) for y in range(h) for x in (0, w - 1)]
    while stack:
        x, y = stack.pop()
        i = y * w + x
        if x < 0 or y < 0 or x >= w or y >= h or seen[i] or not near[i]:
            continue
        seen[i] = 1
        px[x, y] = (0, 0, 0, 0)
        stack += [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
    return img


def poses(img):
    """
    왼쪽부터 자세 5개의 그림. 자세끼리 가로로 겹쳐 있어 세로줄로는 못 자르므로 픽셀 덩어리로 나눈다:
    가장 큰 덩어리 5개가 몸이고, 떨어져 있는 조각(파편·후광·마법진)은 가장 가까운 몸에 붙인다.
    """
    alpha = np.array(img.getchannel("A")) > 40
    labels, n = ndimage.label(ndimage.binary_dilation(alpha, iterations=3))
    labels[~alpha] = 0
    ids = np.arange(1, n + 1)
    area = ndimage.sum(alpha, labels, ids)
    centers = ndimage.center_of_mass(alpha, labels, ids)
    main = sorted(sorted(ids, key=lambda i: -area[i - 1])[:5], key=lambda i: centers[i - 1][1])
    owner = {}
    for i in ids:
        if area[i - 1] == 0:
            continue
        cy, cx = centers[i - 1]
        owner[i] = i if i in main else min(main, key=lambda m: abs(centers[m - 1][1] - cx) + 0.3 * abs(centers[m - 1][0] - cy))
    out = []
    rgba = np.array(img)
    for m in main:
        mask = np.isin(labels, [i for i, o in owner.items() if o == m])
        part = rgba.copy()
        part[~mask] = 0
        pose = Image.fromarray(part, "RGBA")
        bb = pose.getbbox()
        out.append((pose.crop(bb), bb))
    return out


def feet_x(pose):
    """발 위치: 아래쪽 1/5 의 가로 중심 (자른 그림 기준)."""
    a = np.array(pose.getchannel("A")) > 40
    cols = np.nonzero(a[-max(1, a.shape[0] // 5):].any(axis=0))[0]
    return float(cols.mean()) if len(cols) else pose.width / 2


def walk_frames(idle, feet):
    """
    평소 자세 한 장으로 걷는 동작 4장을 만든다: 한쪽 다리를 들고 몸이 반대로 실렸다가 내려앉기를 좌우로 되풀이한다.
    (원본 시트에 걷는 그림이 없어서 다리 부분과 몸 부분을 따로 움직여 만든다. 걷는 그림을 따로 그리면 이 함수 대신 그것을 쓰면 된다.)
    """
    src = np.array(idle)
    h, w = src.shape[:2]
    legs = int(h * 0.74)          # 이 줄 아래가 다리
    cx = int(round(feet))

    def frame(lift_left, lift_right, sink, lean):
        out = np.zeros_like(src)

        def paste(block, x0, y0):
            hh, ww = block.shape[:2]
            ys, xs = max(0, y0), max(0, x0)
            ye, xe = min(h, y0 + hh), min(w, x0 + ww)
            if ye <= ys or xe <= xs:
                return
            part = block[ys - y0:ye - y0, xs - x0:xe - x0]
            mask = part[..., 3] > 0
            out[ys:ye, xs:xe][mask] = part[mask]

        paste(src[legs:, :cx], 0, legs - lift_left)       # 왼다리
        paste(src[legs:, cx:], cx, legs - lift_right)     # 오른다리
        paste(src[:legs], lean, sink)                     # 몸 (다리 위에 덮는다)
        return Image.fromarray(out, "RGBA")

    return [frame(8, 0, 1, 2), frame(0, 0, 5, 0), frame(0, 8, 1, -2), frame(0, 0, 5, 0)]


def main():
    os.makedirs(OUT, exist_ok=True)
    manifest = {"_설명": "직접 만든 보스 시트. tools/import_boss_sheets.py 가 art_source/bosses 에서 만든다."}
    for key, file, (a1, a2), height, hover, cols in BOSSES:
        img = cut_background(Image.open(os.path.join(SRC, file)))
        parts = poses(img)
        assert len(parts) == 5, (file, len(parts))
        k = height / parts[0][0].height
        frames, feet = [], []
        for part, _ in parts:
            size = (max(1, round(part.width * k)), max(1, round(part.height * k)))
            part = part.resize(size, Image.LANCZOS)
            # 반투명 가장자리를 정리해 윤곽을 또렷하게
            r, g, bl, al = part.split()
            al = al.point(lambda v: 0 if v < 110 else 255)
            part = Image.merge("RGBA", (r, g, bl, al))
            part = part.crop(part.getbbox())
            frames.append(part)
            feet.append(feet_x(part))
        # 땅을 딛고 걷는 보스는 걷는 동작을 덧붙인다 (떠 있는 보스는 평소 자세 그대로 움직인다)
        walk = walk_frames(frames[0], feet[0]) if hover == 0 else []
        frames += walk
        feet += [feet[0]] * len(walk)
        # 칸: 발을 기준으로 좌우로 가장 많이 뻗은 만큼, 높이는 가장 큰 자세만큼.
        # 모든 자세의 맨 아래(발)를 칸 바닥에 맞춘다 — 그래야 그림자 위에 서 있는 것처럼 보인다.
        left = max(feet)
        right = max(f.width - x for f, x in zip(frames, feet))
        fw, fh = int(left + right) + 4, max(f.height for f in frames) + 2
        sheet = Image.new("RGBA", (fw * len(frames), fh), (0, 0, 0, 0))
        for i, (part, fx) in enumerate(zip(frames, feet)):
            sheet.alpha_composite(part, (i * fw + round(left - fx) + 2, fh - part.height))
        sheet.save(os.path.join(OUT, key + ".png"))

        def anim(col, n=1, fps=1):
            return {"file": "sprites/boss/" + key + ".png", "frameWidth": fw, "frameHeight": fh, "col": col, "frames": n, "fps": fps}

        manifest[key] = {"originX": round(left) + 2, "originY": 2 - hover, "scale": 1.0, "anims": {
            "idle": anim(cols[0]), "move": anim(5, len(walk), 6) if walk else anim(cols[0]),
            a1 + "_tele": anim(cols[1]), a1 + "_hit": anim(cols[2]), a2 + "_tele": anim(cols[3]), a2 + "_hit": anim(cols[4])}}
        print(key, fw, "x", fh, "frames", len(frames))
    with open(os.path.join(ROOT, "assets", "data", "sprites_bosses.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
