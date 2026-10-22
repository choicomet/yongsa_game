package com.yongsa.combat;

/** 잠깐 그렸다 사라지는 연출 (공격 범위, 충격파, 번개 등). 규칙에는 영향이 없다. */
public class Effect {
    public enum Type { SLASH, WHIRL, SHOCKWAVE, LIGHTNING, SLAM, HIT, DASH_TRAIL, BOUNCE, ORB_BURST, HEAL }

    public final Type type;
    public float x, y, x2, y2, radius, dirX, dirY, arc;
    public float time;
    public final float duration;

    public Effect(Type type, float x, float y, float duration) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.duration = duration;
    }

    public float progress() {
        return Math.min(1f, time / duration);
    }

    public boolean done() {
        return time >= duration;
    }
}
