package com.yongsa.enemy;

import com.yongsa.combat.Physics;
import com.yongsa.config.Balance;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.map.WorldMap;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 몬스터 AI (보스·몬스터 담당): 매 프레임 상태 기계(추격·예고·공격·돌진·기절), 길찾기,
 * 근접 공격, 마법탄 쏘기와 날아가기, 몬스터끼리 밀어내기. 보스만의 패턴은 BossBrain.
 */
public class MonsterBrain extends EnemyContext {
    BossBrain boss;

    public MonsterBrain(GameSession g, List<GameEvent> events) {
        super(g, events);
    }

    /** 싸우는 방의 길찾기 (용사 칸까지의 거리). */
    int[] flow;

    int flowHeroX = Integer.MIN_VALUE, flowHeroY;

    Room flowRoom;

    float pathX, pathY;

    public void updateMonsters(float dt) {
        Balance.MonsterCfg mc = balance.monster;
        List<Monster> summoned = new ArrayList<>();
        for (Monster m : monsters) {
            if (m.dead) continue;
            m.flash -= dt;
            m.immuneMsgCd -= dt;
            m.blinkCd -= dt;
            if (m.kbT > 0) {
                Physics.move(map, m, m.kbVX * dt, m.kbVY * dt, T);
                m.kbT -= dt;
            }
            if (m.state == Monster.State.SPAWN) {
                m.stateT -= dt;
                if (m.stateT <= 0) m.state = Monster.State.CHASE;
                continue;
            }
            float speedUp = m.enraged ? mc.phase2Speed : 1f;
            m.attackCd -= dt * speedUp;
            m.patternCd -= dt * speedUp;
            float dx = hero.x - m.x, dy = hero.y - m.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float reach = mc.attackReachTiles * T + m.hw + hero.hw;
            if (m.boss && m.def.cornerBlink && boss.cornered(m, dist, reach, dt)) continue;
            switch (m.state) {
                case CHASE:
                    if (dist > 0.01f) {
                        m.dirX = dx / dist;
                        m.dirY = dy / dist;
                    }
                    steer(m);
                    if (m.boss) boss.chaseBoss(m, dist, reach, dt, summoned);
                    else chaseMob(m, dist, reach, dt);
                    break;
                case TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        // 예고한 방향으로 친다. 그 사이 걸어서 벗어났으면 빗나간다.
                        if ("brute".equals(m.ai())) Physics.move(map, m, m.dirX * mc.lungeTiles * T, m.dirY * mc.lungeTiles * T, T);
                        meleeStrike(m, reach, 1f);
                        m.enter(Monster.State.RECOVER, m.boss ? 0.45f : "brute".equals(m.ai()) ? 0.5f : 0.3f);
                        m.attackCd = m.cooldown(mc.attackCooldown);
                    }
                    break;
                case COMBO_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        Physics.move(map, m, m.dirX * mc.comboStepTiles * T, m.dirY * mc.comboStepTiles * T, T);
                        meleeStrike(m, reach + 0.6f * T, 1.1f);
                        if (--m.comboLeft > 0) {
                            boss.comboStep(m);
                        } else {
                            m.enter(Monster.State.RECOVER, 0.9f);
                        }
                    }
                    break;
                case SWOOP_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        m.enter(Monster.State.SWOOPING, mc.swoopTime);
                        m.dashLeft = mc.swoopDistanceTiles * T;
                        m.dashHit = false;
                        events.add(GameEvent.sound("dash"));
                    }
                    break;
                case SWOOPING:
                case CHARGING: {
                    boolean charge = m.state == Monster.State.CHARGING;
                    float spd = charge ? mc.chargeSpeedTiles * T : mc.swoopDistanceTiles * T / mc.swoopTime;
                    float step = Math.min(m.dashLeft, spd * dt);
                    boolean blocked = Physics.move(map, m, m.lockX * step, m.lockY * step, T);
                    m.dashLeft -= step;
                    m.stateT -= dt;
                    float hx = hero.x - m.x, hy = hero.y - m.y;
                    float touch = m.hurtRadius + hero.hw + 4;
                    if (!m.dashHit && hx * hx + hy * hy < touch * touch) {
                        m.dashHit = true;
                        hitHero(m.attack(), charge ? mc.chargeMultiplier : 1f, m.x, m.y);
                        if (g.heroJustDied()) return;
                    }
                    boolean over = m.dashLeft <= 0.5f || m.stateT <= 0 || blocked;
                    if (charge && over && m.rushLeft > 1) {
                        // 연속 돌진: 숨을 고르고 다시 조준한다
                        m.rushLeft--;
                        aimAtHero(m);
                        m.enter(Monster.State.CHARGE_TELEGRAPH, mc.rushTelegraph);
                        if (blocked) wallBump(m);
                    } else if (charge && over && (blocked || m.rushLeft == 1)) {
                        // 벽에 부딪히거나 연속 돌진을 다 쓰면 휘청인다: 반격할 기회
                        boolean lastRush = m.rushLeft == 1;
                        m.rushLeft = 0;
                        m.enter(Monster.State.STUNNED, lastRush ? Math.max(mc.rushStun, blocked ? mc.chargeStun : 0) : mc.chargeStun);
                        if (blocked) wallBump(m);
                        else events.add(GameEvent.sound("block"));
                    } else if (over) {
                        m.rushLeft = 0;
                        m.enter(Monster.State.RECOVER, charge ? 0.6f : mc.swoopRecover);
                        m.attackCd = m.cooldown(mc.attackCooldown);
                    }
                    break;
                }
                case CAST_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (dist > 0.01f && !"ring".equals(m.castKind)) {
                        m.dirX = dx / dist;
                        m.dirY = dy / dist;
                    }
                    if (m.stateT <= 0) {
                        fire(m);
                        m.enter(Monster.State.RECOVER, m.boss ? 0.6f : 0.4f);
                        m.attackCd = m.boss ? mc.attackCooldown : m.cooldown(mc.castCooldown);
                    }
                    break;
                case SLAM_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        float sx = hero.x - m.slamX, sy = hero.y - m.slamY;
                        if (sx * sx + sy * sy <= m.slamRadius * m.slamRadius) {
                            hitHero(m.attack(), mc.bossSlamMultiplier, m.slamX, m.slamY);
                        }
                        m.enter(Monster.State.RECOVER, 0.7f);
                        events.add(GameEvent.sound("slam"));
                        GameEvent fx = GameEvent.fx(GameEvent.Type.SLAM_FX, m.slamX, m.slamY);
                        fx.size = m.slamRadius;
                        events.add(fx);
                    }
                    break;
                case CHARGE_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        m.enter(Monster.State.CHARGING, 3f);
                        m.dashLeft = mc.chargeMaxTiles * T;
                        m.dashHit = false;
                        events.add(GameEvent.sound("dash"));
                    }
                    break;
                case CHANNEL:
                    boss.channel(m, dt * speedUp);
                    break;
                case PHASE_SHIFT:
                    m.stateT -= dt;
                    // 변신하는 동안 체력이 다시 차오른다
                    m.hp = Math.max(1, Math.round(m.maxHp * m.progress()));
                    if (m.stateT <= 0) {
                        m.hp = m.maxHp;
                        m.enraged = true;
                        m.patternCd = 0.8f;
                        m.state = Monster.State.CHASE;
                    }
                    break;
                case STUNNED:
                case RECOVER:
                    m.stateT -= dt;
                    if (m.stateT <= 0) m.state = Monster.State.CHASE;
                    break;
                default:
                    break;
            }
            if (g.heroJustDied()) return; // g.die() 가 몬스터 목록을 비웠다
        }
        monsters.addAll(summoned);
        separate();
    }

    void wallBump(Monster m) {
        events.add(GameEvent.sound("slam"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.SLAM_FX, m.x + m.lockX * m.hw, m.y + m.lockY * m.hh);
        fx.size = T * 1.4f;
        events.add(fx);
    }

    void aimAtHero(Monster m) {
        float dx = hero.x - m.x, dy = hero.y - m.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 0.01f) {
            m.dirX = dx / l;
            m.dirY = dy / l;
        }
        m.lockX = m.dirX;
        m.lockY = m.dirY;
    }

    /** 용사에게 곧장 갈 수 없으면 바닥 칸을 따라 돌아간다. pathX, pathY 에 걸어갈 방향을 넣는다. */
    void steer(Monster m) {
        pathX = m.dirX;
        pathY = m.dirY;
        Room r = g.fightRoom();
        if (r == null || r.scene == null || clearLine(r, m.x, m.y, hero.x, hero.y)) return;
        updateFlow(r);
        int mx = tileX(m.x), my = tileY(m.y);
        int best = flowAt(r, mx, my);
        if (best < 0) best = Integer.MAX_VALUE;
        int bx = mx, by = my;
        for (int oy = -1; oy <= 1; oy++) {
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) continue;
                if (ox != 0 && oy != 0 && (flowAt(r, mx + ox, my) < 0 || flowAt(r, mx, my + oy) < 0)) continue;
                int f = flowAt(r, mx + ox, my + oy);
                if (f >= 0 && f < best) {
                    best = f;
                    bx = mx + ox;
                    by = my + oy;
                }
            }
        }
        if (bx == mx && by == my) return;
        float tx = center(bx) - m.x, ty = center(by) - m.y, l = (float) Math.sqrt(tx * tx + ty * ty);
        if (l < 0.01f) return;
        pathX = tx / l;
        pathY = ty / l;
    }

    /** 두 점 사이가 벽에서 넉넉히 떨어진 바닥으로만 이어지는가. */
    boolean clearLine(Room r, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        int n = Math.max(1, (int) (len / (T * 0.5f)));
        for (int i = 1; i < n; i++) {
            int tx = tileX(x0 + dx * i / n), ty = tileY(y0 + dy * i / n);
            if (!r.inside(tx, ty) || map.isBlockedOverlay(tx, ty)) return false;
        }
        return true;
    }

    /** 용사 칸에서 넓이 우선으로 퍼진 거리 (용사 칸이 바뀔 때만 다시 계산). */
    void updateFlow(Room r) {
        int hx = tileX(hero.x), hy = tileY(hero.y);
        if (r == flowRoom && hx == flowHeroX && hy == flowHeroY) return;
        flowRoom = r;
        flowHeroX = hx;
        flowHeroY = hy;
        int w = r.x1 - r.x0 + 1, h = r.y1 - r.y0 + 1;
        if (flow == null || flow.length < w * h) flow = new int[w * h];
        java.util.Arrays.fill(flow, 0, w * h, -1);
        if (!r.contains(hx, hy)) return;
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
        flow[(hx - r.x0) + (hy - r.y0) * w] = 0;
        q.add(new int[]{hx, hy});
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int d0 = flow[(p[0] - r.x0) + (p[1] - r.y0) * w];
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (!r.floor(nx, ny) || map.isBlockedOverlay(nx, ny)) continue;
                int i = (nx - r.x0) + (ny - r.y0) * w;
                if (flow[i] >= 0) continue;
                flow[i] = d0 + 1;
                q.add(new int[]{nx, ny});
            }
        }
    }

    int flowAt(Room r, int x, int y) {
        if (r != flowRoom || !r.contains(x, y)) return -1;
        return flow[(x - r.x0) + (y - r.y0) * (r.x1 - r.x0 + 1)];
    }

    /** 잡몹: 종류마다 다르게 다가오고 공격한다. */
    void chaseMob(Monster m, float dist, float reach, float dt) {
        Balance.MonsterCfg mc = balance.monster;
        switch (m.ai()) {
            case "swoop": {
                // 용사 둘레를 빙글빙글 돌다가 방향을 정하고 덮친다.
                float orbit = mc.orbitTiles * T;
                if (m.attackCd <= 0 && dist < orbit * 1.8f) {
                    m.lockX = m.dirX;
                    m.lockY = m.dirY;
                    m.enter(Monster.State.SWOOP_TELEGRAPH, m.telegraph(mc.swoopTelegraph));
                    return;
                }
                float tx = -m.dirY * m.orbitSign, ty = m.dirX * m.orbitSign;
                float pull = (dist - orbit) / orbit;
                float mx = tx + m.dirX * pull * 2f, my = ty + m.dirY * pull * 2f;
                float l = (float) Math.sqrt(mx * mx + my * my);
                if (l > 0.01f && Physics.move(map, m, mx / l * m.speed * dt, my / l * m.speed * dt, T)) m.orbitSign = -m.orbitSign;
                m.walkTime += dt;
                return;
            }
            case "caster": {
                float keep = mc.keepAwayTiles * T;
                if (dist < keep * 0.6f && m.blinkCd <= 0 && boss.blinkAway(m)) return;
                if (m.attackCd <= 0 && dist < mc.castRangeTiles * T) {
                    m.castKind = "cast";
                    m.enter(Monster.State.CAST_TELEGRAPH, m.telegraph(mc.castTelegraph));
                    return;
                }
                float dir = dist < keep ? -1f : dist > keep * 1.4f ? 1f : 0f;
                if (dir != 0) {
                    float sx = dir > 0 ? pathX : -m.dirX, sy = dir > 0 ? pathY : -m.dirY;
                    Physics.move(map, m, sx * m.speed * dt, sy * m.speed * dt, T);
                    m.walkTime += dt;
                }
                return;
            }
            default:
                // melee, brute: 다가가서 휘두른다
                if (dist <= reach * ("brute".equals(m.ai()) ? 1.6f : 1f) && m.attackCd <= 0) {
                    m.enter(Monster.State.TELEGRAPH, m.telegraph(mc.telegraphSeconds));
                } else if (dist > reach * 0.7f) {
                    Physics.move(map, m, pathX * m.speed * dt, pathY * m.speed * dt, T);
                    m.walkTime += dt;
                }
        }
    }

    /** 앞쪽 부채꼴 안에 용사가 있으면 맞힌다. */
    void meleeStrike(Monster m, float reach, float mult) {
        float dx = hero.x - m.x, dy = hero.y - m.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float dot = dist > 0.01f ? (dx * m.dirX + dy * m.dirY) / dist : 1f;
        if (dist <= reach + 4 && dot > 0.2f) hitHero(m.attack(), mult, m.x, m.y);
        events.add(GameEvent.sound("monsterAttack"));
    }

    /** 모은 투사체를 쏜다: cast(한 발), tri(세 갈래), volley(부채꼴), ring(사방). */
    void fire(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        float speed = mc.projectileSpeedTiles * T * (m.boss ? 1.1f : 1f);
        float base = (float) Math.atan2(m.dirY, m.dirX);
        float sy = m.centerY();
        String kind = m.castKind == null ? "cast" : m.castKind;
        int dmg = m.attack();
        if ("ring".equals(kind)) {
            int n = mc.ringCount + (m.finalBoss ? 4 : 0);
            float off = random.nextFloat() * 6.283f;
            for (int i = 0; i < n; i++) shoot(m, m.x, sy, off + 6.283f * i / n, speed * 0.8f, dmg, m.def.sprite);
        } else if ("volley".equals(kind) || "tri".equals(kind)) {
            int n = "tri".equals(kind) ? 3 : mc.volleyCount;
            float spread = (float) Math.toRadians("tri".equals(kind) ? 30 : mc.volleySpreadDegrees);
            for (int i = 0; i < n; i++) shoot(m, m.x, sy, base - spread / 2f + spread * i / Math.max(1, n - 1), speed, dmg, m.def.sprite);
        } else {
            shoot(m, m.x, sy, base, speed, dmg, m.def.sprite);
        }
        events.add(GameEvent.sound("cast"));
    }

    void shoot(Monster m, float x, float y, float angle, float speed, int damage, String source) {
        Projectile p = new Projectile();
        p.x = x + (float) Math.cos(angle) * m.hurtRadius;
        p.y = y + (float) Math.sin(angle) * m.hurtRadius;
        p.vx = (float) Math.cos(angle) * speed;
        p.vy = (float) Math.sin(angle) * speed;
        p.radius = balance.monster.projectileRadius * (m.boss ? 1.4f : 1f);
        p.life = 4f;
        p.damage = damage;
        p.source = source;
        projectiles.add(p);
    }

    public void updateProjectiles(float dt) {
        for (Projectile p : projectiles) {
            if (p.dead) continue;
            p.age += dt;
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.life <= 0 || map.solid(tileX(p.x), tileY(p.y - 6))) {
                p.dead = true;
                continue;
            }
            float hx = hero.x - p.x, hy = hero.centerY() - p.y;
            float r = p.radius + hero.hw;
            if (hx * hx + hy * hy < r * r) {
                if (hero.rolling()) continue; // 굴러서 피한다
                p.dead = true;
                hitHero(p.damage, 1f, p.x - p.vx, p.y - p.vy);
                if (g.heroJustDied()) return;
            }
        }
        projectiles.removeIf(p -> p.dead);
    }

    /** 몬스터끼리 겹치지 않게 살짝 민다. */
    void separate() {
        for (int i = 0; i < monsters.size(); i++) {
            Monster a = monsters.get(i);
            if (a.dead) continue;
            for (int j = i + 1; j < monsters.size(); j++) {
                Monster b = monsters.get(j);
                if (b.dead) continue;
                float dx = b.x - a.x, dy = b.y - a.y;
                float min = a.hw + b.hw;
                float d2 = dx * dx + dy * dy;
                if (d2 >= min * min || d2 < 0.0001f) continue;
                float d = (float) Math.sqrt(d2);
                float push = (min - d) / 2f;
                Physics.move(map, a, -dx / d * push, -dy / d * push, T);
                Physics.move(map, b, dx / d * push, dy / d * push, T);
            }
        }
    }
}
