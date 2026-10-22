package com.yongsa.model;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.combat.Attack;
import com.yongsa.combat.Boss;
import com.yongsa.combat.CombatContext;
import com.yongsa.combat.Effect;
import com.yongsa.combat.FloatingText;
import com.yongsa.combat.Hazard;
import com.yongsa.combat.HeroUnit;
import com.yongsa.combat.Monster;
import com.yongsa.combat.Projectile;
import com.yongsa.config.AbilityDef;
import com.yongsa.config.Balance;
import com.yongsa.config.DungeonDef;
import com.yongsa.config.MonsterDef;
import com.yongsa.config.SkillDef;
import com.yongsa.config.WeaponDef;
import com.yongsa.world.CollisionWorld;
import com.yongsa.world.Door;
import com.yongsa.world.Gate;
import com.yongsa.world.Portal;
import com.yongsa.world.Room;
import com.yongsa.world.RoomGraph;
import com.yongsa.world.RoomKind;
import com.yongsa.world.RoomState;
import com.yongsa.world.VeinGenerator;
import com.yongsa.world.Vein;
import com.yongsa.world.Visibility;
import com.yongsa.world.WorldMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * 게임 한 판 (구현안 1장). 화면은 여기에 "이동해라, 공격해라, 강화해라" 같은 요청만 보내고,
 * 결과는 GameEvent로 받는다. 그리기·입력·파일과 무관해서 화면 없이 시뮬레이션·테스트할 수 있다.
 */
public final class GameSession implements CombatContext {

    public static final float INTERACT_RANGE = 34f;
    public static final float MONSTER_KNOCKBACK = 14f;

    public final Balance balance;
    public final WorldMap map;
    public final Random random;

    public final Hero hero;
    public final HeroUnit unit;
    public final Inventory inventory = new Inventory();
    public final Progress progress = new Progress();
    public final EnhanceService enhanceService;
    public final CraftService craftService = new CraftService();
    public final DismantleService dismantleService;
    public final AbsorbService absorbService;
    public final SwordPower power;

    public final List<Room> rooms = new ArrayList<>();
    public final Map<String, Room> roomById = new LinkedHashMap<>();
    public final List<Door> doors = new ArrayList<>();
    public final List<Gate> gates = new ArrayList<>();
    public final CollisionWorld collision;
    public final Visibility visibility;
    public final RoomGraph graph;

    public final List<Projectile> projectiles = new ArrayList<>();
    public final List<Effect> effects = new ArrayList<>();
    public final List<FloatingText> texts = new ArrayList<>();
    /** 바닥 위험 지대 (뼈 가시, 번개, 독구름, 촉수). */
    public final List<Hazard> hazards = new ArrayList<>();
    /** 몬스터 갱신 중에 불러낸 소환수 (갱신이 끝난 뒤 방에 넣는다). */
    private final List<Monster> pendingSpawns = new ArrayList<>();
    /** 보스가 떨어뜨린 구슬 (아직 줍지 않은 것). */
    public final List<OrbDrop> orbDrops = new ArrayList<>();
    public final Portal arenaPortal;

    /** 최종 전장의 마왕 (전장에 있을 때만). */
    public Boss demonKing;
    private boolean inArena;
    private Cutscene cutscene;
    private float effectSpeed = 1f;

    private final List<Consumer<GameEvent>> listeners = new ArrayList<>();
    private Room currentRoom, overlapRoom;
    private WorldMap.CorridorDef currentCorridor;
    /** 마지막으로 완전히 들어간 던전 방 (저장 후 복귀 위치). */
    private String lastRoomId;
    private String areaName = "";
    private Interact focus = Interact.NONE;
    private Vein focusVein;
    private Portal focusPortal;
    private Room bossConfirmRoom;
    private String bossConfirmedRoom;
    private float gateMsgCooldown;
    private boolean deathReported;
    private int basicCount;
    private Attack dashAttack;
    private float playTime;
    /** 독·화상 피해를 모아서 0.5초마다 숫자로 보여준다. */
    private float dotAccum, dotTextTimer;
    private float shockMsgCooldown;
    /** 마왕에게 죽었는지 (캠프에서 깨어나면 독백). */
    private boolean diedToDemon;
    private boolean bursting;

    /** 지금 E를 누르면 할 수 있는 일. */
    public enum Interact { NONE, VEIN, BED, FORGE, PORTAL, SIGIL_UNSEAL, SIGIL_RETURN }

    public record OrbDrop(String dungeon, float x, float y) {
    }

    public GameSession(Balance balance, WorldMap map, Random random) {
        this.balance = balance;
        this.map = map;
        this.random = random;
        this.enhanceService = new EnhanceService(balance.enhanceTable(), balance.enhance, random);
        this.dismantleService = new DismantleService(balance.enhance);
        this.absorbService = new AbsorbService(balance);
        this.power = new SwordPower(balance);
        this.enhanceService.setPower(power);

        for (WorldMap.RoomDef d : map.rooms) {
            Room r = new Room(d);
            rooms.add(r);
            roomById.put(r.id, r);
        }
        for (WorldMap.DoorDef d : map.doors) {
            Door door = new Door(d.bounds());
            doors.add(door);
            roomById.get(d.roomId()).doors.add(door);
        }
        for (WorldMap.SpawnDef s : map.monsterSpawns) roomById.get(s.roomId()).spawns.add(s);
        for (Map.Entry<String, com.badlogic.gdx.math.Vector2> e : map.portals.entrySet()) {
            roomById.get(e.getKey()).portal = new Portal(e.getKey(), e.getValue().x, e.getValue().y);
        }
        for (WorldMap.GateDef g : map.gates) gates.add(new Gate(g.dungeon(), g.bounds()));
        arenaPortal = new Portal("arena", map.arenaPortal.x, map.arenaPortal.y);

        collision = new CollisionWorld(map, doors, gates, rooms);
        collision.addObstacle(new Rectangle(map.bed.x + 2, map.bed.y, map.bed.width - 4, map.bed.height - 20));
        collision.addObstacle(new Rectangle(map.forge.x + 4, map.forge.y, map.forge.width - 8, map.forge.height - 24));
        for (WorldMap.DecorDef d : map.decor) if (d.solid()) collision.addObstacle(d.footprint());
        visibility = new Visibility(roomById);
        graph = new RoomGraph(map);

        hero = new Hero(balance.hero.maxHp);
        unit = new HeroUnit(balance.hero, balance.skills, balance.guard, map.spawn.x, map.spawn.y);
        inventory.add(balance.sealedSword());
        refreshGates();
        resetRooms();
    }

    // =================================================================== 이벤트

    public void addListener(Consumer<GameEvent> l) {
        listeners.add(l);
    }

    public void removeListener(Consumer<GameEvent> l) {
        listeners.remove(l);
    }

    private void emit(GameEvent e) {
        for (Consumer<GameEvent> l : new ArrayList<>(listeners)) l.accept(e);
    }

    private void toast(String s) {
        emit(GameEvent.text(GameEvent.Type.TOAST, s));
    }

    @Override
    public void sound(String id) {
        emit(GameEvent.sound(id));
    }

    // =================================================================== 한 프레임

    public void update(float delta, Controls in) {
        playTime += delta;
        gateMsgCooldown = Math.max(0, gateMsgCooldown - delta);
        shockMsgCooldown = Math.max(0, shockMsgCooldown - delta);
        updateEffects(delta);

        if (cutscene != null) {
            cutscene.setSpeed(effectSpeed);
            cutscene.update(delta);
            if (cutscene.shaking()) shake(3, 0.1f);
            if (cutscene.done()) enterArena();
            return;
        }
        if (bossConfirmRoom != null) return; // 확인창이 떠 있는 동안 정지

        if (hero.alive()) {
            boolean wasGuardBroken = unit.guardBroken > 0;
            unit.update(delta, in.moveX, in.moveY, in.guard, collision);
            if (!wasGuardBroken && unit.guardBroken > 0) onGuardBreak();
            locate();
            if (unit.dashing() && dashAttack != null) {
                dashAttack.ex = unit.centerX();
                dashAttack.ey = unit.centerY();
                applyAttack(dashAttack);
            } else {
                dashAttack = null;
            }
            if (unit.free()) {
                boolean skill = in.dash || in.whirl;
                if (in.dodge && unit.dodge(in.moveX, in.moveY)) sound("dodge");
                else if (skill && unit.skillsSealed()) {
                    if (shockMsgCooldown <= 0) {
                        texts.add(new FloatingText("감전! 스킬 봉인", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 44));
                        sound("denied");
                        shockMsgCooldown = 1.2f;
                    }
                }
                else if (unit.guarding) { /* 막는 동안에는 공격하지 않는다 */ }
                else if (in.dash) startDash();
                else if (in.whirl) startWhirl();
                else if (in.attack) basicAttack();
            }
            updateStatuses(delta);
            updateFocus();
            if (in.interact) interact();
            checkGateBump(in);
            checkBossDoor();
            pickOrbs();
            if (inCamp()) hero.heal(balance.hero.campRegenPerSecond * delta);
        }

        updateRooms(delta);
        updateArena(delta);
        updateProjectiles(delta);
        updateHazards(delta);

        if (!hero.alive() && !deathReported) {
            deathReported = true;
            progress.deaths++;
            projectiles.clear();
            hazards.clear();
            if (inArena && demonKing != null) {
                diedToDemon = true;
                emit(GameEvent.speech("마왕", demonKing.phase == 2 ? "꽤나 즐거운 여흥이었다." : "한심하긴.", 3.5f));
            }
            emit(GameEvent.of(GameEvent.Type.HERO_DIED));
        }
    }

    /** 용사가 어느 방·통로·구역에 있는지. */
    private void locate() {
        currentRoom = null;
        overlapRoom = null;
        for (Room r : rooms) {
            if (r.bounds.contains(unit.body)) currentRoom = r;
            if (r.bounds.overlaps(unit.body)) overlapRoom = r;
        }
        currentCorridor = null;
        if (overlapRoom == null) {
            for (WorldMap.CorridorDef c : map.corridors) if (c.bounds().overlaps(unit.body)) currentCorridor = c;
        }
        visibility.setHeroArea(overlapRoom, currentCorridor);
        if (overlapRoom != null && !overlapRoom.seen) {
            overlapRoom.seen = true;
            if (overlapRoom.kind == RoomKind.ORE && !overlapRoom.veinsGenerated) generateVeins(overlapRoom);
        }
        if (currentRoom != null) lastRoomId = currentRoom.id;

        String area = inArena ? "마왕의 심연" : inCamp() ? "용사의 캠프" : dungeonNameAt();
        if (area != null && !area.equals(areaName)) {
            areaName = area;
            emit(GameEvent.text(GameEvent.Type.AREA_CHANGED, area));
        }
    }

    private String dungeonNameAt() {
        if (overlapRoom != null) return balance.dungeon(overlapRoom.dungeon).name;
        if (currentCorridor != null) {
            String id = "camp".equals(currentCorridor.a()) ? currentCorridor.b() : currentCorridor.a();
            return balance.dungeon(roomById.get(id).dungeon).name;
        }
        return null;
    }

    public boolean inCamp() {
        return !inArena && map.camp.contains(unit.footX(), unit.footY());
    }

    public boolean inArena() {
        return inArena;
    }

    // =================================================================== 공격·스킬

    private int weaponAttack() {
        return power.attack(inventory.equipped(), inventory, hero);
    }

    private boolean hasAbility(AwakenAbility a) {
        return a == inventory.equipped().ability() || hero.absorbed().contains(a);
    }

    private void basicAttack() {
        if (unit.attackCooldown > 0) return;
        unit.attackCooldown = balance.hero.attackCooldown;
        float range = balance.hero.attackRange, arc = balance.hero.attackArcDegrees;
        unit.startSwing(Attack.Kind.BASIC, 0.18f, range, arc);
        sound("swing");
        Attack a = Attack.arc(Attack.Kind.BASIC, unit.centerX(), unit.centerY() + 6, unit.dirX, unit.dirY, range, arc, 1f);
        applyAttack(a);

        basicCount++;
        AbilityDef ab = balance.abilities;
        if (hasAbility(AwakenAbility.SHOCKWAVE) && basicCount % ab.shockwaveEvery == 0) {
            Attack wave = Attack.arc(Attack.Kind.SHOCKWAVE, unit.centerX(), unit.centerY() + 6, unit.dirX, unit.dirY,
                    ab.shockwaveRange, ab.shockwaveArcDegrees, ab.shockwaveMultiplier);
            Effect e = new Effect(Effect.Type.SHOCKWAVE, unit.centerX(), unit.centerY() + 6, 0.35f);
            e.dirX = unit.dirX;
            e.dirY = unit.dirY;
            e.radius = ab.shockwaveRange;
            e.arc = ab.shockwaveArcDegrees;
            effects.add(e);
            sound("shockwave");
            applyAttack(wave);
        }
    }

    private void startWhirl() {
        if (unit.whirlCooldown > 0) return;
        SkillDef s = balance.skills.whirl;
        unit.whirlCooldown = s.cooldown;
        unit.startSwing(Attack.Kind.WHIRL, 0.3f, s.radius, 360);
        Effect e = new Effect(Effect.Type.WHIRL, unit.centerX(), unit.centerY() + 6, 0.3f);
        e.radius = s.radius;
        effects.add(e);
        sound("whirl");
        applyAttack(Attack.circle(Attack.Kind.WHIRL, unit.centerX(), unit.centerY() + 6, s.radius, s.multiplier));
        unit.attackCooldown = Math.max(unit.attackCooldown, 0.2f);
    }

    private void startDash() {
        if (unit.dashCooldown > 0 || !unit.dash()) return;
        SkillDef s = balance.skills.dash;
        dashAttack = Attack.segment(Attack.Kind.DASH, unit.centerX(), unit.centerY(), s.width, s.multiplier);
        unit.startSwing(Attack.Kind.DASH, s.duration, s.width, 0);
        Effect e = new Effect(Effect.Type.DASH_TRAIL, unit.centerX(), unit.centerY(), 0.35f);
        e.dirX = unit.dirX;
        e.dirY = unit.dirY;
        e.radius = s.distance;
        effects.add(e);
        sound("dash");
    }

    /** 지금 공격 범위 안의 모든 몬스터에게 피해 (같은 공격에 한 번씩). */
    private void applyAttack(Attack a) {
        for (Monster m : activeMonsters()) {
            if (!m.canBeHit() || !a.tryHit(m, m.body)) continue;
            damageMonster(m, a.multiplier, a.kind == Attack.Kind.BASIC, true);
        }
    }

    /** direct: 용사가 직접 벤 공격 (흡혈은 이때만). */
    private void damageMonster(Monster m, float mult, boolean basic, boolean direct) {
        if (m instanceof Boss b && b.finalBoss && !inventory.equipped().def().holy) {
            texts.add(new FloatingText("튕겨 나갔다!", FloatingText.Kind.IMMUNE, m.centerX(), m.body.y + 64));
            Effect e = new Effect(Effect.Type.BOUNCE, m.centerX(), m.centerY() + 20, 0.25f);
            effects.add(e);
            sound("bounce");
            unit.knockback(m.centerX(), m.centerY(), 20);
            toast("이 검으로는 상처를 낼 수 없다. 성검을 들자!");
            return;
        }
        int dmg = DamageCalc.roll(weaponAttack() * mult, balance.hero.damageVariance, random);
        boolean died = m.damage(dmg, unit.centerX(), unit.centerY(), MONSTER_KNOCKBACK);
        texts.add(new FloatingText(String.valueOf(dmg), FloatingText.Kind.DAMAGE, m.centerX(),
                m.body.y + m.body.height + 30));
        Effect hit = new Effect(Effect.Type.HIT, m.centerX(), m.centerY() + 10, 0.15f);
        effects.add(hit);
        sound(died ? "monster_die" : "hit");
        if (died) {
            progress.kills++;
            if (m instanceof Boss b && b.finalBoss) {
                emit(GameEvent.speech("마왕", "이런 풋내기에게 당하다니!", 3.5f));
                shake(8, 1.2f);
            }
        }

        AbilityDef ab = balance.abilities;
        if (direct) lifesteal(m);
        if (died && !bursting && hasAbility(AwakenAbility.BLOOD_BURST)) bloodBurst(m);
        if (basic && hasAbility(AwakenAbility.CHAIN_LIGHTNING) && random.nextFloat() < ab.chainChance) {
            chainLightning(m);
        }
    }

    /** 흡혈: 맞은 적 최대 체력의 n% (6강부터). 한 번에 용사 최대 체력의 4%까지. */
    private void lifesteal(Monster m) {
        float pct = power.lifesteal(inventory.equipped(), inventory, hero);
        if (pct <= 0 || hero.hp() >= hero.maxHp()) return;
        float heal = m.maxHp() * pct / 100f;
        if (balance.hero.lifestealCapRatio > 0) heal = Math.min(heal, hero.maxHp() * balance.hero.lifestealCapRatio);
        if (heal < 0.5f) return;
        hero.heal(heal);
        texts.add(new FloatingText("+" + Math.round(heal), FloatingText.Kind.HEAL, unit.centerX() + 10, unit.body.y + 34));
        effects.add(new Effect(Effect.Type.HEAL, unit.centerX(), unit.centerY() + 16, 0.4f));
    }

    /** 혈월 폭주: 쓰러진 적 자리에서 피의 폭발. 폭발로 쓰러진 적은 다시 터지지 않는다. */
    private void bloodBurst(Monster from) {
        AbilityDef ab = balance.abilities;
        Effect e = new Effect(Effect.Type.BLOOD_BURST, from.centerX(), from.centerY() + 8, 0.4f);
        e.radius = ab.burstRadius;
        effects.add(e);
        sound("burst");
        bursting = true;
        Attack a = Attack.circle(Attack.Kind.SHOCKWAVE, from.centerX(), from.centerY() + 8, ab.burstRadius, ab.burstMultiplier);
        for (Monster m : activeMonsters()) {
            if (m == from || !m.canBeHit() || !a.tryHit(m, m.body)) continue;
            damageMonster(m, ab.burstMultiplier, false, false);
        }
        bursting = false;
    }

    private void chainLightning(Monster from) {
        AbilityDef ab = balance.abilities;
        int left = ab.chainTargets;
        float px = from.centerX(), py = from.centerY() + 10;
        List<Monster> done = new ArrayList<>();
        done.add(from);
        while (left-- > 0) {
            Monster best = null;
            float bestD = ab.chainRange;
            for (Monster m : activeMonsters()) {
                if (!m.canBeHit() || done.contains(m)) continue;
                float d = (float) Math.hypot(m.centerX() - px, m.centerY() + 10 - py);
                if (d < bestD) { bestD = d; best = m; }
            }
            if (best == null) break;
            Effect e = new Effect(Effect.Type.LIGHTNING, px, py, 0.25f);
            e.x2 = best.centerX();
            e.y2 = best.centerY() + 10;
            effects.add(e);
            done.add(best);
            px = best.centerX();
            py = best.centerY() + 10;
            sound("lightning");
            damageMonster(best, ab.chainMultiplier, false, false);
        }
    }

    /** 지금 싸울 수 있는 몬스터들 (전투 중인 방 + 최종 전장). */
    public List<Monster> activeMonsters() {
        List<Monster> list = new ArrayList<>();
        for (Room r : rooms) if (r.state() == RoomState.FIGHTING) list.addAll(r.monsters);
        if (demonKing != null) list.add(demonKing);
        return list;
    }

    // =================================================================== CombatContext

    @Override public HeroUnit heroUnit() { return unit; }
    @Override public boolean heroAlive() { return hero.alive() && cutscene == null; }
    @Override public CollisionWorld collision() { return collision; }
    @Override public Random random() { return random; }
    @Override public void spawnProjectile(Projectile p) { projectiles.add(p); }
    @Override public void effect(Effect e) { effects.add(e); }

    @Override
    public void shake(float strength, float time) {
        emit(new GameEvent(GameEvent.Type.CAMERA_SHAKE, null, null, Math.round(strength * 10), time));
    }

    @Override
    public boolean hitHero(int damage, float fromX, float fromY) {
        return hitHero(damage, fromX, fromY, false, true);
    }

    @Override
    public boolean hitHeroHeavy(int damage, float fromX, float fromY) {
        return hitHero(damage, fromX, fromY, true, true);
    }

    @Override
    public boolean hitHeroArea(int damage, float fromX, float fromY) {
        return hitHero(damage, fromX, fromY, false, false);
    }

    /** 피해가 실제로 들어갔으면 true. 앞에서 온 공격은 막기(Ctrl)로 막는다. */
    private boolean hitHero(int damage, float fromX, float fromY, boolean heavy, boolean blockable) {
        if (!hero.alive() || unit.invincible() || cutscene != null) return false;
        if (blockable && unit.canBlock(fromX, fromY)) {
            boolean broke = unit.onBlocked(fromX, fromY, heavy);
            Effect e = new Effect(Effect.Type.BLOCK, unit.centerX() + unit.dirX * 12, unit.centerY() + 10 + unit.dirY * 10, 0.22f);
            e.dirX = unit.dirX;
            e.dirY = unit.dirY;
            effects.add(e);
            if (broke) onGuardBreak();
            else {
                texts.add(new FloatingText("막음", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 40));
                sound("block");
            }
            return false;
        }
        int dmg = DamageCalc.roll(damage, balance.hero.damageVariance, random);
        hero.damage(dmg);
        unit.onHit(fromX, fromY);
        texts.add(new FloatingText(String.valueOf(dmg), FloatingText.Kind.HERO_DAMAGE, unit.centerX(), unit.body.y + 40));
        sound("hero_hurt");
        return true;
    }

    private void onGuardBreak() {
        texts.add(new FloatingText("막기 깨짐!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 46));
        effects.add(new Effect(Effect.Type.GUARD_BREAK, unit.centerX(), unit.centerY() + 12, 0.35f));
        sound("guard_break");
    }

    @Override
    public void addHazard(Hazard h) {
        hazards.add(h);
    }

    @Override
    public Rectangle arenaOf(Monster m) {
        return m.room != null ? m.room.bounds : map.arena;
    }

    @Override
    public void summon(Monster owner, String monsterId, int count, int max) {
        Room room = owner.room;
        if (room == null) return;
        int living = 0;
        for (Monster m : room.monsters) if (m.summoned && m.alive()) living++;
        for (Monster m : pendingSpawns) if (m.room == room) living++;
        int n = Math.min(count, max - living);
        MonsterDef def = balance.monster(monsterId);
        for (int i = 0; i < n; i++) {
            for (int tries = 0; tries < 12; tries++) {
                double a = random.nextFloat() * Math.PI * 2;
                float r = 50 + random.nextFloat() * 60;
                float x = owner.centerX() + (float) Math.cos(a) * r, y = owner.footY() + (float) Math.sin(a) * r * 0.7f;
                Rectangle probe = new Rectangle(x - def.width / 2, y, def.width, def.height);
                if (!room.bounds.contains(probe) || collision.blocked(probe)) continue;
                DungeonDef d = balance.dungeon(room.dungeon);
                boolean buffed = progress.defeated(d.id);
                Monster m = new Monster(def, room, x, y, buffed ? d.buffHpMultiplier : 1f, buffed ? d.buffAttackMultiplier : 1f);
                m.summoned = true;
                pendingSpawns.add(m);
                Effect e = new Effect(Effect.Type.ORB_BURST, x, y + 8, 0.5f);
                effects.add(e);
                break;
            }
        }
        if (n > 0) sound("summon");
    }

    // =================================================================== 상태이상·위험 지대

    private void updateStatuses(float delta) {
        float dot = 0;
        if (unit.poison > 0) dot += hero.maxHp() * balance.statuses.poisonMaxHpRatioPerSecond * delta;
        if (unit.burn > 0) dot += balance.statuses.burnPerSecond * delta;
        if (dot > 0) {
            hero.damageExact(dot);
            dotAccum += dot;
        }
        dotTextTimer += delta;
        if (dotTextTimer >= 0.5f) {
            dotTextTimer = 0;
            if (dotAccum >= 1f) {
                int n = Math.round(dotAccum);
                dotAccum -= n;
                texts.add(new FloatingText(String.valueOf(n), unit.poison > 0 ? FloatingText.Kind.POISON : FloatingText.Kind.BURN,
                        unit.centerX() - 8, unit.body.y + 36));
            }
        }
    }

    private void updateHazards(float delta) {
        for (Hazard h : hazards) {
            h.time += delta;
            if (!h.visible()) continue;
            if (h.type == Hazard.Type.POISON_CLOUD) {
                if (h.cloudActive() && hero.alive() && cutscene == null && h.contains(unit.footX(), unit.footY() + 4)) {
                    if (unit.poison <= 0) sound("poison_hit");
                    unit.applyPoison(balance.statuses.poisonDuration);
                }
                continue;
            }
            if (!h.fired && h.activeTime() >= 0) {
                h.fired = true;
                switch (h.type) {
                    case LIGHTNING -> { sound("thunder"); shake(3, 0.15f); }
                    case BONE_SPIKE -> sound("spike");
                    case TENTACLE -> { sound("tentacle"); shake(2, 0.12f); }
                    default -> { }
                }
                if (hero.alive() && h.contains(unit.footX(), unit.footY() + 4)
                        && hitHeroArea(h.damage, h.x, h.y) && h.type == Hazard.Type.LIGHTNING) {
                    unit.applyShock(balance.statuses.shockDuration);
                    texts.add(new FloatingText("감전!", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 52));
                }
            }
        }
        hazards.removeIf(Hazard::done);
    }

    /** 지금 밝기 (0~1). 캠프는 보스를 처음 잡을 때마다 밝아진다. */
    public float ambientLight() {
        if (inArena) return balance.arenaLight;
        if (inCamp() || overlapRoom == null && currentCorridor == null) {
            float[] t = balance.campLight;
            return t[Math.min(t.length - 1, progress.firstClears())];
        }
        return balance.dungeonLight;
    }

    // =================================================================== 상호작용 (E)

    private void updateFocus() {
        focus = Interact.NONE;
        focusVein = null;
        focusPortal = null;
        float best = INTERACT_RANGE;
        if (overlapRoom != null && overlapRoom.kind == RoomKind.ORE) {
            for (Vein v : overlapRoom.veins) {
                if (v.depleted()) continue;
                float d = (float) Math.hypot(v.x - unit.footX(), v.y - unit.footY());
                if (d < best) { best = d; focusVein = v; focus = Interact.VEIN; }
            }
        }
        if (focus != Interact.NONE) return;
        for (Portal p : portals()) {
            if (p.active() && Math.hypot(p.x - unit.footX(), p.y - unit.footY()) < 30) {
                focus = Interact.PORTAL;
                focusPortal = p;
                return;
            }
        }
        if (inCamp()) {
            if (Math.hypot(map.sigil.x - unit.footX(), map.sigil.y - unit.footY()) < 40) {
                if (absorbService.canUnseal(progress)) focus = Interact.SIGIL_UNSEAL;
                else if (progress.sealBroken() && !progress.finalDefeated()) focus = Interact.SIGIL_RETURN;
                if (focus != Interact.NONE) return;
            }
            if (near(map.bed, 14)) focus = Interact.BED;
            else if (near(map.forge, 14)) focus = Interact.FORGE;
        }
    }

    private boolean near(Rectangle r, float margin) {
        return new Rectangle(r.x - margin, r.y - margin - 8, r.width + margin * 2, r.height + margin * 2).overlaps(unit.body);
    }

    public List<Portal> portals() {
        List<Portal> list = new ArrayList<>();
        for (Room r : rooms) if (r.portal != null) list.add(r.portal);
        list.add(arenaPortal);
        return list;
    }

    public Interact focus() {
        return focus;
    }

    /** E 안내 문구. 할 수 있는 일이 있을 때만 (FR-맵-06). */
    public String interactHint() {
        return switch (focus) {
            case VEIN -> "E  " + balance.ore(focusVein.ore).name + " 캐기 (" + focusVein.hitsLeft() + ")";
            case BED -> "E  침대에서 쉬기";
            case FORGE -> "E  대장간";
            case PORTAL -> "E  포탈 타고 캠프로";
            case SIGIL_UNSEAL -> "E  성검 해방";
            case SIGIL_RETURN -> "E  마왕에게 가기";
            default -> "";
        };
    }

    private void interact() {
        switch (focus) {
            case VEIN -> mine(focusVein);
            case BED -> useBed();
            case FORGE -> emit(GameEvent.of(GameEvent.Type.OPEN_FORGE));
            case PORTAL -> usePortal();
            case SIGIL_UNSEAL -> startCutscene(true);
            case SIGIL_RETURN -> startCutscene(false);
            default -> { }
        }
    }

    private void mine(Vein v) {
        unit.mineTime = 0.2f;
        unit.attackCooldown = Math.max(unit.attackCooldown, 0.25f);
        unit.lookAt(v.x - unit.footX(), v.y - unit.footY());
        sound("mine");
        if (!v.hit()) return;
        inventory.addOre(v.ore, v.amount());
        texts.add(new FloatingText("+" + v.amount(), FloatingText.Kind.ORE, v.x, v.y + 34, "ore_" + v.ore));
        sound("ore_get");
        emit(new GameEvent(GameEvent.Type.ORE_GAINED, null, v.ore, v.amount(), null));
    }

    /** 침대: 즉시 회복, 모든 던전의 잡몹·광맥만 다시 채우고 안개도 다시 덮는다 (FR-던전-05, A19). */
    public void useBed() {
        hero.fullHeal();
        resetRooms();
        toast("푹 쉬었다. 체력이 가득 찼고 던전의 몬스터와 광맥이 다시 생겼다.");
        sound("rest");
        emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
    }

    private void usePortal() {
        unit.teleport(map.spawn.x, map.spawn.y);
        if (inArena) leaveArena();
        projectiles.clear();
        locate();
        sound("portal");
        emit(GameEvent.of(GameEvent.Type.TELEPORTED));
    }

    // =================================================================== 방 진행

    private void updateRooms(float delta) {
        if (currentRoom != null && currentRoom.state() == RoomState.IDLE && hero.alive()) {
            if (currentRoom.kind == RoomKind.MOB || currentRoom.kind == RoomKind.BOSS) startFight(currentRoom);
        }
        for (Room r : rooms) {
            if (r.monsters.isEmpty()) continue;
            boolean fighting = r.state() == RoomState.FIGHTING;
            for (Monster m : r.monsters) {
                // 방을 비운 뒤에도 쓰러지는 연출은 끝까지 보여준다
                if (fighting || m.state() == Monster.State.DYING) m.update(delta, this);
                if (fighting) separate(m);
            }
            // 보스가 쓰러지면 불러낸 해골도 같이 무너진다
            if (fighting && r.kind == RoomKind.BOSS) {
                boolean bossAlive = false;
                for (Monster m : r.monsters) if (m.isBoss() && m.alive()) bossAlive = true;
                if (!bossAlive) for (Monster m : r.monsters) if (m.summoned) m.kill();
            }
            if (fighting && !pendingSpawns.isEmpty()) {
                for (Monster m : pendingSpawns) if (m.room == r) r.monsters.add(m);
                pendingSpawns.removeIf(m -> m.room == r);
            }
            r.monsters.removeIf(m -> m.gone() && !(m.isBoss() && r.state() == RoomState.FIGHTING && !r.hasLivingMonsters()));
            if (fighting && !r.hasLivingMonsters() && !bossDying(r)) clearRoom(r);
        }
        pendingSpawns.clear();
    }

    /** 보스가 쓰러지는 연출이 끝날 때까지 방을 비운 것으로 치지 않는다. */
    private boolean bossDying(Room r) {
        for (Monster m : r.monsters) if (m.isBoss() && !m.gone()) return true;
        return false;
    }

    private void separate(Monster m) {
        if (!m.alive() || !m.body.overlaps(unit.body)) return;
        Rectangle a = m.body, b = unit.body;
        float ox = Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x);
        float oy = Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y);
        if (ox < oy) collision.move(a, m.centerX() < unit.centerX() ? -ox : ox, 0);
        else collision.move(a, 0, m.centerY() < unit.centerY() ? -oy : oy);
    }

    private void startFight(Room room) {
        DungeonDef d = balance.dungeon(room.dungeon);
        // 보스를 처음 잡을 때 흡수하지 못한 힘이 던전에 남아 있으면 잡몹이 강해진다 (한 번만, 배율 고정)
        boolean buffed = progress.defeated(d.id);
        float hpMul = buffed ? d.buffHpMultiplier : 1f, atkMul = buffed ? d.buffAttackMultiplier : 1f;
        room.monsters.clear();
        for (WorldMap.SpawnDef s : room.spawns) {
            Monster m = s.boss()
                    ? new Boss(balance.boss(d.boss), room, s.pos().x, s.pos().y, 1f, 1f, false)
                    : new Monster(balance.monster(d.monster), room, s.pos().x, s.pos().y, hpMul, atkMul);
            room.monsters.add(m);
        }
        if (room.monsters.isEmpty()) {
            room.setState(RoomState.CLEARED);
            return;
        }
        room.setState(RoomState.FIGHTING);
        bossConfirmedRoom = null;
        sound("door_lock");
        emit(new GameEvent(GameEvent.Type.ROOM_LOCKED, null, room.id, 0, room));
        if (room.kind == RoomKind.BOSS) {
            toast(balance.boss(d.boss).name + (progress.defeated(d.id) ? "이(가) 다시 나타났다!" : " 등장!"));
            sound("boss_roar");
        }
    }

    private void clearRoom(Room room) {
        room.setState(RoomState.CLEARED);
        sound("door_open");
        if (room.kind != RoomKind.BOSS) {
            emit(new GameEvent(GameEvent.Type.ROOM_CLEARED, "방을 비웠다! 문이 열렸다.", room.id, 0, room));
            return;
        }
        DungeonDef d = balance.dungeon(room.dungeon);
        AbsorbService.BossAbsorb result = absorbService.absorbBoss(d, hero, progress, inventory, random);
        refreshGates();
        hazards.clear();
        projectiles.clear();
        if (room.portal != null) room.portal.setActive(true);
        // 구슬은 보스가 쓰러진 자리 근처에 떨어진다
        Boss boss = null;
        for (Monster m : room.monsters) if (m instanceof Boss b) boss = b;
        float ox = room.portal != null ? room.portal.x : room.bounds.x + room.bounds.width / 2;
        float oy = room.portal != null ? room.portal.y + 56 : room.bounds.y + room.bounds.height / 2;
        if (boss != null) { ox = boss.centerX(); oy = boss.footY(); }
        if (result.firstClear() && !progress.hasOrb(d.id)
                && orbDrops.stream().noneMatch(o -> o.dungeon().equals(d.id))) orbDrops.add(new OrbDrop(d.id, ox, oy));
        Effect burst = new Effect(Effect.Type.ORB_BURST, ox, oy + 10, 0.8f);
        effects.add(burst);
        sound("boss_down");
        if (result.firstClear()) {
            emit(new GameEvent(GameEvent.Type.CAMP_BRIGHTER, null, d.id, progress.firstClears(), null));
        } else {
            texts.add(new FloatingText("+" + result.oreReward(), FloatingText.Kind.ORE, ox, oy + 40, "ore_" + d.ore));
            emit(new GameEvent(GameEvent.Type.ORE_GAINED, null, d.ore, result.oreReward(), null));
        }
        emit(new GameEvent(GameEvent.Type.BOSS_DEFEATED, null, d.id, 0, result));
        emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
    }

    /** 사망하면 싸우던 방만 처음 상태로 (보스 체력도 가득), 다른 비운 방은 그대로 (FR-던전-08). */
    private void resetFightingRooms() {
        for (Room r : rooms) {
            if (r.state() == RoomState.FIGHTING) {
                r.monsters.clear();
                r.setState(RoomState.IDLE);
            }
        }
    }

    private void resetRooms() {
        for (Room r : rooms) {
            r.monsters.clear();
            r.seen = false;
            r.veins.clear();
            r.veinsGenerated = false;
            // 마왕을 뺀 보스는 침대로 쉬면 다시 생긴다 (보상은 광물뿐)
            r.setState(RoomState.IDLE);
            if (r.portal != null) r.portal.setActive(false);
        }
        projectiles.clear();
        hazards.clear();
    }

    /** 광맥 생성 (침대 이후 처음 들어갈 때 한 번). */
    void generateVeins(Room r) {
        r.veinsGenerated = true;
        r.veins.clear();
        int ts = map.tileSize;
        List<VeinGenerator.Cell> candidates = new ArrayList<>();
        for (com.badlogic.gdx.math.Vector2 p : map.veinSpots.getOrDefault(r.id, List.of())) {
            candidates.add(new VeinGenerator.Cell((int) (p.x / ts), (int) (p.y / ts)));
        }
        int[] box = {(int) (r.bounds.x / ts), (int) (r.bounds.y / ts),
                (int) ((r.bounds.x + r.bounds.width) / ts) - 1, (int) ((r.bounds.y + r.bounds.height) / ts) - 1};
        VeinGenerator.Cell entry = new VeinGenerator.Cell((int) (r.def.entry().x / ts), (int) (r.def.entry().y / ts));
        boolean cleared = progress.defeated(r.dungeon);
        int min = cleared ? balance.mining.clearedVeinsMin : balance.mining.veinsMin;
        int max = cleared ? balance.mining.clearedVeinsMax : balance.mining.veinsMax;
        int count = min + random.nextInt(max - min + 1);
        int guarantee = 0;
        if (!progress.firstOreRoomUsed()) {
            guarantee = balance.mining.firstRoomGuarantee;
            progress.useFirstOreRoom();
        }
        String ore = balance.dungeon(r.dungeon).ore;
        for (VeinGenerator.Placement p : VeinGenerator.generate(candidates, count, box, entry, map::solidTile,
                balance.mining.oreMin, balance.mining.oreMax, guarantee, random)) {
            r.veins.add(new Vein(ore, p.cell().x() * ts + ts / 2f, p.cell().y() * ts + ts / 2f - 4,
                    balance.mining.hitsPerVein, p.amount()));
        }
    }

    // =================================================================== 던전 입구·보스방 확인·나가기

    public void refreshGates() {
        for (Gate g : gates) g.setOpen(progress.isOpen(balance.dungeon(g.dungeon)));
    }

    private void checkGateBump(Controls in) {
        if (gateMsgCooldown > 0 || (in.moveX == 0 && in.moveY == 0)) return;
        Rectangle probe = new Rectangle(unit.body.x + in.moveX * 4, unit.body.y + in.moveY * 4, unit.body.width, unit.body.height);
        for (Gate g : gates) {
            if (!g.open() && g.bounds.overlaps(probe)) {
                DungeonDef d = balance.dungeon(g.dungeon);
                emit(GameEvent.text(GameEvent.Type.GATE_SEALED,
                        "봉인되어 있다. 앞 던전(" + balance.dungeon(d.requires).name + ")의 보스를 잡아야 열립니다."));
                gateMsgCooldown = 2.5f;
            }
        }
    }

    /** 보스방 문 앞에 서면 확인창 (FR-던전-09). */
    private void checkBossDoor() {
        if (overlapRoom == null || overlapRoom.kind != RoomKind.BOSS) return;
        Room r = overlapRoom;
        if (r.state() != RoomState.IDLE || r.id.equals(bossConfirmedRoom)) return;
        if (r.bounds.contains(unit.body)) return;
        bossConfirmRoom = r;
        emit(new GameEvent(GameEvent.Type.BOSS_CONFIRM, null, r.id, 0, r));
    }

    /** 보스방 확인창 대답. 아니면 문 밖으로 돌려보낸다. */
    public void answerBossConfirm(boolean enter) {
        Room r = bossConfirmRoom;
        bossConfirmRoom = null;
        if (r == null) return;
        if (enter) {
            bossConfirmedRoom = r.id;
            unit.teleport(r.def.entry().x, r.def.entry().y);
        } else {
            unit.teleport(r.def.outside().x, r.def.outside().y);
        }
        locate();
    }

    public boolean awaitingBossConfirm() {
        return bossConfirmRoom != null;
    }

    /** 권장 무기 안내 (명세서 5.5). */
    public String recommendedFor(String dungeonId) {
        return switch (dungeonId) {
            case "left" -> "흑철검";
            case "right" -> "혈월검 (또는 흑철검 +6 이상)";
            case "top" -> "천공검 (또는 혈월검 +6 이상)";
            default -> "";
        };
    }

    /** Esc 메뉴 "던전 나가기": 문이 열려 있을 때만 (FR-던전-11). */
    public boolean canExitDungeon() {
        if (inCamp() || inArena || cutscene != null) return false;
        for (Room r : rooms) if (r.state() == RoomState.FIGHTING) return false;
        return true;
    }

    public void exitDungeon() {
        if (!canExitDungeon()) return;
        unit.teleport(map.spawn.x, map.spawn.y);
        locate();
        emit(GameEvent.of(GameEvent.Type.TELEPORTED));
    }

    // =================================================================== 구슬·성검·최종 전장

    private void pickOrbs() {
        Iterator<OrbDrop> it = orbDrops.iterator();
        while (it.hasNext()) {
            OrbDrop o = it.next();
            if (Math.hypot(o.x() - unit.footX(), o.y() - unit.footY()) < 26) {
                it.remove();
                progress.addOrb(o.dungeon());
                sound("orb");
                emit(new GameEvent(GameEvent.Type.ORB_PICKED, null, o.dungeon(), progress.orbs().size(), null));
                emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
            }
        }
    }

    private void startCutscene(boolean full) {
        if (full) absorbService.unseal(progress, inventory);
        else {
            // 재도전: 깨어난 성검을 들고 간다
            Weapon holy = inventory.sword();
            if (holy != null) inventory.equip(holy);
        }
        cutscene = new Cutscene(full);
        cutscene.setSpeed(effectSpeed);
        unit.teleport(map.sigil.x, map.sigil.y - 4);
        unit.lookAt(0, -1);
        unit.resetTimers();
        hero.fullHeal();
        sound(full ? "unseal" : "crack");
        emit(GameEvent.of(GameEvent.Type.CUTSCENE_STARTED));
        if (full) emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
    }

    public Cutscene cutscene() {
        return cutscene;
    }

    private void enterArena() {
        cutscene = null;
        inArena = true;
        unit.teleport(map.arenaLand.x, map.arenaLand.y);
        unit.resetTimers();
        demonKing = new Boss(balance.finalBoss, null, map.finalBossSpawn.x, map.finalBossSpawn.y, 1f, 1f, true);
        hazards.clear();
        arenaPortal.setActive(false);
        locate();
        shake(6, 0.4f);
        emit(GameEvent.of(GameEvent.Type.ARENA_ENTERED));
    }

    private void leaveArena() {
        inArena = false;
        demonKing = null;
        hazards.clear();
        arenaPortal.setActive(false);
    }

    private void updateArena(float delta) {
        if (!inArena || demonKing == null) return;
        demonKing.update(delta, this);
        separate(demonKing);
        if (demonKing.consumeTransitionStart()) {
            projectiles.clear();
            hazards.clear();
            Effect e = new Effect(Effect.Type.PHASE_SHIFT, demonKing.centerX(), demonKing.centerY() + 30, Boss.TRANSITION_TIME);
            e.radius = 160;
            effects.add(e);
            sound("phase_shift");
            emit(GameEvent.speech("마왕", "이번 용사는 강하군. 나도 본격적으로 해주마!", 3.6f));
        }
        if (demonKing.consumeTransitionEnd()) toast("마왕이 본모습을 드러냈다!");
        if (!demonKing.alive() && demonKing.gone()) {
            demonKing = null;
            progress.markFinalDefeated();
            arenaPortal.setActive(true);
            sound("boss_down");
            emit(GameEvent.of(GameEvent.Type.FINAL_DEFEATED));
            emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
        }
    }

    // =================================================================== 투사체·연출

    private void updateProjectiles(float delta) {
        Rectangle probe = new Rectangle();
        for (Projectile p : projectiles) {
            p.x += p.vx * delta;
            p.y += p.vy * delta;
            p.life -= delta;
            probe.set(p.x - 3, p.y - 3, 6, 6);
            if (p.life <= 0 || collision.blocked(probe)) p.dead = true;
            else if (Math.hypot(p.x - unit.centerX(), p.y - (unit.centerY() + 8)) < p.radius + 8 && !unit.invincible()
                    && hero.alive() && cutscene == null) {
                boolean hit = hitHero(p.damage, p.x - p.vx, p.y - p.vy);
                p.dead = true;
                if (hit) applyProjectileStatus(p);
            }
            p.age += delta;
        }
        projectiles.removeIf(p -> p.dead);
    }

    private void applyProjectileStatus(Projectile p) {
        switch (p.status) {
            case FREEZE -> {
                if (unit.applyFreeze(balance.statuses.freezeDuration)) {
                    effects.add(new Effect(Effect.Type.FREEZE, unit.centerX(), unit.centerY() + 12, balance.statuses.freezeDuration));
                    texts.add(new FloatingText("빙결!", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 52));
                    sound("freeze");
                }
            }
            case BURN -> {
                if (unit.burn <= 0) texts.add(new FloatingText("화상!", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 52));
                unit.applyBurn(balance.statuses.burnDuration);
            }
            default -> { }
        }
    }

    private void updateEffects(float delta) {
        texts.removeIf(t -> !t.update(delta));
        for (Effect e : effects) e.time += delta;
        effects.removeIf(Effect::done);
        for (Room r : rooms) for (Vein v : r.veins) v.shake = Math.max(0, v.shake - delta);
    }

    // =================================================================== 사망

    /** 캠프에서 다시 시작. 무기·광물은 잃지 않는다 (FR-던전-08). */
    public void respawn() {
        resetFightingRooms();
        if (inArena) leaveArena();
        cutscene = null;
        bossConfirmRoom = null;
        bossConfirmedRoom = null;
        projectiles.clear();
        hazards.clear();
        pendingSpawns.clear();
        hero.fullHeal();
        unit.teleport(map.spawn.x, map.spawn.y);
        unit.resetTimers();
        deathReported = false;
        locate();
        if (diedToDemon) {
            diedToDemon = false;
            emit(GameEvent.speech("용사", "검을 더 강화해야겠어. 성검은 다른 검들의 힘을 가질 수 있는 모양이니.", 5f));
        }
    }

    // =================================================================== 대장간 요청 (화면은 이것만 부른다)

    public EnhancePreview previewEnhance() {
        return enhanceService.preview(inventory.equipped(), inventory, hero);
    }

    public EnhanceResult enhanceEquipped() {
        EnhanceResult r = enhanceService.enhance(inventory.equipped(), inventory);
        if (r.attempted()) {
            progress.enhanceCount++;
            if (r.outcome() == EnhanceOutcome.DESTROY) progress.destroyCount++;
            if (r.awakened()) progress.awakenCount++;
            // 강화 직후 자동 저장: 꺼도 파괴를 되돌릴 수 없다 (FR-대장간-15)
            emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
        }
        return r;
    }

    public Weapon craft(WeaponDef def) {
        Weapon w = craftService.craft(def, inventory);
        if (w != null) emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
        return w;
    }

    public int dismantle(Weapon w) {
        int r = dismantleService.dismantle(w, inventory);
        if (r >= 0) emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
        return r;
    }

    public boolean absorbWeapon(Weapon w) {
        boolean ok = absorbService.absorbWeapon(w, inventory, hero);
        if (ok) emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
        return ok;
    }

    public void equip(Weapon w) {
        inventory.equip(w);
    }

    public int attackOf(Weapon w) {
        return power.attack(w, inventory, hero);
    }

    /** 흡혈 (적 최대 체력의 %). */
    public float lifestealOf(Weapon w) {
        return power.lifesteal(w, inventory, hero);
    }

    // =================================================================== 다음 할 일 (FR-공통-07)

    public Goal goal() {
        if (inArena) {
            return progress.finalDefeated()
                    ? new Goal("포탈(E)을 타고 캠프로 돌아가자", Goal.Target.PORTAL, "arena")
                    : new Goal("성검으로 마왕을 쓰러뜨리자", Goal.Target.NONE, null);
        }
        if (progress.finalDefeated()) return new Goal("마왕을 쓰러뜨렸다! 자유롭게 던전을 돌아보자", Goal.Target.NONE, null);
        if (progress.sealBroken()) return new Goal("캠프 문양에서 마왕에게 가자", Goal.Target.SIGIL, null);
        if (!orbDrops.isEmpty()) return new Goal("보스가 남긴 구슬을 줍자", Goal.Target.ORB, orbDrops.get(0).dungeon());
        if (progress.allOrbs(balance)) return new Goal("캠프로 돌아가 바닥 문양 앞에서 성검을 해방하자", Goal.Target.SIGIL, null);

        for (DungeonDef d : balance.dungeons) {
            if (progress.defeated(d.id)) continue;
            WeaponDef w = balance.weapon(d.weapon);
            String oreName = balance.ore(d.ore).name;
            Weapon eq = inventory.equipped();
            boolean strongEnough = eq.def() == w || eq.baseAttack() >= w.attack;
            if (!strongEnough && !inventory.owns(w)) {
                int have = inventory.ore(d.ore);
                if (have >= w.craftCost) return new Goal("캠프 대장간에서 " + w.name + " 만들기", Goal.Target.FORGE, null);
                Room oreRoom = firstOreRoom(d.id);
                boolean inside = overlapRoom != null && overlapRoom.dungeon.equals(d.id);
                if (have == 0 && !inside) return new Goal(Josa.ro(d.name) + " 이동", Goal.Target.ROOM, oreRoom.id);
                return new Goal(oreName + " 모으기 " + have + "/" + w.craftCost, Goal.Target.ROOM, oreRoom.id);
            }
            if (!strongEnough) return new Goal(w.name + " 장착하기 (I 가방)", Goal.Target.BAG, null);
            Room boss = bossRoom(d.id);
            return new Goal(d.name + "의 " + balance.boss(d.boss).name + " 잡기", Goal.Target.ROOM, boss.id);
        }
        return new Goal("캠프로 돌아가자", Goal.Target.CAMP, null);
    }

    /** 광물을 캐러 갈 방: 광맥이 남았거나 아직 생성 전인 광물방을 먼저. */
    public Room firstOreRoom(String dungeon) {
        Room fallback = null;
        for (Room r : rooms) {
            if (!r.dungeon.equals(dungeon) || r.kind != RoomKind.ORE) continue;
            if (fallback == null) fallback = r;
            boolean hasVeins = !r.veinsGenerated || r.veins.stream().anyMatch(v -> !v.depleted());
            if (hasVeins) return r;
        }
        return fallback;
    }

    public Room bossRoom(String dungeon) {
        for (Room r : rooms) if (r.dungeon.equals(dungeon) && r.kind == RoomKind.BOSS) return r;
        return null;
    }

    /** 길 안내용: 용사가 있는 노드 ("camp", 방 id, 통로면 가까운 쪽). */
    public String heroNode() {
        if (overlapRoom != null) return overlapRoom.id;
        if (currentCorridor != null) {
            WorldMap.CorridorDef c = currentCorridor;
            float da = nodeDistance(c.a()), db = nodeDistance(c.b());
            return da <= db ? c.a() : c.b();
        }
        return "camp";
    }

    private float nodeDistance(String id) {
        if ("camp".equals(id)) return (float) Math.hypot(map.camp.x + map.camp.width / 2 - unit.footX(),
                map.camp.y + map.camp.height / 2 - unit.footY());
        Rectangle b = roomById.get(id).bounds;
        return (float) Math.hypot(b.x + b.width / 2 - unit.footX(), b.y + b.height / 2 - unit.footY());
    }

    /** 다음 할 일 목적지까지 거치는 노드. 캠프 시설이면 "camp"까지. */
    public List<String> routeToGoal() {
        Goal g = goal();
        String to = switch (g.target()) {
            case ROOM -> g.targetId();
            case ORB -> bossRoom(g.targetId()).id;
            case FORGE, SIGIL, CAMP, BAG -> "camp";
            default -> null;
        };
        if (to == null || inArena) return List.of();
        return graph.path(heroNode(), to);
    }

    // =================================================================== 조회·설정

    public Room currentRoom() { return currentRoom; }
    public Room overlapRoom() { return overlapRoom; }
    public String lastRoomId() { return lastRoomId; }
    public void setLastRoomId(String id) { lastRoomId = id; }
    public String areaName() { return areaName; }
    public float playTime() { return playTime; }
    public void setPlayTime(float t) { playTime = t; }
    public void setEffectSpeed(float s) { effectSpeed = s; }

    /** 지금 싸우는 보스 (HUD 보스 체력바). */
    public Boss activeBoss() {
        if (demonKing != null && demonKing.alive()) return demonKing;
        if (currentRoom == null || currentRoom.state() != RoomState.FIGHTING) return null;
        for (Monster m : currentRoom.monsters) if (m instanceof Boss b && b.alive()) return b;
        return null;
    }

    public boolean fighting() {
        for (Room r : rooms) if (r.state() == RoomState.FIGHTING) return true;
        return demonKing != null;
    }

    /** 저장 불러오기 직후 위치를 정하고 상태를 맞춘다 (SaveMapper가 부른다). */
    public void placeAfterLoad(float x, float y, boolean wasInCamp) {
        if (wasInCamp || lastRoomId == null || !roomById.containsKey(lastRoomId)) {
            unit.teleport(wasInCamp ? x : map.spawn.x, wasInCamp ? y : map.spawn.y);
        } else {
            Room r = roomById.get(lastRoomId);
            // 비운 방이면 입구 안쪽, 아니면(전투 중 저장 등) 처음 상태로 돌리고 문 밖에서
            if (r.state() == RoomState.CLEARED || r.kind == RoomKind.ORE) unit.teleport(r.def.entry().x, r.def.entry().y);
            else unit.teleport(r.def.outside().x, r.def.outside().y);
        }
        refreshGates();
        locate();
    }

    /** 지금 저장할 수 있는지. 최종 전장 안에서는 저장하지 않는다 (불러오면 캠프 문양에서 다시 도전). */
    public boolean canSaveNow() {
        return !inArena && hero.alive();
    }
}
