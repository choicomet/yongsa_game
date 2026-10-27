package com.yongsa.combat;

import com.yongsa.config.MonsterDef;
import com.yongsa.world.Room;

/**
 * 몬스터 하나의 상태. 움직임 판단은 MonsterAI가 한다.
 * 보스를 처음 잡은 던전은 흡수하지 못한 힘 때문에 체력·공격력에 배율이 붙는다.
 */
public class Monster extends Body {

    public enum State { SPAWNING, IDLE, CHASE, WINDUP, ATTACK, RECOVER, DYING }

    public static final float SPAWN_TIME = 0.6f, ATTACK_TIME = 0.22f, DYING_TIME = 0.45f;

    public final MonsterDef def;
    public final Room room;
    protected int hp;
    protected int maxHp;
    public int attack;
    public final boolean buffed;

    protected State state = State.SPAWNING;
    protected float stateTime;
    float lungeX, lungeY;
    boolean hitThisAttack;
    /** 막기 자세 남은 시간 (해골 기사, 그림용). */
    public float blockTime;
    /** 공격 방향 (그림용: 베기·찌르기 궤적). */
    public float attackDirX() { return lungeX; }
    public float attackDirY() { return lungeY; }

    /** 용사의 공격을 막는다 (해골 기사): 막기 자세 → 곧바로 반격 예고. */
    public void parry(float fromX) {
        blockTime = 0.35f;
        faceLeft = fromX < centerX();
        setState(State.WINDUP);
        stateTime = def.windup * 0.4f;   // 반격은 평소보다 빨리
    }

    /** 공격자가 앞(바라보는 쪽)에 있는지. */
    public boolean facesTowards(float x) {
        float dx = x - centerX();
        return Math.abs(dx) < 8 || (dx < 0) == faceLeft;
    }

    public Monster(MonsterDef def, Room room, float x, float y, float hpMul, float atkMul) {
        super(x, y, def.width, def.height);
        this.def = def;
        this.room = room;
        this.maxHp = Math.round(def.hp * hpMul);
        this.hp = maxHp;
        this.attack = Math.round(def.attack * atkMul);
        this.buffed = hpMul > 1f || atkMul > 1f;
    }

    public State state() { return state; }
    public float stateTime() { return stateTime; }
    public int hp() { return hp; }
    public int maxHp() { return maxHp; }
    public boolean alive() { return hp > 0; }
    public boolean gone() { return state == State.DYING && stateTime >= dyingTime(); }
    protected float dyingTime() { return DYING_TIME; }
    /** 그릴 그림 이름. */
    public String sprite() { return def.sprite; }
    /** 리치가 불러낸 잡몹 (보스가 쓰러지면 같이 무너진다). */
    public boolean summoned;
    public boolean canBeHit() { return alive() && state != State.SPAWNING; }
    public boolean isBoss() { return false; }

    void setState(State s) {
        state = s;
        stateTime = 0;
    }

    public void update(float delta, CombatContext ctx) {
        animTime += delta;
        stateTime += delta;
        flash = Math.max(0, flash - delta);
        blockTime = Math.max(0, blockTime - delta);
        moving = false;
        MonsterAI.update(this, delta, ctx);
        if (state != State.DYING) updateKnockback(delta, ctx.collision());
    }

    /** 맞는다. 이번에 죽었으면 true. */
    public boolean damage(int amount, float fromX, float fromY, float knock) {
        if (!canBeHit()) return false;
        hp = Math.max(0, hp - amount);
        flash = 0.12f;
        knockback(fromX, fromY, knock);
        if (state == State.IDLE) setState(State.CHASE);
        if (hp == 0) {
            setState(State.DYING);
            return true;
        }
        return false;
    }

    /** 바로 쓰러뜨린다 (주인 보스가 죽었을 때 소환수). */
    public void kill() {
        if (!alive()) return;
        hp = 0;
        setState(State.DYING);
    }

    /** 체력 가득으로 되돌린다 (사망 후 보스방 초기화 등). */
    public void resetHp() {
        hp = maxHp;
    }
}
