"""
직접 그린 그림: 문·바닥 문양, 캠프의 모닥불 자리와 돌기둥, 쌍단검, 휴대용 대장간, 회복약, CraftPix 그림이 없는 몬스터(슬라임·돌진병·포탑).
보스 3종은 직접 만든 시트를 쓴다 (tools/import_boss_sheets.py).
  assets/sprites/gen/*.png + assets/data/sprites_generated.json
사용법: python3 tools/gen_sprites.py
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
        for an, spec in anims.items():
            order, afps = spec[0], spec[1]
            entry["anims"][an] = {"file": file, "frameWidth": w, "frameHeight": h, "frames": len(frames),
                                  "frameOrder": order, "fps": afps}
            if len(spec) > 2 and not spec[2]:
                entry["anims"][an]["loop"] = False  # 예고·타격 자세는 한 번만 재생
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


def turret_frames():
    """마력 포탑: 돌 받침 위에 떠 있는 푸른 구슬."""
    stone = ramp("#6c7488", 6, 0.45, 1.4)
    orb = [(29, 78, 216), (59, 130, 246), (125, 211, 252), (224, 242, 254)]
    frames = []
    for f in range(4):
        cv = Canvas(30, 40)
        bob = [0, -1, -2, -1][f]
        # 받침
        cv.rect(5, 30, 20, 7, stone[1])
        cv.rect(7, 26, 16, 5, stone[3])
        cv.rect(9, 22, 12, 5, stone[2])
        cv.hline(7, 22, 26, stone[5])
        cv.hline(5, 24, 30, stone[4])
        cv.rect(13, 28, 4, 6, orb[1])
        # 구슬을 받치는 쇠 갈퀴
        for x in (8, 21):
            cv.vline(x, 14, 22, stone[0])
        cv.set(9, 13, stone[0])
        cv.set(20, 13, stone[0])
        # 구슬
        cv.ellipse(15, 11 + bob, 7, 7, orb)
        cv.set(12, 8 + bob, orb[3])
        cv.set(13, 8 + bob, orb[3])
        cv.outline(OUTLINE)
        frames.append(cv)
    return frames


def daggers():
    """쌍단검 한 자루 (위를 향한다). 철검과 헷갈리지 않게 짧고 휜 보랏빛 날로 그린다. 게임에서 두 자루로 그린다."""
    cv = Canvas(9, 15)
    blade = [(88, 60, 150), (150, 110, 220), (205, 175, 250), (240, 228, 255)]
    # 휜 날: 위로 갈수록 오른쪽으로 굽는다
    for y, (x0, x1) in enumerate(((6, 6), (5, 7), (5, 7), (4, 6), (4, 6), (3, 5), (3, 5), (3, 5), (3, 5))):
        for x in range(x0, x1 + 1):
            cv.set(x, y, blade[2] if x == x0 else blade[1] if x < x1 else blade[0])
    cv.set(6, 0, blade[3])
    cv.set(5, 2, blade[3])
    cv.set(4, 4, blade[3])
    cv.hline(1, 7, 9, (80, 200, 190))   # 청록 코등이
    cv.vline(4, 10, 12, (60, 44, 70))
    cv.set(4, 13, (80, 200, 190))
    cv.outline(OUTLINE)
    return cv


def daggers_icon():
    """쌍단검 아이콘: 두 자루가 엇갈려 있다 (대장간·화면 아래 칸에서 철검과 구분)."""
    one = daggers().image()
    out = Canvas(20, 20)
    img = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    a = one.rotate(-35, expand=True, resample=Image.NEAREST)
    b = one.transpose(Image.FLIP_LEFT_RIGHT).rotate(35, expand=True, resample=Image.NEAREST)
    img.alpha_composite(a, (20 - a.width, (20 - a.height) // 2))
    img.alpha_composite(b, (0, (20 - b.height) // 2))
    px = img.load()
    for y in range(20):
        for x in range(20):
            if px[x, y][3] > 0:
                out.set(x, y, px[x, y][:3])
    return out


def potion(size, liquid):
    """회복약: size 0 작은 병, 1 보통, 2 큰 병. 붉은 약이 차 있다."""
    cw, ch = ((10, 13), (12, 15), (16, 18))[size]
    cv = Canvas(cw + 2, ch + 2)
    glass = (200, 225, 235)
    red = ramp(liquid, 5, 0.55, 1.35)
    cx = (cw + 2) // 2
    r = cw // 2 - 1
    by = ch - r                      # 병 몸통의 중심
    cv.ellipse(cx, by, r, r, [red[0], red[1], red[2], red[3]])
    # 위쪽 빈 부분 (유리)
    for x in range(cx - r + 1, cx + r):
        cv.set(x, by - r + 1, glass)
    # 목과 마개
    cv.rect(cx - 2, by - r - 3, 4, 4, glass)
    cv.rect(cx - 2, by - r - 5, 4, 2, (150, 104, 60))
    cv.set(cx - r + 2, by - 1, (255, 240, 240))
    cv.set(cx - r + 2, by, (255, 240, 240))
    if size == 2:                    # 큰 병에는 금테와 하트
        cv.hline(cx - r + 1, cx + r - 1, by + 1, (232, 190, 80))
        cv.set(cx - 1, by - 2, (255, 230, 230))
        cv.set(cx + 1, by - 2, (255, 230, 230))
        cv.hline(cx - 1, cx + 1, by - 1, (255, 230, 230))
    cv.outline(OUTLINE)
    return cv


def anvil():
    """주머니에서 꺼내는 휴대용 대장간: 나무 받침 위의 작은 모루와 망치."""
    cv = Canvas(30, 24)
    iron = ramp("#5c6270", 6, 0.4, 1.7)
    cv.rect(9, 15, 12, 7, WOOD[1])           # 받침
    cv.vline(9, 15, 21, WOOD[3])
    cv.hline(9, 20, 15, WOOD[4])
    cv.rect(7, 21, 16, 2, WOOD[0])
    cv.rect(5, 6, 20, 4, iron[3])            # 모루 윗면
    cv.hline(5, 24, 6, iron[5])
    cv.rect(1, 7, 5, 2, iron[2])             # 뿔
    cv.rect(11, 10, 8, 3, iron[2])
    cv.rect(8, 13, 14, 2, iron[1])
    cv.hline(8, 21, 13, iron[4])
    # 망치
    cv.rect(18, 1, 7, 4, iron[4])
    cv.hline(18, 24, 1, iron[5])
    cv.vline(21, 5, 6, WOOD[2])
    cv.set(12, 5, (255, 200, 90))            # 불티
    cv.set(15, 4, (255, 160, 60))
    cv.outline(OUTLINE)
    return cv


def campfire_ring():
    """캠프 가운데 모닥불 자리: 둥글게 놓은 돌과 장작 (불꽃은 게임에서 그린다)."""
    cv = Canvas(48, 26)
    stone = ramp("#5a5560", 6, 0.45, 1.4)
    for i in range(12):
        a = i / 12 * math.tau
        x, y = 24 + math.cos(a) * 19, 14 + math.sin(a) * 8
        cv.ellipse(x, y, 4.5, 3.5, stone)
    for i in range(22):                      # 재
        a = i * 2.4
        cv.set(24 + math.cos(a) * (i % 9), 14 + math.sin(a) * (i % 5), (40, 34, 36))
    for (x0, y0, x1, y1) in ((14, 16, 33, 10), (15, 10, 34, 16), (22, 8, 26, 18)):
        cv.line(x0, y0, x1, y1, WOOD[1])
        cv.line(x0, y0 - 1, x1, y1 - 1, WOOD[3])
    cv.rect(21, 11, 6, 4, (230, 120, 40))    # 달아오른 숯
    cv.rect(23, 12, 2, 2, (255, 220, 120))
    cv.outline(OUTLINE)
    return cv


def camp_pillar():
    """캠프 둘레의 돌기둥: 어두운 사각 기둥에 머릿돌과 받침돌."""
    cv = Canvas(26, 56)
    stone = ramp("#4e4a58", 7, 0.42, 1.45)
    cv.rect(2, 2, 22, 7, stone[4])           # 머릿돌
    cv.hline(2, 23, 2, stone[6])
    cv.hline(2, 23, 8, stone[1])
    cv.rect(5, 9, 16, 38, stone[3])          # 몸
    cv.rect(5, 9, 3, 38, stone[5])
    cv.rect(17, 9, 4, 38, stone[1])
    for y in (18, 29, 39):                   # 쌓은 돌의 이음매
        cv.hline(5, 20, y, stone[1])
        cv.hline(5, 20, y + 1, stone[4])
    cv.rect(10, 13, 6, 3, stone[2])          # 새겨진 무늬
    cv.rect(12, 22, 2, 5, stone[2])
    cv.rect(2, 47, 22, 7, stone[2])          # 받침돌
    cv.hline(2, 23, 47, stone[5])
    cv.rect(2, 52, 22, 2, stone[0])
    for (x, y) in ((7, 33), (15, 16), (9, 43), (18, 26)):   # 이 빠진 자리
        cv.set(x, y, stone[0])
        cv.set(x + 1, y, stone[1])
    cv.outline(OUTLINE)
    return cv


def main():
    MANIFEST.clear()
    # 예전에 만든 그림이 남아 섞이지 않도록 출력 폴더를 비우고 시작한다
    for f in os.listdir(OUT):
        if f.endswith(".png"):
            os.remove(os.path.join(OUT, f))
    # 문·바닥 문양
    for name, fn in (("gate_h", gate_h), ("gate_v", gate_v)):
        save(name, [fn()])
    save("rune_circle", [rune_circle()], origin=(26, 15))
    save("boss_seal", [rune_circle("#6a4a90", 200)], origin=(100, 52))
    # 캠프: 모닥불 자리와 돌기둥
    save("campfire_log", [campfire_ring()], origin=(24, 6), scale=1.5)
    save("camp_pillar", [camp_pillar()], origin=(13, 4), scale=1.5)
    # 쌍단검 (용사 시트에 없는 무기)과 아이콘
    save("daggers", [daggers()], origin=(4.5, 2))
    MANIFEST["daggers"]["pointAngle"] = 90
    save("daggers_icon", [daggers_icon()])
    # 휴대용 대장간, 상자에서 나오는 회복약 (작은 / 보통 / 큰)
    save("anvil", [anvil()], origin=(15, 2), scale=1.5)
    for i, name in enumerate(("potion_small", "potion_half", "potion_big")):
        save(name, [potion(i, "#d83a4a")])
    # CraftPix 그림이 없는 몬스터
    save("slime", slime_frames(), origin=(15, 1),
         anims={"idle": ([0, 1, 2, 3], 5), "move": ([0, 1, 2, 3], 9)})
    save("charger", charger_frames(), origin=(18, 1),
         anims={"idle": ([0, 2], 2), "move": ([0, 1, 2, 3], 8)})
    save("turret", turret_frames(), origin=(15, 3), scale=1.5,
         anims={"idle": ([0, 1, 2, 3], 6)})
    # 보스 3종 (gen_bosses.py)
    with open(os.path.join(ROOT, "assets", "data", "sprites_generated.json"), "w", encoding="utf-8") as f:
        json.dump(MANIFEST, f, ensure_ascii=False, indent=1)
    print("sprites:", len(MANIFEST))


if __name__ == "__main__":
    main()
