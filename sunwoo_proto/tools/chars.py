"""
용사 그림 (참고 그림 1번: 은빛 삐죽머리, 청록 튜닉·목도리·망토, 갈색 가죽 끈·주머니·장갑·부츠).
- hero(direction, frame): 32x32, 대기·걷기1·걷기2
- bed_sleep(): 침대에 누워 자는 모습 (침대 그림과 같은 40x70)
- hero_forge(pose): 대장간 창에서 크게 보여 줄 48x48 자세
  0 서 있기, 1 망치 들기, 2 내려치기, 3 성공(두 주먹 불끈), 4 하락(고개 푹), 5 파괴(무릎 꿇고 좌절), 6 유지(어깨 으쓱)
잡몹은 monsters.py, 보스는 bosses.py.
"""
import math
from art import Canvas, rgb, shade, mix

OUT = (14, 10, 18, 255)

SKIN, SKIN_D = rgb("#f2c9a0"), rgb("#c9946f")
HAIR, HAIR_H, HAIR_D = rgb("#d6dbe6"), rgb("#ffffff"), rgb("#9aa2b8")
TUNIC, TUNIC_D = rgb("#1f7d8e"), rgb("#135260")
CAPE, CAPE_D = rgb("#1d6f86"), rgb("#0f4656")
SCARF = rgb("#2aa6b8")
LEATHER, LEATHER_D = rgb("#9a6236"), rgb("#6a3e20")
PANTS = rgb("#6e4a30")
BOOT = rgb("#7a4824")
GOLD = rgb("#e6b84a")
EYE = rgb("#1b2440")
IRIS = rgb("#5fb0ff")
STEEL = rgb("#c8d0dc")


def _hair_front(c, x, y, wide=13):
    """정면 삐죽머리 (x, y는 머리 왼쪽 위)."""
    c.ellipse((x - 1, y - 1, x + wide, y + 6), HAIR, hi=40, lo=-30)
    spikes = [(-2, 2, -3, -2), (1, 0, 1, -5), (5, -1, 6, -6), (8, 0, 11, -5), (11, 2, 15, -2)]
    for (sx, sy, tx, ty) in spikes:
        c.poly([(x + sx, y + sy + 3), (x + sx + 4, y + sy + 2), (x + tx, y + ty)], HAIR)
    # 앞머리
    c.poly([(x, y + 4), (x + 4, y + 4), (x + 2, y + 9)], HAIR)
    c.poly([(x + 4, y + 4), (x + 8, y + 4), (x + 6, y + 8)], HAIR_D)
    c.poly([(x + 8, y + 4), (x + 12, y + 4), (x + 11, y + 9)], HAIR)
    c.px(x + 2, y + 1, HAIR_H); c.px(x + 3, y, HAIR_H); c.px(x + 4, y, HAIR_H)


def hero(direction, frame):
    c = Canvas(32, 32)
    bob = 0 if frame == 0 else -1
    ll, rl = (0, 0)
    if frame == 1:
        ll, rl = 1, -1
    elif frame == 2:
        ll, rl = -1, 1

    if direction == "down":
        # 망토 (뒤로 펄럭)
        c.poly([(9, 14 + bob), (23, 14 + bob), (26, 28), (6, 28)], CAPE_D)
        # 다리·부츠
        c.rect((12, 22 + bob, 15, 27 + ll), PANTS)
        c.rect((17, 22 + bob, 20, 27 + rl), PANTS)
        c.rrect((11, 26 + ll, 15, 30 + ll), 1, BOOT)
        c.rrect((17, 26 + rl, 21, 30 + rl), 1, BOOT)
        c.flat_rect((11, 26 + ll, 15, 26 + ll), shade(BOOT, 30)); c.flat_rect((17, 26 + rl, 21, 26 + rl), shade(BOOT, 30))
        # 튜닉
        c.rrect((10, 14 + bob, 21, 24 + bob), 3, TUNIC)
        c.flat_rect((15, 15 + bob, 16, 20 + bob), shade(TUNIC, 25))
        # 어깨에서 허리로 비스듬한 가죽 끈
        c.line([(11, 15 + bob), (20, 21 + bob)], LEATHER, 2)
        # 허리띠·버클·주머니
        c.rect((10, 21 + bob, 21, 22 + bob), LEATHER_D, flat=True)
        c.flat_rect((15, 21 + bob, 16, 22 + bob), GOLD)
        c.rrect((18, 22 + bob, 21, 25 + bob), 1, LEATHER)
        # 가죽 어깨받이
        c.ellipse((7, 13 + bob, 13, 18 + bob), LEATHER)
        c.ellipse((18, 13 + bob, 24, 18 + bob), LEATHER)
        # 팔(소매) + 장갑
        c.rect((7, 17 + bob, 9, 21 + bob), TUNIC_D)
        c.rect((22, 17 + bob, 24, 21 + bob), TUNIC_D)
        c.rrect((6, 20 + bob, 10, 24 + bob), 1, LEATHER)
        c.rrect((21, 20 + bob, 25, 24 + bob), 1, LEATHER)
        # 목도리
        c.rrect((10, 12 + bob, 21, 15 + bob), 2, SCARF)
        c.poly([(19, 14 + bob), (22, 14 + bob), (23, 19 + bob), (20, 18 + bob)], shade(SCARF, -25))
        # 머리
        c.ellipse((10, 2 + bob, 21, 13 + bob), SKIN)
        _hair_front(c, 9, 1 + bob)
        for x in (13, 18):
            c.flat_rect((x, 9 + bob, x, 10 + bob), EYE)
            c.px(x, 9 + bob, IRIS)
        c.line([(12, 8 + bob), (14, 8 + bob)], HAIR_D); c.line([(17, 8 + bob), (19, 8 + bob)], HAIR_D)  # 눈썹 (진지한 얼굴)
    elif direction == "up":
        c.rect((12, 22 + bob, 15, 27 + ll), PANTS)
        c.rect((17, 22 + bob, 20, 27 + rl), PANTS)
        c.rrect((11, 26 + ll, 15, 30 + ll), 1, BOOT)
        c.rrect((17, 26 + rl, 21, 30 + rl), 1, BOOT)
        c.ellipse((7, 13 + bob, 13, 18 + bob), LEATHER)
        c.ellipse((18, 13 + bob, 24, 18 + bob), LEATHER)
        c.rrect((6, 20 + bob, 10, 24 + bob), 1, LEATHER)
        c.rrect((21, 20 + bob, 25, 24 + bob), 1, LEATHER)
        # 망토가 등을 덮는다
        c.poly([(9, 13 + bob), (23, 13 + bob), (26, 28 + bob // 2), (21, 26), (16, 29), (11, 26), (6, 28 + bob // 2)], CAPE)
        c.line([(16, 15 + bob), (16, 27)], CAPE_D)
        c.line([(12, 16 + bob), (10, 26)], CAPE_D)
        c.rrect((10, 11 + bob, 21, 15 + bob), 2, SCARF)
        c.ellipse((10, 2 + bob, 21, 13 + bob), HAIR)
        for (x0, tip) in ((9, (8, 0)), (12, (12, -2)), (16, (18, -2)), (19, (23, 1))):
            c.poly([(x0, 6 + bob), (x0 + 4, 5 + bob), (tip[0], tip[1] + bob + 2)], HAIR)
        c.poly([(11, 9 + bob), (14, 9 + bob), (12, 14 + bob)], HAIR_D)
        c.poly([(17, 9 + bob), (20, 9 + bob), (19, 14 + bob)], HAIR_D)
        c.px(13, 4 + bob, HAIR_H); c.px(14, 3 + bob, HAIR_H); c.px(15, 3 + bob, HAIR_H)
    else:  # side: 오른쪽을 본다
        c.poly([(9, 14 + bob), (15, 14 + bob), (13, 28), (3, 27 + bob)], CAPE_D)
        back, front = (ll, rl)
        c.rect((13, 22 + bob, 16, 27 + back), shade(PANTS, -15))
        c.rrect((12 + back, 26 + back, 16 + back, 30 + back), 1, shade(BOOT, -10))
        c.rect((15, 22 + bob, 18, 27 + front), PANTS)
        c.rrect((15 - front, 26 + front, 20 - front, 30 + front), 1, BOOT)
        c.rrect((12, 14 + bob, 20, 24 + bob), 3, TUNIC)
        c.line([(13, 15 + bob), (19, 21 + bob)], LEATHER, 2)
        c.rect((12, 21 + bob, 20, 22 + bob), LEATHER_D, flat=True)
        c.rrect((11, 21 + bob, 14, 24 + bob), 1, LEATHER)
        c.ellipse((12, 13 + bob, 18, 18 + bob), LEATHER)
        c.rect((14, 17 + bob, 16, 21 + bob), TUNIC_D)
        c.rrect((14, 20 + bob, 18, 23 + bob), 1, LEATHER)
        c.rrect((12, 12 + bob, 20, 15 + bob), 2, SCARF)
        c.poly([(12, 13 + bob), (8, 15 + bob), (5, 19 + bob), (10, 16 + bob)], shade(SCARF, -15))
        c.ellipse((12, 2 + bob, 22, 13 + bob), SKIN)
        c.ellipse((10, 1 + bob, 21, 8 + bob), HAIR)
        for (x0, tip) in ((9, (5, 1)), (12, (9, -3)), (16, (15, -4)), (19, (22, -1))):
            c.poly([(x0, 5 + bob), (x0 + 4, 4 + bob), (tip[0], tip[1] + bob + 2)], HAIR)
        c.poly([(10, 4 + bob), (15, 4 + bob), (12, 12 + bob)], HAIR)
        c.poly([(17, 5 + bob), (22, 5 + bob), (21, 9 + bob)], HAIR)
        c.flat_rect((19, 9 + bob, 19, 10 + bob), EYE)
        c.px(19, 9 + bob, IRIS)
        c.line([(18, 8 + bob), (20, 8 + bob)], HAIR_D)
        c.px(21, 11 + bob, SKIN_D)
    return c.outline(OUT)


# ===================================================================== 침대에서 자는 모습
def bed_sleep():
    """props.bed()와 같은 40x70: 베개에 은발 머리를 대고 이불을 덮고 잔다."""
    import props
    c = props.bed()
    # 이불 아래 몸 (불룩)
    c.rrect((9, 22, 30, 60), 6, rgb("#46578a"))
    c.line([(12, 30), (27, 30)], rgb("#33406a"))
    c.line([(11, 44), (28, 44)], rgb("#33406a"))
    # 이불 위로 나온 팔 (장갑)
    c.rrect((24, 26, 29, 31), 1, LEATHER)
    # 머리: 베개 위, 눈 감음
    c.ellipse((13, 9, 26, 22), SKIN)
    c.ellipse((11, 7, 28, 15), HAIR, hi=40, lo=-30)
    for (x0, tx, ty) in ((11, 8, 6), (15, 14, 3), (20, 22, 3), (24, 30, 7)):
        c.poly([(x0, 11), (x0 + 4, 10), (tx, ty)], HAIR)
    c.line([(15, 17), (17, 18)], EYE); c.line([(21, 18), (23, 17)], EYE)
    c.px(19, 20, SKIN_D)
    # 목도리를 베개 옆에 걸쳐 둠
    c.rrect((27, 14, 33, 24), 2, SCARF)
    return c.outline(OUT)


# ===================================================================== 대장간 자세 (48x48)
def _forge_body(c, ox, oy, head_drop=0, kneel=False):
    """서 있는 용사 몸 (정면 약간 오른쪽). ox, oy: 왼쪽 위. head_drop: 고개 숙임 정도."""
    leg_top = 30 if not kneel else 34
    if kneel:
        # 한쪽 무릎을 꿇은 다리
        c.rrect((ox + 13, oy + 36, ox + 21, oy + 42), 2, PANTS)
        c.rrect((ox + 11, oy + 41, ox + 24, oy + 46), 2, BOOT)
        c.rrect((ox + 24, oy + 34, ox + 30, oy + 46), 2, PANTS)
        c.rrect((ox + 23, oy + 42, ox + 33, oy + 47), 2, BOOT)
    else:
        c.rect((ox + 16, oy + leg_top, ox + 21, oy + 41), PANTS)
        c.rect((ox + 25, oy + leg_top, ox + 30, oy + 41), PANTS)
        c.rrect((ox + 14, oy + 40, ox + 22, oy + 47), 2, BOOT)
        c.rrect((ox + 24, oy + 40, ox + 32, oy + 47), 2, BOOT)
        c.flat_rect((ox + 14, oy + 40, ox + 22, oy + 41), shade(BOOT, 30))
        c.flat_rect((ox + 24, oy + 40, ox + 32, oy + 41), shade(BOOT, 30))
    ty = oy + (4 if kneel else 0)
    # 망토
    c.poly([(ox + 12, ty + 16), (ox + 34, ty + 16), (ox + 39, ty + 40), (ox + 7, ty + 40)], CAPE_D)
    # 튜닉
    c.rrect((ox + 13, ty + 16, ox + 33, ty + 34), 4, TUNIC)
    c.line([(ox + 15, ty + 18), (ox + 30, ty + 30)], LEATHER, 3)
    c.rect((ox + 13, ty + 29, ox + 33, ty + 31), LEATHER_D, flat=True)
    c.flat_rect((ox + 22, ty + 29, ox + 24, ty + 31), GOLD)
    c.rrect((ox + 27, ty + 31, ox + 32, ty + 36), 1, LEATHER)
    c.ellipse((ox + 9, ty + 14, ox + 19, ty + 22), LEATHER)
    c.ellipse((ox + 27, ty + 14, ox + 37, ty + 22), LEATHER)
    c.rrect((ox + 14, ty + 12, ox + 32, ty + 18), 3, SCARF)
    # 머리
    hy = ty + 1 + head_drop
    c.ellipse((ox + 15, hy, ox + 31, hy + 15), SKIN)
    c.ellipse((ox + 13, hy - 2, ox + 33, hy + 8), HAIR, hi=40, lo=-30)
    for (sx, tx, ty2) in ((12, 11, -4), (17, 16, -8), (23, 25, -9), (28, 33, -4)):
        c.poly([(ox + sx, hy + 4), (ox + sx + 6, hy + 2), (ox + tx, hy + ty2)], HAIR)
    c.poly([(ox + 14, hy + 5), (ox + 19, hy + 5), (ox + 16, hy + 12)], HAIR)
    c.poly([(ox + 26, hy + 5), (ox + 32, hy + 5), (ox + 30, hy + 12)], HAIR)
    c.px(ox + 18, hy, HAIR_H); c.px(ox + 19, hy - 1, HAIR_H)
    return hy


def _face(c, ox, hy, mood):
    """mood: 'serious', 'joy', 'sad', 'shock', 'closed'"""
    lx, rx, ey = ox + 19, ox + 26, hy + 9
    if mood in ("closed", "sad"):
        c.line([(lx - 1, ey), (lx + 1, ey + 1)], EYE); c.line([(rx - 1, ey + 1), (rx + 1, ey)], EYE)
    elif mood == "joy":
        c.line([(lx - 1, ey + 1), (lx, ey - 1), (lx + 1, ey + 1)], EYE)
        c.line([(rx - 1, ey + 1), (rx, ey - 1), (rx + 1, ey + 1)], EYE)
    else:
        c.flat_rect((lx, ey - 1, lx + 1, ey + 1), EYE); c.flat_rect((rx, ey - 1, rx + 1, ey + 1), EYE)
        c.px(lx, ey - 1, IRIS); c.px(rx, ey - 1, IRIS)
        c.line([(lx - 1, ey - 3), (lx + 2, ey - 2)], HAIR_D); c.line([(rx - 1, ey - 2), (rx + 2, ey - 3)], HAIR_D)
    if mood == "joy":
        c.flat_rect((ox + 21, hy + 12, ox + 24, hy + 13), rgb("#a03030"))
    elif mood == "shock":
        c.flat_ellipse((ox + 21, hy + 11, ox + 24, hy + 14), rgb("#3a1010"))
    elif mood == "sad":
        c.line([(ox + 21, hy + 13), (ox + 24, hy + 12)], SKIN_D)
    else:
        c.line([(ox + 21, hy + 12), (ox + 24, hy + 12)], SKIN_D)


def _hammer(c, x0, y0, x1, y1):
    """손잡이 (x0,y0)→머리 쪽 (x1,y1)."""
    c.line([(x0, y0), (x1, y1)], rgb("#6a4024"), 3)
    ang = math.atan2(y1 - y0, x1 - x0)
    px, py = -math.sin(ang), math.cos(ang)
    hw = 7
    pts = [(x1 + px * hw - math.cos(ang) * 2, y1 + py * hw - math.sin(ang) * 2),
           (x1 + px * hw + math.cos(ang) * 5, y1 + py * hw + math.sin(ang) * 5),
           (x1 - px * hw + math.cos(ang) * 5, y1 - py * hw + math.sin(ang) * 5),
           (x1 - px * hw - math.cos(ang) * 2, y1 - py * hw - math.sin(ang) * 2)]
    c.poly(pts, rgb("#8a8e98"), hi=40, lo=-30)


def hero_forge(pose):
    c = Canvas(48, 48)
    ox, oy = 0, 0
    if pose == 5:
        # 파괴: 무릎 꿇고 두 손으로 땅을 짚은 채 고개를 떨군다
        hy = _forge_body(c, ox, oy, head_drop=8, kneel=True)
        c.line([(ox + 15, oy + 26), (ox + 12, oy + 40)], TUNIC_D, 4)
        c.line([(ox + 31, oy + 26), (ox + 34, oy + 40)], TUNIC_D, 4)
        c.rrect((ox + 9, oy + 39, ox + 15, oy + 44), 1, LEATHER)
        c.rrect((ox + 31, oy + 39, ox + 37, oy + 44), 1, LEATHER)
        _face(c, ox, hy, "closed")
        c.line([(ox + 18, hy + 11), (ox + 18, hy + 15)], rgb("#80c8ff"))  # 눈물
        c.outline(OUT)
        # 어두운 기운
        c.glow(ox + 23, oy + 20, 18, rgb("#203050"), 0.35)
        return c
    head_drop = 6 if pose == 4 else 0
    hy = _forge_body(c, ox, oy, head_drop=head_drop)
    if pose in (0, 1, 2):
        # 망치: 0 들고 서기, 1 머리 위로, 2 내려치기
        if pose == 0:
            c.line([(ox + 12, oy + 20), (ox + 10, oy + 30)], TUNIC_D, 4)
            c.rrect((ox + 8, oy + 29, ox + 13, oy + 33), 1, LEATHER)
            _hammer(c, ox + 11, oy + 34, ox + 9, oy + 44)
            c.line([(ox + 34, oy + 20), (ox + 36, oy + 30)], TUNIC_D, 4)
            c.rrect((ox + 34, oy + 29, ox + 39, oy + 33), 1, LEATHER)
        elif pose == 1:
            c.line([(ox + 32, oy + 19), (ox + 38, oy + 8)], TUNIC_D, 4)
            c.rrect((ox + 36, oy + 5, ox + 41, oy + 10), 1, LEATHER)
            _hammer(c, ox + 39, oy + 8, ox + 32, oy + 0 + 3)
            c.line([(ox + 14, oy + 20), (ox + 11, oy + 30)], TUNIC_D, 4)
            c.rrect((ox + 9, oy + 29, ox + 14, oy + 33), 1, LEATHER)
        else:
            c.line([(ox + 32, oy + 19), (ox + 40, oy + 27)], TUNIC_D, 4)
            c.rrect((ox + 38, oy + 25, ox + 43, oy + 30), 1, LEATHER)
            _hammer(c, ox + 41, oy + 28, ox + 45, oy + 38)
            c.line([(ox + 14, oy + 20), (ox + 11, oy + 30)], TUNIC_D, 4)
            c.rrect((ox + 9, oy + 29, ox + 14, oy + 33), 1, LEATHER)
        _face(c, ox, hy, "serious")
    elif pose == 3:
        # 성공: 두 주먹을 가슴 앞에서 불끈
        c.line([(ox + 13, oy + 20), (ox + 16, oy + 26)], TUNIC_D, 4)
        c.line([(ox + 33, oy + 20), (ox + 30, oy + 26)], TUNIC_D, 4)
        c.rrect((ox + 13, oy + 21, ox + 20, oy + 27), 2, LEATHER, hi=40)
        c.rrect((ox + 26, oy + 21, ox + 33, oy + 27), 2, LEATHER, hi=40)
        _face(c, ox, hy, "joy")
    elif pose == 4:
        # 하락: 고개 푹, 팔은 축 늘어짐
        c.line([(ox + 13, oy + 21), (ox + 12, oy + 34)], TUNIC_D, 4)
        c.line([(ox + 33, oy + 21), (ox + 34, oy + 34)], TUNIC_D, 4)
        c.rrect((ox + 10, oy + 33, ox + 15, oy + 37), 1, LEATHER)
        c.rrect((ox + 32, oy + 33, ox + 37, oy + 37), 1, LEATHER)
        _face(c, ox, hy, "sad")
    else:
        # 유지: 어깨 으쓱
        c.line([(ox + 13, oy + 19), (ox + 6, oy + 22)], TUNIC_D, 4)
        c.line([(ox + 33, oy + 19), (ox + 40, oy + 22)], TUNIC_D, 4)
        c.rrect((ox + 3, oy + 18, ox + 8, oy + 23), 1, LEATHER)
        c.rrect((ox + 38, oy + 18, ox + 43, oy + 23), 1, LEATHER)
        _face(c, ox, hy, "serious")
    c.outline(OUT)
    if pose == 3:
        for (x, y) in ((6, 10), (42, 12), (24, 2)):
            c.glow(x, y, 5, rgb("#fff0a0"), 0.8)
    if pose == 4:
        # 머리 위 먹구름
        c.flat_ellipse((14, 0, 30, 5), (80, 90, 110, 200))
    return c
