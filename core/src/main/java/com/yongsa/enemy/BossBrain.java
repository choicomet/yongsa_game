package com.yongsa.enemy;

import com.yongsa.combat.Physics;
import com.yongsa.config.Balance;
import com.yongsa.config.Balance.DungeonDef;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.map.WorldMap;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;

import java.util.ArrayList;
import java.util.List;

/**
 * 보스 패턴 (보스·몬스터 담당): 패턴 고르기와 시작, 긴 패턴(채널), 바닥 공격(Hazard),
 * 실명, 마왕 변신, 말풍선, 순간이동. 공통 상태 기계와 쏘기는 MonsterBrain.
 */
public class BossBrain extends EnemyContext {
    MonsterBrain mobs;

    /** 매 프레임 쿨타임. */
    public void tick(float dt) {
        blindCd -= dt;
    }

    public BossBrain(GameSession g, List<GameEvent> events, MonsterBrain mobs) {
        super(g, events);
        this.mobs = mobs;
        mobs.boss = this;
    }

    /** 피웅덩이에서 닳은 체력 (1 이 넘으면 깎는다), 숫자를 띄울 때까지 모은 양. */
    float poolAcc, poolShown, poolTextCd;

    float blindCd;

    /**
     * 구석 몰이 방지: 벽 가까이 있는 보스에게 용사가 바짝 붙어 있는 시간이 쌓이면
     * 멀리 순간이동하고 세 갈래 탄을 쏜다. 순간이동했으면 true.
     */
    boolean cornered(Monster m, float dist, float reach, float dt) {
        Balance.MonsterCfg mc = balance.monster;
        boolean idle = m.state == Monster.State.CHASE || m.state == Monster.State.RECOVER
                || m.state == Monster.State.TELEGRAPH;
        if (idle && dist < reach * 1.6f && nearWall(m)) m.cornerT += dt;
        else m.cornerT = Math.max(0f, m.cornerT - dt * 0.5f);
        if (m.cornerT < mc.cornerTime || m.blinkCd > 0 || !idle) return false;
        m.cornerT = 0;
        if (!blinkAway(m)) return false;
        m.blinkCd = mc.cornerCooldown;
        mobs.aimAtHero(m);
        m.castKind = "tri";
        mobs.fire(m);
        m.enter(Monster.State.RECOVER, 0.5f);
        return true;
    }

    boolean nearWall(Monster m) {
        float r = m.hurtRadius + T * 0.9f;
        for (int i = 0; i < 8; i++) {
            float a = 6.283f * i / 8f;
            if (map.solid(tileX(m.x + (float) Math.cos(a) * r), tileY(m.y + (float) Math.sin(a) * r))) return true;
        }
        return false;
    }

    /** 보스: 다가가 근접 공격을 하다가, 패턴 쿨타임이 차면 고유 패턴을 하나 고른다. */
    void chaseBoss(Monster m, float dist, float reach, float dt, List<Monster> summoned) {
        Balance.MonsterCfg mc = balance.monster;
        if (m.patternCd <= 0 && dist < 11 * T) {
            String p = pickPattern(m);
            m.lastPattern = p;
            m.patternCd = mc.bossPatternGap;
            if (startPattern(m, p, summoned)) return;
        }
        if (dist <= reach && m.attackCd <= 0) {
            m.enter(Monster.State.TELEGRAPH, mc.telegraphSeconds);
        } else if (dist > reach * 0.7f) {
            float k = m.speed * dt * (m.enraged ? 1.15f : 1f);
            Physics.move(map, m, mobs.pathX * k, mobs.pathY * k, T);
            m.walkTime += dt;
        }
    }

    String pickPattern(Monster m) {
        List<String> pool = new ArrayList<>();
        if (m.finalBoss && m.phase2) {
            // 마왕 2페이즈는 패턴이 통째로 바뀐다
            if (m.def.phase2Patterns != null) pool.addAll(m.def.phase2Patterns);
        } else {
            if (m.def.patterns != null) pool.addAll(m.def.patterns);
            if (m.enraged && m.def.phase2Patterns != null) pool.addAll(m.def.phase2Patterns);
        }
        if (m.shield > 0) pool.removeIf("shield"::equals); // 방패가 남아 있으면 다시 쓰지 않는다
        if (pool.isEmpty()) return "slam";
        if (pool.size() > 1) pool.removeIf(p -> p.equals(m.lastPattern));
        if (pool.isEmpty()) return "slam";
        return pool.get(random.nextInt(pool.size()));
    }

    boolean startPattern(Monster m, String p, List<Monster> summoned) {
        Balance.MonsterCfg mc = balance.monster;
        switch (p) {
            case "slam":
                m.slamX = hero.x;
                m.slamY = hero.y;
                m.slamRadius = mc.bossSlamRadiusTiles * T * (m.finalBoss ? 1.3f : 1f);
                m.enter(Monster.State.SLAM_TELEGRAPH, mc.bossSlamTelegraph);
                return true;
            case "charge":
                m.rushLeft = 0;
                mobs.aimAtHero(m);
                m.enter(Monster.State.CHARGE_TELEGRAPH, mc.chargeTelegraph);
                events.add(GameEvent.sound("roar"));
                return true;
            case "rush":
                m.rushLeft = mc.rushCount;
                mobs.aimAtHero(m);
                m.enter(Monster.State.CHARGE_TELEGRAPH, mc.rushTelegraph * 1.4f);
                events.add(GameEvent.sound("roar"));
                return true;
            case "combo":
                m.comboLeft = mc.comboHits;
                comboStep(m);
                return true;
            case "volley":
            case "ring":
                m.castKind = p;
                m.enter(Monster.State.CAST_TELEGRAPH, mc.volleyTelegraph);
                return true;
            case "rockfall":
                startChannel(m, p, mc.rockCount, 0.2f);
                events.add(GameEvent.sound("roar"));
                events.add(GameEvent.toast("천장이 무너진다! 계속 움직이자"));
                return true;
            case "lightning":
                startChannel(m, p, mc.strikeCount, 0.25f);
                events.add(GameEvent.sound("roar"));
                return true;
            case "firespin":
                startChannel(m, p, 0, 0f);
                m.stateT = m.stateTotal = mc.spinMin + random.nextFloat() * (mc.spinMax - mc.spinMin);
                m.spinAngle = random.nextFloat() * 6.283f;
                m.orbitSign = random.nextBoolean() ? 1 : -1;
                events.add(GameEvent.sound("roar"));
                return true;
            case "bloodpool":
                spawnPools(m);
                startChannel(m, p, 1, mc.spearDelay);
                events.add(GameEvent.sound("cast"));
                return true;
            case "shield":
                m.shield = m.shieldMax = Math.round(m.maxHp * mc.shieldRatio);
                m.enter(Monster.State.RECOVER, 0.6f);
                events.add(GameEvent.toast(m.def.name + "이(가) 방패를 둘렀다. 깨기 전엔 체력이 닳지 않는다"));
                events.add(GameEvent.sound("block"));
                GameEvent fx = GameEvent.fx(GameEvent.Type.SHIELD_UP, m.x, m.centerY());
                fx.size = m.hurtRadius * 2.4f;
                events.add(fx);
                return true;
            case "tentacle": {
                float rr = mc.tentacleRingTiles * T;
                float off = random.nextFloat() * 6.283f;
                circleHazard("tentacle", hero.x, hero.y, mc.tentacleRadiusTiles * T, mc.tentacleWarning, m.attack(), mc.tentacleMultiplier);
                for (int i = 1; i < mc.tentacleCount; i++) {
                    float a = off + 6.283f * i / (mc.tentacleCount - 1);
                    float tx = hero.x + (float) Math.cos(a) * rr, ty = hero.y + (float) Math.sin(a) * rr;
                    if (map.solid(tileX(tx), tileY(ty))) continue;
                    circleHazard("tentacle", tx, ty, mc.tentacleRadiusTiles * T, mc.tentacleWarning + 0.15f, m.attack(), mc.tentacleMultiplier);
                }
                m.enter(Monster.State.RECOVER, mc.tentacleWarning + 0.5f);
                events.add(GameEvent.sound("roar"));
                return true;
            }
            case "summon": {
                int count = m.def.summonCount > 0 ? m.def.summonCount : mc.summonCount;
                int alive = 0;
                for (Monster o : monsters) if (!o.dead && !o.boss) alive++;
                if (alive >= count) return false;
                Room r = g.fightRoom();
                if (r == null) return false;
                DungeonDef d = balance.dungeon(m.def.summonFrom != null ? m.def.summonFrom : r.dungeonId);
                for (int i = 0; i < count - alive; i++) {
                    Monster s = g.makeMonster(r, d.mob, false, false, false);
                    if (s != null) summoned.add(s);
                }
                events.add(GameEvent.toast(m.def.name + "이(가) 부하를 불렀다!"));
                events.add(GameEvent.sound("roar"));
                m.enter(Monster.State.RECOVER, 0.8f);
                blind(m);
                return true;
            }
            case "bonespike": {
                // 용사 발밑 하나 + 주변에 흩어진 뼈 가시
                float spread = mc.spikeSpreadTiles * T;
                circleHazard("bone", hero.x, hero.y, mc.spikeRadiusTiles * T, mc.spikeWarning, m.attack(), mc.spikeMultiplier);
                for (int i = 1; i < mc.spikeCount; i++) {
                    for (int tries = 0; tries < 8; tries++) {
                        float a = random.nextFloat() * 6.283f, d = spread * (0.45f + 0.55f * random.nextFloat());
                        float tx = hero.x + (float) Math.cos(a) * d, ty = hero.y + (float) Math.sin(a) * d;
                        if (map.solid(tileX(tx), tileY(ty))) continue;
                        circleHazard("bone", tx, ty, mc.spikeRadiusTiles * T, mc.spikeWarning + 0.1f * i, m.attack(), mc.spikeMultiplier);
                        break;
                    }
                }
                m.slamX = hero.x;
                m.slamY = hero.y;
                m.castKind = "bonespike";
                m.enter(Monster.State.RECOVER, mc.spikeWarning + 0.4f);
                events.add(GameEvent.sound("cast"));
                blind(m);
                return true;
            }
            case "whip": {
                // 앞쪽 부채꼴을 한쪽 끝에서 다른 끝으로 휩쓴다: 선 여러 개가 차례로 터진다
                mobs.aimAtHero(m);
                float base = (float) Math.atan2(m.dirY, m.dirX);
                float arc = (float) Math.toRadians(mc.whipArcDegrees);
                boolean cw = random.nextBoolean();
                for (int i = 0; i < mc.whipLines; i++) {
                    float t = mc.whipLines == 1 ? 0.5f : i / (float) (mc.whipLines - 1);
                    float a = base + arc * ((cw ? t : 1 - t) - 0.5f);
                    Hazard h = new Hazard(Hazard.Kind.LINE, "whip");
                    h.x = m.x;
                    h.y = m.centerY();
                    h.x2 = m.x + (float) Math.cos(a) * mc.whipRadiusTiles * T;
                    h.y2 = h.y + (float) Math.sin(a) * mc.whipRadiusTiles * T;
                    h.radius = T * 0.55f;
                    h.delay = h.warning = mc.whipWarning + mc.whipSweep * t;
                    h.damage = m.attack();
                    h.multiplier = mc.whipMultiplier;
                    hazards.add(h);
                }
                m.enter(Monster.State.SLAM_TELEGRAPH, mc.whipWarning);
                m.slamX = m.x;
                m.slamY = m.y;
                m.slamRadius = 0;
                events.add(GameEvent.sound("roar"));
                return true;
            }
            case "eyes": {
                // 눈알이 차례로 뜬다: 뜨는 순간 그쪽을 보고 있으면 안 된다
                Room r = g.fightRoom();
                for (int i = 0; i < mc.eyeCount; i++) {
                    for (int tries = 0; tries < 12; tries++) {
                        float a = random.nextFloat() * 6.283f, d = mc.eyeDistanceTiles * T * (0.7f + 0.3f * random.nextFloat());
                        float tx = hero.x + (float) Math.cos(a) * d, ty = hero.y + (float) Math.sin(a) * d;
                        if (map.solid(tileX(tx), tileY(ty)) || (r != null && !r.inside(tileX(tx), tileY(ty)))) continue;
                        Hazard h = new Hazard(Hazard.Kind.EYE, "eye");
                        h.x = tx;
                        h.y = ty;
                        h.radius = T * 0.9f;
                        h.delay = h.warning = mc.eyeWarning + mc.eyeGap * i;
                        hazards.add(h);
                        break;
                    }
                }
                m.enter(Monster.State.RECOVER, mc.eyeWarning + 0.5f);
                events.add(GameEvent.toast("눈알이 뜬다! 눈을 돌려라"));
                events.add(GameEvent.sound("roar"));
                return true;
            }
            default:
                return false;
        }
    }

    void startChannel(Monster m, String kind, int count, float firstDelay) {
        m.channelKind = kind;
        m.channelLeft = count;
        m.channelT = firstDelay;
        m.enter(Monster.State.CHANNEL, 99f);
    }

    /** 제자리에서 이어 가는 패턴 한 프레임. */
    void channel(Monster m, float dt) {
        Balance.MonsterCfg mc = balance.monster;
        m.channelT -= dt;
        switch (m.channelKind) {
            case "rockfall":
            case "lightning": {
                boolean rock = "rockfall".equals(m.channelKind);
                if (m.channelT > 0) return;
                if (m.channelLeft <= 0) {
                    m.enter(Monster.State.RECOVER, rock ? 0.9f : 0.7f);
                    return;
                }
                m.channelLeft--;
                // 그 순간 용사가 있는 자리를 노린다: 멈춰 있으면 맞는다
                float jitter = rock ? 0 : T * 0.4f;
                float tx = hero.x + (random.nextFloat() * 2 - 1) * jitter, ty = hero.y + (random.nextFloat() * 2 - 1) * jitter;
                if (rock) circleHazard("rock", tx, ty, mc.rockRadiusTiles * T, mc.rockWarning, m.attack(), mc.rockMultiplier);
                else circleHazard("bolt", tx, ty, mc.strikeRadiusTiles * T, mc.strikeWarning, m.attack(), mc.strikeMultiplier);
                m.channelT = m.channelLeft > 0 ? (rock ? mc.rockInterval : mc.strikeInterval) : (rock ? mc.rockWarning : mc.strikeWarning);
                return;
            }
            case "firespin": {
                m.stateT -= dt;
                m.spinAngle += mc.spinTurnSpeed * m.orbitSign * dt;
                m.dirX = (float) Math.cos(m.spinAngle);
                m.dirY = (float) Math.sin(m.spinAngle);
                while (m.channelT <= 0) {
                    m.channelT += mc.spinRate;
                    float speed = mc.spinProjectileSpeedTiles * T;
                    int dmg = Math.max(1, Math.round(m.attack() * mc.spinMultiplier));
                    mobs.shoot(m, m.x, m.centerY(), m.spinAngle, speed, dmg, "fire");
                    mobs.shoot(m, m.x, m.centerY(), m.spinAngle + 3.1416f, speed, dmg, "fire");
                }
                if (m.stateT <= 0) m.enter(Monster.State.RECOVER, 1.1f);
                return;
            }
            case "bloodpool": {
                if (m.channelT > 0) return;
                if (m.channelLeft > 0) {
                    m.channelLeft = 0;
                    mobs.aimAtHero(m);
                    float base = (float) Math.atan2(m.dirY, m.dirX);
                    float spread = (float) Math.toRadians(mc.spearSpreadDegrees);
                    for (int i = 0; i < mc.spearCount; i++) {
                        float a = base + spread * (i - (mc.spearCount - 1) / 2f);
                        Hazard h = new Hazard(Hazard.Kind.LINE, "spear");
                        h.x = m.x;
                        h.y = m.y;
                        h.x2 = m.x + (float) Math.cos(a) * mc.spearLengthTiles * T;
                        h.y2 = m.y + (float) Math.sin(a) * mc.spearLengthTiles * T;
                        h.radius = mc.spearWidthTiles * T / 2f;
                        h.delay = h.warning = mc.spearWarning;
                        h.damage = m.attack();
                        h.multiplier = mc.spearMultiplier;
                        hazards.add(h);
                    }
                    m.channelT = mc.spearWarning + 0.3f;
                    return;
                }
                m.enter(Monster.State.RECOVER, 0.4f);
                return;
            }
            default:
                m.enter(Monster.State.RECOVER, 0.5f);
        }
    }

    void circleHazard(String style, float x, float y, float radius, float warning, int damage, float mult) {
        Hazard h = new Hazard(Hazard.Kind.CIRCLE, style);
        h.x = x;
        h.y = y;
        h.radius = radius;
        h.delay = h.warning = warning;
        h.damage = damage;
        h.multiplier = mult;
        hazards.add(h);
    }

    /** 실명 (해골 사제): 쿨타임이 지났으면 화면을 어둡게 한다. */
    void blind(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        // 실명은 뼈 가시를 쓰는 보스(선우 버전 리치)만 건다
        if (m.def.patterns == null || !m.def.patterns.contains("bonespike") || blindCd > 0 || mc.blindSeconds <= 0) return;
        g.blindT = mc.blindSeconds;
        blindCd = mc.blindCooldown;
        events.add(GameEvent.toast("앞이 캄캄해졌다! 바닥의 빛을 보고 피하자"));
    }

    /** 피웅덩이: 용사 발밑 하나와 방 안 아무 데나 몇 개. */
    void spawnPools(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        Room r = g.fightRoom();
        for (int i = 0; i < mc.poolCount; i++) {
            float px = hero.x, py = hero.y;
            if (i > 0 && r != null) {
                for (int tries = 0; tries < 30; tries++) {
                    int tx = r.x0 + 2 + random.nextInt(Math.max(1, r.x1 - r.x0 - 3));
                    int ty = r.y0 + 2 + random.nextInt(Math.max(1, r.y1 - r.y0 - 3));
                    if (!r.inside(tx, ty)) continue;
                    px = center(tx);
                    py = center(ty);
                    break;
                }
            }
            Hazard h = new Hazard(Hazard.Kind.POOL, "blood");
            h.x = px;
            h.y = py;
            h.startRadius = h.radius = mc.poolStartRadiusTiles * T;
            h.maxRadius = mc.poolMaxRadiusTiles * T;
            h.grow = mc.poolGrowTime;
            h.delay = h.warning = 0.35f;
            h.life = mc.poolLife;
            hazards.add(h);
        }
    }

    /** 바닥 공격: 예고가 끝나면 터지고, 피웅덩이는 남아서 밟으면 닳고 느려진다. */
    public void updateHazards(float dt) {
        Balance.MonsterCfg mc = balance.monster;
        boolean inPool = false;
        for (Hazard h : hazards) {
            if (h.dead) continue;
            h.age += dt;
            if (h.delay > 0) {
                h.delay -= dt;
                if (h.delay > 0) continue;
                h.triggered = true;
                if (h.kind == Hazard.Kind.EYE) {
                    eyeOpens(h);
                    h.dead = true;
                    if (g.heroJustDied()) return;
                    continue;
                }
                if (h.kind != Hazard.Kind.POOL) {
                    if (h.covers(hero.x, hero.y, hero.hw * 0.6f)) hitHero(h.damage, h.multiplier, h.x, h.y);
                    GameEvent fx = GameEvent.fx(GameEvent.Type.HAZARD_FX, h.x, h.y);
                    fx.text = h.style;
                    fx.size = h.radius;
                    fx.dirX = h.x2;
                    fx.dirY = h.y2;
                    events.add(fx);
                    events.add(GameEvent.sound("bolt".equals(h.style) ? "cast" : "slam"));
                    h.dead = true;
                    if (g.heroJustDied()) return;
                    continue;
                }
            }
            if (h.kind == Hazard.Kind.POOL) {
                h.life -= dt;
                float g = Math.min(1f, (h.age - h.warning) / Math.max(0.01f, h.grow));
                h.radius = h.startRadius + (h.maxRadius - h.startRadius) * Math.max(0f, g);
                if (h.life <= 0) h.dead = true;
                else if (h.covers(hero.x, hero.y, 0)) inPool = true;
            }
        }
        hazards.removeIf(h -> h.dead);
        poolTextCd -= dt;
        if (inPool) {
            if (hero.slowT <= 0.2f) hero.slowT = mc.slowSeconds;
            if (!g.devGodMode) {
                poolAcc += rules.maxHp(state) * mc.poolDamageRatio * dt;
                int take = (int) poolAcc;
                if (take > 0) {
                    poolAcc -= take;
                    poolShown += take;
                    state.hp -= take;
                    if (state.hp <= 0) {
                        g.die();
                        return;
                    }
                }
                if (poolTextCd <= 0 && poolShown > 0) {
                    poolTextCd = 0.6f;
                    events.add(GameEvent.floatText(hero.x, hero.y + 40, String.valueOf((int) poolShown), GameEvent.Tone.HERO_HIT));
                    poolShown = 0;
                }
            }
        }
    }

    /** 눈알이 뜬다: 용사가 그쪽을 보고 있으면 (구르는 중이 아니면) 지금 체력의 절반을 잃고 굳는다. */
    void eyeOpens(Hazard h) {
        Balance.MonsterCfg mc = balance.monster;
        GameEvent fx = GameEvent.fx(GameEvent.Type.HAZARD_FX, h.x, h.y);
        fx.text = h.style;
        fx.size = h.radius;
        events.add(fx);
        events.add(GameEvent.sound("cast"));
        float dx = h.x - hero.x, dy = h.y - hero.centerY(), l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 0.01f || hero.rolling() || hero.invuln > 0 || g.devGodMode) return;
        float cos = (dx * hero.aimX + dy * hero.aimY) / l;
        if (cos < (float) Math.cos(Math.toRadians(mc.eyeAngleDegrees / 2f))) return;
        int take = state.hp / 2; // 절반이라 이것만으로는 쓰러지지 않는다
        state.hp -= take;
        hero.stunT = mc.eyeStun;
        hero.flash = 0.3f;
        hero.invuln = Math.max(hero.invuln, 0.4f);
        events.add(GameEvent.floatText(hero.x, hero.y + 40, String.valueOf(take), GameEvent.Tone.HERO_HIT));
        events.add(GameEvent.toast("눈이 마주쳤다! 몸이 굳는다"));
        events.add(GameEvent.sound("hurt"));
        GameEvent hit = GameEvent.fx(GameEvent.Type.HIT_FX, hero.x, hero.centerY());
        hit.tone = GameEvent.Tone.HERO_HIT;
        events.add(hit);
    }

    /** 마왕이 처음 쓰러지면: 무적으로 변신하며 체력이 다시 차고 2페이즈가 된다. */
    public void startPhaseShift(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        m.phase2 = true;
        m.maxHp = Math.round(m.def.hp * mc.finalPhase2HpMultiplier);
        m.hp = 1;
        m.attackMul = mc.finalPhase2AttackMultiplier;
        m.shield = m.shieldMax = 0;
        m.rushLeft = 0;
        m.enter(Monster.State.PHASE_SHIFT, mc.phaseShiftSeconds);
        hazards.clear();
        g.blindT = 0;
        hero.stunT = 0;
        projectiles.clear();
        g.shots.clear();
        speech(m, "이번 용사는 강하군. 나도 본격적으로 해주마!");
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_PHASE2);
        e.text = m.def.name;
        e.x = m.x;
        e.y = m.centerY();
        events.add(e);
        events.add(GameEvent.sound("roar"));
    }

    public void speech(Monster m, String text) {
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_SPEECH);
        e.text = text;
        e.x = m.x;
        e.y = m.y + m.hurtRadius * 3.2f;
        events.add(e);
    }

    /** 연속 베기 한 번 예고: 그때 용사 쪽을 다시 노린다. */
    void comboStep(Monster m) {
        float dx = hero.x - m.x, dy = hero.y - m.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 0.01f) {
            m.dirX = dx / l;
            m.dirY = dy / l;
        }
        float t = balance.monster.comboTelegraph;
        // 첫 번째는 조금 길게 예고한다
        m.enter(Monster.State.COMBO_TELEGRAPH, m.comboLeft == balance.monster.comboHits ? t * 1.6f : t);
    }

    /** 너무 가까우면 용사 반대쪽 멀리 순간이동한다 (마족병, 구석에 몰린 보스). */
    boolean blinkAway(Monster m) {
        if (!m.boss) m.blinkCd = balance.monster.blinkCooldown;
        Room r = g.fightRoom();
        if (r == null) return false;
        for (int tries = 0; tries < 40; tries++) {
            int tx = r.x0 + 2 + random.nextInt(Math.max(1, r.x1 - r.x0 - 3));
            int ty = r.y0 + 2 + random.nextInt(Math.max(1, r.y1 - r.y0 - 3));
            if (!r.inside(tx, ty)) continue;
            float px = center(tx), py = center(ty);
            float hx = px - hero.x, hy = py - hero.y;
            if (hx * hx + hy * hy < (4.5f * T) * (4.5f * T)) continue;
            if (!Physics.free(map, px, py, m, T)) continue;
            GameEvent a = GameEvent.fx(GameEvent.Type.BLINK_FX, m.x, m.centerY());
            events.add(a);
            m.x = px;
            m.y = py;
            events.add(GameEvent.fx(GameEvent.Type.BLINK_FX, m.x, m.centerY()));
            events.add(GameEvent.sound("warp"));
            m.attackCd = Math.max(m.attackCd, 0.5f);
            return true;
        }
        return false;
    }
}
