package com.yongsa.combat;

/** 맞은 자리에서 떠올랐다 사라지는 글자 (FR-전투-09, FR-채집-06). */
public class FloatingText {
    public enum Kind { DAMAGE, HERO_DAMAGE, HEAL, ORE, INFO, IMMUNE, POISON, BURN }

    public static final float LIFE = 0.9f;
    public final String text;
    public final Kind kind;
    public final String icon;
    public float x, y, time;

    public FloatingText(String text, Kind kind, float x, float y) {
        this(text, kind, x, y, null);
    }

    public FloatingText(String text, Kind kind, float x, float y, String icon) {
        this.text = text;
        this.kind = kind;
        this.x = x;
        this.y = y;
        this.icon = icon;
    }

    public boolean update(float delta) {
        time += delta;
        y += 26 * delta;
        return time < LIFE;
    }
}
