package com.yongsa.combat;

/** 몬스터가 쏜 투사체. 벽에 닿거나 용사를 맞히면 사라진다. 막기(Ctrl)로 막을 수 있다. */
public class Projectile {

    /** 맞으면 거는 상태이상. */
    public enum Status { NONE, FREEZE, BURN }

    public float x, y;
    public final float vx, vy, radius;
    public final int damage;
    public final String sprite;
    public float life, age;
    public boolean dead;
    public Status status = Status.NONE;

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

    public Projectile with(Status s) {
        status = s;
        return this;
    }

    /** 날아가는 방향 (그릴 때 회전용, 도). */
    public float angleDeg() {
        return (float) Math.toDegrees(Math.atan2(vy, vx));
    }
}
