package com.yongsa.model;

import com.yongsa.combat.Attack;
import com.yongsa.combat.Facing;
import com.yongsa.combat.HeroActor;
import com.yongsa.combat.Monster;
import com.yongsa.combat.Physics;
import com.yongsa.config.Balance;
import com.yongsa.config.Balance.DungeonDef;
import com.yongsa.world.Door;
import com.yongsa.world.Prop;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;
import com.yongsa.world.VeinGenerator;
import com.yongsa.world.WorldMap;

import java.util.ArrayList;
import java.util.List;
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
    private Attack basicAttack, spinAttack, dashAttack;
    private float lockedGateMsgCd;
    private boolean justDied;

    /** HUD 에 보여줄 상호작용 안내 (없으면 null). */
    public String prompt;
    public float promptX, promptY;
    private Prop targetProp;
    private Vein targetVein;

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
        hero.spinCd -= dt;
        hero.dashCd -= dt;
        hero.mineCd -= dt;
        hero.invuln -= dt;
        hero.flash -= dt;
        hero.swingT -= dt;
        hero.mineSwingT -= dt;

        if (hero.kbT > 0) {
            Physics.move(map, hero, hero.kbVX * dt, hero.kbVY * dt, T);
            hero.kbT -= dt;
        }

        if (hero.dashing()) {
            hero.dashT -= dt;
            float speed = balance.attack.dashDistanceTiles * T / balance.attack.dashTime;
            boolean blocked = Physics.move(map, hero, hero.aimX * speed * dt, hero.aimY * speed * dt, T);
            hitAround(dashAttack, hero.hurtRadius + 10);
            if (blocked) hero.dashT = 0; // 벽은 통과하지 못한다
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

        if (c.attack && hero.attackCd <= 0) basic();
        if (c.spin && hero.spinCd <= 0) spin();
        if (c.dash && hero.dashCd <= 0) dash();
    }

    private void basic() {
        hero.attackCd = balance.attack.basicCooldown;
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

    private void spin() {
        float cd = balance.attack.spinCooldown;
        if (rules.hasAbility(state, "skyfall")) cd -= 2f;
        hero.spinCd = cd;
        hero.swingKind = 1;
        hero.swingT = 0.3f;
        spinAttack = new Attack(Attack.Kind.SPIN, balance.attack.spinMultiplier);
        hitAround(spinAttack, balance.attack.spinRadiusTiles * T);
        events.add(GameEvent.sound("spin"));
    }

    private void dash() {
        hero.dashCd = balance.attack.dashCooldown;
        hero.dashT = balance.attack.dashTime;
        hero.invuln = Math.max(hero.invuln, balance.attack.dashTime);
        hero.swingKind = 2;
        hero.swingT = balance.attack.dashTime + 0.05f;
        dashAttack = new Attack(Attack.Kind.DASH, balance.attack.dashMultiplier);
        events.add(GameEvent.sound("dash"));
    }

    private void hitAround(Attack a, float radius) {
        for (Monster m : monsters) {
            if (m.dead) continue;
            float dx = m.x - hero.x, dy = m.centerY() - hero.centerY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist - m.hurtRadius <= radius && a.tryHit(m)) damageMonster(m, a.multiplier);
        }
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
        if (!m.boss) m.knockback(m.x - hero.x, m.y - hero.y, balance.monster.knockbackTiles * T);
        events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2f, String.valueOf(dmg), GameEvent.Tone.MONSTER_HIT));
        events.add(GameEvent.sound("hit"));
        if (m.hp <= 0) {
            m.hp = 0;
            m.dead = true;
            state.stats.monstersKilled++;
            if (rules.hasAbility(state, "bloodmoon")) {
                int heal = Math.max(1, Math.round(rules.maxHp(state) * 0.05f));
                state.hp = Math.min(rules.maxHp(state), state.hp + heal);
                events.add(GameEvent.floatText(hero.x, hero.y + 40, "+" + heal, GameEvent.Tone.HEAL));
            }
        }
    }

    private void hitHero(int base, float mult, float fromX, float fromY) {
        if (hero.invuln > 0 || hero.dashing()) return;
        int dmg = roll(base, mult);
        state.hp -= dmg;
        hero.invuln = balance.hero.invulnAfterHit;
        hero.flash = balance.hero.invulnAfterHit;
        hero.knockback(hero.x - fromX, hero.y - fromY, balance.hero.knockbackTiles * T);
        events.add(GameEvent.floatText(hero.x, hero.y + 40, String.valueOf(dmg), GameEvent.Tone.HERO_HIT));
        events.add(GameEvent.sound("hurt"));
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
        placeAtCampSpawn();
        heroRoom = currentRoom = map.room("CAMP");
        hero.dashT = 0;
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
                && !state.room(heroRoom.id).cleared
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
        DungeonDef d = balance.dungeon(r.dungeonId);
        if (r.type == RoomType.MOB) {
            for (int i = 0; i < d.mobsPerRoom; i++) spawn(r, d.mob, false, false, false);
        } else if (r.type == RoomType.BOSS) {
            spawn(r, d.boss, true, false, true);
        } else if (r.type == RoomType.FINAL) {
            spawn(r, balance.finalBoss, true, true, true);
        }
        events.add(new GameEvent(GameEvent.Type.ROOM_LOCKED));
        events.add(GameEvent.sound("door"));
    }

    private void spawn(Room r, Balance.MonsterDef def, boolean boss, boolean fin, boolean farSide) {
        Monster m = new Monster(def, boss, fin, T);
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
            break;
        }
        m.attackCd = 0.6f + random.nextFloat() * 0.6f;
        m.slamCd = 2.5f;
        monsters.add(m);
    }

    private void resolveFight() {
        if (fightRoom == null) return;
        for (Monster m : monsters) if (!m.dead) return;
        Room r = fightRoom;
        fightRoom = null;
        r.setFightLock(false);
        monsters.clear();
        state.room(r.id).cleared = true;
        events.add(GameEvent.sound("door"));
        events.add(new GameEvent(GameEvent.Type.ROOM_CLEARED));
        if (r.type == RoomType.BOSS) {
            DungeonDef d = balance.dungeon(r.dungeonId);
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
        for (Monster m : monsters) {
            if (m.dead) continue;
            m.flash -= dt;
            m.attackCd -= dt;
            m.slamCd -= dt;
            m.immuneMsgCd -= dt;
            if (m.kbT > 0) {
                Physics.move(map, m, m.kbVX * dt, m.kbVY * dt, T);
                m.kbT -= dt;
            }
            float dx = hero.x - m.x, dy = hero.y - m.y;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float reach = mc.attackReachTiles * T + m.hw + hero.hw;
            switch (m.state) {
                case CHASE:
                    if (m.boss && m.slamCd <= 0 && dist < 9 * T) {
                        m.state = Monster.State.SLAM_TELEGRAPH;
                        m.stateT = mc.bossSlamTelegraph;
                        m.slamX = hero.x;
                        m.slamY = hero.y;
                        m.slamRadius = mc.bossSlamRadiusTiles * T * (m.finalBoss ? 1.3f : 1f);
                        break;
                    }
                    if (dist > 0.01f) {
                        m.dirX = dx / dist;
                        m.dirY = dy / dist;
                    }
                    if (dist <= reach && m.attackCd <= 0) {
                        m.state = Monster.State.TELEGRAPH;
                        m.stateT = mc.telegraphSeconds;
                    } else if (dist > reach * 0.7f) {
                        Physics.move(map, m, m.dirX * m.speed * dt, m.dirY * m.speed * dt, T);
                        m.walkTime += dt;
                    }
                    break;
                case TELEGRAPH:
                    m.stateT -= dt;
                    if (m.stateT <= 0) {
                        // 예고한 방향으로 친다. 그 사이 걸어서 벗어났으면 빗나간다.
                        float dot = dist > 0.01f ? (dx * m.dirX + dy * m.dirY) / dist : 1f;
                        if (dist <= reach + 4 && dot > 0.2f) hitHero(m.def.attack, 1f, m.x, m.y);
                        m.state = Monster.State.RECOVER;
                        m.stateT = 0.35f;
                        m.attackCd = mc.attackCooldown;
                        events.add(GameEvent.sound("monsterAttack"));
                    }
                    break;
                case SLAM_TELEGRAPH:
                    m.stateT -= dt;
                    if (m.stateT <= 0) {
                        float sx = hero.x - m.slamX, sy = hero.y - m.slamY;
                        if (sx * sx + sy * sy <= m.slamRadius * m.slamRadius) {
                            hitHero(m.def.attack, mc.bossSlamMultiplier, m.slamX, m.slamY);
                        }
                        m.state = Monster.State.RECOVER;
                        m.stateT = 0.5f;
                        m.slamCd = mc.bossSlamCooldown * (m.finalBoss ? 0.7f : 1f);
                        events.add(GameEvent.sound("slam"));
                    }
                    break;
                case RECOVER:
                    m.stateT -= dt;
                    if (m.stateT <= 0) m.state = Monster.State.CHASE;
                    break;
            }
            if (justDied) return; // die() 가 몬스터 목록을 비웠다
        }
        separate();
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
        float best = Float.MAX_VALUE;
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
