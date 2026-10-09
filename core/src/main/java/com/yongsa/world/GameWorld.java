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
import com.yongsa.config.Balance;
import com.yongsa.model.DamageCalc;
import com.yongsa.model.GameState;
import com.yongsa.model.Ids;
import com.yongsa.model.MineRoomState;
import com.yongsa.model.Progress;
import com.yongsa.model.VeinState;
import com.yongsa.model.WeaponState;

import java.util.ArrayList;
import java.util.List;

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
        public float intro;
        public float introTotal;

        Combat(RoomDef room, boolean boss) {
            this.room = room;
            this.boss = boss;
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

    public enum FocusKind { BED, FORGE, SHORTCUT, RETURN, VEIN }

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
        resolveEnemyAttacks();
        // 3. 피해와 사망 (사망이 방 클리어보다 우선)
        if (hero.hp <= 0) die();
        if (combat != null) combat.enemies.removeIf(e -> !e.alive());
        // 4. 방 완료와 보상
        if (!hero.dead && combat != null && combat.started() && combat.enemies.isEmpty()) completeCombat();
        // 5. 저장 요청은 saveRequested 로 화면에 전달된다
    }

    // ---- 용사 ----

    private void updateHero(float dt, PlayerInput in) {
        hero.animTime += dt;
        if (hero.invuln > 0) hero.invuln = Math.max(0, hero.invuln - dt);
        if (hero.hurtFlash > 0) hero.hurtFlash -= dt;
        if (hero.shockCooldown > 0) hero.shockCooldown = Math.max(0, hero.shockCooldown - dt);

        hero.aimDir = Dir8.fromVector(in.aimX - hero.x, in.aimY - hero.hurtY(), hero.aimDir, 6f);

        float mx = in.moveX, my = in.moveY;
        float len = (float) Math.sqrt(mx * mx + my * my);
        if (len > 1e-3f) {
            mx /= len;
            my /= len;
        }
        hero.moving = len > 1e-3f;
        if (hero.kbTime > 0) {
            float s = Math.min(dt, hero.kbTime);
            hero.kbTime -= s;
            moveHero(hero.kbVx * s, hero.kbVy * s);
        } else if (hero.moving) {
            float speed = b.hero.moveSpeed * (hero.attacking() ? b.hero.attackMoveFactor : 1f);
            moveHero(mx * speed * dt, my * speed * dt);
        }

        // 진행 중인 공격
        if (hero.attack != null) {
            hero.attack.t += dt;
            if (hero.attack.phase() == AttackInstance.Phase.DONE) hero.attack = null;
        }
        // 새 공격은 현재 공격이 끝난 뒤에만 받는다 (선입력 예약 없음)
        WeaponState w = state.equippedWeapon();
        if (hero.attack == null && w != null) {
            if (in.shockwavePressed && canShockwave()) {
                startShockwave(w);
            } else if (in.attackHeld) {
                startAttack(w);
            }
        }
        if (hero.attack != null) hero.facing = hero.attack.dir;
        else if (hero.moving) hero.facing = Dir8.fromVector(mx, my, hero.facing, 0.01f);
        if (hero.attack != null) hero.faceLeft = Dir8.facesLeft(hero.attack.dir);
        else if (Math.abs(mx) > 0.01f) hero.faceLeft = mx < 0;
    }

    public boolean canShockwave() {
        return Progress.isAwakeningUnlocked(b, state) && state.equippedWeapon() != null
                && hero.shockCooldown <= 0 && hero.attack == null && !hero.dead;
    }

    private void startAttack(WeaponState w) {
        Balance.WeaponDef def = b.weapon(w.type);
        hero.attack = new AttackInstance(AttackInstance.Kind.NORMAL, hero.aimDir, def.windup, def.active, def.recovery,
                DamageCalc.weaponDamage(b, w), def.reach, def.width);
        sfx("swing_" + w.type);
    }

    private void startShockwave(WeaponState w) {
        Balance.ShockwaveDef sw = b.shockwave;
        hero.attack = new AttackInstance(AttackInstance.Kind.SHOCKWAVE, hero.aimDir, sw.windup, sw.active, sw.recovery,
                DamageCalc.shockwaveDamage(b, w), sw.length, sw.width);
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
        for (MapObject s : map.spawnsIn(r.id)) {
            Enemy e = new Enemy(b.enemy(s.prop("enemy")), s.x(), s.y());
            e.grace = b.enemyCombat.spawnGrace;
            combat.enemies.add(e);
        }
        events.add(WorldEvent.of(WorldEvent.Type.COMBAT_STARTED));
        sfx("door_lock");
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
        // 보스방 입장: 체력 회복과 충격파 즉시 사용 가능
        hero.hp = hero.maxHp;
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
            return state.hasAnyWeapon() ? null : "무기가 필요합니다 · 중앙 대장간에서 " + b.weapon(Ids.SWORD).name + " 제작";
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
            if (miningProgress >= b.mining.holdTime) completeMining(focus.id);
        } else {
            miningVein = null;
            miningProgress = 0;
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
                        f.label = "[E] 길게 눌러 채굴";
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
            case "bed":
                f.kind = FocusKind.BED;
                f.label = "[E] 침대 · 휴식";
                break;
            case "forge":
                f.kind = FocusKind.FORGE;
                f.label = "[E] 대장간";
                break;
            case "shortcut": {
                f.kind = FocusKind.SHORTCUT;
                String zn = b.zone(f.zone).name;
                if (state.hasShortcut(f.zone)) {
                    f.label = "[E] 지름길 · " + zn + " 보스 앞";
                } else {
                    f.label = zn + " 지름길 · 보스 앞 도달 시 개방";
                    f.enabled = false;
                }
                break;
            }
            case "return":
                f.kind = FocusKind.RETURN;
                f.label = "[E] 중앙 캠프로 돌아가기";
                break;
            default:
                return null;
        }
        return f;
    }

    private void activate(Focus f) {
        switch (f.kind) {
            case BED:
                if (combat != null) {
                    toast("전투 중에는 사용할 수 없습니다");
                    return;
                }
                useBed();
                break;
            case FORGE:
                events.add(WorldEvent.of(WorldEvent.Type.OPEN_FORGE));
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

    /** 침대: 회복과 새 채굴 회차. 전투방과 영구 진행은 초기화하지 않는다. */
    public void useBed() {
        hero.hp = hero.maxHp;
        Progress.onBedUsed(state);
        toast("휴식했습니다 · 체력 회복, 광맥이 새로 자라납니다");
        sfx("rest");
        saveRequested = true;
    }

    private void completeMining(String candidateId) {
        MineRoomState m = room == null ? null : state.mineRoom(room.id);
        VeinState v = m == null ? null : m.vein(candidateId);
        miningVein = null;
        miningProgress = 0;
        if (v == null || v.mined) return;
        v.mined = true;
        int amount = b.zone(room.zone).veinYield;
        state.minerals += amount;
        MapObject o = map.vein(candidateId);
        events.add(new WorldEvent(WorldEvent.Type.MINED, "+" + amount, o.x(), o.y() + 20, amount));
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
        for (Enemy e : combat.enemies) {
            if (e.isBoss()) BossPattern.update(e, this, b.bossAttacks, dt);
            else MonsterAI.update(e, this, b.enemyCombat, dt);
        }
        separateEnemies();
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
            if (a.effectiveLength < 0) {
                // 첫 충돌 지점에서 유효 범위를 잘라낸다 (벽과 닫힌 문을 관통하지 않음)
                a.effectiveLength = rayForHero(ox, oy, dx, dy, a.length);
                sfx("shockwave");
            }
            a.shape.set(ox, oy, dx, dy, a.effectiveLength, a.width);
        } else {
            a.shape.set(ox, oy, dx, dy, a.length, a.width);
        }
        if (combat == null || !combat.started()) return;
        for (Enemy e : combat.enemies) {
            if (!e.alive() || a.hit.contains(e)) continue;
            if (!a.shape.hitsCircle(e.x, e.hurtY(), e.radius)) continue;
            a.hit.add(e); // 공격 한 번에 같은 적은 한 번만
            damageEnemy(e, a.damage, dx, dy);
        }
    }

    private void damageEnemy(Enemy e, int dmg, float dx, float dy) {
        e.hp = Math.max(0, e.hp - dmg);
        e.flash = b.enemyCombat.flashTime;
        if (!e.isBoss()) {
            float t = b.enemyCombat.knockbackTime;
            e.kbTime = t;
            e.kbVx = dx * b.enemyCombat.knockbackDistance / t;
            e.kbVy = dy * b.enemyCombat.knockbackDistance / t;
        }
        events.add(new WorldEvent(WorldEvent.Type.DAMAGE_NUMBER, String.valueOf(dmg), e.x, e.hurtY() + e.radius + 6, dmg));
        sfx(e.isBoss() ? "hit_boss" : "hit");
        if (!e.alive()) {
            events.add(new WorldEvent(WorldEvent.Type.ENEMY_DIED, e.id, e.x, e.y, 0));
            sfx("enemy_die");
        }
    }

    private void resolveEnemyAttacks() {
        if (combat == null || !combat.started()) return;
        for (Enemy e : combat.enemies) {
            HitShape s = e.activeHit();
            if (s == null || e.hitHero) continue;
            if (hero.invuln > 0 || hero.dead || hero.hp <= 0) return;
            if (!s.hitsCircle(hero.hurtX(), hero.hurtY(), hero.hurtRadius())) continue;
            e.hitHero = true;
            damageHero(e.damage, e.x, e.hurtY());
        }
    }

    /** 무적 중에는 피해와 추가 밀림을 받지 않는다. 겹친 공격은 먼저 처리된 하나만 적용된다. */
    public void damageHero(int dmg, float fromX, float fromY) {
        if (hero.invuln > 0 || hero.dead) return;
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
        hero.kbVx = dx / len * b.hero.knockbackDistance / b.hero.knockbackTime;
        hero.kbVy = dy / len * b.hero.knockbackDistance / b.hero.knockbackTime;
        events.add(new WorldEvent(WorldEvent.Type.HERO_HURT, String.valueOf(dmg), hero.x, hero.y + 40, dmg));
        sfx("hurt");
    }

    private void die() {
        hero.dead = true;
        hero.hp = 0;
        hero.deathTimer = 0;
        hero.attack = null; // 사망하면 현재 공격은 즉시 취소
        miningVein = null;
        miningProgress = 0;
        events.add(WorldEvent.of(WorldEvent.Type.DIED));
        sfx("death");
    }

    /** 사망 후 중앙 캠프에서 부활. 실패한 방만 초기화하고 나머지 진행은 유지한다. */
    private void respawn() {
        combat = null;
        hero.dead = false;
        hero.hp = hero.maxHp;
        hero.shockCooldown = 0;
        hero.invuln = 0;
        hero.attack = null;
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
            events.add(new WorldEvent(WorldEvent.Type.BOSS_DEFEATED, b.boss(bossId).name, hero.x, hero.y, reward));
            sfx("victory");
            if (bossId.equals(b.zones[0].boss) && !state.awakenGuideSeen) {
                events.add(WorldEvent.of(WorldEvent.Type.AWAKEN_GUIDE));
            }
            if (Progress.isComplete(b, state)) events.add(WorldEvent.of(WorldEvent.Type.ENDING));
        } else {
            int reward = Progress.onRoomCleared(b, state, c.room.id, c.room.zone);
            events.add(new WorldEvent(WorldEvent.Type.ROOM_CLEARED, c.room.id, hero.x, hero.y, reward));
            sfx("clear");
        }
        saveRequested = true;
    }

    // ---- 메뉴 기능 ----

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
            if (map.isSolidAt(x, y) || doorAt(x, y, true)) return t - step;
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
        if (combat == null) return 0;
        int n = 0;
        for (Enemy e : combat.enemies) if (!e.isBoss() && e.alive() && e.aggressive()) n++;
        return n;
    }

    private static float distToRect(Rectangle r, float x, float y) {
        float cx = Math.max(r.x, Math.min(x, r.x + r.width));
        float cy = Math.max(r.y, Math.min(y, r.y + r.height));
        float dx = x - cx, dy = y - cy;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
