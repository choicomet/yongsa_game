"""
픽셀 아트 그리기 도구. 도형마다 왼쪽 위에서 빛이 오는 음영(밝은 면·어두운 면, 경계는 디더링)을 넣고
마지막에 색에 맞춘 외곽선을 두른다. gen_assets.py가 이 도구로 모든 임시 그림을 그린다.
"""
import math
from PIL import Image, ImageDraw

Color = tuple


def rgb(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def shade(c, k):
    return tuple(max(0, min(255, int(v + k))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.img = Image.new("RGBA", (w, h), (0, 0, 0, 0))

    # ---------------------------------------------------------------- 기본 도형 (음영 포함)
    def _mask(self, draw_fn):
        m = Image.new("L", (self.w, self.h), 0)
        draw_fn(ImageDraw.Draw(m))
        return m

    def fill_mask(self, mask, color, light=0.6, hi=34, lo=-38, dither=True, flat=False):
        """mask 영역을 color로 칠하고 왼쪽 위 빛 음영을 넣는다."""
        bbox = mask.getbbox()
        if not bbox:
            return
        x0, y0, x1, y1 = bbox
        w, h = max(1, x1 - x0 - 1), max(1, y1 - y0 - 1)
        mp = mask.load()
        px = self.img.load()
        for y in range(y0, y1):
            for x in range(x0, x1):
                if mp[x, y] < 128:
                    continue
                if flat:
                    px[x, y] = color
                    continue
                u, v = (x - x0) / w, (y - y0) / h
                l = (1 - u) * (1 - light) + (1 - v) * light
                if l > 0.74:
                    c = shade(color, hi)
                elif l > 0.715 and dither and (x + y) % 2 == 0:
                    c = shade(color, hi)
                elif l < 0.28:
                    c = shade(color, lo)
                elif l < 0.305 and dither and (x + y) % 2 == 0:
                    c = shade(color, lo)
                else:
                    c = color
                px[x, y] = c

    def ellipse(self, box, color, **kw):
        self.fill_mask(self._mask(lambda d: d.ellipse(box, fill=255)), color, **kw)

    def rect(self, box, color, **kw):
        self.fill_mask(self._mask(lambda d: d.rectangle(box, fill=255)), color, **kw)

    def rrect(self, box, r, color, **kw):
        self.fill_mask(self._mask(lambda d: d.rounded_rectangle(box, r, fill=255)), color, **kw)

    def poly(self, pts, color, **kw):
        self.fill_mask(self._mask(lambda d: d.polygon(pts, fill=255)), color, **kw)

    def line(self, pts, color, width=1):
        ImageDraw.Draw(self.img).line(pts, fill=color, width=width)

    def px(self, x, y, color):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.img.putpixel((int(x), int(y)), color)

    def flat_rect(self, box, color):
        ImageDraw.Draw(self.img).rectangle(box, fill=color)

    def flat_ellipse(self, box, color):
        ImageDraw.Draw(self.img).ellipse(box, fill=color)

    def flat_poly(self, pts, color):
        ImageDraw.Draw(self.img).polygon(pts, fill=color)

    def glow(self, cx, cy, r, color, strength=0.6):
        """부드러운 빛 번짐 (알파 그라데이션)."""
        px = self.img.load()
        for y in range(int(cy - r), int(cy + r) + 1):
            for x in range(int(cx - r), int(cx + r) + 1):
                if not (0 <= x < self.w and 0 <= y < self.h):
                    continue
                d = math.hypot(x - cx, y - cy) / r
                if d >= 1:
                    continue
                a = int(255 * strength * (1 - d) ** 2)
                old = px[x, y]
                if old[3] == 0:
                    px[x, y] = color[:3] + (a,)
                else:
                    t = a / 255
                    px[x, y] = mix(old, color, t * 0.6)

    # ---------------------------------------------------------------- 마무리
    def outline(self, dark=(16, 12, 22, 255), inner=True):
        """바깥 외곽선. inner=True면 맞닿은 색을 어둡게 한 색으로 (덜 딱딱해 보임)."""
        src = self.img.load()
        out = self.img.copy()
        dst = out.load()
        for y in range(self.h):
            for x in range(self.w):
                if src[x, y][3] != 0:
                    continue
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < self.w and 0 <= ny < self.h and src[nx, ny][3] > 200:
                        dst[x, y] = mix(src[nx, ny], dark, 0.82) if inner else dark
                        break
        self.img = out
        return self

    def flip(self):
        c = Canvas(self.w, self.h)
        c.img = self.img.transpose(Image.FLIP_LEFT_RIGHT)
        return c

    def paste(self, other, x, y):
        self.img.alpha_composite(other.img if isinstance(other, Canvas) else other, (int(x), int(y)))


def strip(frames):
    w, h = frames[0].img.size if isinstance(frames[0], Canvas) else frames[0].size
    out = Image.new("RGBA", (w * len(frames), h), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        out.alpha_composite(f.img if isinstance(f, Canvas) else f, (i * w, 0))
    return out
