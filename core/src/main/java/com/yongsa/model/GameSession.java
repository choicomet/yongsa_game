package com.yongsa.model;

import com.yongsa.combat.Attack;
import com.yongsa.combat.Facing;
import com.yongsa.combat.Hazard;
import com.yongsa.combat.HeroActor;
import com.yongsa.combat.HeroShot;
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
    /** 용사의 검 스킬이 만든 검기·낙뢰. */
    public final List<HeroShot> shots = new ArrayList<>();
    /** 바닥에 예고된 보스 공격 (낙석, 낙뢰, 촉수, 창, 피웅덩이). */
    public final List<Hazard> hazards = new ArrayList<>();
    public final Random random;

    private final int T;
    private final List<GameEvent> events = new ArrayList<>();

    /** 마지막으로 들어간 방 (캠프 문 밖에 있으면 직전 방). */
    private Room currentRoom;
    /** 용사가 지금 서 있는 방, 캠프 문 밖이면 null. */
    private Room heroRoom;
    private Room fightRoom;
    private float entryX, entryY, outsideX, outsideY;
    /** 문을 지나 막 옮겨 왔으면 잠깐 다시 넘어가지 않는다. */
    private float doorCd;
    /** 화면이 어두워지는 동안 기다리는 문 (deferDoors 일 때). */
    private Door pendingDoor;
    /** true 면 문을 지날 때 바로 옮기지 않고 DOOR_ENTER 를 보낸 뒤 passPendingDoor 를 기다린다 (화면 연출용). */
    public boolean deferDoors;
    /** 싸우는 방의 길찾기 (용사 칸까지의 거리). */
    private int[] flow;
    private int flowHeroX = Integer.MIN_VALUE, flowHeroY;
    private Room flowRoom;
    private float pathX, pathY;
    private float hpAcc;
    /** 피웅덩이에서 닳은 체력 (1 이 넘으면 깎는다), 숫자를 띄울 때까지 모은 양. */
    private float poolAcc, poolShown, poolTextCd;
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
    private boolean targetChest;
    private Balance.Villager targetVillager;
    /** 다섯 번 되돌아선 뒤 체념하고 나가는 중 (마을에 닿으면 독백). */
    private boolean giveUpPending;
    /** 침대에서 자는 중 (선우 버전 잠 모션): 0 이면 깨어 있다. 중간에 푹 쉬고(useBed) 끝에 일어난다. */
    private float sleepTime;
    private boolean slept;
    private Prop sleepBed;
    public static final float SLEEP_TIME = 3.2f, SLEEP_REST_AT = 1.5f;
    /** 마을 사람마다 몇 번째 대사까지 했는지. */
    private final java.util.Map<String, Integer> talkCount = new java.util.HashMap<>();

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
        if (state.mapVersion != WorldMap.VERSION) {
            // 방 배치가 바뀐 예전 저장: 광맥 자리와 이어하기 위치는 새 맵에 맞지 않는다
            for (RoomRecord rec : state.rooms.values()) rec.veins = null;
            state.resumeX = state.resumeY = 0;
            state.mapVersion = WorldMap.VERSION;
        }
        applyVeinOverlay();
        if (state.resumeX == 0 && state.resumeY == 0) {
            placeAtCampSpawn();
        } else {
            hero.x = state.resumeX;
            hero.y = state.resumeY;
            if (map.roomAt(tileX(hero.x), tileY(hero.y)) == null || !Physics.free(map, hero.x, hero.y, hero, T)) placeAtCampSpawn();
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
        hero.x = camp.scene == null ? center((int) camp.centerTileX()) : camp.spawnX(T);
        hero.y = camp.scene == null ? center((int) camp.centerTileY() - 2) : camp.spawnY(T);
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
        if (pendingDoor != null) return; // 화면이 어두워지는 동안은 멈춰 있다
        doorCd -= dt;
        if (sleepTime > 0) {
            // 자는 동안은 조작을 받지 않는다
            sleepTime += dt;
            if (!slept && sleepTime >= SLEEP_REST_AT) {
                slept = true;
                useBed();
            }
            if (sleepTime >= SLEEP_TIME) {
                sleepTime = 0;
                hero.facing = Facing.DOWN;
                hero.aimX = 0;
                hero.aimY = -1;
            }
            c = new Controls();
        }
        updateHero(dt, c);
        if (checkDoors()) return;
        updateRooms();
        updateMonsters(dt);
        if (!justDied) updateShots(dt);
        if (!justDied) updateProjectiles(dt);
        if (!justDied) updateHazards(dt);
        if (!justDied) resolveFight();
        if (inCamp() || inVillage()) {
            hpAcc += rules.maxHp(state) * balance.hero.campRegenFractionPerSec * dt;
            int add = (int) hpAcc;
            if (add > 0) {
                hpAcc -= add;
                state.hp = Math.min(rules.maxHp(state), state.hp + add);
            }
            if (!unsealOffered && absorb.canUnseal(state)) {
                unsealOffered = true;
                events.add(GameEvent.toast("세 보스의 힘이 모였다. 캠프 바닥의 문양에서 성검의 봉인을 풀 수 있다"));
            }
        }
        if (sleepTime > 0) {
            prompt = null;
            return;
        }
        findInteraction();
        if (c.interact) interact();
    }

    private void updateHero(float dt, Controls c) {
        hero.attackCd -= dt;
        hero.skillCd -= dt;
        hero.rollCd -= dt;
        hero.mineCd -= dt;
        hero.invuln -= dt;
        hero.flash -= dt;
        hero.swingT -= dt;
        hero.mineSwingT -= dt;
        hero.slowT -= dt;

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
            float speed = balance.hero.moveSpeedTiles * T * (hero.slowT > 0 ? 1f - balance.monster.slowRatio : 1f);
            Physics.move(map, hero, mx * speed * dt, my * speed * dt, T);
            hero.walkTime += dt;
        }
        autoFace();

        if (c.roll && hero.rollCd <= 0) roll(mx, my);
        else if (c.skill) useSkill();
        else if (c.attack && hero.attackCd <= 0) basic();
    }

    /** 가까운 몬스터를 자동으로 바라본다: 걸어가도 몸과 공격은 적을 향한다 (팀 분담: 최해성). */
    private void autoFace() {
        float range = balance.attack.autoFaceTiles * T;
        if (range <= 0) return;
        Monster best = null;
        float bestD = range * range;
        for (Monster m : monsters) {
            if (m.dead || m.state == Monster.State.SPAWN) continue;
            float dx = m.x - hero.x, dy = m.centerY() - hero.centerY();
            float d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        if (best == null) return;
        float dx = best.x - hero.x, dy = best.centerY() - hero.centerY(), l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l < 0.01f) return;
        hero.aimX = dx / l;
        hero.aimY = dy / l;
        hero.facing = Facing.of(hero.aimX, hero.aimY);
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

    // ───────────────────────── 검 스킬 (Q)

    private float skillMsgCd;

    /** 들고 있는 검의 스킬을 쓴다. 아직 안 열렸으면 언제 열리는지 알려 준다. */
    private void useSkill() {
        Balance.SkillDef sk = rules.skill(state);
        if (sk == null || !rules.skillUnlocked(state)) {
            if (skillMsgCd <= 0) {
                skillMsgCd = 1.5f;
                events.add(GameEvent.toast(sk == null ? "봉인된 성검에는 스킬이 없다. 광물로 검을 만들자"
                        : "+" + balance.enhance.skillUnlockLevel + " 까지 강화하면 「" + sk.name + "」을(를) 쓸 수 있다"));
            }
            return;
        }
        if (hero.skillCd > 0) return;
        hero.skillCd = sk.cooldown;
        hero.swingKind = 1;
        hero.swingT = 0.25f;
        boolean aw = rules.skillAwakened(state);
        int n = aw ? sk.awakenedCount : sk.count;
        float mult = sk.multiplier * (aw ? sk.awakenedMultiplier : 1f);
        if ("strike".equals(sk.kind)) {
            List<Monster> targets = new ArrayList<>();
            float range = sk.rangeTiles * T;
            for (Monster m : monsters) {
                if (m.dead || m.state == Monster.State.SPAWN) continue;
                if (Math.hypot(m.x - hero.x, m.y - hero.y) <= range) targets.add(m);
            }
            targets.sort((a, b) -> Float.compare(dist2(a), dist2(b)));
            for (int i = 0; i < n; i++) {
                float tx, ty;
                if (i < targets.size()) {
                    tx = targets.get(i).x;
                    ty = targets.get(i).y;
                } else if (i == 0) {
                    tx = hero.x + hero.aimX * 3 * T; // 적이 없으면 바라보는 쪽 앞에
                    ty = hero.y + hero.aimY * 3 * T;
                } else break;
                HeroShot h = new HeroShot();
                h.kind = sk.kind;
                h.x = tx;
                h.y = ty;
                h.radius = sk.widthTiles * T;
                h.delay = 0.3f + i * 0.08f;
                h.life = h.delay + 0.1f;
                h.multiplier = mult;
                shots.add(h);
            }
        } else {
            float base = (float) Math.atan2(hero.aimY, hero.aimX);
            float spread = (float) Math.toRadians(sk.spreadDegrees);
            for (int i = 0; i < n; i++) {
                float a = base + spread * (i - (n - 1) / 2f);
                HeroShot h = new HeroShot();
                h.kind = sk.kind;
                h.x = hero.x + (float) Math.cos(a) * T * 0.6f;
                h.y = hero.centerY() + (float) Math.sin(a) * T * 0.6f;
                float speed = sk.speedTiles * T;
                h.vx = (float) Math.cos(a) * speed;
                h.vy = (float) Math.sin(a) * speed;
                h.radius = sk.widthTiles * T / 2f;
                h.life = sk.rangeTiles / sk.speedTiles;
                h.multiplier = mult;
                h.stun = sk.stunSeconds;
                h.heal = sk.healRatio;
                shots.add(h);
            }
        }
        GameEvent e = new GameEvent(GameEvent.Type.SKILL_CAST);
        e.text = sk.kind;
        e.x = hero.x;
        e.y = hero.centerY();
        e.dirX = hero.aimX;
        e.dirY = hero.aimY;
        events.add(e);
        events.add(GameEvent.sound("strike".equals(sk.kind) ? "cast" : "slam"));
    }

    private float dist2(Monster m) {
        float dx = m.x - hero.x, dy = m.y - hero.y;
        return dx * dx + dy * dy;
    }

    private void updateShots(float dt) {
        skillMsgCd -= dt;
        for (HeroShot h : shots) {
            if (h.dead) continue;
            h.age += dt;
            h.life -= dt;
            if (h.strike()) {
                h.delay -= dt;
                if (h.delay > 0) continue;
                for (Monster m : monsters) {
                    if (m.dead || m.state == Monster.State.SPAWN) continue;
                    if (Math.hypot(m.x - h.x, m.y - h.y) <= h.radius + m.hurtRadius) damageMonster(m, h.multiplier);
                }
                GameEvent fx = GameEvent.fx(GameEvent.Type.SKILL_HIT, h.x, h.y);
                fx.text = h.kind;
                fx.size = h.radius;
                events.add(fx);
                events.add(GameEvent.sound("cast"));
                h.dead = true;
                continue;
            }
            h.x += h.vx * dt;
            h.y += h.vy * dt;
            if (h.life <= 0 || map.solid(tileX(h.x), tileY(h.y - 6))) {
                h.dead = true;
                continue;
            }
            for (Monster m : monsters) {
                if (m.dead || m.state == Monster.State.SPAWN || h.hit.contains(m)) continue;
                float dx = m.x - h.x, dy = m.centerY() - h.y, r = h.radius + m.hurtRadius;
                if (dx * dx + dy * dy > r * r) continue;
                h.hit.add(m);
                damageMonster(m, h.multiplier);
                if (h.stun > 0 && !m.dead && !m.boss) m.enter(Monster.State.STUNNED, h.stun);
                if (h.heal > 0) {
                    int add = Math.max(1, Math.round(rules.maxHp(state) * h.heal));
                    state.hp = Math.min(rules.maxHp(state), state.hp + add);
                    events.add(GameEvent.floatText(hero.x, hero.y + 40, "+" + add, GameEvent.Tone.HEAL));
                }
                GameEvent fx = GameEvent.fx(GameEvent.Type.SKILL_HIT, m.x, m.centerY());
                fx.text = h.kind;
                fx.size = h.radius;
                events.add(fx);
            }
        }
        shots.removeIf(h -> h.dead);
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
        if (m.invulnerable()) return;
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
        m.flash = 0.15f;
        if (m.shield > 0) {
            // 방패가 남아 있으면 체력 대신 방패가 닳는다
            m.shield -= dmg;
            events.add(GameEvent.floatText(m.x, m.y + m.hurtRadius * 2f, String.valueOf(dmg), GameEvent.Tone.SHIELD));
            events.add(GameEvent.sound("block"));
            if (m.shield <= 0) {
                m.shield = 0;
                m.shieldMax = 0;
                m.enter(Monster.State.STUNNED, balance.monster.shieldStagger);
                events.add(GameEvent.toast(m.def.name + "의 방패가 깨졌다! 지금이 기회다"));
                events.add(GameEvent.sound("slam"));
                GameEvent fx = GameEvent.fx(GameEvent.Type.SHIELD_BREAK, m.x, m.centerY());
                fx.size = m.hurtRadius * 2.4f;
                events.add(fx);
            }
            return;
        }
        m.hp -= dmg;
        // 공격을 준비하거나 돌진하는 중에는 밀리지 않는다: 연타로 공격을 끊을 수 없다
        if (!m.boss && !m.committed()) m.knockback(m.x - hero.x, m.y - hero.y, balance.monster.knockbackTiles * T);
        if (m.boss && !m.finalBoss && !m.enraged && m.hp > 0 && m.hp <= m.maxHp * balance.monster.phase2HpRatio) {
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
        if (m.hp <= 0 && m.finalBoss && !m.phase2) {
            startPhaseShift(m);
            return;
        }
        if (m.hp <= 0) {
            m.hp = 0;
            m.dead = true;
            if (m.finalBoss) speech(m, "이런 풋내기에게 당하다니!");
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
        String last = null;
        if (fightRoom != null && fightRoom.type == RoomType.FINAL) {
            Monster b = activeBoss();
            last = b != null && b.phase2 ? "꽤나 즐거운 여흥이었다." : "한심하긴.";
        }
        hazards.clear();
        hero.slowT = 0;
        if (fightRoom != null) {
            fightRoom.setFightLock(false); // 싸우던 방만 처음 상태로 (A8)
            fightRoom = null;
        }
        monsters.clear();
        projectiles.clear();
        shots.clear();
        placeAtCampSpawn();
        heroRoom = currentRoom = map.room("CAMP");
        hero.rollT = 0;
        hero.kbT = 0;
        state.hp = rules.maxHp(state);
        GameEvent died = new GameEvent(GameEvent.Type.HERO_DIED);
        died.text = last;
        events.add(died);
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
                if (r.dungeonId != null && r.type != RoomType.FINAL
                        && (prevDungeonRoom == null || prevDungeonRoom.dungeonId == null)) {
                    DungeonDef d = balance.dungeon(r.dungeonId);
                    GameEvent e = new GameEvent(GameEvent.Type.ENTERED_DUNGEON);
                    e.dungeonId = d.id;
                    e.text = NextTask.dungeonWord(d.id) + " 던전 · " + d.name;
                    events.add(e);
                }
                if (r.type == RoomType.VILLAGE) {
                    if (giveUpPending) heroSpeech(balance.village.giveUpLine);
                    else if (!villagersFree()) heroSpeech(balance.village.arriveTrappedLine);
                    giveUpPending = false;
                }
                if (r.type == RoomType.BOSS && !state.bossDefeated(r.dungeonId)) {
                    events.add(GameEvent.toast("보스방이다. 권장 무기: " + balance.dungeon(r.dungeonId).recommended));
                }
            }
        }
        if (fightRoom == null && heroRoom != null && heroRoom.type.hasFight()
                && !state.room(heroRoom.id).cleared && !hasPortal(heroRoom) && steppedIn(heroRoom)) {
            startFight(heroRoom);
        }
    }

    /** 문 앞에서 방 안으로 한 걸음 들어섰나 (그러면 문이 잠기고 싸움이 시작된다). */
    private boolean steppedIn(Room r) {
        int tx = tileX(hero.x), ty = tileY(hero.y);
        if (r.scene == null) return r.insideWithMargin(tx, ty, 1);
        if (!r.inside(tx, ty)) return false;
        for (Door d : r.doors) {
            if (d.dead()) continue;
            float dx = hero.x - d.footX, dy = hero.y - d.footY;
            if (dx * dx + dy * dy < (2.6f * T) * (2.6f * T)) return false;
        }
        return true;
    }

    private void setEntry(Door d) {
        if (d == null) {
            entryX = outsideX = hero.x;
            entryY = outsideY = hero.y;
            return;
        }
        entryX = d.arriveX;
        entryY = d.arriveY;
        Door p = d.partner();
        if (d.room.scene != null && p != null) {
            outsideX = p.arriveX; // 그림 방의 '문 밖'은 이어진 방의 문 앞이다
            outsideY = p.arriveY;
        } else {
            outsideX = center(d.outsideX);
            outsideY = center(d.outsideY);
        }
    }

    /**
     * 열린 문을 지나가면 이어진 방의 짝 문 앞으로 옮긴다 (방 사이 통로는 없다).
     * deferDoors 면 DOOR_ENTER 를 보내고 화면이 어두워진 뒤 passPendingDoor 로 옮긴다. 옮겼으면(또는 기다리면) true.
     */
    private boolean checkDoors() {
        if (fightRoom != null || doorCd > 0 || pendingDoor != null) return false;
        // Developer warps and door arrivals can change coordinates before updateRooms runs.
        // Never trigger an old room's unbounded exit plane from a different room.
        Room r = map.roomAt(tileX(hero.x), tileY(hero.y));
        if (r == null) return false;
        for (Door d : r.doors) {
            if (d.isClosed() || !d.triggers(hero.x, hero.y)) continue;
            if (d.partner().room.type == RoomType.VILLAGE && refuseVillageExit(d)) return false;
            if (deferDoors) {
                pendingDoor = d;
                GameEvent e = new GameEvent(GameEvent.Type.DOOR_ENTER);
                Room to = d.partner().room;
                e.text = to.dungeonId == null ? null : to.id;
                events.add(e);
                return true;
            }
            passDoor(d);
            return false;
        }
        return false;
    }

    /** 기다리던 문을 지나간다 (화면이 다 어두워졌을 때 부른다). */
    public void passPendingDoor() {
        Door d = pendingDoor;
        pendingDoor = null;
        if (d != null) passDoor(d);
    }

    public boolean doorPending() {
        return pendingDoor != null;
    }

    /** 문 d 를 지나 이어진 방의 짝 문 앞에 선다. */
    public void passDoor(Door d) {
        Door to = d.partner();
        if (to == null) return;
        hero.x = to.arriveX;
        hero.y = to.arriveY;
        hero.aimX = -to.dirX;
        hero.aimY = -to.dirY;
        hero.facing = Facing.of(hero.aimX, hero.aimY);
        hero.rollT = 0;
        hero.kbT = 0;
        hero.slowT = 0;
        hazards.clear();
        projectiles.clear();
        shots.clear();
        doorCd = 0.35f;
    }

    private Door nearestDoor(Room r) {
        Door best = null;
        float bestD = Float.MAX_VALUE;
        for (Door d : r.doors) {
            if (d.dead()) continue;
            float dx = d.arriveX - hero.x, dy = d.arriveY - hero.y;
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
        shots.clear();
        hazards.clear();
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
            // 심연에는 문이 없다: 잠기는 대신 마왕이 말을 건다
            for (Monster m : monsters) if (m.finalBoss && !m.dead) speech(m, "봉인을 풀고 여기까지 내려오다니. 어디 한번 놀아 주마.");
            return;
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
            if (!farSide && !r.insideWithMargin(tx, ty, 1)) continue;
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
        if (!boss && r.dungeonId != null && state.bossDefeated(r.dungeonId)) {
            // 보스가 흡수하지 못한 힘이 던전에 퍼져 잡몹이 강해진다
            m.hp = m.maxHp = Math.round(def.hp * balance.monster.clearedMobHpMultiplier);
            m.attackMul = balance.monster.clearedMobAttackMultiplier;
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
        shots.clear();
        hazards.clear();
        state.room(r.id).cleared = true;
        events.add(GameEvent.sound("door"));
        events.add(new GameEvent(GameEvent.Type.ROOM_CLEARED));
        if (r.type == RoomType.BOSS) {
            DungeonDef d = balance.dungeon(r.dungeonId);
            revived.remove(d.id);
            boolean first = !state.bossDefeated(d.id);
            int gain = absorb.absorbBoss(state, d.id);
            OreType ore = OreType.valueOf(d.ore);
            if (!state.chests.contains(r.id)) state.chests.add(r.id);
            map.updateGates(state);
            GameEvent e = new GameEvent(GameEvent.Type.BOSS_DEFEATED);
            e.dungeonId = d.id;
            e.hpGain = gain;
            e.ore = ore;
            e.first = first;
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
            if (m.boss && m.def.cornerBlink && cornered(m, dist, reach, dt)) continue;
            switch (m.state) {
                case CHASE:
                    if (dist > 0.01f) {
                        m.dirX = dx / dist;
                        m.dirY = dy / dist;
                    }
                    steer(m);
                    if (m.boss) chaseBoss(m, dist, reach, dt, summoned);
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
                        hitHero(m.attack(), charge ? mc.chargeMultiplier : 1f, m.x, m.y);
                        if (justDied) return;
                    }
                    boolean over = m.dashLeft <= 0.5f || m.stateT <= 0 || blocked;
                    if (charge && over && m.rushLeft > 1) {
                        // 연속 돌진: 숨을 고르고 다시 조준한다
                        m.rushLeft--;
                        aimAtHero(m);
                        m.enter(Monster.State.CHARGE_TELEGRAPH, mc.rushTelegraph);
                        if (blocked) wallBump(m);
                    } else if (charge && blocked) {
                        // 벽에 부딪혀 잠깐 기절한다: 반격할 기회
                        m.rushLeft = 0;
                        m.enter(Monster.State.STUNNED, mc.chargeStun);
                        wallBump(m);
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
                    channel(m, dt * speedUp);
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
            if (justDied) return; // die() 가 몬스터 목록을 비웠다
        }
        monsters.addAll(summoned);
        separate();
    }

    private void wallBump(Monster m) {
        events.add(GameEvent.sound("slam"));
        GameEvent fx = GameEvent.fx(GameEvent.Type.SLAM_FX, m.x + m.lockX * m.hw, m.y + m.lockY * m.hh);
        fx.size = T * 1.4f;
        events.add(fx);
    }

    private void aimAtHero(Monster m) {
        float dx = hero.x - m.x, dy = hero.y - m.y, l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 0.01f) {
            m.dirX = dx / l;
            m.dirY = dy / l;
        }
        m.lockX = m.dirX;
        m.lockY = m.dirY;
    }

    /**
     * 구석 몰이 방지: 벽 가까이 있는 보스에게 용사가 바짝 붙어 있는 시간이 쌓이면
     * 멀리 순간이동하고 세 갈래 탄을 쏜다. 순간이동했으면 true.
     */
    private boolean cornered(Monster m, float dist, float reach, float dt) {
        Balance.MonsterCfg mc = balance.monster;
        boolean idle = m.state == Monster.State.CHASE || m.state == Monster.State.RECOVER
                || m.state == Monster.State.TELEGRAPH;
        if (idle && dist < reach * 1.6f && nearWall(m)) m.cornerT += dt;
        else m.cornerT = Math.max(0f, m.cornerT - dt * 0.5f);
        if (m.cornerT < mc.cornerTime || m.blinkCd > 0 || !idle) return false;
        m.cornerT = 0;
        if (!blinkAway(m)) return false;
        m.blinkCd = mc.cornerCooldown;
        aimAtHero(m);
        m.castKind = "tri";
        fire(m);
        m.enter(Monster.State.RECOVER, 0.5f);
        return true;
    }

    private boolean nearWall(Monster m) {
        float r = m.hurtRadius + T * 0.9f;
        for (int i = 0; i < 8; i++) {
            float a = 6.283f * i / 8f;
            if (map.solid(tileX(m.x + (float) Math.cos(a) * r), tileY(m.y + (float) Math.sin(a) * r))) return true;
        }
        return false;
    }

    // ───────────────────────── 길찾기 (그림 방은 오목해서 곧장 가면 벽에 걸린다)

    /** 용사에게 곧장 갈 수 없으면 바닥 칸을 따라 돌아간다. pathX, pathY 에 걸어갈 방향을 넣는다. */
    private void steer(Monster m) {
        pathX = m.dirX;
        pathY = m.dirY;
        Room r = fightRoom;
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
    private boolean clearLine(Room r, float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
        int n = Math.max(1, (int) (len / (T * 0.5f)));
        for (int i = 1; i < n; i++) {
            int tx = tileX(x0 + dx * i / n), ty = tileY(y0 + dy * i / n);
            if (!r.inside(tx, ty) || map.isBlockedOverlay(tx, ty)) return false;
        }
        return true;
    }

    /** 용사 칸에서 넓이 우선으로 퍼진 거리 (용사 칸이 바뀔 때만 다시 계산). */
    private void updateFlow(Room r) {
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

    private int flowAt(Room r, int x, int y) {
        if (r != flowRoom || !r.contains(x, y)) return -1;
        return flow[(x - r.x0) + (y - r.y0) * (r.x1 - r.x0 + 1)];
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
                if (dist < keep * 0.6f && m.blinkCd <= 0 && blinkAway(m)) return;
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
            float k = m.speed * dt * (m.enraged ? 1.15f : 1f);
            Physics.move(map, m, pathX * k, pathY * k, T);
            m.walkTime += dt;
        }
    }

    private String pickPattern(Monster m) {
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
                m.rushLeft = 0;
                aimAtHero(m);
                m.enter(Monster.State.CHARGE_TELEGRAPH, mc.chargeTelegraph);
                events.add(GameEvent.sound("roar"));
                return true;
            case "rush":
                m.rushLeft = mc.rushCount;
                aimAtHero(m);
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

    private void startChannel(Monster m, String kind, int count, float firstDelay) {
        m.channelKind = kind;
        m.channelLeft = count;
        m.channelT = firstDelay;
        m.enter(Monster.State.CHANNEL, 99f);
    }

    /** 제자리에서 이어 가는 패턴 한 프레임. */
    private void channel(Monster m, float dt) {
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
                    shoot(m, m.x, m.centerY(), m.spinAngle, speed, dmg, "fire");
                    shoot(m, m.x, m.centerY(), m.spinAngle + 3.1416f, speed, dmg, "fire");
                }
                if (m.stateT <= 0) m.enter(Monster.State.RECOVER, 1.1f);
                return;
            }
            case "bloodpool": {
                if (m.channelT > 0) return;
                if (m.channelLeft > 0) {
                    m.channelLeft = 0;
                    aimAtHero(m);
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

    private void circleHazard(String style, float x, float y, float radius, float warning, int damage, float mult) {
        Hazard h = new Hazard(Hazard.Kind.CIRCLE, style);
        h.x = x;
        h.y = y;
        h.radius = radius;
        h.delay = h.warning = warning;
        h.damage = damage;
        h.multiplier = mult;
        hazards.add(h);
    }

    /** 피웅덩이: 용사 발밑 하나와 방 안 아무 데나 몇 개. */
    private void spawnPools(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        Room r = fightRoom;
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
    private void updateHazards(float dt) {
        Balance.MonsterCfg mc = balance.monster;
        boolean inPool = false;
        for (Hazard h : hazards) {
            if (h.dead) continue;
            h.age += dt;
            if (h.delay > 0) {
                h.delay -= dt;
                if (h.delay > 0) continue;
                h.triggered = true;
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
                    if (justDied) return;
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
            if (!devGodMode) {
                poolAcc += rules.maxHp(state) * mc.poolDamageRatio * dt;
                int take = (int) poolAcc;
                if (take > 0) {
                    poolAcc -= take;
                    poolShown += take;
                    state.hp -= take;
                    if (state.hp <= 0) {
                        die();
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

    /** 마왕이 처음 쓰러지면: 무적으로 변신하며 체력이 다시 차고 2페이즈가 된다. */
    private void startPhaseShift(Monster m) {
        Balance.MonsterCfg mc = balance.monster;
        m.phase2 = true;
        m.maxHp = Math.round(m.def.hp * mc.finalPhase2HpMultiplier);
        m.hp = 1;
        m.attackMul = mc.finalPhase2AttackMultiplier;
        m.shield = m.shieldMax = 0;
        m.rushLeft = 0;
        m.enter(Monster.State.PHASE_SHIFT, mc.phaseShiftSeconds);
        hazards.clear();
        projectiles.clear();
        shots.clear();
        speech(m, "이번 용사는 강하군. 나도 본격적으로 해주마!");
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_PHASE2);
        e.text = m.def.name;
        e.x = m.x;
        e.y = m.centerY();
        events.add(e);
        events.add(GameEvent.sound("roar"));
    }

    private void speech(Monster m, String text) {
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_SPEECH);
        e.text = text;
        e.x = m.x;
        e.y = m.y + m.hurtRadius * 3.2f;
        events.add(e);
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
        if (dist <= reach + 4 && dot > 0.2f) hitHero(m.attack(), mult, m.x, m.y);
        events.add(GameEvent.sound("monsterAttack"));
    }

    /** 너무 가까우면 용사 반대쪽 멀리 순간이동한다 (마족병, 구석에 몰린 보스). */
    private boolean blinkAway(Monster m) {
        if (!m.boss) m.blinkCd = balance.monster.blinkCooldown;
        Room r = fightRoom;
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

    /** 모은 투사체를 쏜다: cast(한 발), tri(세 갈래), volley(부채꼴), ring(사방). */
    private void fire(Monster m) {
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

    private void shoot(Monster m, float x, float y, float angle, float speed, int damage, String source) {
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
        targetChest = false;
        targetVillager = null;
        float best = Float.MAX_VALUE;
        if (inVillage()) {
            Balance.Villager near = null;
            float bestV = 1.4f * T;
            for (Balance.Villager v : balance.village.villagers) {
                float d = (float) Math.hypot(villagerX(v) - hero.x, villagerY(v) - hero.y);
                if (d < bestV) {
                    bestV = d;
                    near = v;
                }
            }
            if (near != null) {
                targetVillager = near;
                prompt = villagersFree() ? "E  " + near.name + "와(과) 이야기하기" : "E  " + near.name + " 살펴보기";
                promptX = villagerX(near);
                promptY = villagerY(near) + 2.2f * T;
                return;
            }
        }
        if (heroRoom != null && hasChest(heroRoom) && fightRoom == null) {
            float d = (float) Math.hypot(chestX(heroRoom) - hero.x, chestY(heroRoom) - hero.y);
            if (d < 1.3f * T) {
                targetChest = true;
                prompt = "E  보물상자 열기";
                promptX = chestX(heroRoom);
                promptY = chestY(heroRoom) + 1.6f * T;
                return;
            }
        }
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
            float px = Math.max(p.x(T), Math.min(hero.x, p.x(T) + p.width(T)));
            float py = Math.max(p.y(T), Math.min(hero.y, p.y(T) + p.height(T)));
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
                case SEAL:
                    if (absorb.canUnseal(state)) prompt = "E  성검의 봉인 풀기";
                    else if (state.unsealed && !state.finalBossDefeated) prompt = "E  심연으로 내려가기 (마왕)";
                    else if (state.unsealed) prompt = "E  심연 (마왕 다시 보기는 개발 메뉴)";
                    else prompt = "봉인된 문양 · 세 보스의 힘이 필요하다";
                    break;
                default: prompt = "E  모닥불"; break;
            }
            promptX = targetProp.centerX(T);
            promptY = targetProp.y(T) + targetProp.height(T) + 18;
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
                float dist = (float) Math.hypot(d.footX - hero.x, d.footY - hero.y);
                if (dist < 2.4f * T) {
                    prompt = "FINAL".equals(l.gate) ? "성검의 봉인을 풀어야 열립니다" : "앞 던전의 보스를 잡아야 열립니다";
                    promptX = d.footX;
                    promptY = d.footY + T;
                    return;
                }
            }
        }
    }

    private void interact() {
        if (targetVillager != null) {
            talk(targetVillager);
            return;
        }
        if (targetChest) {
            openChest(heroRoom);
            return;
        }
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
                    startSleep(targetProp);
                    break;
                case SEAL:
                    if (absorb.canUnseal(state)) {
                        events.add(new GameEvent(GameEvent.Type.UNSEAL_READY));
                    } else if (state.unsealed && !state.finalBossDefeated) {
                        events.add(new GameEvent(GameEvent.Type.FALL));
                        events.add(GameEvent.sound("slam"));
                    } else if (!state.unsealed) {
                        int n = 0;
                        for (String id : AbsorbService.BOSS_ORDER) if (state.bossDefeated(id)) n++;
                        events.add(GameEvent.toast("문양 아래에서 무언가 꿈틀댄다. 흡수한 보스의 힘 " + n + "/3"));
                    }
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

    // ───────────────────────── 던전 밖 마을 (선우 버전)

    public boolean inVillage() {
        return heroRoom != null && heroRoom.type == RoomType.VILLAGE;
    }

    /** 마을 사람들은 마왕을 쓰러뜨리면 수정에서 풀려난다. */
    public boolean villagersFree() {
        return state.finalBossDefeated;
    }

    public Room village() {
        for (Room r : map.rooms) if (r.type == RoomType.VILLAGE) return r;
        return null;
    }

    public float villagerX(Balance.Villager v) {
        return village().sceneX(v.at[0]);
    }

    public float villagerY(Balance.Villager v) {
        return village().sceneY(v.at[1]);
    }

    /**
     * 캠프 아래 계단으로 마을에 나가려 할 때: 마왕을 쓰러뜨렸으면 그냥 나간다.
     * 아니면 다섯 번까지는 독백하며 되돌아서고, 여섯 번째에는 체념하고 나간다 (그 뒤로는 그냥 나간다).
     * 되돌아섰으면 true.
     */
    private boolean refuseVillageExit(Door d) {
        Balance.VillageCfg v = balance.village;
        if (state.finalBossDefeated || state.exitRefusals > v.exitRefusals) return false;
        if (state.exitRefusals == v.exitRefusals) {
            state.exitRefusals++;
            giveUpPending = true;
            return false;
        }
        heroSpeech(v.exitLines.get(Math.min(state.exitRefusals, v.exitLines.size() - 1)));
        state.exitRefusals++;
        // 계단에서 한 걸음 물러선다
        float bx = hero.x - d.dirX * 1.3f * T, by = hero.y - d.dirY * 1.3f * T;
        if (Physics.free(map, bx, by, hero, T)) {
            hero.x = bx;
            hero.y = by;
        }
        hero.aimX = -d.dirX;
        hero.aimY = -d.dirY;
        hero.facing = Facing.of(hero.aimX, hero.aimY);
        hero.rollT = 0;
        hero.kbT = 0;
        doorCd = 0.5f;
        events.add(GameEvent.sound("block"));
        return true;
    }

    private void heroSpeech(String text) {
        if (text == null) return;
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_SPEECH);
        e.text = text;
        e.speaker = "용사";
        e.x = hero.x;
        e.y = hero.y + 2.4f * T;
        events.add(e);
    }

    private void talk(Balance.Villager v) {
        float dx = villagerX(v) - hero.x, dy = villagerY(v) - hero.y;
        if (Math.abs(dx) + Math.abs(dy) > 1) {
            hero.facing = Facing.of(dx, dy);
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            hero.aimX = dx / l;
            hero.aimY = dy / l;
        }
        if (!villagersFree()) {
            heroSpeech(String.format(balance.village.trappedLine, v.name));
            return;
        }
        int n = talkCount.merge(v.kind, 1, Integer::sum) - 1;
        GameEvent e = new GameEvent(GameEvent.Type.BOSS_SPEECH);
        e.text = v.freeLines.get(n % v.freeLines.size());
        e.speaker = v.name;
        e.x = villagerX(v);
        e.y = villagerY(v) + 2.2f * T;
        events.add(e);
        events.add(GameEvent.sound("select"));
    }

    // ───────────────────────── 보물상자 (선우·지용 버전의 보스 보상)

    public boolean hasChest(Room r) {
        return r != null && state.chests.contains(r.id);
    }

    /** 상자는 귀환 포탈 오른쪽 옆에 놓인다 (바닥이 아니면 왼쪽). */
    public float chestX(Room r) {
        int cx = (int) r.centerTileX(), cy = (int) r.centerTileY();
        int dx = r.floor(cx + 3, cy) ? 3 : -3;
        return center(cx + dx);
    }

    public float chestY(Room r) {
        return center((int) r.centerTileY());
    }

    /** 상자를 열면 그 던전 광물을 무작위로 받는다. */
    public void openChest(Room r) {
        if (!hasChest(r)) return;
        state.chests.remove(r.id);
        OreType ore = OreType.valueOf(balance.dungeon(r.dungeonId).ore);
        int n = balance.reward.chestOreMin + random.nextInt(balance.reward.chestOreMax - balance.reward.chestOreMin + 1);
        state.addOre(ore, n);
        GameEvent e = new GameEvent(GameEvent.Type.CHEST_OPENED);
        e.ore = ore;
        e.amount = n;
        e.x = chestX(r);
        e.y = chestY(r) + T;
        events.add(e);
        events.add(GameEvent.floatText(e.x, e.y, ore.displayName + " +" + n, GameEvent.Tone.ORE));
        events.add(GameEvent.sound("ore"));
        events.add(new GameEvent(GameEvent.Type.AUTOSAVE));
    }

    /** 침대에 눕는다: 화면이 어두워지는 동안 쉬고 (useBed) 다시 밝아지면 일어난다. */
    public void startSleep(Prop bed) {
        if (sleepTime > 0) return;
        sleepTime = 0.0001f;
        slept = false;
        sleepBed = bed;
        events.add(GameEvent.sound("select"));
    }

    public boolean sleeping() {
        return sleepTime > 0;
    }

    /** 0~1: 잠든 정도의 진행 (그리는 쪽에서 어둡게 하고 Zzz 를 띄운다). */
    public float sleepProgress() {
        return sleepTime > 0 ? Math.min(1f, sleepTime / SLEEP_TIME) : 0f;
    }

    /** 자고 있는 침대 (자고 있지 않으면 null). */
    public Prop sleepBed() {
        return sleepTime > 0 ? sleepBed : null;
    }

    /**
     * 침대: 체력을 채우고, 모든 던전의 잡몹·광맥과 (마왕이 아닌) 보스를 다시 채운다 (FR-던전-05, 선우 버전 보스 재생성).
     * 다시 잡아도 힘(최대 체력)은 한 번만 오르고, 상자는 매번 나온다. 열지 않은 상자가 있는 보스방은 그대로 둔다.
     */
    public void useBed() {
        state.hp = rules.maxHp(state);
        for (Room r : map.rooms) {
            RoomRecord rec = state.room(r.id);
            if (r.type == RoomType.BOSS && hasChest(r)) continue;
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
        return r.type == RoomType.BOSS && state.bossDefeated(r.dungeonId) && state.room(r.id).cleared
                && !revived.contains(r.dungeonId);
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

    /**
     * 개발용: 보스방(dungeonId 가 "FINAL" 이면 심연) 입구로 바로 옮긴다. 조금 걸어 들어가면 싸움이 시작된다.
     * 싸우는 중이면 옮기지 않는다. 옮겼으면 true.
     */
    public boolean devWarpToBoss(String dungeonId) {
        if (fightRoom != null) return false;
        Room target = null;
        for (Room r : map.rooms) {
            boolean match = "FINAL".equals(dungeonId) ? r.type == RoomType.FINAL
                    : r.type == RoomType.BOSS && dungeonId.equals(r.dungeonId);
            if (match) target = r;
        }
        if (target == null) return false;
        Door in = null;
        for (Door d : target.doors) if (!d.dead()) in = d;
        hero.x = in != null ? in.arriveX : target.spawnX(T);
        hero.y = in != null ? in.arriveY : target.spawnY(T);
        hero.aimX = in != null ? -in.dirX : 0;
        hero.aimY = in != null ? -in.dirY : 1;
        hero.facing = Facing.of(hero.aimX, hero.aimY);
        hero.rollT = 0;
        hero.kbT = 0;
        hero.slowT = 0;
        hazards.clear();
        projectiles.clear();
        shots.clear();
        pendingDoor = null;
        doorCd = 0.35f;
        return true;
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

    /** 마왕이 기다리는 심연. 봉인을 풀면 캠프 아래 계단과도 이어진다. */
    public Room abyss() {
        for (Room r : map.rooms) if (r.type == RoomType.FINAL) return r;
        return null;
    }

    /** 캠프 문양에서 땅이 갈라져 심연으로 떨어진다. 떨어진 자리에서 조금 걸어 들어가면 싸움이 시작된다. */
    public void dropToAbyss() {
        Room f = abyss();
        if (f == null || fightRoom != null) return;
        hero.x = f.spawnX(T);
        hero.y = f.spawnY(T);
        hero.facing = Facing.UP;
        hero.aimX = 0;
        hero.aimY = 1;
        hero.rollT = 0;
        hero.kbT = 0;
        heroRoom = currentRoom = f;
        state.room(f.id).visited = true;
        setEntry(null);
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
        if (heroRoom != null && heroRoom.type == RoomType.FINAL) {
            // 심연에서는 캠프(문양 옆)에서 이어 한다
            Room camp = map.room("CAMP");
            state.resumeX = camp.scene == null ? center((int) camp.centerTileX()) : camp.spawnX(T);
            state.resumeY = camp.scene == null ? center((int) camp.centerTileY() - 2) : camp.spawnY(T);
        } else if (inCamp() || inVillage()) {
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
