#!/usr/bin/env python3
"""
CraftPix 에셋(art_source/craftpix/)을 게임용으로 정리한다.

  assets/tiles/craftpix_tiles.png + assets/data/tileset.json      테마 7종 바닥·벽 (16px 원본, 게임에서 2배)
  assets/sprites/cp/*.png + assets/data/sprites_craftpix.json      소품·불꽃·문·수정·언데드 장식

원본 팩(라이선스상 저장소에 그대로 올리지 않는다)은 art_source/craftpix/ 에 풀어 둔다:
  dungeon/   ← Free 2D Top-Down Pixel Dungeon Asset Pack 의 Tiled_files
  objects/   ← Free Pixel Art Dungeon Objects Asset Pack 의 PNG
  undead/    ← Free Undead Tileset Top-Down Pixel Art 의 PNG
  crystals/  ← Top-Down Crystals Pixel Art 의 PNG/Assets_texture_shadow
사용법: python3 tools/build_craftpix.py
"""
import colorsys
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from cp_extract import components  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "art_source", "craftpix")
OUT_SPR = os.path.join(ROOT, "assets", "sprites", "cp")
T = 16


def src(*p):
    return os.path.join(SRC, *p)


def load(*p):
    return Image.open(src(*p)).convert("RGBA")


# ---------------------------------------------------------------------------
# 색 바꾸기 (원본은 푸른 회색 돌)
# ---------------------------------------------------------------------------

def recolor(img, hue=None, sat=1.0, val=1.0, hue_mix=1.0):
    out = img.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if hue is not None:
                h = (h * (1 - hue_mix) + (hue / 360) * hue_mix) % 1.0
            s = min(1, s * sat)
            v = min(1, v * val)
            r2, g2, b2 = colorsys.hsv_to_rgb(h, s, v)
            px[x, y] = (int(r2 * 255), int(g2 * 255), int(b2 * 255), a)
    return out


# 테마: 0 캠프, 1 폐광, 2 요새, 3 신전, 4 폐광 보스, 5 요새 보스, 6 신전 보스
THEMES = [
    dict(name="camp", hue=28, sat=0.75, val=1.0),
    dict(name="mine", hue=24, sat=0.55, val=0.85),
    dict(name="fortress", hue=None, sat=1.0, val=1.0),
    dict(name="temple", hue=272, sat=1.35, val=0.95),
    dict(name="boss_mine", hue=12, sat=1.1, val=0.8),
    dict(name="boss_fortress", hue=222, sat=1.5, val=0.92),
    dict(name="boss_temple", hue=150, sat=0.45, val=0.75),
]

# walls_floor.png (17열) 타일 번호
WF_COLS = 17
TOP9 = {"tl": 18, "t": 19, "tr": 20, "l": 35, "c": 36, "r": 37, "bl": 52, "b": 53, "br": 54}
FACE_U = (69, 70, 71)  # 왼쪽 끝, 가운데, 오른쪽 끝
FACE_L = (86, 87, 88)
FLOOR = 138
FLOOR_MARKS = (228, 229, 245, 246)
FLOOR_DARK = (250,)


def wf_tile(img, i):
    c, r = i % WF_COLS, i // WF_COLS
    return img.crop((c * T, r * T, c * T + T, r * T + T))


def compose_top(img, mask):
    """벽 윗면 16종: 각 사분면을 9조각 중 알맞은 조각에서 가져온다. 비트 1=북 2=동 4=남 8=서 (열림)."""
    n, e, s, w = mask & 1, mask & 2, mask & 4, mask & 8
    out = Image.new("RGBA", (T, T))
    h = T // 2
    quads = [
        ((0, 0), "tl" if n and w else "t" if n else "l" if w else "c"),
        ((h, 0), "tr" if n and e else "t" if n else "r" if e else "c"),
        ((0, h), "bl" if s and w else "b" if s else "l" if w else "c"),
        ((h, h), "br" if s and e else "b" if s else "r" if e else "c"),
    ]
    for (qx, qy), key in quads:
        t = wf_tile(img, TOP9[key]).crop((qx, qy, qx + h, qy + h))
        out.paste(t, (qx, qy))
    return out


def shadow_tile(kind):
    img = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    p = img.load()
    for y in range(T):
        for x in range(T):
            if kind == "n":
                a = max(0, 1 - y / 6) * 0.5
            elif kind == "w":
                a = max(0, 1 - x / 4) * 0.35
            elif kind == "e":
                a = max(0, 1 - (T - 1 - x) / 4) * 0.35
            else:
                a = max(0, 1 - (T - 1 - y) / 2) * 0.3
            if a > 0:
                p[x, y] = (0, 0, 0, int(a * 255))
    return img


def build_tiles():
    wf = load("dungeon", "walls_floor.png")
    undead = load("undead", "Ground_rocks.png")
    tiles = []  # (image)
    meta = {"image": "tiles/craftpix_tiles.png", "tileSize": T, "themes": []}

    def add(img):
        tiles.append(img)
        return len(tiles) - 1

    for th in THEMES:
        rc = lambda im: recolor(im, th["hue"], th["sat"], th["val"]) if (th["hue"] is not None or th["sat"] != 1 or th["val"] != 1) else im
        e = {}
        if th["name"] == "boss_temple":
            ground = [undead.crop((c * T, r * T, c * T + T, r * T + T)) for c, r in ((27, 3), (30, 3), (2, 2), (5, 6), (9, 6), (12, 6), (4, 22))]
            e["floor"] = [add(g) for g in ground]
            e["floorWeights"] = [8, 8, 8, 2, 2, 2, 2]
        elif th["name"] == "boss_fortress":
            # 밝은 판석과 어두운 판석의 바둑판 무늬
            e["floor"] = [add(rc(wf_tile(wf, FLOOR))), add(rc(wf_tile(wf, FLOOR_DARK[0])))]
            e["pattern"] = "checker"
        else:
            fl = [wf_tile(wf, FLOOR)] + [wf_tile(wf, i) for i in FLOOR_MARKS]
            weights = [40, 1, 1, 1, 1]
            if th["name"] == "boss_mine":
                weights = [16, 2, 2, 2, 2]
            e["floor"] = [add(rc(t)) for t in fl]
            e["floorWeights"] = weights
        e["faceUpper"] = [add(rc(wf_tile(wf, FACE_U[1])))]
        e["faceUpperL"] = add(rc(wf_tile(wf, FACE_U[0])))
        e["faceUpperR"] = add(rc(wf_tile(wf, FACE_U[2])))
        e["faceLower"] = [add(rc(wf_tile(wf, FACE_L[1])))]
        e["faceLowerL"] = add(rc(wf_tile(wf, FACE_L[0])))
        e["faceLowerR"] = add(rc(wf_tile(wf, FACE_L[2])))
        e["top"] = [add(rc(compose_top(wf, m))) for m in range(16)]
        meta["themes"].append(e)
    meta["shadow"] = {k: add(shadow_tile(k)) for k in ("n", "w", "e", "s")}

    cols = 32
    rows = (len(tiles) + cols - 1) // cols
    atlas = Image.new("RGBA", (cols * T, rows * T), (0, 0, 0, 0))
    for i, t in enumerate(tiles):
        atlas.paste(t, ((i % cols) * T, (i // cols) * T))

    # 번호 → [열, 행]
    def xy(i):
        return [i % cols, i // cols]

    for e in meta["themes"]:
        for k, v in list(e.items()):
            if k in ("floorWeights", "pattern"):
                continue
            e[k] = [xy(i) for i in v] if isinstance(v, list) else xy(v)
    meta["shadow"] = {k: xy(v) for k, v in meta["shadow"].items()}
    os.makedirs(os.path.join(ROOT, "assets", "tiles"), exist_ok=True)
    atlas.save(os.path.join(ROOT, "assets", "tiles", "craftpix_tiles.png"))
    with open(os.path.join(ROOT, "assets", "data", "tileset.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=1)
    print("tiles:", len(tiles))


# ---------------------------------------------------------------------------
# 스프라이트
# ---------------------------------------------------------------------------
MANIFEST = {}


def save_still(key, img, scale=2.0, origin_y=None):
    """한 장짜리 그림. 원점은 아래 가운데 (origin_y 로 발 높이 조정)."""
    os.makedirs(OUT_SPR, exist_ok=True)
    bb = img.getbbox()
    if bb:
        img = img.crop(bb)
    img.save(os.path.join(OUT_SPR, key + ".png"))
    MANIFEST[key] = {"file": "sprites/cp/" + key + ".png", "originX": img.width / 2,
                     "originY": img.height * 0.12 if origin_y is None else origin_y, "scale": scale}


def save_anim(key, frames, fps, scale=2.0, origin_y=2):
    """같은 크기 프레임을 가로로 이어 붙인다."""
    os.makedirs(OUT_SPR, exist_ok=True)
    w, h = frames[0].size
    sheet = Image.new("RGBA", (w * len(frames), h))
    for i, f in enumerate(frames):
        sheet.paste(f, (i * w, 0))
    sheet.save(os.path.join(OUT_SPR, key + ".png"))
    MANIFEST[key] = {"file": "sprites/cp/" + key + ".png", "frameWidth": w, "frameHeight": h, "frames": len(frames),
                     "fps": fps, "originX": w / 2, "originY": origin_y, "scale": scale}


def pick(sheet_img, boxes, idx):
    return sheet_img.crop(boxes[idx])


def build_sprites():
    MANIFEST.clear()
    obj = load("dungeon", "Objects.png")
    ob = components(obj, gap=1)
    sup = load("objects", "supplies_objects.png")
    sb = components(sup, gap=1)
    oth = load("objects", "Other_objects.png")
    otb = components(oth, gap=1)

    variants = {
        # 던전 소품
        "chest": [(obj, ob, 5)],
        "chest_gold": [(obj, ob, 13), (obj, ob, 14)],
        "gold": [(obj, ob, 7), (obj, ob, 8), (obj, ob, 15), (obj, ob, 16), (obj, ob, 40)],
        "crates": [(obj, ob, 10), (obj, ob, 11), (obj, ob, 12)],
        "crate": [(obj, ob, 24), (obj, ob, 27)],
        "barrel": [(obj, ob, 25), (obj, ob, 28), (obj, ob, 35)],
        "pot": [(obj, ob, 29), (obj, ob, 30), (obj, ob, 32), (obj, ob, 33)],
        "vases": [(obj, ob, 36), (obj, ob, 21)],
        "shields": [(obj, ob, 38)],
        "ladder": [(obj, ob, 3)],
        # 보급품
        "shelf": [(sup, sb, 1), (sup, sb, 2), (sup, sb, 6), (sup, sb, 7), (sup, sb, 8)],
        "supplies": [(sup, sb, 19), (sup, sb, 20), (sup, sb, 30), (sup, sb, 31), (sup, sb, 50), (sup, sb, 51), (sup, sb, 70), (sup, sb, 72)],
        "sacks": [(sup, sb, 73), (sup, sb, 74), (sup, sb, 75), (sup, sb, 62)],
        "weapon_rack": [(sup, sb, 76), (sup, sb, 77), (sup, sb, 80), (sup, sb, 81), (sup, sb, 82)],
        "tool_rack": [(sup, sb, 87), (sup, sb, 88), (sup, sb, 89), (sup, sb, 90)],
        # 기타
        "dragon_skull": [(oth, otb, 0), (oth, otb, 1), (oth, otb, 2)],
        "trophy": [(oth, otb, 3), (oth, otb, 6)],
        "big_barrel": [(oth, otb, 10), (oth, otb, 11)],
        "armor": [(oth, otb, 12), (oth, otb, 13), (oth, otb, 14)],
        "banner_red": [(oth, otb, 15), (oth, otb, 19)],
        "banner_blue": [(oth, otb, 16), (oth, otb, 20)],
    }
    for key, items in variants.items():
        for i, (im, boxes, idx) in enumerate(items):
            origin = 0 if key.startswith("banner") else None
            save_still(f"{key}_{i}", pick(im, boxes, idx), 2.0, origin)

    # 불꽃 애니메이션 (6프레임): 벽 횃불, 작은 가마솥, 큰 불 그릇, 받침대 화로
    fire = load("dungeon", "fire_animation.png")
    for key, (x0, x1) in (("torch", (0, 36)), ("cauldron", (36, 80)), ("fire_bowl", (80, 128)), ("brazier", (128, 176))):
        frames = [fire.crop((x0, f * 48, x1, f * 48 + 48)) for f in range(6)]
        save_anim(key, frames, 9, 2.0, origin_y=4)
    fire2 = load("dungeon", "fire_animation2.png")
    for key, (x0, x1) in (("candle_0", (0, 32)), ("candle_1", (32, 64)), ("candle_2", (64, 96))):
        frames = [fire2.crop((x0, f * 32, x1, f * 32 + 32)) for f in range(6)]
        save_anim(key, frames, 8, 2.0, origin_y=6)

    # 아치 문 (닫힘 → 열림 5프레임): 쇠창살 / 나무
    doors = load("dungeon", "doors_lever_chest_animation.png")
    for key, x0 in (("door_grate", 64), ("door_wood", 96)):
        frames = [doors.crop((x0, f * 48, x0 + 32, f * 48 + 48)) for f in range(5)]
        save_anim(key, frames, 10, 2.0, origin_y=0)

    # 수정 → 광맥 (구역별 색)
    for key, name in (("vein_mine", "Yellow_crystal2"), ("vein_fortress", "Blue_crystal2"), ("vein_temple", "Violet_crystal2")):
        save_still(key, load("crystals", name + ".png"), 1.0, 6)
    for key, name in (("crystal_mine", "Yellow_crystal3"), ("crystal_fortress", "Blue_crystal3"), ("crystal_temple", "Violet_crystal3"),
                      ("crystal_red", "Red_crystal3"), ("crystal_green", "Green_crystal3")):
        save_still(key + "_0", load("crystals", name + ".png"), 1.0, 4)

    # 언데드 장식 (신전 구역과 신전 보스방)
    sep = lambda n: load("undead", "Objects_separately", n)
    und = {
        "grave": ["Grave_shadow1_1.png", "Grave_shadow1_7.png", "Grave_shadow1_8.png", "Grave_shadow1_9.png", "Grave_shadow1_11.png", "Grave_shadow1_12.png"],
        "ubones": ["Bones_shadow1_3.png", "Bones_shadow1_4.png", "Bones_shadow1_6.png", "Bones_shadow1_8.png", "Bones_shadow1_14.png", "Bones_shadow1_17.png"],
        "dead_tree": ["Dead_tree_shadow1_2.png", "Broken_tree_shadow1_2.png", "Broken_tree_shadow1_3.png"],
        "thorn": ["Thorn_plant_shadow1_2.png", "Thorn_plant_shadow1_4.png"],
        "ruin": ["Ruin_shadow1_3.png", "Ruin_shadow1_4.png"],
        "skull_pile": ["Pile_sculls_shadow1.png"],
        "lich": ["Lich_shadow1.png"],
        "dead_arm": ["Dead_arm_shadow1_1.png"],
    }
    for key, files in und.items():
        for i, f in enumerate(files):
            if not os.path.exists(src("undead", "Objects_separately", f)):
                continue
            scale = {"lich": 1.5, "ubones": 1.0, "dead_tree": 1.3, "thorn": 1.3, "skull_pile": 1.3, "ruin": 1.5}.get(key, 2.0)
            save_still(f"{key}_{i}", sep(f), scale)

    with open(os.path.join(ROOT, "assets", "data", "sprites_craftpix.json"), "w", encoding="utf-8") as f:
        json.dump(MANIFEST, f, ensure_ascii=False, indent=1)
    print("sprites:", len(MANIFEST))


if __name__ == "__main__":
    if not os.path.isdir(SRC):
        raise SystemExit("art_source/craftpix 가 없습니다. 팩을 풀어 넣어 주세요 (파일 위쪽 설명 참고).")
    build_tiles()
    build_sprites()
