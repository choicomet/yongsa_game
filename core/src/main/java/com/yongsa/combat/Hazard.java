package com.yongsa.combat;

/**
 * 바닥에 생기는 보스 공격: 예고가 보이고 delay 초 뒤 터진다.
 * CIRCLE(낙석·낙뢰·촉수)과 LINE(창)은 한 번 터지고 사라지고,
 * POOL(피웅덩이)은 점점 넓어지며 life 초 동안 남아 밟으면 체력이 닳고 느려진다.
 */
public class Hazard {
    public enum Kind { CIRCLE, LINE, POOL }

    public final Kind kind;
    /** 그림 구분: rock, bolt, tentacle, spear, blood. */
    public final String style;
    public float x, y;
    /** LINE 의 끝점. */
    public float x2, y2;
    /** CIRCLE·LINE 의 범위 (LINE 은 두께의 절반). POOL 은 지금 반지름. */
    public float radius;
    public float startRadius, maxRadius, grow;
    /** 터지기까지 남은 시간, 예고 전체 시간. */
    public float delay, warning;
    /** POOL 이 남아 있는 시간. */
    public float life;
    public int damage;
    public float multiplier;
    public float age;
    public boolean triggered, dead;

    public Hazard(Kind kind, String style) {
        this.kind = kind;
        this.style = style;
    }

    /** 예고가 차오르는 정도 0 → 1. */
    public float progress() {
        return warning <= 0 ? 1f : 1f - Math.max(0f, delay) / warning;
    }

    /** 점 (px, py) 가 범위 안인가. LINE 은 선분까지의 거리로 본다. */
    public boolean covers(float px, float py, float extra) {
        if (kind != Kind.LINE) {
            float dx = px - x, dy = py - y, r = radius + extra;
            return dx * dx + dy * dy <= r * r;
        }
        float vx = x2 - x, vy = y2 - y;
        float len2 = vx * vx + vy * vy;
        float t = len2 <= 0 ? 0 : Math.max(0f, Math.min(1f, ((px - x) * vx + (py - y) * vy) / len2));
        float cx = x + vx * t, cy = y + vy * t;
        float dx = px - cx, dy = py - cy, r = radius + extra;
        return dx * dx + dy * dy <= r * r;
    }
}
