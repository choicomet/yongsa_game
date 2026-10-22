"""용사·잡몹·보스 임시 그림 (art.Canvas로 그린다)."""
import math
from art import Canvas, rgb, shade, mix

OUT = (14, 10, 18, 255)

# ===================================================================== 용사
SKIN, SKIN_D = rgb("#f2c9a0"), rgb("#c9946f")
# 은발, 청록 망토, 가죽 장비 (이야기 그림의 용사)
HAIR, HAIR_H = rgb("#d8dee8"), rgb("#ffffff")
STEEL, STEEL_D = rgb("#23808e"), rgb("#2c2834")
CAPE, CAPE_D = rgb("#1d7486"), rgb("#114a58")
SCARF = rgb("#2aa2b2")
LEATHER, BOOT = rgb("#8a5a32"), rgb("#6a3e22")
GOLD = rgb("#e6b84a")
EYE = rgb("#1b2440")


def hero(direction, frame):
    c = Canvas(32, 32)
    bob = 0 if frame == 0 else -1
    # 다리 위치 (걷기)
    ll, rl = (0, 0)
    if frame == 1:
        ll, rl = 1, -1
    elif frame == 2:
        ll, rl = -1, 1

    if direction == "down":
        # 망토(뒤)
        c.poly([(10, 14 + bob), (22, 14 + bob), (25, 28), (7, 28)], CAPE_D)
        # 다리·부츠
        c.rect((12, 22 + bob, 15, 27 + ll), STEEL_D)
        c.rect((17, 22 + bob, 20, 27 + rl), STEEL_D)
        c.rrect((11, 26 + ll, 15, 30 + ll), 1, BOOT)
        c.rrect((17, 26 + rl, 21, 30 + rl), 1, BOOT)
        # 몸통 갑옷
        c.rrect((10, 14 + bob, 21, 23 + bob), 3, STEEL)
        c.rect((15, 15 + bob, 16, 21 + bob), shade(STEEL, 30), flat=True)
        c.rect((10, 21 + bob, 21, 22 + bob), LEATHER, flat=True)
        c.rect((15, 21 + bob, 16, 22 + bob), GOLD, flat=True)
        # 어깨 보호대
        c.ellipse((7, 13 + bob, 13, 19 + bob), STEEL)
        c.ellipse((18, 13 + bob, 24, 19 + bob), STEEL)
        # 목도리
        c.rrect((10, 12 + bob, 21, 15 + bob), 2, SCARF)
        # 머리
        c.ellipse((10, 2 + bob, 21, 13 + bob), SKIN)
        c.ellipse((9, 1 + bob, 22, 8 + bob), HAIR)
        for (x0, tip) in ((8, (6, 0)), (12, (12, -2)), (16, (18, -2)), (20, (25, 1))):
            c.poly([(x0, 5 + bob), (x0 + 4, 4 + bob), (tip[0], tip[1] + bob + 2)], HAIR)
        c.poly([(9, 5 + bob), (13, 5 + bob), (11, 10 + bob)], HAIR)
        c.poly([(18, 5 + bob), (22, 5 + bob), (21, 10 + bob)], HAIR)
        c.poly([(13, 5 + bob), (17, 5 + bob), (15, 8 + bob)], HAIR)
        c.px(12, 3 + bob, HAIR_H); c.px(13, 2 + bob, HAIR_H); c.px(14, 2 + bob, HAIR_H)
        for x in (13, 18):
            c.flat_rect((x, 9 + bob, x, 10 + bob), EYE)
        c.px(13, 9 + bob, rgb("#7fb7ff")); c.px(18, 9 + bob, rgb("#7fb7ff"))
    elif direction == "up":
        c.rect((12, 22 + bob, 15, 27 + ll), STEEL_D)
        c.rect((17, 22 + bob, 20, 27 + rl), STEEL_D)
        c.rrect((11, 26 + ll, 15, 30 + ll), 1, BOOT)
        c.rrect((17, 26 + rl, 21, 30 + rl), 1, BOOT)
        c.ellipse((7, 13 + bob, 13, 19 + bob), STEEL)
        c.ellipse((18, 13 + bob, 24, 19 + bob), STEEL)
        c.poly([(9, 13 + bob), (23, 13 + bob), (26, 28 + bob // 2), (6, 28 + bob // 2)], CAPE)
        c.line([(16, 15 + bob), (16, 27)], CAPE_D)
        c.rrect((10, 11 + bob, 21, 15 + bob), 2, SCARF)
        c.poly([(19, 14 + bob), (22, 14 + bob), (24, 20 + bob), (21, 19 + bob)], shade(SCARF, -20))
        c.ellipse((10, 2 + bob, 21, 13 + bob), HAIR)
        for (x0, tip) in ((8, (6, 1)), (12, (12, -1)), (16, (18, -1)), (20, (25, 2))):
            c.poly([(x0, 6 + bob), (x0 + 4, 5 + bob), (tip[0], tip[1] + bob + 2)], HAIR)
        c.px(13, 4 + bob, HAIR_H); c.px(14, 3 + bob, HAIR_H); c.px(15, 3 + bob, HAIR_H)
    else:  # side: 오른쪽을 본다
        c.poly([(9, 14 + bob), (15, 14 + bob), (14, 28), (5, 27 + bob)], CAPE_D)
        back, front = (ll, rl)
        c.rect((13, 22 + bob, 16, 27 + back), shade(STEEL_D, -15))
        c.rrect((12 + back, 26 + back, 16 + back, 30 + back), 1, shade(BOOT, -10))
        c.rect((15, 22 + bob, 18, 27 + front), STEEL_D)
        c.rrect((15 - front, 26 + front, 20 - front, 30 + front), 1, BOOT)
        c.rrect((12, 14 + bob, 20, 23 + bob), 3, STEEL)
        c.rect((12, 21 + bob, 20, 22 + bob), LEATHER, flat=True)
        c.ellipse((12, 13 + bob, 18, 19 + bob), STEEL)
        c.rrect((12, 12 + bob, 20, 15 + bob), 2, SCARF)
        c.poly([(12, 13 + bob), (9, 15 + bob), (7, 19 + bob), (11, 16 + bob)], shade(SCARF, -15))
        c.ellipse((12, 2 + bob, 22, 13 + bob), SKIN)
        c.ellipse((10, 1 + bob, 21, 8 + bob), HAIR)
        for (x0, tip) in ((9, (4, 2)), (12, (9, -2)), (16, (16, -2)), (19, (23, 1))):
            c.poly([(x0, 5 + bob), (x0 + 4, 4 + bob), (tip[0], tip[1] + bob + 2)], HAIR)
        c.poly([(10, 4 + bob), (15, 4 + bob), (12, 12 + bob)], HAIR)
        c.poly([(17, 5 + bob), (21, 5 + bob), (21, 8 + bob)], HAIR)
        c.flat_rect((19, 9 + bob, 19, 10 + bob), EYE)
        c.px(19, 9 + bob, rgb("#7fb7ff"))
        c.px(21, 11 + bob, SKIN_D)
    return c.outline(OUT)


# ===================================================================== 잡몹
def zombie_miner(frame):
    """갱도 구울: 구부정한 광부 좀비, 안전모 등불."""
    c = Canvas(32, 32)
    skin = rgb("#7f9a6a")
    sway = 1 if frame else 0
    c.rect((12, 22, 15, 29 - sway), rgb("#4a3a30"))
    c.rect((17, 22, 20, 28 + sway), rgb("#4a3a30"))
    c.rrect((11, 27 - sway, 15, 30 - sway), 1, rgb("#2c2224"))
    c.rrect((17, 27 + sway, 21, 30 + sway), 1, rgb("#2c2224"))
    c.rrect((10, 13, 22, 23), 3, rgb("#7a5a3a"))
    c.poly([(10, 19), (14, 23), (10, 23)], rgb("#5a3f2a"))
    c.rect((12, 16, 13, 18), skin, flat=True)
    # 앞으로 뻗은 팔
    c.rrect((19, 15 + sway, 28, 18 + sway), 1, skin)
    c.rrect((4, 16 - sway, 12, 19 - sway), 1, skin)
    c.px(28, 16 + sway, shade(skin, -30)); c.px(4, 17 - sway, shade(skin, -30))
    # 머리 + 안전모
    c.ellipse((11, 5, 22, 15), skin)
    c.ellipse((10, 2, 23, 9), rgb("#c99a2e"))
    c.rect((10, 7, 23, 8), rgb("#8c6a1e"), flat=True)
    c.rrect((15, 3, 18, 6), 1, rgb("#fff1a0"))
    c.flat_rect((13, 10, 14, 11), rgb("#e8f0a0")); c.flat_rect((18, 10, 19, 11), rgb("#e8f0a0"))
    c.line([(14, 13), (18, 13)], shade(skin, -45))
    c.outline(OUT)
    c.glow(16, 4, 7, rgb("#fff3a0"), 0.35)
    return c


def skeleton_warrior(frame):
    """저주받은 해골 기사: 녹슨 갑옷, 붉은 눈, 검과 방패."""
    c = Canvas(32, 32)
    bone, armor = rgb("#ddd6c2"), rgb("#6e3b45")
    s = 1 if frame else 0
    c.line([(13, 22), (13 - s, 29)], bone, 2)
    c.line([(18, 22), (18 + s, 29)], bone, 2)
    c.rrect((10 - s, 27, 14 - s, 30), 1, rgb("#3c3036"))
    c.rrect((17 + s, 27, 21 + s, 30), 1, rgb("#3c3036"))
    c.rrect((11, 13, 21, 22), 2, armor)
    c.line([(16, 14), (16, 21)], shade(armor, -30))
    c.ellipse((4, 12, 13, 23), rgb("#8a8f9a"))           # 방패
    c.ellipse((7, 15, 10, 19), rgb("#b04048"))
    c.line([(22, 21), (27, 5)], rgb("#c9ced8"), 2)       # 검
    c.line([(20, 19), (24, 21)], rgb("#8a6a2a"), 2)
    c.ellipse((11, 2, 21, 12), bone)
    c.poly([(11, 4), (13, 0), (14, 4)], rgb("#9a9488"))  # 투구 뿔
    c.poly([(18, 4), (19, 0), (21, 4)], rgb("#9a9488"))
    c.flat_rect((13, 6, 14, 8), rgb("#1a0a10")); c.flat_rect((17, 6, 18, 8), rgb("#1a0a10"))
    c.px(13, 7, rgb("#ff4040")); c.px(18, 7, rgb("#ff4040"))
    c.line([(14, 10), (18, 10)], rgb("#5a5448"))
    c.outline(OUT)
    c.glow(16, 7, 6, rgb("#ff3030"), 0.25)
    return c


def drakeling(frame):
    """탑의 비룡: 불을 뱉는 새끼 용 (원거리)."""
    c = Canvas(40, 32)
    body, belly = rgb("#5b3fa8"), rgb("#e0b070")
    w = -3 if frame else 0
    c.poly([(14, 14), (3, 4 + w), (6, 14), (2, 16 + w // 2), (14, 19)], shade(body, -40))
    c.poly([(26, 14), (37, 4 + w), (34, 14), (38, 16 + w // 2), (26, 19)], shade(body, -40))
    c.poly([(16, 26), (8, 30), (6, 27), (14, 24)], shade(body, -20))  # 꼬리
    c.ellipse((13, 12, 27, 27), body)
    c.ellipse((16, 17, 24, 26), belly)
    c.ellipse((14, 3, 26, 14), body)
    c.poly([(15, 5), (13, 0), (17, 4)], rgb("#e8d090"))
    c.poly([(25, 5), (27, 0), (23, 4)], rgb("#e8d090"))
    c.flat_rect((17, 7, 18, 8), rgb("#ffd040")); c.flat_rect((22, 7, 23, 8), rgb("#ffd040"))
    c.rrect((17, 10, 23, 13), 1, shade(body, 25))
    c.px(19, 12, rgb("#ff8030")); c.px(21, 12, rgb("#ff8030"))
    c.rect((15, 26, 17, 29), shade(body, -30)); c.rect((23, 26, 25, 29), shade(body, -30))
    return c.outline(OUT)


# ===================================================================== 보스
def zombie_king(pose):
    """좀비킹 96x96: 부풀어 오른 거구, 왕관, 찢어진 보라 망토. pose 0/1=대기, 2=돌진."""
    c = Canvas(96, 96)
    skin, flesh = rgb("#6f8f5a"), rgb("#a0544a")
    cape = rgb("#4a2a60")
    lean = 8 if pose == 2 else 0
    sway = 2 if pose == 1 else 0
    # 망토
    c.poly([(22 + lean, 30), (74 + lean, 30), (86, 90), (10, 90)], cape)
    c.poly([(20, 84), (30, 90), (14, 92)], shade(cape, -30))
    # 다리
    c.rrect((30, 70, 44, 92), 4, rgb("#4c3a3a"))
    c.rrect((54, 70, 68, 92), 4, rgb("#4c3a3a"))
    c.rrect((26, 86, 46, 94), 3, rgb("#2a2024"))
    c.rrect((52, 86, 72, 94), 3, rgb("#2a2024"))
    # 몸통 (배)
    c.ellipse((22 + lean, 34, 78 + lean, 82), skin)
    c.ellipse((34 + lean, 52, 58 + lean, 76), flesh)
    for i in range(4):
        c.line([(36 + lean + i * 5, 56), (38 + lean + i * 5, 72)], shade(flesh, -40))
    c.rrect((24 + lean, 66, 76 + lean, 72), 2, rgb("#5a3a22"))
    c.rrect((45 + lean, 65, 53 + lean, 73), 1, rgb("#e0b040"))
    # 팔 (사슬 족쇄)
    c.rrect((6 + lean, 38 - sway, 26 + lean, 74 - sway), 8, skin)
    c.rrect((72 + lean, 38 + sway, 92, 74 + sway), 8, skin)
    c.rrect((6 + lean, 58 - sway, 26 + lean, 64 - sway), 2, rgb("#7a7f8a"))
    c.rrect((72 + lean, 58 + sway, 92, 64 + sway), 2, rgb("#7a7f8a"))
    for k in range(3):
        c.ellipse((10 + lean + k * 5, 74 - sway + k * 3, 15 + lean + k * 5, 78 - sway + k * 3), rgb("#8a8f9a"))
    # 머리
    hx = 48 + lean + (4 if pose == 2 else 0)
    c.ellipse((hx - 16, 10, hx + 16, 40), skin)
    c.poly([(hx - 14, 12), (hx - 16, 0), (hx - 8, 8), (hx - 3, -2), (hx + 3, 8), (hx + 8, -2), (hx + 12, 8), (hx + 16, 0), (hx + 14, 12)],
           rgb("#e8c048"))
    c.ellipse((hx - 3, 2, hx + 3, 8), rgb("#d03040"))
    c.flat_rect((hx - 10, 20, hx - 5, 24), rgb("#200a08")); c.flat_rect((hx + 5, 20, hx + 10, 24), rgb("#200a08"))
    c.flat_rect((hx - 9, 21, hx - 7, 22), rgb("#ffe060")); c.flat_rect((hx + 6, 21, hx + 8, 22), rgb("#ffe060"))
    c.rrect((hx - 9, 29, hx + 9, 35), 2, rgb("#2a0e0e"))
    for k in range(5):
        c.px(hx - 7 + k * 4, 30, rgb("#e8e0c0")); c.px(hx - 6 + k * 4, 34, rgb("#e8e0c0"))
    c.outline(OUT)
    c.glow(hx - 8, 22, 6, rgb("#ffe060"), 0.4)
    c.glow(hx + 7, 22, 6, rgb("#ffe060"), 0.4)
    return c


def lich(pose):
    """리치 96x112: 떠 있는 해골 마법사, 녹색 영혼불 지팡이. pose 0/1=대기, 2=시전."""
    c = Canvas(96, 112)
    robe, trim = rgb("#2a2440"), rgb("#7a5ac8")
    bone = rgb("#e4dcc6")
    fl = 2 if pose == 1 else 0
    # 영혼 기운
    c.glow(48, 60, 46, rgb("#40ff90"), 0.18)
    # 로브 (아래가 해진 모양, 발 없음)
    pts = [(30, 34 + fl), (66, 34 + fl), (78, 96 + fl)]
    for i in range(7):
        x = 78 - i * 9
        pts.append((x - 4, 104 + fl - (i % 2) * 8))
    pts += [(18, 96 + fl)]
    c.poly(pts, robe)
    c.poly([(40, 36 + fl), (56, 36 + fl), (52, 96 + fl), (44, 96 + fl)], shade(robe, 18))
    c.rrect((30, 34 + fl, 66, 40 + fl), 2, trim)
    c.line([(48, 40 + fl), (48, 92 + fl)], trim)
    # 어깨 장식 (뼈 견갑)
    c.ellipse((20, 30 + fl, 40, 46 + fl), rgb("#b8ae96"))
    c.ellipse((56, 30 + fl, 76, 46 + fl), rgb("#b8ae96"))
    # 팔
    raise_ = pose == 2
    if raise_:
        c.line([(26, 40 + fl), (14, 14 + fl)], bone, 3)
        c.line([(70, 40 + fl), (82, 14 + fl)], bone, 3)
    else:
        c.line([(26, 42 + fl), (20, 64 + fl)], bone, 3)
        c.line([(70, 42 + fl), (78, 62 + fl)], bone, 3)
    # 지팡이
    sx = 84 if not raise_ else 84
    c.line([(sx, 18 + fl), (sx - 2, 104 + fl)], rgb("#5a4030"), 3)
    c.ellipse((sx - 7, 6 + fl, sx + 7, 20 + fl), bone)
    c.flat_rect((sx - 4, 11 + fl, sx - 2, 13 + fl), rgb("#103020")); c.flat_rect((sx + 2, 11 + fl, sx + 4, 13 + fl), rgb("#103020"))
    # 해골 머리 + 왕관
    c.ellipse((34, 6 + fl, 62, 34 + fl), bone)
    c.poly([(34, 12 + fl), (36, 0 + fl), (42, 8 + fl), (48, -2 + fl), (54, 8 + fl), (60, 0 + fl), (62, 12 + fl)], rgb("#6a5ab0"))
    c.flat_rect((39, 17 + fl, 45, 23 + fl), rgb("#0a1a12")); c.flat_rect((51, 17 + fl, 57, 23 + fl), rgb("#0a1a12"))
    c.flat_rect((41, 19 + fl, 43, 21 + fl), rgb("#60ffa0")); c.flat_rect((53, 19 + fl, 55, 21 + fl), rgb("#60ffa0"))
    c.poly([(47, 25 + fl), (49, 25 + fl), (48, 28 + fl)], rgb("#3a3428"))
    for k in range(5):
        c.px(41 + k * 3, 30 + fl, rgb("#3a3428"))
    c.outline(OUT)
    c.glow(84, 13 + fl, 14 if not raise_ else 20, rgb("#50ff90"), 0.6)
    c.glow(42, 20 + fl, 6, rgb("#60ffa0"), 0.5)
    c.glow(54, 20 + fl, 6, rgb("#60ffa0"), 0.5)
    if raise_:
        c.glow(14, 14 + fl, 12, rgb("#60ffa0"), 0.6)
        c.glow(82, 14 + fl, 12, rgb("#60ffa0"), 0.6)
    return c


SCALE, SCALE_D = rgb("#3a2440"), rgb("#24142a")
BELLY = rgb("#b07a4a")
HORN = rgb("#e6d6a8")


def dragon(pose):
    """마룡 176x144. pose: 0/1 대기(날개), 2 아래를 보며 포효, 3 위를 보며 포효, 4 엉덩이(꼬리) 들기, 5 회전."""
    c = Canvas(176, 144)
    wing = 0 if pose != 1 else 8
    rear = 14 if pose == 4 else 0
    # 날개 (뒤)
    for side in (-1, 1):
        cx = 88
        tip = (cx + side * 86, 8 + wing)
        pts = [(cx + side * 20, 52 - rear // 2), tip, (cx + side * 80, 40 + wing), (cx + side * 74, 70 + wing // 2),
               (cx + side * 62, 58), (cx + side * 54, 82), (cx + side * 40, 66), (cx + side * 30, 80)]
        c.poly(pts, rgb("#4a1e3a"))
        c.line([(cx + side * 20, 52 - rear // 2), tip], shade(SCALE, 40), 2)
        c.line([(cx + side * 30, 54), (cx + side * 62, 58)], SCALE_D)
        c.line([(cx + side * 30, 54), (cx + side * 54, 82)], SCALE_D)
    # 꼬리
    tail_y = 112 - rear * 3
    c.poly([(70, 108), (40, 120 - rear * 2), (16, tail_y), (8, tail_y - 6), (22, tail_y - 10), (50, 108 - rear), (74, 98)], SCALE)
    if pose == 4:
        for k in range(5):
            c.poly([(14 + k * 9, tail_y - 12 - k), (18 + k * 9, tail_y - 22 - k), (22 + k * 9, tail_y - 12 - k)], rgb("#a6e8ff"))
    # 다리
    c.rrect((52, 96 - rear, 72, 132 - rear), 6, SCALE_D)
    c.rrect((104, 96, 124, 132), 6, SCALE_D)
    for x in (52, 104):
        for k in range(3):
            c.poly([(x + 2 + k * 7, 130 - (rear if x == 52 else 0)), (x + 5 + k * 7, 138 - (rear if x == 52 else 0)), (x + 8 + k * 7, 130 - (rear if x == 52 else 0))], HORN)
    # 몸통
    c.ellipse((44, 52 - rear, 132, 118 - rear // 2), SCALE)
    c.ellipse((66, 66 - rear, 110, 116 - rear // 2), BELLY)
    for k in range(5):
        c.line([(70, 74 + k * 8 - rear), (106, 74 + k * 8 - rear)], shade(BELLY, -40))
    # 등 가시
    for k in range(6):
        x = 56 + k * 13
        c.poly([(x, 56 - rear), (x + 5, 44 - rear), (x + 10, 56 - rear)], shade(SCALE, 30))
    # 목·머리
    if pose == 2:      # 아래를 본다
        neck_end, head_y = (88, 92), 88
    elif pose == 3:    # 위를 본다
        neck_end, head_y = (88, 18), 6
    else:
        neck_end, head_y = (88, 40), 26
    c.poly([(74, 66 - rear), (102, 66 - rear), (neck_end[0] + 12, neck_end[1] + 10), (neck_end[0] - 12, neck_end[1] + 10)], SCALE)
    hy = head_y
    c.ellipse((66, hy, 110, hy + 36), SCALE)
    c.rrect((74, hy + 22, 102, hy + 40), 6, shade(SCALE, 12))
    c.poly([(70, hy + 8), (52, hy - 14), (76, hy + 2)], HORN)
    c.poly([(106, hy + 8), (124, hy - 14), (100, hy + 2)], HORN)
    c.poly([(80, hy + 4), (76, hy - 8), (86, hy + 2)], shade(HORN, -30))
    c.poly([(96, hy + 4), (100, hy - 8), (90, hy + 2)], shade(HORN, -30))
    eye = rgb("#ff9a20")
    c.flat_rect((76, hy + 14, 82, hy + 18), eye); c.flat_rect((94, hy + 14, 100, hy + 18), eye)
    c.flat_rect((78, hy + 15, 80, hy + 17), rgb("#3a0800")); c.flat_rect((96, hy + 15, 98, hy + 17), rgb("#3a0800"))
    if pose in (2, 3):
        c.rrect((78, hy + 32, 98, hy + 44), 4, rgb("#2a0810"))
        for k in range(4):
            c.poly([(80 + k * 5, hy + 32), (82 + k * 5, hy + 36), (84 + k * 5, hy + 32)], rgb("#f0e8d0"))
    c.outline(OUT)
    c.glow(79, hy + 16, 9, eye, 0.5)
    c.glow(97, hy + 16, 9, eye, 0.5)
    if pose == 4:
        c.glow(20, tail_y - 16, 26, rgb("#9ae8ff"), 0.5)
    return c


def dragon_spin(angle):
    """회전 자세: 몸을 말아 도는 모습 (각도별 4프레임)."""
    base = dragon(0)
    img = base.img.rotate(angle, resample=0, expand=False)
    out = Canvas(176, 144)
    out.img = img
    return out


def demon_king(frame):
    """마왕 1페이즈 128x128: 금관을 쓴 리치 왕. 보라 로브, 가슴의 자수정, 두 손의 마력 구슬."""
    c = Canvas(128, 128)
    robe, robe_d = rgb("#3a2350"), rgb("#22142e")
    gold, gold_d = rgb("#d8a83c"), rgb("#8a6420")
    bone = rgb("#e8dfc4")
    gem = rgb("#b060ff")
    fl = 2 if frame else 0
    c.glow(64, 70, 60, rgb("#9040ff"), 0.22)
    # 해진 로브 자락 (떠 있다)
    pts = [(38, 40 + fl), (90, 40 + fl), (108, 108 + fl)]
    for i in range(9):
        x = 108 - i * 10
        pts.append((x - 5, 124 + fl - (i % 2) * 12))
    pts.append((20, 108 + fl))
    c.poly(pts, robe)
    c.poly([(50, 44 + fl), (78, 44 + fl), (74, 116 + fl), (54, 116 + fl)], robe_d)
    # 금 장식 띠
    c.poly([(56, 46 + fl), (72, 46 + fl), (70, 112 + fl), (58, 112 + fl)], gold_d)
    c.poly([(58, 46 + fl), (70, 46 + fl), (68, 110 + fl), (60, 110 + fl)], gold)
    for y in (64, 80, 96):
        c.poly([(64, y + fl), (68, y + 4 + fl), (64, y + 8 + fl), (60, y + 4 + fl)], gem, flat=True)
    # 갈비뼈 + 가슴 자수정
    c.rrect((46, 40 + fl, 82, 62 + fl), 6, rgb("#2a1a36"))
    for k in range(4):
        c.line([(50, 44 + k * 5 + fl), (62, 46 + k * 5 + fl)], bone, 2)
        c.line([(78, 44 + k * 5 + fl), (66, 46 + k * 5 + fl)], bone, 2)
    c.poly([(64, 44 + fl), (70, 52 + fl), (64, 62 + fl), (58, 52 + fl)], gem)
    # 금 어깨 갑주 (뿔처럼 솟은)
    for side in (-1, 1):
        cx = 64 + side * 30
        c.ellipse((cx - 14, 30 + fl, cx + 14, 50 + fl), gold)
        c.poly([(cx - 6 * side, 32 + fl), (cx + 18 * side, 14 + fl), (cx + 10 * side, 36 + fl)], gold)
    # 팔: 벌려서 구슬을 든다
    for side in (-1, 1):
        sx = 64 + side * 36
        hx = 64 + side * 54
        c.poly([(sx - 6, 44 + fl), (sx + 6, 44 + fl), (hx + 6 * side, 66 + fl), (hx - 4 * side, 70 + fl)], robe)
        c.line([(hx, 66 + fl), (hx, 56 + fl)], bone, 2)
        c.line([(hx - 3, 66 + fl), (hx - 4, 57 + fl)], bone, 1)
        c.line([(hx + 3, 66 + fl), (hx + 4, 57 + fl)], bone, 1)
    # 해골 + 금관
    c.ellipse((50, 14 + fl, 78, 42 + fl), bone)
    c.rrect((55, 34 + fl, 73, 44 + fl), 3, bone)
    c.poly([(48, 22 + fl), (50, 4 + fl), (56, 14 + fl), (60, -2 + fl), (64, 10 + fl), (68, -2 + fl),
            (72, 14 + fl), (78, 4 + fl), (80, 22 + fl)], gold)
    c.rect((48, 18 + fl, 80, 22 + fl), gold_d)
    c.poly([(64, 8 + fl), (67, 13 + fl), (64, 19 + fl), (61, 13 + fl)], gem, flat=True)
    c.flat_rect((55, 25 + fl, 61, 31 + fl), rgb("#10061a")); c.flat_rect((67, 25 + fl, 73, 31 + fl), rgb("#10061a"))
    c.flat_rect((57, 27 + fl, 59, 29 + fl), rgb("#e0a0ff")); c.flat_rect((69, 27 + fl, 71, 29 + fl), rgb("#e0a0ff"))
    c.poly([(63, 33 + fl), (65, 33 + fl), (64, 36 + fl)], rgb("#3a3028"))
    for k in range(5):
        c.px(58 + k * 3, 40 + fl, rgb("#3a3028"))
    c.outline(OUT)
    # 마력 구슬 (두 손 위)
    for side in (-1, 1):
        hx = 64 + side * 54
        r = 9 + (1 if frame else 0)
        c.glow(hx, 48 + fl, 18 + 3 * frame, rgb("#b070ff"), 0.7)
        c.ellipse((hx - r, 48 + fl - r, hx + r, 48 + fl + r), rgb("#c890ff"))
        c.px(hx - 3, 44 + fl, rgb("#ffffff")); c.px(hx - 2, 44 + fl, rgb("#ffffff"))
    c.glow(58, 28 + fl, 6, rgb("#d080ff"), 0.6)
    c.glow(70, 28 + fl, 6, rgb("#d080ff"), 0.6)
    c.glow(64, 52 + fl, 10, rgb("#b060ff"), 0.6)
    return c


def eldritch(frame):
    """마왕 2페이즈 176x176: 수많은 눈과 촉수, 거대한 아가리 (크툴루풍)."""
    c = Canvas(176, 176)
    flesh, dark = rgb("#3a4a3a"), rgb("#1c2a24")
    purple = rgb("#5a2a6a")
    c.glow(88, 100, 86, rgb("#9a40ff"), 0.22)
    # 촉수들: 몸 아래와 옆으로 뻗어 나와 말린다
    for i in range(11):
        base = math.pi * (0.05 + 0.9 * i / 10)          # 오른쪽(0)부터 왼쪽(pi)까지 아래쪽 반원
        sx, sy = 88 + math.cos(base) * 44, 100 + math.sin(base) * 34
        ang = base
        length = 70 + (i % 3) * 12
        prev = (sx, sy)
        segs = 14
        for k in range(1, segs + 1):
            t = k / segs
            ang += math.sin(frame * 1.3 + i * 0.9 + t * 4) * 0.22 + (0.08 if i < 5 else -0.08) * t
            x = prev[0] + math.cos(ang) * length / segs
            y = prev[1] + math.sin(ang) * length / segs * 0.8
            w = max(2, int(14 * (1 - t)) + 2)
            col = shade(flesh, 6 if k % 3 else -14)
            c.line([prev, (x, y)], col, w)
            if k % 3 == 0 and w > 4:
                c.flat_ellipse((x - 1.5, y - 1.5, x + 1.5, y + 1.5), rgb("#c8a0d8"))
            prev = (x, y)
    # 몸통 덩어리
    c.ellipse((34, 30, 142, 140), flesh)
    c.ellipse((50, 44, 126, 130), shade(flesh, -12))
    # 아가리
    c.ellipse((62, 92, 114, 132), rgb("#200810"))
    for k in range(8):
        x = 66 + k * 6
        c.poly([(x, 96 + abs(k - 3.5) * 2), (x + 3, 108), (x + 6, 96 + abs(k - 3.5) * 2)], rgb("#e8e0c8"))
        c.poly([(x, 128 - abs(k - 3.5) * 2), (x + 3, 118), (x + 6, 128 - abs(k - 3.5) * 2)], rgb("#e8e0c8"))
    # 왕관 같은 뿔 고리
    for k in range(7):
        x = 46 + k * 14
        c.poly([(x, 40), (x + 6, 14 - (k % 2) * 8), (x + 12, 40)], purple)
    # 눈들
    eyes = [(88, 64, 16), (60, 58, 9), (116, 58, 9), (70, 82, 6), (106, 82, 6), (48, 80, 5), (128, 80, 5), (88, 40, 7)]
    for (x, y, r) in eyes:
        blink = frame == 1 and r < 8 and x % 3 == 0
        c.flat_ellipse((x - r, y - r * 0.7, x + r, y + r * 0.7), rgb("#f0e070") if not blink else dark)
        if not blink:
            c.flat_ellipse((x - r * 0.25, y - r * 0.6, x + r * 0.25, y + r * 0.6), rgb("#100808"))
    c.outline(OUT)
    c.glow(88, 64, 22, rgb("#fff080"), 0.35)
    return c
