package com.yongsa.combat;

/** 몬스터가 쏜 불덩이·마력탄. 벽에 닿거나 용사를 맞히면 사라진다. 구르는 동안에는 통과한다. */
public class Projectile {
    public float x, y, vx, vy;
    public float radius;
    public float life;
    public int damage;
    /** 그림 색 구분용: 쏜 몬스터의 그림 이름. */
    public String source;
    public boolean dead;
    public float age;
}
