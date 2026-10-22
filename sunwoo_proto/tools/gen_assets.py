"""
용사가 다한다 - 임시 에셋과 통합 맵 생성기.

만드는 것
  assets/maps/tiles.png         32px 타일셋 (테마 5개 × 바닥 3변형 + 벽 윗면 + 벽 앞면)
  assets/maps/world.tmx         Tiled 통합 맵 (캠프 + 던전 3곳 + 마왕의 심연), 장식·횃불·조명 포함
  assets/sprites/*.png          캐릭터·보스·장식 그림 (프레임은 가로로 이어 붙임)
  assets/sprites/manifest.json  그림 이름 → 프레임 수

진짜 그림이 오면 같은 이름·같은 프레임 크기로 덮어쓰고, 프레임 수가 바뀌면 manifest.json만 고친다.
이 스크립트를 다시 돌리면 맵과 그림을 모두 덮어쓴다.

실행: python3 tools/gen_assets.py
"""
import json
import math
import os
import random
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw  # noqa: E402

import chars  # noqa: E402
import props  # noqa: E402
from art import Canvas, rgb, shade, strip  # noqa: E402

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")
T = 32
W, H = 150, 140


class R:
    def __init__(self, x0, y0, x1, y1):
        self.x0, self.y0, self.x1, self.y1 = x0, y0, x1, y1

    def cells(self):
        for y in range(self.y0, self.y1 + 1):
            for x in range(self.x0, self.x1 + 1):
                yield x, y

    @property
    def w(self): return self.x1 - self.x0 + 1

    @property
    def h(self): return self.y1 - self.y0 + 1

    @property
    def cx(self): return (self.x0 + self.x1 + 1) / 2

    @property
    def cy(self): return (self.y0 + self.y1 + 1) / 2

    def ring(self):
        return R(self.x0 - 1, self.y0 - 1, self.x1 + 1, self.y1 + 1)


CAMP = R(66, 30, 83, 43)
ARENA = R(62, 4, 87, 21)
BED = R(68, 40, 68, 41)
FORGE = R(79, 41, 81, 42)
SIGIL = (75, 36.5)
SPAWN = (75, 32.6)

DUNGEONS = [
    dict(id="left", theme="left", mobs=5, gate=None,
         rooms=[("L1", "mob", R(46, 30, 59, 41), "camp"),
                ("L2", "mob", R(26, 30, 39, 41), "L1"),
                ("L3", "ore", R(28, 14, 39, 23), "L2"),
                ("L4", "mob", R(6, 30, 19, 41), "L2"),
                ("L5", "boss", R(4, 48, 21, 61), "L4")],
         corridors=[(R(60, 35, 65, 37), "camp", "L1"), (R(40, 35, 45, 37), "L1", "L2"),
                    (R(32, 24, 34, 29), "L2", "L3"), (R(20, 35, 25, 37), "L2", "L4"),
                    (R(11, 42, 13, 47), "L4", "L5")]),
    dict(id="right", theme="right", mobs=5, gate=R(84, 35, 84, 37),
         rooms=[("R1", "mob", R(90, 30, 103, 41), "camp"),
                ("R2", "ore", R(92, 14, 103, 23), "R1"),
                ("R3", "mob", R(110, 30, 123, 41), "R1"),
                ("R4", "mob", R(130, 30, 143, 41), "R3"),
                ("R5", "ore", R(132, 14, 143, 23), "R4"),
                ("R6", "boss", R(128, 48, 145, 61), "R4")],
         corridors=[(R(84, 35, 89, 37), "camp", "R1"), (R(96, 24, 98, 29), "R1", "R2"),
                    (R(104, 35, 109, 37), "R1", "R3"), (R(124, 35, 129, 37), "R3", "R4"),
                    (R(136, 24, 138, 29), "R4", "R5"), (R(136, 42, 138, 47), "R4", "R6")]),
    dict(id="top", theme="top", mobs=5, gate=R(73, 44, 75, 44),
         rooms=[("T1", "mob", R(68, 50, 81, 61), "camp"),
                ("T2", "mob", R(68, 68, 81, 79), "T1"),
                ("T3", "ore", R(48, 69, 59, 78), "T2"),
                ("T4", "mob", R(68, 86, 81, 97), "T2"),
                ("T5", "ore", R(90, 87, 101, 96), "T4"),
                ("T6", "mob", R(68, 104, 81, 115), "T4"),
                ("T7", "boss", R(62, 120, 87, 137), "T6")],
         corridors=[(R(73, 44, 75, 49), "camp", "T1"), (R(73, 62, 75, 67), "T1", "T2"),
                    (R(60, 73, 67, 75), "T2", "T3"), (R(73, 80, 75, 85), "T2", "T4"),
                    (R(82, 90, 89, 92), "T4", "T5"), (R(73, 98, 75, 103), "T4", "T6"),
                    (R(73, 116, 75, 119), "T6", "T7")]),
]


# =====================================================================================
# 바닥·벽 배치 (벽은 아래가 바닥이면 앞면, 아니면 윗면)
# =====================================================================================
def build_layers():
    theme = [[None] * W for _ in range(H)]
    floor = [[False] * W for _ in range(H)]

    def carve(rect, th):
        for x, y in rect.cells():
            theme[y][x] = th
            floor[y][x] = True

    carve(CAMP, "camp")
    carve(ARENA, "arena")
    for d in DUNGEONS:
        for _, _, r, _ in d["rooms"]:
            carve(r, d["theme"])
        for c, _, _ in d["corridors"]:
            carve(c, d["theme"])

    rnd = random.Random(31)
    ground = [[0] * W for _ in range(H)]
    walls = [[0] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if floor[y][x]:
                th = theme[y][x]
                roll = rnd.random()
                v = 0 if roll < 0.72 else (1 if roll < 0.92 else 2)
                if th == "left" and v == 2 and rnd.random() < 0.6:
                    v = 1
                ground[y][x] = props.tile_index(th, v) + 1
                continue
            near = None
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H and floor[ny][nx]:
                        near = theme[ny][nx]
            if near:
                below_floor = y - 1 >= 0 and floor[y - 1][x]
                walls[y][x] = props.tile_index(near, 4 if below_floor else 3) + 1
    return ground, walls, floor


def door_cells(room, corridor):
    ring = room.ring()
    cells = [(x, y) for x, y in corridor.cells()
             if (x in (ring.x0, ring.x1) and ring.y0 <= y <= ring.y1)
             or (y in (ring.y0, ring.y1) and ring.x0 <= x <= ring.x1)]
    if not cells:
        return None
    xs, ys = [c[0] for c in cells], [c[1] for c in cells]
    return R(min(xs), min(ys), max(xs), max(ys))


def inward(room, door):
    ring = room.ring()
    if door.x0 == ring.x0 and door.x1 == ring.x0: return (1, 0)
    if door.x0 == ring.x1 and door.x1 == ring.x1: return (-1, 0)
    if door.y0 == ring.y0 and door.y1 == ring.y0: return (0, 1)
    return (0, -1)


# =====================================================================================
# 장식 배치
# =====================================================================================
DECOR_SOLID = {"barrel", "crate", "pillar", "pillar_broken", "brazier", "brazier_blue", "brazier_red",
               "minecart", "rocks", "weapon_rack", "crystal_blue", "crystal_purple", "candelabra", "tent", "campfire"}
LIGHTS = {
    "torch": (150, "ffb050"), "brazier": (190, "ffa040"), "brazier_blue": (190, "70a0ff"),
    "brazier_red": (200, "ff4030"), "campfire": (230, "ffa040"), "lantern": (150, "ffc860"),
    "candelabra": (150, "ffb050"), "crystal_blue": (140, "70c8ff"), "crystal_purple": (140, "c070ff"),
}


def decorate():
    rnd = random.Random(77)
    out = []

    def add(sprite, tx, ty, flat=False):
        out.append((sprite, tx, ty, flat))

    # ---- 캠프
    add("tent", 71.5, 41.2)
    add("campfire", 70.0, 34.2)
    add("weapon_rack", 77.6, 41.3)
    for (x, y) in ((66.7, 30.5), (83.3, 30.5), (66.7, 43.2), (83.3, 43.2)):
        add("brazier", x, y)
    add("barrel", 82.6, 32.4); add("crate", 82.5, 33.6); add("barrel", 67.3, 32.6); add("crate", 67.4, 38.6)
    for x in (70.5, 79.5):
        add("torch", x, 44.15, flat=True)

    theme_items = {
        "left": ["rocks", "barrel", "minecart", "crate", "rocks"],
        "right": ["pillar_broken", "bones", "candelabra", "bones", "pillar"],
        "top": ["crystal_blue", "crystal_purple", "pillar", "crystal_blue"],
    }
    corner_light = {"left": "lantern", "right": "candelabra", "top": "brazier_blue"}
    for d in DUNGEONS:
        th = d["theme"]
        for rid, kind, r, _ in d["rooms"]:
            doors = [door_cells(r, c) for c, a, b in d["corridors"] if rid in (a, b)]

            def near_door(x, y):
                return any(dd and abs(x - dd.cx + 0.5) < 3.2 and abs(y - dd.cy + 0.5) < 3.2 for dd in doors)

            # 윗벽 횃불 (벽 앞면에 붙인다)
            for x in (r.x0 + 2, r.x1 - 2) if r.w < 16 else (r.x0 + 2, int(r.cx), r.x1 - 2):
                if not near_door(x, r.y1 + 1):
                    add("torch", x + 0.5, r.y1 + 1.15, flat=True)
            if th == "right":
                for x in (r.x0 + 4, r.x1 - 4):
                    if not near_door(x, r.y1 + 1):
                        add("banner", x + 0.5, r.y1 + 0.55, flat=True)
            # 보스방: 네 구석 화로
            if kind == "boss":
                br = {"left": "brazier", "right": "brazier_red", "top": "brazier_blue"}[th]
                for (x, y) in ((r.x0 + 1.5, r.y0 + 1.2), (r.x1 - 0.5, r.y0 + 1.2), (r.x0 + 1.5, r.y1 - 0.4), (r.x1 - 0.5, r.y1 - 0.4)):
                    add(br, x, y)
                continue
            # 구석 조명
            for (x, y) in ((r.x0 + 0.6, r.y1 - 0.2), (r.x1 + 0.4, r.y1 - 0.2)):
                if not near_door(x, y):
                    add(corner_light[th], x, y)
            # 테마 장식: 가장자리 칸에만
            edge = [(x, r.y0) for x in range(r.x0, r.x1 + 1)] + [(r.x0, y) for y in range(r.y0, r.y1 + 1)] + \
                   [(r.x1, y) for y in range(r.y0, r.y1 + 1)]
            rnd.shuffle(edge)
            placed = []
            for (x, y) in edge:
                if len(placed) >= (3 if kind == "mob" else 2):
                    break
                if near_door(x, y) or any(abs(x - px) < 3 and abs(y - py) < 3 for px, py in placed):
                    continue
                if y == r.y1:
                    continue
                item = rnd.choice(theme_items[th])
                add(item, x + 0.5, y + 0.25)
                placed.append((x, y))

    # ---- 마왕의 심연
    for (x, y) in ((ARENA.x0 + 1.5, ARENA.y0 + 1.2), (ARENA.x1 - 0.5, ARENA.y0 + 1.2),
                   (ARENA.x0 + 1.5, ARENA.y1 - 0.4), (ARENA.x1 - 0.5, ARENA.y1 - 0.4)):
        add("brazier_red", x, y)
    for k in range(10):
        a = k / 10 * math.pi * 2
        add("flesh_spike", ARENA.cx + math.cos(a) * 11.2, ARENA.cy + math.sin(a) * 7.2)
    return out


# =====================================================================================
# TMX
# =====================================================================================
def write_tmx(path, ground, walls, decor):
    rnd = random.Random(2026)
    objs = []
    oid = [1]

    def add(name, typ, x, y, w=None, h=None, props_=None):
        s = f'  <object id="{oid[0]}" name="{name}" type="{typ}" x="{x:g}" y="{y:g}"'
        if w is not None:
            s += f' width="{w:g}" height="{h:g}"'
        oid[0] += 1
        inner = ""
        if props_:
            inner += "   <properties>\n" + "".join(
                f'    <property name="{k}" value="{v}"/>\n' for k, v in props_.items()) + "   </properties>\n"
        if w is None:
            inner += "   <point/>\n"
        s += (">\n" + inner + "  </object>\n") if inner else "/>\n"
        objs.append(s)

    def add_rect(name, typ, r, p=None):
        add(name, typ, r.x0 * T, (H - 1 - r.y1) * T, r.w * T, r.h * T, p)

    def add_point(name, typ, tx, ty, p=None):
        add(name, typ, round(tx * T, 2), round((H - ty) * T, 2), None, None, p)

    add_rect("camp", "camp", CAMP)
    add_rect("bed", "bed", BED)
    add_rect("forge", "forge", FORGE)
    add_point("sigil", "sigil", *SIGIL)
    add_point("spawn", "spawn", *SPAWN)
    add_rect("arena", "arena", ARENA)
    add_point("arena_land", "arena_land", ARENA.cx, ARENA.y0 + 5)
    add_point("final_boss", "final_boss", ARENA.cx, ARENA.y1 - 7)
    add_point("arena_portal", "arena_portal", ARENA.cx, ARENA.cy - 1)

    for d in DUNGEONS:
        for order, (rid, kind, r, parent) in enumerate(d["rooms"]):
            entry = None
            for c, a, b in d["corridors"]:
                if {a, b} == {rid, parent}:
                    dr = door_cells(r, c)
                    ix, iy = inward(r, dr)
                    entry = (dr.cx + ix * 1.6, dr.cy + iy * 1.6, dr.cx - ix * 1.6, dr.cy - iy * 1.6)
            add_rect(rid, "room", r, {"kind": kind, "dungeon": d["id"], "parent": parent, "order": order,
                                     "entryX": f"{entry[0]:g}", "entryY": f"{entry[1]:g}",
                                     "outX": f"{entry[2]:g}", "outY": f"{entry[3]:g}"})
            for i, (c, a, b) in enumerate(d["corridors"]):
                if rid in (a, b):
                    dr = door_cells(r, c)
                    if dr:
                        add_rect(f"{rid}_door{i}", "door", dr, {"room": rid})
            if kind == "mob":
                ex, ey = entry[0], entry[1]
                pts = set()
                tries = 0
                while len(pts) < d["mobs"] and tries < 2000:
                    tries += 1
                    x, y = rnd.randint(r.x0 + 2, r.x1 - 2), rnd.randint(r.y0 + 2, r.y1 - 2)
                    if math.hypot(x + 0.5 - ex, y + 0.5 - ey) < 5:
                        continue
                    if any(abs(x - px) < 2 and abs(y - py) < 2 for px, py in pts):
                        continue
                    pts.add((x, y))
                for x, y in sorted(pts):
                    add_point(f"{rid}_m", "monster", x + 0.5, y + 0.5, {"room": rid, "boss": "false"})
            elif kind == "boss":
                add_point(f"{rid}_boss", "monster", r.cx, r.y1 - 4, {"room": rid, "boss": "true"})
                add_point(f"{rid}_portal", "portal", r.cx, r.cy - 1.5, {"room": rid})
            elif kind == "ore":
                doors = [door_cells(r, c) for c, a, b in d["corridors"] if rid in (a, b)]
                for x in range(r.x0 + 1, r.x1):
                    for y in range(r.y0 + 1, r.y1):
                        if (x + y) % 2:
                            continue
                        if any(abs(x - dd.cx + 0.5) < 3 and abs(y - dd.cy + 0.5) < 3 for dd in doors):
                            continue
                        add_point(f"{rid}_v", "vein_spot", x + 0.5, y + 0.5, {"room": rid})
        for i, (c, a, b) in enumerate(d["corridors"]):
            add_rect(f"{d['id']}_c{i}", "corridor", c, {"a": a, "b": b})
        if d["gate"]:
            add_rect(f"gate_{d['id']}", "gate", d["gate"], {"dungeon": d["id"]})

    for (sprite, tx, ty, flat) in decor:
        p = {"sprite": sprite, "flat": str(flat).lower(), "solid": str(sprite in DECOR_SOLID and not flat).lower()}
        if sprite in LIGHTS:
            p["light"] = LIGHTS[sprite][0]
            p["lightColor"] = LIGHTS[sprite][1]
        add_point(sprite, "decor", tx, ty, p)

    def csv(layer):
        return ",\n".join(",".join(str(v) for v in layer[y]) for y in range(H - 1, -1, -1))

    n = 26
    tmx = f"""<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.10.2" orientation="orthogonal" renderorder="right-down" width="{W}" height="{H}" tilewidth="{T}" tileheight="{T}" infinite="0" nextlayerid="4" nextobjectid="{oid[0]}">
 <tileset firstgid="1" name="tiles" tilewidth="{T}" tileheight="{T}" tilecount="{n}" columns="{n}">
  <image source="tiles.png" width="{n * T}" height="{T}"/>
 </tileset>
 <layer id="1" name="ground" width="{W}" height="{H}">
  <data encoding="csv">
{csv(ground)}
</data>
 </layer>
 <layer id="2" name="walls" width="{W}" height="{H}">
  <data encoding="csv">
{csv(walls)}
</data>
 </layer>
 <objectgroup id="3" name="objects">
{''.join(objs)} </objectgroup>
</map>
"""
    with open(path, "w", encoding="utf-8") as f:
        f.write(tmx)


# =====================================================================================
# 무기·광물·구슬·기타 작은 그림
# =====================================================================================
OUT = (14, 10, 18, 255)


def sword(blade, guard, glow=None, big=False, gem=None):
    c = Canvas(32, 32)
    if glow:
        c.glow(18, 14, 15, glow, 0.45)
    w = 4 if big else 3
    c.line([(9, 22), (27, 4)], blade, w)
    c.line([(10, 21), (27, 4)], shade(blade, 60), 1)
    c.line([(4, 17), (14, 27)], guard, 3)
    c.line([(3, 28), (8, 23)], rgb("#5a3a24"), 3)
    c.ellipse((1, 26, 5, 30), guard)
    if gem:
        c.ellipse((8, 20, 12, 24), gem)
    return c.outline(OUT)


def vein(color):
    c = Canvas(32, 32)
    c.poly([(2, 30), (4, 14), (12, 4), (22, 6), (30, 18), (30, 30)], rgb("#5e5660"))
    c.poly([(6, 14), (12, 6), (20, 8)], rgb("#7a7280"))
    for p in ((10, 18), (18, 12), (22, 22), (12, 24), (17, 19)):
        c.rect((p[0], p[1], p[0] + 2, p[1] + 2), color, hi=60)
    c.outline(OUT)
    c.glow(16, 18, 10, color, 0.2)
    return c


def ore_icon(color):
    c = Canvas(24, 24)
    c.poly([(4, 18), (6, 6), (14, 2), (20, 8), (18, 20)], color, hi=60, lo=-50)
    return c.outline(OUT)


def orb(color):
    c = Canvas(24, 24)
    c.glow(12, 12, 12, color, 0.5)
    c.ellipse((3, 3, 20, 20), color, hi=70, lo=-50)
    c.flat_ellipse((7, 6, 10, 9), (255, 255, 255, 220))
    return c.outline(OUT)


def portal(frame):
    c = Canvas(48, 48)
    c.glow(24, 24, 24, rgb("#60b0ff"), 0.4)
    d = ImageDraw.Draw(c.img)
    for i, r in enumerate((21, 17, 13, 9, 5)):
        a = (frame * 2 + i) % 5
        col = [(80, 200, 255), (120, 160, 255), (170, 120, 255), (120, 220, 255), (230, 245, 255)][a]
        d.ellipse([24 - r, 24 - r * 0.8, 24 + r, 24 + r * 0.8], outline=col + (255,), width=2)
    return c


def door(color, bars):
    c = Canvas(32, 32)
    c.flat_rect((0, 0, 31, 31), rgb("#1c1416"))
    for x in (3, 11, 19, 27):
        c.rect((x, 0, x + 2, 31), bars, hi=40, lo=-30)
    c.rect((0, 6, 31, 8), color, hi=30, lo=-30); c.rect((0, 23, 31, 25), color, hi=30, lo=-30)
    return c


def bolt(color, size=16):
    c = Canvas(size, size)
    c.glow(size / 2, size / 2, size / 2, color, 0.9)
    c.flat_ellipse((size * 0.3, size * 0.3, size * 0.7, size * 0.7), (255, 255, 255, 255))
    return c


def bone_spike():
    c = Canvas(40, 48)
    b = rgb("#e6dec8")
    for (x, h) in ((8, 30), (18, 44), (28, 34), (34, 22)):
        c.poly([(x - 5, 47), (x, 47 - h), (x + 5, 47)], b, hi=40, lo=-40)
    return c.outline(OUT)


def ice_chunk():
    c = Canvas(24, 24)
    c.poly([(3, 14), (8, 3), (18, 2), (22, 12), (15, 22), (6, 21)], rgb("#9ad8ff"), hi=60, lo=-40)
    c.line([(9, 6), (14, 18)], (255, 255, 255, 255))
    return c.outline(OUT)


def fire_bolt():
    c = Canvas(24, 24)
    c.glow(12, 12, 12, rgb("#ff7020"), 0.9)
    c.flat_ellipse((7, 7, 17, 17), rgb("#ffd060"))
    c.flat_ellipse((9, 9, 15, 15), (255, 255, 230, 255))
    return c


def shadow():
    c = Canvas(48, 14)
    ImageDraw.Draw(c.img).ellipse([0, 0, 47, 13], fill=(0, 0, 0, 95))
    return c


def skill_icon(kind):
    c = Canvas(48, 48)
    if kind == "whirl":
        for a in range(0, 360, 30):
            r = math.radians(a)
            c.line([(24 + 8 * math.cos(r), 24 + 8 * math.sin(r)), (24 + 20 * math.cos(r + 0.4), 24 + 20 * math.sin(r + 0.4))],
                   rgb("#e8ecff"), 2)
        c.ellipse((16, 16, 32, 32), rgb("#ffd060"))
    elif kind == "dash":
        c.poly([(42, 24), (22, 8), (22, 18), (6, 18), (6, 30), (22, 30), (22, 40)], rgb("#ffd060"))
        for y in (12, 24, 36):
            c.line([(2, y), (10, y)], rgb("#c0c8e0"), 2)
    elif kind == "dodge":
        d = ImageDraw.Draw(c.img)
        d.arc([6, 6, 42, 42], 200, 520, fill=rgb("#90e0ff"), width=5)
        c.poly([(36, 6), (44, 16), (30, 16)], rgb("#90e0ff"))
    elif kind == "guard":
        c.poly([(24, 4), (42, 10), (40, 28), (24, 44), (8, 28), (6, 10)], rgb("#8aa0c8"), hi=50)
        c.poly([(24, 10), (36, 14), (34, 27), (24, 38), (14, 27), (12, 14)], rgb("#3a5090"))
        c.line([(24, 12), (24, 34)], rgb("#e0c060"), 3)
    return c.outline(OUT)


def status_icon(kind):
    c = Canvas(28, 28)
    if kind == "poison":
        c.ellipse((4, 6, 24, 26), rgb("#5ad060"))
        c.flat_ellipse((9, 11, 12, 14), rgb("#1a3a1a")); c.flat_ellipse((16, 11, 19, 14), rgb("#1a3a1a"))
        c.line([(10, 20), (18, 20)], rgb("#1a3a1a"), 2)
    elif kind == "burn":
        c.poly([(6, 26), (8, 12), (12, 16), (14, 2), (18, 14), (22, 10), (22, 26)], rgb("#ff7020"))
        c.poly([(10, 26), (14, 14), (18, 26)], rgb("#ffe080"))
    elif kind == "shock":
        c.poly([(16, 2), (6, 16), (13, 16), (10, 26), (22, 10), (15, 10)], rgb("#ffe040"))
    elif kind == "freeze":
        for a in range(0, 180, 60):
            r = math.radians(a)
            c.line([(14 - 11 * math.cos(r), 14 - 11 * math.sin(r)), (14 + 11 * math.cos(r), 14 + 11 * math.sin(r))],
                   rgb("#a0e0ff"), 3)
    return c.outline(OUT)


# =====================================================================================
def save(name, frames, out, manifest):
    img = strip(frames)
    img.save(f"{out}/{name}.png")
    manifest[name] = len(frames)


def make_sprites(out):
    os.makedirs(out, exist_ok=True)
    m = {}
    for d in ("down", "up", "side"):
        save(f"hero_{d}", [chars.hero(d, f) for f in range(3)], out, m)
    save("mob_left", [chars.zombie_miner(f) for f in range(2)], out, m)
    save("mob_right", [chars.skeleton_warrior(f) for f in range(2)], out, m)
    save("mob_top", [chars.drakeling(f) for f in range(2)], out, m)
    save("boss_left", [chars.zombie_king(p) for p in range(3)], out, m)       # 대기, 대기, 돌진
    save("boss_right", [chars.lich(p) for p in range(3)], out, m)             # 대기, 대기, 시전
    save("boss_top", [chars.dragon(p) for p in range(5)], out, m)             # 대기, 대기, 아래 포효, 위 포효, 꼬리 들기
    save("demon_king", [chars.demon_king(f) for f in range(2)], out, m)
    save("demon_eldritch", [chars.eldritch(f) for f in range(2)], out, m)

    swords = {
        "sealed": sword(rgb("#8a8a94"), rgb("#6a5a44"), gem=rgb("#4a4a52")),
        "holy": sword(rgb("#fff6dc"), rgb("#f0c850"), glow=rgb("#fff0a0"), big=True, gem=rgb("#60c8ff")),
        "blackiron": sword(rgb("#4a4e60"), rgb("#3a3a46")),
        "blackiron_aw": sword(rgb("#2a2a40"), rgb("#9a60ff"), glow=rgb("#a070ff"), big=True, gem=rgb("#c090ff")),
        "bloodmoon": sword(rgb("#b02a40"), rgb("#5a1a24")),
        "bloodmoon_aw": sword(rgb("#e0203c"), rgb("#f0c060"), glow=rgb("#ff4060"), big=True, gem=rgb("#ff8090")),
        "sky": sword(rgb("#80c0ff"), rgb("#d0d8f0")),
        "sky_aw": sword(rgb("#c8eeff"), rgb("#fff080"), glow=rgb("#80e0ff"), big=True, gem=rgb("#ffffff")),
    }
    for k, c in swords.items():
        save(f"weapon_{k}", [c], out, m)
    ores = {"blackiron": rgb("#6a6a90"), "bloodmoon": rgb("#e0304a"), "sky": rgb("#70c8ff")}
    for k, col in ores.items():
        save(f"vein_{k}", [vein(col)], out, m)
        save(f"ore_{k}", [ore_icon(col)], out, m)
    save("orb_left", [orb(rgb("#a070ff"))], out, m)
    save("orb_right", [orb(rgb("#ff4060"))], out, m)
    save("orb_top", [orb(rgb("#70e0ff"))], out, m)

    save("portal", [portal(i) for i in range(4)], out, m)
    save("door", [door(rgb("#8a6a40"), rgb("#6a6a74"))], out, m)
    save("gate", [door(rgb("#a02a2a"), rgb("#c03a3a"))], out, m)
    save("bolt_blue", [bolt(rgb("#70c0ff"))], out, m)
    save("bolt_green", [bolt(rgb("#50ff90"))], out, m)
    save("bolt_purple", [bolt(rgb("#c060ff"))], out, m)
    save("bolt_red", [bolt(rgb("#ff4060"))], out, m)
    save("fire_bolt", [fire_bolt()], out, m)
    save("ice_chunk", [ice_chunk()], out, m)
    save("bone_spike", [bone_spike()], out, m)
    save("shadow", [shadow()], out, m)
    for k in ("whirl", "dash", "dodge", "guard"):
        save(f"skill_{k}", [skill_icon(k)], out, m)
    for k in ("poison", "burn", "shock", "freeze"):
        save(f"status_{k}", [status_icon(k)], out, m)

    # 캠프 시설·장식
    save("bed", [props.bed()], out, m)
    save("forge", [props.forge(f) for f in range(3)], out, m)
    save("tent", [props.tent()], out, m)
    save("campfire", [props.campfire(f) for f in range(3)], out, m)
    save("torch", [props.torch(f) for f in range(3)], out, m)
    save("brazier", [props.brazier(f) for f in range(3)], out, m)
    save("brazier_blue", [props.brazier(f, "#5a8aff", "#c8e0ff") for f in range(3)], out, m)
    save("brazier_red", [props.brazier(f, "#ff3020", "#ffc080") for f in range(3)], out, m)
    save("lantern", [props.lantern(f) for f in range(3)], out, m)
    save("candelabra", [props.candelabra(f) for f in range(3)], out, m)
    save("barrel", [props.barrel()], out, m)
    save("crate", [props.crate()], out, m)
    save("weapon_rack", [props.weapon_rack()], out, m)
    save("minecart", [props.minecart()], out, m)
    save("rocks", [props.rocks()], out, m)
    save("banner", [props.banner()], out, m)
    save("pillar", [props.pillar(False)], out, m)
    save("pillar_broken", [props.pillar(True)], out, m)
    save("bones", [props.bones()], out, m)
    save("crystal_blue", [props.crystal("#70c8ff")], out, m)
    save("crystal_purple", [props.crystal("#c070ff")], out, m)
    save("flesh_spike", [props.flesh_spike()], out, m)
    with open(f"{out}/manifest.json", "w", encoding="utf-8") as f:
        json.dump(m, f, ensure_ascii=False, indent=1, sort_keys=True)


if __name__ == "__main__":
    os.makedirs(f"{ROOT}/maps", exist_ok=True)
    ts, n = props.tileset()
    ts.save(f"{ROOT}/maps/tiles.png")
    g, w, _ = build_layers()
    write_tmx(f"{ROOT}/maps/world.tmx", g, w, decorate())
    sprites = f"{ROOT}/sprites"
    for f in os.listdir(sprites) if os.path.isdir(sprites) else []:
        if f.endswith(".png"):
            os.remove(os.path.join(sprites, f))
    make_sprites(sprites)
    print("done", n, "tiles")
