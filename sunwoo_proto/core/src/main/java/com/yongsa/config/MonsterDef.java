package com.yongsa.config;

/** 잡몹·보스 공통. 보스 전용 값은 잡몹이면 비어 있다. */
public class MonsterDef {
    public String id;
    public String name;
    public String sprite;
    /** MELEE(돌진) 또는 RANGED(투사체). */
    public String kind = "MELEE";
    public int hp;
    public int attack;
    public float speed;
    public float detectRange;
    public float attackRange;
    /** 공격 예고 시간 (FR-전투-06). */
    public float windup;
    public float cooldown;
    public float lunge;
    public float projectileSpeed;
    public float width;
    public float height;

    // ---- 보스
    public String[] patterns = new String[0];
    public float patternInterval;
    public float slamRadius;
    public float slamWarning;
    public float slamMultiplier;
    public float chargeSpeed;
    public float chargeWarning;
    public int novaCount;
    /** 체력이 이 비율 아래로 내려가면 2페이즈 (마왕). 0이면 없음. */
    public float phase2Ratio;

    public boolean isRanged() {
        return "RANGED".equals(kind);
    }
}
