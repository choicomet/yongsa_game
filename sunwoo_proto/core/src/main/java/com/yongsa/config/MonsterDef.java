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
    /**
     * 근접 공격 방식 (2026-10-13 결정): THRUST = 예고 뒤 앞으로 내지르며 찌르기(해골 병사 등),
     * SLASH = 제자리에서 앞쪽 부채꼴(slashArc도)을 크게 베기(해골 기사). SLASH는 찌르지 않는다(lunge 무시).
     */
    public String attackStyle = "THRUST";
    public float slashArc = 140;
    /** 용사의 공격을 앞에서 받으면 이 확률로 막는다 (해골 기사). 막으면 곧바로 반격 베기. */
    public float blockChance;

    public boolean slashes() { return "SLASH".equals(attackStyle); }
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
    /** 마지막 돌진 뒤 기절(휘청)하는 시간. */
    public float rushStun;

    // ---- 리치: 뼈 가시·소환
    public int spikeCount;
    public float spikeRadius;
    public float spikeWarning;
    public float spikeMultiplier;
    public String summonId;
    public int summonCount;
    /**
     * 소환 (2026-10-12 결정): n번째 소환마다 summonCount×n마리(최대 summonCap) +
     * summonExtraId를 summonExtraCount×n마리(최대 summonExtraCap). 리치: 해골 병사 5·10·15·20, 해골 기사 1·2·3·4.
     * summonMax는 방에 함께 살아 있을 수 있는 소환수 전체 상한.
     */
    public String summonExtraId;
    public int summonExtraCount;
    public int summonCap = 99;
    public int summonExtraCap = 99;
    public int summonMax;
    /** 실명 (뼈 가시·소환과 함께). */
    public float blindDuration;
    public float blindCooldown;
    /** 뼈갑옷 방어막. */
    public float armorRatio;
    public float armorStagger;
    /** 늪 → 뼈창. */
    public int swampCount;
    public float swampStartRadius;
    public float swampMaxRadius;
    public float swampGrowTime;
    public float swampLife;
    public float swampDamageRatio;
    public int spearCount;
    public float spearWarning;
    public float spearWidth;
    public float spearLength;
    public float spearMultiplier;
    public float spearDelay;
    /** 구석에 몰리면 순간이동. */
    public float blinkCloseTime;
    public float blinkCooldown;

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
    /** 화염 구슬: 뒤돌아서는 시간, 초당 회전 각도. */
    public float spinTurnTime = 0.6f;
    public float spinSpeed = 540f;

    // ---- 마왕 2페이즈 (1페이즈 체력이 다 닳으면 모습이 바뀌고 체력이 다시 찬다)
    public String phase2Sprite;
    public float phase2HpMultiplier;
    public float phase2AttackMultiplier = 1f;
    public String[] phase2Patterns = new String[0];
    public int tentacleCount;
    public float tentacleRadius;
    public float tentacleWarning;
    public float tentacleMultiplier;

    // ---- 마룡: 태풍, 광폭화(바람의 고치 + 메테오)
    public int typhoonCount;
    public int typhoonRings;
    public float typhoonSpeed;
    public float typhoonMultiplier;
    /** 태풍에 맞으면 밀려나는 거리 (맵 끝까지). */
    public float typhoonKnockback;
    /** 체력이 이 비율 아래로 내려가면 광폭화 (한 번). 0이면 없음. */
    public float enrageRatio;
    public float cocoonTime;
    public float meteorInterval;
    public float meteorWarning;
    public float meteorSafeRadius;
    public float meteorMultiplier;
    public float enrageAttackMultiplier = 1f;

    // ---- 마왕 1페이즈: 척추 채찍, 머리 포탑, 뼛가루 폭풍, 뼈 무더기 붕괴
    public float whipRadius;
    public float whipArc;
    public float whipWarning;
    public float whipSweepTime;
    public float whipMultiplier;
    public float headTime;
    public float headFireRate;
    public float headHomingSpeed;
    public float headTurn;
    public float headLaserEvery;
    public float headLaserWarning;
    public float headLaserWidth;
    public float headLaserMultiplier;
    public float headlessSpeedMultiplier = 1f;
    /** 머리 위치 (그림 안, 왼쪽 위 기준 px): 머리를 떼어 그릴 때 쓴다. */
    public int headSrcX, headSrcY, headSrcW, headSrcH;
    public float stormTime;
    public float stormWarning;
    public float stormDamageRatio;
    public float rockWidth;
    public float rockHeight;
    public float shelterLength;
    public float collapseRange;
    public float collapseKiteTime;
    public float collapseInvuln;
    public float collapseSlamWarning;
    public float collapseSlamRadius;
    public float collapseMultiplier;
    public float collapseCooldown;

    // ---- 마왕 2페이즈: 눈알, 반전 마법진, 공허 균열, 공간 왜곡 촉수, 살점 해일, 군체
    public float eyeWarning;
    /** 심연의 눈 개수와 눈끼리 빛을 뿜는 시간 차이. */
    public int eyeCount = 1;
    public float eyeGap = 0.9f;
    public float eyeAngle;
    public float eyeStun;
    public float eyeSeal;
    public float sigilRadius;
    public float sigilLife;
    public int voidMax;
    /**
     * 공간 절단 (2026-10-12 결정): 기형적인 팔·촉수로 지금 남은 전장의 rendRatio(20%)를 한쪽에서 잘라낸다.
     * 잘린 곳은 전투 내내 어두운 공허로 남아 지나갈 수 없고, 닿기만 해도 초당 최대 체력의 rendDamageRatio.
     * 본모습이 된 뒤 rendFirst초에 처음, 그 뒤로 rendIntervalMin~Max초(1분 안팎)마다. 전장이 rendMinWidth×rendMinHeight보다 작아지지는 않는다.
     */
    public float rendFirst;
    public float rendIntervalMin, rendIntervalMax;
    public float rendRatio = 0.2f;
    public float rendWarning;
    public float rendDamageRatio;
    public float rendHitRatio;
    public float rendMinWidth = 300, rendMinHeight = 220;
    public float voidWidth;
    public float voidWarning;
    public float voidDamageRatio;
    public float warpWarning;
    public float warpWidth;
    public float warpMultiplier;
    public int tideWaves;
    public float tideGap;
    public float tideSpeed;
    public float tideHeroRatio;
    public int crackCount;
    public float crackRadius;
    public float swarmTime;
    public int swarmRows;
    public float swarmRowGap;
    public float swarmSpeed;
    public float swarmGapWidth;
    public float swarmMultiplier;

    public boolean isRanged() {
        return "RANGED".equals(kind);
    }

    public boolean hasPhase2() {
        return phase2HpMultiplier > 0;
    }
}
