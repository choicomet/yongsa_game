package com.yongsa.combat;

/** 몬스터가 쏜 투사체. 벽에 닿거나 용사를 맞히면 사라진다. 막기(Ctrl)로 막을 수 있다. */
public class Projectile {

    /** 맞으면 거는 상태이상. */
    public enum Status { NONE, FREEZE, BURN }

    public float x, y;
    public float vx, vy;
    public final float radius;
    public final int damage;
    public final String sprite;
    public float life, age;
    public boolean dead;
    public Status status = Status.NONE;
    /** 보스가 쏜 탄: 막으면 막기가 한 번에 깨진다. */
    public boolean fromBoss;
    /** 유도: 초당 이만큼(라디안) 용사 쪽으로 꺾는다, homingTime초 동안. */
    public float homing, homingTime;
    /** 맞으면 이만큼 밀려난다 (태풍: 맵 끝까지). 0이면 보통. */
    public float knock;

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

    public Projectile boss() {
        fromBoss = true;
        return this;
    }

    public Projectile homing(float turnPerSecond, float seconds) {
        homing = turnPerSecond;
        homingTime = seconds;
        return this;
    }

    public Projectile knock(float distance) {
        knock = distance;
        return this;
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
