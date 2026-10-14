#!/usr/bin/env python3
"""
타이틀 로고(assets/ui/title_logo.png)를 만든다.

Galmuri 픽셀 폰트를 정수 배율로 키워 글자의 픽셀이 또렷하게 보이게 하고,
금속 느낌의 세로 색 띠 + 굵은 외곽선 + 아래로 떨어지는 그림자 + 윗면 반짝임을 입힌다.
'다한다' 는 더 크게, 붉은 계열로 강조한다. 글자 아래에는 검 모양의 가로 장식을 둔다.
사용법: python3 tools/make_title.py
(타이틀 배경 title_bg.png 는 게임 화면을 찍어 만든다: gradlew lwjgl3:run -Pyongsa.showcase=폴더)
"""
import os

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FONT = os.path.join(ROOT, "assets", "fonts", "Galmuri11.ttf")
OUT = os.path.join(ROOT, "assets", "ui", "title_logo.png")


def glyphs(text, scale):
    """글자를 원래 크기(12px, 계단 현상 제거 없이)로 그린 뒤 정수 배율로 키운 흑백 마스크."""
    font = ImageFont.truetype(FONT, 12)
    w = int(font.getlength(text)) + 4
    m = Image.new("L", (w, 18), 0)
    d = ImageDraw.Draw(m)
    d.fontmode = "1"
    d.text((2, 2), text, font=font, fill=255)
    m = m.crop(m.getbbox())
    return m.resize((m.width * scale, m.height * scale), Image.NEAREST)


def grow(mask, r):
    """마스크를 r 픽셀만큼 사방으로 넓힌다 (외곽선용)."""
    out = mask.copy()
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            if dx * dx + dy * dy <= r * r + 1:
                out = ImageChops.lighter(out, ImageChops.offset(mask, dx, dy))
    return out


def bands(size, colors):
    """위에서 아래로 끊어지는 색 띠 (픽셀 아트 느낌의 금속 음영)."""
    w, h = size
    img = Image.new("RGBA", size)
    d = ImageDraw.Draw(img)
    n = len(colors)
    for i, c in enumerate(colors):
        d.rectangle([0, h * i // n, w, h * (i + 1) // n], fill=c)
    return img


def word(text, scale, colors, outline=(26, 14, 12, 255), shadow=(8, 6, 10, 255)):
    m = glyphs(text, scale)
    pad = scale + 4
    canvas = Image.new("L", (m.width + pad * 2, m.height + pad * 2 + scale), 0)
    canvas.paste(m, (pad, pad))
    out = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    edge = grow(canvas, max(2, scale // 2))
    # 그림자 (아래로), 외곽선, 색 띠 순서로 쌓는다
    out.paste(Image.new("RGBA", canvas.size, shadow), (0, scale), ImageChops.offset(edge, 0, 0))
    out.paste(Image.new("RGBA", canvas.size, outline), (0, 0), edge)
    out.paste(bands(canvas.size, colors), (0, 0), canvas)
    # 윗면 반짝임: 글자 획의 맨 윗줄만 밝게
    top = ImageChops.subtract(canvas, ImageChops.offset(canvas, 0, max(1, scale // 3)))
    out.paste(Image.new("RGBA", canvas.size, (255, 250, 225, 255)), (0, 0), top)
    return out


def blade(width):
    """글자 아래의 검 모양 장식."""
    h = 14
    img = Image.new("RGBA", (width, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    mid = h // 2
    # 칼날 (오른쪽으로 뾰족)
    d.polygon([(36, mid - 3), (width - 16, mid - 3), (width - 2, mid), (width - 16, mid + 2), (36, mid + 2)], fill=(26, 14, 12, 255))
    d.polygon([(38, mid - 2), (width - 17, mid - 2), (width - 6, mid), (width - 17, mid + 1), (38, mid + 1)], fill=(214, 222, 236, 255))
    d.line([(38, mid), (width - 10, mid)], fill=(120, 134, 160, 255))
    # 코등이와 손잡이
    d.rectangle([28, 0, 36, h - 1], fill=(26, 14, 12, 255))
    d.rectangle([30, 2, 34, h - 3], fill=(232, 176, 64, 255))
    d.rectangle([4, mid - 3, 28, mid + 2], fill=(26, 14, 12, 255))
    d.rectangle([6, mid - 2, 27, mid + 1], fill=(124, 78, 44, 255))
    d.rectangle([0, mid - 4, 5, mid + 3], fill=(26, 14, 12, 255))
    d.rectangle([1, mid - 3, 4, mid + 2], fill=(232, 176, 64, 255))
    return img


def main():
    gold = [(255, 236, 170, 255), (250, 204, 96, 255), (232, 160, 52, 255), (190, 110, 38, 255)]
    red = [(255, 214, 170, 255), (248, 140, 84, 255), (220, 78, 52, 255), (158, 44, 44, 255)]
    a = word("용사가", 5, gold)
    b = word("다한다", 8, red)
    w = a.width + b.width - 6
    h = max(a.height, b.height)
    deco = blade(w - 30)
    out = Image.new("RGBA", (w, h + deco.height + 6), (0, 0, 0, 0))
    out.alpha_composite(a, (0, h - a.height - 6))
    out.alpha_composite(b, (a.width - 6, 0))
    out.alpha_composite(deco, (14, h + 4))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    out.save(OUT)
    print("logo:", out.size)


if __name__ == "__main__":
    main()
