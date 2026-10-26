package com.yongsa.gfx;

import com.yongsa.combat.Facing;

/** Presentation curves, independent of rendering and combat timing. */
public final class HeroMotion {
    public static final float WIDTH = 64, HEIGHT = 72, FOOT_OFFSET = 4;
    private HeroMotion() {}

    /** 그림 줄: 0 앞, 1 뒤, 2 옆. 대각선 그림이 없어 대각선은 옆모습을 쓴다. */
    public static int row(Facing facing) {
        return facing == Facing.UP ? 1 : facing == Facing.DOWN ? 0 : 2;
    }

    /** 가만히 들고 있을 때 칼 각도. 대각선은 옆과 위/아래 사이로 기울인다. */
    public static float restAngle(Facing facing) {
        switch (facing) {
            case RIGHT: return 55;
            case LEFT: return 125;
            case UP: return 100;
            case UP_RIGHT: return 75;
            case UP_LEFT: return 105;
            case DOWN_RIGHT: return 40;
            case DOWN_LEFT: return 140;
            default: return 75;
        }
    }

    public static int walkFrame(float time) {
        return Math.floorMod((int) Math.floor(time * 12), 6);
    }

    public static int rollFrame(float progress) {
        return Math.min(5, (int) (clamp(progress) * 6));
    }

    public static float rollWeight(float progress) {
        return smooth(progress / .12f) * (1 - smooth((progress - .80f) / .20f));
    }

    public static float smooth(float value) {
        float p = clamp(value);
        return p * p * (3 - 2 * p);
    }

    public static float clamp(float value) {
        return Math.max(0, Math.min(1, value));
    }

    private static float angle(float from, float to, float p) {
        float delta = (to - from + 540) % 360 - 180;
        return from + delta * smooth(p);
    }

    /** Wind-up, strike, recovery. Both endpoints meet the ordinary held-sword pose. */
    public static float swordAngle(float rest, float aim, float progress, int kind) {
        float p = clamp(progress);
        float windup = kind == 1 ? 100 : aim + (kind == 2 ? 65 : 70);
        float end = kind == 1 ? aim - 20 : aim - 70;
        if (p < .18f) return angle(rest, windup, p / .18f);
        if (p < .65f) return angle(windup, end, (p - .18f) / .47f);
        return angle(end, rest, (p - .65f) / .35f);
    }
}
