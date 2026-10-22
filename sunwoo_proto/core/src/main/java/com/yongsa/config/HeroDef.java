package com.yongsa.config;

/** 용사 기본 능력치 (명세서 5.4). */
public class HeroDef {
    public int maxHp = 100;
    /** px/s. 타일 4칸 = 128. */
    public float speed = 128;
    public float invincibleTime = 0.6f;
    /** 맞았을 때 밀려나는 거리(px). 타일 0.5칸 = 16. */
    public float knockback = 16;
    /** 캠프에 있을 때 초당 회복량 (FR-맵-09). */
    public float campRegenPerSecond = 8;
    public float attackCooldown = 0.4f;
    public float attackRange = 52;
    public float attackArcDegrees = 120;
    /** 피해량 무작위 폭: 0.1이면 90~110% (FR-전투-09). */
    public float damageVariance = 0.1f;
}
