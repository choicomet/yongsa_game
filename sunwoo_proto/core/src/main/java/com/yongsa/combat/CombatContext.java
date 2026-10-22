package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.world.CollisionWorld;

import java.util.Random;

/** 몬스터가 세상과 주고받는 것. GameSession이 구현한다. */
public interface CombatContext {
    HeroUnit heroUnit();

    boolean heroAlive();

    CollisionWorld collision();

    Random random();

    /** 앞에서 오는 공격: 막기로 막을 수 있다. 실제로 피해가 들어갔으면 true. */
    boolean hitHero(int damage, float fromX, float fromY);

    /** 보스의 큰 공격(돌진 등): 막으면 게이지가 많이 닳는다. */
    boolean hitHeroHeavy(int damage, float fromX, float fromY);

    /** 바닥·하늘에서 오는 공격: 막을 수 없다. */
    boolean hitHeroArea(int damage, float fromX, float fromY);

    void spawnProjectile(Projectile p);

    void addHazard(Hazard h);

    /** owner 근처에 잡몹 id를 count마리 부른다. 살아 있는 소환수가 max를 넘지 않게. */
    void summon(Monster owner, String monsterId, int count, int max);

    /** 몬스터가 싸우는 공간 (보스방 또는 최종 전장). */
    Rectangle arenaOf(Monster m);

    void effect(Effect e);

    void shake(float strength, float time);

    void sound(String id);
}
