package com.yongsa.model;

import com.yongsa.combat.Attack;
import com.yongsa.combat.Facing;
import com.yongsa.combat.HeroActor;
import com.yongsa.combat.Monster;
import com.yongsa.combat.Physics;
import com.yongsa.combat.Projectile;
import com.yongsa.config.Balance;
import com.yongsa.config.Balance.DungeonDef;
import com.yongsa.world.Door;
import com.yongsa.world.Prop;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;
import com.yongsa.world.VeinGenerator;
import com.yongsa.world.WorldMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Random;

/**
 * 게임 한 판을 진행한다. 화면은 이동·공격·상호작용 요청(Controls)만 보내고,
 * GameSession 이 규칙을 불러 결과를 이벤트(GameEvent)로 돌려준다.
 * 그리기 코드는 없어서 화면 없이 테스트할 수 있다 (NFR-05).
 */
public class GameSession {
    public final Balance balance;
    public final Rules rules;
    public final EnhanceService enhance;
    public final ForgeService forge;
    public final AbsorbService absorb;
    public final GameState state;
    public final WorldMap map;
    public final HeroActor hero = new HeroActor();
    public final List<Monster> monsters = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final Random random;

    private final int T;
    private final List<GameEvent> events = new ArrayList<>();

    /** 마지막으로 들어간 방 (통로에 있으면 직전 방). */
    private Room currentRoom;
    /** 용사가 지금 서 있는 방, 통로면 null. */
    private Room heroRoom;
    private Room fightRoom;
    private float entryX, entryY, outsideX, outsideY;
    private float hpAcc;
    private boolean unsealOffered;
    private Attack basicAttack;
    private float lockedGateMsgCd;
    private boolean justDied;

    /** 개발용: 맞아도 체력이 줄지 않는다. */
    public boolean devGodMode;
    /** 개발용: 다시 싸우려고 되살린 보스의 던전 (저장하지 않는다). 흡수한 힘과 열린 길은 그대로다. */
    private final Set<String> revived = new HashSet<>();

    /** HUD 에 보여줄 상호작용 안내 (없으면 null). */
    public String prompt;
    public float promptX, promptY;
    private Prop targetProp;
    private Vein targetVein;
    private boolean targetPortal;

    public GameSession(Balance balance, WorldMap map, GameState state, long seed) {
        this.balance = balance;
        this.rules = new Rules(balance);
        this.enhance = new EnhanceService(balance);
        this.forge = new ForgeService(balance);
        this.absorb = new AbsorbService(balance);
        this.state = state;
        this.map = map;
        this.random = new Random(seed);
        this.T = balance.tileSize;

        map.updateGates(state);
        applyVeinOverlay();
        if (state.resumeX == 0 && state.resumeY == 0) {
            placeAtCampSpawn();
        } else {
            hero.x = state.resumeX;
            hero.y = state.resumeY;
        }
        if (state.hp <= 0) state.hp = rules.maxHp(state);
        heroRoom = map.roomAt(tileX(hero.x), tileY(hero.y));
        currentRoom = heroRoom != null ? heroRoom : map.room("CAMP");
        if (heroRoom != null) {
            Door d = nearestDoor(heroRoom);
            setEntry(d);
        }
    }

    // ───────────────────────── 위치 도우미

    private int tileX(float px) {
        return (int) Math.floor(px / T);
    }

    private int tileY(float py) {
        return (int) Math.floor(py / T);
    }

    private float center(int tile) {
        return tile * T + T / 2f;
    }

    public void placeAtCampSpawn() {
        Room camp = map.room("CAMP");
        hero.x = center((int) camp.centerTileX());
        hero.y = center((int) camp.centerTileY() - 3);
        hero.facing = Facing.DOWN;
    }

    public Room heroRoom() {
        return heroRoom;
    }

    public Room currentRoom() {
        return currentRoom;
    }

    public boolean fighting() {
        return fightRoom != null;
    }

    public boolean inCamp() {
        return heroRoom != null && heroRoom.type == RoomType.CAMP;
    }

    public boolean inDungeon() {
        return currentRoom != null && currentRoom.dungeonId != null && !inCamp();
    }

    public Monster activeBoss() {
        for (Monster m : monsters) if (m.boss && !m.dead) return m;
        return null;
    }

    public List<GameEvent> pollEvents() {
        List<GameEvent> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    public String nextTask() {
        return NextTask.describe(state, balance, state.room("L1").visited);
    }

    // ───────────────────────── 한 프레임

    public void update(float dt, Controls c) {
        state.stats.playSeconds += dt;
        lockedGateMsgCd -= dt;
        justDied = false;
        updateHero(dt, c);
        updateRooms();
        updateMonsters(dt);
        if (!justDied) updateProjectiles(dt);
        if (!justDied) resolveFight();
        if (inCamp()) {
            hpAcc += rules.maxHp(state) * balance.hero.campRegenFractionPerSec * dt;
            int add = (int) hpAcc;
            if (add > 0) {
                hpAcc -= add;
                state.hp = Math.min(rules.maxHp(state), state.hp + add);
            }
            if (!unsealOffered && absorb.canUnseal(state)) {
                unsealOffered = true;
                events.add(new GameEvent(GameEvent.Type.UNSEAL_READY));
            }
        }
        findInteraction();
        if (c.interact) interact();
    }

    private void updateHero(float dt, Controls c) {
        hero.attackCd -= dt;
        hero.rollCd -= dt;
        hero.mineCd -= dt;
        hero.invuln -= dt;
        hero.flash -= dt;
        hero.swingT -= dt;
        hero.mineSwingT -= dt;

        if (hero.kbT > 0) {
            Physics.move(map, hero, hero.kbVX * dt, hero.kbVY * dt, T);
            hero.kbT -= dt;
        }

        if (hero.rolling()) {
            // 처음에 빠르고 끝으로 갈수록 느려진다 (평균 속도 = 거리 / 시간)
            float total = balance.attack.rollTime;
            float p = 1f - hero.rollT / total;
            float speed = balance.attack.rollDistanceTiles * T / total * 2f * (1f - p);
            Physics.move(map, hero, hero.rollX * speed * dt, hero.rollY * speed * dt, T);
            hero.rollT -= dt;
            hero.walkTime += dt;
            return;
        }

        float mx = c.moveX, my = c.moveY;
        float len = (float) Math.sqrt(mx * mx + my * my);
        hero.moving = len > 0.01f;
        if (hero.moving) {
            mx /= len;
            my /= len; // 대각선도 직선과 같은 속도 (FR-맵-01)
            hero.aimX = mx;
            hero.aimY = my;
            hero.facing = Facing.of(mx, my);
            float speed = balance.hero.moveSpeedTiles * T;
            Physics.move(map, hero, mx * speed * dt, my * speed * dt, T);
            hero.walkTime += dt;
        }

        if (c.roll && hero.rollCd <= 0) roll(mx, my);
        else if (c.attack && hero.attackCd <= 0) basic();
    }

    private void basic() {
        hero.attackCd = balance.attack.basicCooldown * (rules.hasAbility(state, "skyfall") ? 0.8f : 1f);
        hero.swingKind = 0;
        hero.swingT = 0.18f;
        basicAttack = new Attack(Attack.Kind.BASIC, balance.attack.basicMultiplier);
        float range = basicRange();
        float half = (float) Math.toRadians(balance.attack.basicArcDegrees / 2f);
        float hx = hero.x, hy = hero.centerY();
        for (Monster m : monsters) {
            if (m.dead) continue;
            float dx = m.x - hx, dy = m.centerY() - hy;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist - m.hurtRadius > range) continue;
            if (dist > m.hurtRadius + 6) {
                float cos = (dx * hero.aimX + dy * hero.aimY) / dist;
                if (Math.acos(Math.max(-1, Math.min(1, cos))) > half) continue;
            }
            if (basicAttack.tryHit(m)) damageMonster(m, basicAttack.multiplier);
        }
        events.add(GameEvent.sound("swing"));
    }

    public float basicRange() {
        float r = balance.attack.basicRangeTiles * T;
        if (rules.hasAbility(state, "blackiron")) r *= 1.3f;
        return r;
    }

    /** 구르기: 움직이는 방향(없으면 바라보는 방향)으로 굴러서 공격을 피한다. */
    private void roll(float mx, float my) {
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len > 0.01f) {
            hero.rollX = mx;
            hero.rollY = my;
        } else {
            hero.rollX = hero.aimX;
            hero.rollY = hero.aimY;
        }
        hero.rollCd = balance.attack.rollTime + balance.attack.rollCooldown;
        hero.rollT = hero.rollTotal = balance.attack.rollTime;
        hero.invuln = Math.max(hero.invuln, balance.attack.rollInvulnTime);
        hero.swingT = 0;
        events.add(GameEvent.sound("dash"));
    }

    private int roll(int base, float mult) {
        float spread = balance.attack.damageSpreadMin
                + random.nextFloat() * (balance.attack.damageSpreadMax - balance.attack.damageSpreadMin);
        return Math.max(1, Math.round(base * mult * spread));
    }

    private void damageMonster(Monster m, float mult) {
        if (m.finalBoss && !Rules.HOLY_AWAKENED.equals(state.equipped().defId)) {
            if (m.immuneMsgCd <= 0) {
                m.immuneMsgCd = 1.2f;
                events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2.2f,
                        "이 검으로는 상처를 낼 수 없다", GameEvent.Tone.BLOCKED));
                events.add(GameEvent.sound("block"));
            }
            return;
        }
        int dmg = roll(rules.attack(state), mult);
        m.hp -= dmg;
        m.flash = 0.15f;
        // 공격을 준비하거나 돌진하는 중에는 밀리지 않는다: 연타로 공격을 끊을 수 없다
        if (!m.boss && !m.committed()) m.knockback(m.x - hero.x, m.y - hero.y, balance.monster.knockbackTiles * T);
        if (m.boss && !m.enraged && m.hp > 0 && m.hp <= m.maxHp * balance.monster.phase2HpRatio) {
            m.enraged = true;
            m.patternCd = Math.min(m.patternCd, 0.6f);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_ENRAGED);
            e.text = m.def.name;
            e.x = m.x;
            e.y = m.centerY();
            events.add(e);
            events.add(GameEvent.sound("roar"));
        }
        events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2f, String.valueOf(dmg), GameEvent.Tone.MONSTER_HIT));
        events.add(GameEvent.sound("hit"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.HIT_FX, m.x, m.centerY());
        fx.tone = GameEvent.Tone.MONSTER_HIT;
        float ddx = m.x - hero.x, ddy = m.centerY() - hero.centerY(), dl = (float) Math.sqrt(ddx * ddx + ddy * ddy);
        fx.dirX = dl > 0.01f ? ddx / dl : hero.aimX;
        fx.dirY = dl > 0.01f ? ddy / dl : hero.aimY;
        fx.boss = m.boss;
        events.add(fx);
        if (m.hp <= 0) {
            m.hp = 0;
            m.dead = true;
            state.stats.monstersKilled++;
            GameEvent died = GameEvent.fx(GameEvent.Type.MONSTER_DIED, m.x, m.y);
            died.sprite = m.def.sprite;
            died.size = m.finalBoss ? 2.0f : m.boss ? 1.7f : 1f;
            died.boss = m.boss;
            died.flip = m.dirX < -0.2f;
            events.add(died);
            if (rules.hasAbility(state, "bloodmoon")) {
                int heal = Math.max(1, Math.round(rules.maxHp(state) * 0.05f));
                state.hp = Math.min(rules.maxHp(state), state.hp + heal);
                events.add(GameEvent.floatText(hero.x, hero.y + 40, "+" + heal, GameEvent.Tone.HEAL));
            }
        }
    }

    private void hitHero(int base, float mult, float fromX, float fromY) {
        if (hero.invuln > 0) return;
        if (devGodMode) {
            hero.invuln = balance.hero.invulnAfterHit;
            hero.flash = balance.hero.invulnAfterHit;
            events.add(GameEvent.floatText(hero.x, hero.y + 40, "0", GameEvent.Tone.BLOCKED));
            return;
        }
        int dmg = roll(base, mult);
        state.hp -= dmg;
        hero.invuln = balance.hero.invulnAfterHit;
        hero.flash = balance.hero.invulnAfterHit;
        hero.knockback(hero.x - fromX, hero.y - fromY, balance.hero.knockbackTiles * T);
        events.add(GameEvent.floatText(hero.x, hero.y + 40, String.valueOf(dmg), GameEvent.Tone.HERO_HIT));
        events.add(GameEvent.sound("hurt"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.HIT_FX, hero.x, hero.centerY());
        fx.tone = GameEvent.Tone.HERO_HIT;
        events.add(fx);
        if (state.hp <= 0) die();
    }

    private void die() {
        state.hp = 0;
        justDied = true;
        state.stats.deaths++;
        if (fightRoom != null) {
            fightRoom.setFightLock(false); // 싸우던 방만 처음 상태로 (A8)
            fightRoom = null;
        }
        monsters.clear();
        projectiles.clear();
        placeAtCampSpawn();
        heroRoom = currentRoom = map.room("CAMP");
        hero.rollT = 0;
        hero.kbT = 0;
        state.hp = rules.maxHp(state);
        events.add(new GameEvent(GameEvent.Type.HERO_DIED));
    }

    // ───────────────────────── 방

    private void updateRooms() {
        Room r = map.roomAt(tileX(hero.x), tileY(hero.y));
        if (r != heroRoom) {
            Room prevDungeonRoom = currentRoom;
            heroRoom = r;
            if (r != null && r != currentRoom) {
                currentRoom = r;
                setEntry(nearestDoor(r));
                state.room(r.id).visited = true;
                if (r.type == RoomType.ORE) ensureVeins(r);
                if (r.dungeonId != null && (prevDungeonRoom == null || prevDungeonRoom.dungeonId == null)) {
                    DungeonDef d = balance.dungeon(r.dungeonId);
                    GameEvent e = new GameEvent(GameEvent.Type.ENTERED_DUNGEON);
                    e.dungeonId = d.id;
                    e.text = NextTask.dungeonWord(d.id) + " 던전 · " + d.name;
                    events.add(e);
                }
                if (r.type == RoomType.BOSS && !state.bossDefeated(r.dungeonId)) {
                    events.add(GameEvent.toast("보스방이다. 권장 무기: " + balance.dungeon(r.dungeonId).recommended));
                }
            }
        }
        if (fightRoom == null && heroRoom != null && heroRoom.type.hasFight()
                && !state.room(heroRoom.id).cleared && !hasPortal(heroRoom)
                && heroRoom.insideWithMargin(tileX(hero.x), tileY(hero.y), 1)) {
            startFight(heroRoom);
        }
    }

    private void setEntry(Door d) {
        if (d == null) {
            entryX = outsideX = hero.x;
            entryY = outsideY = hero.y;
            return;
        }
        entryX = center(d.insideX);
        entryY = center(d.insideY);
        outsideX = center(d.outsideX);
        outsideY = center(d.outsideY);
    }

    private Door nearestDoor(Room r) {
        Door best = null;
        float bestD = Float.MAX_VALUE;
        for (Door d : r.doors) {
            float dx = center(d.insideX) - hero.x, dy = center(d.insideY) - hero.y;
            float dd = dx * dx + dy * dy;
            if (dd < bestD) {
                bestD = dd;
                best = d;
            }
        }
        return best;
    }

    private void startFight(Room r) {
        fightRoom = r;
        r.setFightLock(true);
        monsters.clear();
        projectiles.clear();
        DungeonDef d = balance.dungeon(r.dungeonId);
        if (r.type == RoomType.MOB) {
            for (int i = 0; i < d.mobsPerRoom; i++) spawn(r, d.mob, false, false, false);
        } else if (r.type == RoomType.BOSS) {
            spawn(r, d.boss, true, false, true);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_FIGHT_START);
            e.text = d.boss.name;
            events.add(e);
        } else if (r.type == RoomType.FINAL) {
            spawn(r, balance.finalBoss, true, true, true);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_FIGHT_START);
            e.text = balance.finalBoss.name;
            e.boss = true;
            events.add(e);
        }
        events.add(new GameEvent(GameEvent.Type.ROOM_LOCKED));
        events.add(GameEvent.sound("door"));
    }

    private void spawn(Room r, Balance.MonsterDef def, boolean boss, boolean fin, boolean farSide) {
        Monster m = makeMonster(r, def, boss, fin, farSide);
        if (m != null) monsters.add(m);
    }

    /** 방 안 빈 자리에 몬스터를 만든다 (목록에 넣지는 않는다). 처음 잠깐은 마법진만 보이고 움직이지 않는다. */
    private Monster makeMonster(Room r, Balance.MonsterDef def, boolean boss, boolean fin, boolean farSide) {
        Monster m = new Monster(def, boss, fin, T);
        boolean placed = false;
        for (int tries = 0; tries < 200; tries++) {
            int tx, ty;
            if (farSide) {
                tx = (int) r.centerTileX();
                ty = (int) r.centerTileY();
                // 문 반대쪽으로 조금 밀어 둔다
                float dx = r.centerTileX() - tileX(hero.x), dy = r.centerTileY() - tileY(hero.y);
                float l = (float) Math.sqrt(dx * dx + dy * dy);
                if (l > 0) {
                    tx += Math.round(dx / l * 3);
                    ty += Math.round(dy / l * 3);
                }
            } else {
                tx = r.x0 + 2 + random.nextInt(r.x1 - r.x0 - 3);
                ty = r.y0 + 2 + random.nextInt(r.y1 - r.y0 - 3);
            }
            float px = center(tx), py = center(ty);
            float hx = px - hero.x, hy = py - hero.y;
            if (!farSide && hx * hx + hy * hy < (5f * T) * (5f * T)) continue;
            if (!Physics.free(map, px, py, m, T)) continue;
            m.x = px;
            m.y = py;
            placed = true;
            break;
        }
        if (!placed && !farSide) return null;
        if (!placed) {
            m.x = center((int) r.centerTileX());
            m.y = center((int) r.centerTileY());
        }
        m.attackCd = 0.6f + random.nextFloat() * 0.6f;
        m.patternCd = 2.5f;
        m.orbitSign = random.nextBoolean() ? 1 : -1;
        m.enter(Monster.State.SPAWN, balance.monster.spawnSeconds * (boss ? 1.4f : 1f) + random.nextFloat() * 0.25f);
        return m;
    }

    private void resolveFight() {
        if (fightRoom == null) return;
        for (Monster m : monsters) if (!m.dead) return;
        Room r = fightRoom;
        fightRoom = null;
        r.setFightLock(false);
        monsters.clear();
        projectiles.clear();
        state.room(r.id).cleared = true;
        events.add(GameEvent.sound("door"));
        events.add(new GameEvent(GameEvent.Type.ROOM_CLEARED));
        if (r.type == RoomType.BOSS) {
            DungeonDef d = balance.dungeon(r.dungeonId);
            revived.remove(d.id);
            int gain = absorb.absorbBoss(state, d.id);
            OreType ore = OreType.valueOf(d.ore);
            state.addOre(ore, d.bossOreReward);
            map.updateGates(state);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_DEFEATED);
            e.dungeonId = d.id;
            e.hpGain = gain;
            e.ore = ore;
            e.amount = d.bossOreReward;
            e.text = d.boss.name;
            int idx = indexOf(d.id);
            if (idx + 1 < AbsorbService.BOSS_ORDER.length) {
                e.opened = NextTask.dungeonWord(AbsorbService.BOSS_ORDER[idx + 1]) + " 던전 · "
                        + balance.dungeon(AbsorbService.BOSS_ORDER[idx + 1]).name;
            } else {
                e.opened = "세 보스의 힘을 모두 흡수했다. 캠프로 돌아가 성검의 봉인을 풀 수 있다";
            }
            events.add(e);
            events.add(new GameEvent(GameEvent.Type.AUTOSAVE));
        } else if (r.type == RoomType.FINAL) {
            state.finalBossDefeated = true;
            events.add(new GameEvent(GameEvent.Type.FINAL_DEFEATED));
            events.add(new GameEvent(GameEvent.Type.AUTOSAVE));
        } else {
            events.add(GameEvent.toast("방을 비웠다! 문이 열렸다"));
        }
    }

    private static int indexOf(String dungeonId) {
        for (int i = 0; i < AbsorbService.BOSS_ORDER.length; i++) if (AbsorbService.BOSS_ORDER[i].equals(dungeonId)) return i;
        return -1;
    }

    // ───────────────────────── 몬스터

    private void updateMonsters(float dt) {
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
            switch (m.state) {
                case CHASE:
                    if (dist > 0.01f) {
                        m.dirX = dx / dist;
                        m.dirY = dy / dist;
                    }
                    if (m.boss) chaseBoss(m, dist, reach, dt, summoned);
                    else chaseMob(m, dist, reach, dt);
                    break;
                case TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        // 예고한 방향으로 친다. 그 사이 걸어서 벗어났으면 빗나간다.
                        if ("brute".equals(m.ai())) Physics.move(map, m, m.dirX * mc.lungeTiles * T, m.dirY * mc.lungeTiles * T, T);
                        meleeStrike(m, reach, 1f);
                        m.enter(Monster.State.RECOVER, m.boss ? 0.45f : "brute".equals(m.ai()) ? 0.6f : 0.35f);
                        m.attackCd = mc.attackCooldown;
                    }
                    break;
                case COMBO_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        Physics.move(map, m, m.dirX * mc.comboStepTiles * T, m.dirY * mc.comboStepTiles * T, T);
                        meleeStrike(m, reach + 0.6f * T, 1.1f);
                        if (--m.comboLeft > 0) {
                            comboStep(m);
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
                        hitHero(m.def.attack, charge ? mc.chargeMultiplier : 1f, m.x, m.y);
                        if (justDied) return;
                    }
                    if (charge && blocked) {
                        // 벽에 부딪혀 잠깐 기절한다: 반격할 기회
                        m.enter(Monster.State.STUNNED, mc.chargeStun);
                        events.add(GameEvent.sound("slam"));
                        GameEvent fx = GameEvent.fx(GameEvent.Type.SLAM_FX, m.x + m.lockX * m.hw, m.y + m.lockY * m.hh);
                        fx.size = T * 1.4f;
                        events.add(fx);
                    } else if (m.dashLeft <= 0.5f || m.stateT <= 0 || blocked) {
                        m.enter(Monster.State.RECOVER, charge ? 0.6f : mc.swoopRecover);
                        m.attackCd = mc.attackCooldown;
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
                        m.attackCd = m.boss ? mc.attackCooldown : mc.castCooldown;
                    }
                    break;
                case SLAM_TELEGRAPH:
                    m.stateT -= dt * speedUp;
                    if (m.stateT <= 0) {
                        float sx = hero.x - m.slamX, sy = hero.y - m.slamY;
                        if (sx * sx + sy * sy <= m.slamRadius * m.slamRadius) {
                            hitHero(m.def.attack, mc.bossSlamMultiplier, m.slamX, m.slamY);
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
                case STUNNED:
                case RECOVER:
                    m.stateT -= dt;
                    if (m.stateT <= 0) m.state = Monster.State.CHASE;
                    break;
                default:
                    break;
            }
            if (justDied) return; // die() 가 몬스터 목록을 비웠다
        }
        monsters.addAll(summoned);
        separate();
    }

    /** 잡몹: 종류마다 다르게 다가오고 공격한다. */
    private void chaseMob(Monster m, float dist, float reach, float dt) {
        Balance.MonsterCfg mc = balance.monster;
        switch (m.ai()) {
            case "swoop": {
                // 용사 둘레를 빙글빙글 돌다가 방향을 정하고 덮친다.
                float orbit = mc.orbitTiles * T;
                if (m.attackCd <= 0 && dist < orbit * 1.8f) {
                    m.lockX = m.dirX;
                    m.lockY = m.dirY;
                    m.enter(Monster.State.SWOOP_TELEGRAPH, mc.swoopTelegraph);
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
                if (dist < keep * 0.6f && m.blinkCd <= 0 && blinkAway(m)) return;
                if (m.attackCd <= 0 && dist < mc.castRangeTiles * T) {
                    m.castKind = "cast";
                    m.enter(Monster.State.CAST_TELEGRAPH, mc.castTelegraph);
                    return;
                }
                float dir = dist < keep ? -1f : dist > keep * 1.4f ? 1f : 0f;
                if (dir != 0) {
                    Physics.move(map, m, m.dirX * dir * m.speed * dt, m.dirY * dir * m.speed * dt, T);
                    m.walkTime += dt;
                }
                return;
            }
            default:
                // melee, brute: 다가가서 휘두른다
                if (dist <= reach * ("brute".equals(m.ai()) ? 1.6f : 1f) && m.attackCd <= 0) {
                    m.enter(Monster.State.TELEGRAPH, mc.telegraphSeconds * ("brute".equals(m.ai()) ? 1.2f : 1f));
                } else if (dist > reach * 0.7f) {
                    Physics.move(map, m, m.dirX * m.speed * dt, m.dirY * m.speed * dt, T);
                    m.walkTime += dt;
                }
        }
    }

    /** 보스: 다가가 근접 공격을 하다가, 패턴 쿨타임이 차면 고유 패턴을 하나 고른다. */
    private void chaseBoss(Monster m, float dist, float reach, float dt, List<Monster> summoned) {
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
            Physics.move(map, m, m.dirX * m.speed * dt * (m.enraged ? 1.15f : 1f), m.dirY * m.speed * dt * (m.enraged ? 1.15f : 1f), T);
            m.walkTime += dt;
        }
    }

    private String pickPattern(Monster m) {
        List<String> pool = new ArrayList<>();
        if (m.def.patterns != null) pool.addAll(m.def.patterns);
        if (m.enraged && m.def.phase2Patterns != null) pool.addAll(m.def.phase2Patterns);
        if (pool.isEmpty()) return "slam";
        if (pool.size() > 1) pool.remove(m.lastPattern);
        return pool.get(random.nextInt(pool.size()));
    }

    private boolean startPattern(Monster m, String p, List<Monster> summoned) {
        Balance.MonsterCfg mc = balance.monster;
        switch (p) {
            case "slam":
                m.slamX = hero.x;
                m.slamY = hero.y;
                m.slamRadius = mc.bossSlamRadiusTiles * T * (m.finalBoss ? 1.3f : 1f);
                m.enter(Monster.State.SLAM_TELEGRAPH, mc.bossSlamTelegraph);
                return true;
            case "charge":
                m.lockX = m.dirX;
                m.lockY = m.dirY;
                m.enter(Monster.State.CHARGE_TELEGRAPH, mc.chargeTelegraph);
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
            case "summon": {
                int alive = 0;
                for (Monster o : monsters) if (!o.dead && !o.boss) alive++;
                if (alive >= mc.summonCount) return false;
                Room r = fightRoom;
                if (r == null) return false;
                DungeonDef d = balance.dungeon(r.dungeonId);
                for (int i = 0; i < mc.summonCount; i++) {
                    Monster s = makeMonster(r, d.mob, false, false, false);
                    if (s != null) summoned.add(s);
                }
                events.add(GameEvent.toast(m.def.name + "이(가) 부하를 불렀다!"));
                events.add(GameEvent.sound("roar"));
                m.enter(Monster.State.RECOVER, 0.8f);
                return true;
            }
            default:
                return false;
        }
    }

    /** 연속 베기 한 번 예고: 그때 용사 쪽을 다시 노린다. */
    private void comboStep(Monster m) {
        float dx = hero.x - m.x, dy = hero.y - m.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 0.01f) {
            m.dirX = dx / l;
            m.dirY = dy / l;
        }
        float t = balance.monster.comboTelegraph;
        // 첫 번째는 조금 길게 예고한다
        m.enter(Monster.State.COMBO_TELEGRAPH, m.comboLeft == balance.monster.comboHits ? t * 1.6f : t);
    }

    /** 앞쪽 부채꼴 안에 용사가 있으면 맞힌다. */
    private void meleeStrike(Monster m, float reach, float mult) {
        float dx = hero.x - m.x, dy = hero.y - m.y;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float dot = dist > 0.01f ? (dx * m.dirX + dy * m.dirY) / dist : 1f;
        if (dist <= reach + 4 && dot > 0.2f) hitHero(m.def.attack, mult, m.x, m.y);
        events.add(GameEvent.sound("monsterAttack"));
    }

    /** 마족병: 너무 가까우면 용사 반대쪽으로 순간이동한다. */
    private boolean blinkAway(Monster m) {
        m.blinkCd = balance.monster.blinkCooldown;
        Room r = fightRoom;
        if (r == null) return false;
        for (int tries = 0; tries < 30; tries++) {
            int tx = r.x0 + 2 + random.nextInt(Math.max(1, r.x1 - r.x0 - 3));
            int ty = r.y0 + 2 + random.nextInt(Math.max(1, r.y1 - r.y0 - 3));
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

    /** 모은 투사체를 쏜다: cast(한 발), volley(부채꼴), ring(사방). */
    private void fire(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        float speed = mc.projectileSpeedTiles * T * (m.boss ? 1.1f : 1f);
        float base = (float) Math.atan2(m.dirY, m.dirX);
        float sy = m.centerY();
        String kind = m.castKind == null ? "cast" : m.castKind;
        if ("ring".equals(kind)) {
            int n = mc.ringCount;
            float off = random.nextFloat() * 6.283f;
            for (int i = 0; i < n; i++) shoot(m, m.x, sy, off + 6.283f * i / n, speed * 0.8f);
        } else if ("volley".equals(kind)) {
            int n = mc.volleyCount;
            float spread = (float) Math.toRadians(mc.volleySpreadDegrees);
            for (int i = 0; i < n; i++) shoot(m, m.x, sy, base - spread / 2f + spread * i / Math.max(1, n - 1), speed);
        } else {
            shoot(m, m.x, sy, base, speed);
        }
        events.add(GameEvent.sound("cast"));
    }

    private void shoot(Monster m, float x, float y, float angle, float speed) {
        Projectile p = new Projectile();
        p.x = x + (float) Math.cos(angle) * m.hurtRadius;
        p.y = y + (float) Math.sin(angle) * m.hurtRadius;
        p.vx = (float) Math.cos(angle) * speed;
        p.vy = (float) Math.sin(angle) * speed;
        p.radius = balance.monster.projectileRadius * (m.boss ? 1.4f : 1f);
        p.life = 4f;
        p.damage = m.def.attack;
        p.source = m.def.sprite;
        projectiles.add(p);
    }

    private void updateProjectiles(float dt) {
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
                if (justDied) return;
            }
        }
        projectiles.removeIf(p -> p.dead);
    }

    /** 몬스터끼리 겹치지 않게 살짝 민다. */
    private void separate() {
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

    // ───────────────────────── 광맥

    private void ensureVeins(Room r) {
        RoomRecord rec = state.room(r.id);
        if (rec.veins != null) return;
        Balance.MiningCfg mc = balance.mining;
        boolean cleared = state.bossDefeated(r.dungeonId);
        int min = cleared ? mc.veinsMinCleared : mc.veinsMin;
        int max = cleared ? mc.veinsMaxCleared : mc.veinsMax;
        int count = min + random.nextInt(max - min + 1);
        int guarantee = state.firstOreRoomDone ? 0 : mc.firstOreRoomGuarantee;
        OreType ore = OreType.valueOf(balance.dungeon(r.dungeonId).ore);
        rec.veins = VeinGenerator.generate(r, random, count, ore, mc.orePerVeinMin, mc.orePerVeinMax,
                mc.hitsToBreak, guarantee);
        state.firstOreRoomDone = true;
        for (Vein v : rec.veins) map.setBlocked(v.tileX, v.tileY, true);
    }

    private void applyVeinOverlay() {
        for (Room r : map.rooms) {
            if (r.type != RoomType.ORE) continue;
            clearOverlay(r);
            RoomRecord rec = state.rooms.get(r.id);
            if (rec != null && rec.veins != null) {
                for (Vein v : rec.veins) map.setBlocked(v.tileX, v.tileY, true);
            }
        }
    }

    private void clearOverlay(Room r) {
        for (int y = r.y0; y <= r.y1; y++)
            for (int x = r.x0; x <= r.x1; x++) map.setBlocked(x, y, false);
    }

    public List<Vein> veinsOf(Room r) {
        RoomRecord rec = state.rooms.get(r.id);
        return rec == null || rec.veins == null ? null : rec.veins;
    }

    // ───────────────────────── 상호작용

    private void findInteraction() {
        prompt = null;
        targetProp = null;
        targetVein = null;
        targetPortal = false;
        float best = Float.MAX_VALUE;
        if (heroRoom != null && hasPortal(heroRoom) && fightRoom == null) {
            float d = (float) Math.hypot(portalX(heroRoom) - hero.x, portalY(heroRoom) - hero.y);
            if (d < 1.1f * T) {
                targetPortal = true;
                prompt = "E  캠프로 귀환";
                promptX = portalX(heroRoom);
                promptY = portalY(heroRoom) + 1.6f * T;
                return;
            }
        }
        for (Prop p : map.props) {
            float px = Math.max(p.tileX * T, Math.min(hero.x, (p.tileX + p.w) * T));
            float py = Math.max(p.tileY * T, Math.min(hero.y, (p.tileY + p.h) * T));
            float d = (float) Math.hypot(px - hero.x, py - hero.y);
            if (d < 0.9f * T && d < best) {
                best = d;
                targetProp = p;
            }
        }
        if (targetProp != null) {
            switch (targetProp.kind) {
                case FORGE: prompt = "E  대장간"; break;
                case BED: prompt = "E  침대 (회복·저장)"; break;
                default: prompt = "E  모닥불"; break;
            }
            promptX = (targetProp.tileX + targetProp.w / 2f) * T;
            promptY = (targetProp.tileY + targetProp.h) * T + 18;
            return;
        }
        if (heroRoom != null && heroRoom.type == RoomType.ORE) {
            List<Vein> veins = veinsOf(heroRoom);
            if (veins != null) {
                for (Vein v : veins) {
                    float d = (float) Math.hypot(center(v.tileX) - hero.x, center(v.tileY) - hero.y);
                    if (d < 1.35f * T && d < best) {
                        best = d;
                        targetVein = v;
                    }
                }
            }
            if (targetVein != null) {
                prompt = "E  채굴 (" + targetVein.hitsLeft + "번 남음)";
                promptX = center(targetVein.tileX);
                promptY = (targetVein.tileY + 1) * T + 14;
                return;
            }
        }
        // 잠긴 던전 입구 (FR-맵-08)
        for (com.yongsa.world.Link l : map.links) {
            if (!l.gateClosed) continue;
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float dist = (float) Math.hypot(center(d.outsideX) - hero.x, center(d.outsideY) - hero.y);
                float dist2 = (float) Math.hypot(center(d.insideX) - hero.x, center(d.insideY) - hero.y);
                if (Math.min(dist, dist2) < 2.2f * T) {
                    prompt = "FINAL".equals(l.gate) ? "성검의 봉인을 풀어야 열립니다" : "앞 던전의 보스를 잡아야 열립니다";
                    promptX = center(d.tilesX[1]);
                    promptY = center(d.tilesY[1]) + T;
                    return;
                }
            }
        }
    }

    private void interact() {
        if (targetPortal) {
            events.add(new GameEvent(GameEvent.Type.WARP));
            events.add(GameEvent.sound("warp"));
            return;
        }
        if (targetProp != null) {
            switch (targetProp.kind) {
                case FORGE:
                    events.add(new GameEvent(GameEvent.Type.OPEN_FORGE));
                    break;
                case BED:
                    useBed();
                    break;
                default:
                    events.add(GameEvent.toast("따뜻하다. 캠프에 있으면 체력이 천천히 찬다"));
            }
        } else if (targetVein != null) {
            mine(targetVein);
        }
    }

    private void mine(Vein v) {
        if (hero.mineCd > 0) return;
        hero.mineCd = 0.28f;
        hero.mineSwingT = 0.22f;
        float dx = center(v.tileX) - hero.x, dy = center(v.tileY) - hero.y;
        if (Math.abs(dx) + Math.abs(dy) > 1) hero.facing = Facing.of(dx, dy);
        v.hitsLeft--;
        events.add(GameEvent.sound("mine"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.MINE_FX, center(v.tileX), center(v.tileY));
        fx.ore = v.ore;
        fx.size = v.hitsLeft;
        events.add(fx);
        if (v.hitsLeft > 0) return;
        state.addOre(v.ore, v.amount);
        state.stats.oreMined += v.amount;
        veinsOf(heroRoom).remove(v);
        map.setBlocked(v.tileX, v.tileY, false);
        GameEvent e = new GameEvent(GameEvent.Type.ORE_GAINED);
        e.ore = v.ore;
        e.amount = v.amount;
        e.x = center(v.tileX);
        e.y = center(v.tileY) + T / 2f;
        events.add(e);
        events.add(GameEvent.floatText(e.x, e.y, v.ore.displayName + " +" + v.amount, GameEvent.Tone.ORE));
        events.add(GameEvent.sound("ore"));
    }

    /**
     * 침대: 체력을 채우고, 모든 던전의 잡몹과 광맥만 다시 채운다 (FR-던전-05).
     * 보스 처치·던전 개방 기록은 그대로다.
     */
    public void useBed() {
        state.hp = rules.maxHp(state);
        for (Room r : map.rooms) {
            RoomRecord rec = state.room(r.id);
            if (r.type == RoomType.BOSS && state.bossDefeated(r.dungeonId)) continue;
            if (r.type == RoomType.FINAL && state.finalBossDefeated) continue;
            rec.cleared = false;
            if (r.type == RoomType.ORE) {
                rec.veins = null;
                clearOverlay(r);
            }
        }
        events.add(new GameEvent(GameEvent.Type.BED_USED));
        events.add(new GameEvent(GameEvent.Type.AUTOSAVE));
    }

    // ───────────────────────── 메뉴에서 부르는 것

    public boolean canExitDungeon() {
        return inDungeon() && fightRoom == null;
    }

    /** Esc 메뉴의 던전 나가기 (FR-던전-11). 문이 잠긴 전투 중에는 안 된다. */
    public boolean exitDungeon() {
        if (!canExitDungeon()) return false;
        placeAtCampSpawn();
        heroRoom = currentRoom = map.room("CAMP");
        return true;
    }

    /** 보스를 쓰러뜨린 보스방 가운데에는 캠프로 돌아가는 포탈이 생긴다. */
    public boolean hasPortal(Room r) {
        return r.type == RoomType.BOSS && state.bossDefeated(r.dungeonId) && !revived.contains(r.dungeonId);
    }

    // ───────────────────────── 개발용 (F1)

    /**
     * 쓰러뜨린 보스를 다시 싸울 수 있게 되살린다. 이미 흡수한 힘·열린 던전·광물은 그대로 두고,
     * 보스방에 다시 들어가면 싸움이 시작된다. dungeonId 가 "FINAL" 이면 마왕.
     * 싸우는 중이면 되살리지 않는다. 되살렸으면 true.
     */
    public boolean devReviveBoss(String dungeonId) {
        if (fightRoom != null) return false;
        if ("FINAL".equals(dungeonId)) {
            if (!state.finalBossDefeated) return false;
            state.finalBossDefeated = false;
            for (Room r : map.rooms) if (r.type == RoomType.FINAL) state.room(r.id).cleared = false;
            return true;
        }
        if (!state.bossDefeated(dungeonId) || revived.contains(dungeonId)) return false;
        revived.add(dungeonId);
        for (Room r : map.rooms) {
            if (r.type == RoomType.BOSS && dungeonId.equals(r.dungeonId)) state.room(r.id).cleared = false;
        }
        return true;
    }

    /** 되살아나 보스방에서 기다리는 보스인가 (개발 메뉴 표시용). */
    public boolean devRevived(String dungeonId) {
        return revived.contains(dungeonId);
    }

    /** 광물 무한: 모자라면 채워 둔다. */
    public void devFillOres(int amount) {
        for (OreType t : OreType.values()) if (state.ore(t) < amount) state.ores.put(t, amount);
    }

    public float portalX(Room r) {
        return center((int) r.centerTileX());
    }

    public float portalY(Room r) {
        return center((int) r.centerTileY());
    }

    /** 귀환 포탈로 캠프에 돌아간다. */
    public void warpToCamp() {
        if (fightRoom != null) return;
        placeAtCampSpawn();
        heroRoom = currentRoom = map.room("CAMP");
        hero.rollT = 0;
        hero.kbT = 0;
    }

    /** 저장 직전에 이어하기 위치를 정한다 (FR-저장-03, A25). */
    public void prepareSave() {
        if (inCamp()) {
            state.resumeX = hero.x;
            state.resumeY = hero.y;
        } else if (fightRoom != null) {
            state.resumeX = outsideX; // 잠긴 방은 처음 상태로 돌아가고 문 밖에서 시작
            state.resumeY = outsideY;
        } else {
            state.resumeX = entryX;
            state.resumeY = entryY;
        }
        if (state.hp <= 0) state.hp = rules.maxHp(state);
    }

    /** 봉인 해제 장면이 끝나면 부른다. */
    public Weapon unseal() {
        Weapon w = absorb.unseal(state);
        map.updateGates(state);
        return w;
    }

    public void onWeaponsChanged() {
        state.hp = Math.min(state.hp, rules.maxHp(state));
    }

    public void emit(GameEvent e) {
        events.add(e);
    }

    /** 테스트·디버그용: 방을 바로 비운 것으로 만든다. */
    public void debugKillAll() {
        for (Monster m : monsters) {
            m.hp = 0;
            m.dead = true;
        }
    }
}
