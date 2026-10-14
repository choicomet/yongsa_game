package com.yongsa.config;

import com.google.gson.Gson;

import java.util.List;

/**
 * 게임의 모든 수치. assets/data/balance.json 을 그대로 읽는다 (NFR-06).
 * 필드 이름은 JSON 키와 같다.
 */
public class Balance {
    public int tileSize;
    public HeroCfg hero;
    public AttackCfg attack;
    public List<WeaponDef> weapons;
    public EnhanceCfg enhance;
    public MiningCfg mining;
    public MonsterCfg monster;
    public List<DungeonDef> dungeons;
    public MonsterDef finalBoss;
    public RewardCfg reward;
    public PotionCfg potion;
    public VillageCfg village;

    public static Balance parse(String json) {
        return new Gson().fromJson(json, Balance.class);
    }

    public WeaponDef weapon(String id) {
        for (WeaponDef w : weapons) if (w.id.equals(id)) return w;
        throw new IllegalArgumentException("없는 무기: " + id);
    }

    public DungeonDef dungeon(String id) {
        for (DungeonDef d : dungeons) if (d.id.equals(id)) return d;
        throw new IllegalArgumentException("없는 던전: " + id);
    }

    /** 그 광물로 만드는 무기 (던전마다 하나). */
    public WeaponDef weaponForOre(String ore) {
        for (WeaponDef w : weapons) if (ore.equals(w.ore)) return w;
        return null;
    }

    public static class HeroCfg {
        public int baseMaxHp;
        public int[] maxHpAfterBosses;
        public float moveSpeedTiles;
        public float invulnAfterHit;
        public float knockbackTiles;
        public float campRegenFractionPerSec;
    }

    public static class AttackCfg {
        public float basicMultiplier, basicCooldown, basicRangeTiles, basicArcDegrees;
        /** 구르기: 짧게 빠르게 이동하고 그 앞부분 동안은 맞지 않는다. 공격하지는 않는다. */
        public float rollDistanceTiles, rollTime, rollInvulnTime, rollCooldown;
        public float damageSpreadMin, damageSpreadMax;
        /** 이 거리 안에 몬스터가 있으면 용사가 가장 가까운 몬스터를 자동으로 바라본다 (0 이면 끔). */
        public float autoFaceTiles;
    }

    /** 던전 밖 마을 (선우 버전). 마을 사람 자리는 마을 그림 기준 (0~1). */
    public static class VillageCfg {
        public String name;
        /** 마왕 전에는 이만큼 독백하며 되돌아선 다음에야 나갈 수 있다. */
        public int exitRefusals;
        public List<String> exitLines;
        public String giveUpLine, arriveTrappedLine, trappedLine;
        public List<Villager> villagers;
    }

    public static class Villager {
        public String kind, name;
        public float[] at;
        public List<String> freeLines;
    }

    /** 보스 보물상자: 잡을 때마다 그 던전 광물을 chestOreMin~chestOreMax 개. */
    public static class RewardCfg {
        public int chestOreMin, chestOreMax, bossChestPotions;
        /** 잡몹방을 비웠을 때 상자가 나올 확률과 그 안의 광물·포션. */
        public float mobChestChance;
        public int mobChestOreMin, mobChestOreMax, mobChestPotions;
    }

    /** 회복 포션 (던전 상자에서만 나온다). */
    public static class PotionCfg {
        public float healRatio, cooldown;
        public int max;
    }

    public static class WeaponDef {
        public String id, name, ore;
        public int baseAttack, perLevel, craftCost;
        public boolean enhanceable, holy;
        public String awakenedName, abilityName, abilityDesc;
        /** 검마다 하나 있는 액티브 스킬 (Q). 없으면 null (봉인된 성검). */
        public SkillDef skill;
    }

    /**
     * 검 스킬. enhance.skillUnlockLevel 강화부터 쓸 수 있고, 각성하면 awakened* 값으로 강해진다.
     * kind: cleave(땅 가르기 충격파) · crescent(날아가는 초승달 검기) · strike(가까운 적에게 낙뢰) · beam(큰 빛의 검기).
     */
    public static class SkillDef {
        public String name, desc, awakenedDesc, kind;
        public float cooldown, multiplier, awakenedMultiplier = 1f;
        /** 사거리, 검기 폭(낙뢰는 터지는 반지름), 날아가는 빠르기 (타일 단위). */
        public float rangeTiles, widthTiles, speedTiles;
        /** 검기 줄 수 또는 낙뢰 수, 각성하면 awakenedCount. 여러 줄이면 spreadDegrees 간격으로 퍼진다. */
        public int count = 1, awakenedCount = 1;
        public float spreadDegrees;
        /** 맞은 잡몹 기절 시간, 맞힌 적마다 회복하는 최대 체력 비율. */
        public float stunSeconds, healRatio;
    }

    public static class EnhanceCfg {
        public int maxLevel;
        public int successBasePercent, successStepPercent;
        public List<Band> bands;
        public float destroyRefundRatio;
        public int dismantleRefund;
        public float hammerSeconds, awakenSeconds;
        /** 이 강화 단계부터 검 스킬(Q)을 쓸 수 있다. 성검은 깨어나면 바로. */
        public int skillUnlockLevel;
    }

    public static class Band {
        public int fromLevel, toLevel, cost;
        public int destroyShare, downShare, keepShare;
    }

    public static class MiningCfg {
        public int hitsToBreak, orePerVeinMin, orePerVeinMax;
        public int veinsMin, veinsMax, veinsMinCleared, veinsMaxCleared;
        public int firstOreRoomGuarantee;
    }

    public static class MonsterCfg {
        public float detectTiles, attackReachTiles, telegraphSeconds, attackCooldown, knockbackTiles;
        public float bossSlamTelegraph, bossSlamRadiusTiles, bossSlamMultiplier;
        /** 방에 나타난 뒤 움직이기 시작할 때까지 (마법진이 보이는 시간). */
        public float spawnSeconds;
        /** 박쥐: 주위를 돌다가 일직선으로 덮친다. */
        public float swoopTelegraph, swoopDistanceTiles, swoopTime, swoopRecover, orbitTiles;
        /** 해골병: 휘두르며 앞으로 한 발 내딛는다. */
        public float lungeTiles;
        /** 마족병: 떨어져서 불덩이를 쏜다. 가까이 오면 순간이동으로 물러난다. */
        public float castTelegraph, castCooldown, castRangeTiles, keepAwayTiles, blinkCooldown;
        public float projectileSpeedTiles, projectileRadius;
        /** 보스 고유 패턴. */
        public float bossPatternGap;
        public float chargeTelegraph, chargeSpeedTiles, chargeMaxTiles, chargeStun, chargeMultiplier;
        public int comboHits;
        public float comboTelegraph, comboStepTiles;
        public int volleyCount;
        public float volleySpreadDegrees, volleyTelegraph;
        public int ringCount;
        public int summonCount;
        /** 체력이 이 비율 아래로 내려가면 2페이즈 (패턴이 빨라지고 하나가 더 붙는다). */
        public float phase2HpRatio, phase2Speed;
        /** 연속 돌진: 매번 다시 조준해서 rushCount 번 달려든다. */
        public int rushCount;
        public float rushTelegraph;
        /** 연속 돌진의 마지막 돌진이 끝나면 이만큼 휘청인다 (선우 버전 좀비킹). */
        public float rushStun;
        /** 해골 사제 뼈 가시 (선우 버전 리치): 용사 발밑 하나 + 주변 몇 개. */
        public int spikeCount;
        public float spikeRadiusTiles, spikeSpreadTiles, spikeWarning, spikeMultiplier;
        /** 실명: 뼈 가시·소환을 쓸 때 화면이 어두워진다. 한 번 걸면 쿨타임 동안 다시 안 건다. */
        public float blindSeconds, blindCooldown;
        /** 마왕 척추 채찍: 앞쪽 부채꼴을 한쪽 끝에서 다른 끝으로 휩쓴다. */
        public int whipLines;
        public float whipArcDegrees, whipRadiusTiles, whipWarning, whipSweep, whipMultiplier;
        /** 마왕 2페이즈 눈알: 뜨는 순간 그쪽을 보고 있으면 지금 체력의 절반을 잃고 잠깐 굳는다. */
        public int eyeCount;
        public float eyeGap, eyeWarning, eyeAngleDegrees, eyeStun, eyeDistanceTiles;
        /** 낙석: 용사가 있던 자리마다 차례로 예고 뒤 떨어진다. */
        public int rockCount;
        public float rockInterval, rockWarning, rockRadiusTiles, rockMultiplier;
        /** 방패: 최대 체력의 shieldRatio 만큼 막이 생기고, 깨기 전엔 보스 체력이 닳지 않는다. 깨지면 휘청. */
        public float shieldRatio, shieldStagger;
        /** 피웅덩이: 점점 넓어지는 웅덩이. 밟으면 초당 최대 체력의 poolDamageRatio + 둔화. 이어서 창 세 줄. */
        public int poolCount;
        public float poolStartRadiusTiles, poolMaxRadiusTiles, poolGrowTime, poolLife, poolDamageRatio;
        public float slowRatio, slowSeconds;
        public int spearCount;
        public float spearDelay, spearWarning, spearWidthTiles, spearLengthTiles, spearMultiplier, spearSpreadDegrees;
        /** 낙뢰: 용사 자리에 차례로 번개. */
        public int strikeCount;
        public float strikeInterval, strikeWarning, strikeRadiusTiles, strikeMultiplier;
        /** 회전 화염: 제자리에서 돌며 두 갈래로 불덩이를 뿜는다. */
        public float spinMin, spinMax, spinRate, spinTurnSpeed, spinProjectileSpeedTiles, spinMultiplier;
        /** 마왕 2페이즈 촉수: 용사 자리와 그 둘레에 예고 뒤 솟는다. */
        public int tentacleCount;
        public float tentacleRingTiles, tentacleRadiusTiles, tentacleWarning, tentacleMultiplier;
        /** 구석 몰이 방지: 벽 가까이에서 용사가 cornerTime 초 넘게 붙어 있으면 순간이동하고 세 갈래 탄. */
        public float cornerTime, cornerCooldown;
        /** 마왕: 처음 쓰러지면 체력이 이만큼 다시 차서 2페이즈 (변신하는 동안은 무적). */
        public float finalPhase2HpMultiplier, finalPhase2AttackMultiplier, phaseShiftSeconds;
        /** 보스를 잡은 던전의 잡몹: 흡수하지 못한 힘이 퍼져 강해진다. */
        public float clearedMobHpMultiplier, clearedMobAttackMultiplier;
    }

    public static class DungeonDef {
        public String id, name, ore, recommended;
        public MonsterDef mob, boss;
        public int mobsPerRoom;
    }

    public static class MonsterDef {
        public String name, sprite;
        public int hp, attack;
        public float speedTiles;
        /** 잡몹 행동: melee(기본), swoop(박쥐), brute(해골병), caster(마족병). */
        public String ai;
        /** 보스 패턴 이름: slam, charge, rush, combo, volley, ring, summon, rockfall, shield, bloodpool, lightning, firespin, tentacle, bonespike, whip, eyes. */
        public List<String> patterns;
        /** 2페이즈에 더해지는 패턴 (없으면 빠르기만 한다). 마왕은 2페이즈에 이 목록으로 바뀐다. */
        public List<String> phase2Patterns;
        /** 잡몹: 공격 예고 시간과 공격 뒤 쉬는 시간 (0이면 monster 공통 값). */
        public float telegraph, cooldown;
        /** 보스: 구석에 몰리면 순간이동해서 빠져나간다. */
        public boolean cornerBlink;
        /** 보스 소환: 어느 던전의 잡몹을 몇 마리 부르나 (없으면 자기 던전 잡몹, monster.summonCount). */
        public String summonFrom;
        public int summonCount;
    }
}
