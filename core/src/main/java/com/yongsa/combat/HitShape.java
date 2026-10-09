package com.yongsa.combat;

/**
 * 공격 판정 영역. 회전된 사각형(BOX) 또는 원(CIRCLE).
 * 경고 표시와 실제 판정이 같은 객체를 사용하므로 보이는 영역과 판정이 어긋나지 않는다.
 */
public class HitShape {
    public enum Type { BOX, CIRCLE }

    public Type type;
    // BOX: 시작점(ox, oy)에서 방향(dx, dy)으로 length 만큼, 폭 width
    public float ox, oy, dx, dy, length, width;
    // CIRCLE
    public float cx, cy, radius;

    public static HitShape box(float ox, float oy, float dx, float dy, float length, float width) {
        HitShape s = new HitShape();
        s.type = Type.BOX;
        s.set(ox, oy, dx, dy, length, width);
        return s;
    }

    public static HitShape circle(float cx, float cy, float r) {
        HitShape s = new HitShape();
        s.type = Type.CIRCLE;
        s.cx = cx;
        s.cy = cy;
        s.radius = r;
        return s;
    }

    public void set(float ox, float oy, float dx, float dy, float length, float width) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6f) {
            dx = 1;
            dy = 0;
            len = 1;
        }
        this.ox = ox;
        this.oy = oy;
        this.dx = dx / len;
        this.dy = dy / len;
        this.length = length;
        this.width = width;
    }

    /** 원(px, py, pr)과 겹치는지. */
    public boolean hitsCircle(float px, float py, float pr) {
        if (type == Type.CIRCLE) {
            float ddx = px - cx, ddy = py - cy;
            float rr = radius + pr;
            return ddx * ddx + ddy * ddy <= rr * rr;
        }
        float rx = px - ox, ry = py - oy;
        float along = rx * dx + ry * dy;
        float across = -rx * dy + ry * dx;
        float ca = Math.max(0, Math.min(length, along));
        float cc = Math.max(-width / 2f, Math.min(width / 2f, across));
        float ea = along - ca, ec = across - cc;
        return ea * ea + ec * ec <= pr * pr;
    }

    /** BOX 의 네 꼭짓점 (x0,y0,...,x3,y3). 그리기용. */
    public float[] corners() {
        float px = -dy * width / 2f, py = dx * width / 2f;
        float ex = ox + dx * length, ey = oy + dy * length;
        return new float[]{ox + px, oy + py, ex + px, ey + py, ex - px, ey - py, ox - px, oy - py};
    }
}
