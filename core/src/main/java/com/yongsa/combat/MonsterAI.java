package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.config.Balance;

/**
 * 일반 적 행동: 대기 → 추적 → 공격 예고 → 공격 → 회복.
 * 예고가 시작되면 방향/목표 위치를 고정하고, 예고 중에는 용사를 따라 조준하지 않는다.
 */
public final class MonsterAI {
    private MonsterAI() {}

    private static final float[] pos = new float[2];

    public static void update(Enemy e, Arena a, Balance.EnemyCombatDef cfg, float dt) {
        if (e.flash > 0) e.flash -= dt;
        Statuses.update(e, dt);
        // 얼거나 기절한 동안은 예고·공격·회복 시간도 함께 멈춘다 (밀려나기만 한다)
        boolean held = Statuses.immobile(e);
        if (!held) {
            e.animTime += dt;
            e.stateTime += dt;
            if (e.grace > 0) e.grace -= dt;
        }
        Hero h = a.hero();

        if (e.kbTime > 0) {
            float step = Math.min(dt, e.kbTime);
            e.kbTime -= step;
            moveEnemy(e, a, e.kbVx * step, e.kbVy * step);
            // Do not immediately undo hit knockback by chasing in the same frame.
            return;
        }
        if (held) return;

        Balance.EnemyDef d = e.def;
        float dx = h.x - e.x, dy = h.y - e.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        switch (e.state) {
            case IDLE:
            case CHASE: {
                e.state = Enemy.State.CHASE;
                if (Math.abs(dx) > 2) e.faceLeft = dx < 0;
                boolean turn = e.ranged() ? a.rangedAggressiveCount() < cfg.maxRangedAggressive
                        : a.aggressiveCount() < cfg.maxAggressive;
                boolean canAttack = e.grace <= 0 && !h.dead && turn;
                if (canAttack && inTrigger(e, dist, a)) {
                    beginTelegraph(e, a);
                    break;
                }
                float desired = desiredDistance(d);
                float speed = e.speed * Statuses.speedMul(e) * dt;
                if (e.speed <= 0) {
                    // 포탑·묘목처럼 제자리에서 쏘는 적
                } else if (e.ranged()) {
                    // 원거리 적은 일정 거리를 유지한다
                    if (dist > desired + 30) moveToward(e, a, dx, dy, dist, speed);
                    else if (dist < desired - 40) moveToward(e, a, -dx, -dy, dist, speed * 0.7f);
                } else if (dist > desired) {
                    moveToward(e, a, dx, dy, dist, speed);
                }
                break;
            }
            case TELEGRAPH:
                if (e.stateTime >= e.telegraphTime) {
                    e.enter(Enemy.State.ATTACK);
                    e.hitHero = false;
                    a.sfx("enemy_attack");
                }
                break;
            case ATTACK:
                if (e.dashing) {
                    updateDash(e, a, dt);
                } else if (e.stateTime >= e.activeTime) {
                    e.enter(Enemy.State.RECOVER);
                }
                break;
            case RECOVER:
                if (e.stateTime >= e.recoverTime) e.enter(Enemy.State.CHASE);
                break;
        }
    }

    static float desiredDistance(Balance.EnemyDef d) {
        switch (d.kind) {
            case "melee": return d.triggerRange * 0.6f;
            case "charge": return d.reach * 0.6f;
            default: return d.keepDistance;
        }
    }

    static boolean inTrigger(Enemy e, float dist, Arena a) {
        Balance.EnemyDef d = e.def;
        if (dist > d.triggerRange) return false;
        if ("melee".equals(d.kind)) return true;
        // 돌진과 표식은 시야가 트여 있어야 한다
        Hero h = a.hero();
        float dx = h.x - e.x, dy = h.y - e.y;
        return a.rayLength(e.x, e.y, dx, dy, dist) >= dist - 1f;
    }

    static void beginTelegraph(Enemy e, Arena a) {
        Balance.EnemyDef d = e.def;
        Hero h = a.hero();
        e.enter(Enemy.State.TELEGRAPH);
        e.telegraphTime = d.telegraph;
        e.activeTime = d.active;
        e.recoverTime = d.recovery;
        e.hitHero = false;
        e.dashing = false;
        e.knockbackMul = 1f;
        e.extraShapes.clear();
        e.attackId = d.kind;
        float cy = e.hurtY();
        float dx = h.x - e.x, dy = h.hurtY() - cy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) {
            dx = e.faceLeft ? -1 : 1;
            dy = 0;
            len = 1;
        }
        e.lockDx = dx / len;
        e.lockDy = dy / len;
        e.faceLeft = e.lockDx < 0;
        switch (d.kind) {
            case "melee":
                e.shape = HitShape.box(e.x, cy, e.lockDx, e.lockDy, d.reach, d.width);
                break;
            case "charge":
                prepareDash(e, a, d.reach, d.width, d.active);
                break;
            case "mark":
                e.shape = HitShape.circle(h.x, h.hurtY(), d.markRadius);
                break;
            case "shoot":
                // 조준선(벽까지). 사방으로 쏘는 적은 자기 둘레에 원으로 예고한다. 판정은 탄이 한다.
                if (d.reach > 0) {
                    e.shape = HitShape.box(e.x, cy, e.lockDx, e.lockDy, a.rayLength(e.x, cy, e.lockDx, e.lockDy, d.reach), d.width);
                } else {
                    e.shape = HitShape.circle(e.x, cy, e.radius + 16);
                }
                break;
            default:
                break;
        }
        a.sfx("warn");
    }

    /** 돌진 경로를 벽까지 잘라 예고 영역으로 만든다. 판정 원이 지나가는 영역 전체를 덮는다. */
    static void prepareDash(Enemy e, Arena a, float reach, float width, float active) {
        float r = width / 2f;
        float lane = Math.max(0, a.rayLength(e.x, e.y, e.lockDx, e.lockDy, reach + r) - r);
        e.dashing = true;
        e.dashLeft = lane;
        e.dashSpeed = reach / active;
        e.shape = HitShape.box(e.x - e.lockDx * r, e.hurtY() - e.lockDy * r, e.lockDx, e.lockDy, lane + 2 * r, width);
        e.dashShape = HitShape.circle(e.x, e.hurtY(), r);
    }

    /** 돌진 중 이동. 벽이나 문에 닿으면 돌진을 끝내고 회복한다. */
    static void updateDash(Enemy e, Arena a, float dt) {
        float step = Math.min(e.dashSpeed * dt, e.dashLeft);
        boolean blocked = step > 0 && moveEnemy(e, a, e.lockDx * step, e.lockDy * step);
        e.dashLeft -= step;
        e.dashShape.cx = e.x;
        e.dashShape.cy = e.hurtY();
        if (blocked || e.dashLeft <= 0.01f || e.stateTime >= e.activeTime) {
            e.enter(Enemy.State.RECOVER);
        }
    }

    static void moveToward(Enemy e, Arena a, float dx, float dy, float dist, float step) {
        if (dist < 1e-3f) return;
        float x = e.x, y = e.y;
        moveEnemy(e, a, dx / dist * step, dy / dist * step);
        if (Math.abs(e.x-x) + Math.abs(e.y-y) < step * 0.15f) {
            // Sidestep a prop instead of endlessly pushing straight into its collision box.
            float side = e.faceLeft ? -1 : 1;
            boolean blocked = moveEnemy(e, a, -dy / dist * step * side, dx / dist * step * side);
            if (blocked) moveEnemy(e, a, dy / dist * step * side, -dx / dist * step * side);
        }
    }

    /** 방 경계 안에서만 이동한다. */
    static boolean moveEnemy(Enemy e, Arena a, float mx, float my) {
        pos[0] = e.x;
        pos[1] = e.y;
        float w = e.radius * 1.4f, h = e.radius * 0.8f;
        Rectangle room = a.roomBounds();
        boolean hit = Physics.move(pos, w, h, mx, my, r -> a.blockedForEnemy(r)
                || r.x < room.x || r.y < room.y || r.x + r.width > room.x + room.width || r.y + r.height > room.y + room.height);
        e.x = pos[0];
        e.y = pos[1];
        return hit;
    }
}
