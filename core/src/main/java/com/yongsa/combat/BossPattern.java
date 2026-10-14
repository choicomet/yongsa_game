package com.yongsa.combat;

import com.yongsa.config.Balance;

import java.util.Random;

/**
 * 보스 패턴. 정해진 순서대로 공격하고, 각 공격 뒤에 회복 시간을 둔다.
 * 공격은 이름이 아니라 모양(BossAttackDef.shape)으로 처리하므로, 새 공격은 balance.json 에 한 줄을 더하면 된다.
 * 탄 발사와 부하 소환은 공격이 시작되는 순간 GameWorld 가 같은 정의(shots, summon)를 보고 처리한다.
 */
public final class BossPattern {
    private BossPattern() {}

    /** 한 공격을 쓰려고 쫓아가는 최대 시간(초). */
    private static final float CHASE_LIMIT = 2.2f;

    public static String currentAttack(Enemy e) {
        String[] p = e.enraged && e.bossDef.enragePattern != null && e.bossDef.enragePattern.length > 0
                ? e.bossDef.enragePattern : e.bossDef.pattern;
        return p[e.patternIndex % p.length];
    }

    /** 격노하면 예고와 회복이 짧아진다. */
    private static float timeMul(Enemy e) {
        return e.enraged ? e.bossDef.enrageTimeMul : 1f;
    }

    public static void update(Enemy e, Arena a, Balance b, float dt) {
        e.animTime += dt;
        e.stateTime += dt;
        if (e.flash > 0) e.flash -= dt;
        Statuses.update(e, dt); // 보스는 affectsBoss 인 상태(저주)만 걸린다
        Hero h = a.hero();
        float dx = h.x - e.x, dy = h.y - e.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        Balance.BossAttackDef def = b.bossAttack(currentAttack(e));

        switch (e.state) {
            case IDLE:
            case CHASE: {
                e.state = Enemy.State.CHASE;
                if (Math.abs(dx) > 2) e.faceLeft = dx < 0;
                // 소환은 쉬는 주기가 있다: 부하가 남아 있거나, 다 쓰러진 뒤 다른 공격을 충분히 쓰지 않았으면 건너뛴다
                if (def.summon != null && def.summon.restAttacks > 0 && e.summonedOnce
                        && (e.minionsAlive > 0 || e.attacksSinceClear < def.summon.restAttacks)) {
                    e.patternIndex++;
                    break;
                }
                if (!h.dead && inTrigger(e, a, def, dist)) {
                    e.aiTimer = 0;
                    begin(e, a, def);
                    break;
                }
                // 용사가 거리를 벌리며 도망 다녀 이 공격을 쓸 수 없으면 다음 공격으로 넘어간다 (멀리서 맴돌며 버티기 방지)
                e.aiTimer += dt;
                if (e.aiTimer >= CHASE_LIMIT) {
                    e.aiTimer = 0;
                    e.patternIndex++;
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
                    a.sfx(e.dashing ? "dash" : "boss_attack");
                }
                break;
            case ATTACK:
                if (e.dashing) {
                    MonsterAI.updateDash(e, a, dt);
                    if (e.state == Enemy.State.RECOVER) e.recoverTime = e.bossDef.recovery * timeMul(e);
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
                        a.sfx("shockwave_charge");
                    }
                    e.enter(Enemy.State.CHASE);
                }
                break;
        }
    }

    /** 이 공격을 지금 시작해도 되는 거리·시야인지. */
    static boolean inTrigger(Enemy e, Arena a, Balance.BossAttackDef def, float dist) {
        switch (def.shape) {
            case "box":
                return dist <= def.reach * 0.8f;
            case "circle":
                return dist <= def.radius;
            case "cross":
                return dist <= def.reach;
            case "dash":
            case "mark":
            case "trail":
            case "scatter": {
                Hero h = a.hero();
                return a.rayLength(e.x, e.y, h.x - e.x, h.y - e.y, dist) >= dist - 1f;
            }
            default:
                return true; // 판정이 없는 공격(탄·소환)은 어디서든 쓴다
        }
    }

    static void begin(Enemy e, Arena a, Balance.BossAttackDef def) {
        Hero h = a.hero();
        e.enter(Enemy.State.TELEGRAPH);
        e.attackId = def.id;
        e.telegraphTime = def.telegraph * timeMul(e);
        e.activeTime = def.active;
        e.recoverTime = e.bossDef.recovery * timeMul(e);
        e.hitHero = false;
        e.dashing = false;
        e.knockbackMul = def.knockbackMul;
        e.shape = null;
        e.extraShapes.clear();
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
        switch (def.shape) {
            case "box":
                e.shape = HitShape.box(e.x, cy, e.lockDx, e.lockDy, def.reach, def.width);
                break;
            case "circle":
                e.shape = HitShape.circle(e.x, cy, def.radius);
                break;
            case "dash":
                MonsterAI.prepareDash(e, a, def.reach, def.width, def.active);
                break;
            case "mark":
                e.shape = HitShape.circle(h.x, h.hurtY(), def.radius);
                break;
            case "scatter": {
                // 첫 원은 용사 발밑, 나머지는 그 주변에 흩어진다 (같은 상황이면 같은 배치)
                e.shape = HitShape.circle(h.x, h.hurtY(), def.radius);
                Random rnd = new Random(e.patternIndex * 31L + e.hp);
                for (int i = 1; i < def.count; i++) {
                    double ang = Math.PI * 2 * (i - 1) / Math.max(1, def.count - 1) + rnd.nextDouble() * 0.6;
                    float r = def.spread * (0.55f + rnd.nextFloat() * 0.45f);
                    e.extraShapes.add(HitShape.circle(h.x + (float) Math.cos(ang) * r, h.hurtY() + (float) Math.sin(ang) * r, def.radius));
                }
                break;
            }
            case "trail": {
                // 조준 방향으로 줄지어 솟는 원. fan 이 2 이상이면 부채꼴로 여러 줄
                double base = Math.atan2(e.lockDy, e.lockDx);
                for (int f = 0; f < def.fan; f++) {
                    double ang = base + (f - (def.fan - 1) / 2.0) * def.fanAngle;
                    float ux = (float) Math.cos(ang), uy = (float) Math.sin(ang);
                    for (int i = 0; i < def.count; i++) {
                        float d = e.radius + def.radius * 0.6f + i * def.spread;
                        HitShape c = HitShape.circle(e.x + ux * d, cy + uy * d, def.radius);
                        if (e.shape == null) e.shape = c;
                        else e.extraShapes.add(c);
                    }
                }
                break;
            }
            case "cross": {
                // 자기 중심에서 사방으로 뻗는 띠
                for (int i = 0; i < def.count; i++) {
                    double ang = Math.toRadians(def.fanAngle) + Math.PI * 2 * i / def.count;
                    HitShape box = HitShape.box(e.x, cy, (float) Math.cos(ang), (float) Math.sin(ang), def.reach, def.width);
                    if (e.shape == null) e.shape = box;
                    else e.extraShapes.add(box);
                }
                break;
            }
            default:
                break;
        }
        a.sfx("warn");
    }
}
