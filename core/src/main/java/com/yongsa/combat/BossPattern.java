package com.yongsa.combat;

import com.yongsa.config.Balance;

/**
 * 보스 패턴. 정해진 순서대로 공격하고, 각 공격 뒤에 회복 시간을 둔다.
 * 패턴 대기 중에는 천천히 접근하며, 정면 공격은 유효 사거리 안에 들어오면 예고를 시작한다.
 */
public final class BossPattern {
    private BossPattern() {}

    public static String currentAttack(Enemy e) {
        String[] p = e.enraged && e.bossDef.enragePattern != null && e.bossDef.enragePattern.length > 0
                ? e.bossDef.enragePattern : e.bossDef.pattern;
        return p[e.patternIndex % p.length];
    }

    public static void update(Enemy e, Arena a, Balance.BossAttacks atk, float dt) {
        e.animTime += dt;
        e.stateTime += dt;
        if (e.flash > 0) e.flash -= dt;
        Hero h = a.hero();
        float dx = h.x - e.x, dy = h.y - e.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        String id = currentAttack(e);
        Balance.BossAttackDef def = atk.get(id);

        switch (e.state) {
            case IDLE:
            case CHASE: {
                e.state = Enemy.State.CHASE;
                if (Math.abs(dx) > 2) e.faceLeft = dx < 0;
                if (!h.dead && inTrigger(e, a, id, def, dist)) {
                    begin(e, a, id, def);
                    break;
                }
                if (dist > e.radius + 8) {
                    MonsterAI.moveToward(e, a, dx, dy, dist, e.speed * dt);
                }
                break;
            }
            case TELEGRAPH:
                if (e.stateTime >= e.telegraphTime) {
                    e.enter(Enemy.State.ATTACK);
                    e.hitHero = false;
                    a.sfx("charge".equals(e.attackId) ? "dash" : "boss_attack");
                }
                break;
            case ATTACK:
                if ("charge".equals(e.attackId)) {
                    MonsterAI.updateDash(e, a, dt);
                    if (e.state == Enemy.State.RECOVER) e.recoverTime = e.bossDef.recovery;
                } else if (e.stateTime >= e.activeTime) {
                    e.enter(Enemy.State.RECOVER);
                }
                break;
            case RECOVER:
                if (e.stateTime >= e.recoverTime) {
                    e.patternIndex++;
                    if (!e.enraged && e.bossDef.enrageAt > 0 && e.hp <= e.maxHp * e.bossDef.enrageAt
                            && e.bossDef.enragePattern != null && e.bossDef.enragePattern.length > 0) {
                        e.enraged = true;
                        e.patternIndex = 0;
                    }
                    e.enter(Enemy.State.CHASE);
                }
                break;
        }
    }

    static boolean inTrigger(Enemy e, Arena a, String id, Balance.BossAttackDef def, float dist) {
        switch (id) {
            case "swing":
            case "smash":
                return dist <= def.reach * 0.8f;
            case "slam":
                return dist <= def.radius;
            case "charge":
            case "mark": {
                Hero h = a.hero();
                return a.rayLength(e.x, e.y, h.x - e.x, h.y - e.y, dist) >= dist - 1f;
            }
            default:
                return false;
        }
    }

    static void begin(Enemy e, Arena a, String id, Balance.BossAttackDef def) {
        Hero h = a.hero();
        e.enter(Enemy.State.TELEGRAPH);
        e.attackId = id;
        e.telegraphTime = def.telegraph;
        e.activeTime = def.active;
        e.recoverTime = e.bossDef.recovery;
        e.hitHero = false;
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
        switch (id) {
            case "swing":
            case "smash":
                e.shape = HitShape.box(e.x, cy, e.lockDx, e.lockDy, def.reach, def.width);
                break;
            case "slam":
                e.shape = HitShape.circle(e.x, cy, def.radius);
                break;
            case "charge":
                MonsterAI.prepareDash(e, a, def.reach, def.width, def.active);
                break;
            case "mark":
                e.shape = HitShape.circle(h.x, h.hurtY(), def.radius);
                break;
            default:
                break;
        }
        a.sfx("warn");
    }
}
