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
        public float spinMultiplier, spinCooldown, spinRadiusTiles;
        public float dashMultiplier, dashCooldown, dashDistanceTiles, dashTime;
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
        public float bossSlamTelegraph, bossSlamRadiusTiles, bossSlamMultiplier, bossSlamCooldown;
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
    }
}
