"""스프라이트 시트에서 투명 영역으로 분리된 그림 조각을 찾는다 (CraftPix 에셋 정리용)."""
from PIL import Image


def components(img, alpha=10, gap=1, min_px=12):
    """연결된 불투명 픽셀 덩어리의 경계 상자 목록 (위→아래, 왼→오른 순). gap 픽셀 이내 덩어리는 합친다."""
    w, h = img.size
    a = img.getchannel("A").load()
    seen = [[False] * w for _ in range(h)]
    boxes = []
    for y in range(h):
        for x in range(w):
            if seen[y][x] or a[x, y] <= alpha:
                continue
            stack = [(x, y)]
            seen[y][x] = True
            x0 = x1 = x
            y0 = y1 = y
            n = 0
            while stack:
                cx, cy = stack.pop()
                n += 1
                x0, x1, y0, y1 = min(x0, cx), max(x1, cx), min(y0, cy), max(y1, cy)
                for dy in range(-gap, gap + 1):
                    for dx in range(-gap, gap + 1):
                        nx, ny = cx + dx, cy + dy
                        if 0 <= nx < w and 0 <= ny < h and not seen[ny][nx] and a[nx, ny] > alpha:
                            seen[ny][nx] = True
                            stack.append((nx, ny))
            if n >= min_px:
                boxes.append((x0, y0, x1 + 1, y1 + 1))
    boxes.sort(key=lambda b: (b[1] // 16, b[0]))
    return boxes
