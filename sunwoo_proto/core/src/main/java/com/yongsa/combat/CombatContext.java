package com.yongsa.combat;

import com.yongsa.world.CollisionWorld;

import java.util.Random;

/** 몬스터가 세상과 주고받는 것. GameSession이 구현한다. */
public interface CombatContext {
    HeroUnit heroUnit();

    boolean heroAlive();

    CollisionWorld collision();

    Random random();

    /** 용사에게 피해. 무적이면 무시. */
    void hitHero(int damage, float fromX, float fromY);

    void spawnProjectile(Projectile p);

    void effect(Effect e);

    void shake(float strength, float time);

    void sound(String id);
}
