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
    /** 원거리 공격 투사체 그림. */
    public String projectile = "bolt_blue";
    public float width;
    public float height;

    // ---- 보스 공통
    public String[] patterns = new String[0];
    public float patternInterval;

    // ---- 마왕: 내려찍기·돌진·사방 탄
    public float slamRadius;
    public float slamWarning;
    public float slamMultiplier;
    public float chargeSpeed;
    public float chargeWarning;
    public int novaCount;

    // ---- 좀비킹: 연속 돌진
    public int rushCount;
    public float rushSpeed;
    public float rushWarning;
    public float rushMultiplier;

    // ---- 리치: 뼈 가시·소환
    public int spikeCount;
    public float spikeRadius;
    public float spikeWarning;
    public float spikeMultiplier;
    public String summonId;
    public int summonCount;
    public int summonMax;

    // ---- 마룡: 독·번개·얼음·화염
    public int cloudCount;
    public float cloudRadius;
    public float cloudLife;
    public int strikeCount;
    public float strikeRadius;
    public float strikeWarning;
    public float strikeInterval;
    public float strikeMultiplier;
    public int iceCount;
    public float iceSpeed;
    public float iceMultiplier;
    public float spinMin;
    public float spinMax;
    public float fireRate;
    public float fireSpeed;
    public float fireMultiplier;

    // ---- 마왕 2페이즈 (1페이즈 체력이 다 닳으면 모습이 바뀌고 체력이 다시 찬다)
    public String phase2Sprite;
    public float phase2HpMultiplier;
    public float phase2AttackMultiplier = 1f;
    public String[] phase2Patterns = new String[0];
    public int tentacleCount;
    public float tentacleRadius;
    public float tentacleWarning;
    public float tentacleMultiplier;

    public boolean isRanged() {
        return "RANGED".equals(kind);
    }

    public boolean hasPhase2() {
        return phase2HpMultiplier > 0;
    }
}
