"""타일·장식·캠프 시설 임시 그림."""
import math
import random
from art import Canvas, rgb, shade, mix

OUT = (14, 10, 18, 255)
T = 32

THEMES = ["camp", "left", "right", "top", "arena"]


# ===================================================================== 타일
def _noise(c, base, n, rnd, k=14):
    for _ in range(n):
        x, y = rnd.randrange(T), rnd.randrange(T)
        c.px(x, y, shade(base, rnd.choice((-k, k))))


def floor_tile(theme, variant):
    rnd = random.Random(hash((theme, variant)) & 0xffff)
    c = Canvas(T, T)
    if theme == "camp":
        base = rgb("#7a6650")
        c.flat_rect((0, 0, 31, 31), base)
        _noise(c, base, 40, rnd)
        # 박힌 돌
        for _ in range(3 + variant):
            x, y = rnd.randrange(2, 26), rnd.randrange(2, 26)
            r = rnd.randrange(3, 6)
            c.ellipse((x, y, x + r * 1.4, y + r), rgb("#9a8c78"), hi=26, lo=-30)
        if variant == 2:
            for _ in range(5):
                x, y = rnd.randrange(2, 30), rnd.randrange(2, 30)
                c.line([(x, y), (x - 1, y - 3)], rgb("#5f7a3a")); c.line([(x + 1, y), (x + 2, y - 2)], rgb("#78964a"))
    elif theme == "left":
        base = rgb("#4c3d34")
        c.flat_rect((0, 0, 31, 31), base)
        _noise(c, base, 70, rnd, 10)
        for _ in range(4):
            x, y = rnd.randrange(1, 29), rnd.randrange(1, 29)
            c.flat_rect((x, y, x + 1, y + 1), rgb("#6a5648")); c.px(x, y, rgb("#857060"))
        if variant == 1:
            c.line([(4, 22), (12, 18), (17, 21), (26, 15)], shade(base, -24))
        if variant == 2:
            for _ in range(3):
                x, y = rnd.randrange(3, 26), rnd.randrange(3, 26)
                c.ellipse((x, y, x + 6, y + 4), rgb("#5e4e44"), hi=20, lo=-20)
    elif theme == "right":
        base = rgb("#4a3c44")
        c.flat_rect((0, 0, 31, 31), base)
        for (x0, y0, x1, y1) in ((0, 0, 15, 15), (16, 0, 31, 15), (0, 16, 15, 31), (16, 16, 31, 31)):
            sl = shade(base, rnd.choice((-6, 0, 6)))
            c.rect((x0 + 1, y0 + 1, x1 - 1, y1 - 1), sl, hi=14, lo=-14, dither=False)
        if variant == 1:
            c.line([(18, 18), (22, 24), (28, 26)], shade(base, -30))
        if variant == 2:
            c.flat_ellipse((6, 6, 14, 10), rgb("#5a1a24")); c.px(10, 8, rgb("#7a2a34"))
    elif theme == "top":
        base = rgb("#2c2844")
        c.flat_rect((0, 0, 31, 31), base)
        c.rect((1, 1, 30, 30), shade(base, 6), hi=16, lo=-12, dither=False)
        c.line([(0, 0), (31, 0)], shade(base, -18)); c.line([(0, 0), (0, 31)], shade(base, -18))
        if variant == 1:
            c.line([(8, 16), (16, 8), (24, 16), (16, 24), (8, 16)], rgb("#4a4a8a"))
            c.px(16, 16, rgb("#8a9aff"))
        if variant == 2:
            c.line([(5, 26), (13, 20), (20, 23)], shade(base, -20))
    else:  # arena
        base = rgb("#2e1218")
        c.flat_rect((0, 0, 31, 31), base)
        _noise(c, base, 50, rnd, 10)
        if variant >= 1:
            pts = [(rnd.randrange(32), rnd.randrange(32)) for _ in range(3)]
            for a, b in zip(pts, pts[1:]):
                c.line([a, b], rgb("#ff5a20"), 1)
                c.line([(a[0] + 1, a[1]), (b[0] + 1, b[1])], rgb("#a02010"), 1)
    return c


WALL = {
    "camp": (rgb("#6a4a30"), rgb("#8a6440")),
    "left": (rgb("#3a2e2a"), rgb("#56463e")),
    "right": (rgb("#3a2a34"), rgb("#5a4652")),
    "top": (rgb("#1e1c34"), rgb("#3a3864")),
    "arena": (rgb("#1a080c"), rgb("#4a1820")),
}


def wall_top(theme):
    dark, light = WALL[theme]
    c = Canvas(T, T)
    c.flat_rect((0, 0, 31, 31), shade(dark, -14))
    c.rect((2, 2, 29, 29), dark, hi=12, lo=-10, dither=False)
    if theme == "camp":
        for x in range(0, 32, 8):
            c.flat_ellipse((x + 1, 10, x + 7, 22), shade(light, -10))
            c.flat_ellipse((x + 2, 12, x + 5, 16), light)
    return c


def wall_face(theme):
    dark, light = WALL[theme]
    rnd = random.Random(hash(theme) & 0xffff)
    c = Canvas(T, T)
    c.flat_rect((0, 0, 31, 31), dark)
    c.flat_rect((0, 0, 31, 5), shade(light, 20))          # 윗면 모서리
    c.flat_rect((0, 6, 31, 6), shade(dark, -20))
    if theme == "camp":                                  # 통나무 울타리
        for x in range(0, 32, 8):
            c.rrect((x, 4, x + 7, 31), 3, light, hi=22, lo=-28)
            c.poly([(x, 4), (x + 3, 0), (x + 7, 4)], shade(light, 10))
            c.line([(x + 2, 12), (x + 5, 12)], shade(light, -30))
    elif theme == "left":                                # 거친 바위 + 나무 버팀목
        for _ in range(6):
            x, y = rnd.randrange(0, 28), rnd.randrange(8, 28)
            c.ellipse((x, y, x + rnd.randrange(6, 12), y + rnd.randrange(4, 8)), shade(light, rnd.choice((-10, 0, 8))), hi=18, lo=-18)
    elif theme == "right":                               # 벽돌
        for row, y in enumerate(range(8, 32, 6)):
            off = 0 if row % 2 == 0 else 8
            for x in range(-8 + off, 32, 16):
                c.rect((x + 1, y + 1, x + 14, y + 5), shade(light, rnd.choice((-12, -4, 4))), hi=14, lo=-16, dither=False)
    elif theme == "top":                                 # 매끈한 석판 + 빛나는 홈
        c.rect((2, 8, 14, 30), light, hi=18, lo=-16, dither=False)
        c.rect((17, 8, 29, 30), light, hi=18, lo=-16, dither=False)
        c.flat_rect((15, 8, 16, 30), rgb("#5a6aff"))
    else:                                                # 심연: 살덩이 바위
        for _ in range(5):
            x, y = rnd.randrange(0, 26), rnd.randrange(8, 26)
            c.ellipse((x, y, x + 9, y + 7), light, hi=16, lo=-16)
        c.line([(4, 30), (10, 18), (16, 26)], rgb("#ff4020"))
    c.flat_rect((0, 30, 31, 31), shade(dark, -30))       # 바닥 그림자
    return c


def tileset():
    tiles = [Canvas(T, T)]
    tiles[0].flat_rect((0, 0, 31, 31), rgb("#0c0a12"))
    for th in THEMES:
        for v in range(3):
            tiles.append(floor_tile(th, v))
        tiles.append(wall_top(th))
        tiles.append(wall_face(th))
    from PIL import Image
    out = Image.new("RGBA", (len(tiles) * T, T))
    for i, t in enumerate(tiles):
        out.alpha_composite(t.img, (i * T, 0))
    return out, len(tiles)


def tile_index(theme, kind):
    """kind: 0~2 바닥 변형, 3 벽 윗면, 4 벽 앞면. 반환은 0부터 (gid = +1)."""
    return 1 + THEMES.index(theme) * 5 + kind


# ===================================================================== 장식
def torch(frame):
    c = Canvas(32, 32)
    c.rrect((13, 14, 18, 26), 1, rgb("#5a3a22"))
    c.rect((11, 12, 20, 15), rgb("#7a7a86"))
    h = (0, 2, 1)[frame]
    c.poly([(12, 13), (16, 2 + h), (20, 13)], rgb("#ff8a20"))
    c.poly([(14, 13), (16, 6 + h), (18, 13)], rgb("#ffe080"))
    c.outline(OUT)
    c.glow(16, 9, 10, rgb("#ffb040"), 0.35)
    return c


def brazier(frame, flame="#ff8a20", core="#ffe080"):
    c = Canvas(32, 40)
    c.rect((14, 22, 18, 36), rgb("#4a4652"))
    c.rrect((8, 34, 24, 38), 1, rgb("#3a3640"))
    c.poly([(6, 16), (26, 16), (22, 24), (10, 24)], rgb("#6a6672"))
    h = (0, 3, 1)[frame]
    c.poly([(8, 16), (12, 6 + h), (16, 0 + h), (20, 6 + h), (24, 16)], rgb(flame))
    c.poly([(12, 16), (16, 6 + h), (20, 16)], rgb(core))
    c.outline(OUT)
    c.glow(16, 10, 14, rgb(flame), 0.35)
    return c


def campfire(frame):
    c = Canvas(48, 40)
    for i in range(7):
        a = i / 7 * math.pi * 2
        c.ellipse((20 + math.cos(a) * 16, 28 + math.sin(a) * 7, 28 + math.cos(a) * 16, 34 + math.sin(a) * 7), rgb("#7a7470"))
    c.line([(12, 32), (36, 26)], rgb("#6a4428"), 4)
    c.line([(12, 26), (36, 32)], rgb("#5a3a22"), 4)
    h = (0, 3, 1)[frame]
    c.poly([(14, 28), (18, 14 + h), (22, 20), (24, 6 + h), (28, 18), (31, 12 + h), (34, 28)], rgb("#ff7a1a"))
    c.poly([(19, 28), (24, 14 + h), (29, 28)], rgb("#ffe07a"))
    c.outline(OUT)
    c.glow(24, 20, 18, rgb("#ff9a30"), 0.4)
    return c


def tent():
    c = Canvas(80, 64)
    cloth, dark = rgb("#9a7a52"), rgb("#6a5236")
    c.poly([(6, 58), (40, 6), (74, 58)], cloth)
    c.poly([(40, 6), (74, 58), (56, 58)], shade(cloth, -30))
    c.poly([(30, 58), (40, 26), (50, 58)], rgb("#2a1e18"))
    c.poly([(30, 58), (40, 26), (36, 58)], dark)
    c.line([(40, 6), (40, 0)], rgb("#5a3a22"), 2)
    c.poly([(40, 0), (50, 3), (40, 6)], rgb("#c4303c"))
    for x in (6, 74):
        c.line([(x, 58), (x + (6 if x < 40 else -6), 62)], rgb("#5a4030"))
    return c.outline(OUT)


def bed():
    c = Canvas(40, 70)
    wood, fur, pillow = rgb("#6a4228"), rgb("#8a6a4a"), rgb("#e8e0d0")
    c.rrect((2, 6, 37, 66), 3, wood)
    c.rrect((5, 10, 34, 63), 3, rgb("#3a4a7a"))          # 이불
    c.rrect((6, 12, 33, 22), 4, pillow)
    c.poly([(5, 30), (34, 26), (34, 63), (5, 63)], fur)  # 털가죽
    for k in range(6):
        c.line([(8 + k * 5, 32), (6 + k * 5, 60)], shade(fur, -25))
    c.rect((0, 0, 6, 14), wood); c.rect((33, 0, 39, 14), wood)     # 기둥
    c.rect((0, 58, 6, 69), wood); c.rect((33, 58, 39, 69), wood)
    c.flat_ellipse((1, 0, 5, 3), rgb("#e0b040")); c.flat_ellipse((34, 0, 38, 3), rgb("#e0b040"))
    return c.outline(OUT)


def forge(frame):
    """대장간 112x96: 돌 화덕(굴뚝·불꽃), 모루와 망치, 물통, 도구걸이."""
    c = Canvas(112, 96)
    stone = rgb("#6a6470")
    # 굴뚝
    c.rect((18, 0, 34, 40), shade(stone, -10))
    for y in range(4, 40, 7):
        c.line([(18, y), (34, y)], shade(stone, -34))
    # 화덕
    c.rrect((4, 30, 52, 92), 6, stone)
    for y in range(36, 92, 9):
        off = 0 if (y // 9) % 2 else 6
        for x in range(4 + off, 52, 12):
            c.line([(x, y), (x, y + 8)], shade(stone, -30))
        c.line([(4, y), (52, y)], shade(stone, -30))
    c.rrect((14, 52, 42, 80), 8, rgb("#1a0c08"))
    h = (0, 2, 1)[frame]
    c.poly([(16, 80), (20, 62 + h), (25, 70), (28, 56 + h), (32, 68), (36, 60 + h), (40, 80)], rgb("#ff6a1a"))
    c.poly([(21, 80), (28, 64 + h), (35, 80)], rgb("#ffe07a"))
    # 모루
    c.rrect((60, 54, 104, 62), 2, rgb("#4a4e5a"))
    c.poly([(60, 54), (52, 58), (60, 62)], rgb("#4a4e5a"))
    c.rect((72, 62, 92, 70), rgb("#3a3e48"))
    c.rrect((66, 70, 98, 76), 2, rgb("#3a3e48"))
    c.rrect((62, 76, 102, 92), 3, rgb("#6a4428"))        # 나무 받침
    c.line([(61, 54), (103, 54)], rgb("#b0b8c8"))
    # 망치
    c.line([(84, 52), (100, 38)], rgb("#7a5030"), 3)
    c.rrect((96, 32, 106, 42), 1, rgb("#5a5e6a"))
    # 빛나는 쇳조각
    c.rrect((70, 50, 82, 54), 1, rgb("#ff9a40"))
    c.outline(OUT)
    c.glow(28, 70, 26, rgb("#ff7a20"), 0.45)
    c.glow(76, 52, 8, rgb("#ffb060"), 0.5)
    return c


def barrel():
    c = Canvas(24, 28)
    c.rrect((2, 2, 21, 26), 6, rgb("#7a5030"))
    c.flat_rect((2, 7, 21, 8), rgb("#5a5e6a")); c.flat_rect((2, 19, 21, 20), rgb("#5a5e6a"))
    c.flat_ellipse((4, 1, 19, 6), rgb("#9a6a40"))
    return c.outline(OUT)


def crate():
    c = Canvas(28, 28)
    c.rect((1, 4, 26, 26), rgb("#8a6238"))
    c.rect((1, 1, 26, 6), rgb("#a87a48"))
    c.line([(3, 8), (24, 24)], rgb("#5a3a20"), 2); c.line([(24, 8), (3, 24)], rgb("#5a3a20"), 2)
    return c.outline(OUT)


def weapon_rack():
    c = Canvas(40, 48)
    c.rect((2, 18, 37, 22), rgb("#6a4428")); c.rect((2, 36, 37, 40), rgb("#6a4428"))
    c.rect((4, 16, 7, 47), rgb("#5a3a20")); c.rect((32, 16, 35, 47), rgb("#5a3a20"))
    for i, col in enumerate(("#a8b0c0", "#c04050", "#90c8ff")):
        x = 11 + i * 8
        c.line([(x, 4), (x, 44)], rgb(col), 2)
        c.rect((x - 3, 30, x + 3, 31), rgb("#c0a040"))
    return c.outline(OUT)


def support_beam():
    c = Canvas(64, 64)
    wood = rgb("#6a4428")
    c.rect((4, 6, 12, 63), wood); c.rect((52, 6, 60, 63), wood)
    c.rect((0, 2, 63, 10), shade(wood, 10))
    c.line([(12, 18), (22, 10)], wood, 3); c.line([(52, 18), (42, 10)], wood, 3)
    return c.outline(OUT)


def lantern(frame):
    c = Canvas(24, 32)
    c.line([(12, 0), (12, 6)], rgb("#3a3a40"))
    c.rrect((6, 6, 17, 22), 2, rgb("#3a3a40"))
    c.rrect((8, 9, 15, 19), 1, rgb("#ffd060") if frame != 1 else rgb("#ffe890"))
    c.outline(OUT)
    c.glow(12, 14, 11, rgb("#ffc040"), 0.35)
    return c


def minecart():
    c = Canvas(48, 36)
    c.poly([(4, 8), (44, 8), (40, 28), (8, 28)], rgb("#5a5e6a"))
    c.rect((6, 4, 42, 10), rgb("#7a7e8a"))
    for x in (8, 20, 30):
        c.ellipse((x, 2, x + 10, 10), rgb("#3c3c48"))
    c.ellipse((8, 26, 18, 35), rgb("#2a2a30")); c.ellipse((30, 26, 40, 35), rgb("#2a2a30"))
    return c.outline(OUT)


def rocks():
    c = Canvas(40, 28)
    c.ellipse((2, 8, 22, 26), rgb("#6a5e58")); c.ellipse((16, 4, 38, 26), rgb("#5a504c"))
    c.ellipse((10, 16, 24, 27), rgb("#7a6e66"))
    return c.outline(OUT)


def banner():
    c = Canvas(32, 48)
    c.rect((2, 2, 29, 4), rgb("#5a4a3a"))
    c.poly([(5, 4), (27, 4), (27, 40), (16, 34), (5, 40)], rgb("#8a1a2a"))
    c.poly([(12, 12), (20, 12), (16, 26)], rgb("#e0b040"))
    c.ellipse((13, 14, 19, 20), rgb("#2a0a10"))
    return c.outline(OUT)


def pillar(broken):
    c = Canvas(32, 72)
    st = rgb("#7a6e78")
    top = 30 if broken else 6
    c.rect((7, top, 24, 64), st)
    for x in (11, 16, 21):
        c.line([(x, top + 2), (x, 63)], shade(st, -20))
    c.rect((3, 62, 28, 70), shade(st, -10))
    if broken:
        c.poly([(7, top), (12, top - 6), (17, top + 2), (22, top - 4), (24, top)], st)
        c.ellipse((26, 62, 31, 68), shade(st, -15))
    else:
        c.rect((3, 0, 28, 8), shade(st, -10))
    return c.outline(OUT)


def bones():
    c = Canvas(36, 22)
    b = rgb("#ddd6c2")
    c.line([(4, 16), (20, 10)], b, 3); c.line([(14, 18), (30, 12)], b, 3)
    c.ellipse((20, 4, 32, 14), b)
    c.flat_rect((23, 8, 24, 9), rgb("#1a1010")); c.flat_rect((27, 8, 28, 9), rgb("#1a1010"))
    return c.outline(OUT)


def candelabra(frame):
    c = Canvas(28, 44)
    gold = rgb("#b08a3a")
    c.rect((12, 14, 15, 40), gold); c.rrect((6, 38, 21, 43), 1, gold)
    c.line([(4, 18), (23, 18)], gold, 2)
    for x in (4, 13, 22):
        c.rect((x - 1, 10, x + 1, 17), rgb("#e8e0d0"))
        h = (0, 1, 2)[(frame + x) % 3]
        c.poly([(x - 2, 10), (x, 3 + h), (x + 2, 10)], rgb("#ffb040"))
    c.outline(OUT)
    c.glow(13, 7, 12, rgb("#ffb040"), 0.35)
    return c


def crystal(color):
    c = Canvas(32, 44)
    col = rgb(color)
    c.poly([(8, 42), (6, 18), (12, 6), (16, 20), (14, 42)], shade(col, -20))
    c.poly([(14, 42), (16, 10), (22, 0), (26, 14), (24, 42)], col)
    c.poly([(22, 42), (24, 24), (29, 18), (30, 42)], shade(col, -30))
    c.line([(18, 12), (20, 34)], shade(col, 60))
    c.outline(OUT)
    c.glow(18, 20, 16, col, 0.35)
    return c


def flesh_spike():
    c = Canvas(32, 48)
    c.poly([(6, 46), (14, 4), (20, 46)], rgb("#5a1a24"))
    c.poly([(16, 46), (24, 16), (28, 46)], rgb("#4a121c"))
    c.line([(14, 10), (14, 40)], rgb("#ff5030"))
    return c.outline(OUT)


def rug():
    c = Canvas(96, 64)
    c.rrect((2, 2, 93, 61), 6, rgb("#6a2a2a"))
    c.rrect((8, 8, 87, 55), 4, rgb("#8a3a32"))
    for x in range(12, 86, 12):
        c.flat_poly([(x, 32), (x + 6, 26), (x + 12, 32), (x + 6, 38)], rgb("#c8a050"))
    return c
