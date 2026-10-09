"""
소품·몬스터·보스 임시 그림 (gen_art.py 가 호출).
빛은 왼쪽 위에서 들어오며, 모든 그림에 어두운 외곽선을 둔다.
"""
import json
import math
import os
import random

from PIL import Image

from pixel import Canvas, hexc, mix, ramp, shade

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "assets", "sprites", "gen")
OUTLINE = hexc("#120d12")
MANIFEST = {}

WOOD = ramp("#7a4e2c", 6, 0.5, 1.35)
IRON = ramp("#6a6e78", 6, 0.45, 1.5)
STONE = ramp("#6e6670", 6, 0.5, 1.35)
GOLD = ramp("#c89a3a", 5, 0.6, 1.35)


def save(name, frames, fps=1, origin=None, anims=None, scale=1.0):
    """frames: Canvas 목록 → 가로 띠 PNG. anims: {이름: [프레임 번호...]}."""
    os.makedirs(OUT, exist_ok=True)
    w, h = frames[0].w, frames[0].h
    sheet = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f.image(), (i * w, 0))
    sheet.save(os.path.join(OUT, name + ".png"))
    ox, oy = origin if origin else (w / 2, 2)
    entry = {"originX": ox, "originY": oy}
    if scale != 1.0:
        entry["scale"] = scale
    file = "sprites/gen/" + name + ".png"
    if anims:
        entry["anims"] = {}
        for an, (order, afps) in anims.items():
            entry["anims"][an] = {"file": file, "frameWidth": w, "frameHeight": h, "frames": len(frames),
                                  "frameOrder": order, "fps": afps}
    else:
        entry.update({"file": file, "frameWidth": w, "frameHeight": h, "frames": len(frames), "fps": fps})
    MANIFEST[name] = entry


def cyl(cv, x0, y0, w, h, pal):
    """세로 원기둥 음영 (왼쪽이 밝음)."""
    for x in range(w):
        t = x / max(1, w - 1)
        lit = 1 - abs(t - 0.32) * 1.6
        i = max(0, min(len(pal) - 1, int(1 + lit * (len(pal) - 2))))
        for y in range(h):
            cv.set(x0 + x, y0 + y, pal[i])


def ell_fill(cv, cx, cy, rx, ry, c, a=255):
    for y in range(int(cy - ry), int(cy + ry) + 1):
        for x in range(int(cx - rx), int(cx + rx) + 1):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                cv.set(x, y, c, a)


# ---------------------------------------------------------------------------
# 소품
# ---------------------------------------------------------------------------

def barrel():
    cv = Canvas(22, 26)
    cyl(cv, 2, 5, 18, 19, WOOD)
    for x in range(4, 20, 4):
        cv.vline(x, 6, 23, WOOD[1])
    for y in (8, 18):
        cyl(cv, 2, y, 18, 2, IRON)
    ell_fill(cv, 11, 5, 9, 3.2, WOOD[4])
    ell_fill(cv, 11, 5, 7, 2.2, WOOD[3])
    cv.hline(6, 15, 5, WOOD[2])
    cv.outline(OUTLINE)
    return cv


def crate():
    cv = Canvas(24, 24)
    cv.rect(1, 1, 22, 7, WOOD[4])
    cv.rect(1, 8, 22, 15, WOOD[2])
    for y in (12, 17):
        cv.hline(1, 22, y, WOOD[1])
    for i in range(15):
        cv.set(2 + i * 20 // 14, 22 - i, WOOD[3])
        cv.set(3 + i * 20 // 14, 22 - i, WOOD[3])
    cv.rect(1, 8, 2, 15, WOOD[3])
    cv.rect(21, 8, 2, 15, WOOD[0])
    cv.hline(1, 22, 8, WOOD[5])
    for x, y in ((3, 10), (20, 10), (3, 21), (20, 21)):
        cv.set(x, y, IRON[4])
    for x in range(2, 22, 5):
        cv.vline(x, 2, 6, WOOD[3])
    cv.outline(OUTLINE)
    return cv


def pot():
    cv = Canvas(18, 20)
    clay = ramp("#9a5a38", 6, 0.5, 1.35)
    cv.ellipse(9, 12.5, 7.5, 6.5, clay)
    cv.rect(6, 3, 6, 4, clay[2])
    cv.hline(5, 12, 3, clay[4])
    cv.hline(5, 12, 2, clay[3])
    cv.hline(6, 11, 10, clay[1])
    cv.set(5, 9, clay[5])
    cv.set(5, 10, clay[5])
    cv.outline(OUTLINE)
    return cv


def bones():
    cv = Canvas(24, 12)
    bone = ramp("#d8d0bc", 4, 0.6, 1.1)
    cv.line(2, 8, 12, 4, bone[2])
    cv.line(3, 9, 13, 5, bone[1])
    for x, y in ((2, 8), (12, 4)):
        cv.rect(x - 1, y - 1, 2, 2, bone[3])
    cv.line(14, 9, 21, 8, bone[2])
    cv.rect(20, 7, 2, 2, bone[3])
    cv.outline(OUTLINE)
    return cv


def skull():
    cv = Canvas(14, 12)
    bone = ramp("#ddd4c0", 5, 0.55, 1.15)
    cv.ellipse(7, 5, 5.5, 4.5, bone)
    cv.rect(4, 8, 6, 3, bone[2])
    cv.rect(4, 5, 2, 2, OUTLINE)
    cv.rect(8, 5, 2, 2, OUTLINE)
    cv.set(7, 8, OUTLINE)
    cv.outline(OUTLINE)
    return cv


def rubble():
    cv = Canvas(26, 14)
    rnd = random.Random(4)
    for _ in range(7):
        cx, cy = rnd.randint(4, 21), rnd.randint(6, 10)
        cv.ellipse(cx, cy, rnd.randint(2, 4), rnd.randint(2, 3), STONE)
    cv.outline(OUTLINE)
    return cv


def minecart():
    cv = Canvas(32, 26)
    rust = ramp("#7a4a30", 6, 0.5, 1.3)
    for y in range(4, 18):
        inset = (y - 4) // 5
        for x in range(2 + inset, 30 - inset):
            i = 3 if y < 7 else 2
            if x < 4 + inset:
                i = 4
            if x > 27 - inset:
                i = 1
            cv.set(x, y, rust[i])
    cv.hline(2, 29, 4, IRON[5])
    cv.hline(2, 29, 5, IRON[3])
    for x in (8, 16, 24):
        cv.vline(x, 6, 16, rust[1])
    rnd = random.Random(7)
    for _ in range(6):
        cv.ellipse(rnd.randint(7, 24), rnd.randint(1, 4), 3, 2, STONE)
    for wx in (8, 23):
        cv.ellipse(wx, 20, 4, 4, IRON)
        cv.set(wx, 20, OUTLINE)
    cv.outline(OUTLINE)
    return cv


def weapon_rack():
    cv = Canvas(28, 32)
    cv.rect(2, 10, 24, 3, WOOD[3])
    cv.rect(2, 22, 24, 3, WOOD[2])
    cv.rect(3, 6, 3, 25, WOOD[2])
    cv.rect(22, 6, 3, 25, WOOD[1])
    for i, x in enumerate((9, 14, 19)):
        cv.vline(x, 1, 28, WOOD[4] if i != 1 else IRON[2])
        cv.rect(x - 1, 0, 3, 4, IRON[4])
        cv.set(x, 0, IRON[5])
    cv.outline(OUTLINE)
    return cv


def statue():
    cv = Canvas(24, 44)
    st = ramp("#7a7088", 6, 0.5, 1.35)
    cv.rect(2, 34, 20, 9, st[2])
    cv.hline(2, 21, 34, st[4])
    cv.rect(2, 41, 20, 2, st[0])
    # 두건 쓴 인물
    for y in range(8, 34):
        half = 4 + (y - 8) * 5 // 26
        for x in range(12 - half, 12 + half):
            i = 4 if x < 12 - half + 2 else 1 if x > 12 + half - 3 else 2
            cv.set(x, y, st[i])
    cv.ellipse(12, 7, 5, 5.5, st)
    cv.rect(10, 6, 4, 3, OUTLINE)
    cv.set(11, 7, hexc("#b08ae0"))
    cv.set(13, 7, hexc("#b08ae0"))
    cv.outline(OUTLINE)
    return cv


def candles():
    cv = Canvas(18, 16)
    wax = ramp("#e8dcc0", 4, 0.6, 1.1)
    for x, h in ((3, 8), (8, 11), (13, 6)):
        cv.rect(x, 15 - h, 3, h, wax[2])
        cv.vline(x, 15 - h, 14, wax[3])
        cv.set(x + 1, 14 - h, hexc("#ffd060"))
        cv.set(x + 1, 13 - h, hexc("#fff0a0"))
    cv.outline(OUTLINE)
    return cv


def cobweb(flip=False):
    cv = Canvas(20, 20)
    c = (220, 220, 230)
    for i in range(0, 20, 4):
        cv.line(0, i, i, 0, c, 90)
    cv.line(0, 0, 19, 19, c, 110)
    cv.line(0, 0, 8, 19, c, 90)
    cv.line(0, 0, 19, 8, c, 90)
    if flip:
        cv.px = [list(reversed(r)) for r in cv.px]
    return cv


def banner(color="#8a2a2a"):
    cv = Canvas(18, 34)
    cloth = ramp(color, 6, 0.5, 1.3)
    cv.rect(0, 0, 18, 2, WOOD[2])
    for y in range(2, 30):
        for x in range(2, 16):
            i = 4 if x < 4 else 1 if x > 13 else 3 if (x + y // 3) % 5 else 2
            cv.set(x, y, cloth[i])
    for x in range(2, 16):  # 끝단 V자
        d = abs(x - 8.5)
        for y in range(30, 34 - int(d / 2)):
            cv.set(x, y, cloth[2])
    gold = GOLD
    cv.rect(6, 10, 6, 6, gold[2])
    cv.set(8, 12, gold[4])
    cv.set(9, 13, gold[0])
    cv.outline(OUTLINE)
    return cv


def rug():
    cv = Canvas(96, 56)
    red = ramp("#8a3028", 6, 0.55, 1.3)
    gold = GOLD
    ell_fill(cv, 48, 28, 47, 27, red[2])
    ell_fill(cv, 48, 28, 43, 24, red[3])
    ell_fill(cv, 48, 28, 40, 22, red[2])
    for a in range(0, 360, 6):
        cv.set(48 + round(41.5 * math.cos(math.radians(a))), 28 + round(23 * math.sin(math.radians(a))), gold[2])
    ell_fill(cv, 48, 28, 12, 7, red[4])
    return cv


def bedroll():
    cv = Canvas(60, 30)
    blue = ramp("#3a5a9a", 6, 0.5, 1.3)
    cloth = ramp("#d8c8a0", 5, 0.6, 1.15)
    cv.rect(2, 8, 56, 20, WOOD[1])
    cv.rect(16, 6, 42, 20, blue[3])
    for x in range(16, 58):
        cv.set(x, 6, blue[5])
        cv.set(x, 25, blue[1])
    cv.vline(16, 6, 25, blue[4])
    for x in range(20, 56, 8):
        cv.vline(x, 7, 24, blue[2])
    cv.ellipse(9, 14, 7, 6, cloth)
    cv.outline(OUTLINE)
    return cv


def furnace():
    cv = Canvas(52, 62)
    brick = ramp("#7a5a4e", 6, 0.5, 1.3)
    cv.rect(30, 2, 10, 22, brick[2])  # 굴뚝
    cv.vline(30, 2, 23, brick[4])
    cv.hline(29, 40, 2, brick[5])
    for y in range(14, 60):
        t = (y - 14) / 46
        half = int(16 + 8 * math.sqrt(min(1, t * 1.6)))
        for x in range(26 - half, 26 + half):
            row = (y // 5) % 2
            mortar = y % 5 == 4 or (x + row * 4) % 8 == 7
            i = 1 if mortar else (4 if x < 26 - half + 4 else 2 if x < 26 + half - 5 else 1)
            cv.set(x, y, brick[i])
    # 불구멍
    for y in range(38, 54):
        for x in range(16, 37):
            dx = (x - 26) / 10.5
            if y - 38 >= 6 * (dx * dx) and y < 54:
                cv.set(x, y, hexc("#1a0a06"))
    fire = [hexc("#ff7a20"), hexc("#ffb040"), hexc("#ffe080")]
    for y in range(45, 53):
        for x in range(18, 35):
            dx = abs(x - 26) / 8.5
            k = 1 - dx - (52 - y) / 12
            if k > 0:
                cv.set(x, y, fire[min(2, int(k * 3))])
    cv.outline(OUTLINE)
    return cv


def anvil():
    cv = Canvas(32, 28)
    cv.rect(10, 16, 12, 11, WOOD[2])
    cv.vline(10, 16, 26, WOOD[4])
    cv.hline(10, 21, 16, WOOD[4])
    iron = ramp("#4a4e58", 6, 0.5, 1.6)
    cv.rect(2, 4, 26, 5, iron[3])
    cv.rect(28, 5, 3, 2, iron[2])
    cv.hline(2, 27, 4, iron[5])
    cv.rect(10, 9, 12, 4, iron[2])
    cv.rect(7, 13, 18, 3, iron[1])
    cv.outline(OUTLINE)
    return cv


def workbench():
    cv = Canvas(46, 32)
    cv.rect(1, 6, 44, 6, WOOD[4])
    cv.hline(1, 44, 6, WOOD[5])
    cv.rect(1, 12, 44, 3, WOOD[1])
    for x in (3, 40):
        cv.rect(x, 15, 3, 16, WOOD[2])
    cv.hline(5, 40, 24, WOOD[1])
    # 연장
    cv.line(8, 5, 14, 1, IRON[4])
    cv.rect(20, 2, 6, 4, IRON[3])
    cv.vline(23, 0, 5, WOOD[3])
    cv.line(30, 5, 38, 3, IRON[2])
    cv.outline(OUTLINE)
    return cv


def lantern_table():
    cv = Canvas(28, 30)
    cv.rect(1, 14, 26, 5, WOOD[4])
    cv.hline(1, 26, 14, WOOD[5])
    for x in (3, 22):
        cv.rect(x, 19, 3, 10, WOOD[2])
    cv.rect(10, 4, 8, 10, IRON[1])
    cv.rect(11, 5, 6, 8, hexc("#ffcc60"))
    cv.rect(12, 6, 3, 5, hexc("#fff0b0"))
    cv.hline(9, 18, 3, IRON[3])
    cv.set(13, 1, IRON[3])
    cv.set(14, 2, IRON[3])
    cv.outline(OUTLINE)
    return cv


def pillar(color="#6e6670"):
    cv = Canvas(30, 70)
    st = ramp(color, 6, 0.5, 1.35)
    cv.rect(1, 0, 28, 8, st[3])
    cv.hline(1, 28, 0, st[5])
    cv.hline(1, 28, 7, st[0])
    cyl(cv, 4, 8, 22, 52, st)
    for x in (9, 15, 21):
        cv.vline(x, 9, 58, st[1])
    cv.rect(1, 60, 28, 9, st[2])
    cv.hline(1, 28, 60, st[4])
    cv.outline(OUTLINE)
    return cv


def brazier():
    cv = Canvas(24, 28)
    iron = ramp("#3e3a40", 6, 0.5, 1.7)
    for y in range(4, 12):
        half = 11 - (y - 4) // 2
        for x in range(12 - half, 12 + half):
            cv.set(x, y, iron[4] if x < 12 - half + 3 else iron[2])
    cv.hline(1, 22, 4, iron[5])
    for x in (7, 12, 17):
        cv.line(12, 12, x + (x - 12), 26, iron[2])
    ell_fill(cv, 12, 4, 9, 2, hexc("#ff8a30"))
    cv.outline(OUTLINE)
    return cv


def torch():
    cv = Canvas(12, 24)
    cv.rect(4, 8, 4, 14, WOOD[2])
    cv.vline(4, 8, 21, WOOD[4])
    cv.rect(2, 6, 8, 3, IRON[3])
    cv.rect(3, 4, 6, 3, hexc("#3a2014"))
    cv.rect(2, 16, 8, 2, IRON[2])
    cv.outline(OUTLINE)
    return cv


def campfire_log():
    cv = Canvas(44, 26)
    rnd = random.Random(2)
    for a in range(0, 360, 30):
        x, y = 22 + 18 * math.cos(math.radians(a)), 14 + 9 * math.sin(math.radians(a))
        cv.ellipse(x, y, 4, 3, STONE)
    cv.line(10, 16, 34, 10, WOOD[2])
    cv.line(10, 17, 34, 11, WOOD[1])
    cv.line(12, 9, 32, 17, WOOD[3])
    cv.line(12, 10, 32, 18, WOOD[1])
    ell_fill(cv, 22, 13, 6, 3, hexc("#5a1a08"))
    ell_fill(cv, 22, 13, 3, 1.5, hexc("#ff7a20"))
    cv.outline(OUTLINE)
    return cv


def vein(ore, rock="#5e5660"):
    cv = Canvas(32, 24)
    rk = ramp(rock, 6, 0.5, 1.4)
    cv.ellipse(16, 14, 14, 9, rk)
    cv.ellipse(10, 11, 7, 6, rk)
    cv.ellipse(22, 12, 7, 6, rk)
    o = ramp(ore, 5, 0.6, 1.5)
    rnd = random.Random(hash(ore) & 0xFFFF)
    for x, y in ((9, 10), (16, 13), (22, 9), (13, 16), (20, 16), (25, 13)):
        cv.set(x, y, o[4])
        cv.set(x + 1, y, o[2])
        cv.set(x, y + 1, o[1])
        cv.set(x - 1, y, o[3])
    cv.outline(OUTLINE)
    return cv


def gate_h():
    """세로 통로를 막는 창살 (정면에서 본 모습, 가로로 이어 붙인다)."""
    cv = Canvas(32, 46)
    iron = ramp("#5a5e68", 6, 0.45, 1.6)
    for x in (3, 11, 19, 27):
        cv.rect(x, 0, 3, 44, iron[2])
        cv.vline(x, 0, 43, iron[4])
        cv.rect(x, 43, 3, 3, iron[1])
        cv.set(x + 1, 44, iron[5])
    for y in (6, 24):
        cv.rect(0, y, 32, 3, iron[3])
        cv.hline(0, 31, y, iron[5])
    cv.outline(OUTLINE)
    return cv


def gate_v():
    """가로 통로를 막는 창살 (옆에서 본 모습, 세로로 이어 붙인다)."""
    cv = Canvas(14, 32)
    iron = ramp("#5a5e68", 6, 0.45, 1.6)
    cv.rect(4, 0, 6, 32, iron[2])
    cv.vline(4, 0, 31, iron[4])
    for y in (4, 14, 24):
        cv.rect(1, y, 12, 3, iron[3])
        cv.hline(1, 12, y, iron[5])
    cv.outline(OUTLINE)
    return cv


def shortcut_door():
    cv = Canvas(52, 60)
    st = ramp("#6a6070", 6, 0.5, 1.35)
    for y in range(60):
        for x in range(52):
            dx = (x - 26) / 26
            top = 20 - 20 * math.sqrt(max(0, 1 - dx * dx))
            if y >= top:
                cv.set(x, y, st[3] if x < 26 else st[2])
    for y in range(60):
        for x in range(8, 44):
            dx = (x - 26) / 18
            top = 24 - 18 * math.sqrt(max(0, 1 - dx * dx))
            if y >= top + 2:
                cv.set(x, y, hexc("#0c0a12"))
    for x in range(8, 44):  # 문지방
        cv.set(x, 58, st[4])
    cv.set(26, 3, hexc("#b08ae0"))
    cv.set(25, 4, hexc("#b08ae0"))
    cv.set(27, 4, hexc("#b08ae0"))
    cv.outline(OUTLINE)
    return cv


def rune_circle(color="#9a6ad0", size=52):
    cv = Canvas(size, size // 2 + 4)
    c = hexc(color)
    cx, cy = size / 2, size / 4 + 2
    for a in range(0, 360, 2):
        r = math.radians(a)
        cv.set(cx + (size / 2 - 1) * math.cos(r), cy + (size / 4) * math.sin(r), c)
        cv.set(cx + (size / 2 - 6) * math.cos(r), cy + (size / 4 - 3) * math.sin(r), shade(c, 0.7))
    for k in range(6):
        r = math.radians(k * 60)
        cv.line(cx + (size / 2 - 6) * math.cos(r), cy + (size / 4 - 3) * math.sin(r),
                cx + (size / 2 - 6) * math.cos(r + 2.1), cy + (size / 4 - 3) * math.sin(r + 2.1), shade(c, 0.8))
    return cv


# ---------------------------------------------------------------------------
# 몬스터 (오른쪽을 보는 그림)
# ---------------------------------------------------------------------------

def slime_frames():
    frames = []
    body = ramp("#5ab84a", 6, 0.45, 1.35)
    for f in range(4):
        sq = [0, 1, 0, -1][f]
        cv = Canvas(30, 26)
        rx, ry = 12 + sq, 9 - sq
        cy = 24 - ry
        cv.ellipse(15, cy, rx, ry, body)
        for x in range(int(15 - rx), int(15 + rx)):
            cv.set(x, 24, body[0])
        # 반투명 하이라이트와 눈
        cv.rect(8, int(cy - ry + 3), 3, 2, body[5])
        cv.set(9, int(cy - ry + 2), (240, 255, 240))
        for ex in (17, 22):
            cv.rect(ex, int(cy - 2), 2, 4, OUTLINE)
            cv.set(ex, int(cy - 2), (255, 255, 255))
        cv.outline(OUTLINE)
        frames.append(cv)
    return frames


def charger_frames():
    frames = []
    steel = ramp("#8a90a0", 6, 0.45, 1.45)
    red = ramp("#9a2a2a", 5, 0.55, 1.3)
    for f in range(4):
        cv = Canvas(36, 40)
        step = [0, 2, 0, -2][f]
        bob = 1 if f % 2 else 0
        # 다리
        cv.rect(12 + step, 30, 5, 9, steel[1])
        cv.rect(19 - step, 30, 5, 9, steel[2])
        cv.rect(11 + step, 37, 7, 2, OUTLINE)
        cv.rect(18 - step, 37, 7, 2, OUTLINE)
        # 몸통과 휘장
        cv.rect(10, 16 + bob, 16, 15, steel[3])
        cv.vline(10, 16 + bob, 30 + bob, steel[5])
        cv.rect(14, 18 + bob, 8, 13, red[2])
        cv.vline(14, 18 + bob, 30 + bob, red[4])
        cv.rect(15, 21 + bob, 6, 2, (220, 180, 80))
        # 투구와 깃털
        cv.ellipse(18, 10 + bob, 7, 7, steel)
        cv.rect(15, 9 + bob, 9, 2, OUTLINE)
        cv.set(22, 9 + bob, (255, 80, 60))
        for i in range(6):
            cv.set(14 - i // 2, 3 + bob - i // 3 + i, red[3])
            cv.set(15 - i // 2, 3 + bob - i // 3 + i, red[2])
        # 방패(앞)
        cv.ellipse(10, 23 + bob, 5, 7, ramp("#6a4a30", 5, 0.6, 1.3))
        cv.set(10, 23 + bob, steel[5])
        # 창
        cv.line(20, 22 + bob, 35, 18 + bob, ramp("#6a4a30", 5)[3])
        cv.rect(32, 16 + bob, 4, 3, steel[5])
        cv.outline(OUTLINE)
        frames.append(cv)
    return frames


def watcher_frames():
    frames = []
    flesh = ramp("#7a4aa8", 6, 0.45, 1.35)
    for f in range(4):
        cv = Canvas(34, 36)
        bob = [0, -1, -2, -1][f]
        # 촉수
        for k, bx in enumerate((10, 15, 20, 25)):
            for i in range(10):
                x = bx + round(math.sin((i + f * 2 + k) * 0.7) * 2)
                cv.set(x, 22 + bob + i, flesh[1] if i > 5 else flesh[2])
                cv.set(x + 1, 22 + bob + i, flesh[0])
        cv.ellipse(17, 15 + bob, 13, 12, flesh)
        # 눈
        cv.ellipse(19, 15 + bob, 8, 7.5, ramp("#f0e8e0", 4, 0.75, 1.05))
        cv.ellipse(21, 15 + bob, 4.5, 4.5, ramp("#d03a30", 4, 0.6, 1.3))
        cv.rect(22, 13 + bob, 2, 4, OUTLINE)
        cv.set(20, 13 + bob, (255, 255, 255))
        cv.outline(OUTLINE)
        frames.append(cv)
    return frames


def golem_frames():
    frames = []
    rock = ramp("#7a6a5a", 7, 0.45, 1.35)
    glow = [hexc("#ff7a20"), hexc("#ffc060")]
    for f in range(4):
        cv = Canvas(72, 70)
        bob = [0, 1, 2, 1][f]
        sw = [0, 1, 0, -1][f]
        # 다리
        cv.ellipse(26, 60, 8, 8, rock)
        cv.ellipse(46, 60, 8, 8, rock)
        # 몸통
        cv.ellipse(36, 38 + bob, 20, 18, rock)
        cv.ellipse(36, 18 + bob, 13, 11, rock)
        # 팔과 주먹
        cv.ellipse(12, 34 + bob + sw, 8, 12, rock)
        cv.ellipse(9, 50 + bob + sw, 9, 8, rock)
        cv.ellipse(60, 34 + bob - sw, 8, 12, rock)
        cv.ellipse(63, 50 + bob - sw, 9, 8, rock)
        # 빛나는 균열과 눈
        for x, y in ((30, 34), (31, 35), (32, 36), (40, 40), (41, 41), (36, 46), (37, 47), (26, 42)):
            cv.set(x, y + bob, glow[0])
        cv.rect(32, 16 + bob, 3, 3, glow[1])
        cv.rect(39, 16 + bob, 3, 3, glow[1])
        cv.ellipse(36, 38 + bob, 4, 4, [glow[0], glow[0], glow[1], glow[1]])
        cv.outline(OUTLINE)
        frames.append(cv)
    return frames


def knight_frames():
    frames = []
    steel = ramp("#4a5468", 7, 0.45, 1.55)
    blue = (90, 170, 255)
    for f in range(4):
        cv = Canvas(72, 76)
        bob = [0, 1, 2, 1][f]
        step = [0, 2, 0, -2][f]
        cv.rect(24 + step, 52, 10, 22, steel[2])
        cv.rect(40 - step, 52, 10, 22, steel[1])
        cv.rect(22 + step, 70, 14, 4, OUTLINE)
        cv.rect(38 - step, 70, 14, 4, OUTLINE)
        # 몸통 판금
        for y in range(24, 54):
            half = 18 - max(0, y - 40) // 2
            for x in range(36 - half, 36 + half):
                i = 5 if x < 36 - half + 3 else 1 if x > 36 + half - 4 else 3
                cv.set(x, y + bob, steel[i])
        cv.hline(20, 51, 24 + bob, steel[6])
        cv.rect(30, 30 + bob, 12, 3, (60, 120, 200))
        # 어깨
        cv.ellipse(18, 26 + bob, 9, 7, steel)
        cv.ellipse(54, 26 + bob, 9, 7, steel)
        # 투구와 뿔
        cv.ellipse(36, 15 + bob, 11, 11, steel)
        cv.rect(29, 14 + bob, 15, 3, OUTLINE)
        cv.rect(33, 14 + bob, 8, 2, blue)
        for i in range(8):
            cv.set(25 - i, 8 + bob - i // 2, steel[4])
            cv.set(47 + i, 8 + bob - i // 2, steel[3])
        # 대검 (오른쪽)
        cv.rect(58, 6 + bob, 4, 46, steel[5])
        cv.vline(58, 6 + bob, 51 + bob, (220, 230, 240))
        cv.rect(53, 50 + bob, 14, 3, (200, 160, 70))
        cv.rect(59, 53 + bob, 3, 8, (90, 60, 40))
        # 방패 (왼쪽)
        cv.ellipse(12, 44 + bob, 10, 14, ramp("#2e3a5a", 5, 0.6, 1.4))
        cv.rect(11, 36 + bob, 3, 16, blue)
        cv.outline(OUTLINE)
        frames.append(cv)
    return frames


def guardian_frames():
    frames = []
    robe = ramp("#5a3a8a", 7, 0.45, 1.4)
    gold = GOLD
    for f in range(4):
        cv = Canvas(72, 80)
        bob = [0, -1, -2, -1][f]
        # 후광
        for a in range(0, 360, 4):
            r = math.radians(a + f * 8)
            cv.set(36 + 19 * math.cos(r), 16 + bob + 8 * math.sin(r), gold[3] if a % 24 else gold[4])
        # 로브 (아래로 퍼짐, 떠 있음)
        for y in range(24, 74):
            half = 10 + (y - 24) * 16 // 50
            wave = round(math.sin(y * 0.4 + f) * 1.5) if y > 62 else 0
            for x in range(36 - half + wave, 36 + half + wave):
                i = 5 if x < 36 - half + 4 else 1 if x > 36 + half - 5 else 3 if (x // 5) % 2 else 2
                cv.set(x, y + bob, robe[i])
        cv.rect(33, 30 + bob, 6, 30, gold[2])
        cv.vline(33, 30 + bob, 59 + bob, gold[4])
        # 소매와 손 위의 구슬
        cv.ellipse(16, 40 + bob, 7, 10, robe)
        cv.ellipse(56, 40 + bob, 7, 10, robe)
        cv.ellipse(12, 52 + bob, 4, 4, ramp("#c0a0ff", 4, 0.7, 1.3))
        cv.ellipse(60, 52 + bob, 4, 4, ramp("#c0a0ff", 4, 0.7, 1.3))
        # 가면
        cv.ellipse(36, 18 + bob, 9, 10, gold)
        cv.rect(31, 17 + bob, 3, 2, OUTLINE)
        cv.rect(38, 17 + bob, 3, 2, OUTLINE)
        cv.set(32, 17 + bob, (200, 140, 255))
        cv.set(39, 17 + bob, (200, 140, 255))
        cv.outline(OUTLINE)
        frames.append(cv)
    return frames


def main():
    MANIFEST.clear()
    for name, fn in (("barrel", barrel), ("crate", crate), ("pot", pot), ("bones", bones), ("skull", skull),
                     ("rubble", rubble), ("minecart", minecart), ("weapon_rack", weapon_rack), ("statue", statue),
                     ("candles", candles), ("rug", rug), ("bedroll", bedroll), ("furnace", furnace), ("anvil", anvil),
                     ("workbench", workbench), ("lantern_table", lantern_table), ("pillar", pillar), ("brazier", brazier),
                     ("torch", torch), ("campfire_log", campfire_log), ("gate_h", gate_h), ("gate_v", gate_v),
                     ("shortcut_door", shortcut_door)):
        save(name, [fn()])
    save("cobweb", [cobweb()], origin=(0, 20))
    save("cobweb_r", [cobweb(True)], origin=(20, 20))
    save("banner", [banner()], origin=(9, 34))
    save("rune_circle", [rune_circle()], origin=(26, 15))
    save("boss_seal", [rune_circle("#6a4a90", 200)], origin=(100, 52))
    save("vein_mine", [vein("#e8b040")], origin=(16, 4))
    save("vein_fortress", [vein("#60b8ff", "#4e5664")], origin=(16, 4))
    save("vein_temple", [vein("#d070ff", "#544a64")], origin=(16, 4))
    save("vein", [vein("#e8b040")], origin=(16, 4))
    save("slime", slime_frames(), origin=(15, 1),
         anims={"idle": ([0, 1, 2, 3], 5), "move": ([0, 1, 2, 3], 9)})
    save("charger", charger_frames(), origin=(18, 1),
         anims={"idle": ([0, 2], 2), "move": ([0, 1, 2, 3], 8)})
    save("watcher", watcher_frames(), origin=(17, 1),
         anims={"idle": ([0, 1, 2, 3], 6)})
    save("boss_mine", scale=1.25, frames=golem_frames(), origin=(36, 2),
         anims={"idle": ([0, 1, 2, 3], 3), "move": ([0, 1, 2, 3], 6)})
    save("boss_fortress", scale=1.25, frames=knight_frames(), origin=(36, 2),
         anims={"idle": ([0, 1, 2, 3], 3), "move": ([0, 1, 2, 3], 6)})
    save("boss_temple", scale=1.25, frames=guardian_frames(), origin=(36, 2),
         anims={"idle": ([0, 1, 2, 3], 4)})
    with open(os.path.join(ROOT, "assets", "data", "sprites_generated.json"), "w", encoding="utf-8") as f:
        json.dump(MANIFEST, f, ensure_ascii=False, indent=1)
    print("sprites:", len(MANIFEST))


if __name__ == "__main__":
    main()
