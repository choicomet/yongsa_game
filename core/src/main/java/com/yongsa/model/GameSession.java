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
    /** 이 거리 안에 몬스터가 있으면 용사가 가장 가까운 몬스터를 자동으로 바라본다. */
    public static final float AUTO_FACE_RANGE = 200f;

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
    /** 강화 숙련도 (모든 검이 함께 쓰는 용사의 실력, 저장된다). */
    public final EnhanceMastery mastery;
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
    /**
     * 갇힌 던전 id (2026-10-10 결정): 던전 방에 한 번 들어가면 그 던전의 보스를 잡거나 쓰러질 때까지 입구가 닫힌다.
     * 이미 비운 보스방(침대 전까지)이 있는 던전은 잠그지 않는다.
     */
    private String lockedDungeon;
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
    /** 반전 마법진 위에 서 있는지 (이번 프레임 입력을 뒤바꾼다). */
    private boolean inverted;
    /** 마법진에서 빠져나온 뒤 남은 좌우 반전 시간 (2026-10-12 결정: 5초). */
    private float mirrorTime;
    public static final float MIRROR_AFTER_SIGIL = 5f;
    /** 마왕 전장에서 아직 남아 있는 곳 (공간 절단으로 줄어든다)과 잘려 나간 곳들. */
    private final Rectangle field = new Rectangle();
    public final List<Rectangle> rends = new ArrayList<>();
    private float rendTextTimer;
    /** 보스 보물상자. */
    public final List<TreasureChest> chests = new ArrayList<>();
    private TreasureChest focusChest;
    /** 침대에서 자는 중이면 지난 시간 (0이면 깨어 있음). */
    private float sleepTime;
    private boolean slept;
    public static final float SLEEP_TIME = 3.2f, SLEEP_REST_AT = 1.5f;
    /** 위험 지대 안내 문구를 너무 자주 띄우지 않게 (종류별 마지막 시각). */
    private final Map<Hazard.Type, Float> hintTime = new java.util.EnumMap<>(Hazard.Type.class);
    private float voidTextTimer;

    /** 지금 E를 누르면 할 수 있는 일. */
    public enum Interact { NONE, VEIN, BED, FORGE, PORTAL, SIGIL_UNSEAL, SIGIL_RETURN, VILLAGE_RETURN, TALK, CHEST }

    // ------------------------------------------------------------------ 던전 출구·마을 (2026-10-11 결정)
    /** 마왕을 쓰러뜨리기 전에는 이만큼 거절한 다음에야 나갈 수 있다. */
    public static final int EXIT_REFUSALS = 5;
    /** 마왕 전에 나가려 할 때 용사의 독백 (n번째 시도). */
    static final String[] EXIT_LINES = {
            "밖의 모두를 위해서라도, 마왕을 물리치기 전까지는 나갈 수 없어!",
            "아니야… 마을 사람들은 아직 수정 속에 갇혀 있어. 여기서 돌아설 순 없어!",
            "마왕을 물리치기 전까지는 나갈 수 없어! 모두가 나를 기다리고 있으니까.",
            "…발이 떨어지지 않아. 마왕을 물리치기 전까지는 안 돼!",
            "몇 번을 와도 마찬가지야. 마왕을 물리치기 전까지는 나갈 수 없어!",
    };
    static final String EXIT_GIVE_UP = "어쩔 수 없지. 밖으로 나가서 마왕에 대한 복수심을 불태워 보자.";
    /** 출구 칸에 올라가 있는지 (한 번 올라갈 때마다 한 번만 센다). */
    private boolean onExit;
    private WorldMap.VillagerDef focusVillager;
    /** 마을 사람마다 몇 번째 대사까지 했는지. */
    private final Map<String, Integer> talkCount = new java.util.HashMap<>();

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
        this.mastery = new EnhanceMastery(balance.enhance.mastery);
        this.enhanceService.setMastery(mastery);

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
        for (WorldMap.VillagerDef v : map.villagers) collision.addObstacle(new Rectangle(v.pos().x - 8, v.pos().y, 16, 8));
        visibility = new Visibility(roomById);
        graph = new RoomGraph(map);

        hero = new Hero(balance.hero.maxHp);
        unit = new HeroUnit(balance.hero, balance.skills, balance.guard, map.spawn.x, map.spawn.y);
        inventory.add(balance.sealedSword());
        field.set(map.arena);
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
        for (TreasureChest c : chests) if (c.opened) c.openTime += delta;

        if (cutscene != null) {
            cutscene.setSpeed(effectSpeed);
            cutscene.update(delta);
            if (cutscene.shaking()) shake(3, 0.1f);
            if (cutscene.done()) enterArena();
            return;
        }
        if (bossConfirmRoom != null) return; // 확인창이 떠 있는 동안 정지

        if (sleepTime > 0) {
            // 침대에서 자는 중: 조작은 받지 않는다. 중간쯤 푹 쉬고(회복·던전 초기화) 끝에 일어난다
            sleepTime += delta;
            if (!slept && sleepTime >= SLEEP_REST_AT) {
                slept = true;
                useBed();
            }
            if (sleepTime >= SLEEP_TIME) {
                sleepTime = 0;
                unit.lookAt(0, -1);
            }
            in = new Controls();
        }
        mirrorTime = Math.max(0, mirrorTime - delta);
        if (inverted && hero.alive()) in = invert(in);
        else if (mirrorTime > 0 && hero.alive()) in = mirrorX(in);
        if (hero.alive()) {
            boolean wasGuardBroken = unit.guardBroken > 0;
            unit.update(delta, in.moveX, in.moveY, in.guard, collision);
            if (!wasGuardBroken && unit.guardBroken > 0) onGuardBreak();
            locate();
            autoFace();
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
            // 마룡이 바람의 고치에 있는 동안은 회피 쿨타임이 없다
            Boss ab = activeBoss();
            if (ab != null && ab.cocoon) unit.dodgeCooldown = 0;
            updateFocus();
            if (in.interact) interact();
            checkGateBump(in);
            checkExit();
            checkBossDoor();
            pickOrbs();
            if (inCamp() || inVillage()) hero.heal(balance.hero.campRegenPerSecond * delta);
        }

        updateRooms(delta);
        updateArena(delta);
        updateProjectiles(delta);
        updateHazards(delta);

        if (!hero.alive() && !deathReported) {
            deathReported = true;
            progress.deaths++;
            projectiles.clear();
            clearHazards();
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
        if (currentRoom != null && lockedDungeon == null && !inArena) {
            Room boss = bossRoom(currentRoom.dungeon);
            if (boss != null && boss.state() != RoomState.CLEARED) {
                lockedDungeon = currentRoom.dungeon;
                refreshGates();
                emit(GameEvent.text(GameEvent.Type.TOAST, "뒤에서 입구가 닫혔다… 보스를 쓰러뜨리기 전에는 나갈 수 없다."));
                sound("door_lock");
            }
        }

        String area = inArena ? "마왕의 심연" : inCamp() ? "용사의 캠프"
                : inVillage() ? (progress.finalDefeated() ? "되찾은 마을" : "수정에 잠든 마을")
                : overlapRoom == null && currentCorridor == null ? "용사의 캠프" : dungeonNameAt();
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

    /** 던전 밖 마을에 있는지. */
    public boolean inVillage() {
        return !inArena && map.village.contains(unit.footX(), unit.footY());
    }

    /** 몬스터가 오지 않는 곳 (캠프·마을·출구 통로). 저장하면 그 자리에서 다시 시작한다. */
    public boolean inSafeArea() {
        return !inArena && (inCamp() || inVillage() || overlapRoom == null && currentCorridor == null);
    }

    /** 마을 사람들이 수정에서 풀려났는지 (마왕을 쓰러뜨리면). */
    public boolean villagersFree() {
        return progress.finalDefeated();
    }

    // =================================================================== 던전 출구 → 마을

    /**
     * 캠프 아래 출구에 닿을 때마다 한 번: 마왕을 쓰러뜨렸으면 바로 마을로 나간다.
     * 아니면 다섯 번까지는 독백과 함께 되돌아서고, 여섯 번째에는 체념하고 나간다 (그 뒤로는 그냥 나간다).
     */
    private void checkExit() {
        if (inArena || !map.exit.contains(unit.footX(), unit.footY())) {
            onExit = false;
            return;
        }
        if (onExit) return;
        onExit = true;
        if (progress.finalDefeated() || progress.exitRefusals > EXIT_REFUSALS) {
            leaveToVillage(null);
            return;
        }
        if (progress.exitRefusals < EXIT_REFUSALS) {
            emit(GameEvent.speech("용사", EXIT_LINES[progress.exitRefusals], 3.4f));
            progress.exitRefusals++;
            unit.knockback(map.exit.x + map.exit.width / 2, map.exit.y - 60, 44);
            unit.lookAt(0, 1);
            sound("denied");
            return;
        }
        progress.exitRefusals++;
        leaveToVillage(EXIT_GIVE_UP);
    }

    private void leaveToVillage(String line) {
        projectiles.clear();
        clearHazards();
        unit.teleport(map.villageLand.x, map.villageLand.y);
        unit.resetTimers();
        unit.lookAt(0, -1);
        onExit = false;
        locate();
        sound("portal");
        emit(GameEvent.of(GameEvent.Type.TELEPORTED));
        if (line != null) emit(GameEvent.speech("용사", line, 4.5f));
        else if (!progress.finalDefeated()) emit(GameEvent.speech("용사", "…모두 수정 속에 잠들어 있다. 마왕, 절대 용서하지 않겠어.", 3.5f));
    }

    /** 마을 계단으로 던전(캠프 아래 출구 통로)에 돌아간다. */
    public void returnFromVillage() {
        unit.teleport(map.exit.x + map.exit.width / 2, map.exit.y + map.exit.height + 44);
        unit.resetTimers();
        unit.lookAt(0, 1);
        onExit = false;
        locate();
        sound("portal");
        emit(GameEvent.of(GameEvent.Type.TELEPORTED));
    }

    /** 마을 사람 이름. */
    public static String villagerName(String kind) {
        return switch (kind) {
            case "baker" -> "빵집 아저씨";
            case "redhood" -> "빨간 두건";
            case "child" -> "꼬마";
            case "elder" -> "촌장";
            case "maid" -> "꽃집 아가씨";
            case "boy" -> "종지기 소년";
            default -> "마을 사람";
        };
    }

    private static final Map<String, String[]> FREE_LINES = Map.of(
            "baker", new String[]{"용사님! 갓 구운 빵이에요. 오늘은 마을 전체가 잔치랍니다!", "수정 속에서도 빵 굽는 냄새가 그리웠어요. 하하!"},
            "redhood", new String[]{"수정 속에서 계속 꿈을 꿨어요. 용사님이 오는 꿈이요!", "할머니 댁에도 얼른 소식을 전해야겠어요."},
            "child", new String[]{"와아! 용사님이다! 나도 크면 용사님처럼 될래!", "그 반짝이는 칼, 한 번만 만져 보면 안 돼요?"},
            "elder", new String[]{"마왕의 저주가 풀렸구려… 마을을 대표해 감사드리오, 용사여.", "이 마을은 그대의 이름을 오래도록 기억할 것이오."},
            "maid", new String[]{"꽃들도 다시 피어났어요. 이 꽃, 용사님께 드릴게요.", "얼어붙은 시간 동안에도 꽃은 기다려 줬나 봐요."},
            "boy", new String[]{"종을 울려도 돼요? 마을이 다시 살아났다고 알리고 싶어요!", "댕— 댕—! 들리세요? 다들 광장으로 모이고 있어요!"});

    private void talk(WorldMap.VillagerDef v) {
        String name = villagerName(v.kind());
        unit.lookAt(v.pos().x - unit.footX(), v.pos().y - unit.footY());
        if (!villagersFree()) {
            emit(GameEvent.speech("용사", name + "… 차가운 수정 속에서 잠든 것처럼 움직이지 않아. 반드시 구해 낼게.", 3.5f));
            return;
        }
        String[] lines = FREE_LINES.getOrDefault(v.kind(), new String[]{"고마워요, 용사님!"});
        int n = talkCount.merge(v.kind(), 1, Integer::sum) - 1;
        emit(GameEvent.speech(name, lines[n % lines.length], 3.5f));
        sound("ui_click");
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
        // 해골 기사: 앞에서 오는 공격을 칼로 막아 내고 곧바로 반격한다 (공격 중·예고 중에는 못 막음)
        if (!(m instanceof Boss) && m.def.blockChance > 0 && direct && m.facesTowards(unit.centerX())
                && (m.state() == Monster.State.CHASE || m.state() == Monster.State.RECOVER || m.state() == Monster.State.IDLE)
                && random.nextFloat() < m.def.blockChance) {
            m.parry(unit.centerX());
            texts.add(new FloatingText("막음!", FloatingText.Kind.IMMUNE, m.centerX(), m.body.y + m.body.height + 34));
            Effect e = new Effect(Effect.Type.PARRY, m.centerX() + (m.faceLeft ? -12 : 12), m.centerY() + 22, 0.25f);
            effects.add(e);
            unit.knockback(m.centerX(), m.centerY(), 10);
            sound("parry");
            return;
        }
        int dmg = DamageCalc.roll(weaponAttack() * mult, balance.hero.damageVariance, random);
        if (m instanceof Boss b && b.armor > 0) {
            // 뼈갑옷: 방어막만 깎인다
            b.damage(dmg, unit.centerX(), unit.centerY(), 0);
            texts.add(new FloatingText(String.valueOf(dmg), FloatingText.Kind.SHIELD, m.centerX(), m.body.y + m.body.height + 30));
            effects.add(new Effect(Effect.Type.BLOCK, m.centerX(), m.centerY() + 30, 0.15f));
            sound("armor_hit");
            if (b.consumeArmorBroken()) {
                texts.add(new FloatingText("뼈갑옷 파괴!", FloatingText.Kind.IMMUNE, m.centerX(), m.body.y + 96));
                Effect e = new Effect(Effect.Type.GUARD_BREAK, m.centerX(), m.centerY() + 40, 0.6f);
                effects.add(e);
                shake(5, 0.35f);
                sound("armor_break");
            }
            return;
        }
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

    /** 흡혈: 벨 때마다 용사 최대 체력의 n% (6강부터). 한 번에 용사 최대 체력의 4%까지. */
    private void lifesteal(Monster m) {
        float pct = power.lifesteal(inventory.equipped(), inventory, hero);
        if (pct <= 0 || hero.hp() >= hero.maxHp()) return;
        float heal = hero.maxHp() * pct / 100f;
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

    /**
     * 가까운 몬스터를 자동으로 바라본다 (공격 방향도 그쪽). 이동 키로 걸어가도 몸은 적을 향한다.
     * 예외: 회피·돌진·빙결·기절 중, 그리고 마왕의 눈알이 빛을 모으는 동안 (눈을 등지는 건 용사가 직접 정해야 하므로).
     */
    private void autoFace() {
        if (unit.dodging() || unit.dashing() || unit.frozen() || unit.stunned()) return;
        for (Hazard h : hazards) if (h.type == Hazard.Type.EYE && h.visible() && !h.fired) return;
        Monster best = null;
        float bestD = AUTO_FACE_RANGE;
        for (Monster m : activeMonsters()) {
            if (!m.canBeHit()) continue;
            if (m.room != null && !visibility.room(m.room)) continue;
            float d = (float) Math.hypot(m.centerX() - unit.centerX(), m.centerY() - unit.centerY());
            if (d < bestD) {
                bestD = d;
                best = m;
            }
        }
        if (best != null) unit.lookAt(best.centerX() - unit.centerX(), best.centerY() - unit.centerY());
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

    /**
     * 피해가 실제로 들어갔으면 true. 앞에서 온 공격은 막기(Ctrl)로 막는다.
     * heavy = 보스의 공격(몸통·돌진·보스 탄): 막아도 한 번에 막기가 깨진다.
     */
    private boolean hitHero(int damage, float fromX, float fromY, boolean heavy, boolean blockable) {
        if (!hero.alive() || unit.invincible() || cutscene != null || hero.invulnerable) return false;
        if (blockable && unit.canBlock(fromX, fromY)) {
            boolean broke = unit.onBlocked(fromX, fromY, heavy, heavy);
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
        String hint = switch (h.type) {
            case BONE_STORM -> "뼛가루 폭풍이 몰아친다! 떨어지는 바위 뒤(바람이 불어 가는 쪽)로 숨자.";
            case FLESH_TIDE -> "살점의 해일이 밀려온다! 회피로는 못 피한다 — 빛나는 균열에 숨자. (균열은 한 번 쓰면 닫힌다)";
            case EYE -> "허공에 거대한 눈이 열린다… 빛을 뿜을 때 눈을 등지고 있자!";
            case METEOR_FIELD -> "메테오! 금빛 원 안으로 피하자. (지금은 회피(Space) 쿨타임이 없다)";
            case ABYSS_SIGIL -> "발밑에 뒤틀린 마법진… 밟고 있으면 키가 뒤바뀐다!";
            case VOID -> "공간이 찢어졌다! 찢긴 곳은 전투 내내 남는다.";
            case WARP -> null;
            default -> null;
        };
        if (hint != null) {
            Float last = hintTime.get(h.type);
            if (last == null || playTime - last > 8f) {
                hintTime.put(h.type, playTime);
                toast(hint);
            }
        }
    }

    @Override
    public boolean hitHeroTrue(int damage, float fromX, float fromY) {
        if (!hero.alive() || cutscene != null || hero.invulnerable) return false;
        int dmg = Math.max(1, damage);
        hero.damage(dmg);
        unit.onHit(fromX, fromY);
        texts.add(new FloatingText(String.valueOf(dmg), FloatingText.Kind.HERO_DAMAGE, unit.centerX(), unit.body.y + 40));
        sound("hero_hurt");
        shake(4, 0.25f);
        return true;
    }

    /** 위험 지대를 모두 없앤다 (폭풍 바위도 치운다). */
    private void clearHazards() {
        for (Hazard h : hazards) if (h.rockPlaced) collision.removeObstacle(h.rock);
        hazards.clear();
    }

    // =================================================================== 반전 마법진

    /** 이번 판에 뒤바뀌는 키. 처음 한 번 무작위로 정하고 저장한다. */
    public String inversionKeys() {
        if (progress.inversion().isEmpty()) {
            String[] moves = {"LR", "UD"};
            String[] acts = {"ATTACK_DODGE", "WHIRL_DODGE", "WHIRL_DASH", "ATTACK_WHIRL", "DASH_DODGE"};
            progress.setInversion(moves[random.nextInt(moves.length)] + "|" + acts[random.nextInt(acts.length)]);
        }
        return progress.inversion();
    }

    /** 뒤바뀐 입력 (원본은 그대로 두고 새로 만든다). */
    private Controls invert(Controls in) {
        Controls c = new Controls();
        c.moveX = in.moveX;
        c.moveY = in.moveY;
        c.attack = in.attack;
        c.whirl = in.whirl;
        c.dash = in.dash;
        c.dodge = in.dodge;
        c.interact = in.interact;
        c.guard = in.guard;
        for (String sw : inversionKeys().split("\\|")) {
            switch (sw) {
                case "LR" -> c.moveX = -in.moveX;
                case "UD" -> c.moveY = -in.moveY;
                case "ATTACK_DODGE" -> { c.attack = in.dodge; c.dodge = in.attack; }
                case "WHIRL_DODGE" -> { c.whirl = in.dodge; c.dodge = in.whirl; }
                case "WHIRL_DASH" -> { c.whirl = in.dash; c.dash = in.whirl; }
                case "ATTACK_WHIRL" -> { c.attack = in.whirl; c.whirl = in.attack; }
                case "DASH_DODGE" -> { c.dash = in.dodge; c.dodge = in.dash; }
                default -> { }
            }
        }
        return c;
    }

    /** 좌우만 뒤바뀐 입력 (마법진에서 나온 뒤 5초). */
    private static Controls mirrorX(Controls in) {
        Controls c = new Controls();
        c.moveX = -in.moveX;
        c.moveY = in.moveY;
        c.attack = in.attack;
        c.whirl = in.whirl;
        c.dash = in.dash;
        c.dodge = in.dodge;
        c.interact = in.interact;
        c.guard = in.guard;
        return c;
    }

    public boolean inverted() {
        return inverted;
    }

    /** 마법진 밖에서 좌우가 뒤바뀐 채로 남은 시간. */
    public float mirrorTime() {
        return mirrorTime;
    }

    // =================================================================== 마왕 공간 절단

    /** 마왕 전장에서 아직 남은 곳. */
    public Rectangle field() {
        return field;
    }

    @Override
    public Rectangle planRend(Boss b) {
        if (!inArena) return null;
        MonsterDef def = b.def;
        float cutW = field.width * def.rendRatio, cutH = field.height * def.rendRatio;
        List<Rectangle> sides = new ArrayList<>();
        if (field.width - cutW >= def.rendMinWidth) {
            sides.add(new Rectangle(field.x, field.y, cutW, field.height));                          // 왼쪽
            sides.add(new Rectangle(field.x + field.width - cutW, field.y, cutW, field.height));     // 오른쪽
        }
        if (field.height - cutH >= def.rendMinHeight) {
            sides.add(new Rectangle(field.x, field.y, field.width, cutH));                           // 아래
            sides.add(new Rectangle(field.x, field.y + field.height - cutH, field.width, cutH));     // 위
        }
        if (sides.isEmpty()) return null;
        // 마왕이 서 있는 쪽은 피한다 (마왕은 찢긴 공간 너머에 서지 않는다)
        sides.removeIf(r -> sides.size() > 1 && r.contains(b.footX(), b.footY()));
        return new Rectangle(sides.get(random.nextInt(sides.size())));
    }

    @Override
    public void applyRend(Rectangle r) {
        if (r == null || !inArena) return;
        rends.add(new Rectangle(r));
        collision.addObstacle(new Rectangle(r));
        // 남은 전장 다시 계산 (한쪽 끝이 잘렸다)
        if (r.width < field.width - 0.5f) {
            if (Math.abs(r.x - field.x) < 0.5f) field.x += r.width;
            field.width -= r.width;
        } else {
            if (Math.abs(r.y - field.y) < 0.5f) field.y += r.height;
            field.height -= r.height;
        }
        // 찢긴 곳에 있던 것들
        hazards.removeIf(h -> r.contains(h.x, h.y) && !h.rockPlaced);
        projectiles.removeIf(p -> r.contains(p.x, p.y));
        if (r.overlaps(unit.body) && hero.alive()) {
            // 공간과 함께 휩쓸렸다: 남은 쪽 가장자리로 내던져지고 큰 피해
            pushInsideField(unit);
            hitHeroTrue(Math.round(hero.maxHp() * balance.finalBoss.rendHitRatio), r.x + r.width / 2, r.y + r.height / 2);
        }
        if (demonKing != null && r.overlaps(demonKing.body)) pushInsideField(demonKing);
        toast("공간이 찢겨 나갔다! 공허에는 닿기만 해도 위험하다.");
    }

    private void pushInsideField(com.yongsa.combat.Body b) {
        float m = 10;
        float x = Math.max(field.x + b.body.width / 2 + m, Math.min(field.x + field.width - b.body.width / 2 - m, b.footX()));
        float y = Math.max(field.y + m, Math.min(field.y + field.height - b.body.height - m, b.footY()));
        b.teleport(x, y);
    }

    /** 공허 가장자리에 닿아 있으면 이번 프레임 피해. */
    private float rendTouch(float delta) {
        if (rends.isEmpty() || !inArena) return 0;
        // 찢길수록 공허가 번진다 (범위·피해 증가): 닿는 거리 5px → 한 번 더 찢길 때마다 +4px (최대 21px), 피해 +25%씩
        float reach = rendReach();
        Rectangle probe = new Rectangle(unit.body.x - reach, unit.body.y - reach, unit.body.width + reach * 2, unit.body.height + reach * 2);
        for (Rectangle r : rends) {
            if (r.overlaps(probe)) {
                rendTextTimer -= delta;
                if (rendTextTimer <= 0) {
                    texts.add(new FloatingText("공허!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 56));
                    rendTextTimer = 1.2f;
                }
                if (hero.invulnerable) return 0;
                return hero.maxHp() * balance.finalBoss.rendDamageRatio * (1f + 0.25f * (rends.size() - 1)) * delta;
            }
        }
        return 0;
    }

    /** 공허에 "닿았다"고 치는 거리 (px). 찢긴 횟수만큼 넓어진다. */
    public float rendReach() {
        return Math.min(21f, 5f + 4f * Math.max(0, rends.size() - 1));
    }

    private void clearRends() {
        for (Rectangle r : rends) collision.removeObstacle(r);
        rends.clear();
        field.set(map.arena);
    }

    @Override
    public void blindHero(float seconds) {
        if (!hero.alive() || cutscene != null) return;
        unit.applyBlind(seconds);
        texts.add(new FloatingText("실명!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 56));
        sound("blind");
        shake(2, 0.3f);
    }

    @Override
    public Rectangle arenaOf(Monster m) {
        return m.room != null ? m.room.bounds : field;
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
            for (int tries = 0; tries < 20; tries++) {
                // 많이 부를수록 넓게 퍼진다
                double a = random.nextFloat() * Math.PI * 2;
                float r = 50 + random.nextFloat() * (60 + n * 6);
                float x = owner.centerX() + (float) Math.cos(a) * r, y = owner.footY() + (float) Math.sin(a) * r * 0.7f;
                Rectangle probe = new Rectangle(x - def.width / 2, y, def.width, def.height);
                if (!room.bounds.contains(probe) || collision.blocked(probe)) continue;
                DungeonDef d = balance.dungeon(room.dungeon);
                boolean buffed = progress.defeated(d.id);
                Monster m = new Monster(def, room, x, y, buffed ? d.buffHpMultiplier : 1f, buffed ? d.buffAttackMultiplier : 1f);
                m.summoned = true;
                pendingSpawns.add(m);
                effects.add(new Effect(Effect.Type.SUMMON, x, y + 8, 0.5f));
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
        boolean onSwamp = false, onSigil = false;
        float voidDot = 0, stormDot = 0;
        boolean live = hero.alive() && cutscene == null;
        float fx = unit.footX(), fy = unit.footY() + 4;
        for (Hazard h : hazards) {
            h.time += delta;
            if (!h.visible()) continue;
            boolean touching = h.cloudActive() && live && h.contains(fx, fy);
            switch (h.type) {
                case POISON_CLOUD -> {
                    if (touching) {
                        if (unit.poison <= 0) sound("poison_hit");
                        unit.applyPoison(balance.statuses.poisonDuration);
                    }
                }
                case SWAMP -> onSwamp |= touching;
                case ABYSS_SIGIL -> onSigil |= touching;
                case VOID -> {
                    if (touching) voidDot += hero.maxHp() * h.ratio * delta;
                }
                case CRACK -> { }
                case BONE_STORM -> stormDot += updateStorm(h, delta, live);
                case FLESH_TIDE -> updateTide(h, live);
                case SWARM_ROW -> updateSwarmRow(h, live);
                case METEOR_FIELD -> {
                    if (!h.fired && h.activeTime() >= 0) {
                        h.fired = true;
                        shake(7, 0.5f);
                        sound("meteor");
                        if (live && h.contains(unit.centerX(), fy)) hitHeroTrue(h.damage, h.x, h.y + 300);
                    }
                }
                case EYE -> {
                    if (!h.fired && h.activeTime() >= 0) {
                        h.fired = true;
                        if (live) gaze(h);
                    }
                }
                default -> strikeOnce(h, live, fx, fy);
            }
        }
        for (Hazard h : hazards) {
            if (h.done() && h.rockPlaced) {
                collision.removeObstacle(h.rock);
                h.rockPlaced = false;
                Effect e = new Effect(Effect.Type.GUARD_BREAK, h.rock.x + h.rock.width / 2, h.rock.y + 14, 0.6f);
                effects.add(e);
                sound("rock_break");
            }
        }
        hazards.removeIf(Hazard::done);
        if (onSigil && !inverted && live) {
            texts.add(new FloatingText("키가 뒤틀렸다!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 56));
            sound("invert");
        }
        if (inverted && !onSigil && live) {
            // 마법진에서 빠져나와도 5초 동안은 좌우가 뒤바뀐 채로
            mirrorTime = MIRROR_AFTER_SIGIL;
            texts.add(new FloatingText("좌우 반전 " + (int) MIRROR_AFTER_SIGIL + "초!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 56));
        }
        inverted = onSigil && live;
        float rendDot = live ? rendTouch(delta) : 0;
        float dot = voidDot + stormDot + rendDot;
        if (dot > 0 && live) {
            hero.damageExact(dot);
            dotAccum += dot;
            voidTextTimer -= delta;
            if (voidDot > 0 && voidTextTimer <= 0) {
                texts.add(new FloatingText("공허!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 56));
                voidTextTimer = 1.2f;
            }
        }
        updateSwamp(onSwamp, delta);
    }

    /** 한 번 치는 것 (가시·번개·촉수·뼈창·레이저·왜곡 촉수). */
    private void strikeOnce(Hazard h, boolean live, float fx, float fy) {
        if (h.fired || h.activeTime() < 0) return;
        h.fired = true;
        switch (h.type) {
            case LIGHTNING -> {
                // 하늘을 가르는 굵은 벼락 + 섬광 (크고 웅장하게)
                sound("thunder");
                shake(6, 0.3f);
                Effect e = new Effect(Effect.Type.THUNDER, h.x, h.y, 0.75f);
                e.radius = h.radius;
                effects.add(e);
            }
            case BONE_SPIKE -> sound("spike");
            case TENTACLE -> { sound("tentacle"); shake(2, 0.12f); }
            case BONE_SPEAR -> { sound("spear"); shake(3, 0.15f); }
            case LASER -> { sound("laser"); shake(2, 0.15f); }
            case WARP -> { sound("warp_hit"); shake(5, 0.3f); }
            default -> { }
        }
        // 띠 모양은 몸 가운데 기준 (옆으로 길게 지나가므로)
        float py = h.isLine() ? unit.centerY() : fy;
        if (live && h.contains(h.isLine() ? unit.centerX() : fx, py)
                && hitHeroArea(h.damage, h.isLine() ? h.x : h.x, h.y) && h.type == Hazard.Type.LIGHTNING) {
            unit.applyShock(balance.statuses.shockDuration);
            texts.add(new FloatingText("감전!", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 52));
        }
    }

    /** 뼛가루 폭풍: 예고 중간에 바위가 떨어지고, 폭풍 동안 바위 그늘 밖이면 지속 피해. 이번 프레임 피해량. */
    private float updateStorm(Hazard h, float delta, boolean live) {
        if (!h.rockPlaced && h.activeTime() >= -h.warning * 0.5f && h.activeTime() < h.life) {
            if (!h.rock.overlaps(unit.body)) {
                collision.addObstacle(h.rock);
                h.rockPlaced = true;
                Effect e = new Effect(Effect.Type.SLAM, h.rock.x + h.rock.width / 2, h.rock.y + 8, 0.4f);
                e.radius = 40;
                effects.add(e);
                shake(5, 0.3f);
                sound("rock_fall");
            }
        }
        if (!h.cloudActive() || !live) return 0;
        if (h.sheltered(unit.centerX(), unit.footY() + 4, h.radius)) return 0;
        return hero.maxHp() * h.ratio * delta;
    }

    /** 바위 그늘 안인지 (HUD 안내용). */
    public boolean sheltered() {
        for (Hazard h : hazards) {
            if (h.type == Hazard.Type.BONE_STORM && h.cloudActive()) return h.sheltered(unit.centerX(), unit.footY() + 4, h.radius);
        }
        return false;
    }

    /** 살점 해일: 앞줄이 용사를 지나가는 순간 열린 균열 안이면 산다 (그 균열은 닫힌다). */
    private void updateTide(Hazard h, boolean live) {
        if (h.fired || h.activeTime() < 0 || !live) return;
        if (h.front() < h.along(unit.centerX(), unit.footY())) return;
        h.fired = true;
        shake(8, 0.6f);
        sound("tide_hit");
        for (Hazard c : hazards) {
            if (c.type == Hazard.Type.CRACK && c.cloudActive() && c.contains(unit.footX(), unit.footY() + 4)) {
                c.used = true;
                c.usedTime = c.time;
                texts.add(new FloatingText("균열에 숨었다!", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 56));
                return;
            }
        }
        hitHeroTrue(Math.round(hero.maxHp() * h.ratio), unit.centerX() - h.dirX * 40, unit.centerY() - h.dirY * 40);
    }

    /** 군체 한 줄: 지나가는 순간 빈틈에 있지 않으면 맞는다 (회피 무적으로 뚫을 수는 있다). */
    private void updateSwarmRow(Hazard h, boolean live) {
        if (h.fired || h.activeTime() < 0 || !live) return;
        if (h.front() < h.along(unit.centerX(), unit.footY())) return;
        h.fired = true;
        if (h.inGap(unit.centerX(), unit.footY())) return;
        if (hitHeroArea(h.damage, unit.centerX() - h.dirX * 30, unit.centerY() - h.dirY * 30)) {
            unit.knockback(unit.centerX() - h.dirX * 30, unit.centerY() - h.dirY * 30, 40);
        }
    }

    /** 눈알: 빛을 뿜는 순간 용사가 눈 쪽을 바라보고 있으면 벌 (현재 체력 절반 / 기절 / 스킬 봉인 중 하나). */
    private void gaze(Hazard h) {
        if (hero.invulnerable) return;
        float dx = h.x - unit.centerX(), dy = h.y - unit.centerY();
        float len = Math.max(0.01f, (float) Math.sqrt(dx * dx + dy * dy));
        float cos = (dx * unit.dirX + dy * unit.dirY) / len;
        shake(4, 0.3f);
        sound("eye_flash");
        if (cos < Math.cos(Math.toRadians(h.eyeAngle))) {
            texts.add(new FloatingText("시선을 피했다", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 56));
            return;
        }
        texts.add(new FloatingText("눈을 마주쳤다!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 64));
        switch (random.nextInt(3)) {
            case 0 -> hitHeroTrue(Math.max(1, hero.hp() / 2), h.x, h.y);
            case 1 -> {
                unit.applyStun(h.eyeStun);
                texts.add(new FloatingText("기절!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 48));
            }
            default -> {
                unit.applyShock(h.eyeSeal);
                texts.add(new FloatingText("스킬 봉인!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 48));
            }
        }
    }

    /** 늪: 서 있는 동안 초당 최대 체력의 n% + 둔화 (HeroUnit.updateSwamp 규칙). */
    private void updateSwamp(boolean onSwamp, float delta) {
        if (!hero.alive() || cutscene != null) return;
        if (onSwamp) {
            Boss lich = activeBoss();
            float ratio = lich != null && lich.def.swampDamageRatio > 0 ? lich.def.swampDamageRatio : 0.008f;
            float dot = hero.maxHp() * ratio * delta;
            hero.damageExact(dot);
            dotAccum += dot;
        }
        var st = balance.statuses;
        if (unit.updateSwamp(onSwamp, delta, st.slowDuration, st.slowRatio, st.slowRetouchTime)) {
            texts.add(new FloatingText("둔화!", FloatingText.Kind.INFO, unit.centerX(), unit.body.y + 52));
            sound("slow");
        }
    }

    /** 지금 밝기 (0~1). 캠프는 보스를 처음 잡을 때마다 밝아진다. */
    public float ambientLight() {
        if (inArena) return balance.arenaLight;
        if (inVillage()) return balance.villageLight[villagersFree() ? 1 : 0];
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
        focusChest = null;
        for (TreasureChest c : chests) {
            if (!c.opened && Math.hypot(c.x - unit.footX(), c.y - unit.footY()) < 40) {
                focus = Interact.CHEST;
                focusChest = c;
                return;
            }
        }
        focusVillager = null;
        if (inVillage()) {
            if (Math.hypot(map.villageReturn.x - unit.footX(), map.villageReturn.y - unit.footY()) < 44) {
                focus = Interact.VILLAGE_RETURN;
                return;
            }
            float bestV = 40;
            for (WorldMap.VillagerDef v : map.villagers) {
                float d = (float) Math.hypot(v.pos().x - unit.footX(), v.pos().y - unit.footY());
                if (d < bestV) { bestV = d; focusVillager = v; focus = Interact.TALK; }
            }
            return;
        }
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
    /** 상호작용 키 이름 (안내 문구 "E  대장간" 의 앞부분). 조작 키를 바꾸면 화면이 다시 알려 준다. */
    public String interactKey = "E";

    public String interactHint() {
        return switch (focus) {
            case VEIN -> interactKey + "  " + balance.ore(focusVein.ore).name + " 캐기 (" + focusVein.hitsLeft() + ")";
            case BED -> interactKey + "  침대에서 쉬기";
            case FORGE -> interactKey + "  대장간";
            case PORTAL -> interactKey + "  포탈 타고 캠프로";
            case SIGIL_UNSEAL -> interactKey + "  성검 해방";
            case SIGIL_RETURN -> interactKey + "  마왕에게 가기";
            case VILLAGE_RETURN -> interactKey + "  던전으로 돌아가기";
            case CHEST -> interactKey + "  보물상자 열기";
            case TALK -> villagersFree() ? interactKey + "  " + Josa.wa(villagerName(focusVillager.kind())) + " 이야기하기"
                    : interactKey + "  " + villagerName(focusVillager.kind()) + " 살펴보기";
            default -> "";
        };
    }

    private void interact() {
        switch (focus) {
            case VEIN -> mine(focusVein);
            case BED -> startSleep();
            case FORGE -> emit(GameEvent.of(GameEvent.Type.OPEN_FORGE));
            case PORTAL -> usePortal();
            case SIGIL_UNSEAL -> startCutscene(true);
            case SIGIL_RETURN -> startCutscene(false);
            case VILLAGE_RETURN -> returnFromVillage();
            case CHEST -> openChest(focusChest);
            case TALK -> talk(focusVillager);
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

    /** 침대에 눕는다: 잠들었다가(연출) 중간에 useBed로 쉬고 일어난다. */
    public void startSleep() {
        if (sleepTime > 0) return;
        sleepTime = 0.0001f;
        slept = false;
        unit.resetTimers();
        sound("sleep");
    }

    /** 자는 중이면 0~1 진행도, 깨어 있으면 0. */
    public float sleepProgress() {
        return sleepTime > 0 ? Math.min(1f, sleepTime / SLEEP_TIME) : 0f;
    }

    public boolean sleeping() {
        return sleepTime > 0;
    }

    /** 침대: 즉시 회복, 모든 던전의 잡몹·광맥만 다시 채우고 안개도 다시 덮는다 (FR-던전-05, A19). */
    public void useBed() {
        hero.fullHeal();
        resetRooms();
        chests.removeIf(c -> c.opened);
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
        Boss b = activeBoss();
        if (b != null) {
            if (b.consumeCocoonStart()) {
                emit(GameEvent.speech(b.def.name, "크아아아아—!", 2.5f));
                toast(Josa.i(b.def.name) + " 광폭화했다! 바람의 고치에 싸여 하늘로 — " + Math.round(b.def.cocoonTime) + "초 동안 메테오를 버텨라. (회피 쿨타임 없음)");
                shake(8, 1f);
                sound("cocoon");
            }
            if (b.consumeCocoonEnd()) toast(Josa.i(b.def.name) + " 내려왔다! 광폭화: 공격력이 1.5배가 되었다.");
        }
    }

    /** 보스가 쓰러지는 연출이 끝날 때까지 방을 비운 것으로 치지 않는다. */
    private boolean bossDying(Room r) {
        for (Monster m : r.monsters) if (m.isBoss() && !m.gone()) return true;
        return false;
    }

    /**
     * 겹친 몸 떼어 내기. 잡몹은 밀려나지만, 보스는 절대 밀리지 않고 용사 쪽을 밀어낸다
     * (회피·돌진으로 보스를 구석에 몰아넣는 것을 막는다).
     */
    private void separate(Monster m) {
        if (!m.alive() || !m.body.overlaps(unit.body)) return;
        Rectangle a = m.body, b = unit.body;
        float ox = Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x);
        float oy = Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y);
        if (m.isBoss()) {
            if (ox < oy) collision.move(b, unit.centerX() < m.centerX() ? -ox : ox, 0);
            else collision.move(b, 0, unit.centerY() < m.centerY() ? -oy : oy);
            return;
        }
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
        lockedDungeon = null; // 보스를 잡으면 던전 입구가 다시 열린다
        refreshGates();
        clearHazards();
        projectiles.clear();
        if (room.portal != null) room.portal.setActive(true);
        // 보상은 보스방의 보물상자에 (광물 30~40개, 첫 처치면 보스의 정수도). 포탈 위쪽에 나타난다
        boolean essence = result.firstClear() && !progress.hasOrb(d.id)
                && orbDrops.stream().noneMatch(o -> o.dungeon().equals(d.id));
        TreasureChest chest = placeChest(room, d.id, result.oreReward(), essence);
        Effect burst = new Effect(Effect.Type.ORB_BURST, chest.x, chest.y + 10, 0.8f);
        effects.add(burst);
        sound("boss_down");
        toast("보스가 남긴 보물상자가 나타났다! (E로 열기)");
        if (result.firstClear()) {
            emit(new GameEvent(GameEvent.Type.CAMP_BRIGHTER, null, d.id, progress.firstClears(), null));
        }
        emit(new GameEvent(GameEvent.Type.BOSS_DEFEATED, null, d.id, 0, result));
        emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
    }

    /** 보스방에 보물상자를 둔다. 이미 안 연 상자가 있으면 거기에 더 담는다. */
    private TreasureChest placeChest(Room room, String dungeon, int ore, boolean essence) {
        for (TreasureChest c : chests) {
            if (!c.opened && c.dungeon.equals(dungeon)) {
                c.ore += ore;
                c.essence |= essence;
                return c;
            }
        }
        float x = room.portal != null ? room.portal.x : room.bounds.x + room.bounds.width / 2;
        float y = room.portal != null ? room.portal.y + 78 : room.bounds.y + room.bounds.height / 2;
        TreasureChest c = new TreasureChest(dungeon, x, y, ore, essence);
        chests.add(c);
        return c;
    }

    /** 보물상자 열기: 광물이 들어오고, 정수가 있으면 상자 앞으로 튀어나온다 (밟으면 줍는다). */
    public void openChest(TreasureChest c) {
        if (c == null || c.opened) return;
        c.opened = true;
        c.openTime = 0;
        DungeonDef d = balance.dungeon(c.dungeon);
        inventory.addOre(d.ore, c.ore);
        texts.add(new FloatingText("+" + c.ore, FloatingText.Kind.ORE, c.x, c.y + 44, "ore_" + d.ore));
        emit(new GameEvent(GameEvent.Type.ORE_GAINED, null, d.ore, c.ore, null));
        if (c.essence && !progress.hasOrb(c.dungeon) && orbDrops.stream().noneMatch(o -> o.dungeon().equals(c.dungeon))) {
            orbDrops.add(new OrbDrop(c.dungeon, c.x, c.y - 30));
            toast(balance.boss(d.boss).name + "의 정수가 튀어나왔다! 주워서 가져가자.");
        }
        c.essence = false;
        effects.add(new Effect(Effect.Type.CHEST, c.x, c.y, 1.2f));
        sound("chest_open");
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
        clearHazards();
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
        for (Gate g : gates) g.setOpen(progress.isOpen(balance.dungeon(g.dungeon)) && !g.dungeon.equals(lockedDungeon));
    }

    /** 지금 갇혀 있는 던전 (없으면 null). */
    public String lockedDungeon() {
        return lockedDungeon;
    }

    private void unlockDungeon() {
        if (lockedDungeon == null) return;
        lockedDungeon = null;
        refreshGates();
    }

    private void checkGateBump(Controls in) {
        if (gateMsgCooldown > 0 || (in.moveX == 0 && in.moveY == 0)) return;
        Rectangle probe = new Rectangle(unit.body.x + in.moveX * 4, unit.body.y + in.moveY * 4, unit.body.width, unit.body.height);
        for (Gate g : gates) {
            if (!g.open() && g.bounds.overlaps(probe)) {
                DungeonDef d = balance.dungeon(g.dungeon);
                String msg = g.dungeon.equals(lockedDungeon)
                        ? "입구가 닫혀 있다. " + balance.boss(d.boss).name + "을(를) 쓰러뜨리거나, 쓰러져야 캠프로 돌아갈 수 있다."
                        : "봉인되어 있다. 앞 던전(" + balance.dungeon(d.requires).name + ")의 보스를 잡아야 열립니다.";
                emit(GameEvent.text(GameEvent.Type.GATE_SEALED, msg));
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
        clearRends();
        unit.teleport(map.arenaLand.x, map.arenaLand.y);
        unit.resetTimers();
        demonKing = new Boss(balance.finalBoss, null, map.finalBossSpawn.x, map.finalBossSpawn.y, 1f, 1f, true);
        clearHazards();
        arenaPortal.setActive(false);
        locate();
        shake(6, 0.4f);
        emit(GameEvent.of(GameEvent.Type.ARENA_ENTERED));
    }

    private void leaveArena() {
        inArena = false;
        clearRends();
        mirrorTime = 0;
        demonKing = null;
        clearHazards();
        arenaPortal.setActive(false);
    }

    private void updateArena(float delta) {
        if (!inArena || demonKing == null) return;
        demonKing.update(delta, this);
        separate(demonKing);
        if (demonKing.consumeTransitionStart()) {
            projectiles.clear();
            clearHazards();
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
            projectiles.clear();
            clearHazards();
            clearRends();     // 찢긴 공간이 닫혀 포탈까지 걸어갈 수 있다
            mirrorTime = 0;
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
            if (p.homing > 0 && p.homingTime > 0 && hero.alive()) {
                // 유도: 용사 쪽으로 조금씩 꺾는다
                p.homingTime -= delta;
                float sp = (float) Math.hypot(p.vx, p.vy);
                double cur = Math.atan2(p.vy, p.vx);
                double want = Math.atan2(unit.centerY() + 8 - p.y, unit.centerX() - p.x);
                double d = want - cur;
                while (d > Math.PI) d -= Math.PI * 2;
                while (d < -Math.PI) d += Math.PI * 2;
                double turn = Math.max(-p.homing * delta, Math.min(p.homing * delta, d));
                p.vx = (float) Math.cos(cur + turn) * sp;
                p.vy = (float) Math.sin(cur + turn) * sp;
            }
            p.x += p.vx * delta;
            p.y += p.vy * delta;
            p.life -= delta;
            probe.set(p.x - 3, p.y - 3, 6, 6);
            if (p.life <= 0 || collision.blocked(probe)) p.dead = true;
            else if (p.knock > 0 && p.damage <= 0 && Math.hypot(p.x - unit.centerX(), p.y - (unit.centerY() + 8)) < p.radius + 8
                    && !unit.invincible() && hero.alive() && cutscene == null) {
                // 바람기둥: 피해 없이 날아오던 방향으로 뒤쪽 벽까지 밀려난다 (막기로도 못 버틴다)
                p.dead = true;
                unit.knockback(unit.centerX() - p.vx, unit.centerY() - p.vy, p.knock);
                unit.startTumble(p.vx);
                unit.flash = 0.12f;
                texts.add(new FloatingText("날아갔다!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 56));
                sound("wind_hit");
                shake(3, 0.2f);
            }
            else if (Math.hypot(p.x - unit.centerX(), p.y - (unit.centerY() + 8)) < p.radius + 8 && !unit.invincible()
                    && hero.alive() && cutscene == null) {
                boolean hit = p.fromBoss ? hitHeroHeavy(p.damage, p.x - p.vx, p.y - p.vy)
                        : hitHero(p.damage, p.x - p.vx, p.y - p.vy);
                p.dead = true;
                if (hit) applyProjectileStatus(p);
                if (hit && p.knock > 0) {
                    // 태풍: 맵 끝까지 날아간다
                    unit.knockback(p.x - p.vx, p.y - p.vy, p.knock);
                    unit.startTumble(p.vx);
                    texts.add(new FloatingText("날아갔다!", FloatingText.Kind.IMMUNE, unit.centerX(), unit.body.y + 56));
                }
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
        unlockDungeon();
        if (inArena) leaveArena();
        cutscene = null;
        bossConfirmRoom = null;
        bossConfirmedRoom = null;
        projectiles.clear();
        clearHazards();
        pendingSpawns.clear();
        hero.fullHeal();
        unit.teleport(map.spawn.x, map.spawn.y);
        unit.resetTimers();
        deathReported = false;
        locate();
        // (2026-10-12 결정) 마왕에게 쓰러진 뒤 "검을 더 강화해야겠어" 독백은 뺐다
        diedToDemon = false;
        mirrorTime = 0;
        sleepTime = 0;
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
            if (r.outcome() == EnhanceOutcome.GREAT) progress.greatCount++;
            if (r.outcome() == EnhanceOutcome.DOWN) progress.downCount++;
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

    /** 개발자 도구: 무적 켜기/끄기 (테스트·발표용). 피해·벌칙(눈알 등)을 받지 않는다. 켜져 있는지 돌려준다. */
    public boolean toggleGodMode() {
        hero.invulnerable = !hero.invulnerable;
        if (hero.invulnerable) {
            hero.fullHeal();
            unit.clearStatuses();
        }
        toast(hero.invulnerable ? "[개발자 도구] 무적 켜짐: 어떤 공격에도 피해를 받지 않는다." : "[개발자 도구] 무적 꺼짐.");
        sound("ui_click");
        return hero.invulnerable;
    }

    /**
     * 개발자 도구: n번째 던전 보스방 입구(문 앞) 바로가기 (테스트·발표용).
     * 싸우던 방은 처음 상태로 돌리고, 갇혀 있던 던전 잠금도 푼다. 봉인된 던전도 갈 수 있다.
     * 문 앞에서 걸어 들어가면 평소처럼 보스방 확인창이 뜬다. 성공하면 true.
     */
    public boolean warpToBossDoor(int index) {
        if (cutscene != null || !hero.alive() || index < 0 || index >= balance.dungeons.length) return false;
        DungeonDef d = balance.dungeons[index];
        Room boss = bossRoom(d.id);
        if (boss == null) return false;
        if (inArena) leaveArena();
        resetFightingRooms();
        lockedDungeon = null;
        refreshGates();
        bossConfirmRoom = null;
        bossConfirmedRoom = null;
        projectiles.clear();
        clearHazards();
        unit.teleport(boss.def.outside().x, boss.def.outside().y);
        unit.resetTimers();
        locate();
        toast("[개발자 도구] " + d.name + " 보스방 입구로 이동했다. (" + balance.boss(d.boss).name + ")");
        sound("portal");
        emit(GameEvent.of(GameEvent.Type.TELEPORTED));
        return true;
    }

    /**
     * 개발자 도구: 마을이나 캠프로 바로 이동 (테스트·발표용). 출구 거절 횟수는 건드리지 않는다.
     * 싸우던 방은 처음 상태로, 던전 잠금은 푼다. 성공하면 true.
     */
    public boolean warpTo(boolean village) {
        if (cutscene != null || !hero.alive()) return false;
        if (inArena) leaveArena();
        resetFightingRooms();
        lockedDungeon = null;
        refreshGates();
        bossConfirmRoom = null;
        bossConfirmedRoom = null;
        projectiles.clear();
        clearHazards();
        if (village) unit.teleport(map.villageLand.x, map.villageLand.y);
        else unit.teleport(map.spawn.x, map.spawn.y);
        unit.resetTimers();
        onExit = false;
        locate();
        toast("[개발자 도구] " + (village ? "마을" : "캠프") + "로 이동했다.");
        sound("portal");
        emit(GameEvent.of(GameEvent.Type.TELEPORTED));
        return true;
    }

    /** 개발자 도구: 강화 성공 확률 100% 켜기/끄기. 켜져 있는지 돌려준다. */
    public boolean toggleSureEnhance() {
        boolean on = !enhanceService.alwaysSucceed();
        enhanceService.setAlwaysSucceed(on);
        toast(on ? "[개발자 도구] 강화 성공 100% 켜짐: 강화가 언제나 성공한다." : "[개발자 도구] 강화 성공 100% 꺼짐.");
        sound("ui_click");
        return on;
    }

    /** 개발자 도구: 강화 숙련도를 바로 정한다 (0 ~ 최대). */
    public void devSetMastery(int level) {
        mastery.set(level);
        toast("[개발자 도구] 강화 숙련도 Lv " + mastery.level() + (mastery.maxed() ? " (마스터)" : ""));
        sound("ui_click");
        emit(GameEvent.of(GameEvent.Type.SAVE_REQUEST));
    }

    /** 개발자 도구: 광물 무한 모드 켜기/끄기 (테스트·발표용). 켜져 있는지 돌려준다. */
    public boolean toggleInfiniteOre() {
        boolean on = !inventory.infiniteOre();
        inventory.setInfiniteOre(on);
        toast(on ? "[개발자 도구] 광물 무한 켜짐: 제작·강화에 광물이 줄지 않는다." : "[개발자 도구] 광물 무한 꺼짐.");
        sound("ui_click");
        return on;
    }


    public int attackOf(Weapon w) {
        return power.attack(w, inventory, hero);
    }

    /** 흡혈 (용사 최대 체력의 %). */
    public float lifestealOf(Weapon w) {
        return power.lifesteal(w, inventory, hero);
    }

    // =================================================================== 다음 할 일 (FR-공통-07)

    public Goal goal() {
        Goal g = freeGoal();
        if (lockedDungeon == null) return g;
        // 갇혀 있으면 캠프로 가야 하는 목표 대신 보스를 안내한다
        boolean needsCamp = switch (g.target()) {
            case FORGE, CAMP, SIGIL -> true;
            case ROOM -> !roomById.get(g.targetId()).dungeon.equals(lockedDungeon);
            default -> false;
        };
        if (!needsCamp) return g;
        DungeonDef d = balance.dungeon(lockedDungeon);
        return new Goal("갇혔다! " + balance.boss(d.boss).name + " 잡기 (쓰러지면 캠프로)", Goal.Target.ROOM, bossRoom(d.id).id);
    }

    private Goal freeGoal() {
        if (inArena) {
            return progress.finalDefeated()
                    ? new Goal("포탈(E)을 타고 캠프로 돌아가자", Goal.Target.PORTAL, "arena")
                    : new Goal("성검으로 마왕을 쓰러뜨리자", Goal.Target.NONE, null);
        }
        if (inVillage()) {
            return villagersFree()
                    ? new Goal("마을이 평화를 되찾았다! 마을 사람들과 이야기해 보자 (E)", Goal.Target.NONE, null)
                    : new Goal("위쪽 계단(E)으로 던전에 돌아가 마왕을 물리치자", Goal.Target.NONE, null);
        }
        if (progress.finalDefeated()) return new Goal("마왕을 쓰러뜨렸다! 아래쪽 출구로 나가 마을에 가 보자", Goal.Target.NONE, null);
        if (progress.sealBroken()) return new Goal("캠프 문양에서 마왕에게 가자", Goal.Target.SIGIL, null);
        for (TreasureChest c : chests) {
            if (!c.opened && c.essence) return new Goal("보스방의 보물상자를 열자 (E)", Goal.Target.ORB, c.dungeon);
        }
        if (!orbDrops.isEmpty()) return new Goal("보물상자에서 나온 보스의 정수를 줍자", Goal.Target.ORB, orbDrops.get(0).dungeon());
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
        // wasInCamp: 저장할 때 안전한 곳(캠프·마을·출구 통로)에 있었는지
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
