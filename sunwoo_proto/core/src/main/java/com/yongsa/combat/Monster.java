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
    protected final int maxHp;
    public final int attack;
    public final boolean buffed;

    protected State state = State.SPAWNING;
    protected float stateTime;
    float lungeX, lungeY;
    boolean hitThisAttack;

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
    public boolean gone() { return state == State.DYING && stateTime >= DYING_TIME; }
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

    /** 체력 가득으로 되돌린다 (사망 후 보스방 초기화 등). */
    public void resetHp() {
        hp = maxHp;
    }
}
