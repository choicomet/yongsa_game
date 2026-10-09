package com.yongsa.config;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Json;

import java.io.File;

/**
 * balance.json 의 내용을 그대로 담는 수치 정의.
 * 피해량·체력·비용·확률·시간 등은 모두 여기서 읽고, UI와 전투 코드에 따로 적지 않는다.
 */
public class Balance {
    public int dataVersion;
    public HeroDef hero;
    public MiningDef mining;
    public WeaponDef[] weapons;
    public float dismantleRefundRate;
    public float damagePerLevel;
    public EnhanceStep[] enhance;
    public int[] enhanceCapByBossCount;
    public ShockwaveDef shockwave;
    public ZoneDef[] zones;
    public EnemyCombatDef enemyCombat;
    public EnemyDef[] enemies;
    public BossDef[] bosses;
    public BossAttacks bossAttacks;
    public BossIntroDef bossIntro;
    public SettingsDef settingsDefault;

    public static class HeroDef {
        public int maxHp;
        public float moveSpeed;
        public float attackMoveFactor;
        public float invulnTime;
        public float knockbackDistance;
        public float knockbackTime;
        public float hurtRadius;
        public float bodyWidth;
        public float bodyHeight;
        public float deathTime;
    }

    public static class MiningDef {
        public float holdTime;
        public float range;
        public int veinsPerRoom;
        public int minCandidates;
    }

    public static class WeaponDef {
        public String id;
        public String name;
        public String unlockBoss;
        public int craftCost;
        public int baseAttack;
        public float windup;
        public float active;
        public float recovery;
        public float reach;
        public float width;

        public float cycle() {
            return windup + active + recovery;
        }
    }

    public static class EnhanceStep {
        public int target;
        public int cost;
        public float chance;
        public int maxTries;
    }

    public static class ShockwaveDef {
        public float length;
        public float width;
        public int damageMultiplier;
        public float windup;
        public float active;
        public float recovery;
        public float cooldown;
    }

    public static class ZoneDef {
        public String id;
        public String name;
        public int veinYield;
        public int roomReward;
        public int bossReward;
        public String boss;
        public String requiresBoss;
        public String direction;
    }

    public static class EnemyCombatDef {
        public float spawnGrace;
        public int maxAggressive;
        public float knockbackDistance;
        public float knockbackTime;
        public float flashTime;
    }

    public static class EnemyDef {
        public String id;
        public String name;
        public int hp;
        public int damage;
        public float speed;
        public float radius;
        /** melee | charge | mark */
        public String kind;
        public float telegraph;
        public float active;
        public float recovery;
        public float reach;
        public float width;
        public float markRadius;
        public float triggerRange;
    }

    public static class BossDef {
        public String id;
        public String name;
        public int hp;
        public int damage;
        public float speed;
        public float radius;
        public float recovery;
        public String[] pattern;
        public String[] enragePattern;
        public float enrageAt;
    }

    public static class BossAttackDef {
        public float telegraph;
        public float active;
        public float reach;
        public float width;
        public float radius;
    }

    public static class BossAttacks {
        public BossAttackDef swing;
        public BossAttackDef slam;
        public BossAttackDef charge;
        public BossAttackDef smash;
        public BossAttackDef mark;

        public BossAttackDef get(String id) {
            switch (id) {
                case "swing": return swing;
                case "slam": return slam;
                case "charge": return charge;
                case "smash": return smash;
                case "mark": return mark;
                default: throw new IllegalArgumentException("알 수 없는 보스 공격: " + id);
            }
        }
    }

    public static class BossIntroDef {
        public float cameraTime;
        public float readyTime;
    }

    public static class SettingsDef {
        public int masterVolume;
        public int musicVolume;
        public int sfxVolume;
        public boolean fullscreen;
    }

    // ---- 조회 도우미 ----

    public WeaponDef weapon(String id) {
        for (WeaponDef w : weapons) if (w.id.equals(id)) return w;
        throw new IllegalArgumentException("알 수 없는 무기: " + id);
    }

    public EnhanceStep enhanceStep(int targetLevel) {
        for (EnhanceStep s : enhance) if (s.target == targetLevel) return s;
        return null;
    }

    public int maxLevel() {
        int max = 0;
        for (EnhanceStep s : enhance) max = Math.max(max, s.target);
        return max;
    }

    public ZoneDef zone(String id) {
        for (ZoneDef z : zones) if (z.id.equals(id)) return z;
        throw new IllegalArgumentException("알 수 없는 구역: " + id);
    }

    public ZoneDef zoneOfBoss(String bossId) {
        for (ZoneDef z : zones) if (z.boss.equals(bossId)) return z;
        throw new IllegalArgumentException("알 수 없는 보스: " + bossId);
    }

    public EnemyDef enemy(String id) {
        for (EnemyDef e : enemies) if (e.id.equals(id)) return e;
        throw new IllegalArgumentException("알 수 없는 적: " + id);
    }

    public BossDef boss(String id) {
        for (BossDef b : bosses) if (b.id.equals(id)) return b;
        throw new IllegalArgumentException("알 수 없는 보스: " + id);
    }

    // ---- 불러오기 ----

    public static Balance load(FileHandle file) {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        Balance b = json.fromJson(Balance.class, file);
        b.validate();
        return b;
    }

    /** libGDX 백엔드 없이(테스트 등) 읽을 때 사용한다. */
    public static Balance loadFromPath(String path) {
        return load(new FileHandle(new File(path)));
    }

    /** 설정 데이터의 기본 일관성을 검사한다. 잘못되면 실행 초기에 바로 실패시킨다. */
    public void validate() {
        if (weapons == null || weapons.length == 0) throw new IllegalStateException("무기 정의가 없습니다");
        if (enhance == null || enhance.length == 0) throw new IllegalStateException("강화 정의가 없습니다");
        for (int i = 0; i < enhance.length; i++) {
            EnhanceStep s = enhance[i];
            if (s.target != i + 1) throw new IllegalStateException("강화 단계는 +1부터 순서대로 정의해야 합니다");
            if (s.chance < 0f || s.chance > 1f) throw new IllegalStateException("강화 확률 범위 오류: +" + s.target);
            if (s.maxTries < 1) throw new IllegalStateException("최대 시도 횟수 오류: +" + s.target);
            if (s.cost < 0) throw new IllegalStateException("강화 비용 오류: +" + s.target);
        }
        if (enhanceCapByBossCount == null || enhanceCapByBossCount.length < zones.length + 1)
            throw new IllegalStateException("강화 상한 정의가 보스 수보다 짧습니다");
    }
}
