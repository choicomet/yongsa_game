#!/usr/bin/env python3
"""
던전 전체를 표현하는 Tiled 맵(assets/maps/dungeon.tmx)과 임시 타일셋(assets/maps/placeholder_tiles.png)을 만든다.

- 레이아웃(방·통로·문·스폰·광맥 후보)은 이 파일 위쪽의 표에서 정의한다.
- 생성된 dungeon.tmx 는 Tiled 에서 바로 열 수 있다. CraftPix 타일셋으로 교체할 때는
  Tiled 에서 타일셋만 바꾸고 오브젝트 레이어(rooms, doors, spawns, veins, points, interact, solids)는 유지하면 된다.

사용법: python3 tools/gen_map.py
"""
import os
import random
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_TMX = os.path.join(ROOT, "assets", "maps", "dungeon.tmx")
OUT_PNG = os.path.join(ROOT, "assets", "maps", "placeholder_tiles.png")

T = 32           # 타일 크기(px)
W, H = 200, 140  # 맵 크기(타일). y는 아래로 증가(Tiled 좌표)

# ---------------------------------------------------------------------------
# 방 정의: (id, zone, kind, x, y, w, h)  — 타일 단위, 바닥 영역
# kind: camp | ore | battle | ante | boss
# ---------------------------------------------------------------------------
ROOMS = []
CORRIDORS = []  # (x, y, w, h)


def room(rid, zone, kind, x, y, w, h):
    ROOMS.append(dict(id=rid, zone=zone, kind=kind, x=x, y=y, w=w, h=h))


def corr(x, y, w, h):
    CORRIDORS.append((x, y, w, h))


# 중앙 캠프 28x14
CAMP = (86, 110, 28, 14)
room("camp", "camp", "camp", *CAMP)


def side_zone(zone, sign):
    """왼쪽(sign=-1) 또는 오른쪽(sign=+1)으로 뻗는 구역. 왼쪽 기준 좌표를 거울 대칭한다."""
    def mx(x, w):
        return x if sign < 0 else W - x - w

    # 캠프 → 내부 통로
    corr(mx(74, 12), 115, 12, 3)
    room(f"{zone}_ore", zone, "ore", mx(58, 16), 110, 16, 12)
    corr(mx(52, 6), 115, 6, 3)
    room(f"{zone}_battle_a", zone, "battle", mx(32, 20), 109, 20, 14)
    corr(mx(40, 4), 101, 4, 8)
    room(f"{zone}_battle_b", zone, "battle", mx(32, 20), 87, 20, 14)
    corr(mx(40, 4), 81, 4, 6)
    room(f"{zone}_ante", zone, "ante", mx(34, 16), 71, 16, 10)
    corr(mx(40, 4), 67, 4, 4)
    room(f"{zone}_boss", zone, "boss", mx(30, 24), 55, 24, 12)


side_zone("mine", -1)
side_zone("fortress", +1)

# 위쪽 봉인의 신전
corr(98, 100, 4, 10)
room("temple_ore", "temple", "ore", 92, 88, 16, 12)
corr(98, 82, 4, 6)
room("temple_battle_a", "temple", "battle", 90, 68, 20, 14)
corr(98, 62, 4, 6)
room("temple_battle_b", "temple", "battle", 90, 48, 20, 14)
corr(98, 42, 4, 6)
room("temple_ante", "temple", "ante", 92, 32, 16, 10)
corr(98, 28, 4, 4)
room("temple_boss", "temple", "boss", 88, 16, 24, 12)

ROOM = {r["id"]: r for r in ROOMS}

# ---------------------------------------------------------------------------
# 문: 통로 안의 한 줄. lock 규칙은 게임 코드(Door)에서 해석한다.
#   rule = zone:<zoneId>      → 해당 구역 해금 전 잠김
#   rule = weapon             → 무기가 없으면 잠김
#   rule = cleared:<roomId>   → 해당 방 클리어 전 잠김
#   combat = 방 ID 목록       → 이 방들이 전투 중이면 잠김
# ---------------------------------------------------------------------------
DOORS = []


def door(did, x, y, w, h, rule="", combat=""):
    DOORS.append(dict(id=did, x=x, y=y, w=w, h=h, rule=rule, combat=combat))


def side_doors(zone, sign):
    def mx(x, w):
        return x if sign < 0 else W - x - w
    if zone != "mine":
        door(f"{zone}_gate", mx(85, 1), 115, 1, 3, rule=f"zone:{zone}")
    door(f"{zone}_door_a", mx(52, 1), 115, 1, 3, rule="weapon", combat=f"{zone}_battle_a")
    door(f"{zone}_door_ab", mx(40, 4), 108, 4, 1, rule=f"cleared:{zone}_battle_a",
         combat=f"{zone}_battle_a,{zone}_battle_b")
    door(f"{zone}_door_b", mx(40, 4), 81, 4, 1, rule=f"cleared:{zone}_battle_b", combat=f"{zone}_battle_b")
    door(f"{zone}_door_boss", mx(40, 4), 67, 4, 1, combat=f"{zone}_boss")


side_doors("mine", -1)
side_doors("fortress", +1)
door("temple_gate", 98, 109, 4, 1, rule="zone:temple")
door("temple_door_a", 98, 82, 4, 1, rule="weapon", combat="temple_battle_a")
door("temple_door_ab", 98, 67, 4, 1, rule="cleared:temple_battle_a", combat="temple_battle_a,temple_battle_b")
door("temple_door_b", 98, 42, 4, 1, rule="cleared:temple_battle_b", combat="temple_battle_b")
door("temple_door_boss", 98, 28, 4, 1, combat="temple_boss")

# ---------------------------------------------------------------------------
# 적 생성점: (room, enemy, tx, ty) — 방 기준 상대 타일 좌표(중심)
# ---------------------------------------------------------------------------
SPAWNS = []


def spawns_for(rid, enemies):
    r = ROOM[rid]
    slots = [(0.30, 0.30), (0.70, 0.30), (0.50, 0.25), (0.25, 0.60), (0.75, 0.60)]
    for i, e in enumerate(enemies):
        fx, fy = slots[i]
        SPAWNS.append(dict(room=rid, enemy=e, x=(r["x"] + r["w"] * fx) * T, y=(r["y"] + r["h"] * fy) * T))


# 입구 반대쪽에 생성되도록 위쪽 방(입구가 아래)은 위쪽 슬롯, 왼쪽 방은 그대로 둔다.
spawns_for("mine_battle_a", ["slime", "slime"])
spawns_for("mine_battle_b", ["slime", "slime", "slime"])
spawns_for("fortress_battle_a", ["charger", "charger"])
spawns_for("fortress_battle_b", ["charger", "charger", "slime"])
spawns_for("temple_battle_a", ["watcher", "watcher"])
spawns_for("temple_battle_b", ["watcher", "watcher", "charger", "charger"])

for zone, boss in (("mine", "boss_mine"), ("fortress", "boss_fortress"), ("temple", "boss_temple")):
    r = ROOM[f"{zone}_boss"]
    SPAWNS.append(dict(room=r["id"], enemy=boss, x=(r["x"] + r["w"] / 2) * T, y=(r["y"] + 3.5) * T))

# ---------------------------------------------------------------------------
# 점 오브젝트: camp_spawn, 지름길 도착점 등 (px, 중심)
# ---------------------------------------------------------------------------
cx, cy, cw, ch = CAMP
POINTS = [
    dict(name="camp_spawn", x=(cx + 14) * T, y=(cy + 9.5) * T),
]

# 상호작용 오브젝트 (px 사각형)
INTERACT = []
SOLIDS = []
DECOR = []  # (kind, x, y) px — 렌더러가 그리는 장식 / 조명


def interact(name, kind, x, y, w, h, **props):
    INTERACT.append(dict(name=name, kind=kind, x=x, y=y, w=w, h=h, props=props))


# 캠프 시설: 모닥불(중앙), 침대(왼쪽), 대장간(오른쪽)
fire_x, fire_y = (cx + 14) * T, (cy + 7) * T
DECOR.append(("campfire", fire_x, fire_y))
SOLIDS.append(dict(x=fire_x - 20, y=fire_y - 12, w=40, h=22))

bed_x, bed_y = (cx + 9.5) * T, (cy + 7.5) * T
interact("bed", "bed", bed_x - 30, bed_y - 22, 60, 44)
SOLIDS.append(dict(x=bed_x - 28, y=bed_y - 14, w=56, h=26))
DECOR.append(("bedroll", bed_x, bed_y))
DECOR.append(("crate", (cx + 7) * T, (cy + 5.4) * T))
DECOR.append(("lantern_table", (cx + 9.5) * T, (cy + 4.8) * T))
SOLIDS.append(dict(x=(cx + 9.5) * T - 16, y=(cy + 4.8) * T - 8, w=32, h=14))
SOLIDS.append(dict(x=(cx + 7) * T - 12, y=(cy + 5.4) * T - 8, w=24, h=14))

forge_x, forge_y = (cx + 18.5) * T, (cy + 7.5) * T
interact("forge", "forge", forge_x - 40, forge_y - 30, 80, 60)
DECOR.append(("furnace", (cx + 18) * T, (cy + 5.2) * T))
DECOR.append(("anvil", forge_x, forge_y + 4))
DECOR.append(("workbench", (cx + 21) * T, (cy + 6.2) * T))
SOLIDS.append(dict(x=(cx + 17) * T, y=(cy + 4.4) * T, w=64, h=36))
SOLIDS.append(dict(x=forge_x - 16, y=forge_y - 6, w=32, h=16))
SOLIDS.append(dict(x=(cx + 19.8) * T, y=(cy + 5.6) * T, w=48, h=24))

# 지름길: 캠프 벽면(통로 옆)에 문, 보스 앞 공간에 귀환문
SHORTCUTS = [
    # zone, 캠프 쪽 문 (벽면 옆 바닥 사각형), 캠프 도착점, 보스 앞 도착점
    ("mine", (cx * T + 4, (cy + 1.5) * T, 40, 56), (cx * T + 40, (cy + 3.5) * T)),
    ("fortress", ((cx + cw) * T - 44, (cy + 1.5) * T, 40, 56), ((cx + cw) * T - 40, (cy + 3.5) * T)),
    ("temple", ((cx + 9) * T, cy * T + 2, 56, 40), ((cx + 10) * T, (cy + 2.2) * T)),
]
for zone, (x, y, w, h), (ax, ay) in SHORTCUTS:
    a = ROOM[f"{zone}_ante"]
    interact(f"shortcut_{zone}", "shortcut", x, y, w, h, zone=zone)
    POINTS.append(dict(name=f"shortcut_{zone}_camp", x=ax, y=ay))
    # 보스 앞 공간의 귀환문: 왼쪽 벽 쪽
    rx, ry = (a["x"] + 1) * T, (a["y"] + a["h"] / 2 - 1) * T
    interact(f"return_{zone}", "return", rx - 8, ry - 24, 48, 56, zone=zone)
    POINTS.append(dict(name=f"shortcut_{zone}_ante", x=rx + 64, y=ry + 8))

# 횃불 (캠프와 각 방)
def torches_for(r, color):
    x0, y0, w, h = r["x"], r["y"], r["w"], r["h"]
    for fx in (0.18, 0.5, 0.82):
        DECOR.append((f"torch_{color}", (x0 + w * fx) * T, y0 * T - 6))
    if r["kind"] in ("battle", "boss", "ore"):
        for side in (x0 + 0.35, x0 + w - 0.35):
            DECOR.append((f"torch_{color}", side * T, (y0 + h * 0.3) * T))


for r in ROOMS:
    color = {"camp": "warm", "mine": "warm", "fortress": "blue", "temple": "violet"}[r["zone"]]
    torches_for(r, color)
DECOR.append(("brazier", (cx + 11) * T, (cy + 1) * T))
DECOR.append(("brazier", (cx + 17) * T, (cy + 1) * T))
for px in (cx + 3, cx + cw - 3):
    for py in (cy + 3, cy + 11):
        DECOR.append(("pillar", px * T, py * T))
        SOLIDS.append(dict(x=px * T - 14, y=py * T - 8, w=28, h=16))

# 광맥 후보: 각 광물방에 10개. 벽에 붙여 두어 이동 경로를 막지 않는다.
VEINS = []
for zone in ("mine", "fortress", "temple"):
    r = ROOM[f"{zone}_ore"]
    x0, y0, w, h = r["x"], r["y"], r["w"], r["h"]
    cand = []
    for i in range(5):
        cand.append((x0 + 1.5 + i * (w - 3) / 4, y0 + 1.3))          # 위쪽 벽을 따라
    for i in range(5):
        cand.append((x0 + 1.5 + i * (w - 3) / 4, y0 + h - 1.2))      # 아래쪽 벽을 따라
    if zone == "temple":
        # 신전 광물방은 통로가 위/아래 중앙에 있으므로 중앙 열 후보를 좌우 벽 쪽으로 옮긴다.
        cand[2] = (x0 + 1.3, y0 + h / 2)
        cand[7] = (x0 + w - 1.3, y0 + h / 2)
    for i, (tx, ty) in enumerate(cand):
        # 입구에서 바로 보이는 후보(모든 후보가 방 안에서 보이지만, 첫 배치 규칙 검증용으로 표시)
        visible = "true" if ty < y0 + h / 2 else "false"
        VEINS.append(dict(name=f"{zone}_vein_{i + 1:02d}", room=r["id"], x=tx * T, y=ty * T, visible=visible))

# ---------------------------------------------------------------------------
# 타일 격자 구성
# ---------------------------------------------------------------------------
floor = [[False] * W for _ in range(H)]


def carve(x, y, w, h):
    for yy in range(int(y), int(y + h)):
        for xx in range(int(x), int(x + w)):
            floor[yy][xx] = True


for r in ROOMS:
    carve(r["x"], r["y"], r["w"], r["h"])
for c in CORRIDORS:
    carve(*c)

# 타일 ID (1부터, 타일셋 순서)
# 1~4 바닥(석재 변형), 5 캠프 바닥, 6 벽 윗면, 7 벽 정면, 8 벽 정면(아래 그림자), 9 통로 바닥, 10 깨진 바닥
ZONE_OF_CELL = [[None] * W for _ in range(H)]
for r in ROOMS:
    for yy in range(r["y"], r["y"] + r["h"]):
        for xx in range(r["x"], r["x"] + r["w"]):
            ZONE_OF_CELL[yy][xx] = r["kind"]

rnd = random.Random(7)
floor_layer = [[0] * W for _ in range(H)]
wall_layer = [[0] * W for _ in range(H)]
for y in range(H):
    for x in range(W):
        if floor[y][x]:
            kind = ZONE_OF_CELL[y][x]
            if kind == "camp":
                floor_layer[y][x] = 5 if rnd.random() < 0.85 else 10
            elif kind is None:
                floor_layer[y][x] = 9
            else:
                floor_layer[y][x] = rnd.choice([1, 1, 1, 2, 2, 3, 4])
        else:
            near = any(0 <= y + dy < H and 0 <= x + dx < W and floor[y + dy][x + dx]
                       for dy in (-2, -1, 0, 1, 2) for dx in (-2, -1, 0, 1, 2))
            if not near:
                continue  # 완전히 막힌 바깥은 비워 둔다(검은 배경)
            below = y + 1 < H and floor[y + 1][x]
            below2 = y + 2 < H and floor[y + 2][x]
            if below:
                wall_layer[y][x] = 8
            elif below2:
                wall_layer[y][x] = 7
            else:
                wall_layer[y][x] = 6

# 벽 정면이 2타일 높이가 되도록 바닥 위쪽 첫 줄은 벽으로 다시 칠하지 않는다 (위 규칙으로 처리됨)

# ---------------------------------------------------------------------------
# 임시 타일셋 PNG (그림은 CraftPix 타일셋으로 교체 예정)
# ---------------------------------------------------------------------------
TILES = 10
img = [[(0, 0, 0, 0)] * (T * TILES) for _ in range(T)]


def put(tile, px, py, c):
    if 0 <= px < T and 0 <= py < T:
        img[py][(tile - 1) * T + px] = c


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3]) + (255,)


def stone_floor(tile, base, seed, cracks=False):
    r = random.Random(seed)
    for py in range(T):
        for px in range(T):
            n = r.random()
            k = 0.92 + n * 0.16
            c = shade(base, k)
            # 석판 이음새
            if px % 16 == 0 or (py % 16 == 0 and (px // 16 + py // 16) % 2 == 0) or py % 32 == 0:
                c = shade(base, 0.62)
            put(tile, px, py, c)
    if cracks:
        x, y = r.randint(6, 24), r.randint(6, 24)
        for _ in range(14):
            put(tile, x, y, shade(base, 0.5))
            x += r.choice([-1, 0, 1])
            y += r.choice([0, 1])


stone_floor(1, (74, 66, 60), 1)
stone_floor(2, (70, 63, 58), 2)
stone_floor(3, (78, 70, 62), 3, cracks=True)
stone_floor(4, (66, 60, 56), 4)
stone_floor(5, (92, 78, 62), 5)
stone_floor(9, (60, 55, 52), 9)
stone_floor(10, (88, 74, 60), 10, cracks=True)

# 6 벽 윗면: 어두운 돌
r = random.Random(6)
for py in range(T):
    for px in range(T):
        put(6, px, py, shade((40, 36, 38), 0.85 + r.random() * 0.25))
# 7 벽 정면 (벽돌)
for tile, dark in ((7, 1.0), (8, 0.85)):
    r = random.Random(tile)
    for py in range(T):
        for px in range(T):
            row = py // 8
            off = 8 if row % 2 else 0
            mortar = py % 8 == 7 or (px + off) % 16 == 15
            base = (58, 52, 54) if not mortar else (30, 27, 30)
            k = (0.9 + r.random() * 0.2) * dark
            if tile == 8 and py > 26:
                k *= 0.55  # 바닥과 닿는 그림자
            put(tile, px, py, shade(base, k))


def write_png(path, rows):
    h = len(rows)
    w = len(rows[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in rows)

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)


# ---------------------------------------------------------------------------
# TMX 쓰기
# ---------------------------------------------------------------------------
def csv(layer):
    return ",\n".join(",".join(str(v) for v in row) for row in layer)


def props_xml(props):
    if not props:
        return ""
    inner = "".join(f'<property name="{k}" value="{v}"/>' for k, v in props.items())
    return f"<properties>{inner}</properties>"


oid = [1]


def obj(name, typ, x, y, w=None, h=None, props=None, point=False):
    i = oid[0]
    oid[0] += 1
    if point:
        return f'  <object id="{i}" name="{name}" type="{typ}" x="{x:.1f}" y="{y:.1f}">{props_xml(props)}<point/></object>\n'
    return (f'  <object id="{i}" name="{name}" type="{typ}" x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}">'
            f'{props_xml(props)}</object>\n')


out = []
out.append('<?xml version="1.0" encoding="UTF-8"?>\n')
out.append(f'<map version="1.10" tiledversion="1.10.2" orientation="orthogonal" renderorder="right-down" '
           f'width="{W}" height="{H}" tilewidth="{T}" tileheight="{T}" infinite="0" nextlayerid="20" nextobjectid="9999">\n')
out.append(f' <tileset firstgid="1" name="placeholder_tiles" tilewidth="{T}" tileheight="{T}" tilecount="{TILES}" columns="{TILES}">\n')
out.append(f'  <image source="placeholder_tiles.png" width="{T * TILES}" height="{T}"/>\n')
out.append(' </tileset>\n')
out.append(f' <layer id="1" name="floor" width="{W}" height="{H}">\n  <data encoding="csv">\n{csv(floor_layer)}\n  </data>\n </layer>\n')
out.append(f' <layer id="2" name="walls" width="{W}" height="{H}">\n'
           f'  <properties><property name="solid" type="bool" value="true"/></properties>\n'
           f'  <data encoding="csv">\n{csv(wall_layer)}\n  </data>\n </layer>\n')

out.append(' <objectgroup id="3" name="rooms">\n')
for r in ROOMS:
    out.append(obj(r["id"], r["kind"], r["x"] * T, r["y"] * T, r["w"] * T, r["h"] * T, {"zone": r["zone"]}))
out.append(' </objectgroup>\n')

out.append(' <objectgroup id="4" name="doors">\n')
for d in DOORS:
    props = {}
    if d["rule"]:
        props["rule"] = d["rule"]
    if d["combat"]:
        props["combat"] = d["combat"]
    out.append(obj(d["id"], "door", d["x"] * T, d["y"] * T, d["w"] * T, d["h"] * T, props))
out.append(' </objectgroup>\n')

out.append(' <objectgroup id="5" name="spawns">\n')
for i, s in enumerate(SPAWNS):
    out.append(obj(f"{s['room']}_spawn_{i}", "spawn", s["x"], s["y"], props={"room": s["room"], "enemy": s["enemy"]}, point=True))
out.append(' </objectgroup>\n')

out.append(' <objectgroup id="6" name="veins">\n')
for v in VEINS:
    out.append(obj(v["name"], "vein", v["x"], v["y"], props={"room": v["room"], "visible": v["visible"]}, point=True))
out.append(' </objectgroup>\n')

out.append(' <objectgroup id="7" name="points">\n')
for p in POINTS:
    out.append(obj(p["name"], "point", p["x"], p["y"], point=True))
out.append(' </objectgroup>\n')

out.append(' <objectgroup id="8" name="interact">\n')
for it in INTERACT:
    out.append(obj(it["name"], it["kind"], it["x"], it["y"], it["w"], it["h"], it["props"]))
out.append(' </objectgroup>\n')

out.append(' <objectgroup id="9" name="solids">\n')
for i, s in enumerate(SOLIDS):
    out.append(obj(f"solid_{i}", "solid", s["x"], s["y"], s["w"], s["h"]))
out.append(' </objectgroup>\n')

out.append(' <objectgroup id="10" name="decor">\n')
for i, (kind, x, y) in enumerate(DECOR):
    out.append(obj(f"decor_{i}", kind, x, y, point=True))
out.append(' </objectgroup>\n')
out.append('</map>\n')

os.makedirs(os.path.dirname(OUT_TMX), exist_ok=True)
with open(OUT_TMX, "w", encoding="utf-8") as f:
    f.write("".join(out))
write_png(OUT_PNG, img)
print(f"wrote {OUT_TMX} ({len(ROOMS)} rooms, {len(DOORS)} doors, {len(SPAWNS)} spawns, {len(VEINS)} vein candidates)")
