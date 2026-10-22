"""
보스 그림 (참고 그림 2·4·5·8·9번). 모든 보스는 같은 13프레임 순서를 쓴다 (렌더러가 번호로 고른다):
  0 대기1, 1 대기2, 2 특수 동작(돌진·시전·아래 포효), 3 위 포효, 4 꼬리 들기, 5 공격 준비, 6 공격,
  7 휘청, 8 쓰러짐1, 9 쓰러짐2, 10 쓰러짐3(잔해), 11 뒷모습, 12 옆모습
해당 동작이 없는 보스는 가장 가까운 그림을 다시 쓴다.
"""
import math
from PIL import Image
from art import Canvas, rgb, shade, mix

OUT = (14, 10, 18, 255)
FRAMES = 13


def _rotate(c, deg, cx=None, cy=None):
    out = Canvas(c.w, c.h)
    out.img = c.img.rotate(deg, resample=Image.NEAREST, center=(cx if cx is not None else c.w / 2, cy if cy is not None else c.h),
                           expand=False)
    return out


def _fade(c, k, tint=(60, 50, 70)):
    """잔해·재로 변해 가는 색 (k=0 원래, 1 거의 잿빛)."""
    px = c.img.load()
    for y in range(c.h):
        for x in range(c.w):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (int(r + (tint[0] - r) * k), int(g + (tint[1] - g) * k), int(b + (tint[2] - b) * k), a)
    return c


def _squash(c, sy, sx=1.0):
    """아래(발밑)를 기준으로 눌러 그린다 (무너지는 모습)."""
    w, h = c.w, c.h
    nw, nh = max(1, int(w * sx)), max(1, int(h * sy))
    small = c.img.resize((nw, nh), Image.NEAREST)
    out = Canvas(w, h)
    out.img.alpha_composite(small, ((w - nw) // 2, h - nh))
    return out


# ===================================================================== 좀비킹 112x112
ZK_SKIN, ZK_SKIN_D = rgb("#7e9a6a"), rgb("#56704a")
ZK_ARM, ZK_ARM_H, ZK_ARM_D = rgb("#8a5636"), rgb("#b47a4a"), rgb("#55301e")
ZK_RUST = rgb("#6e3a1e")
ZK_MOSS = rgb("#5e7040")
ZK_PURPLE = rgb("#5a2a6a")
ZK_GOLD = rgb("#d8a838")
ZK_HAIR = rgb("#a8a898")


def _zk_sword(c, x0, y0, x1, y1):
    """녹슨 대검 (해골 장식 손잡이): 손 (x0,y0) → 칼끝 (x1,y1)."""
    ang = math.atan2(y1 - y0, x1 - x0)
    ux, uy = math.cos(ang), math.sin(ang)
    px, py = -uy, ux
    # 손잡이와 해골 머리장식 (손 반대쪽)
    hx, hy = x0 - ux * 9, y0 - uy * 9
    c.line([(x0 + ux * 4, y0 + uy * 4), (hx, hy)], rgb("#5a3a22"), 4)
    c.ellipse((hx - 5 - ux * 3, hy - 5 - uy * 3, hx + 5 - ux * 3, hy + 5 - uy * 3), rgb("#e0d8c0"))
    sx, sy = hx - ux * 3, hy - uy * 3
    c.px(sx - 2, sy - 1, rgb("#2a1a10")); c.px(sx + 2, sy - 1, rgb("#2a1a10"))
    # 코등이
    gx, gy = x0 + ux * 5, y0 + uy * 5
    c.line([(gx + px * 9, gy + py * 9), (gx - px * 9, gy - py * 9)], rgb("#7a5a30"), 4)
    # 칼날
    bx, by = gx + ux * 2, gy + uy * 2
    pts = [(bx + px * 5, by + py * 5), (x1 - ux * 8 + px * 4, y1 - uy * 8 + py * 4), (x1, y1),
           (x1 - ux * 8 - px * 4, y1 - uy * 8 - py * 4), (bx - px * 5, by - py * 5)]
    c.poly(pts, rgb("#8e8478"), hi=40, lo=-30)
    c.line([(bx, by), (x1 - ux * 6, y1 - uy * 6)], rgb("#5e564c"))
    # 녹 얼룩
    for k in range(5):
        t = 0.15 + k * 0.17
        rx, ry = bx + (x1 - bx) * t + px * ((k % 3) - 1) * 2, by + (y1 - by) * t + py * ((k % 3) - 1) * 2
        c.flat_ellipse((rx - 2, ry - 1.5, rx + 2, ry + 1.5), ZK_RUST)


def _zk_body(c, ox=0, oy=0, sword="down", lean=0, breath=0, crouch=0, head_tilt=0, eyes=True):
    """서 있는 좀비킹. sword: down, raised, swing, back, planted, none."""
    L = lean
    y = oy + crouch
    # 망토 (보라, 해짐)
    pts = [(ox + 30 + L, y + 34), (ox + 82 + L, y + 34), (ox + 96, oy + 106)]
    for i in range(8):
        pts.append((ox + 96 - i * 11 - 3, oy + 110 - (i % 2) * 8))
    pts.append((ox + 14, oy + 106))
    c.poly(pts, shade(ZK_PURPLE, -20))
    # 다리 (갑주)
    for (lx, k) in ((34, 0), (62, 1)):
        top = y + 74
        c.rrect((ox + lx + L // 2, top, ox + lx + 16 + L // 2, oy + 104), 4, ZK_ARM, hi=30)
        c.ellipse((ox + lx - 1 + L // 2, top + 6, ox + lx + 17 + L // 2, top + 18), ZK_ARM_H)
        c.rrect((ox + lx - 3 + L // 2, oy + 100, ox + lx + 19 + L // 2, oy + 110), 3, ZK_ARM_D)
    # 몸통 갑주
    c.rrect((ox + 26 + L, y + 36 + breath, ox + 86 + L, y + 82), 10, ZK_ARM, hi=34, lo=-30)
    c.rrect((ox + 34 + L, y + 42 + breath, ox + 78 + L, y + 62), 6, ZK_ARM_H)
    c.line([(ox + 56 + L, y + 42 + breath), (ox + 56 + L, y + 62)], ZK_ARM_D, 2)
    for (rx, ry) in ((38, 48), (70, 54), (46, 70), (64, 72)):
        c.flat_ellipse((ox + rx + L - 3, y + ry - 2, ox + rx + L + 3, y + ry + 2), ZK_RUST)
    c.flat_ellipse((ox + 74 + L, y + 64, ox + 82 + L, y + 70), ZK_MOSS)
    # 보라 앞천 (타바드)
    c.poly([(ox + 44 + L, y + 62), (ox + 68 + L, y + 62), (ox + 72 + L, y + 96), (ox + 64 + L, y + 92), (ox + 56 + L, y + 98),
            (ox + 48 + L, y + 92), (ox + 40 + L, y + 96)], ZK_PURPLE)
    c.rect((ox + 30 + L, y + 60, ox + 82 + L, y + 65), ZK_ARM_D, flat=True)
    c.ellipse((ox + 52 + L, y + 59, ox + 60 + L, y + 66), ZK_GOLD)
    # 어깨 갑주 (큰 원형)
    for side in (-1, 1):
        cx = ox + 56 + L + side * 32
        c.ellipse((cx - 16, y + 30, cx + 16, y + 52), ZK_ARM, hi=40, lo=-34)
        c.flat_ellipse((cx - 4, y + 36, cx + 4, y + 42), ZK_ARM_H)
        c.flat_ellipse((cx + side * 6 - 3, y + 44, cx + side * 6 + 3, y + 48), ZK_RUST)
    # 왼팔 (화면 왼쪽): 주먹
    if sword in ("raised",):
        c.rrect((ox + 20 + L, y + 18, ox + 34 + L, y + 48), 6, ZK_ARM)
        c.ellipse((ox + 24 + L, y + 12, ox + 40 + L, y + 26), ZK_SKIN)
    else:
        c.rrect((ox + 12 + L, y + 44, ox + 28 + L, y + 76), 6, ZK_ARM)
        c.ellipse((ox + 11 + L, y + 72, ox + 29 + L, y + 88), ZK_SKIN)
        c.line([(ox + 15 + L, y + 78), (ox + 25 + L, y + 78)], ZK_SKIN_D)
    # 오른팔 + 대검
    if sword == "down":
        c.rrect((ox + 84 + L, y + 44, ox + 100 + L, y + 76), 6, ZK_ARM)
        c.ellipse((ox + 84 + L, y + 70, ox + 100 + L, y + 84), ZK_SKIN)
        _zk_sword(c, ox + 92 + L, y + 76, ox + 98 + L, oy + 110)
    elif sword == "planted":
        c.rrect((ox + 80 + L, y + 40, ox + 96 + L, y + 66), 6, ZK_ARM)
        c.ellipse((ox + 82 + L, y + 60, ox + 98 + L, y + 74), ZK_SKIN)
        _zk_sword(c, ox + 90 + L, y + 66, ox + 92 + L, oy + 112)
    elif sword == "raised":
        c.rrect((ox + 78 + L, y + 14, ox + 92 + L, y + 46), 6, ZK_ARM)
        c.ellipse((ox + 72 + L, y + 8, ox + 88 + L, y + 22), ZK_SKIN)
        _zk_sword(c, ox + 66 + L, y + 16, ox + 12 + L, y - 8 + 10)
    elif sword == "swing":
        c.rrect((ox + 80 + L, y + 50, ox + 106, y + 64), 6, ZK_ARM)
        c.ellipse((ox + 96, y + 52, ox + 110, y + 66), ZK_SKIN)
        _zk_sword(c, ox + 100, y + 60, ox + 18, oy + 106)
    elif sword == "back":
        c.rrect((ox + 84 + L, y + 44, ox + 100 + L, y + 66), 6, ZK_ARM)
        c.ellipse((ox + 90 + L, y + 60, ox + 104 + L, y + 72), ZK_SKIN)
        _zk_sword(c, ox + 96 + L, y + 66, ox + 110, y + 26)
    # 머리: 초록 피부, 긴 잿빛 머리, 노란 눈, 금관
    hx, hy = ox + 56 + L + head_tilt, y + 6 + crouch // 3
    # 긴 잿빛 머리: 머리 뒤와 양옆으로 흘러내린다
    c.ellipse((hx - 17, hy + 4, hx + 17, hy + 26), ZK_HAIR)
    for side in (-1, 1):
        c.poly([(hx + side * 10, hy + 10), (hx + side * 18, hy + 12), (hx + side * 21, hy + 44), (hx + side * 13, hy + 40)], ZK_HAIR)
        c.line([(hx + side * 15, hy + 14), (hx + side * 17, hy + 40)], shade(ZK_HAIR, -25))
    c.ellipse((hx - 13, hy + 8, hx + 13, hy + 38), ZK_SKIN)
    c.poly([(hx - 13, hy + 10), (hx + 13, hy + 10), (hx + 8, hy + 15), (hx, hy + 12), (hx - 8, hy + 15)], ZK_HAIR)
    c.line([(hx - 6, hy + 30), (hx - 9, hy + 26)], ZK_SKIN_D); c.line([(hx + 6, hy + 30), (hx + 9, hy + 26)], ZK_SKIN_D)
    c.flat_rect((hx - 10, hy + 20, hx - 4, hy + 24), rgb("#1a0a06")); c.flat_rect((hx + 4, hy + 20, hx + 10, hy + 24), rgb("#1a0a06"))
    if eyes:
        c.flat_rect((hx - 9, hy + 21, hx - 6, hy + 23), rgb("#ffd040")); c.flat_rect((hx + 5, hy + 21, hx + 8, hy + 23), rgb("#ffd040"))
    c.rrect((hx - 8, hy + 28, hx + 8, hy + 34), 2, rgb("#2a0e0e"))
    for k in range(4):
        c.px(hx - 6 + k * 4, hy + 29, rgb("#e8e0c0")); c.px(hx - 5 + k * 4, hy + 33, rgb("#e8e0c0"))
    c.flat_ellipse((hx + 8, hy + 12, hx + 13, hy + 16), ZK_SKIN_D)
    # 금관 (가시 + 보석)
    crown = [(hx - 16, hy + 10)]
    for k in range(5):
        crown += [(hx - 16 + k * 8 + 2, hy - 2 - (k % 2) * 4), (hx - 16 + k * 8 + 6, hy + 6)]
    crown += [(hx + 16, hy + 10)]
    c.poly(crown, ZK_GOLD, hi=50, lo=-40)
    c.rect((hx - 16, hy + 6, hx + 16, hy + 10), shade(ZK_GOLD, -30), flat=True)
    c.flat_ellipse((hx - 2, hy + 4, hx + 2, hy + 8), rgb("#c03040"))
    c.flat_ellipse((hx - 11, hy + 6, hx - 8, hy + 9), rgb("#40a0c0")); c.flat_ellipse((hx + 8, hy + 6, hx + 11, hy + 9), rgb("#40a0c0"))
    return hx, hy


def zombie_king(frame):
    c = Canvas(112, 112)
    eyes_glow = True
    if frame in (0, 3, 4, 11, 12):
        hx, hy = _zk_body(c)
    elif frame == 1:
        hx, hy = _zk_body(c, breath=2, crouch=1)
    elif frame == 2:          # 돌진: 앞으로 숙이고 칼을 뒤로
        hx, hy = _zk_body(c, sword="back", lean=6, crouch=4, head_tilt=4)
    elif frame == 5:          # 공격 준비: 대검을 머리 위로
        hx, hy = _zk_body(c, sword="raised", lean=-2)
    elif frame == 6:          # 공격: 앞으로 내려벰
        hx, hy = _zk_body(c, sword="swing", lean=4, crouch=3)
    elif frame == 7:          # 휘청: 고개를 떨구고 숨을 몰아쉰다
        hx, hy = _zk_body(c, sword="planted", crouch=6, head_tilt=-4)
    elif frame == 8:          # 쓰러짐1: 대검을 짚고 한쪽 무릎
        hx, hy = _zk_body(c, sword="planted", crouch=14, head_tilt=-6)
        c = _squash(c, 0.86)
        eyes_glow = False
    elif frame == 9:          # 쓰러짐2: 앞으로 고꾸라짐
        tmp = Canvas(112, 112)
        _zk_body(tmp, sword="none", crouch=10, eyes=False)
        c = _rotate(_squash(tmp, 0.8), -28, 56, 108)
        eyes_glow = False
    else:                     # 쓰러짐3: 갑옷 더미, 떨어진 왕관과 대검
        eyes_glow = False
        for (x0, y0, x1, y1) in ((20, 84, 60, 104), (48, 80, 92, 106), (30, 94, 80, 110)):
            c.ellipse((x0, y0, x1, y1), ZK_ARM, hi=30, lo=-30)
        c.poly([(24, 96), (88, 96), (96, 110), (16, 110)], shade(ZK_PURPLE, -20))
        c.ellipse((40, 86, 62, 100), ZK_SKIN_D)
        _zk_sword(c, 70, 104, 4, 100)
        crown = [(80, 96)]
        for k in range(4):
            crown += [(82 + k * 6, 86 - (k % 2) * 3), (85 + k * 6, 92)]
        crown += [(106, 96)]
        c.poly(crown, ZK_GOLD)
        _fade(c, 0.35)
        c.outline(OUT)
        return c
    c.outline(OUT)
    if eyes_glow:
        c.glow(hx - 7, hy + 22, 7, rgb("#ffe060"), 0.45)
        c.glow(hx + 7, hy + 22, 7, rgb("#ffe060"), 0.45)
    if frame == 9:
        _fade(c, 0.2)
    return c


# ===================================================================== 리치 96x112
LI_ROBE, LI_ROBE_D, LI_ROBE_H = rgb("#4c2a5c"), rgb("#2a1638"), rgb("#6e4282")
LI_BONE = rgb("#e4dcc6")
LI_GLOW = rgb("#b060ff")
LI_GREEN = rgb("#3a8a5a")


def _lich_body(c, fl=0, arm="down", staff="side", sag=0, hood_tilt=0, eyes=True):
    # 해진 후드 로브 (발 없음, 떠 있다)
    top = 30 + fl + sag
    pts = [(30, top), (66, top), (80, 98 + fl)]
    for i in range(8):
        x = 80 - i * 9
        pts.append((x - 4, 108 + fl - (i % 2) * 9))
    pts.append((16, 98 + fl))
    c.poly(pts, LI_ROBE, hi=24, lo=-30)
    c.poly([(42, top + 4), (54, top + 4), (52, 100 + fl), (44, 100 + fl)], LI_ROBE_D)
    c.rect((32, 62 + fl, 64, 66 + fl), rgb("#3a2a24"), flat=True)                     # 허리끈
    c.line([(46, 66 + fl), (44, 82 + fl)], rgb("#3a2a24"), 2)
    # 소매와 뼈 손
    if arm == "raise":
        c.poly([(30, top + 4), (40, top + 6), (22, top - 22), (14, top - 18)], LI_ROBE)
        c.line([(16, top - 20), (12, top - 28)], LI_BONE, 2)
        c.line([(18, top - 20), (18, top - 30)], LI_BONE, 2)
        c.line([(20, top - 19), (24, top - 27)], LI_BONE, 2)
    else:
        c.poly([(28, top + 4), (38, top + 6), (30, top + 34), (18, top + 32)], LI_ROBE)
        c.line([(22, top + 32), (20, top + 40)], LI_BONE, 2)
        c.line([(25, top + 33), (25, top + 41)], LI_BONE, 2)
    # 지팡이 + 보라·초록 연기
    if staff == "raise":
        sx, sy0, sy1 = 80, top - 34, top + 70
    elif staff == "thrust":
        sx, sy0, sy1 = 86, top - 10, top + 76
    else:
        sx, sy0, sy1 = 80, top - 22, 108 + fl
    c.poly([(66, top + 4), (58, top + 6), (sx - 2, top + 26), (sx + 6, top + 22)], LI_ROBE)
    c.line([(sx, sy0), (sx - 3, sy1)], rgb("#5a3a22"), 3)
    c.line([(sx, sy0), (sx - 6, sy0 - 6)], rgb("#5a3a22"), 2)
    c.line([(sx, sy0), (sx + 5, sy0 - 7)], rgb("#5a3a22"), 2)
    c.ellipse((sx - 4, top + 22, sx + 4, top + 30), LI_BONE)
    # 후드 + 해골 + 뼈관
    hx, hy = 48 + hood_tilt, 4 + fl + sag
    c.poly([(hx - 18, hy + 34), (hx - 16, hy + 8), (hx, hy - 4), (hx + 16, hy + 8), (hx + 18, hy + 34)], LI_ROBE, hi=30)
    c.ellipse((hx - 11, hy + 8, hx + 11, hy + 30), rgb("#120a18"))
    c.ellipse((hx - 9, hy + 10, hx + 9, hy + 28), LI_BONE)
    c.rrect((hx - 6, hy + 22, hx + 6, hy + 31), 2, LI_BONE)
    for k in range(5):
        c.poly([(hx - 10 + k * 5, hy + 12), (hx - 8 + k * 5, hy + 4 - (k % 2) * 3), (hx - 6 + k * 5, hy + 12)], rgb("#d8ccb0"))
    c.flat_rect((hx - 6, hy + 16, hx - 2, hy + 20), rgb("#0a0612")); c.flat_rect((hx + 2, hy + 16, hx + 6, hy + 20), rgb("#0a0612"))
    if eyes:
        c.flat_rect((hx - 5, hy + 17, hx - 3, hy + 19), rgb("#e090ff")); c.flat_rect((hx + 3, hy + 17, hx + 5, hy + 19), rgb("#e090ff"))
    c.poly([(hx - 1, hy + 22), (hx + 1, hy + 22), (hx, hy + 24)], rgb("#3a3428"))
    for k in range(4):
        c.px(hx - 4 + k * 3, hy + 28, rgb("#3a3428"))
    return hx, hy, sx, sy0


def lich(frame):
    c = Canvas(96, 112)
    eyes = frame < 8
    if frame in (0, 3, 4, 11, 12):
        hx, hy, sx, sy = _lich_body(c)
    elif frame == 1:
        hx, hy, sx, sy = _lich_body(c, fl=2)
    elif frame == 2:
        hx, hy, sx, sy = _lich_body(c, arm="raise", staff="raise")
    elif frame == 5:
        hx, hy, sx, sy = _lich_body(c, arm="raise", staff="side")
    elif frame == 6:
        hx, hy, sx, sy = _lich_body(c, staff="thrust")
    elif frame == 7:
        hx, hy, sx, sy = _lich_body(c, sag=6, hood_tilt=-5)
    elif frame == 8:
        hx, hy, sx, sy = _lich_body(c, sag=10, hood_tilt=-6, eyes=False)
        c = _squash(c, 0.85)
    elif frame == 9:
        tmp = Canvas(96, 112)
        _lich_body(tmp, sag=14, hood_tilt=-8, eyes=False)
        c = _rotate(_squash(tmp, 0.6, 1.1), 18, 48, 110)
        _fade(c, 0.3)
        c.outline(OUT)
        return c
    else:
        # 빈 로브와 굴러떨어진 해골·뼈관·지팡이
        c.poly([(14, 104), (30, 92), (60, 90), (84, 100), (88, 110), (8, 110)], LI_ROBE_D)
        c.poly([(24, 100), (40, 94), (70, 96), (78, 106), (20, 108)], LI_ROBE)
        c.line([(10, 106), (84, 96)], rgb("#5a3a22"), 3)
        c.ellipse((56, 92, 72, 106), LI_BONE)
        c.flat_rect((60, 96, 62, 98), rgb("#0a0612")); c.flat_rect((66, 96, 68, 98), rgb("#0a0612"))
        for k in range(4):
            c.poly([(54 + k * 5, 92), (56 + k * 5, 86 - (k % 2) * 2), (58 + k * 5, 92)], rgb("#d8ccb0"))
        _fade(c, 0.3)
        c.outline(OUT)
        return c
    c.outline(OUT)
    # 지팡이 끝 연기 + 눈빛
    big = frame in (2, 5, 6)
    for (dx, dy, col, r) in ((0, -6, LI_GLOW, 12), (-5, -12, rgb("#40c080"), 9), (5, -14, LI_GLOW, 8)):
        c.glow(sx + dx, sy + dy, r + (6 if big else 0), col, 0.55)
    if frame == 2:
        c.glow(16, 8, 18, LI_GLOW, 0.6)
    if eyes:
        c.glow(hx - 4, hy + 18, 5, rgb("#d070ff"), 0.6)
        c.glow(hx + 4, hy + 18, 5, rgb("#d070ff"), 0.6)
    return c


# ===================================================================== 마룡 176x144 (검은 비늘, 보랏빛 마맥)
DR, DR_D, DR_H = rgb("#2c2832"), rgb("#18151c"), rgb("#4c4658")
DR_VEIN = rgb("#c050ff")
DR_HORN = rgb("#8a8292")
DR_MEMB = rgb("#3a2a44")


def _veins(c, pts, w=1):
    c.line(pts, DR_VEIN, w)


def _dragon_front(c, pose):
    """정면 마룡. pose: 0/1 대기, 2 아래 포효, 3 위 포효, 4 꼬리 들기, 5 앞발 치켜듦, 6 할퀴기, 7 숙임."""
    wing = 8 if pose == 1 else (-8 if pose == 5 else 0)
    rear = 14 if pose == 4 else 0
    up = 10 if pose == 5 else 0
    for side in (-1, 1):
        cx = 88
        tip = (cx + side * 86, 6 + wing)
        pts = [(cx + side * 20, 52 - rear // 2 - up), tip, (cx + side * 82, 34 + wing), (cx + side * 76, 66 + wing // 2),
               (cx + side * 64, 56), (cx + side * 56, 80), (cx + side * 42, 64), (cx + side * 30, 78)]
        c.poly(pts, DR_MEMB, hi=18, lo=-20)
        c.line([(cx + side * 20, 52 - rear // 2 - up), tip], DR_H, 3)
        for (ex, ey) in ((64, 56), (56, 80), (42, 64)):
            c.line([(cx + side * 26, 54 - up), (cx + side * ex, ey)], DR_D, 2)
        c.poly([(tip[0], tip[1]), (tip[0] - side * 4, tip[1] - 6), (tip[0] - side * 6, tip[1] + 2)], DR_HORN)
    # 꼬리
    ty = 112 - rear * 3
    c.poly([(70, 108), (40, 120 - rear * 2), (16, ty), (6, ty - 6), (22, ty - 10), (50, 108 - rear), (74, 98)], DR)
    for k in range(4):
        x = 20 + k * 12
        c.poly([(x, ty - 8 + k * 2 - (rear if k < 2 else 0)), (x + 4, ty - 16 + k * 2 - (rear if k < 2 else 0)), (x + 8, ty - 8 + k * 2 - (rear if k < 2 else 0))], DR_HORN)
    _veins(c, [(70, 104), (44, 114 - rear * 2), (20, ty - 2)])
    if pose == 4:
        for k in range(5):
            c.poly([(12 + k * 9, ty - 14 - k), (16 + k * 9, ty - 24 - k), (20 + k * 9, ty - 14 - k)], rgb("#a6e8ff"))
    # 뒷다리
    c.rrect((50, 96 - rear, 72, 134 - rear), 7, DR_D)
    c.rrect((104, 96, 126, 134), 7, DR_D)
    for x in (50, 104):
        r = rear if x == 50 else 0
        for k in range(3):
            c.poly([(x + 3 + k * 7, 132 - r), (x + 6 + k * 7, 140 - r), (x + 9 + k * 7, 132 - r)], DR_HORN)
    # 몸통
    c.ellipse((44, 52 - rear - up, 132, 118 - rear // 2), DR, hi=26, lo=-20)
    c.ellipse((66, 66 - rear - up, 110, 116 - rear // 2), rgb("#3a3244"))
    for k in range(5):
        c.line([(70, 74 + k * 8 - rear - up), (106, 74 + k * 8 - rear - up)], DR_D)
    _veins(c, [(60, 70 - up), (66, 84), (62, 100)], 2)
    _veins(c, [(116, 70 - up), (112, 86), (118, 100)], 2)
    _veins(c, [(88, 70 - up), (88, 112)])
    # 등 가시
    for k in range(6):
        x = 56 + k * 13
        c.poly([(x, 56 - rear - up), (x + 5, 42 - rear - up), (x + 10, 56 - rear - up)], DR_HORN)
    # 앞발
    if pose in (5, 6):
        claw_y = 40 if pose == 5 else 108
        for side in (-1, 1):
            ax = 88 + side * 34
            c.line([(88 + side * 20, 80 - up), (ax + side * (6 if pose == 6 else 0), claw_y)], DR_D, 10)
            for k in range(3):
                c.poly([(ax - 5 + k * 5, claw_y + 2), (ax - 3 + k * 5, claw_y + 10), (ax - 1 + k * 5, claw_y + 2)], DR_HORN)
    # 목·머리
    if pose == 2:
        neck_end, hy = (88, 92), 88
    elif pose == 3:
        neck_end, hy = (88, 18), 4
    elif pose == 7:
        neck_end, hy = (88, 80), 76
    elif pose == 6:
        neck_end, hy = (88, 56), 46
    else:
        neck_end, hy = (88, 40 - up), 24 - up
    c.poly([(74, 66 - rear - up), (102, 66 - rear - up), (neck_end[0] + 12, neck_end[1] + 10), (neck_end[0] - 12, neck_end[1] + 10)], DR)
    _veins(c, [(88, 66 - rear - up), (neck_end[0], neck_end[1] + 10)])
    c.ellipse((66, hy, 110, hy + 36), DR, hi=30)
    c.rrect((74, hy + 22, 102, hy + 42), 6, DR_H)
    # 뿔 (뒤로 휜 큰 뿔 두 쌍)
    c.poly([(70, hy + 10), (48, hy - 16), (58, hy - 18), (78, hy + 4)], DR_HORN, hi=40)
    c.poly([(106, hy + 10), (128, hy - 16), (118, hy - 18), (98, hy + 4)], DR_HORN, hi=40)
    c.poly([(80, hy + 4), (74, hy - 10), (86, hy + 2)], shade(DR_HORN, -30))
    c.poly([(96, hy + 4), (102, hy - 10), (90, hy + 2)], shade(DR_HORN, -30))
    for k in range(3):
        c.poly([(68 + k * 2, hy + 24 + k * 4), (60, hy + 26 + k * 5), (68 + k * 2, hy + 28 + k * 4)], DR_HORN)
        c.poly([(108 - k * 2, hy + 24 + k * 4), (116, hy + 26 + k * 5), (108 - k * 2, hy + 28 + k * 4)], DR_HORN)
    eye = rgb("#e080ff")
    c.flat_rect((76, hy + 14, 82, hy + 18), eye); c.flat_rect((94, hy + 14, 100, hy + 18), eye)
    c.flat_rect((78, hy + 15, 79, hy + 17), rgb("#200030")); c.flat_rect((97, hy + 15, 98, hy + 17), rgb("#200030"))
    if pose in (2, 3, 6):
        c.rrect((78, hy + 32, 98, hy + 46), 4, rgb("#2a0830"))
        for k in range(4):
            c.poly([(80 + k * 5, hy + 32), (82 + k * 5, hy + 37), (84 + k * 5, hy + 32)], rgb("#f0e8d0"))
            c.poly([(80 + k * 5, hy + 46), (82 + k * 5, hy + 41), (84 + k * 5, hy + 46)], rgb("#f0e8d0"))
    return hy, ty


def dragon(frame):
    c = Canvas(176, 144)
    if frame in (0, 1, 2, 3, 4, 5, 6, 7):
        pose = frame if frame <= 6 else 7
        hy, ty = _dragon_front(c, pose)
        c.outline(OUT)
        c.glow(79, hy + 16, 9, rgb("#e080ff"), 0.6)
        c.glow(97, hy + 16, 9, rgb("#e080ff"), 0.6)
        if frame in (2, 3):
            c.glow(88, hy + 44, 16, rgb("#c060ff"), 0.55)   # 보랏빛 숨결
        if frame == 4:
            c.glow(20, ty - 16, 26, rgb("#9ae8ff"), 0.5)
        for (x, y) in ((62, 84), (114, 84), (88, 96)):
            c.glow(x, y, 6, DR_VEIN, 0.4)
        return c
    if frame == 8:
        tmp = Canvas(176, 144)
        _dragon_front(tmp, 7)
        c = _squash(tmp, 0.82)
        _fade(c, 0.15)
        c.outline(OUT)
        return c
    if frame == 9:
        tmp = Canvas(176, 144)
        _dragon_front(tmp, 7)
        c = _rotate(_squash(tmp, 0.62, 1.05), -12, 88, 140)
        _fade(c, 0.35)
        c.outline(OUT)
        return c
    if frame == 10:
        # 옆으로 쓰러진 몸, 축 처진 날개, 보랏빛 재
        c.poly([(10, 132), (40, 112), (100, 104), (150, 112), (170, 132), (90, 140)], DR_MEMB)
        c.ellipse((40, 100, 140, 138), DR)
        c.ellipse((120, 108, 166, 132), DR)
        c.poly([(150, 112), (172, 98), (164, 114)], DR_HORN)
        c.poly([(40, 120), (6, 128), (2, 136), (44, 132)], DR)
        _veins(c, [(50, 120), (100, 116), (136, 120)])
        _fade(c, 0.55)
        c.outline(OUT)
        return c
    if frame == 11:
        # 뒷모습: 날개를 펴고 등을 보인다, 꼬리가 앞으로
        for side in (-1, 1):
            cx = 88
            tip = (cx + side * 86, 4)
            c.poly([(cx + side * 18, 56), tip, (cx + side * 80, 36), (cx + side * 74, 72), (cx + side * 60, 60),
                    (cx + side * 50, 84), (cx + side * 36, 68)], shade(DR_MEMB, -10))
            c.line([(cx + side * 18, 56), tip], DR_H, 3)
        c.ellipse((46, 50, 130, 120), DR, hi=20)
        for k in range(7):
            y = 50 + k * 10
            c.poly([(82, y), (88, y - 12), (94, y)], DR_HORN)
        _veins(c, [(60, 70), (70, 100)], 2)
        _veins(c, [(116, 70), (106, 100)], 2)
        c.ellipse((70, 20, 106, 50), DR)
        c.poly([(72, 30), (54, 4), (64, 4), (80, 26)], DR_HORN)
        c.poly([(104, 30), (122, 4), (112, 4), (96, 26)], DR_HORN)
        c.poly([(80, 112), (96, 112), (100, 140), (88, 144), (76, 140)], DR)
        c.rrect((54, 108, 74, 136), 7, DR_D); c.rrect((102, 108, 122, 136), 7, DR_D)
        c.outline(OUT)
        return c
    # 12 옆모습 (오른쪽을 본다)
    c.poly([(70, 60), (40, 4), (56, 20), (82, 6), (100, 52)], DR_MEMB)
    c.line([(70, 60), (40, 4)], DR_H, 3)
    c.poly([(40, 96), (8, 110), (2, 100), (14, 94), (46, 84)], DR)
    c.ellipse((34, 64, 126, 116), DR, hi=24)
    c.ellipse((60, 84, 120, 116), rgb("#3a3244"))
    for k in range(6):
        c.poly([(44 + k * 12, 70 - abs(k - 2) * 2), (48 + k * 12, 56 - abs(k - 2) * 2), (52 + k * 12, 70 - abs(k - 2) * 2)], DR_HORN)
    c.rrect((50, 104, 70, 136), 7, DR_D); c.rrect((96, 104, 116, 136), 7, DR_D)
    c.poly([(108, 76), (126, 40), (140, 44), (124, 84)], DR)
    c.ellipse((122, 26, 168, 56), DR, hi=30)
    c.rrect((146, 40, 174, 56), 5, DR_H)
    c.poly([(130, 32), (108, 8), (120, 10), (140, 28)], DR_HORN)
    c.flat_rect((150, 34, 156, 37), rgb("#e080ff"))
    _veins(c, [(50, 86), (80, 96), (112, 92)], 2)
    c.outline(OUT)
    c.glow(153, 35, 8, rgb("#e080ff"), 0.6)
    return c


# ===================================================================== 마왕 1페이즈 128x128 (리치 왕)
DK_ROBE, DK_ROBE_D = rgb("#43255a"), rgb("#24142e")
DK_GOLD, DK_GOLD_D = rgb("#dcac40"), rgb("#8a6420")
DK_BONE = rgb("#e8dfc4")
DK_GEM = rgb("#b060ff")


def demon_king(frame):
    """머리 영역(46,0)-(82,46)은 머리 포탑 패턴이 잘라 쓰므로 그 안에 머리와 관을 둔다."""
    c = Canvas(128, 128)
    fl = (0, 2, 0, 0, 0, 1, 0, 3, 2, 2, 2, 0, 0)[frame]
    raise_ = frame in (2, 5)
    c.glow(64, 76, 60, rgb("#9040ff"), 0.22)
    # 보랏빛 소용돌이 (발 아래)
    for k in range(3):
        y = 108 + k * 5
        c.line([(30 + k * 6, y), (64, y + 4), (98 - k * 6, y)], (176, 96, 255, 150 - k * 30), 2)
    # 매달린 뼈 다리
    for side in (-1, 1):
        lx = 64 + side * 9
        c.line([(lx, 96 + fl), (lx + side * 2, 116 + fl)], DK_BONE, 3)
        c.ellipse((lx + side * 2 - 4, 114 + fl, lx + side * 2 + 4, 120 + fl), DK_BONE)
    # 로브
    pts = [(38, 40 + fl), (90, 40 + fl), (106, 100 + fl)]
    for i in range(9):
        x = 104 - i * 9
        pts.append((x - 4, 112 + fl - (i % 2) * 10))
    pts.append((22, 100 + fl))
    c.poly(pts, DK_ROBE, hi=24, lo=-28)
    # 금 테두리 장식
    c.line([(38, 40 + fl), (22, 100 + fl)], DK_GOLD, 2)
    c.line([(90, 40 + fl), (106, 100 + fl)], DK_GOLD, 2)
    c.poly([(54, 66 + fl), (74, 66 + fl), (72, 108 + fl), (64, 112 + fl), (56, 108 + fl)], DK_ROBE_D)
    c.poly([(57, 66 + fl), (71, 66 + fl), (69, 104 + fl), (64, 108 + fl), (59, 104 + fl)], DK_GOLD_D)
    for y in (74, 88):
        c.poly([(64, y + fl), (68, y + 5 + fl), (64, y + 10 + fl), (60, y + 5 + fl)], DK_GEM, flat=True)
    # 드러난 갈비뼈 + 가슴 자수정
    c.rrect((46, 40 + fl, 82, 66 + fl), 6, rgb("#22142e"))
    c.line([(64, 42 + fl), (64, 66 + fl)], DK_BONE, 2)
    for k in range(4):
        c.line([(49, 44 + k * 5 + fl), (62, 47 + k * 5 + fl)], DK_BONE, 2)
        c.line([(79, 44 + k * 5 + fl), (66, 47 + k * 5 + fl)], DK_BONE, 2)
    c.poly([(64, 47 + fl), (70, 54 + fl), (64, 63 + fl), (58, 54 + fl)], DK_GEM, hi=60)
    # 금 어깨 갑주
    for side in (-1, 1):
        cx = 64 + side * 28
        c.ellipse((cx - 13, 32 + fl, cx + 13, 50 + fl), DK_GOLD, hi=50, lo=-40)
        c.poly([(cx - 4 * side, 34 + fl), (cx + 16 * side, 18 + fl), (cx + 10 * side, 38 + fl)], DK_GOLD)
        c.flat_ellipse((cx - 3, 38 + fl, cx + 3, 44 + fl), DK_GEM)
    # 넓은 소매 + 뼈 손 (구슬)
    hand_y = 40 if raise_ else 64
    for side in (-1, 1):
        sx = 64 + side * 34
        hx = 64 + side * 52
        c.poly([(sx - 7, 44 + fl), (sx + 7, 44 + fl), (hx + 9 * side, hand_y + 8 + fl), (hx - 7 * side, hand_y + 12 + fl)], DK_ROBE)
        c.line([(sx, 46 + fl), (hx + 4 * side, hand_y + 6 + fl)], DK_GOLD_D, 1)
        for k in (-3, 0, 3):
            c.line([(hx + k, hand_y + 6 + fl), (hx + k * 1.3, hand_y - 4 + fl)], DK_BONE, 1)
    # 해골 + 높은 금관 (보석)
    c.ellipse((51, 16 + fl, 77, 42 + fl), DK_BONE)
    c.rrect((55, 34 + fl, 73, 45 + fl), 3, DK_BONE)
    crown = [(47, 24 + fl), (49, 8 + fl), (54, 14 + fl), (57, 0 + fl), (61, 10 + fl), (64, -2 + fl + 2), (67, 10 + fl), (71, 0 + fl),
             (74, 14 + fl), (79, 8 + fl), (81, 24 + fl)]
    c.poly(crown, DK_GOLD, hi=50, lo=-40)
    c.rect((47, 19 + fl, 81, 24 + fl), DK_GOLD_D)
    c.poly([(64, 8 + fl), (68, 14 + fl), (64, 21 + fl), (60, 14 + fl)], DK_GEM, hi=60)
    c.flat_rect((55, 26 + fl, 61, 31 + fl), rgb("#10061a")); c.flat_rect((67, 26 + fl, 73, 31 + fl), rgb("#10061a"))
    c.flat_rect((57, 27 + fl, 59, 29 + fl), rgb("#e0a0ff")); c.flat_rect((69, 27 + fl, 71, 29 + fl), rgb("#e0a0ff"))
    c.poly([(63, 33 + fl), (65, 33 + fl), (64, 36 + fl)], rgb("#3a3028"))
    for k in range(5):
        c.px(58 + k * 3, 42 + fl, rgb("#3a3028"))
    if frame >= 8:
        _fade(c, 0.25)
    c.outline(OUT)
    # 두 손의 마력 구슬
    for side in (-1, 1):
        hx = 64 + side * 52
        r = 9 + (3 if raise_ else 0) + (1 if frame == 1 else 0)
        oy = hand_y - 8 + fl
        c.glow(hx, oy, r * 2 + 4, rgb("#b070ff"), 0.7)
        c.ellipse((hx - r, oy - r, hx + r, oy + r), rgb("#c890ff"), hi=60)
        for k in range(4):
            a = k * math.pi / 2 + frame
            c.px(hx + math.cos(a) * (r + 3), oy + math.sin(a) * (r + 3), rgb("#f0d0ff"))
        c.px(hx - 3, oy - 4, rgb("#ffffff")); c.px(hx - 2, oy - 4, rgb("#ffffff"))
    c.glow(58, 29 + fl, 6, rgb("#d080ff"), 0.6)
    c.glow(70, 29 + fl, 6, rgb("#d080ff"), 0.6)
    c.glow(64, 55 + fl, 10, rgb("#b060ff"), 0.6)
    for (x, y) in ((22, 60), (106, 52), (30, 96), (100, 92)):
        c.flat_poly([(x, y - 4), (x + 2, y), (x, y + 4), (x - 2, y)], rgb("#c080ff"))
    return c


# ===================================================================== 마왕 본모습 176x176
EL, EL_D = rgb("#2a1e34"), rgb("#140c1c")
EL_TENT, EL_TENT_G = rgb("#3a2a4a"), rgb("#40c090")
EL_EYE = rgb("#7af0e0")


def _eye(c, x, y, r, shut=0.0, glow=True):
    if shut >= 1:
        c.line([(x - r, y), (x + r, y)], rgb("#0a0610"), 2)
        return
    ry = r * 0.75 * (1 - shut)
    c.flat_ellipse((x - r - 1, y - ry - 1, x + r + 1, y + ry + 1), rgb("#0a0610"))
    c.flat_ellipse((x - r, y - ry, x + r, y + ry), rgb("#d8fff4"))
    c.flat_ellipse((x - r * 0.55, y - min(ry, r * 0.55), x + r * 0.55, y + min(ry, r * 0.55)), EL_EYE)
    c.flat_ellipse((x - r * 0.2, y - min(ry, r * 0.45), x + r * 0.2, y + min(ry, r * 0.45)), rgb("#06040a"))


def _tentacle(c, sx, sy, ang, length, frame, i, width=14, curl=0.08):
    prev = (sx, sy)
    segs = 16
    for k in range(1, segs + 1):
        t = k / segs
        ang += math.sin(frame * 1.3 + i * 0.9 + t * 4) * 0.2 + curl * t
        x = prev[0] + math.cos(ang) * length / segs
        y = prev[1] + math.sin(ang) * length / segs * 0.8
        w = max(2, int(width * (1 - t)) + 2)
        col = mix(EL_TENT, EL_TENT_G, 0.25 if k % 4 else 0.6)
        c.line([prev, (x, y)], col, w)
        if k % 3 == 0 and w > 4:
            c.flat_ellipse((x - 1.5, y - 1.5, x + 1.5, y + 1.5), rgb("#a0ffd0"))
        prev = (x, y)


def eldritch(frame, shut=0.0, melt=0.0):
    """수많은 청록 눈, 별빛 아가리, 녹색·보라 촉수, 얼굴이 박힌 눈알 구체."""
    c = Canvas(176, 176)
    tf = frame % 2
    big = frame in (2, 5, 6)
    c.glow(88, 100, 86, rgb("#9a40ff"), 0.22)
    for i in range(12):
        base = math.pi * (0.02 + 0.96 * i / 11)
        sx, sy = 88 + math.cos(base) * 46, 104 + math.sin(base) * 34
        _tentacle(c, sx, sy, base, (74 + (i % 3) * 14) * (1 - melt * 0.5), tf, i, curl=0.08 if i < 6 else -0.08)
    # 위로 솟은 촉수
    for i in range(4):
        base = -math.pi * (0.2 + 0.6 * i / 3)
        _tentacle(c, 88 + math.cos(base) * 40, 60 + math.sin(base) * 20, base, 60 * (1 - melt), tf, i + 20, width=10)
    # 몸통 덩어리
    c.ellipse((32, 26 + melt * 30, 144, 144), EL, hi=20, lo=-20)
    c.ellipse((46, 40 + melt * 30, 130, 136), EL_D)
    # 별이 보이는 아가리
    my = 102 + melt * 10
    mh = 20 if not big else 26
    c.ellipse((60, my - mh + 10, 116, my + mh + 10), rgb("#06030c"))
    for k in range(14):
        x, y = 66 + (k * 37) % 44, my - 6 + (k * 23) % (mh * 2 - 4)
        c.px(x, y, rgb("#c0d0ff") if k % 3 else rgb("#ffffff"))
    for k in range(9):
        x = 62 + k * 6
        c.poly([(x, my - mh + 12 + abs(k - 4) * 2), (x + 3, my - mh + 22), (x + 6, my - mh + 12 + abs(k - 4) * 2)], rgb("#e8e0c8"))
        c.poly([(x, my + mh + 8 - abs(k - 4) * 2), (x + 3, my + mh - 2), (x + 6, my + mh + 8 - abs(k - 4) * 2)], rgb("#e8e0c8"))
    # 눈들 (가운데 큰 눈 + 여러 작은 눈)
    s = max(shut, 0.0)
    eyes = [(88, 56, 15), (60, 50, 8), (116, 50, 8), (70, 74, 6), (106, 74, 6), (46, 72, 5), (130, 72, 5), (88, 32, 6),
            (52, 94, 4), (124, 94, 4), (74, 36, 4), (102, 36, 4)]
    for j, (x, y, r) in enumerate(eyes):
        blink = (frame == 1 and j in (3, 8)) and s == 0
        _eye(c, x, y + melt * 30, r, shut=1 if blink else s)
    # 얼굴이 박힌 눈알 구체 둘
    for (x, y) in ((24, 70), (152, 78)):
        c.ellipse((x - 14, y - 14, x + 14, y + 14), rgb("#3a3048"), hi=30)
        c.ellipse((x - 6, y - 7, x + 6, y + 7), rgb("#8a8478"))
        c.px(x - 2, y - 2, rgb("#20180c")); c.px(x + 2, y - 2, rgb("#20180c")); c.line([(x - 2, y + 3), (x + 2, y + 3)], rgb("#20180c"))
        for a in range(0, 360, 60):
            ex, ey = x + math.cos(math.radians(a)) * 11, y + math.sin(math.radians(a)) * 11
            _eye(c, ex, ey, 2.5, shut=s)
    # 뿔 왕관
    for k in range(7):
        x = 46 + k * 14
        c.poly([(x, 40 + melt * 30), (x + 6, 14 - (k % 2) * 8 + melt * 30), (x + 12, 40 + melt * 30)], rgb("#4a2a5a"))
    c.outline(OUT)
    if s < 1:
        c.glow(88, 56 + melt * 30, 26 if big else 20, EL_EYE, 0.4 * (1 - s))
        for (x, y, r) in eyes[1:6]:
            c.glow(x, y + melt * 30, r + 5, EL_EYE, 0.3 * (1 - s))
    for (x, y) in ((20, 30), (156, 34)):
        c.flat_poly([(x, y - 8), (x + 4, y), (x, y + 8), (x - 4, y)], rgb("#c070ff"))
        c.glow(x, y, 10, rgb("#c070ff"), 0.5)
    return c


def eldritch_frame(frame):
    if frame <= 7 or frame >= 11:
        return eldritch(frame if frame < 8 else 0)
    if frame == 8:
        return eldritch(0, shut=0.6, melt=0.1)
    if frame == 9:
        c = eldritch(0, shut=1.0, melt=0.4)
        return _fade(_squash(c, 0.8, 1.05), 0.3)
    c = eldritch(0, shut=1.0, melt=0.8)
    return _fade(_squash(c, 0.45, 1.15), 0.55)
