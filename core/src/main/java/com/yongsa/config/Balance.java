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
    public DungeonDef dungeon;
    public WeaponDef[] weapons;
    public float damagePerLevel;
    public EnhanceStep[] enhance;
    public int[] enhanceCapByBossCount;
    public ShockwaveDef shockwave;
    public ZoneDef[] zones;
    public EnemyCombatDef enemyCombat;
    public EnemyDef[] enemies;
    public BossDef[] bosses;
    public BossAttackDef[] bossAttacks;
    public BossIntroDef bossIntro;
    public SettingsDef settingsDefault;
    public FeelDef feel = new FeelDef();
    public EliteDef elite = new EliteDef();
    public StatusDef[] statuses = {};
    public BlessingDef[] blessings = {};
    /** 일반 방의 상자에서 나올 수 있는 회복약 (보스방 상자에는 없다). chance 의 합이 1보다 작으면 나머지는 골드만. */
    public ChestHealDef[] chestHeals = {};

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
        public float dodgeSpeed = 540;
        public float dodgeDuration = 0.18f;
        public float dodgeCooldown = 0.65f;
        /** 구르기를 시작한 뒤 다치지 않는 시간(초). 구르는 시간보다 조금 길어, 적을 지나친 직후에도 안전하다. */
        public float dodgeInvuln = 0.32f;
        public float autoAimRange = 360;
        public float inputBuffer = 0.16f;
        public float comboWindow = 0.7f;
        public float finisherMultiplier = 1.5f;
    }

    public static class MiningDef {
        public float holdTime;
        public float range;
        public int oreRoomVeins;
        /** 구역 첫 광물방(무기 없이 재료를 모으는 곳)의 광맥 수. */
        public int firstRoomVeins;
        public int mixedRoomVeins;
        public int minCandidates;
    }

    /** 무작위 던전 생성 수치. 방 크기는 타일 단위. */
    public static class DungeonDef {
        public int cellWidth;
        public int cellHeight;
        public int mainPathRooms;
        public int branchRooms;
        public int roomMinWidth;
        public int roomMaxWidth;
        public int roomMinHeight;
        public int roomMaxHeight;
        public float oreOnlyChance;
        public float battleOnlyChance;
        public float branchOreChance;
        /** 적이 나오기 전에 바닥 표식이 떠 있는 시간(초). */
        public float spawnMarkTime = 0.6f;
        /** 표식은 용사에게서 이만큼 떨어진 생성점을 먼저 쓴다(px). */
        public float spawnMinDistance = 150;
        /** 방마다 준비해 두는 생성점 수. */
        public int spawnSlots = 6;
        /** 전투방 안쪽에 놓는 엄폐물(지나갈 수 없고 탄을 막는다) 수의 범위. */
        public int coverMin = 2, coverMax = 4;
        /** 이 깊이 이하의 방은 웨이브를 최소로 한다 (구역 초입은 가볍게). */
        public int shallowDepth = 1;
    }

    /** 구역별 웨이브 규칙. 한 웨이브는 예산만큼 적을 부르고, 남은 적이 nextAt 이하가 되면 다음 웨이브가 온다. */
    public static class WaveDef {
        public int minWaves = 1;
        public int maxWaves = 1;
        public float budget = 2;
        public float budgetPerDepth;
        public int maxPerWave = 4;
        public int nextAt = 1;
        /** 제단을 지키는 방: 예산 배율, 추가 웨이브 수. 웨이브마다 강화 개체가 하나씩 섞인다. */
        public float guardBudgetMul = 1.6f;
        public int guardExtraWaves = 1;
    }

    /** 색만 바꾼 강화 개체. 그림은 같고 수치와 색조만 다르다. */
    public static class EliteDef {
        public int minDepth = 4;
        public float chance = 0.15f;
        public float branchChance = 0.3f;
        public float hpMul = 2f;
        public float damageMul = 1.25f;
        public float speedMul = 1.1f;
        public float scale = 1.2f;
        /** 강화 개체 한 마리당 방 돌파 보상에 더해지는 광물. */
        public int bonusMinerals = 2;
    }

    /** 상태 이상 하나. 규칙은 Statuses 참고. */
    public static class StatusDef {
        public String id;
        public String name;
        public float decayPerSec;
        public float slowAtFull;
        public float holdTime;
        public boolean immobile;
        public float damageTakenMul = 1f;
        public boolean affectsBoss;
        public float[] color = {1, 1, 1};
    }

    /** 상자에서 나오는 회복약: 최대 체력의 heal 만큼 바로 회복한다. */
    public static class ChestHealDef {
        public String id;
        public String name;
        public float heal;
        public float chance;
        /** 그림 키. */
        public String sprite;
    }

    /** 축복 = 이름 + 효과 묶음. */
    public static class BlessingDef {
        public String id;
        public String name;
        public String desc;
        public com.yongsa.model.Modifiers mods = new com.yongsa.model.Modifiers();
    }

    /** 구역마다 하나 있는 제단: 골드를 바치고 축복 하나를 받는다. 죽으면 축복은 사라진다. */
    public static class ShrineDef {
        public String name;
        public String sprite;
        public int costGold;
        /** true 면 강한 적이 지키는 방에 놓인다 (돌파해야 바칠 수 있다). */
        public boolean guarded;
        public String[] blessings = {};
    }

    /** 탄 발사 규칙. ring 이면 사방으로, 아니면 조준 방향을 중심으로 spread 간격 부채꼴. */
    public static class ShotDef {
        public int count;
        public float spread;
        public float speed;
        public boolean ring;
        /** 탄 피해 = 쏜 적의 damage × 이 값 (최소 6). */
        public float damageMul = 0.5f;
        /** 여러 번 나눠 쏘기: 횟수, 간격(초), 쏠 때마다 돌아가는 각도(라디안). 소용돌이 탄막을 만든다. */
        public int bursts = 1;
        public float interval = 0.12f;
        public float rotate;
        /** 탄의 색 (r, g, b). 없으면 기본 붉은 탄. */
        public float[] color;
    }

    /** 보스가 부르는 부하: enemies 중에서 count 마리, 살아 있는 부하가 maxAlive 이상이면 부르지 않는다. */
    public static class SummonDef {
        /** 부른 부하가 모두 쓰러진 뒤, 다른 공격을 이만큼 쓰고 나서야 다시 부른다. 0 이면 순서가 올 때마다 부른다. */
        public int restAttacks;
        public String[] enemies = {};
        public int count = 2;
        public int maxAlive = 4;
    }

    /** 연출 수치. 화면 흔들림은 약·중·강 세 단계만 쓴다. */
    public static class FeelDef {
        public float shakeLight = 0.03f;
        public float shakeMedium = 0.14f;
        public float shakeHeavy = 0.35f;
        /** 이 피해 이상이면 한 단계 큰 흔들림. */
        public int heavyHitDamage = 25;
        /** 보스 사망 연출 시간(초). 이 동안 전투는 멈추고 용사는 무적이다. */
        public float bossDeathTime = 1.2f;
        public float bossDeathFlash = 0.6f;
        public float noticeTime = 3.2f;
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
        /** 제작과 강화에 쓰는 광물이 나오는 구역 id. 광물 색마다 만들 수 있는 장비가 다르다. */
        public String ore;
        /** 연속 공격 타수. 2 이상이면 마지막 타가 마무리 일격이 된다. */
        public int comboHits = 1;
        /** 마무리 일격의 동작. "slam" 이면 옆으로 쓸지 않고 머리 위로 들어 올렸다가 내려찍는다. 비어 있으면 평소처럼 휘두른다. */
        public String finisherMotion = "";
        /** 강화 단계에 따라 바뀌는 공격 형태. fromLevel 오름차순. 비어 있으면 형태 변화 없음. */
        public FormDef[] forms = {};

        public float cycle() {
            return windup + active + recovery;
        }
    }

    /**
     * 무기 형태 하나: 강화가 fromLevel 이상이면 적용되는 공격 변화 묶음.
     * 형태는 누적되지 않으며 가장 높은 형태 한 줄이 공격 전체를 정의한다.
     */
    public static class FormDef {
        public int fromLevel;
        public String name;
        public String desc;
        /** true 면 연속 공격의 마지막 타(3타)에만 적용한다. */
        public boolean finisherOnly;
        public float reachMul = 1f;
        public float widthMul = 1f;
        /** 조준 방향으로 뻗는 검기: 길이·폭(px)과 피해 배율. 길이가 0이면 없음. */
        public float waveLength;
        public float waveWidth;
        public float waveDamageMul;
        /** 타격 지점의 원형 충격: 반경(px)과 피해 배율. 반경이 0이면 없음. */
        public float burstRadius;
        public float burstDamageMul;
        /** 맞은 적에게 거는 상태 이상 id 와 한 번에 쌓는 게이지(1이면 즉시 발동). */
        public String status = "";
        public float statusAmount;
    }

    public static class EnhanceStep {
        public int target;
        public int cost;
        public float chance;
        public int maxTries;
    }

    /** 각성 충격파: 용사 몸에서 퍼지는 파동. 방 안의 모든 적이 한 번씩 맞고 밀려난다. */
    public static class ShockwaveDef {
        /** 파동이 퍼져 보이는 크기(px). 판정은 방 전체다. */
        public float radius = 460;
        /** 일반 적이 밀려나는 거리(px). 보스는 bosses[].shockPush 만큼만 밀린다. */
        public float knockback = 90;
        public int damageMultiplier;
        public float windup;
        public float active;
        public float recovery;
        public float cooldown;
    }

    public static class ZoneDef {
        public String id;
        public String name;
        /** 이 구역에서 나오는 광물의 이름과 색 (r, g, b). */
        public String oreName;
        public float[] oreColor = {1, 1, 1};
        public int veinYield;
        public int roomReward;
        public int bossReward;
        /** 방을 돌파하면 나타나는 상자의 골드 (일반 방 / 제단을 지키는 방 / 보스방). */
        public int chestGold;
        public int guardChestGold;
        public int bossGold;
        public String boss;
        public String requiresBoss;
        public String direction;
        /** 이 구역 방에 나오는 적 (같은 이름을 여러 번 적으면 더 자주 나온다). */
        public String[] enemyTable;
        public WaveDef waves = new WaveDef();
        /** 강화 개체의 색조 (r, g, b). */
        public float[] eliteTint = {1f, 0.5f, 0.5f};
        /** 이 구역의 제단 (구역마다 하나). 없으면 null. */
        public ShrineDef shrine;
    }

    public static class EnemyCombatDef {
        public float spawnGrace;
        public int maxAggressive;
        /** 원거리 적(mark, shoot)은 공격 순서 제한과 별도로 이만큼 더 동시에 공격할 수 있다. */
        public int maxRangedAggressive = 2;
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
        /** melee | charge | mark(용사 발밑 표식) | shoot(조준선을 보여 주고 탄을 쏜다) */
        public String kind;
        /** 그림 키. 비어 있으면 id 와 같다. 같은 그림에 tint 만 달리해 다른 적을 만들 수 있다. */
        public String sprite = "";
        public float[] tint;
        /** 원거리 적이 유지하려는 거리(px). */
        public float keepDistance = 150;
        /** 이 깊이부터 웨이브에 섞인다. */
        public int minDepth;
        /** 몸이 닿았을 때의 피해 = damage × 이 값. 0 이면 닿아도 다치지 않는다 (원거리 적). */
        public float contactMul;
        public float telegraph;
        public float active;
        public float recovery;
        public float reach;
        public float width;
        public float markRadius;
        public float triggerRange;
        /** 웨이브 예산에서 차지하는 몫. */
        public float cost = 1f;
        /** 처치 파편 색 (r, g, b). */
        public float[] color = {0.9f, 0.7f, 0.4f};
        /** 공격이 시작될 때 쏘는 탄. 없으면 null. */
        public ShotDef shots;
    }

    public static class BossDef {
        public String id;
        public String name;
        public int hp;
        public int damage;
        public float speed;
        public float radius;
        /** 용사의 공격이 닿는 몸 크기(px). 0 이면 radius 와 같다. 덩치 큰 보스는 옆에서도 맞도록 발 크기보다 넓게 둔다. */
        public float hurtRadius;
        /** 피격 범위를 좌우로 더 늘이는 길이(px). 몸이 옆으로 넓은 보스용: 반경 hurtRadius 인 원이 좌우로 이만큼 늘어난 모양이 된다. */
        public float hurtExtend;
        /** 충격파에 밀려나는 거리(px). 0 이면 밀리지 않는다 (마왕). */
        public float shockPush;
        public float recovery;
        public String[] pattern;
        public String[] enragePattern;
        public float enrageAt;
        /** 격노 후 예고 시간과 회복 시간에 곱하는 값 (1보다 작으면 빨라진다). */
        public float enrageTimeMul = 1f;
        /** 몸이 닿았을 때의 피해 = damage × 이 값. */
        public float contactMul;
    }

    /**
     * 보스 공격 하나. 모양(shape)과 수치만으로 정의하며, 새 공격은 여기에 한 줄을 더하고 보스의 pattern 에 id 를 넣으면 된다.
     * shape: box(정면 사각) | circle(자기 중심 원) | dash(돌진) | mark(용사 발밑 원)
     *      | scatter(용사 주변에 원 여러 개) | trail(조준 방향으로 줄지은 원: 갈라지는 땅)
     *      | cross(자기 중심에서 사방으로 뻗는 띠) | none(판정 없음: 탄이나 소환만)
     */
    public static class BossAttackDef {
        public String id;
        public String shape = "none";
        public float telegraph;
        public float active;
        public float reach;
        public float width;
        public float radius;
        /** scatter: 원의 수와 흩어지는 반경. trail: 한 줄의 원 수와 간격. cross: 띠의 수. */
        public int count = 1;
        public float spread;
        /** trail: 줄의 수와 줄 사이 각도(라디안). cross: 처음 띠의 각도(도). */
        public int fan = 1;
        public float fanAngle;
        /** 맞았을 때 용사가 밀리는 거리 배율 (방패 밀기 등). */
        public float knockbackMul = 1f;
        /** 보스 그림의 자세 이름: "<anim>_tele" 로 예고, "<anim>_hit" 로 타격을 그린다. 비어 있으면 평소 자세. */
        public String anim = "";
        /** 판정 영역에 그리는 효과: "spikes"(땅에서 솟는 가시) | "" */
        public String fx = "";
        /** 공격이 시작될 때 쏘는 탄. 없으면 null. */
        public ShotDef shots;
        /** 공격이 시작될 때 부르는 부하. 없으면 null. */
        public SummonDef summon;
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

    public BossAttackDef bossAttack(String id) {
        for (BossAttackDef a : bossAttacks) if (a.id.equals(id)) return a;
        throw new IllegalArgumentException("알 수 없는 보스 공격: " + id);
    }

    public int zoneIndex(String id) {
        for (int i = 0; i < zones.length; i++) if (zones[i].id.equals(id)) return i;
        throw new IllegalArgumentException("알 수 없는 구역: " + id);
    }

    /** 이 무기를 만들고 강화하는 데 쓰는 광물의 칸 번호 (= 구역 순서). */
    public int oreIndex(String weaponId) {
        return zoneIndex(weapon(weaponId).ore);
    }

    public EnemyDef enemyOrNull(String id) {
        for (EnemyDef e : enemies) if (e.id.equals(id)) return e;
        return null;
    }

    public StatusDef statusOrNull(String id) {
        if (id == null || id.isEmpty()) return null;
        for (StatusDef s : statuses) if (s.id.equals(id)) return s;
        return null;
    }

    public BlessingDef blessingOrNull(String id) {
        for (BlessingDef d : blessings) if (d.id.equals(id)) return d;
        return null;
    }

    /** 이 강화 단계에서 쓰는 무기 형태. 아직 형태가 열리지 않았으면 null. */
    public FormDef form(String weaponId, int level) {
        FormDef best = null;
        for (FormDef f : weapon(weaponId).forms) if (level >= f.fromLevel && (best == null || f.fromLevel > best.fromLevel)) best = f;
        return best;
    }

    /** 다음에 열릴 형태 (안내용). 없으면 null. */
    public FormDef nextForm(String weaponId, int level) {
        FormDef best = null;
        for (FormDef f : weapon(weaponId).forms) if (f.fromLevel > level && (best == null || f.fromLevel < best.fromLevel)) best = f;
        return best;
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
        if (dungeon == null) throw new IllegalStateException("던전 생성 정의가 없습니다");
        if (enhanceCapByBossCount == null || enhanceCapByBossCount.length < zones.length + 1)
            throw new IllegalStateException("강화 상한 정의가 보스 수보다 짧습니다");
        // 표끼리 가리키는 id 가 실제로 있는지 (오타는 실행 초기에 잡는다)
        for (WeaponDef w : weapons) {
            for (FormDef f : w.forms) {
                if (!f.status.isEmpty() && statusOrNull(f.status) == null)
                    throw new IllegalStateException("알 수 없는 상태 이상: " + w.id + " → " + f.status);
            }
        }
        for (BlessingDef d : blessings) {
            if (!d.mods.onHitStatus.isEmpty() && statusOrNull(d.mods.onHitStatus) == null)
                throw new IllegalStateException("알 수 없는 상태 이상: " + d.id + " → " + d.mods.onHitStatus);
        }
        for (WeaponDef w : weapons) {
            if (w.ore == null) throw new IllegalStateException("무기의 광물 정의 없음: " + w.id);
            zoneIndex(w.ore);
            if (w.comboHits < 1) throw new IllegalStateException("연속 공격 타수 오류: " + w.id);
        }
        for (BossDef boss : bosses) {
            for (String a : boss.pattern) bossAttack(a);
            if (boss.enragePattern != null) for (String a : boss.enragePattern) bossAttack(a);
        }
        for (BossAttackDef a : bossAttacks) {
            if (a.summon != null) for (String e : a.summon.enemies) enemy(e);
        }
        for (ZoneDef z : zones) {
            boolean shallowOk = false;
            for (String e : z.enemyTable) shallowOk |= enemy(e).minDepth <= 0;
            if (!shallowOk) throw new IllegalStateException("구역 초입에 나올 적이 없습니다: " + z.id);
            if (z.waves.minWaves < 1 || z.waves.maxWaves < z.waves.minWaves || z.waves.maxPerWave < 1)
                throw new IllegalStateException("웨이브 정의 오류: " + z.id);
            if (z.shrine != null) {
                for (String id : z.shrine.blessings) {
                    if (blessingOrNull(id) == null) throw new IllegalStateException("알 수 없는 축복: " + z.id + " → " + id);
                }
            }
        }
    }
}
