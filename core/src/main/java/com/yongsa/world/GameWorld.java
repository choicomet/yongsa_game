package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.yongsa.combat.Arena;
import com.yongsa.combat.AttackInstance;
import com.yongsa.combat.BossPattern;
import com.yongsa.combat.Dir8;
import com.yongsa.combat.Enemy;
import com.yongsa.combat.Hero;
import com.yongsa.combat.HitShape;
import com.yongsa.combat.MonsterAI;
import com.yongsa.combat.Physics;
import com.yongsa.combat.Projectile;
import com.yongsa.combat.Statuses;
import com.yongsa.config.Balance;
import com.yongsa.model.DamageCalc;
import com.yongsa.model.GameState;
import com.yongsa.model.Ids;
import com.yongsa.model.MineRoomState;
import com.yongsa.model.Modifiers;
import com.yongsa.model.Progress;
import com.yongsa.model.VeinState;
import com.yongsa.model.WeaponState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 던전 한 판의 실행 상태와 시뮬레이션.
 * 일정한 간격(step)으로만 갱신하며, 갱신 순서는
 * 입력과 이동 → 공격 판정 → 피해와 사망 → 방 완료와 보상 → 저장 요청 으로 고정한다.
 * 그래픽에 의존하지 않으므로 테스트에서 그대로 실행할 수 있다.
 */
public class GameWorld implements Arena {

    public static final float STEP = 1f / 60f;

    /** 진행 중인 전투. 실패하면 통째로 버려져 방이 처음 상태로 돌아간다. */
    public static class Combat {
        public final RoomDef room;
        public final boolean boss;
        public final List<Enemy> enemies = new ArrayList<>();
        public final List<Projectile> projectiles = new ArrayList<>();
        public float intro;
        public float introTotal;
        /** 보스 사망 연출. dying 이 되면 전투는 멈추고, outro 가 끝나면 보상을 준다. */
        public boolean dying;
        public float outro;
        public float outroTotal;
        /** 이 방의 웨이브 구성 (보스방은 비어 있다)과 다음에 부를 웨이브 번호. */
        public List<List<Waves.Spawn>> waves = Collections.emptyList();
        public int waveIndex;
        int lastWaveSize;
        /** 바닥에 떠 있는 소환 표식. */
        public final List<SpawnMark> marks = new ArrayList<>();
        /** 강화 개체 처치로 방 돌파 보상에 더해질 광물. */
        public int bonusReward;

        Combat(RoomDef room, boolean boss) {
            this.room = room;
            this.boss = boss;
        }

        public int wavesLeft() {
            return waves.size() - waveIndex;
        }

        /** 적, 표식, 남은 웨이브가 모두 없으면 방을 돌파한 것이다. */
        public boolean finished() {
            return enemies.isEmpty() && marks.isEmpty() && wavesLeft() <= 0;
        }

        public Enemy bossEnemy() {
            if (!boss) return null;
            for (Enemy e : enemies) if (e.isBoss()) return e;
            return null;
        }

        public boolean started() {
            return intro <= 0;
        }
    }

    /** 적이 나오기 전에 바닥에 뜨는 표식. 표식이 떠 있는 동안에는 맞지도 때리지도 않는다. */
    public static class SpawnMark {
        public final float x, y;
        public final Waves.Spawn spawn;
        public final float total;
        public float t;

        SpawnMark(float x, float y, Waves.Spawn spawn, float time) {
            this.x = x;
            this.y = y;
            this.spawn = spawn;
            this.total = time;
            this.t = time;
        }
    }

    public enum FocusKind { SHORTCUT, RETURN, VEIN, CHEST, SHRINE }

    /** E 안내 대상. 안내 대상과 실제 실행 대상은 항상 같다. */
    public static class Focus {
        public String id;
        public FocusKind kind;
        public String label;
        public String zone;
        public float x, y;
        public boolean enabled = true;
    }

    public final Balance b;
    public final DungeonMap map;
    public final GameState state;
    public final Hero hero;
    public Combat combat;
    /** 용사의 발이 있는 방 (통로에서는 null). */
    public RoomDef room;
    public String lastZone = Ids.CAMP;
    public Focus focus;
    public String miningVein;
    public float miningProgress;
    public DoorDef hintDoor;
    public float time;
    public boolean saveRequested;
    public final List<WorldEvent> events = new ArrayList<>();
    /** 개발자 모드: 다치지 않는다 / 해금 조건을 무시한다(충격파 등). 저장되지 않는다. */
    public boolean devInvincible, devUnlockAll;
    /** 지금 받은 축복을 합친 효과. 전투 코드는 축복 이름이 아니라 이 수치만 본다. */
    public Modifiers mods = new Modifiers();

    /** 상자가 열리고 회복약에 손이 닿는 거리(px). */
    private static final float CHEST_RANGE = 44f;
    private final Vector2 chestTmp = new Vector2();
    private final Vector2 spawn;
    private final float[] pos = new float[2];
    private final Rectangle tmp = new Rectangle();

    public GameWorld(Balance b, DungeonMap map, GameState state) {
        this.b = b;
        this.map = map;
        this.state = state;
        this.hero = new Hero(b.hero);
        this.spawn = map.point("camp_spawn");
        placeHero(spawn.x, spawn.y);
        room = map.roomAt(hero.x, hero.y);
        refreshModifiers();
        hero.hp = hero.maxHp;
    }

    /** 축복이 바뀔 때마다 부른다. */
    public void refreshModifiers() {
        mods = Modifiers.of(b, state);
        hero.maxHp = b.hero.maxHp + mods.maxHpAdd;
        hero.hp = Math.min(hero.hp, hero.maxHp);
    }

    public float miningHoldTime() {
        return b.mining.holdTime * mods.miningTimeMul;
    }

    // =====================================================================
    // 시뮬레이션 한 간격
    // =====================================================================

    public void step(float dt, PlayerInput in) {
        time += dt;
        if (hero.dead) {
            hero.deathTimer += dt;
            if (hero.deathTimer >= b.hero.deathTime) respawn();
            return;
        }
        // 1. 입력과 이동
        updateHero(dt, in);
        updateRooms();
        updateInteraction(dt, in);
        if (combat != null) updateCombat(dt);
        // 2. 공격 판정
        resolveHeroAttack();
        updateProjectiles(dt);
        resolveEnemyAttacks();
        resolveContact();
        // 3. 피해와 사망 (사망이 방 클리어보다 우선)
        if (hero.hp <= 0) die();
        if (combat != null) removeDead();
        // 4. 방 완료와 보상
        if (!hero.dead && combat != null && combat.started() && combat.finished()) completeCombat();
        // 5. 저장 요청은 saveRequested 로 화면에 전달된다
    }

    // ---- 용사 ----

    private void updateHero(float dt, PlayerInput in) {
        hero.animTime += dt;
        hero.attackBuffer = Math.max(0, hero.attackBuffer - dt);
        hero.shockBuffer = Math.max(0, hero.shockBuffer - dt);
        hero.comboTimer = Math.max(0, hero.comboTimer - dt);
        if (hero.comboTimer == 0 && hero.attack == null) hero.combo = 0;
        hero.dodgeCooldown = Math.max(0, hero.dodgeCooldown - dt);
        boolean wasDodging = hero.dodgeTime > 0;
        hero.dodgeTime = Math.max(0, hero.dodgeTime - dt);
        hero.dodgeSafe = Math.max(0, hero.dodgeSafe - dt);
        if (wasDodging && hero.dodgeTime == 0) events.add(new WorldEvent(WorldEvent.Type.DODGE, null, hero.x, hero.y, 0));
        if (in.attackPressed) hero.attackBuffer = b.hero.inputBuffer;
        if (in.shockwavePressed) hero.shockBuffer = b.hero.inputBuffer;
        if (hero.invuln > 0) hero.invuln = Math.max(0, hero.invuln - dt);
        if (hero.hurtFlash > 0) hero.hurtFlash -= dt;
        if (hero.shockCooldown > 0) hero.shockCooldown = Math.max(0, hero.shockCooldown - dt);

        float mx = in.moveX, my = in.moveY;
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len > 1e-3f) {
            mx /= len;
            my /= len;
        }
        if (len > 1e-3f) hero.moveDir = Dir8.fromVector(mx, my, hero.moveDir, 0.01f);
        updateAim(in);
        if (in.dodgePressed && hero.dodgeCooldown <= 0 && hero.kbTime <= 0) {
            int dir = len > 1e-3f ? hero.moveDir : hero.aimDir;
            hero.dodgeDx = Dir8.DX[dir]; hero.dodgeDy = Dir8.DY[dir];
            hero.dodgeTime = b.hero.dodgeDuration;
            hero.dodgeSafe = Math.max(b.hero.dodgeDuration, b.hero.dodgeInvuln);
            hero.dodgeCooldown = b.hero.dodgeCooldown * mods.dodgeCooldownMul;
            hero.attack = null;
            hero.attackBuffer = hero.shockBuffer = 0;
            events.add(new WorldEvent(WorldEvent.Type.DODGE, null, hero.x, hero.y, 0));
            sfx("dash");
        }
        float oldX = hero.x, oldY = hero.y;
        if (hero.dodgeTime > 0) {
            moveHero(hero.dodgeDx * b.hero.dodgeSpeed * dt, hero.dodgeDy * b.hero.dodgeSpeed * dt);
        } else if (hero.kbTime > 0) {
            float s = Math.min(dt, hero.kbTime);
            hero.kbTime -= s;
            moveHero(hero.kbVx * s, hero.kbVy * s);
        } else if (len > 1e-3f) {
            // Recovery must never feel like walking through mud while J is held.
            boolean swinging = hero.attack != null && hero.attack.phase() != AttackInstance.Phase.RECOVERY;
            float speed = b.hero.moveSpeed * mods.moveSpeedMul * (swinging ? b.hero.attackMoveFactor : 1f);
            moveHero(mx * speed * dt, my * speed * dt);
        }
        float distance = Vector2.dst(oldX, oldY, hero.x, hero.y);
        hero.moving = distance > 0.01f;
        if (hero.moving) hero.walkTime += distance / b.hero.moveSpeed;
        else hero.walkTime = 0;

        // 진행 중인 공격
        if (hero.attack != null) {
            hero.attack.t += dt;
            if (hero.attack.phase() == AttackInstance.Phase.DONE) hero.attack = null;
        }
        // A short input buffer catches quick taps between fixed simulation frames.
        WeaponState w = state.equippedWeapon();
        if (hero.attack == null && w != null && hero.dodgeTime <= 0) {
            if (hero.shockBuffer > 0 && canShockwave()) {
                startShockwave(w);
                hero.shockBuffer = 0;
            } else if (in.attackHeld || hero.attackBuffer > 0) {
                startAttack(w);
                hero.attackBuffer = 0;
            }
        }
        if (hero.attack != null) hero.facing = hero.attack.dir;
        else if (hero.target != null) hero.facing = hero.aimDir;
        else if (hero.moving) hero.facing = hero.moveDir;
        if (hero.attack != null) hero.faceLeft = Dir8.facesLeft(hero.attack.dir);
        else if (Math.abs(mx) > 0.01f) hero.faceLeft = mx < 0;
    }

    private void updateAim(PlayerInput in) {
        hero.target = null;
        if (!in.keyboardAim) {
            hero.aimDir = Dir8.fromVector(in.aimX - hero.x, in.aimY - hero.hurtY(), hero.aimDir, 6f);
            return;
        }
        float best = b.hero.autoAimRange;
        if (combat != null) for (Enemy e : combat.enemies) {
            if (!e.alive()) continue;
            float dx = e.x - hero.x, dy = e.hurtY() - hero.hurtY();
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            if (dist >= best || rayForHero(hero.x, hero.hurtY(), dx / Math.max(dist, 1), dy / Math.max(dist, 1), dist) < dist - 4) continue;
            best = dist;
            hero.target = e;
        }
        hero.aimDir = hero.target == null ? hero.moveDir
                : Dir8.fromVector(hero.target.x - hero.x, hero.target.hurtY() - hero.hurtY(), hero.aimDir, 1f);
    }

    public void clearBufferedActions() {
        hero.attackBuffer = hero.shockBuffer = 0;
    }

    public boolean canShockwave() {
        return (devUnlockAll || Progress.isAwakeningUnlocked(b, state)) && state.equippedWeapon() != null
                && hero.shockCooldown <= 0 && hero.attack == null && !hero.dead;
    }

    private void startAttack(WeaponState w) {
        Balance.WeaponDef def = b.weapon(w.type);
        hero.combo = hero.combo % def.comboHits + 1;
        boolean finisher = def.comboHits > 1 && hero.combo == def.comboHits;
        int damage = Math.round(DamageCalc.weaponDamage(b, w) * mods.damageMul);
        if (finisher) damage = Math.round(damage * b.hero.finisherMultiplier);
        float windup = finisher ? def.windup + 0.05f : def.windup;
        // 강화 단계가 연 무기 형태: 범위·검기·원형 충격·상태 이상을 한 줄의 수치로 받는다
        Balance.FormDef form = b.form(w.type, w.level);
        if (form != null && form.finisherOnly && !finisher) form = null;
        float reach = def.reach * (form == null ? 1f : form.reachMul);
        float width = def.width * (form == null ? 1f : form.widthMul);
        hero.attack = new AttackInstance(AttackInstance.Kind.NORMAL, hero.aimDir, windup, def.active, def.recovery,
                damage, reach, width);
        hero.attack.combo = hero.combo;
        hero.attack.finisher = finisher;
        hero.attack.slam = finisher && "slam".equals(def.finisherMotion);
        hero.attack.form = form;
        hero.comboTimer = def.cycle() + b.hero.comboWindow;
        sfx("swing_" + w.type);
    }

    private void startShockwave(WeaponState w) {
        Balance.ShockwaveDef sw = b.shockwave;
        hero.attack = new AttackInstance(AttackInstance.Kind.SHOCKWAVE, hero.aimDir, sw.windup, sw.active, sw.recovery,
                Math.round(DamageCalc.shockwaveDamage(b, w) * mods.damageMul), 0, 0);
        hero.shockCooldown = sw.cooldown; // 시전이 시작되면 재사용 시간 계산 시작
        sfx("shockwave_charge");
    }

    private void moveHero(float dx, float dy) {
        pos[0] = hero.x;
        pos[1] = hero.y;
        Physics.move(pos, b.hero.bodyWidth, b.hero.bodyHeight, dx, dy, this::blockedForHero);
        hero.x = pos[0];
        hero.y = pos[1];
    }

    private void placeHero(float x, float y) {
        hero.x = x;
        hero.y = y;
        hero.kbTime = 0;
    }

    public boolean blockedForHero(Rectangle r) {
        if (map.overlapsStatic(r)) return true;
        for (DoorDef d : map.doors) if (d.bounds.overlaps(r) && isDoorLocked(d)) return true;
        for (MineRoomState m : state.mineRooms) {
            for (VeinState v : m.veins) {
                if (v.mined) continue;
                MapObject o = map.vein(v.candidateId);
                if (o != null && VeinGeometry.solidRect(o.x(), o.y()).overlaps(r)) return true;
            }
        }
        return false;
    }

    // ---- 방과 문 ----

    private void updateRooms() {
        RoomDef r = map.roomAt(hero.x, hero.y);
        room = r;
        if (r == null) return;
        lastZone = r.zone;
        if (!state.visitedRooms.contains(r.id)) state.visitedRooms.add(r.id);
        Rectangle body = hero.body();
        boolean fullyInside = body.x >= r.bounds.x + 6 && body.y >= r.bounds.y + 6
                && body.x + body.width <= r.bounds.x + r.bounds.width - 6
                && body.y + body.height <= r.bounds.y + r.bounds.height - 6;

        if (r.veins) {
            boolean existed = state.mineRoom(r.id) != null;
            VeinGenerator.ensure(b, state, map, r.id);
            if (!existed) saveRequested = true;
        }
        switch (r.kind) {
            case ROOM:
                if (r.enemies && combat == null && fullyInside && !state.isRoomCleared(r.id)) startBattle(r);
                break;
            case BOSS:
                if (combat == null && fullyInside && !state.isBossDefeated(b.zone(r.zone).boss)) startBoss(r);
                break;
            case ANTE:
                if (Progress.onAntechamberReached(state, r.zone)) {
                    events.add(WorldEvent.text(WorldEvent.Type.SHORTCUT_UNLOCKED, b.zone(r.zone).name));
                    toast("중앙 캠프의 " + b.zone(r.zone).name + " 지름길이 열렸습니다");
                    saveRequested = true;
                }
                break;
            default:
                break;
        }
    }

    private void startBattle(RoomDef r) {
        combat = new Combat(r, false);
        combat.waves = Waves.plan(b, r, state.dungeonSeed);
        startWave();
        events.add(WorldEvent.of(WorldEvent.Type.COMBAT_STARTED));
        sfx("door_lock");
    }

    /** 다음 웨이브의 표식을 띄운다. 용사에게서 먼 생성점부터 쓴다. */
    private void startWave() {
        List<Waves.Spawn> wave = combat.waves.get(combat.waveIndex++);
        combat.lastWaveSize = wave.size();
        List<MapObject> slots = new ArrayList<>(map.spawnsIn(combat.room.id));
        float min = b.dungeon.spawnMinDistance;
        slots.sort((p, q) -> Boolean.compare(Vector2.dst(p.x(), p.y(), hero.x, hero.y) < min,
                Vector2.dst(q.x(), q.y(), hero.x, hero.y) < min));
        for (int i = 0; i < wave.size(); i++) {
            MapObject s = slots.get(i % slots.size());
            // 생성점보다 적이 많으면 옆으로 조금 비켜서 겹치지 않게 한다
            float x = s.x() + (i / slots.size()) * 30f;
            combat.marks.add(new SpawnMark(x, s.y(), wave.get(i), b.dungeon.spawnMarkTime));
        }
        if (combat.waveIndex > 1) {
            events.add(new WorldEvent(WorldEvent.Type.WAVE_STARTED, null, hero.x, hero.y, combat.wavesLeft()));
            sfx("shockwave_charge");
        }
    }

    private void updateWaves(float dt) {
        for (Iterator<SpawnMark> it = combat.marks.iterator(); it.hasNext(); ) {
            SpawnMark m = it.next();
            m.t -= dt;
            if (m.t > 0) continue;
            it.remove();
            Enemy e = new Enemy(m.spawn.def, m.x, m.y, m.spawn.elite ? b.elite : null);
            if (e.isElite()) e.tint = b.zone(combat.room.zone).eliteTint;
            e.grace = b.enemyCombat.spawnGrace;
            e.faceLeft = hero.x < e.x;
            combat.enemies.add(e);
            events.add(new WorldEvent(WorldEvent.Type.ENEMY_SPAWNED, e.id, e.x, e.y, e.isElite() ? 1 : 0));
        }
        if (combat.wavesLeft() <= 0 || !combat.marks.isEmpty()) return;
        // 남은 적이 nextAt 이하가 되면 다음 웨이브. 한 마리짜리 웨이브가 곧바로 다음을 부르지 않게 한다.
        int nextAt = Math.min(b.zone(combat.room.zone).waves.nextAt, combat.lastWaveSize - 1);
        int alive = 0;
        for (Enemy e : combat.enemies) if (e.alive()) alive++;
        if (alive <= Math.max(0, nextAt)) startWave();
    }

    private void startBoss(RoomDef r) {
        combat = new Combat(r, true);
        String bossId = b.zone(r.zone).boss;
        MapObject s = map.spawnsIn(r.id).get(0);
        Enemy boss = new Enemy(b.boss(bossId), s.x(), s.y());
        boss.faceLeft = true;
        combat.enemies.add(boss);
        combat.introTotal = b.bossIntro.cameraTime + b.bossIntro.readyTime;
        combat.intro = combat.introTotal;
        // 보스방 입장: 충격파 즉시 사용 가능 (체력은 채워 주지 않는다. 회복은 보스를 쓰러뜨렸을 때)
        hero.shockCooldown = 0;
        events.add(WorldEvent.text(WorldEvent.Type.BOSS_INTRO, boss.name()));
        sfx("door_lock");
    }

    public boolean isDoorLocked(DoorDef d) {
        if (combat != null && d.combatRooms.contains(combat.room.id)) return true;
        return ruleLockReason(d) != null;
    }

    /** 규칙에 의한 잠김 이유. 열려 있으면 null. 전투 잠김은 포함하지 않는다. */
    public String ruleLockReason(DoorDef d) {
        String rule = d.rule;
        if (rule.isEmpty()) return null;
        if (rule.startsWith("zone:")) return Progress.zoneLockReason(b, state, rule.substring(5));
        if (rule.equals("weapon")) {
            return state.hasAnyWeapon() ? null : "무기가 필요합니다";
        }
        if (rule.startsWith("cleared:")) {
            return state.isRoomCleared(rule.substring(8)) ? null : "앞 전투방을 먼저 돌파해야 합니다";
        }
        return null;
    }

    public String doorLockReason(DoorDef d) {
        String r = ruleLockReason(d);
        if (r != null) return r;
        if (combat != null && d.combatRooms.contains(combat.room.id)) return "전투 중에는 나갈 수 없습니다";
        return null;
    }

    public boolean isZoneGate(DoorDef d) {
        return d.rule.startsWith("zone:");
    }

    // ---- 상호작용과 채굴 ----

    private void updateInteraction(float dt, PlayerInput in) {
        focus = findFocus();
        hintDoor = null;
        float best = 72f;
        for (DoorDef d : map.doors) {
            if (!isDoorLocked(d)) continue;
            float dist = distToRect(d.bounds, hero.x, hero.y);
            if (dist < best) {
                best = dist;
                hintDoor = d;
            }
        }

        boolean moved = Math.abs(in.moveX) > 0.01f || Math.abs(in.moveY) > 0.01f;
        if (focus != null && focus.kind == FocusKind.VEIN && in.interactHeld && !moved && hero.attack == null) {
            if (!focus.id.equals(miningVein)) {
                miningVein = focus.id;
                miningProgress = 0;
            }
            miningProgress += dt;
            if (miningProgress >= miningHoldTime()) completeMining(focus.id);
        } else {
            miningVein = null;
            miningProgress = 0;
        }

        // 상자는 다가가면 열리고 골드가 들어온다. 회복약은 그 자리에 남는다.
        if (combat == null && hasChest(room) && !hero.dead) {
            Vector2 c = chestSpot(room, chestTmp);
            if (Vector2.dst(c.x, c.y, hero.x, hero.y) <= CHEST_RANGE) openChest(room);
        }
        if (in.interactPressed && focus != null && focus.kind != FocusKind.VEIN) activate(focus);
    }

    private Focus findFocus() {
        Focus best = null;
        float bestDist = Float.MAX_VALUE;
        for (MapObject o : map.interacts) {
            float d = distToRect(o.bounds, hero.x, hero.y);
            if (d > 28f) continue;
            Focus f = makeFocus(o);
            if (f == null) continue;
            if (d < bestDist || (d == bestDist && best != null && f.id.compareTo(best.id) < 0)) {
                best = f;
                bestDist = d;
            }
        }
        // 상자에서 나와 바닥에 남아 있는 회복약
        Balance.ChestHealDef heal = combat == null ? pendingHeal(room) : null;
        if (heal != null) {
            Vector2 c = chestSpot(room, chestTmp);
            float d = Vector2.dst(c.x, c.y, hero.x, hero.y);
            if (d <= CHEST_RANGE && d < bestDist) {
                Focus f = new Focus();
                f.id = room.id;
                f.kind = FocusKind.CHEST;
                f.enabled = hero.hp < hero.maxHp;
                f.label = f.enabled ? "[L] " + heal.name + " 마시기" : heal.name;
                f.x = c.x;
                f.y = c.y + 30;
                best = f;
                bestDist = d;
            }
        }
        // 광맥은 전투가 끝난 방에서만 캘 수 있다
        if (room != null && room.veins && combat == null) {
            MineRoomState m = state.mineRoom(room.id);
            if (m != null) {
                for (VeinState v : m.veins) {
                    if (v.mined) continue;
                    MapObject o = map.vein(v.candidateId);
                    if (o == null) continue;
                    float dx = o.x() - hero.x, dy = o.y() - hero.y;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d > b.mining.range) continue;
                    if (d < bestDist || (d == bestDist && best != null && o.name.compareTo(best.id) < 0)) {
                        Focus f = new Focus();
                        f.id = o.name;
                        f.kind = FocusKind.VEIN;
                        f.label = "[L] 길게 눌러 채굴";
                        f.x = o.x();
                        f.y = o.y() + 30;
                        best = f;
                        bestDist = d;
                    }
                }
            }
        }
        return best;
    }

    private Focus makeFocus(MapObject o) {
        Focus f = new Focus();
        f.id = o.name;
        f.x = o.x();
        f.y = o.bounds.y + o.bounds.height + 6;
        f.zone = o.prop("zone");
        switch (o.type) {
            case "shortcut": {
                f.kind = FocusKind.SHORTCUT;
                String zn = b.zone(f.zone).name;
                if (state.hasShortcut(f.zone)) {
                    f.label = "[L] 지름길 · " + zn + " 보스 앞";
                } else {
                    f.label = zn + " 지름길 · 보스 앞 도달 시 개방";
                    f.enabled = false;
                }
                break;
            }
            case "return":
                f.kind = FocusKind.RETURN;
                f.label = "[L] 중앙 캠프로 돌아가기";
                break;
            case "shrine": {
                // 전투 중에는 안내를 띄우지 않는다 (싸움을 가리지 않게)
                if (combat != null || state.usedShrines.contains(o.name)) return null;
                f.kind = FocusKind.SHRINE;
                Balance.ShrineDef sh = b.zone(f.zone).shrine;
                String block = shrineBlock(o, sh);
                f.label = block == null ? "[L] " + sh.name + "에 바치기 · 골드 " + sh.costGold : sh.name + " · " + block;
                f.enabled = block == null;
                break;
            }
            default:
                return null;
        }
        return f;
    }

    /** 전투가 없고, 적이 있는 방이라면 이미 돌파한 방인지. */
    private boolean roomSafe(String roomId) {
        if (combat != null) return false;
        RoomDef r = map.room(roomId);
        return r == null || !r.enemies || state.isRoomCleared(roomId);
    }

    /** 제단에 바칠 수 없는 이유. 바칠 수 있으면 null. */
    private String shrineBlock(MapObject o, Balance.ShrineDef sh) {
        if (!roomSafe(o.prop("room"))) return "방을 돌파하면 바칠 수 있습니다";
        if (shrineChoices(sh).isEmpty()) return "더 받을 축복이 없습니다";
        if (state.gold < sh.costGold) return "골드 " + sh.costGold + "이 필요합니다";
        return null;
    }

    private List<String> shrineChoices(Balance.ShrineDef sh) {
        List<String> out = new ArrayList<>();
        for (String id : sh.blessings) if (!state.blessings.contains(id)) out.add(id);
        return out;
    }

    /** 방을 돌파하면 방 가운데에 상자가 나타난다. 아직 열지 않은 상자가 있는 방인지. */
    public boolean hasChest(RoomDef r) {
        if (r == null || state.openedChests.contains(r.id)) return false;
        if (r.kind == RoomDef.Kind.BOSS) return state.isBossDefeated(b.zone(r.zone).boss);
        return r.kind == RoomDef.Kind.ROOM && r.enemies && state.isRoomCleared(r.id);
    }

    /** 상자가 놓이는 자리 (방 가운데, 제단이 있는 방은 그 아래). */
    public Vector2 chestSpot(RoomDef r, Vector2 out) {
        return out.set(r.centerX(), r.centerY() - ("shrine".equals(r.feature) ? 2.4f * map.tileSize : 0));
    }

    public int chestGold(RoomDef r) {
        Balance.ZoneDef z = b.zone(r.zone);
        return r.kind == RoomDef.Kind.BOSS ? z.bossGold : r.guard ? z.guardChestGold : z.chestGold;
    }

    /**
     * 이 방의 상자에 든 회복약. 없으면 null. 같은 씨앗값·같은 방이면 항상 같다.
     * 보스방 상자에는 없다 (보스를 쓰러뜨리면 체력이 모두 찬다).
     */
    public Balance.ChestHealDef chestHeal(RoomDef r) {
        if (r.kind == RoomDef.Kind.BOSS) return null;
        // 비슷한 씨앗값끼리는 첫 난수가 비슷하게 나오므로, 섞은 뒤 한 번 버리고 쓴다
        Random rnd = new Random(state.dungeonSeed * 0x9E3779B97F4A7C15L ^ r.id.hashCode() * 0xC2B2AE3D27D4EB4FL);
        rnd.nextInt();
        float roll = rnd.nextFloat();
        for (Balance.ChestHealDef h : b.chestHeals) {
            if (roll < h.chance) return h;
            roll -= h.chance;
        }
        return null;
    }

    /** 상자가 열리고 회복약이 아직 그 자리에 남아 있으면 그 회복약. 없으면 null. */
    public Balance.ChestHealDef pendingHeal(RoomDef r) {
        if (r == null || !state.openedChests.contains(r.id) || state.takenHeals.contains(r.id)) return null;
        return chestHeal(r);
    }

    /** 상자를 연다: 골드만 들어온다. 회복약이 들어 있었다면 바닥에 남는다. */
    private void openChest(RoomDef r) {
        int gold = Progress.onChestOpened(state, r.id, chestGold(r));
        if (gold <= 0) return;
        Vector2 p = chestSpot(r, new Vector2());
        events.add(new WorldEvent(WorldEvent.Type.CHEST_OPENED, "+" + gold, p.x, p.y + 20, gold));
        sfx("clear");
        saveRequested = true;
    }

    /** 남겨 둔 회복약을 마신다. 체력이 가득하면 마시지 않고 그대로 둔다. */
    private void drinkHeal(RoomDef r) {
        Balance.ChestHealDef heal = pendingHeal(r);
        if (heal == null || hero.hp >= hero.maxHp) return;
        int before = hero.hp;
        hero.hp = Math.min(hero.maxHp, hero.hp + Math.round(hero.maxHp * heal.heal));
        state.takenHeals.add(r.id);
        Vector2 p = chestSpot(r, new Vector2());
        events.add(new WorldEvent(WorldEvent.Type.CHEST_HEAL, heal.id, p.x, p.y + 20, hero.hp - before));
        sfx("enhance_success");
        saveRequested = true;
    }

    /** 골드를 바치고 축복 하나를 받는다. 같은 제단은 항상 같은 순서로 축복을 준다. */
    private void useShrine(MapObject o) {
        Balance.ShrineDef sh = b.zone(o.prop("zone")).shrine;
        List<String> choices = shrineChoices(sh);
        String id = choices.get(new Random(state.dungeonSeed ^ o.name.hashCode()).nextInt(choices.size()));
        state.gold -= sh.costGold;
        state.blessings.add(id);
        state.usedShrines.add(o.name);
        refreshModifiers();
        events.add(WorldEvent.text(WorldEvent.Type.BLESSING, id));
        sfx("enhance_success");
        saveRequested = true;
    }

    private void activate(Focus f) {
        if (!f.enabled && f.kind == FocusKind.SHRINE) {
            toast(f.label);
            return;
        }
        switch (f.kind) {
            case CHEST:
                drinkHeal(map.room(f.id)); // 체력이 가득하면 아무 일도 없다 (회복약은 그대로 남는다)
                break;
            case SHRINE:
                useShrine(map.interact(f.id));
                break;
            case SHORTCUT:
                if (!state.hasShortcut(f.zone)) {
                    toast(b.zone(f.zone).name + " 보스 앞 공간에 도달하면 열립니다");
                    return;
                }
                teleport(map.point("shortcut_" + f.zone + "_ante"));
                break;
            case RETURN:
                teleport(map.point("shortcut_" + f.zone + "_camp"));
                break;
            default:
                break;
        }
    }

    private void completeMining(String candidateId) {
        MineRoomState m = room == null ? null : state.mineRoom(room.id);
        VeinState v = m == null ? null : m.vein(candidateId);
        miningVein = null;
        miningProgress = 0;
        if (v == null || v.mined) return;
        v.mined = true;
        int amount = b.zone(room.zone).veinYield;
        state.addOre(b.zoneIndex(room.zone), amount);
        MapObject o = map.vein(candidateId);
        events.add(new WorldEvent(WorldEvent.Type.MINED, room.zone, o.x(), o.y() + 20, amount));
        sfx("mine_done");
        saveRequested = true;
    }

    private void teleport(Vector2 p) {
        placeHero(p.x, p.y);
        miningVein = null;
        miningProgress = 0;
        room = map.roomAt(hero.x, hero.y);
        if (room != null) lastZone = room.zone;
        events.add(WorldEvent.of(WorldEvent.Type.TELEPORT));
        sfx("teleport");
    }

    // ---- 전투 ----

    private void updateCombat(float dt) {
        if (combat.intro > 0) {
            combat.intro -= dt;
            return;
        }
        if (combat.dying) {
            combat.outro = Math.max(0, combat.outro - dt);
            return;
        }
        updateWaves(dt);
        int minions = combat.marks.size();
        if (combat.boss) for (Enemy e : combat.enemies) if (!e.isBoss() && e.alive()) minions++;
        for (Enemy e : combat.enemies) {
            Enemy.State previous = e.state;
            e.minionsAlive = minions;
            if (e.isBoss() && e.kbTime > 0) {
                float s = Math.min(dt, e.kbTime);
                e.kbTime -= s;
                nudge(e, e.kbVx * s, e.kbVy * s);
            }
            if (e.isBoss()) BossPattern.update(e, this, b, dt);
            else MonsterAI.update(e, this, b.enemyCombat, dt);
            if (previous == Enemy.State.TELEGRAPH && e.state == Enemy.State.ATTACK) {
                // 공격과 함께 쏘는 탄은 적·보스 공격 정의(shots)에 적혀 있다
                Balance.BossAttackDef atk = e.isBoss() ? b.bossAttack(e.attackId) : null;
                Balance.ShotDef shots = atk != null ? atk.shots : e.def.shots;
                if (shots != null) fire(e, shots);
                if (atk != null && atk.summon != null) {
                    summoned.add(e);
                    e.summonedOnce = true;
                    e.attacksSinceClear = 0;
                } else if (atk != null && minions == 0) e.attacksSinceClear++;
            }
            updateBursts(e, dt);
        }
        // 소환은 적 목록을 도는 중에 바꾸지 않도록 뒤에 처리한다
        for (Enemy boss : summoned) summon(boss, b.bossAttack(boss.attackId).summon);
        summoned.clear();
        separateEnemies();
    }

    private final List<Enemy> summoned = new ArrayList<>();

    /** 공격과 함께 쏘는 탄. 여러 번 나눠 쏘는 정의면 첫 발만 쏘고 나머지는 updateBursts 가 이어서 쏜다. */
    private void fire(Enemy e, Balance.ShotDef s) {
        float angle = (float) Math.atan2(e.lockDy, e.lockDx);
        volley(e, s, angle);
        e.burst = s.bursts > 1 ? s : null;
        e.burstsLeft = s.bursts - 1;
        e.burstTimer = s.interval;
        e.burstAngle = angle;
    }

    private void updateBursts(Enemy e, float dt) {
        if (e.burst == null || e.burstsLeft <= 0 || !e.alive()) return;
        e.burstTimer -= dt;
        if (e.burstTimer > 0) return;
        e.burstTimer += e.burst.interval;
        e.burstAngle += e.burst.rotate;
        e.burstsLeft--;
        volley(e, e.burst, e.burstAngle);
    }

    private void volley(Enemy e, Balance.ShotDef s, float angle) {
        float spread = s.ring ? (float) Math.PI * 2 / s.count : s.spread;
        int damage = Math.max(6, Math.round(e.damage * s.damageMul));
        for (int i = 0; i < s.count; i++) {
            float a = angle + (i - (s.count - 1) / 2f) * spread;
            Projectile p = new Projectile(e.x, e.hurtY(), a, s.speed, damage);
            p.color = s.color;
            combat.projectiles.add(p);
        }
    }

    /** 보스가 부하를 부른다: 보스 둘레에 소환 표식이 뜬다. 살아 있는 부하가 많으면 부르지 않는다. */
    private void summon(Enemy boss, Balance.SummonDef def) {
        int alive = combat.marks.size();
        for (Enemy e : combat.enemies) if (!e.isBoss() && e.alive()) alive++;
        Random rnd = new Random(state.dungeonSeed + boss.patternIndex * 131L + boss.hp);
        Rectangle rb = combat.room.bounds;
        for (int i = 0; i < def.count && alive < def.maxAlive; i++, alive++) {
            double ang = Math.PI * 2 * i / def.count + rnd.nextDouble();
            float x = boss.x + (float) Math.cos(ang) * 130, y = boss.y + (float) Math.sin(ang) * 90;
            x = Math.max(rb.x + 48, Math.min(rb.x + rb.width - 48, x));
            y = Math.max(rb.y + 48, Math.min(rb.y + rb.height - 64, y));
            Balance.EnemyDef d = b.enemy(def.enemies[rnd.nextInt(def.enemies.length)]);
            combat.marks.add(new SpawnMark(x, y, new Waves.Spawn(d, false), b.dungeon.spawnMarkTime));
        }
    }

    /**
     * 죽은 적을 치운다. 보스는 바로 사라지지 않고 사망 연출(outro) 동안 남아 있으며,
     * 그동안 전투는 멈추고 용사는 다치지 않는다.
     */
    private void removeDead() {
        Enemy boss = combat.bossEnemy();
        if (boss != null && !boss.alive() && !hero.dead && !combat.dying) {
            combat.dying = true;
            combat.outroTotal = combat.outro = b.feel.bossDeathTime;
            combat.projectiles.clear();
            // 하던 공격과 예고는 그 자리에서 끊기고, 부르던 부하도 함께 쓰러진다
            boss.enter(Enemy.State.IDLE);
            boss.shape = null;
            boss.extraShapes.clear();
            boss.burstsLeft = 0;
            combat.marks.clear();
            for (Enemy e : combat.enemies) {
                if (e.isBoss() || !e.alive()) continue;
                e.hp = 0;
                events.add(new WorldEvent(WorldEvent.Type.ENEMY_DIED, e.id, e.x, e.y, 0));
            }
            hero.invuln = Math.max(hero.invuln, combat.outro + 0.5f);
            events.add(new WorldEvent(WorldEvent.Type.BOSS_DYING, boss.name(), boss.x, boss.y, 0));
        }
        combat.enemies.removeIf(e -> !e.alive() && !(e.isBoss() && combat.dying && combat.outro > 0));
    }

    private void updateProjectiles(float dt) {
        if (combat == null || !combat.started() || combat.dying) return;
        for (Iterator<Projectile> it = combat.projectiles.iterator(); it.hasNext();) {
            Projectile p = it.next();
            p.life -= dt;
            float step = p.speed * dt;
            if (p.life <= 0 || rayForHero(p.x, p.y, p.dx, p.dy, step + p.radius) < step + p.radius) { it.remove(); continue; }
            p.x += p.dx * step; p.y += p.dy * step;
            // 상자·기둥 같은 엄폐물은 탄을 막는다
            if (map.coverAt(p.x, p.y)) {
                it.remove();
                events.add(new WorldEvent(WorldEvent.Type.PROJECTILE_BROKEN, null, p.x, p.y, 0));
                continue;
            }
            // 휘두른 무기·검기·충격파에 닿은 탄은 지워진다
            AttackInstance atk = hero.attack;
            if (atk != null && atk.kind == AttackInstance.Kind.NORMAL && atk.phase() == AttackInstance.Phase.ACTIVE && (atk.shape.hitsCircle(p.x, p.y, p.radius)
                    || (atk.wave != null && atk.wave.hitsCircle(p.x, p.y, p.radius)))) {
                it.remove();
                events.add(new WorldEvent(WorldEvent.Type.PROJECTILE_BROKEN, null, p.x, p.y, 0));
                sfx("hit");
                continue;
            }
            float distance = Vector2.dst(p.x, p.y, hero.hurtX(), hero.hurtY());
            if (distance <= p.radius + hero.hurtRadius() && hero.dodgeSafe <= 0) { // 구르는 동안에는 탄이 그대로 지나간다
                damageHero(p.damage, p.x - p.dx * 10, p.y - p.dy * 10);
                it.remove();
            } else if (!combat.room.bounds.contains(p.x, p.y)) it.remove();
        }
    }

    /** 적끼리 겹치지 않도록 살짝 밀어낸다(길을 막는 고체 충돌은 아님). */
    private void separateEnemies() {
        List<Enemy> list = combat.enemies;
        for (int i = 0; i < list.size(); i++) {
            Enemy a = list.get(i);
            if (a.isBoss() || a.state == Enemy.State.ATTACK) continue;
            for (int j = i + 1; j < list.size(); j++) {
                Enemy c = list.get(j);
                if (c.isBoss() || c.state == Enemy.State.ATTACK) continue;
                float dx = c.x - a.x, dy = c.y - a.y;
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                float min = (a.radius + c.radius) * 0.8f;
                if (d < min && d > 1e-3f) {
                    float push = (min - d) * 0.25f;
                    nudge(a, -dx / d * push, -dy / d * push);
                    nudge(c, dx / d * push, dy / d * push);
                }
            }
        }
    }

    private void nudge(Enemy e, float dx, float dy) {
        pos[0] = e.x;
        pos[1] = e.y;
        Rectangle rb = combat.room.bounds;
        Physics.move(pos, e.radius * 1.4f, e.radius * 0.8f, dx, dy, r -> blockedForEnemy(r)
                || r.x < rb.x || r.y < rb.y || r.x + r.width > rb.x + rb.width || r.y + r.height > rb.y + rb.height);
        e.x = pos[0];
        e.y = pos[1];
    }

    private void resolveHeroAttack() {
        AttackInstance a = hero.attack;
        if (a == null || a.phase() != AttackInstance.Phase.ACTIVE) return;
        float ox = hero.x, oy = hero.hurtY();
        float dx = Dir8.DX[a.dir], dy = Dir8.DY[a.dir];
        if (a.kind == AttackInstance.Kind.SHOCKWAVE) {
            if (!a.originLocked) releaseShockwave(a, ox, oy);
            return;
        } else {
            a.shape.set(ox, oy, dx, dy, a.length, a.width);
            a.shape.type = HitShape.Type.SECTOR;
            if (a.form != null) placeFormShapes(a, ox, oy, dx, dy);
        }
        if (combat == null || !combat.started() || combat.dying) return;
        for (Enemy e : combat.enemies) {
            if (!e.alive() || a.hit.contains(e)) continue;
            float mul = hitMultiplier(a, e);
            if (mul <= 0) continue;
            float dist = Vector2.dst(ox, oy, e.x, e.hurtY());
            if (dist > 1 && rayForHero(ox, oy, (e.x - ox) / dist, (e.hurtY() - oy) / dist, dist) < dist - e.hurtRadius() - (e.isBoss() ? e.bossDef.hurtExtend : 0)) continue;
            a.hit.add(e); // 공격 한 번에 같은 적은 한 번만 (본 타격·검기·충격 통틀어)
            damageEnemy(e, Math.round(a.damage * mul), dx, dy);
            if (a.form != null) applyStatus(e, b.statusOrNull(a.form.status), a.form.statusAmount);
            applyStatus(e, b.statusOrNull(mods.onHitStatus), mods.onHitStatusAmount);
        }
    }

    /**
     * 충격파가 터지는 순간: 방 안의 모든 적이 한 번씩 맞고 용사에게서 멀어지는 쪽으로 밀려난다. 날아오던 탄은 모두 지워진다.
     * 보스는 shockPush 만큼만 밀리고(0 이면 밀리지 않는다), 하던 공격은 끊기지 않는다.
     */
    private void releaseShockwave(AttackInstance a, float ox, float oy) {
        a.originX = ox;
        a.originY = oy;
        a.originLocked = true;
        a.effectiveLength = b.shockwave.radius; // 파동이 퍼져 보이는 크기
        sfx("shockwave");
        if (combat == null || !combat.started() || combat.dying) return;
        for (Projectile p : combat.projectiles) events.add(new WorldEvent(WorldEvent.Type.PROJECTILE_BROKEN, null, p.x, p.y, 0));
        combat.projectiles.clear();
        float t = b.enemyCombat.knockbackTime * 1.6f;
        for (Enemy e : combat.enemies) {
            if (!e.alive()) continue;
            float dx = e.x - ox, dy = e.hurtY() - oy;
            float len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-3f) {
                dx = Dir8.DX[a.dir];
                dy = Dir8.DY[a.dir];
                len = (float) Math.sqrt(dx * dx + dy * dy);
            }
            dx /= len;
            dy /= len;
            a.hit.add(e);
            damageEnemy(e, a.damage, dx, dy);
            float push = e.isBoss() ? e.bossDef.shockPush : e.speed > 0 ? b.shockwave.knockback : 0;
            e.kbTime = push > 0 ? t : 0;
            e.kbVx = dx * push / t;
            e.kbVy = dy * push / t;
            applyStatus(e, b.statusOrNull(mods.onHitStatus), mods.onHitStatusAmount);
        }
    }

    /** 무기 형태가 더하는 판정: 조준 방향의 검기(벽까지), 타격 지점의 원형 충격. */
    private void placeFormShapes(AttackInstance a, float ox, float oy, float dx, float dy) {
        Balance.FormDef f = a.form;
        if (f.waveLength > 0) {
            if (a.waveLength < 0) a.waveLength = rayForHero(ox, oy, dx, dy, f.waveLength);
            if (a.wave == null) a.wave = HitShape.box(ox, oy, dx, dy, a.waveLength, f.waveWidth);
            else a.wave.set(ox, oy, dx, dy, a.waveLength, f.waveWidth);
        }
        if (f.burstRadius > 0) {
            float cx = ox + dx * a.length * 0.75f, cy = oy + dy * a.length * 0.75f;
            if (a.burst == null) a.burst = HitShape.circle(cx, cy, f.burstRadius);
            else {
                a.burst.cx = cx;
                a.burst.cy = cy;
            }
        }
    }

    /** 이 공격이 적에게 주는 피해 배율. 본 타격 1, 검기·충격은 형태에 적힌 배율, 안 맞으면 0. */
    private static float hitMultiplier(AttackInstance a, Enemy e) {
        if (e.hitBy(a.shape)) return 1f;
        if (a.wave != null && e.hitBy(a.wave)) return a.form.waveDamageMul;
        if (a.burst != null && e.hitBy(a.burst)) return a.form.burstDamageMul;
        return 0f;
    }

    private void applyStatus(Enemy e, Balance.StatusDef def, float amount) {
        if (Statuses.apply(e, def, amount)) {
            events.add(new WorldEvent(WorldEvent.Type.STATUS_APPLIED, def.id, e.x, e.hurtY() + e.radius + 18, 0));
        }
    }

    private void damageEnemy(Enemy e, int dmg, float dx, float dy) {
        dmg = Math.round(dmg * Statuses.damageTakenMul(e));
        e.hp = Math.max(0, e.hp - dmg);
        e.flash = b.enemyCombat.flashTime;
        if (!e.isBoss() && e.speed > 0) { // 제자리에서 쏘는 적은 밀리지 않는다
            float t = b.enemyCombat.knockbackTime;
            e.kbTime = t;
            e.kbVx = dx * b.enemyCombat.knockbackDistance / t;
            e.kbVy = dy * b.enemyCombat.knockbackDistance / t;
            if (e.alive() && e.state != Enemy.State.ATTACK) {
                e.enter(Enemy.State.RECOVER);
                e.recoverTime = Math.max(0.18f, t);
            }
        }
        events.add(new WorldEvent(WorldEvent.Type.DAMAGE_NUMBER, String.valueOf(dmg), e.x, e.hurtY() + e.radius + 6, dmg));
        sfx(e.isBoss() ? "hit_boss" : "hit");
        if (!e.alive()) {
            if (e.isElite()) combat.bonusReward += e.elite.bonusMinerals;
            events.add(new WorldEvent(WorldEvent.Type.ENEMY_DIED, e.id, e.x, e.y, e.isElite() ? 1 : 0));
            sfx("enemy_die");
        }
    }

    private void resolveEnemyAttacks() {
        if (combat == null || !combat.started() || combat.dying) return;
        for (Enemy e : combat.enemies) {
            // 같은 간격에 쓰러진 적의 공격도 유효하다 (동시 사망은 용사 사망이 우선)
            HitShape s = e.activeHit();
            if (s == null || e.hitHero) continue;
            if (hero.invuln > 0 || hero.dead || hero.hp <= 0) return;
            boolean hit = s.hitsCircle(hero.hurtX(), hero.hurtY(), hero.hurtRadius());
            if (!e.dashing) for (HitShape x : e.extraShapes) hit |= x.hitsCircle(hero.hurtX(), hero.hurtY(), hero.hurtRadius());
            if (!hit) continue;
            e.hitHero = true;
            damageHero(e.damage, e.x, e.hurtY(), e.knockbackMul);
        }
    }

    /**
     * 근접 적에게 너무 바짝 붙으면 예고 없이 바로 다친다 (몸이 닿는 거리).
     * 막 나온 적(grace), 얼거나 기절한 적, 원거리 적은 닿아도 다치지 않는다.
     */
    private void resolveContact() {
        if (combat == null || !combat.started() || combat.dying || hero.dead || hero.invuln > 0 || hero.dodgeSafe > 0) return;
        for (Enemy e : combat.enemies) {
            float mul = e.contactMul();
            if (mul <= 0 || !e.alive() || e.grace > 0 || Statuses.immobile(e)) continue;
            float reach = e.radius + hero.hurtRadius() * 0.8f;
            float dx = e.x - hero.hurtX(), dy = e.hurtY() - hero.hurtY();
            if (dx * dx + dy * dy > reach * reach) continue;
            damageHero(Math.max(1, Math.round(e.damage * mul)), e.x, e.hurtY());
            return;
        }
    }

    /** 무적 중에는 피해와 추가 밀림을 받지 않는다. 겹친 공격은 먼저 처리된 하나만 적용된다. */
    public void damageHero(int dmg, float fromX, float fromY) {
        damageHero(dmg, fromX, fromY, 1f);
    }

    /** knockbackMul: 밀려나는 거리 배율 (방패 밀기처럼 멀리 날리는 공격). */
    public void damageHero(int dmg, float fromX, float fromY, float knockbackMul) {
        if (devInvincible || hero.invuln > 0 || hero.dead || hero.dodgeSafe > 0) return;
        hero.hp = Math.max(0, hero.hp - dmg);
        hero.invuln = b.hero.invulnTime;
        hero.hurtFlash = 0.2f;
        float dx = hero.hurtX() - fromX, dy = hero.hurtY() - fromY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-3f) {
            dx = 0;
            dy = -1;
            len = 1;
        }
        hero.kbTime = b.hero.knockbackTime;
        hero.kbVx = dx / len * b.hero.knockbackDistance * knockbackMul / b.hero.knockbackTime;
        hero.kbVy = dy / len * b.hero.knockbackDistance * knockbackMul / b.hero.knockbackTime;
        events.add(new WorldEvent(WorldEvent.Type.HERO_HURT, String.valueOf(dmg), hero.x, hero.y + 40, dmg));
        sfx("hurt");
    }

    private void die() {
        hero.dead = true;
        hero.hp = 0;
        hero.deathTimer = 0;
        hero.attack = null; // 사망하면 현재 공격은 즉시 취소
        hero.dodgeTime = hero.dodgeSafe = 0;
        clearBufferedActions();
        miningVein = null;
        miningProgress = 0;
        events.add(WorldEvent.of(WorldEvent.Type.DIED));
        sfx("death");
    }

    /** 사망 후 중앙 캠프에서 부활. 실패한 방만 초기화하고 나머지 진행은 유지한다. */
    private void respawn() {
        combat = null;
        hero.dead = false;
        // 축복은 이번 목숨까지만. 다시 일어나면 광맥도 새로 자라 있다.
        Progress.onDeath(state);
        Progress.onVeinsRegrow(state);
        refreshModifiers();
        hero.hp = hero.maxHp;
        hero.shockCooldown = 0;
        hero.invuln = 0;
        hero.attack = null;
        hero.dodgeTime = hero.dodgeSafe = hero.dodgeCooldown = 0;
        hero.combo = 0;
        clearBufferedActions();
        placeHero(spawn.x, spawn.y);
        room = map.roomAt(hero.x, hero.y);
        lastZone = Ids.CAMP;
        events.add(WorldEvent.of(WorldEvent.Type.RESPAWNED));
        events.add(WorldEvent.of(WorldEvent.Type.TELEPORT));
        saveRequested = true;
    }

    private void completeCombat() {
        Combat c = combat;
        combat = null;
        if (c.boss) {
            String bossId = b.zone(c.room.zone).boss;
            int reward = Progress.onBossDefeated(b, state, bossId);
            // 보스를 쓰러뜨리면 체력이 모두 차고 광맥이 새로 자란다
            hero.hp = hero.maxHp;
            Progress.onVeinsRegrow(state);
            events.add(new WorldEvent(WorldEvent.Type.BOSS_DEFEATED, b.boss(bossId).name, hero.x, hero.y, reward));
            sfx("victory");
            if (bossId.equals(b.zones[0].boss) && !state.awakenGuideSeen) {
                events.add(WorldEvent.of(WorldEvent.Type.AWAKEN_GUIDE));
            }
            if (Progress.isComplete(b, state)) events.add(WorldEvent.of(WorldEvent.Type.ENDING));
        } else {
            int reward = Progress.onRoomCleared(b, state, c.room.id, c.room.zone, c.bonusReward);
            events.add(new WorldEvent(WorldEvent.Type.ROOM_CLEARED, c.room.zone, hero.x, hero.y, reward));
            sfx("clear");
        }
        if (hasChest(c.room)) {
            Vector2 p = chestSpot(c.room, new Vector2());
            events.add(new WorldEvent(WorldEvent.Type.CHEST_APPEARED, null, p.x, p.y, 0));
        }
        saveRequested = true;
    }

    // ---- 메뉴 기능 ----

    /** 개발자 모드: 진행 중인 전투를 버리고 그 자리로 옮긴다. */
    public void devTeleport(Vector2 p) {
        combat = null;
        hero.attack = null;
        teleport(p);
    }

    public boolean inCombat() {
        return combat != null;
    }

    public boolean canReturnToCamp() {
        return combat == null && !hero.dead;
    }

    /** 메뉴의 캠프 귀환: 위치만 옮긴다. 회복이나 광맥 재생은 없다. */
    public boolean returnToCamp() {
        if (!canReturnToCamp()) return false;
        hero.attack = null;
        teleport(spawn);
        lastZone = Ids.CAMP;
        return true;
    }

    public String locationName() {
        String z = room != null ? room.zone : lastZone;
        if (z == null || z.isEmpty() || Ids.CAMP.equals(z)) return "중앙 캠프";
        return b.zone(z).name;
    }

    public Vector2 spawnPoint() {
        return spawn;
    }

    // ---- 보조 ----

    private void toast(String msg) {
        events.add(WorldEvent.text(WorldEvent.Type.TOAST, msg));
    }

    @Override
    public void sfx(String id) {
        events.add(WorldEvent.text(WorldEvent.Type.SFX, id));
    }

    @Override
    public Hero hero() {
        return hero;
    }

    @Override
    public boolean blockedForEnemy(Rectangle r) {
        if (map.overlapsStatic(r)) return true;
        for (DoorDef d : map.doors) if (d.bounds.overlaps(r)) return true;
        return false;
    }

    @Override
    public float rayLength(float ox, float oy, float dx, float dy, float max) {
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1e-6f) return max;
        dx /= len;
        dy /= len;
        float step = 4f;
        for (float t = step; t <= max; t += step) {
            float x = ox + dx * t, y = oy + dy * t;
            if (map.isSolidAt(x, y) || map.coverAt(x, y) || doorAt(x, y, true)) return t - step;
        }
        return max;
    }

    /** 용사 충격파용: 벽과 닫힌 문만 막는다. */
    private float rayForHero(float ox, float oy, float dx, float dy, float max) {
        float step = 4f;
        for (float t = step; t <= max; t += step) {
            float x = ox + dx * t, y = oy + dy * t;
            if (map.isSolidAt(x, y) || doorAt(x, y, false)) return t - step;
        }
        return max;
    }

    private boolean doorAt(float x, float y, boolean anyDoor) {
        for (DoorDef d : map.doors) {
            if (d.bounds.contains(x, y) && (anyDoor || isDoorLocked(d))) return true;
        }
        return false;
    }

    @Override
    public Rectangle roomBounds() {
        return combat != null ? combat.room.bounds : tmp.set(0, 0, map.widthPx, map.heightPx);
    }

    @Override
    public int aggressiveCount() {
        return aggressive(false);
    }

    @Override
    public int rangedAggressiveCount() {
        return aggressive(true);
    }

    private int aggressive(boolean ranged) {
        if (combat == null) return 0;
        int n = 0;
        for (Enemy e : combat.enemies) if (!e.isBoss() && e.alive() && e.aggressive() && e.ranged() == ranged) n++;
        return n;
    }

    private static float distToRect(Rectangle r, float x, float y) {
        float cx = Math.max(r.x, Math.min(x, r.x + r.width));
        float cy = Math.max(r.y, Math.min(y, r.y + r.height));
        float dx = x - cx, dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
