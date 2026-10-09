package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;

import java.util.function.Predicate;

/** 타일과 단순 사각형 충돌. 축을 나눠 이동하며 한 번에 최대 4픽셀씩 나눠 검사한다. */
public final class Physics {
    private Physics() {}

    private static final Rectangle tmp = new Rectangle();

    /**
     * pos[0], pos[1]은 발 위치. w×h 몸통을 (dx, dy)만큼 옮긴다.
     * @return 이동 중 막혔으면 true
     */
    public static boolean move(float[] pos, float w, float h, float dx, float dy, Predicate<Rectangle> blocked) {
        boolean hit = false;
        float dist = Math.max(Math.abs(dx), Math.abs(dy));
        int steps = Math.max(1, (int) Math.ceil(dist / 4f));
        float sx = dx / steps, sy = dy / steps;
        for (int i = 0; i < steps; i++) {
            if (sx != 0) {
                tmp.set(pos[0] + sx - w / 2f, pos[1] - h / 2f, w, h);
                if (blocked.test(tmp)) hit = true;
                else pos[0] += sx;
            }
            if (sy != 0) {
                tmp.set(pos[0] - w / 2f, pos[1] + sy - h / 2f, w, h);
                if (blocked.test(tmp)) hit = true;
                else pos[1] += sy;
            }
        }
        return hit;
    }
}
