package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;

/** 판정 도형 검사. 대상 몸 사각형의 가운데와 크기를 기준으로 넉넉하게 본다. */
public final class HitTest {
    private HitTest() {
    }

    /** 부채꼴: 거리 ≤ 사거리 + 몸 반지름, 바라보는 방향과 각도 차 ≤ 반각. */
    public static boolean arc(float ox, float oy, float dirX, float dirY, float range, float arcDeg, Rectangle t) {
        float tx = t.x + t.width / 2f, ty = t.y + t.height / 2f;
        float dx = tx - ox, dy = ty - oy;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float r = radius(t);
        if (dist > range + r) return false;
        if (dist <= r + 6) return true;
        float cos = (dx * dirX + dy * dirY) / dist;
        // 몸 크기만큼 각도를 넉넉하게
        double extra = Math.atan2(r, dist);
        return Math.acos(Math.max(-1, Math.min(1, cos))) <= Math.toRadians(arcDeg / 2f) + extra;
    }

    public static boolean circle(float ox, float oy, float radius, Rectangle t) {
        float tx = t.x + t.width / 2f, ty = t.y + t.height / 2f;
        float dx = tx - ox, dy = ty - oy;
        float r = radius + radius(t);
        return dx * dx + dy * dy <= r * r;
    }

    /** 선분(두께 width)과 사각형. 돌진 베기에 쓴다. */
    public static boolean segment(float x1, float y1, float x2, float y2, float width, Rectangle t) {
        float tx = t.x + t.width / 2f, ty = t.y + t.height / 2f;
        float vx = x2 - x1, vy = y2 - y1;
        float len2 = vx * vx + vy * vy;
        float k = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((tx - x1) * vx + (ty - y1) * vy) / len2));
        float px = x1 + vx * k, py = y1 + vy * k;
        float dx = tx - px, dy = ty - py;
        float r = width / 2f + radius(t);
        return dx * dx + dy * dy <= r * r;
    }

    static float radius(Rectangle t) {
        return Math.max(t.width, t.height) / 2f;
    }
}
