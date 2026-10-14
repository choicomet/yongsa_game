package com.yongsa.combat;

import com.yongsa.config.Balance;

import java.util.ArrayList;
import java.util.List;

/** 일반 적과 보스의 실행 상태. 보스는 bossDef 가 채워진다. */
public class Enemy {
    public enum State { IDLE, CHASE, TELEGRAPH, ATTACK, RECOVER }

    public final Balance.EnemyDef def;
    public final Balance.BossDef bossDef;
    public final String id;
    public float x, y;
    public int hp;
    public final int maxHp;
    public final float radius;
    public final float speed;
    public final int damage;

    public State state = State.IDLE;
    public float stateTime;
    public float grace;
    public float flash;
    public float kbTime, kbVx, kbVy;
    public boolean faceLeft;
    public float animTime;

    // 현재 공격
    public String attackId = "";
    public float telegraphTime, activeTime, recoverTime;
    public float lockDx = 1, lockDy;
    /** 예고 영역(시각화와 판정 공용). */
    public HitShape shape;
    /** 예고 영역이 여러 개인 공격(흩어지는 낙석 등)의 나머지 영역. */
    public final List<HitShape> extraShapes = new ArrayList<>();
    /** 지금 공격에 맞으면 용사가 밀리는 거리 배율. */
    public float knockbackMul = 1f;
    /** 나눠 쏘는 탄(소용돌이 등)의 남은 횟수와 다음 발사까지 남은 시간, 지금 각도. */
    public Balance.ShotDef burst;
    public int burstsLeft;
    public float burstTimer, burstAngle;
    /** 지금 공격이 돌진인지. 돌진 중에는 움직이는 판정 원(dashShape)이 판정이다. */
    public boolean dashing;
    /** 돌진 중 실제로 움직이는 판정 원. */
    public HitShape dashShape;
    public boolean hitHero;
    public float dashSpeed, dashLeft;

    // 보스 패턴
    public int patternIndex;
    public boolean enraged;
    public float aiTimer;

    /** 강화 개체면 그 수치 묶음, 아니면 null. */
    public final Balance.EliteDef elite;
    /** 그릴 때 입히는 색조 (강화 개체의 구역 색). 보통 적은 null. */
    public float[] tint;
    public final List<Statuses.State> statuses = new ArrayList<>();

    public Enemy(Balance.EnemyDef def, float x, float y) {
        this(def, x, y, null);
    }

    /** elite 가 있으면 같은 적의 수치만 키운 강화 개체가 된다. */
    public Enemy(Balance.EnemyDef def, float x, float y, Balance.EliteDef elite) {
        this.def = def;
        this.bossDef = null;
        this.elite = elite;
        this.id = def.id;
        this.x = x;
        this.y = y;
        this.maxHp = elite == null ? def.hp : Math.round(def.hp * elite.hpMul);
        this.hp = maxHp;
        this.radius = elite == null ? def.radius : def.radius * elite.scale;
        this.speed = elite == null ? def.speed : def.speed * elite.speedMul;
        this.damage = elite == null ? def.damage : Math.round(def.damage * elite.damageMul);
        this.tint = def.tint;
    }

    public Enemy(Balance.BossDef def, float x, float y) {
        this.def = null;
        this.bossDef = def;
        this.elite = null;
        this.id = def.id;
        this.x = x;
        this.y = y;
        this.hp = def.hp;
        this.maxHp = def.hp;
        this.radius = def.radius;
        this.speed = def.speed;
        this.damage = def.damage;
    }

    /** 보스의 소환 주기 (GameWorld 가 갱신): 한 번이라도 불렀는지, 살아 있는 부하 수, 부하가 없는 동안 쓴 공격 수. */
    public boolean summonedOnce;
    public int minionsAlive, attacksSinceClear;

    /** 이 공격 판정이 몸에 닿는지. 옆으로 넓은 보스는 좌우로 늘인 범위(hurtExtend)까지 맞는다. */
    public boolean hitBy(HitShape s) {
        float r = hurtRadius(), ext = bossDef == null ? 0 : bossDef.hurtExtend;
        if (s.hitsCircle(x, hurtY(), r)) return true;
        if (ext <= 0) return false;
        return s.hitsCircle(x - ext, hurtY(), r) || s.hitsCircle(x + ext, hurtY(), r)
                || s.hitsCircle(x - ext / 2f, hurtY(), r) || s.hitsCircle(x + ext / 2f, hurtY(), r);
    }

    /** 용사의 공격이 닿는 몸 크기. 이동·접촉 판정에 쓰는 radius 와 따로 둔다. */
    public float hurtRadius() {
        return bossDef != null && bossDef.hurtRadius > 0 ? bossDef.hurtRadius : radius;
    }

    public boolean isBoss() {
        return bossDef != null;
    }

    public boolean alive() {
        return hp > 0;
    }

    /** 예고 또는 공격 중인 적. 회복 중은 제외한다. */
    public boolean aggressive() {
        return state == State.TELEGRAPH || state == State.ATTACK;
    }

    public String name() {
        return isBoss() ? bossDef.name : def.name;
    }

    public boolean isElite() {
        return elite != null;
    }

    /** 그림 확대 배율 (판정 반경과 같은 비율). */
    public float drawScale() {
        return elite == null ? 1f : elite.scale;
    }

    /** 피격 판정 원 중심 (몸통 중앙). */
    public float hurtY() {
        return y + radius * 0.6f;
    }

    /** 지금 유효한 공격 판정. 공격 중이 아니거나 탄만 쏘는 공격이면 null. */
    public HitShape activeHit() {
        if (state != State.ATTACK || shotsOnly()) return null;
        return dashing ? dashShape : shape;
    }

    /** 조준선은 보여 주지만 몸으로 때리지는 않는 공격 (탄이 피해를 준다). */
    public boolean shotsOnly() {
        return def != null && "shoot".equals(def.kind);
    }

    /** 거리를 두고 싸우는 적. 근접 적과 공격 순서를 따로 센다. */
    public boolean ranged() {
        return def != null && ("mark".equals(def.kind) || "shoot".equals(def.kind));
    }

    /** 몸이 닿았을 때의 피해 배율 (0 이면 닿아도 다치지 않는다). */
    public float contactMul() {
        return isBoss() ? bossDef.contactMul : def.contactMul;
    }

    /** 그림 키. 같은 그림에 색만 다른 적은 def.sprite 로 원본을 가리킨다. */
    public String spriteKey() {
        return def != null && def.sprite != null && !def.sprite.isEmpty() ? def.sprite : id;
    }

    public void enter(State s) {
        state = s;
        stateTime = 0;
    }
}
