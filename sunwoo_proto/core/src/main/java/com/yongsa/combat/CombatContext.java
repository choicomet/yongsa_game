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

    /** 피할 수 없는 공격 (메테오·해일 등): 막기·회피 무적도 무시한다. */
    boolean hitHeroTrue(int damage, float fromX, float fromY);

    void spawnProjectile(Projectile p);

    void addHazard(Hazard h);

    /** 실명: seconds 동안 맵 전체가 어두워진다. */
    void blindHero(float seconds);

    /** owner 근처에 잡몹 id를 count마리 부른다. 살아 있는 소환수가 max를 넘지 않게. */
    void summon(Monster owner, String monsterId, int count, int max);

    /** 몬스터가 싸우는 공간 (보스방 또는 최종 전장). */
    Rectangle arenaOf(Monster m);

    void effect(Effect e);

    void shake(float strength, float time);

    void sound(String id);

    /**
     * 마왕 공간 절단: 지금 남은 전장에서 잘라낼 사각형을 정한다 (아직 자르지 않음). 더 자를 수 없으면 null.
     */
    default Rectangle planRend(Boss b) { return null; }

    /** 정해 둔 사각형을 실제로 잘라낸다: 공허가 되어 지나갈 수 없다. */
    default void applyRend(Rectangle r) { }
}
