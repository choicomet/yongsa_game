package com.yongsa.combat;

/** 몬스터가 쏜 투사체. 벽에 닿거나 용사를 맞히면 사라진다. */
public class Projectile {
    public float x, y;
    public final float vx, vy, radius;
    public final int damage;
    public final String sprite;
    public float life;
    public boolean dead;

    public Projectile(float x, float y, float vx, float vy, float radius, int damage, String sprite, float life) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.radius = radius;
        this.damage = damage;
        this.sprite = sprite;
        this.life = life;
    }
}
