"""
용사가 다한다 - 임시(placeholder) 에셋과 통합 맵 생성기.

만드는 것
  assets/maps/tiles.png    32px 타일셋
  assets/maps/world.tmx    Tiled 통합 맵 (캠프 + 던전 3곳 + 최종 전장)
  assets/sprites/*.png     캐릭터·무기·오브젝트 임시 그림

진짜 그림이 오면 같은 파일 이름·같은 프레임 크기로 덮어쓰면 된다.
맵은 Tiled에서 world.tmx를 직접 고쳐도 되지만, 이 스크립트를 다시 돌리면 덮어쓴다.

실행: python3 tools/gen_assets.py
"""
import math
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")
T = 32  # 타일 크기(px)

# =====================================================================================
# 타일셋
# =====================================================================================
(VOID, CAMP_FLOOR, CAMP_WALL, L_FLOOR, L_WALL, R_FLOOR, R_WALL, T_FLOOR, T_WALL,
 A_FLOOR, A_WALL, CAMP_RUG) = range(12)

PALETTE = {
    VOID: ((14, 12, 20), None),
    CAMP_FLOOR: ((118, 104, 86), (104, 92, 76)),
    CAMP_WALL: ((92, 70, 52), (128, 98, 72)),
    L_FLOOR: ((84, 70, 60), (72, 60, 52)),
    L_WALL: ((48, 40, 38), (76, 64, 58)),
    R_FLOOR: ((92, 62, 70), (80, 52, 60)),
    R_WALL: ((54, 30, 40), (90, 52, 64)),
    T_FLOOR: ((70, 76, 112), (60, 66, 98)),
    T_WALL: ((34, 36, 64), (64, 70, 118)),
    A_FLOOR: ((44, 20, 30), (60, 26, 38)),
    A_WALL: ((20, 8, 14), (70, 20, 30)),
    CAMP_RUG: ((132, 116, 94), (146, 128, 104)),
}
WALL_TILES = {CAMP_WALL, L_WALL, R_WALL, T_WALL, A_WALL}


def make_tileset(path):
    img = Image.new("RGBA", (len(PALETTE) * T, T), (0, 0, 0, 0))
    rnd = random.Random(11)
    for idx, (base, accent) in PALETTE.items():
        tile = Image.new("RGBA", (T, T), base + (255,))
        d = ImageDraw.Draw(tile)
        dark = tuple(max(0, c - 22) for c in base) + (255,)
        if idx in WALL_TILES:
            # 벽: 위 면(밝음) + 앞 면 벽돌
            d.rectangle([0, 0, T - 1, 9], fill=accent + (255,))
            d.line([0, 10, T - 1, 10], fill=dark)
            for row, y in enumerate(range(14, T, 7)):
                d.line([0, y, T - 1, y], fill=dark)
                off = 0 if row % 2 == 0 else 8
                for x in range(off, T, 16):
                    d.line([x, y - 6, x, y], fill=dark)
        elif idx == VOID:
            pass
        else:
            # 바닥: 큰 돌판 줄눈 + 잔무늬
            d.line([0, T - 1, T - 1, T - 1], fill=dark)
            d.line([T - 1, 0, T - 1, T - 1], fill=dark)
            for _ in range(7):
                x, y = rnd.randrange(2, T - 3), rnd.randrange(2, T - 3)
                d.rectangle([x, y, x + 1, y], fill=accent + (255,))
        img.paste(tile, (idx * T, 0))
    img.save(path)


# =====================================================================================
# 맵 배치 (타일 좌표, 생성기 안에서는 y가 아래→위)
# =====================================================================================
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

    def contains(self, x, y):
        return self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1


CAMP = R(66, 30, 83, 43)
ARENA = R(62, 4, 87, 21)

# 던전: (방 id, 종류, 사각형, 부모 방 id), 통로: (사각형, a, b)
DUNGEONS = [
    dict(id="left", floor=L_FLOOR, wall=L_WALL, mobs=4, gate=None,
         rooms=[("L1", "mob", R(46, 30, 59, 41), "camp"),
                ("L2", "mob", R(26, 30, 39, 41), "L1"),
                ("L3", "ore", R(28, 14, 39, 23), "L2"),
                ("L4", "mob", R(6, 30, 19, 41), "L2"),
                ("L5", "boss", R(4, 48, 21, 61), "L4")],
         corridors=[(R(60, 35, 65, 37), "camp", "L1"), (R(40, 35, 45, 37), "L1", "L2"),
                    (R(32, 24, 34, 29), "L2", "L3"), (R(20, 35, 25, 37), "L2", "L4"),
                    (R(11, 42, 13, 47), "L4", "L5")]),
    dict(id="right", floor=R_FLOOR, wall=R_WALL, mobs=4, gate=R(84, 35, 84, 37),
         rooms=[("R1", "mob", R(90, 30, 103, 41), "camp"),
                ("R2", "ore", R(92, 14, 103, 23), "R1"),
                ("R3", "mob", R(110, 30, 123, 41), "R1"),
                ("R4", "mob", R(130, 30, 143, 41), "R3"),
                ("R5", "ore", R(132, 14, 143, 23), "R4"),
                ("R6", "boss", R(128, 48, 145, 61), "R4")],
         corridors=[(R(84, 35, 89, 37), "camp", "R1"), (R(96, 24, 98, 29), "R1", "R2"),
                    (R(104, 35, 109, 37), "R1", "R3"), (R(124, 35, 129, 37), "R3", "R4"),
                    (R(136, 24, 138, 29), "R4", "R5"), (R(136, 42, 138, 47), "R4", "R6")]),
    dict(id="top", floor=T_FLOOR, wall=T_WALL, mobs=5, gate=R(73, 44, 75, 44),
         rooms=[("T1", "mob", R(68, 50, 81, 61), "camp"),
                ("T2", "mob", R(68, 68, 81, 79), "T1"),
                ("T3", "ore", R(48, 69, 59, 78), "T2"),
                ("T4", "mob", R(68, 86, 81, 97), "T2"),
                ("T5", "ore", R(90, 87, 101, 96), "T4"),
                ("T6", "mob", R(68, 104, 81, 115), "T4"),
                ("T7", "boss", R(64, 122, 85, 135), "T6")],
         corridors=[(R(73, 44, 75, 49), "camp", "T1"), (R(73, 62, 75, 67), "T1", "T2"),
                    (R(60, 73, 67, 75), "T2", "T3"), (R(73, 80, 75, 85), "T2", "T4"),
                    (R(82, 90, 89, 92), "T4", "T5"), (R(73, 98, 75, 103), "T4", "T6"),
                    (R(73, 116, 75, 121), "T6", "T7")]),
]

BED = R(68, 40, 68, 41)        # 1x2 타일
FORGE = R(80, 41, 81, 41)      # 2x1 타일
SIGIL = (75, 37)               # 캠프 가운데 문양 (타일 경계 좌표)
SPAWN = (75, 33)


def build_layers():
    ground = [[VOID] * W for _ in range(H)]
    floor = [[False] * W for _ in range(H)]
    owner = [[None] * W for _ in range(H)]

    def carve(rect, tile, own):
        for x, y in rect.cells():
            ground[y][x] = tile
            floor[y][x] = True
            owner[y][x] = own

    carve(CAMP, CAMP_FLOOR, "camp")
    for x, y in R(71, 33, 78, 40).cells():
        ground[y][x] = CAMP_RUG
    carve(ARENA, A_FLOOR, "arena")
    for d in DUNGEONS:
        for _, _, r, _ in d["rooms"]:
            carve(r, d["floor"], d["id"])
        for c, _, _ in d["corridors"]:
            carve(c, d["floor"], d["id"])

    walls = [[0] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if floor[y][x]:
                continue
            near = None
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < W and 0 <= ny < H and floor[ny][nx]:
                        near = owner[ny][nx]
            if near:
                walls[y][x] = {"camp": CAMP_WALL, "arena": A_WALL, "left": L_WALL,
                               "right": R_WALL, "top": T_WALL}[near]
    return ground, walls


def door_cells(room, corridor):
    ring = room.ring()
    cells = [(x, y) for x, y in corridor.cells()
             if (x in (ring.x0, ring.x1) and ring.y0 <= y <= ring.y1)
             or (y in (ring.y0, ring.y1) and ring.x0 <= x <= ring.x1)]
    if not cells:
        return None
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    return R(min(xs), min(ys), max(xs), max(ys))


def inward(room, door):
    """문이 방의 어느 변에 있는지 → 방 안쪽 방향 (dx, dy)."""
    ring = room.ring()
    if door.x0 == ring.x0 and door.x1 == ring.x0: return (1, 0)
    if door.x0 == ring.x1 and door.x1 == ring.x1: return (-1, 0)
    if door.y0 == ring.y0 and door.y1 == ring.y0: return (0, 1)
    return (0, -1)


# =====================================================================================
# TMX 쓰기
# =====================================================================================
def write_tmx(path, ground, walls):
    rnd = random.Random(2026)
    objs = []
    oid = [1]

    def px_rect(r):
        return r.x0 * T, (H - 1 - r.y1) * T, r.w * T, r.h * T

    def add(name, typ, x, y, w=None, h=None, props=None):
        s = f'  <object id="{oid[0]}" name="{name}" type="{typ}" x="{x:g}" y="{y:g}"'
        if w is not None:
            s += f' width="{w:g}" height="{h:g}"'
        oid[0] += 1
        inner = ""
        if props:
            inner += "   <properties>\n" + "".join(
                f'    <property name="{k}" value="{v}"/>\n' for k, v in props.items()) + "   </properties>\n"
        if w is None:
            inner += "   <point/>\n"
        s += (">\n" + inner + "  </object>\n") if inner else "/>\n"
        objs.append(s)

    def add_rect(name, typ, r, props=None):
        x, y, w, h = px_rect(r)
        add(name, typ, x, y, w, h, props)

    def add_point(name, typ, tx, ty, props=None):
        # (tx, ty) 타일 좌표(실수 가능, y-up) → Tiled 픽셀(y-down)
        add(name, typ, tx * T, (H - ty) * T, None, None, props)

    add_rect("camp", "camp", CAMP)
    add_rect("bed", "bed", BED)
    add_rect("forge", "forge", FORGE)
    add_point("sigil", "sigil", *SIGIL)
    add_point("spawn", "spawn", *SPAWN)
    add_rect("arena", "arena", ARENA)
    add_point("arena_land", "arena_land", ARENA.cx, ARENA.y0 + 4)
    add_point("final_boss", "final_boss", ARENA.cx, ARENA.y1 - 4)
    add_point("arena_portal", "arena_portal", ARENA.cx, ARENA.cy - 1)

    for d in DUNGEONS:
        rooms = {rid: (kind, r, parent) for rid, kind, r, parent in d["rooms"]}
        for order, (rid, kind, r, parent) in enumerate(d["rooms"]):
            # 부모 쪽 통로 → 입구 문
            entry = None
            for c, a, b in d["corridors"]:
                if {a, b} == {rid, parent}:
                    dr = door_cells(r, c)
                    ix, iy = inward(r, dr)
                    cx, cy = dr.cx, dr.cy
                    entry = (cx + ix * 1.6, cy + iy * 1.6, cx - ix * 1.6, cy - iy * 1.6)
            props = {"kind": kind, "dungeon": d["id"], "parent": parent, "order": order,
                     "entryX": f"{entry[0]:g}", "entryY": f"{entry[1]:g}",
                     "outX": f"{entry[2]:g}", "outY": f"{entry[3]:g}"}
            add_rect(rid, "room", r, props)

            for i, (c, a, b) in enumerate(d["corridors"]):
                if rid in (a, b):
                    dr = door_cells(r, c)
                    if dr:
                        add_rect(f"{rid}_door{i}", "door", dr, {"room": rid})

            if kind == "mob":
                ex, ey = entry[0], entry[1]
                pts = set()
                tries = 0
                while len(pts) < d["mobs"] and tries < 1000:
                    tries += 1
                    x = rnd.randint(r.x0 + 2, r.x1 - 2)
                    y = rnd.randint(r.y0 + 2, r.y1 - 2)
                    if math.hypot(x + 0.5 - ex, y + 0.5 - ey) < 5:
                        continue
                    if any(abs(x - px) < 2 and abs(y - py) < 2 for px, py in pts):
                        continue
                    pts.add((x, y))
                for x, y in sorted(pts):
                    add_point(f"{rid}_m", "monster", x + 0.5, y + 0.5, {"room": rid, "boss": "false"})
            elif kind == "boss":
                add_point(f"{rid}_boss", "monster", r.cx, r.y1 - 3, {"room": rid, "boss": "true"})
                add_point(f"{rid}_portal", "portal", r.cx, r.cy - 1, {"room": rid})
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

    def csv(layer):
        return ",\n".join(",".join(str(v) for v in layer[y]) for y in range(H - 1, -1, -1))

    g = [[t + 1 for t in row] for row in ground]
    wl = [[(t + 1) if t else 0 for t in row] for row in walls]
    n = len(PALETTE)
    tmx = f"""<?xml version="1.0" encoding="UTF-8"?>
<map version="1.10" tiledversion="1.10.2" orientation="orthogonal" renderorder="right-down" width="{W}" height="{H}" tilewidth="{T}" tileheight="{T}" infinite="0" nextlayerid="4" nextobjectid="{oid[0]}">
 <tileset firstgid="1" name="tiles" tilewidth="{T}" tileheight="{T}" tilecount="{n}" columns="{n}">
  <image source="tiles.png" width="{n * T}" height="{T}"/>
 </tileset>
 <layer id="1" name="ground" width="{W}" height="{H}">
  <data encoding="csv">
{csv(g)}
</data>
 </layer>
 <layer id="2" name="walls" width="{W}" height="{H}">
  <data encoding="csv">
{csv(wl)}
</data>
 </layer>
 <objectgroup id="3" name="objects">
{''.join(objs)} </objectgroup>
</map>
"""
    with open(path, "w", encoding="utf-8") as f:
        f.write(tmx)


# =====================================================================================
# 스프라이트 (16px 기준으로 그리고 2배 확대 → 32px 픽셀 아트)
# =====================================================================================
OUT = (18, 14, 22, 255)


def outline(img):
    src = img.load()
    out = img.copy()
    dst = out.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3] > 0:
                        dst[x, y] = OUT
                        break
    return out


def up2(img, k=2):
    return img.resize((img.width * k, img.height * k), Image.NEAREST)


def strip(frames):
    w, h = frames[0].size
    img = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        img.paste(f, (i * w, 0))
    return img


def shade(c, k):
    return tuple(max(0, min(255, int(v + k))) for v in c[:3]) + (255,)


# ---- 용사 (16x16 → 32x32), 방향: down / up / side(오른쪽)
SKIN = (246, 206, 160, 255)
HAIR = (226, 176, 52, 255)
TUNIC = (54, 104, 196, 255)
CAPE = (196, 46, 52, 255)
BOOT = (78, 54, 40, 255)


def hero(direction, step):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    bob = 0 if step == 0 else -1
    y = 1 + bob
    if direction == "up":
        d.rectangle([4, y + 8, 11, y + 12], fill=CAPE)
    d.rectangle([5, y + 7, 10, y + 12], fill=TUNIC)
    d.rectangle([5, y + 1, 10, y + 6], fill=SKIN)
    if direction == "down":
        d.rectangle([4, y, 11, y + 2], fill=HAIR)
        d.point((6, y + 4), fill=(30, 30, 40, 255))
        d.point((9, y + 4), fill=(30, 30, 40, 255))
        d.rectangle([4, y + 7, 4, y + 10], fill=CAPE)
        d.rectangle([11, y + 7, 11, y + 10], fill=CAPE)
    elif direction == "up":
        d.rectangle([4, y, 11, y + 5], fill=HAIR)
    else:  # side, 오른쪽을 본다
        d.rectangle([4, y, 10, y + 2], fill=HAIR)
        d.rectangle([4, y + 3, 6, y + 4], fill=HAIR)
        d.point((9, y + 4), fill=(30, 30, 40, 255))
        d.rectangle([3, y + 7, 4, y + 11], fill=CAPE)
    lx, rx = 5, 9
    if step == 1:
        lx, rx = 4, 10
    elif step == 2:
        lx, rx = 6, 8
    d.rectangle([lx, 13, lx + 1, 14], fill=BOOT)
    d.rectangle([rx, 13, rx + 1, 14], fill=BOOT)
    return up2(outline(im))


# ---- 무기 아이콘 (16x16 → 32x32), 오른쪽 위를 향한 검. 손잡이 = 왼쪽 아래
def sword(blade, guard, glow=None, big=False):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if glow:
        d.line([4, 11, 14, 1], fill=glow[:3] + (110,), width=5)
    d.line([5, 10, 13, 2], fill=blade, width=3 if big else 2)
    d.line([6, 9, 13, 2], fill=shade(blade, 50), width=1)
    d.line([2, 9, 6, 13], fill=guard, width=2)
    d.line([2, 13, 4, 11], fill=(90, 60, 40, 255), width=2)
    return up2(outline(im))


# ---- 몬스터
def skeleton_miner(step):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    bone = (222, 216, 196, 255)
    d.rectangle([5, 1, 10, 6], fill=bone)
    d.rectangle([4, 0, 11, 2], fill=(200, 160, 40, 255))   # 안전모
    d.point((6, 4), fill=(20, 20, 20, 255)); d.point((9, 4), fill=(20, 20, 20, 255))
    d.rectangle([6, 7, 9, 11], fill=(110, 90, 70, 255))
    d.line([4, 8, 4, 11], fill=bone); d.line([11, 8, 12, 5], fill=bone)
    d.line([12, 5, 14, 3], fill=(120, 120, 130, 255), width=1)  # 곡괭이
    d.line([6 + step, 12, 6 + step, 15], fill=bone); d.line([9 - step, 12, 9 - step, 15], fill=bone)
    return up2(outline(im))


def cursed_knight(step):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    armor = (110, 44, 58, 255)
    d.rectangle([5, 1, 10, 6], fill=(80, 80, 92, 255))
    d.line([6, 4, 9, 4], fill=(255, 60, 60, 255))
    d.rectangle([4, 7, 11, 11], fill=armor)
    d.rectangle([2, 6, 4, 11], fill=(150, 150, 160, 255))  # 방패
    d.line([12, 10, 14, 2], fill=(200, 200, 210, 255))     # 검
    d.rectangle([5 + step, 12, 6 + step, 15], fill=shade(armor, -30))
    d.rectangle([9 - step, 12, 10 - step, 15], fill=shade(armor, -30))
    return up2(outline(im))


def tower_mage(step):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    robe = (70, 60, 160, 255)
    d.polygon([(7, 0), (4, 5), (11, 5)], fill=shade(robe, -20))
    d.rectangle([5, 5, 10, 7], fill=(40, 30, 60, 255))
    d.point((6, 6), fill=(140, 230, 255, 255)); d.point((9, 6), fill=(140, 230, 255, 255))
    d.polygon([(4, 8), (11, 8), (12 + step, 15), (3 - step, 15)], fill=robe)
    d.line([13, 4, 13, 14], fill=(150, 110, 70, 255))
    d.rectangle([12, 2, 14, 4], fill=(140, 230, 255, 255))
    return up2(outline(im))


def golem(step):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    rock = (110, 96, 84, 255)
    d.rectangle([8, 4, 23, 14], fill=rock)
    d.rectangle([5, 12, 26, 24], fill=shade(rock, -10))
    d.rectangle([1, 12 - step, 6, 24 - step], fill=shade(rock, 10))
    d.rectangle([25, 12 + step, 30, 24 + step], fill=shade(rock, 10))
    d.rectangle([12, 8, 14, 10], fill=(255, 170, 60, 255)); d.rectangle([18, 8, 20, 10], fill=(255, 170, 60, 255))
    d.rectangle([8, 25, 13, 30], fill=shade(rock, -25)); d.rectangle([18, 25, 23, 30], fill=shade(rock, -25))
    for p in ((10, 16), (20, 19), (15, 21)):
        d.rectangle([p[0], p[1], p[0] + 1, p[1] + 1], fill=(40, 40, 60, 255))
    return up2(outline(im))


def blood_lord(step):
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cape = (140, 20, 40, 255)
    d.polygon([(6, 10), (26, 10), (29 + step, 30), (3 - step, 30)], fill=cape)
    d.rectangle([11, 9, 20, 22], fill=(40, 30, 40, 255))
    d.rectangle([12, 1, 19, 8], fill=(220, 210, 220, 255))
    d.polygon([(11, 1), (13, -2), (15, 1)], fill=(220, 180, 60, 255))
    d.polygon([(16, 1), (18, -2), (20, 1)], fill=(220, 180, 60, 255))
    d.point((14, 5), fill=(255, 40, 40, 255)); d.point((17, 5), fill=(255, 40, 40, 255))
    d.line([24, 6, 24, 24], fill=(200, 40, 60, 255), width=2)
    return up2(outline(im))


def sky_dragon(step):
    im = Image.new("RGBA", (40, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = (100, 140, 230, 255)
    wing = 4 if step else 9
    d.polygon([(8, 16), (0, wing), (14, 12)], fill=shade(c, -40))
    d.polygon([(32, 16), (40, wing), (26, 12)], fill=shade(c, -40))
    d.ellipse([10, 12, 30, 28], fill=c)
    d.rectangle([15, 3, 25, 12], fill=c)
    d.point((17, 7), fill=(255, 240, 120, 255)); d.point((23, 7), fill=(255, 240, 120, 255))
    d.rectangle([13, 27, 16, 31], fill=shade(c, -40)); d.rectangle([24, 27, 27, 31], fill=shade(c, -40))
    return up2(outline(im))


def demon_king(step):
    im = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    body = (60, 20, 50, 255)
    d.polygon([(6, 18), (42, 18), (46 + step, 46), (2 - step, 46)], fill=(30, 8, 24, 255))
    d.rectangle([16, 14, 31, 36], fill=body)
    d.rectangle([17, 3, 30, 14], fill=(120, 40, 80, 255))
    d.polygon([(17, 4), (11, -2 + step), (19, 2)], fill=(230, 220, 200, 255))
    d.polygon([(30, 4), (36, -2 + step), (28, 2)], fill=(230, 220, 200, 255))
    d.rectangle([20, 7, 21, 8], fill=(255, 220, 40, 255)); d.rectangle([26, 7, 27, 8], fill=(255, 220, 40, 255))
    d.line([38, 8, 38, 40], fill=(160, 40, 220, 255), width=2)
    d.ellipse([35, 3, 41, 9], fill=(200, 80, 255, 255))
    return up2(outline(im))


# ---- 오브젝트
def vein(color):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(1, 15), (2, 7), (6, 2), (11, 3), (15, 9), (15, 15)], fill=(96, 88, 92, 255))
    d.polygon([(3, 8), (6, 3), (10, 4)], fill=(120, 112, 116, 255))
    for p in ((5, 9), (9, 6), (11, 11), (6, 12)):
        d.rectangle([p[0], p[1], p[0] + 1, p[1] + 1], fill=color)
        d.point((p[0], p[1]), fill=shade(color, 60))
    return up2(outline(im))


def ore_icon(color):
    im = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon([(2, 9), (3, 3), (7, 1), (10, 4), (9, 10)], fill=color)
    d.line([4, 4, 6, 3], fill=shade(color, 70))
    return up2(outline(im))


def orb(color):
    im = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([1, 1, 10, 10], fill=color)
    d.ellipse([3, 3, 5, 5], fill=shade(color, 90))
    return up2(outline(im))


def bed():
    im = Image.new("RGBA", (16, 30), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 2, 14, 28], fill=(120, 80, 50, 255))
    d.rectangle([2, 4, 13, 9], fill=(240, 240, 240, 255))
    d.rectangle([2, 10, 13, 27], fill=(70, 110, 180, 255))
    d.line([2, 14, 13, 14], fill=(90, 130, 200, 255))
    return up2(outline(im))


def forge():
    im = Image.new("RGBA", (32, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([1, 4, 14, 22], fill=(90, 70, 60, 255))           # 화덕
    d.rectangle([4, 12, 11, 19], fill=(255, 120, 40, 255))
    d.rectangle([5, 14, 10, 18], fill=(255, 210, 90, 255))
    d.rectangle([16, 10, 30, 14], fill=(80, 84, 96, 255))         # 모루
    d.polygon([(16, 10), (14, 12), (16, 14)], fill=(80, 84, 96, 255))
    d.rectangle([20, 15, 26, 18], fill=(64, 66, 76, 255))
    d.rectangle([18, 19, 28, 22], fill=(64, 66, 76, 255))
    d.line([17, 10, 29, 10], fill=(150, 156, 170, 255))
    return up2(outline(im))


def portal(frame):
    im = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for i, r in enumerate((11, 9, 7, 5, 3)):
        a = (frame * 2 + i) % 5
        col = [(80, 200, 255), (120, 160, 255), (170, 120, 255), (120, 220, 255), (220, 240, 255)][a]
        d.ellipse([12 - r, 12 - r * 0.8, 12 + r, 12 + r * 0.8], outline=col + (255,), width=2)
    return up2(im)


def door(color, bars):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 15, 15], fill=(34, 28, 30, 255))
    for x in (2, 6, 10, 14):
        d.line([x, 0, x, 15], fill=bars)
    d.line([0, 3, 15, 3], fill=color); d.line([0, 12, 15, 12], fill=color)
    return up2(im)


def projectile(color):
    im = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.ellipse([0, 0, 7, 7], fill=color[:3] + (160,))
    d.ellipse([2, 2, 5, 5], fill=(255, 255, 255, 255))
    return up2(im)


def shadow():
    im = Image.new("RGBA", (32, 10), (0, 0, 0, 0))
    ImageDraw.Draw(im).ellipse([0, 0, 31, 9], fill=(0, 0, 0, 90))
    return im


def skill_icon(kind):
    im = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    if kind == "whirl":
        for a in range(0, 360, 45):
            r = math.radians(a)
            d.line([12, 12, 12 + 9 * math.cos(r), 12 + 9 * math.sin(r)], fill=(230, 230, 250, 255), width=2)
        d.ellipse([8, 8, 16, 16], outline=(255, 210, 90, 255), width=2)
    elif kind == "dash":
        d.polygon([(3, 12), (14, 4), (14, 9), (21, 9), (21, 15), (14, 15), (14, 20)], fill=(255, 210, 90, 255))
        d.line([2, 6, 8, 6], fill=(200, 200, 220, 255)); d.line([2, 18, 8, 18], fill=(200, 200, 220, 255))
    elif kind == "dodge":
        d.arc([3, 3, 21, 21], 200, 520, fill=(140, 220, 255, 255), width=3)
        d.polygon([(18, 4), (22, 9), (15, 9)], fill=(140, 220, 255, 255))
    return up2(outline(im))


def make_sprites(out):
    os.makedirs(out, exist_ok=True)
    for direction in ("down", "up", "side"):
        strip([hero(direction, 0), hero(direction, 1), hero(direction, 2)]).save(f"{out}/hero_{direction}.png")

    swords = {
        "sealed": sword((150, 150, 160, 255), (110, 90, 60, 255)),
        "holy": sword((250, 246, 220, 255), (240, 200, 70, 255), glow=(255, 240, 160, 255), big=True),
        "blackiron": sword((70, 74, 90, 255), (60, 60, 70, 255)),
        "blackiron_aw": sword((40, 40, 60, 255), (150, 80, 255, 255), glow=(150, 80, 255, 255), big=True),
        "bloodmoon": sword((180, 40, 60, 255), (90, 30, 40, 255)),
        "bloodmoon_aw": sword((230, 30, 60, 255), (250, 200, 80, 255), glow=(255, 60, 90, 255), big=True),
        "sky": sword((120, 190, 255, 255), (220, 220, 240, 255)),
        "sky_aw": sword((180, 230, 255, 255), (255, 240, 120, 255), glow=(120, 220, 255, 255), big=True),
    }
    for k, im in swords.items():
        im.save(f"{out}/weapon_{k}.png")

    ores = {"blackiron": (90, 90, 120, 255), "bloodmoon": (220, 40, 70, 255), "sky": (110, 200, 255, 255)}
    for k, c in ores.items():
        vein(c).save(f"{out}/vein_{k}.png")
        ore_icon(c).save(f"{out}/ore_{k}.png")
    orb((150, 90, 255, 255)).save(f"{out}/orb_left.png")
    orb((255, 60, 90, 255)).save(f"{out}/orb_right.png")
    orb((110, 220, 255, 255)).save(f"{out}/orb_top.png")

    strip([skeleton_miner(0), skeleton_miner(1)]).save(f"{out}/mob_left.png")
    strip([cursed_knight(0), cursed_knight(1)]).save(f"{out}/mob_right.png")
    strip([tower_mage(0), tower_mage(1)]).save(f"{out}/mob_top.png")
    strip([golem(0), golem(1)]).save(f"{out}/boss_left.png")
    strip([blood_lord(0), blood_lord(1)]).save(f"{out}/boss_right.png")
    strip([sky_dragon(0), sky_dragon(1)]).save(f"{out}/boss_top.png")
    strip([demon_king(0), demon_king(1)]).save(f"{out}/demon_king.png")

    bed().save(f"{out}/bed.png")
    forge().save(f"{out}/forge.png")
    strip([portal(i) for i in range(4)]).save(f"{out}/portal.png")
    door((150, 120, 80, 255), (120, 96, 70, 255)).save(f"{out}/door.png")
    door((200, 60, 60, 255), (170, 40, 40, 255)).save(f"{out}/gate.png")
    projectile((140, 220, 255, 255)).save(f"{out}/bolt_blue.png")
    projectile((255, 80, 120, 255)).save(f"{out}/bolt_red.png")
    projectile((200, 90, 255, 255)).save(f"{out}/bolt_purple.png")
    shadow().save(f"{out}/shadow.png")
    for k in ("whirl", "dash", "dodge"):
        skill_icon(k).save(f"{out}/skill_{k}.png")


if __name__ == "__main__":
    os.makedirs(f"{ROOT}/maps", exist_ok=True)
    make_tileset(f"{ROOT}/maps/tiles.png")
    g, w = build_layers()
    write_tmx(f"{ROOT}/maps/world.tmx", g, w)
    make_sprites(f"{ROOT}/sprites")
    print("done")
