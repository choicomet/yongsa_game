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
    public AbsorbCfg absorb;
    public List<WeaponDef> weapons;
    public EnhanceCfg enhance;
    public MiningCfg mining;
    public MonsterCfg monster;
    public List<DungeonDef> dungeons;
    public MonsterDef finalBoss;

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
    }

    public static class AbsorbCfg {
        public float attackBonusPerAwakenedWeapon;
    }

    public static class WeaponDef {
        public String id, name, ore;
        public int baseAttack, perLevel, craftCost;
        public boolean enhanceable, holy;
        public String awakenedName, abilityName, abilityDesc;
    }

    public static class EnhanceCfg {
        public int maxLevel;
        public int successBasePercent, successStepPercent;
        public List<Band> bands;
        public float destroyRefundRatio;
        public int dismantleRefund;
        public float hammerSeconds, awakenSeconds;
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
    }

    public static class DungeonDef {
        public String id, name, ore, recommended;
        public MonsterDef mob, boss;
        public int mobsPerRoom, bossOreReward;
    }

    public static class MonsterDef {
        public String name, sprite;
        public int hp, attack;
        public float speedTiles;
        /** 잡몹 행동: melee(기본), swoop(박쥐), brute(해골병), caster(마족병). */
        public String ai;
        /** 보스 패턴 이름: slam, charge, combo, volley, ring, summon. */
        public List<String> patterns;
        /** 2페이즈에 더해지는 패턴 (없으면 빠르기만 한다). */
        public List<String> phase2Patterns;
    }
}
