"""
간단한 픽셀 아트 도구: 색 단계(ramp), 노이즈, 음영, 외곽선.
gen_art.py 가 타일·소품·몬스터 그림을 만들 때 쓴다.
"""
import math
import random

from PIL import Image


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, k):
    """k>1 밝게, k<1 어둡게. 어두울수록 약간 푸른 쪽, 밝을수록 노란 쪽으로 (색 온도 이동)."""
    r, g, b = c
    if k < 1:
        t = 1 - k
        return (int(r * k), int(g * k), int(min(255, b * k + 18 * t)))
    t = k - 1
    return (min(255, int(r * k + 10 * t)), min(255, int(g * k + 6 * t)), min(255, int(b * k)))


def ramp(base, n=5, lo=0.55, hi=1.35):
    """어두운 것부터 밝은 것까지 n 단계."""
    return [shade(hexc(base) if isinstance(base, str) else base, lo + (hi - lo) * i / (n - 1)) for i in range(n)]


class Canvas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[None] * w for _ in range(h)]

    def set(self, x, y, c, a=255):
        x, y = int(x), int(y)
        if 0 <= x < self.w and 0 <= y < self.h and c is not None:
            if a >= 255 or self.px[y][x] is None:
                self.px[y][x] = (c[0], c[1], c[2], a)
            else:
                o = self.px[y][x]
                t = a / 255
                self.px[y][x] = (int(o[0] + (c[0] - o[0]) * t), int(o[1] + (c[1] - o[1]) * t),
                                 int(o[2] + (c[2] - o[2]) * t), max(o[3], a))

    def get(self, x, y):
        if 0 <= x < self.w and 0 <= y < self.h:
            return self.px[y][x]
        return None

    def rect(self, x, y, w, h, c, a=255):
        for yy in range(int(y), int(y + h)):
            for xx in range(int(x), int(x + w)):
                self.set(xx, yy, c, a)

    def hline(self, x0, x1, y, c, a=255):
        for x in range(int(x0), int(x1) + 1):
            self.set(x, y, c, a)

    def vline(self, x, y0, y1, c, a=255):
        for y in range(int(y0), int(y1) + 1):
            self.set(x, y, c, a)

    def line(self, x0, y0, x1, y1, c, a=255):
        n = int(max(abs(x1 - x0), abs(y1 - y0))) + 1
        for i in range(n + 1):
            t = i / max(1, n)
            self.set(round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t), c, a)

    def ellipse(self, cx, cy, rx, ry, rampc, light=(-0.6, -0.8), rim=True, a=255):
        """빛 방향을 반영한 타원(구) 음영."""
        lx, ly = light
        for y in range(int(cy - ry - 1), int(cy + ry + 2)):
            for x in range(int(cx - rx - 1), int(cx + rx + 2)):
                dx, dy = (x + 0.5 - cx) / rx, (y + 0.5 - cy) / ry
                d = dx * dx + dy * dy
                if d > 1:
                    continue
                nz = math.sqrt(max(0, 1 - d))
                lit = max(0, -(dx * lx + dy * ly) * 0.8 + nz * 0.6)
                i = min(len(rampc) - 1, int(lit * (len(rampc) - 0.01)))
                if rim and d > 0.82 and dx * lx + dy * ly > 0.2:
                    i = max(0, i - 1)
                self.set(x, y, rampc[i], a)

    def outline(self, color, diag=False):
        """투명한 칸 중 그림과 맞닿은 칸에 외곽선."""
        out = []
        for y in range(self.h):
            for x in range(self.w):
                if self.px[y][x] is not None:
                    continue
                nb = [(1, 0), (-1, 0), (0, 1), (0, -1)]
                if diag:
                    nb += [(1, 1), (-1, -1), (1, -1), (-1, 1)]
                for dx, dy in nb:
                    p = self.get(x + dx, y + dy)
                    if p is not None and p[3] > 128:
                        out.append((x, y))
                        break
        for x, y in out:
            self.set(x, y, color)

    def image(self):
        img = Image.new("RGBA", (self.w, self.h), (0, 0, 0, 0))
        pix = img.load()
        for y in range(self.h):
            for x in range(self.w):
                p = self.px[y][x]
                if p is not None:
                    pix[x, y] = p
        return img


def noise_fill(cv, x, y, w, h, rampc, rnd, base_idx=2, spread=1, p=0.35):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            i = base_idx
            if rnd.random() < p:
                i += rnd.choice([-spread, spread])
            cv.set(xx, yy, rampc[max(0, min(len(rampc) - 1, i))])


def paste(atlas, img, x, y):
    atlas.alpha_composite(img, (x, y))
