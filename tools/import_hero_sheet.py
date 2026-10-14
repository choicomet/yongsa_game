#!/usr/bin/env python3
"""
용사 스프라이트 시트(art_source/hero_sheet.png)를 게임용 시트로 변환한다.

원본 배치: 열 = 방향(앞, 왼쪽, 오른쪽, 뒤), 행 1~3 = 걷기 프레임, 4행 = 아이템(검, 망치, 곡괭이, 랜턴).
원본은 약 9px 마다 한 '픽셀'인 확대 그림이므로, 격자 칸마다 가장 많이 쓰인 색을 골라 1:1 픽셀 아트로 되돌린다.

출력:
  assets/sprites/hero/hero.png      한 칸 FW x FH, 행 = 방향(아래, 왼쪽, 오른쪽, 위), 열 = 프레임 0~2
  assets/sprites/items/<이름>.png   sword, hammer, pickaxe (랜턴 칸은 쓰지 않는다)
사용법: python3 tools/import_hero_sheet.py [원본 경로]
"""
import os
import sys
from collections import Counter

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "art_source", "hero_sheet.png")
GRID = 140  # 원본 한 변의 그림 픽셀 수 (1254 / 140 ≈ 8.96)


def bands(occ):
    out, start = [], None
    for i, o in enumerate(occ + [False]):
        if o and start is None:
            start = i
        if not o and start is not None:
            out.append((start, i))
            start = None
    return out


def to_pixels(img):
    """원본을 GRID x GRID 그림 픽셀로 샘플링한다(칸 중앙부의 최빈색)."""
    w, h = img.size
    bw, bh = w / GRID, h / GRID
    px = img.load()
    out = Image.new("RGBA", (GRID, GRID), (0, 0, 0, 0))
    op = out.load()
    for gy in range(GRID):
        for gx in range(GRID):
            x0, y0 = gx * bw, gy * bh
            votes = Counter()
            for sy in range(int(y0 + bh * 0.25), int(y0 + bh * 0.75) + 1):
                for sx in range(int(x0 + bw * 0.25), int(x0 + bw * 0.75) + 1):
                    if 0 <= sx < w and 0 <= sy < h:
                        r, g, b, a = px[sx, sy]
                        if a < 128:
                            votes[None] += 1
                        else:
                            votes[(r // 8 * 8, g // 8 * 8, b // 8 * 8)] += 1
            c, _ = votes.most_common(1)[0]
            if c is not None:
                op[gx, gy] = (c[0], c[1], c[2], 255)
    return out


def main():
    src = Image.open(SRC).convert("RGBA")
    art = to_pixels(src)
    a = art.getchannel("A").load()
    rows = bands([any(a[x, y] for x in range(GRID)) for y in range(GRID)])
    cells = []
    for (y0, y1) in rows:
        cols = bands([any(a[x, y] for y in range(y0, y1)) for x in range(GRID)])
        cells.append([(x0, y0, x1, y1) for (x0, x1) in cols])
    if len(cells) != 4 or any(len(r) != 4 for r in cells):
        raise SystemExit(f"예상한 4x4 배치가 아닙니다: {[len(r) for r in cells]}")

    # 각 칸을 실제 그림 영역으로 다시 자른다
    def crop(box):
        im = art.crop(box)
        bb = im.getbbox()
        return im.crop(bb) if bb else im

    chars = [[crop(cells[r][c]) for c in range(4)] for r in range(3)]
    fw = max(im.width for row in chars for im in row) + 2
    fh = max(im.height for row in chars for im in row) + 1
    sheet = Image.new("RGBA", (fw * 3, fh * 4), (0, 0, 0, 0))
    # 원본 열(앞, 왼쪽, 오른쪽, 뒤) → 출력 행(아래, 왼쪽, 오른쪽, 위)
    for direction in range(4):
        for frame in range(3):
            im = chars[frame][direction]
            x = frame * fw + (fw - im.width) // 2
            y = direction * fh + (fh - im.height)  # 발을 칸 바닥에 맞춘다
            sheet.paste(im, (x, y), im)
    os.makedirs(os.path.join(ROOT, "assets", "sprites", "hero"), exist_ok=True)
    sheet.save(os.path.join(ROOT, "assets", "sprites", "hero", "hero.png"))

    os.makedirs(os.path.join(ROOT, "assets", "sprites", "items"), exist_ok=True)
    for name, box in zip(("sword", "hammer", "pickaxe"), cells[3]):
        crop(box).save(os.path.join(ROOT, "assets", "sprites", "items", name + ".png"))
    print(f"hero.png 프레임 {fw}x{fh}, 아이템 4개")
    for name, box in zip(("sword", "hammer", "pickaxe"), cells[3]):
        print(" ", name, crop(box).size)


if __name__ == "__main__":
    main()
