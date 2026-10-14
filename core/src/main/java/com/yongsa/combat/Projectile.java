package com.yongsa.combat;

/** Enemy bolts are simulated at the same fixed rate as the hero; swords can shatter them. */
public final class Projectile {
    public float x, y, dx, dy, speed, life = 5;
    public final float radius = 6;
    public final int damage;
    /** 탄의 색 (r, g, b). null 이면 기본 붉은 탄. */
    public float[] color;
    public Projectile(float x, float y, float angle, float speed, int damage) {
        this.x = x; this.y = y;
        this.dx = (float) Math.cos(angle); this.dy = (float) Math.sin(angle);
        this.speed = speed; this.damage = damage;
    }
}
