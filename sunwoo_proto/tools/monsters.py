"""
잡몹 그림 (참고 그림 3·6·7·10번). 프레임: 0 걷기1, 1 걷기2, 2 공격 준비(무기 들어 올림), 3 공격(휘두름).
- zombie: 갱도 좀비 36x40 (잿빛 피부, 긴 머리, 찢어진 녹색 셔츠·드러난 갈비뼈, 녹슨 단검)
- skeleton_soldier: 해골 병사 36x40 (찌그러진 투구, 보랏빛 눈, 나무 원형 방패, 녹슨 검)
- skeleton_knight: 해골 기사 48x56 (가시 돋친 검은 갑옷, 해골 투구, 보랏빛 눈, 대검, 해진 보라 망토)
- drakeling: 탑의 비룡 48x44 (모래빛 새끼 용, 보랏빛 마력 구슬)
"""
import math
from art import Canvas, rgb, shade

OUT = (14, 10, 18, 255)
RUST, RUST_D = rgb("#a0886e"), rgb("#7a4a2a")


def _blade(c, x0, y0, x1, y1, color=RUST, width=3, hilt=rgb("#6a4024"), guard=rgb("#8a6a3a")):
    """칼날: 손잡이 (x0,y0) → 칼끝 (x1,y1)."""
    ang = math.atan2(y1 - y0, x1 - x0)
    gx, gy = x0 + math.cos(ang) * 3, y0 + math.sin(ang) * 3
    c.line([(x0 - math.cos(ang) * 3, y0 - math.sin(ang) * 3), (gx, gy)], hilt, 2)
    c.line([(gx, gy), (x1, y1)], color, width)
    c.line([(gx + math.cos(ang) * 2, gy + math.sin(ang) * 2), (x1, y1)], shade(color, 40), 1)
    px, py = -math.sin(ang), math.cos(ang)
    c.line([(gx + px * 4, gy + py * 4), (gx - px * 4, gy - py * 4)], guard, 2)


# ===================================================================== 갱도 좀비
def zombie(frame):
    c = Canvas(36, 40)
    skin, skin_d = rgb("#8f9c86"), rgb("#5e6a58")
    hair = rgb("#bdb8a6")
    shirt, pants = rgb("#5b7448"), rgb("#6b4a2e")
    sway = (1, -1, 0, 0)[frame]
    lean = (0, 0, -2, 3)[frame]
    # 다리 (해진 바지 + 맨발)
    c.rect((13, 27, 16, 34 + sway), pants)
    c.rect((20, 27, 23, 34 - sway), pants)
    c.poly([(12, 33 + sway), (17, 33 + sway), (15, 35 + sway)], shade(pants, -20))
    c.rrect((12, 34 + sway, 17, 38 + sway), 1, skin_d)
    c.rrect((19, 34 - sway, 24, 38 - sway), 1, skin_d)
    # 몸통: 찢어진 셔츠, 갈비뼈
    x = lean
    c.rrect((11 + x, 15, 25 + x, 28), 3, shirt)
    c.poly([(11 + x, 27), (14 + x, 30), (17 + x, 27), (20 + x, 31), (23 + x, 27), (25 + x, 29), (25 + x, 26), (11 + x, 26)], shirt)
    c.flat_ellipse((15 + x, 18, 21 + x, 25), rgb("#3a2e2a"))
    for k in range(3):
        c.line([(16 + x, 19 + k * 2), (20 + x, 19 + k * 2)], rgb("#d8d0b8"))
    c.flat_rect((11 + x, 25, 25 + x, 26), rgb("#b8a070"))                 # 밧줄 허리띠
    # 왼팔: 축 늘어짐
    c.rrect((6 + x, 17, 11 + x, 30 - sway), 2, skin)
    c.poly([(6 + x, 29 - sway), (11 + x, 29 - sway), (10 + x, 33 - sway), (7 + x, 33 - sway)], skin_d)
    # 오른팔 + 녹슨 단검
    if frame == 2:      # 머리 위로 치켜듦
        c.rrect((24 + x, 6, 29 + x, 19), 2, skin)
        _blade(c, 27 + x, 7, 20 + x, -1 + 2)
    elif frame == 3:    # 앞으로 내리그음
        c.rrect((24 + x, 18, 32 + x, 23), 2, skin)
        _blade(c, 31 + x, 21, 35, 33)
    else:
        c.rrect((25, 17, 30, 29 + sway), 2, skin)
        _blade(c, 28, 29 + sway, 30, 37 + sway)
    # 머리: 퀭한 눈, 벌린 입, 긴 머리카락
    hx = 18 + x + (2 if frame == 3 else 0)
    c.ellipse((hx - 6, 4, hx + 6, 16), skin)
    c.ellipse((hx - 7, 2, hx + 7, 9), hair)
    for sx in (hx - 7, hx - 6, hx + 6, hx + 7):
        c.line([(sx, 6), (sx + (1 if sx < hx else -1) * 0, 18 + (sx % 3))], hair, 1)
    c.flat_rect((hx - 4, 9, hx - 2, 10), rgb("#1a1a14")); c.flat_rect((hx + 2, 9, hx + 4, 10), rgb("#1a1a14"))
    c.px(hx - 3, 9, rgb("#e0e0b0")); c.px(hx + 3, 9, rgb("#e0e0b0"))
    c.flat_ellipse((hx - 2, 12, hx + 2, 15), rgb("#2a1414"))
    c.px(hx - 1, 12, rgb("#e8e0c8")); c.px(hx + 1, 12, rgb("#e8e0c8"))
    c.px(hx - 5, 11, rgb("#a04040"))                                       # 상처
    return c.outline(OUT)


# ===================================================================== 해골 병사
def skeleton_soldier(frame):
    c = Canvas(36, 40)
    bone, bone_d = rgb("#ddd6c4"), rgb("#a49c88")
    helm = rgb("#7c7a70")
    wood, wood_d = rgb("#8a5a32"), rgb("#5a3a1e")
    s = (1, -1, 0, 0, 0)[frame]
    lunge = 3 if frame == 3 else 0
    # 다리 (뼈) + 허리 천
    c.line([(15, 27), (14 - s, 36)], bone, 2)
    c.line([(21, 27), (22 + s, 36)], bone, 2)
    c.rrect((11 - s, 35, 16 - s, 38), 1, bone_d)
    c.rrect((20 + s, 35, 25 + s, 38), 1, bone_d)
    c.poly([(13, 24), (23, 24), (24, 31), (20, 29), (17, 32), (13, 29)], rgb("#5a4030"))
    # 갈비뼈 몸통
    c.rrect((13 + lunge, 14, 23 + lunge, 25), 3, rgb("#2a2228"))
    c.line([(18 + lunge, 14), (18 + lunge, 25)], bone, 2)
    for k in range(4):
        c.line([(14 + lunge, 16 + k * 2), (22 + lunge, 16 + k * 2)], bone)
    c.ellipse((11 + lunge, 13, 16 + lunge, 17), bone)
    c.ellipse((20 + lunge, 13, 25 + lunge, 17), bone)
    # 오른팔 + 녹슨 검
    if frame == 2:
        c.line([(23, 16), (28, 8)], bone, 2)
        _blade(c, 28, 8, 20, -0 + 1)
    elif frame == 3:
        c.line([(26, 16), (31, 20)], bone, 2)
        _blade(c, 31, 20, 36, 30)
    else:
        c.line([(23, 16), (26, 24 + s)], bone, 2)
        _blade(c, 26, 24 + s, 34, 32 + s)
    # 왼팔 + 나무 원형 방패
    c.line([(13 + lunge, 16), (9 + lunge, 22)], bone, 2)
    sx = 2 + lunge // 2
    c.ellipse((sx, 15, sx + 14, 31), wood_d)
    c.ellipse((sx + 1, 16, sx + 13, 30), wood)
    c.line([(sx + 4, 17), (sx + 4, 29)], wood_d); c.line([(sx + 9, 17), (sx + 9, 29)], wood_d)
    c.ellipse((sx + 5, 21, sx + 9, 25), rgb("#9a9488"))
    # 해골 머리 + 찌그러진 투구
    hx = 18 + lunge
    c.ellipse((hx - 6, 3, hx + 6, 15), bone)
    c.rrect((hx - 4, 11, hx + 4, 16), 2, bone)
    c.ellipse((hx - 7, 1, hx + 7, 9), helm)
    c.rect((hx - 7, 7, hx + 7, 8), shade(helm, -30), flat=True)
    c.px(hx + 3, 3, shade(helm, -40)); c.px(hx + 4, 4, shade(helm, -40))
    c.flat_rect((hx - 4, 9, hx - 2, 11), rgb("#120818")); c.flat_rect((hx + 2, 9, hx + 4, 11), rgb("#120818"))
    c.px(hx - 3, 10, rgb("#e070ff")); c.px(hx + 3, 10, rgb("#e070ff"))
    for k in range(4):
        c.px(hx - 3 + k * 2, 14, rgb("#4a4438"))
    c.outline(OUT)
    c.glow(hx - 3, 10, 4, rgb("#c060ff"), 0.6)
    c.glow(hx + 3, 10, 4, rgb("#c060ff"), 0.6)
    return c


# ===================================================================== 해골 기사
def skeleton_knight(frame):
    c = Canvas(48, 56)
    arm, arm_h, arm_d = rgb("#33333d"), rgb("#6a6a78"), rgb("#1c1c24")
    cape = rgb("#4a2052")
    bone = rgb("#ddd6c4")
    s = (1, -1, 0, 0, 0)[frame]
    # 해진 보라 망토 (뒤)
    pts = [(14, 16), (34, 16), (40, 50)]
    for i in range(6):
        pts.append((40 - i * 5 - 2, 54 - (i % 2) * 6))
    pts.append((8, 50))
    c.poly(pts, cape)
    # 다리 (갑주)
    c.rrect((15, 36, 21, 50 + s), 2, arm)
    c.rrect((27, 36, 33, 50 - s), 2, arm)
    c.rrect((13, 48 + s, 22, 54 + s), 2, arm_d)
    c.rrect((26, 48 - s, 35, 54 - s), 2, arm_d)
    c.ellipse((14, 38, 22, 44), arm_h); c.ellipse((26, 38, 34, 44), arm_h)   # 무릎
    # 몸통 갑주 + 가시
    c.rrect((13, 16, 35, 38), 5, arm, hi=40, lo=-20)
    c.poly([(24, 18), (30, 26), (24, 34), (18, 26)], arm_d)
    c.line([(24, 19), (24, 33)], arm_h)
    c.rect((13, 33, 35, 36), rgb("#3a2a24"), flat=True)
    c.flat_rect((22, 33, 26, 36), rgb("#8a8a96"))
    # 어깨 갑주 + 가시
    for side in (-1, 1):
        cx = 24 + side * 12
        c.ellipse((cx - 7, 13, cx + 7, 24), arm, hi=44)
        c.poly([(cx - 2, 14), (cx + side * 6, 5), (cx + 3, 15)], arm_h)
        c.poly([(cx + side * 3, 17), (cx + side * 11, 13), (cx + side * 6, 21)], arm_h)
    # 대검 (해골 코등이)
    if frame == 2:
        # 예고: 대검을 어깨 위로 높이 치켜든다 (크게 베기 직전)
        c.rrect((32, 14, 39, 24), 2, arm)
        _blade(c, 36, 18, 46, 0, color=rgb("#b4bac4"), width=5, guard=bone)
        c.ellipse((33, 16, 40, 22), bone)
    elif frame == 3:
        c.rrect((32, 20, 42, 27), 2, arm)
        _blade(c, 40, 24, 47, 52, color=rgb("#9aa0aa"), width=5, guard=bone)
        c.ellipse((37, 20, 44, 26), bone)
    elif frame == 4:
        # 막기: 대검을 가로로 눕혀 몸 앞을 막는다
        c.rrect((30, 22, 38, 30), 2, arm)
        _blade(c, 34, 26, 4, 18, color=rgb("#b4bac4"), width=5, guard=bone)
        c.ellipse((31, 23, 38, 29), bone)
    else:
        c.rrect((33, 20, 39, 33 + s), 2, arm)
        _blade(c, 36, 32 + s, 44, 54, color=rgb("#9aa0aa"), width=5, guard=bone)
        c.ellipse((33, 29 + s, 40, 35 + s), bone)
        c.flat_rect((35, 31 + s, 35, 31 + s), rgb("#120818")); c.flat_rect((38, 31 + s, 38, 31 + s), rgb("#120818"))
    c.rrect((9, 20, 15, 34 - s), 2, arm)
    c.rrect((8, 32 - s, 15, 37 - s), 2, arm_d)
    # 해골 투구
    c.ellipse((16, 1, 32, 18), arm, hi=40)
    c.poly([(16, 6), (12, -2 + 2), (19, 4)], arm_h)
    c.poly([(32, 6), (36, -2 + 2), (29, 4)], arm_h)
    c.poly([(24, 1), (26, -1 + 1), (25, 4)], arm_h)
    c.rrect((18, 7, 30, 17), 3, bone)
    c.flat_rect((19, 9, 22, 12), rgb("#120818")); c.flat_rect((26, 9, 29, 12), rgb("#120818"))
    c.flat_rect((20, 10, 21, 11), rgb("#e070ff")); c.flat_rect((27, 10, 28, 11), rgb("#e070ff"))
    for k in range(5):
        c.px(20 + k * 2, 15, rgb("#4a4438"))
    c.line([(16, 7), (32, 7)], arm_d)
    c.outline(OUT)
    c.glow(21, 10, 6, rgb("#c060ff"), 0.6)
    c.glow(28, 10, 6, rgb("#c060ff"), 0.6)
    return c


# ===================================================================== 탑의 비룡
def drakeling(frame):
    c = Canvas(48, 44)
    body, body_d = rgb("#a58e5c"), rgb("#6e5c3a")
    belly = rgb("#dcc48e")
    memb = rgb("#7a6646")
    w = (0, -5, -7, 2)[frame]
    # 날개
    for side in (-1, 1):
        cx = 24
        tip = (cx + side * 22, 2 + w)
        c.poly([(cx + side * 5, 16), tip, (cx + side * 20, 14 + w), (cx + side * 22, 22 + w // 2), (cx + side * 14, 20),
                (cx + side * 16, 28), (cx + side * 8, 24)], memb)
        c.line([(cx + side * 5, 16), tip], body_d, 2)
        c.line([(cx + side * 9, 18), (cx + side * 20, 14 + w)], body_d)
    # 꼬리
    c.poly([(20, 34), (10, 40), (4, 37), (8, 35), (16, 31)], body)
    c.poly([(4, 37), (1, 34), (6, 35)], belly)
    # 몸
    c.ellipse((15, 17, 33, 38), body)
    c.ellipse((19, 22, 29, 37), belly)
    for k in range(3):
        c.line([(20, 26 + k * 3), (28, 26 + k * 3)], shade(belly, -30))
    c.rect((17, 35, 20, 41), body_d); c.rect((28, 35, 31, 41), body_d)
    # 머리
    open_ = frame == 3
    c.ellipse((16, 4, 32, 18), body)
    c.rrect((19, 12, 29, 19 + (2 if open_ else 0)), 2, shade(body, 18))
    if open_:
        c.flat_rect((20, 16, 28, 19), rgb("#3a0e18"))
        c.px(21, 16, rgb("#f0e8d0")); c.px(27, 16, rgb("#f0e8d0"))
    c.poly([(18, 7), (13, 0), (20, 5)], belly)
    c.poly([(30, 7), (35, 0), (28, 5)], belly)
    c.flat_rect((19, 9, 21, 10), rgb("#ffd040")); c.flat_rect((27, 9, 29, 10), rgb("#ffd040"))
    c.px(20, 9, rgb("#2a1a00")); c.px(28, 9, rgb("#2a1a00"))
    # 앞발로 든 보랏빛 마력 구슬
    r = 4 + (2 if frame == 2 else 0)
    ox, oy = (15, 26) if frame != 3 else (13, 22)
    c.rrect((15, 24, 19, 28), 1, body_d); c.rrect((29, 24, 33, 28), 1, body_d)
    c.outline(OUT)
    for (x, y) in ((ox, oy), (48 - ox, oy)):
        c.glow(x, y, r + 5, rgb("#b060ff"), 0.6)
        c.ellipse((x - r, y - r, x + r, y + r), rgb("#c890ff"), hi=50)
        c.px(x - 1, y - 2, rgb("#ffffff"))
    # 떠다니는 자수정 조각
    for (x, y) in ((6, 8 + frame % 2), (42, 10 - frame % 2)):
        c.flat_poly([(x, y - 3), (x + 2, y), (x, y + 3), (x - 2, y)], rgb("#c080ff"))
    return c
