"""
마을 그림 (이야기 그림의 마을 느낌): 반목조 집과 붉은 기와지붕, 종탑, 덩굴 덮인 우물, 줄무늬 차양 노점,
빵 바구니, 꽃, 가로등, 소나무, 던전 출구 계단, 마을 사람 6명(평소·환호·수정 속), 수정 감옥.
"""
import math
import random

from art import Canvas, rgb, shade, mix

OUT = (14, 10, 18, 255)

PLASTER = rgb("#efe2c4")
BEAM = rgb("#6a4026")
ROOF = rgb("#b8452e")
ROOF_D = rgb("#8a2e22")
STONE = rgb("#9a9488")
GLOW = rgb("#ffcf6a")
IVY = rgb("#4f7a34")
PINK, WHITE, LILAC = rgb("#f08ab0"), rgb("#f6f2e8"), rgb("#b892e8")


def _flowers(c, x0, y0, x1, y1, n, rnd, colors=(PINK, WHITE, LILAC)):
    for _ in range(n):
        x, y = rnd.randint(x0, x1), rnd.randint(y0, y1)
        col = rnd.choice(colors)
        c.px(x, y, col); c.px(x + 1, y, col); c.px(x, y + 1, col); c.px(x + 1, y + 1, shade(col, -30))
        c.px(x, y - 1, shade(col, 20))


def _ivy(c, x0, y0, x1, y1, n, rnd):
    for _ in range(n):
        x, y = rnd.randint(x0, x1), rnd.randint(y0, y1)
        c.flat_ellipse((x - 2, y - 1, x + 2, y + 2), rnd.choice((IVY, shade(IVY, 20), shade(IVY, -18))))


def _window(c, x, y, w, h):
    c.rect((x - 2, y - 2, x + w + 1, y + h + 1), BEAM, flat=True)
    c.rect((x, y, x + w - 1, y + h - 1), GLOW, flat=True)
    c.rect((x, y + h // 2, x + w - 1, y + h - 1), shade(GLOW, -30), flat=True)
    c.line([(x + w // 2, y), (x + w // 2, y + h - 1)], BEAM)
    c.line([(x, y + h // 2), (x + w - 1, y + h // 2)], BEAM)
    c.glow(x + w / 2, y + h / 2, max(w, h) * 1.1, GLOW, 0.35)


# ===================================================================== 집·종탑
def house(variant):
    """반목조 집 112x128. variant 0: 2층 + 화분, 1: 넓은 1.5층 + 덩굴."""
    rnd = random.Random(40 + variant)
    c = Canvas(112, 128)
    roof = ROOF if variant == 0 else shade(ROOF, -12)
    # 벽
    c.rect((8, 50, 103, 125), PLASTER, hi=10, lo=-18)
    # 반목조 들보
    for x in (8, 39, 72, 103):
        c.rect((x - 2, 50, x + 1, 125), BEAM, flat=True)
    c.rect((6, 86, 105, 89), BEAM, flat=True)
    c.rect((6, 50, 105, 53), BEAM, flat=True)
    c.line([(10, 88), (37, 54)], BEAM, 2); c.line([(74, 54), (101, 88)], BEAM, 2)
    # 창문과 문
    _window(c, 47, 58, 18, 20)
    _window(c, 16, 96, 16, 16)
    _window(c, 80, 96, 16, 16)
    c.rrect((46, 96, 66, 125), 5, rgb("#5a3420"))
    c.line([(56, 98), (56, 125)], rgb("#3a2010"))
    c.flat_ellipse((60, 110, 62, 112), rgb("#e8c060"))
    # 화분 (창 아래)
    for (x0, x1, y) in ((14, 34, 113), (78, 98, 113)):
        c.rect((x0, y, x1, y + 4), rgb("#7a4a2a"), flat=True)
        _ivy(c, x0, y - 3, x1, y + 1, 10, rnd)
        _flowers(c, x0, y - 4, x1, y, 8, rnd)
    # 지붕 (기와)
    c.poly([(0, 54), (56, 6), (111, 54)], roof)
    for k, y in enumerate(range(12, 54, 6)):
        half = (y - 6) * 56 / 48
        x0, x1 = 56 - half, 56 + half
        for x in range(int(x0) + (k % 2) * 4, int(x1), 8):
            c.line([(x, y), (x + 4, y + 4)], shade(roof, -26))
        c.line([(x0, y), (x1, y)], shade(roof, 18))
    c.poly([(0, 54), (56, 6), (60, 10), (8, 56)], shade(roof, 26))
    # 굴뚝
    c.rect((78, 14, 88, 36), rgb("#8a6a5a"))
    c.rect((76, 12, 90, 16), rgb("#6a4a3a"))
    # 처마 덩굴·꽃
    _ivy(c, 4, 50, 108, 58, 30, rnd)
    _flowers(c, 6, 50, 106, 56, 18, rnd)
    if variant == 1:
        _ivy(c, 92, 60, 106, 118, 26, rnd)
        _flowers(c, 92, 60, 106, 118, 10, rnd)
    c.outline(OUT)
    return c


def bell_tower():
    """돌 종탑 64x176: 아치 속 종, 십자가 꼭대기, 불 켜진 창."""
    rnd = random.Random(7)
    c = Canvas(64, 176)
    stone = rgb("#c8beb0")
    c.rect((8, 50, 55, 173), stone, hi=12, lo=-20)
    for y in range(54, 172, 8):
        off = 0 if (y // 8) % 2 else 6
        c.line([(8, y), (55, y)], shade(stone, -28))
        for x in range(8 + off, 55, 12):
            c.line([(x, y), (x, y + 7)], shade(stone, -28))
    # 종이 걸린 아치
    c.rect((4, 40, 59, 52), shade(stone, -10))
    c.rrect((16, 56, 47, 90), 14, rgb("#2a2430"))
    c.line([(31, 58), (31, 64)], rgb("#5a4a3a"))
    c.poly([(22, 80), (25, 66), (31, 63), (37, 66), (40, 80)], rgb("#c89a40"))
    c.flat_ellipse((29, 79, 33, 83), rgb("#8a6a2a"))
    _window(c, 25, 112, 14, 22)
    # 지붕 + 십자가
    c.poly([(0, 42), (32, 10), (63, 42)], rgb("#3a6a6a"))
    c.poly([(0, 42), (32, 10), (34, 14), (8, 42)], rgb("#4f8a86"))
    c.line([(32, 10), (32, 0)], rgb("#3a3030"), 2)
    c.line([(28, 4), (36, 4)], rgb("#3a3030"), 2)
    _ivy(c, 8, 140, 55, 172, 30, rnd)
    _flowers(c, 8, 150, 55, 172, 10, rnd)
    c.outline(OUT)
    c.glow(31, 74, 20, GLOW, 0.25)
    return c


# ===================================================================== 광장 소품
def well():
    """덩굴 덮인 돌 우물 96x80: 이야기의 시작점. 가운데는 던전으로 이어지는 어둠."""
    rnd = random.Random(3)
    c = Canvas(96, 80)
    # 우물 몸통 (원통)
    c.rrect((6, 26, 89, 76), 10, STONE, hi=14, lo=-26)
    for y in (38, 50, 62):
        c.line([(8, y), (88, y)], shade(STONE, -34))
    for k, y in enumerate((26, 38, 50, 62)):
        for x in range(10 + (k % 2) * 8, 88, 16):
            c.line([(x, y), (x, y + 12)], shade(STONE, -34))
    # 윗면 테두리 + 구멍
    c.ellipse((4, 12, 91, 40), shade(STONE, 16), hi=16, lo=-16)
    c.flat_ellipse((14, 17, 81, 35), rgb("#1a1424"))
    c.flat_ellipse((22, 21, 73, 33), rgb("#0c0a14"))
    # 덩굴과 꽃
    _ivy(c, 6, 24, 90, 76, 90, rnd)
    _flowers(c, 6, 50, 90, 78, 22, rnd)
    _flowers(c, 4, 14, 92, 22, 10, rnd)
    c.outline(OUT)
    return c


def market_stall():
    """노점 96x72: 빨강·흰 줄무늬 차양, 나무 진열대, 빵 바구니."""
    c = Canvas(96, 72)
    wood = rgb("#7a4e2e")
    c.rect((6, 22, 9, 70), wood); c.rect((86, 22, 89, 70), wood)
    # 차양
    for i in range(8):
        x0 = 2 + i * 11.5
        col = rgb("#d23a36") if i % 2 == 0 else rgb("#f4ece0")
        c.poly([(x0, 6), (x0 + 11.5, 6), (x0 + 12.5, 24), (x0 - 1, 24)], col, dither=False)
        c.flat_ellipse((x0 - 1, 20, x0 + 12.5, 28), col)
    c.rect((0, 2, 95, 7), shade(wood, 10))
    # 진열대 + 빵
    c.rect((4, 44, 91, 66), wood)
    c.rect((4, 44, 91, 47), shade(wood, 26), flat=True)
    for (x, y) in ((14, 36), (30, 34), (46, 36), (62, 34), (78, 36)):
        c.ellipse((x - 7, y, x + 7, y + 9), rgb("#c98a44"), hi=30, lo=-26)
        c.line([(x - 3, y + 2), (x - 1, y + 6)], rgb("#e8b878")); c.line([(x + 2, y + 2), (x + 4, y + 6)], rgb("#e8b878"))
    c.ellipse((70, 40, 80, 46), rgb("#c83a2a"))
    return c.outline(OUT)


def bread_basket():
    c = Canvas(40, 28)
    c.rrect((2, 12, 37, 26), 4, rgb("#8a5a30"))
    for x in range(4, 36, 5):
        c.line([(x, 13), (x + 2, 25)], rgb("#6a4020"))
    c.rect((6, 8, 22, 14), rgb("#f2ece0"), flat=True)
    c.line([(6, 10), (22, 10)], rgb("#c83a36"))
    for (x, y) in ((22, 6), (30, 8)):
        c.ellipse((x - 6, y, x + 6, y + 8), rgb("#d0924a"), hi=30, lo=-26)
    return c.outline(OUT)


def flower_bush(variant):
    rnd = random.Random(90 + variant)
    c = Canvas(40, 28)
    c.ellipse((2, 6, 37, 27), IVY, hi=18, lo=-24)
    _ivy(c, 4, 8, 35, 24, 20, rnd)
    cols = ((PINK, WHITE), (LILAC, WHITE), (rgb("#ffd060"), PINK))[variant % 3]
    _flowers(c, 4, 6, 35, 22, 16, rnd, cols)
    return c.outline(OUT)


def lamp_post(frame):
    c = Canvas(24, 64)
    c.rect((10, 20, 13, 62), rgb("#2c2a30"))
    c.rect((6, 58, 17, 63), rgb("#2c2a30"))
    c.rect((5, 8, 18, 22), rgb("#3a3640"))
    c.rect((7, 10, 16, 20), shade(GLOW, (0, 18, 8)[frame]), flat=True)
    c.poly([(4, 8), (12, 2), (19, 8)], rgb("#3a3640"))
    c.outline(OUT)
    c.glow(12, 15, 12, GLOW, 0.5)
    return c


def pine():
    c = Canvas(48, 88)
    c.rect((21, 70, 26, 87), rgb("#5a3a22"))
    greens = (rgb("#2f5a3a"), rgb("#3a6e44"), rgb("#468052"))
    for i, y in enumerate((8, 24, 40, 54)):
        w = 10 + i * 6
        c.poly([(24, y), (24 + w, y + 22), (24 - w, y + 22)], greens[i % 3])
        c.poly([(24, y), (24 - w, y + 22), (24 - w + 6, y + 22)], shade(greens[i % 3], 20))
    return c.outline(OUT)


def exit_stairs():
    """던전 출구 계단 96x48: 위(마을)에서 햇빛이 새어 든다."""
    c = Canvas(96, 48)
    for i in range(6):
        y = 6 + i * 7
        c.rect((6 + i * 2, y, 89 - i * 2, y + 6), shade(STONE, 18 - i * 8), hi=10, lo=-14, dither=False)
    c.outline(OUT)
    c.glow(48, 8, 40, rgb("#ffe8a0"), 0.55)
    return c


def crystal_prison(frame):
    """수정 감옥 44x60: 반투명 보랏빛 육각 수정. 사람 위에 겹쳐 그린다."""
    c = Canvas(44, 60)
    body = (178, 120, 255, 120)
    pts = [(22, 1), (40, 14), (40, 46), (22, 59), (4, 46), (4, 14)]
    c.flat_poly(pts, body)
    c.flat_poly([(22, 1), (40, 14), (22, 22), (4, 14)], (210, 170, 255, 110))
    c.flat_poly([(22, 22), (40, 14), (40, 46), (22, 59)], (120, 70, 220, 110))
    for a, b in zip(pts, pts[1:] + pts[:1]):
        c.line([a, b], (235, 210, 255, 230), 1)
    c.line([(22, 1), (22, 22)], (235, 210, 255, 160))
    c.line([(4, 14), (22, 22), (40, 14)], (235, 210, 255, 160))
    sx = 10 + frame * 8
    c.line([(sx, 18), (sx + 3, 12)], (255, 255, 255, 220), 1)
    return c


# ===================================================================== 마을 사람
def villager(kind, pose):
    """마을 사람 32x40. pose 0: 웃는 얼굴, 1: 환호(팔을 들고), 2: 수정 속(눈 감고 손 모음)."""
    c = Canvas(32, 40)
    skin = rgb("#f0c8a0")
    spec = {
        "baker": dict(hair=rgb("#6a3a1e"), top=rgb("#f2eadc"), bottom=rgb("#4a3a32"), beard=True, apron=True),
        "redhood": dict(hair=rgb("#8a5a3a"), top=rgb("#b4302c"), bottom=rgb("#f2eadc"), hood=True, dress=True),
        "child": dict(hair=rgb("#7a4a26"), top=rgb("#4f7a3a"), bottom=rgb("#3a4a5a"), small=True),
        "elder": dict(hair=rgb("#eceae4"), top=rgb("#5a3a6a"), bottom=rgb("#5a3a6a"), beard=True, robe=True, cane=True),
        "maid": dict(hair=rgb("#a06a3a"), top=rgb("#8a5a7a"), bottom=rgb("#f2eadc"), cap=True, dress=True),
        "boy": dict(hair=rgb("#c09040"), top=rgb("#6a7a9a"), bottom=rgb("#5a5a6a"), small=True),
    }[kind]
    sm = 6 if spec.get("small") else 0
    top, bottom = spec["top"], spec["bottom"]
    # 다리·치마
    if spec.get("dress") or spec.get("robe"):
        c.poly([(9, 22 + sm // 2), (23, 22 + sm // 2), (26, 38), (6, 38)], bottom if spec.get("dress") else top)
        if spec.get("dress"):
            c.rect((10, 24, 22, 36), rgb("#f6f0e4"), flat=True) if kind == "redhood" else None
    else:
        c.rect((11, 28 + sm // 2, 14, 37), bottom); c.rect((18, 28 + sm // 2, 21, 37), bottom)
        c.rrect((10, 35, 15, 39), 1, rgb("#5a3a22")); c.rrect((17, 35, 22, 39), 1, rgb("#5a3a22"))
    # 몸통
    c.rrect((9, 16 + sm, 23, 30 + sm // 2), 3, top)
    if spec.get("apron"):
        c.rect((11, 20, 21, 32), rgb("#faf6ee"), flat=True)
        c.line([(11, 19), (14, 16)], rgb("#6a4a2a")); c.line([(21, 19), (18, 16)], rgb("#6a4a2a"))
    # 팔
    if pose == 1:
        c.line([(9, 18 + sm), (4, 8 + sm)], top, 3); c.line([(23, 18 + sm), (28, 8 + sm)], top, 3)
        c.flat_ellipse((2, 5 + sm, 6, 9 + sm), skin); c.flat_ellipse((26, 5 + sm, 30, 9 + sm), skin)
    elif pose == 2:
        c.line([(9, 19 + sm), (14, 24 + sm)], top, 3); c.line([(23, 19 + sm), (18, 24 + sm)], top, 3)
        c.flat_ellipse((14, 22 + sm, 18, 26 + sm), skin)
    else:
        c.line([(9, 18 + sm), (7, 27 + sm)], top, 3); c.line([(23, 18 + sm), (25, 27 + sm)], top, 3)
        c.flat_ellipse((5, 26 + sm, 9, 29 + sm), skin); c.flat_ellipse((23, 26 + sm, 27, 29 + sm), skin)
    if spec.get("cane"):
        c.line([(27, 18), (28, 38)], rgb("#8a6a3a"), 2)
    # 머리
    hy = 3 + sm
    c.ellipse((10, hy, 22, hy + 13), skin)
    if spec.get("hood"):
        c.poly([(8, hy + 14), (9, hy + 2), (16, hy - 2), (23, hy + 2), (24, hy + 14), (21, hy + 6), (11, hy + 6)], top)
    elif spec.get("cap"):
        c.flat_ellipse((9, hy - 1, 23, hy + 6), rgb("#f6f0e4"))
    else:
        c.flat_ellipse((9, hy - 1, 23, hy + 6), spec["hair"])
        c.poly([(9, hy + 2), (12, hy + 2), (10, hy + 9)], spec["hair"])
        c.poly([(20, hy + 2), (23, hy + 2), (22, hy + 9)], spec["hair"])
    if spec.get("beard"):
        c.poly([(11, hy + 9), (21, hy + 9), (19, hy + 16), (16, hy + 18), (13, hy + 16)], spec["hair"])
    # 얼굴
    eye = rgb("#2a1a14")
    if pose == 2:
        c.line([(13, hy + 7), (14, hy + 8)], eye); c.line([(18, hy + 8), (19, hy + 7)], eye)
    else:
        c.line([(12, hy + 7), (13, hy + 6), (14, hy + 7)], eye); c.line([(18, hy + 7), (19, hy + 6), (20, hy + 7)], eye)
        c.px(15, hy + 10, rgb("#c0504a")); c.px(16, hy + 10, rgb("#c0504a"))
        c.px(12, hy + 9, rgb("#f0a0a0")); c.px(20, hy + 9, rgb("#f0a0a0"))
    return c.outline(OUT)


VILLAGERS = ["baker", "redhood", "child", "elder", "maid", "boy"]
