#!/usr/bin/env python3
"""
임시 아트 생성기. CraftPix 에셋을 넣기 전까지 쓰는 그림을 만든다.

  assets/tiles/dungeon_tiles.png + assets/data/tileset.json   테마별 바닥·벽 타일 (32px)
  assets/sprites/gen/*.png + assets/data/sprites_generated.json 소품·몬스터·보스

sprites.json(직접 지정한 에셋)이 sprites_generated.json 보다 우선한다.
사용법: python3 tools/gen_art.py
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pixel import Canvas, hexc, mix, noise_fill, ramp, shade  # noqa: E402
from PIL import Image  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
T = 32

# ---------------------------------------------------------------------------
# 테마 색
# ---------------------------------------------------------------------------
THEMES = [
    # 0 캠프: 따뜻한 사암 석판
    dict(name="camp", floor="#7a6250", floor2="#6c5646", mortar="#3a2c26", brick="#5e4c46", top="#1c1618", accent="#a07850"),
    # 1 폐광: 거친 흙바닥과 자갈, 바위 벽과 나무 버팀목
    dict(name="mine", floor="#5c5048", floor2="#4c4038", mortar="#2a221e", brick="#4e4542", top="#161214", accent="#7a5634"),
    # 2 지하 요새: 푸른 회색 판석, 벽돌
    dict(name="fortress", floor="#4e5866", floor2="#434c58", mortar="#1e232c", brick="#46505e", top="#12151c", accent="#8a96a8"),
    # 3 봉인의 신전: 보랏빛 연마 석재, 룬 상감
    dict(name="temple", floor="#4e4560", floor2="#433b54", mortar="#1e1a2a", brick="#4a4260", top="#130f1c", accent="#b08ae0"),
]

FLOOR_N, FACE_N = 8, 3
COLS = FLOOR_N + FACE_N * 2 + 16


def slab(cv, x0, y0, w, h, pal, mortar, rnd, tint=0, bevel=True):
    """한 장의 석판: 은은한 색 차이, 왼쪽 위 빛 모서리, 오른쪽 아래 그림자 모서리."""
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            if not (0 <= x < T and 0 <= y < T):
                continue
            if y == y0 + h - 1 or x == x0 + w - 1:
                cv.set(x, y, mortar)
                continue
            i = 3 + tint
            if bevel and (y == y0 or x == x0):
                i += 1
            elif bevel and (y == y0 + h - 2 or x == x0 + w - 2):
                i -= 1
            elif rnd.random() < 0.12:
                i += rnd.choice([-1, 1])
            cv.set(x, y, pal[max(0, min(len(pal) - 1, i))])


def tile_floor(th, v, rnd):
    cv = Canvas(T, T)
    name = th["name"]
    base = ramp(th["floor"], 7, 0.62, 1.25)
    alt = ramp(th["floor2"], 7, 0.62, 1.25)
    mortar = shade(hexc(th["mortar"]), 1.1)
    tint = [0, 0, -1, 0, 1, 0, -1, 0][v]
    if name == "mine":
        # 다져진 흙바닥과 드문 돌
        for y in range(T):
            for x in range(T):
                n = (math.sin(x * 0.45 + v) + math.cos(y * 0.39 + v * 2)) * 0.5
                i = 3 + (1 if n > 0.6 else -1 if n < -0.7 else 0)
                if rnd.random() < 0.08:
                    i += rnd.choice([-1, 1])
                cv.set(x, y, alt[max(0, min(6, i))])
        for _ in range([1, 2, 0, 1, 3, 1, 2, 0][v]):
            cx, cy = rnd.randint(4, 27), rnd.randint(4, 27)
            rx, ry = rnd.randint(2, 4), rnd.randint(2, 3)
            for y in range(cy - ry, cy + ry + 2):
                for x in range(cx - rx, cx + rx + 1):
                    dx, dy = (x - cx) / rx, (y - cy) / ry
                    d = dx * dx + dy * dy
                    if d <= 1:
                        i = 5 if dy < -0.4 else 4 if dy < 0.3 else 2
                        cv.set(x, y, base[i])
                    elif d <= 1.6 and dy > 0.3:
                        cv.set(x, y, alt[1])  # 돌 그림자
        if v in (5, 6):
            for _ in range(6):
                x, y = rnd.randint(1, 30), rnd.randint(1, 30)
                cv.set(x, y, base[5])
                cv.set(x + 1, y + 1, alt[1])
    elif name == "camp":
        # 엇갈린 큰 벽돌 바닥 (32x16)
        for row in range(2):
            off = 16 if (row + v) % 2 else 0
            for bx in range(-1, 2):
                pal = base if (bx + row + v) % 3 else alt
                slab(cv, bx * 32 + off, row * 16, 32, 16, pal, mortar, rnd, tint + (1 if (bx + row) % 2 else 0))
    elif name == "fortress":
        # 큰 판석, 가끔 두 장으로 나뉨
        if v in (2, 5):
            slab(cv, 0, 0, 32, 16, base, mortar, rnd, tint)
            slab(cv, 0, 16, 32, 16, alt, mortar, rnd, tint)
        elif v == 3:
            slab(cv, 0, 0, 16, 32, alt, mortar, rnd, tint)
            slab(cv, 16, 0, 16, 32, base, mortar, rnd, tint)
        else:
            slab(cv, 0, 0, 32, 32, base if v % 2 else alt, mortar, rnd, tint)
        if v == 6:
            for y in range(10, 20):
                for x in range(10, 22):
                    cv.set(x, y, hexc("#1a1e26") if (x + y) % 2 else hexc("#5e6674"))
    else:
        # 신전: 연마된 큰 판석 + 테두리 상감
        slab(cv, 0, 0, 32, 32, base if v % 2 else alt, mortar, rnd, tint)
        acc = hexc(th["accent"])
        if v in (2, 5):
            for i in range(4, 28):
                cv.set(i, 4, shade(acc, 0.45))
                cv.set(i, 27, shade(acc, 0.45))
                cv.set(4, i, shade(acc, 0.45))
                cv.set(27, i, shade(acc, 0.45))
            for a in range(0, 360, 15):
                cv.set(16 + round(6 * math.cos(math.radians(a))), 16 + round(6 * math.sin(math.radians(a))), shade(acc, 0.65))
            cv.set(16, 16, acc)
        for k in range(6):  # 광택
            cv.set(6 + k, 4 + k // 2, base[6], 120)
    if v in (4, 7):
        x, y = rnd.randint(6, 24), rnd.randint(4, 12)
        for _ in range(rnd.randint(8, 13)):
            cv.set(x, y, mortar)
            x += rnd.choice([-1, 0, 1])
            y += 1
    return cv.image()


def tile_face(th, upper, v, rnd):
    """벽 정면 (위/아래 두 칸이 이어져 64px 높이가 된다)."""
    cv = Canvas(T, T)
    name = th["name"]
    br = ramp(th["brick"], 6, 0.55, 1.3)
    mortar = hexc(th["mortar"])
    if name == "mine":
        # 거친 바위 면
        noise_fill(cv, 0, 0, T, T, br, rnd, 2, 1, 0.5)
        for _ in range(7):
            cx, cy = rnd.randint(0, 31), rnd.randint(0, 31)
            rx, ry = rnd.randint(4, 8), rnd.randint(3, 5)
            for y in range(cy - ry, cy + ry + 1):
                for x in range(cx - rx, cx + rx + 1):
                    dx, dy = (x - cx) / rx, (y - cy) / ry
                    d = dx * dx + dy * dy
                    if d <= 1 and 0 <= x < T and 0 <= y < T:
                        i = 4 if dy < -0.5 else 3 if dy < 0 else 2 if d < 0.7 else 0
                        cv.set(x, y, br[i])
        if v == 1:
            # 나무 버팀목
            wood = ramp("#6a4426", 5, 0.6, 1.3)
            for y in range(T):
                for x in range(12, 20):
                    i = 3 if x == 13 else 1 if x >= 18 else 2
                    cv.set(x, y, wood[i])
                cv.set(11, y, hexc("#1a120c"))
                cv.set(20, y, hexc("#1a120c"))
            if upper:
                for x in range(4, 28):
                    for y in range(2, 7):
                        cv.set(x, y, wood[1 if y >= 5 else 3 if y == 2 else 2])
                cv.hline(4, 27, 7, hexc("#1a120c"))
    else:
        # 벽돌 (8px 줄)
        for row in range(4):
            y0 = row * 8
            off = 8 if (row + (0 if upper else 1)) % 2 else 0
            for bx in range(-1, 3):
                x0 = bx * 16 + off
                k = rnd.choice([2, 2, 3, 1])
                for y in range(y0, y0 + 8):
                    for x in range(x0, x0 + 16):
                        if not (0 <= x < T):
                            continue
                        if y == y0 + 7 or x == x0 + 15:
                            cv.set(x, y, mortar)
                            continue
                        i = k
                        if y == y0:
                            i = min(5, k + 2)
                        elif y == y0 + 6:
                            i = max(0, k - 1)
                        elif rnd.random() < 0.25:
                            i += rnd.choice([-1, 1])
                        cv.set(x, y, br[max(0, min(5, i))])
        if name == "temple" and upper and v == 2:
            # 조각된 문양
            acc = hexc(th["accent"])
            for x in range(8, 24):
                cv.set(x, 12, shade(acc, 0.55))
                cv.set(x, 20, shade(acc, 0.55))
            for y in range(12, 21):
                cv.set(8, y, shade(acc, 0.55))
                cv.set(23, y, shade(acc, 0.55))
            cv.set(16, 16, acc)
            cv.set(15, 16, shade(acc, 0.8))
            cv.set(17, 16, shade(acc, 0.8))
        if name == "fortress" and upper and v == 2:
            # 쇠사슬
            for y in range(4, 28, 3):
                cv.set(15, y, hexc("#8a8e96"))
                cv.set(16, y + 1, hexc("#5a5e66"))
    if upper:
        # 윗면과 만나는 모서리 (갓돌)
        cap = ramp(th["brick"], 6, 0.55, 1.45)
        for x in range(T):
            cv.set(x, 0, cap[5])
            cv.set(x, 1, cap[4])
            cv.set(x, 2, cap[2])
            cv.set(x, 3, mortar)
    else:
        # 바닥과 만나는 걸레받이 그림자
        for x in range(T):
            cv.set(x, 30, mortar)
            cv.set(x, 31, shade(mortar, 0.7))
            cv.set(x, 29, shade(br[1], 0.8))
    return cv.image()


def tile_top(th, mask, rnd):
    """벽 윗면. mask 비트: 1=북, 2=동, 4=남, 8=서 쪽이 열려 있음(바닥/정면)."""
    cv = Canvas(T, T)
    top = ramp(th["top"], 6, 1.2, 2.6)
    rim = ramp(th["brick"], 6, 0.6, 1.5)
    # 윗면 돌 블록 (16px, 엇갈림)
    for row in range(2):
        off = 8 if row else 0
        for bx in range(-1, 3):
            x0, y0 = bx * 16 + off, row * 16
            k = rnd.choice([1, 2, 2, 3])
            for y in range(y0, y0 + 16):
                for x in range(x0, x0 + 16):
                    if not (0 <= x < T):
                        continue
                    if y == y0 + 15 or x == x0 + 15:
                        cv.set(x, y, top[0])
                    elif y == y0 or x == x0:
                        cv.set(x, y, top[min(5, k + 1)])
                    else:
                        cv.set(x, y, top[k] if rnd.random() > 0.1 else top[max(0, k - 1)])
    if mask & 4:  # 남쪽이 정면: 갓돌 모서리
        for x in range(T):
            cv.set(x, 31, rim[5])
            cv.set(x, 30, rim[3])
            cv.set(x, 29, top[0])
    if mask & 1:
        for x in range(T):
            cv.set(x, 0, rim[2])
    if mask & 8:
        for y in range(T):
            cv.set(0, y, rim[4])
            cv.set(1, y, rim[2])
    if mask & 2:
        for y in range(T):
            cv.set(31, y, rim[2])
            cv.set(30, y, rim[1])
    return cv.image()


def shadow_tiles():
    """바닥 위에 겹치는 그림자 (흰 바탕 투명도). 북쪽 벽 아래, 서/동쪽 벽 옆."""
    imgs = []
    for kind in ("n", "w", "e", "s"):
        img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
        p = img.load()
        for y in range(T):
            for x in range(T):
                if kind == "n":
                    a = max(0, 1 - y / 12) * 0.55
                elif kind == "w":
                    a = max(0, 1 - x / 8) * 0.4
                elif kind == "e":
                    a = max(0, 1 - (31 - x) / 8) * 0.4
                else:
                    a = max(0, 1 - (31 - y) / 4) * 0.35
                if a > 0:
                    p[x, y] = (0, 0, 0, int(a * 255))
        imgs.append(img)
    return imgs


def gen_tiles():
    rnd = random.Random(11)
    atlas = Image.new("RGBA", (COLS * T, (len(THEMES) + 1) * T), (0, 0, 0, 0))
    meta = {"image": "tiles/dungeon_tiles.png", "tileSize": T, "scale": 1, "themes": []}
    for ti, th in enumerate(THEMES):
        y = ti * T
        col = 0
        entry = {"floor": [], "faceUpper": [], "faceLower": [], "top": []}
        for v in range(FLOOR_N):
            atlas.alpha_composite(tile_floor(th, v, rnd), (col * T, y))
            entry["floor"].append([col, ti])
            col += 1
        for v in range(FACE_N):
            atlas.alpha_composite(tile_face(th, True, v, rnd), (col * T, y))
            entry["faceUpper"].append([col, ti])
            col += 1
        for v in range(FACE_N):
            atlas.alpha_composite(tile_face(th, False, v, rnd), (col * T, y))
            entry["faceLower"].append([col, ti])
            col += 1
        for mask in range(16):
            atlas.alpha_composite(tile_top(th, mask, rnd), (col * T, y))
            entry["top"].append([col, ti])
            col += 1
        # 바닥 무늬 가중치: 앞쪽 변형이 자주 나오게
        entry["floorWeights"] = [6, 6, 2, 3, 1, 1, 2, 1]
        meta["themes"].append(entry)
    sy = len(THEMES)
    meta["shadow"] = {}
    for i, (k, img) in enumerate(zip(("n", "w", "e", "s"), shadow_tiles())):
        atlas.alpha_composite(img, (i * T, sy * T))
        meta["shadow"][k] = [i, sy]
    os.makedirs(os.path.join(ROOT, "assets", "tiles"), exist_ok=True)
    atlas.save(os.path.join(ROOT, "assets", "tiles", "dungeon_tiles.png"))
    with open(os.path.join(ROOT, "assets", "data", "tileset.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
    return atlas


if __name__ == "__main__":
    gen_tiles()
    import gen_sprites
    gen_sprites.main()
    print("art ok")
