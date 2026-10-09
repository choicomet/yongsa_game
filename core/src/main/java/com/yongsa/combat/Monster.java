package com.yongsa.combat;

import com.yongsa.config.Balance.MonsterDef;

/** 잡몹과 보스 (FR-전투-06, 10). */
public class Monster extends Actor {
    public enum State { CHASE, TELEGRAPH, RECOVER, SLAM_TELEGRAPH }

    public final MonsterDef def;
    public final boolean boss, finalBoss;
    public final float speed;
    public State state = State.CHASE;
    public float stateT;
    public float attackCd;
    public float dirX = 0, dirY = -1;
    public float slamCd;
    public float slamX, slamY, slamRadius;
    public float walkTime;
    public float immuneMsgCd;

    public Monster(MonsterDef def, boolean boss, boolean finalBoss, float tileSize) {
        this.def = def;
        this.boss = boss;
        this.finalBoss = finalBoss;
        this.speed = def.speedTiles * tileSize;
        this.hp = this.maxHp = def.hp;
        float scale = finalBoss ? 2.4f : boss ? 2.0f : 1f;
        hw = 9 * scale;
        hh = 6 * scale;
        hurtRadius = 12 * scale;
    }

    public Facing facing() {
        return Facing.of(dirX, dirY);
    }
}
