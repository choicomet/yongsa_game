package com.yongsa;

import com.yongsa.combat.Enemy;
import com.yongsa.combat.Statuses;
import com.yongsa.config.Balance;
import com.yongsa.model.CraftService;
import com.yongsa.model.GameState;
import com.yongsa.model.Modifiers;
import com.yongsa.model.Progress;
import com.yongsa.save.SaveManager;
import com.yongsa.world.DungeonGenerator;
import com.yongsa.world.DungeonMap;
import com.yongsa.world.GameWorld;
import com.yongsa.world.MapObject;
import com.yongsa.world.PlayerInput;
import com.yongsa.world.RoomDef;
import com.yongsa.world.Waves;
import com.yongsa.world.WorldEvent;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 웨이브·소환 표식, 강화 개체, 무기 형태, 상태 이상, 상자·제단, 보스 사망 연출.
 * 표식과 연출의 시간까지 보려고 balance.json 을 고치지 않은 수치(liveBalance)를 쓴다.
 */
class RoomSystemsTest {
    final Balance b = TestData.liveBalance();
    final float dt = GameWorld.STEP;

    static MapObject shrine(DungeonMap m, String zone) {
        for (MapObject o : m.interacts) if (o.type.equals("shrine") && o.prop("zone").equals(zone)) return o;
        return null;
    }

    GameState armed(DungeonMap map, String weapon, int level) {
        GameState s = GameState.newGame(map.seed, b.dataVersion, null);
        for (int i = 0; i < s.ore.length; i++) s.ore[i] = 50;
        s.defeatedBosses.add("boss_mine");
        s.defeatedBosses.add("boss_fortress");
        CraftService.craft(b, s, weapon);
        s.weapon(weapon).level = level;
        for (int i = 0; i < s.ore.length; i++) s.ore[i] = 0;
        s.defeatedBosses.clear();
        return s;
    }

    PlayerInput idle(GameWorld w) {
        PlayerInput in = new PlayerInput();
        in.aimX = w.hero.x;
        in.aimY = w.hero.y - 100;
        return in;
    }

    void run(GameWorld w, PlayerInput in, float seconds) {
        for (int i = 0, n = Math.round(seconds / dt); i < n; i++) {
            w.step(dt, in);
            in.clearEdges();
        }
    }

    GameWorld enter(DungeonMap map, GameState s, RoomDef r) {
        GameWorld w = new GameWorld(b, map, s);
        w.hero.x = r.centerX();
        w.hero.y = r.centerY();
        w.step(dt, idle(w));
        return w;
    }

    static boolean has(GameWorld w, WorldEvent.Type t) {
        for (WorldEvent e : w.events) if (e.type == t) return true;
        return false;
    }

    // ---- 웨이브와 표식 ----

    @Test
    void enemiesArriveThroughMarksAndCannotBeHitBefore() {
        DungeonMap map = TestData.map();
        GameWorld w = enter(map, armed(map, "sword", 0), map.firstCombatRoom("mine"));
        assertNotNull(w.combat);
        assertTrue(w.combat.enemies.isEmpty(), "표식이 떠 있는 동안에는 적이 없다");
        assertEquals(2, w.combat.marks.size());
        assertFalse(w.combat.finished(), "표식만 남아 있어도 방은 끝나지 않는다");
        PlayerInput in = idle(w);
        run(w, in, b.dungeon.spawnMarkTime - 0.1f);
        assertTrue(w.combat.enemies.isEmpty());
        run(w, in, 0.2f);
        assertEquals(2, w.combat.enemies.size());
        assertTrue(w.combat.marks.isEmpty());
        for (Enemy e : w.combat.enemies) assertTrue(e.grace > 0, "나온 직후에는 바로 공격하지 않는다");
    }

    @Test
    void roomClearsOnlyAfterLastWave() {
        DungeonMap map = TestData.map();
        // 웨이브가 둘 이상인 방
        RoomDef room = null;
        for (RoomDef r : map.rooms) {
            if (r.kind == RoomDef.Kind.ROOM && r.enemies && Waves.plan(b, r, map.seed).size() >= 2) {
                room = r;
                break;
            }
        }
        assertNotNull(room, "웨이브가 여러 번인 방이 있어야 한다");
        int total = Waves.plan(b, room, map.seed).size();
        GameState s = armed(map, "sword", 0);
        GameWorld w = enter(map, s, room);
        PlayerInput in = idle(w);
        for (int wave = 1; wave <= total; wave++) {
            run(w, in, b.dungeon.spawnMarkTime + 0.05f);
            assertNotNull(w.combat, wave + "번째 웨이브 중에는 전투가 이어진다");
            assertFalse(w.combat.enemies.isEmpty());
            assertEquals(total - wave, w.combat.wavesLeft());
            assertFalse(s.isRoomCleared(room.id));
            for (Enemy e : w.combat.enemies) {
                e.hp = 0;
                e.grace = 999;
            }
            w.step(dt, in);
        }
        assertNull(w.combat);
        assertTrue(s.isRoomCleared(room.id));
    }

    @Test
    void wavePlansFollowZoneRulesAndAreStable() {
        DungeonMap map = TestData.map();
        boolean sawElite = false;
        for (RoomDef r : map.rooms) {
            if (r.kind != RoomDef.Kind.ROOM || !r.enemies) continue;
            Balance.WaveDef def = b.zone(r.zone).waves;
            List<List<Waves.Spawn>> plan = Waves.plan(b, r, map.seed);
            int extra = r.guard ? def.guardExtraWaves : 0;
            assertTrue(plan.size() >= def.minWaves + extra && plan.size() <= def.maxWaves + extra, r.id);
            if (r.depth <= b.dungeon.shallowDepth && !r.guard) assertEquals(def.minWaves, plan.size(), "구역 초입은 최소 웨이브: " + r.id);
            List<List<Waves.Spawn>> again = Waves.plan(b, r, map.seed);
            for (int i = 0; i < plan.size(); i++) {
                List<Waves.Spawn> wave = plan.get(i);
                assertFalse(wave.isEmpty(), r.id);
                assertTrue(wave.size() <= def.maxPerWave, r.id);
                assertEquals(wave.size(), again.get(i).size(), "같은 방은 항상 같은 구성");
                float cost = 0;
                int elites = 0;
                for (int k = 0; k < wave.size(); k++) {
                    Waves.Spawn sp = wave.get(k);
                    assertSame(sp.def, again.get(i).get(k).def);
                    assertEquals(sp.elite, again.get(i).get(k).elite);
                    cost += sp.def.cost;
                    assertTrue(sp.def.minDepth <= r.depth, r.id + " 에 너무 이른 적: " + sp.def.id);
                    if (sp.elite) elites++;
                }
                float budget = Waves.budget(b, r);
                assertTrue(cost <= budget + 1e-3f || wave.size() == 1, r.id + " 예산 초과: " + cost + " > " + budget);
                assertTrue(elites <= 1, "웨이브마다 강화 개체는 하나까지");
                if (r.guard) assertEquals(1, elites, "제단을 지키는 방은 웨이브마다 강화 개체 하나: " + r.id);
                else if (elites > 0) assertTrue(r.depth >= b.elite.minDepth, "얕은 방에는 강화 개체가 없다: " + r.id);
                sawElite |= elites > 0;
            }
        }
        assertTrue(sawElite, "던전 어딘가에는 강화 개체가 있어야 한다");
    }

    @Test
    void eliteIsStrongerAndPaysOnceWithTheRoom() {
        Balance.EnemyDef slime = b.enemy("slime");
        Enemy normal = new Enemy(slime, 0, 0), elite = new Enemy(slime, 0, 0, b.elite);
        assertTrue(elite.maxHp > normal.maxHp && elite.damage > normal.damage && elite.radius > normal.radius);
        assertTrue(elite.isElite());

        DungeonMap map = TestData.map();
        RoomDef room = map.firstCombatRoom("mine");
        GameState s = armed(map, "sword", 0);
        GameWorld w = enter(map, s, room);
        PlayerInput in = idle(w);
        run(w, in, b.dungeon.spawnMarkTime + 0.05f);
        // 강화 개체 한 마리를 공격으로 쓰러뜨린 것과 같은 상태를 만든다
        w.combat.bonusReward += b.elite.bonusMinerals;
        for (Enemy e : w.combat.enemies) e.hp = 0;
        w.step(dt, in);
        assertEquals(b.zone("mine").roomReward + b.elite.bonusMinerals, s.ore[0]);
        assertEquals(0, s.ore[1], "폐광 보상은 폐광의 광석으로만");
        assertEquals(0, Progress.onRoomCleared(b, s, room.id, "mine", 5), "두 번 지급하지 않는다");
    }

    // ---- 무기 형태와 상태 이상 ----

    @Test
    void formsOpenAtEnhanceMilestones() {
        assertNull(b.form("sword", 4));
        assertEquals("넓은 마무리", b.form("sword", 5).name);
        assertEquals("넓은 마무리", b.form("sword", 9).name);
        assertEquals("서리 검기", b.form("sword", 10).name);
        assertEquals(5, b.nextForm("sword", 0).fromLevel);
        assertNull(b.nextForm("sword", 10));
        for (Balance.WeaponDef w : b.weapons) {
            int last = 0;
            for (Balance.FormDef f : w.forms) {
                assertTrue(f.fromLevel > last && f.fromLevel <= b.maxLevel(), w.id + " 형태 단계 순서");
                last = f.fromLevel;
            }
        }
    }

    @Test
    void finisherOnlyFormAppliesToThirdSwingOnly() {
        DungeonMap map = TestData.map();
        GameWorld w = new GameWorld(b, map, armed(map, "sword", 5));
        PlayerInput in = idle(w);
        in.attackHeld = true;
        float base = b.weapon("sword").reach;
        float[] reach = new float[4];
        Object last = null;
        for (int i = 0; i < 90 && reach[3] == 0; i++) {
            w.step(dt, in);
            if (w.hero.attack != null && w.hero.attack != last) {
                last = w.hero.attack;
                reach[w.hero.attack.combo] = w.hero.attack.length;
            }
        }
        assertEquals(base, reach[1], 1e-3);
        assertEquals(base, reach[2], 1e-3);
        assertEquals(base * b.form("sword", 5).reachMul, reach[3], 1e-3);
    }

    @Test
    void hammerPlusTenStunsWhatItHits() {
        DungeonMap map = TestData.map();
        GameWorld w = enter(map, armed(map, "hammer", 10), map.firstCombatRoom("mine"));
        PlayerInput in = idle(w);
        run(w, in, b.dungeon.spawnMarkTime + 0.05f);
        Enemy target = w.combat.enemies.get(0);
        w.combat.enemies.get(1).x = target.x + 400; // 다른 적은 멀리
        w.combat.enemies.get(1).grace = 999;
        target.hp = 9999;
        target.grace = 999;
        target.x = w.hero.x + 50;
        target.y = w.hero.y;
        in.aimX = target.x;
        in.aimY = w.hero.hurtY();
        in.attackHeld = true;
        w.events.clear();
        // 1타(옆으로 쓸기)는 기절시키지 않는다
        run(w, in, b.weapon("hammer").windup + 0.05f);
        assertTrue(target.hp < 9999, "망치가 맞았다");
        assertFalse(w.hero.attack.slam);
        assertFalse(Statuses.immobile(target), "쓸기는 기절시키지 않는다");
        // 2타(내려찍기)가 기절시킨다
        run(w, in, b.weapon("hammer").cycle() + 0.05f);
        target.kbTime = 0;
        target.x = w.hero.x + 50;
        assertTrue(w.hero.attack.slam, "2타째는 내려찍기");
        run(w, in, b.weapon("hammer").windup + 0.1f);
        assertTrue(Statuses.immobile(target), "기절");
        assertTrue(has(w, WorldEvent.Type.STATUS_APPLIED));
        in.attackHeld = false;
        float x = target.x;
        target.grace = 0;
        run(w, in, 0.4f);
        assertEquals(Enemy.State.RECOVER, target.state, "기절한 동안에는 상태 시간도 멈춘다");
        run(w, in, b.statusOrNull("stun").holdTime + 0.6f);
        assertFalse(Statuses.immobile(target));
        assertNotEquals(x, target.x, "풀리면 다시 움직인다");
    }

    @Test
    void chillBuildsUpThenFreezesAndBossesIgnoreIt() {
        Balance.StatusDef chill = b.statusOrNull("chill"), curse = b.statusOrNull("curse");
        Enemy e = new Enemy(b.enemy("slime"), 0, 0);
        assertFalse(Statuses.apply(e, chill, 0.5f), "절반만 쌓였다");
        assertFalse(Statuses.immobile(e));
        assertEquals(1f - chill.slowAtFull * 0.5f, Statuses.speedMul(e), 1e-4, "쌓이는 동안 느려진다");
        assertTrue(Statuses.apply(e, chill, 0.5f), "가득 차면 발동");
        assertTrue(Statuses.immobile(e));
        assertFalse(Statuses.apply(e, chill, 1f), "발동 중에는 다시 쌓이지 않는다");
        for (float t = 0; t < chill.holdTime + 0.1f; t += dt) Statuses.update(e, dt);
        assertFalse(Statuses.immobile(e));
        assertTrue(e.statuses.isEmpty(), "끝난 상태는 지워진다");

        // 덜 쌓인 게이지는 시간이 지나면 빠진다
        Statuses.apply(e, chill, 0.5f);
        for (float t = 0; t < 0.5f / chill.decayPerSec + 0.1f; t += dt) Statuses.update(e, dt);
        assertEquals(1f, Statuses.speedMul(e), 1e-4);

        Enemy boss = new Enemy(b.boss("boss_mine"), 0, 0);
        assertFalse(Statuses.apply(boss, chill, 1f), "보스는 얼지 않는다");
        assertTrue(Statuses.apply(boss, curse, 1f), "저주는 보스에게도 걸린다");
        assertEquals(curse.damageTakenMul, Statuses.damageTakenMul(boss), 1e-4);
    }

    // ---- 방 구성, 상자, 제단, 골드 ----

    @Test
    void roomsAreMostlyFightsAndEachZoneHasOneShrine() {
        for (long seed = 1; seed <= 12; seed++) {
            DungeonMap m = DungeonGenerator.generate(seed, b);
            for (Balance.ZoneDef z : b.zones) {
                int rooms = 0, oreOnly = 0, shrines = 0;
                float w0 = -1, h0 = -1;
                for (RoomDef r : m.zoneRooms(z.id)) {
                    if (r.kind != RoomDef.Kind.ROOM) continue;
                    rooms++;
                    if (w0 < 0) {
                        w0 = r.bounds.width;
                        h0 = r.bounds.height;
                    }
                    assertEquals(w0, r.bounds.width, "일반 방의 크기는 모두 같다");
                    assertEquals(h0, r.bounds.height);
                    if ("shrine".equals(r.feature)) {
                        shrines++;
                        assertFalse(r.veins, "제단 방에는 광맥이 없다");
                        assertEquals(z.shrine.guarded, r.enemies, z.id + " 제단을 지키는 적");
                        assertEquals(z.shrine.guarded, r.guard);
                        assertNotNull(shrine(m, z.id));
                    } else if (!r.enemies) {
                        oreOnly++;
                    }
                }
                assertEquals(1, shrines, "구역마다 제단 하나: " + z.id + " seed " + seed);
                assertTrue(oreOnly <= 3, "광물만 있는 방은 몇 개뿐: " + z.id + " " + oreOnly + "/" + rooms);
            }
        }
    }

    @Test
    void chestAppearsAfterClearAndPaysGoldOnce() {
        DungeonMap map = TestData.map();
        RoomDef room = map.firstCombatRoom("mine");
        GameState s = armed(map, "sword", 0);
        GameWorld w = enter(map, s, room);
        PlayerInput in = idle(w);
        assertFalse(w.hasChest(room), "싸우는 동안에는 상자가 없다");
        run(w, in, b.dungeon.spawnMarkTime + 0.05f);
        for (Enemy e : w.combat.enemies) e.hp = 0;
        w.events.clear();
        w.step(dt, in);
        assertNull(w.combat);
        assertTrue(w.hasChest(room), "방을 돌파하면 상자가 나타난다");
        assertTrue(has(w, WorldEvent.Type.CHEST_APPEARED));
        assertEquals(0, s.gold);
        // 방 가운데의 상자 앞으로
        com.badlogic.gdx.math.Vector2 spot = w.chestSpot(room, new com.badlogic.gdx.math.Vector2());
        w.hero.x = spot.x;
        w.hero.y = spot.y - 20;
        // 다가가기만 해도 상자가 열리고 골드가 들어온다
        w.saveRequested = false;
        w.step(dt, in);
        assertEquals(b.zone("mine").chestGold, s.gold);
        assertTrue(w.saveRequested);
        assertFalse(w.hasChest(room));
        in.interactPressed = true;
        w.step(dt, in);
        assertEquals(b.zone("mine").chestGold, s.gold, "한 번만");
        assertEquals(0, Progress.onChestOpened(s, room.id, 99));
    }

    @Test
    void guardedShrineTradesGoldForBlessingUntilDeath() {
        DungeonMap map = TestData.map();
        MapObject shrine = shrine(map, "fortress");
        Balance.ShrineDef def = b.zone("fortress").shrine;
        RoomDef room = map.room(shrine.prop("room"));
        assertTrue(room.guard && room.enemies, "요새의 제단은 강한 적이 지킨다");
        GameState s = armed(map, "sword", 0);
        s.defeatedBosses.add("boss_mine");
        s.gold = def.costGold + 5;
        // 돌파하기 전에는 바칠 수 없다
        GameWorld w = new GameWorld(b, map, s);
        w.hero.x = shrine.x();
        w.hero.y = shrine.bounds.y - 14;
        PlayerInput in = idle(w);
        w.step(dt, in);
        assertTrue(w.focus == null || !w.focus.enabled);
        w.combat = null;
        s.clearedRooms.add(room.id);

        s.gold = def.costGold - 1;
        in.interactPressed = true;
        w.step(dt, in);
        in.clearEdges();
        assertTrue(s.blessings.isEmpty(), "골드가 모자라면 받지 못한다");

        s.gold = def.costGold + 5;
        in.interactPressed = true;
        w.step(dt, in);
        in.clearEdges();
        assertEquals(1, s.blessings.size());
        assertTrue(java.util.Arrays.asList(def.blessings).contains(s.blessings.get(0)));
        assertEquals(5, s.gold);
        assertTrue(s.usedShrines.contains(shrine.name));
        w.step(dt, in);
        assertTrue(w.focus == null || w.focus.kind != GameWorld.FocusKind.SHRINE, "같은 제단은 한 번만");

        // 사망하면 축복과 제단 기록이 사라진다. 골드와 영구 진행은 그대로다.
        w.hero.hp = 0;
        run(w, in, b.hero.deathTime + 0.1f);
        assertTrue(s.blessings.isEmpty());
        assertTrue(s.usedShrines.isEmpty());
        assertEquals(b.hero.maxHp, w.hero.maxHp);
        assertEquals(5, s.gold);
        assertTrue(s.isRoomCleared(room.id));
    }

    @Test
    void rangedEnemiesShootInsteadOfTouching() {
        for (String id : new String[]{"spitter", "turret", "sapling"}) {
            Balance.EnemyDef d = b.enemy(id);
            assertEquals("shoot", d.kind, id);
            assertNotNull(d.shots, id + " 은 탄을 쏜다");
            Enemy e = new Enemy(d, 0, 0);
            assertTrue(e.ranged() && e.shotsOnly());
            e.enter(Enemy.State.ATTACK);
            e.shape = com.yongsa.combat.HitShape.circle(0, 0, 500);
            assertNull(e.activeHit(), "조준선에 닿아도 다치지 않는다");
        }
        assertEquals("slime", new Enemy(b.enemy("spitter"), 0, 0).spriteKey(), "같은 그림에 색만 다른 적");
        // 구역마다 원거리 적이 있다
        for (Balance.ZoneDef z : b.zones) {
            boolean ranged = false;
            for (String id : z.enemyTable) ranged |= new Enemy(b.enemy(id), 0, 0).ranged();
            assertTrue(ranged, z.id);
        }
    }

    @Test
    void comboLengthComesFromTheWeapon() {
        DungeonMap map = TestData.map();
        GameWorld w = new GameWorld(b, map, armed(map, "daggers", 0));
        PlayerInput in = idle(w);
        in.attackHeld = true;
        java.util.List<Integer> combos = new java.util.ArrayList<>();
        java.util.List<Boolean> finishers = new java.util.ArrayList<>();
        Object last = null;
        for (int i = 0; i < 120 && combos.size() < 5; i++) {
            w.step(dt, in);
            if (w.hero.attack != null && w.hero.attack != last) {
                last = w.hero.attack;
                combos.add(w.hero.attack.combo);
                finishers.add(w.hero.attack.finisher);
            }
        }
        assertEquals(java.util.Arrays.asList(1, 2, 3, 4, 1), combos);
        assertEquals(java.util.Arrays.asList(false, false, false, true, false), finishers);
        assertEquals(2, b.weapon("hammer").comboHits, "망치: 쓸기 + 내려찍기");
        assertEquals("slam", b.weapon("hammer").finisherMotion);
    }

    @Test
    void blessingsAreJustModifierBundles() {
        GameState s = GameState.newGame(1, b.dataVersion, null);
        s.blessings.add("vigor");
        s.blessings.add("swift");
        s.blessings.add("might");
        s.blessings.add("no_such_blessing"); // 데이터가 바뀐 저장: 건너뛴다
        Modifiers m = Modifiers.of(b, s);
        assertEquals(b.blessingOrNull("vigor").mods.maxHpAdd, m.maxHpAdd);
        assertEquals(b.blessingOrNull("swift").mods.dodgeCooldownMul, m.dodgeCooldownMul, 1e-5);
        assertEquals(b.blessingOrNull("might").mods.damageMul, m.damageMul, 1e-5);

        DungeonMap map = TestData.map();
        GameWorld w = new GameWorld(b, map, s);
        assertEquals(b.hero.maxHp + m.maxHpAdd, w.hero.maxHp);
        assertEquals(w.hero.maxHp, w.hero.hp, "불러온 직후에는 늘어난 최대 체력으로 시작한다");
    }

    @Test
    void newStateSurvivesSaveAndOldSavesStillLoad() throws Exception {
        SaveManager sm = new SaveManager(Files.createTempDirectory("ys"), b);
        GameState s = TestData.newGame();
        s.openedChests.add("mine_07");
        s.gold = 77;
        s.ore[1] = 9;
        s.blessings.add("vigor");
        s.usedShrines.add("fortress_11_shrine");
        assertTrue(sm.save(s), sm.lastError());
        GameState l = sm.load().state;
        assertEquals(s.openedChests, l.openedChests);
        assertEquals(77, l.gold);
        assertEquals(9, l.ore[1]);
        assertEquals(s.blessings, l.blessings);
        assertEquals(s.usedShrines, l.usedShrines);

        // 이 항목들이 없던 예전 저장
        GameState old = TestData.newGame();
        old.openedChests = null;
        old.blessings = null;
        old.usedShrines = null;
        old.ore = null;
        old.minerals = 12; // 광물이 한 종류이던 때의 저장
        sm.validate(old);
        assertEquals(12, old.ore[0], "예전 광물은 첫 구역 광석으로 옮긴다");
        assertEquals(0, old.minerals);
        assertNotNull(old.openedChests);
        assertNotNull(old.blessings);
        assertNotNull(old.usedShrines);
    }

    // ---- 보스 사망 연출 ----

    @Test
    void bossDeathPlaysOutBeforeTheRewardAndHeroIsSafe() {
        DungeonMap map = TestData.map();
        GameState s = armed(map, "sword", 0);
        GameWorld w = enter(map, s, map.room("mine_boss"));
        PlayerInput in = idle(w);
        run(w, in, b.bossIntro.cameraTime + b.bossIntro.readyTime + 0.05f);
        Enemy boss = w.combat.bossEnemy();
        boss.hp = 0;
        w.events.clear();
        w.step(dt, in);
        assertTrue(w.combat.dying);
        assertTrue(has(w, WorldEvent.Type.BOSS_DYING));
        assertFalse(s.isBossDefeated("boss_mine"), "연출이 끝나야 보상이 확정된다");
        assertSame(boss, w.combat.bossEnemy(), "쓰러지는 동안 보스는 화면에 남는다");
        w.damageHero(50, boss.x, boss.y);
        assertEquals(w.hero.maxHp, w.hero.hp, "연출 중에는 다치지 않는다");
        run(w, in, b.feel.bossDeathTime - 0.1f);
        assertNotNull(w.combat);
        run(w, in, 0.2f);
        assertNull(w.combat);
        assertTrue(s.isBossDefeated("boss_mine"));
        assertEquals(b.zone("mine").bossReward, s.ore[0]);
    }

    @Test
    void bossAttacksAreDataRows() {
        assertEquals(3, b.enemy("watcher").shots.count);
        assertNull(b.enemy("slime").shots);
        assertTrue(b.bossAttack("slam").shots.ring);
        assertEquals("scatter", b.bossAttack("rockfall").shape);
        assertNull(b.bossAttack("swing").shots);
        for (Balance.BossDef boss : b.bosses) {
            assertTrue(boss.pattern.length >= 4, boss.id + " 패턴이 단순하지 않다");
            assertTrue(boss.enrageAt > 0 && boss.enragePattern.length > 0, boss.id + " 은 격노한다");
            java.util.Set<String> kinds = new java.util.HashSet<>(java.util.Arrays.asList(boss.pattern));
            kinds.addAll(java.util.Arrays.asList(boss.enragePattern));
            assertTrue(kinds.size() >= 4, boss.id + " 공격 종류");
        }
    }

    @Test
    void scatterAttackWarnsSeveralAreasAndAnyOfThemHits() {
        DungeonMap map = TestData.map();
        GameWorld w = enter(map, armed(map, "sword", 0), map.room("mine_boss"));
        PlayerInput in = idle(w);
        run(w, in, b.bossIntro.cameraTime + b.bossIntro.readyTime + 0.05f);
        Enemy boss = w.combat.bossEnemy();
        Balance.BossAttackDef rock = b.bossAttack("rockfall");
        com.yongsa.combat.BossPattern.update(boss, w, b, 0); // 상태 초기화
        boss.enter(Enemy.State.TELEGRAPH);
        boss.attackId = rock.id;
        boss.telegraphTime = 0.01f;
        boss.activeTime = rock.active;
        boss.recoverTime = 1;
        boss.dashing = false;
        // 용사는 첫 영역 밖, 추가 영역 안
        boss.shape = com.yongsa.combat.HitShape.circle(w.hero.x + 400, w.hero.y, rock.radius);
        boss.extraShapes.clear();
        boss.extraShapes.add(com.yongsa.combat.HitShape.circle(w.hero.hurtX(), w.hero.hurtY(), rock.radius));
        int hp = w.hero.hp;
        run(w, in, 0.1f);
        assertTrue(w.hero.hp < hp, "흩어진 영역 중 하나에 서 있으면 맞는다");
    }

    @Test
    void finalBossSummonsHelpersWhoFallWithIt() {
        DungeonMap map = TestData.map();
        GameState s = armed(map, "sword", 0);
        s.defeatedBosses.add("boss_mine");
        s.defeatedBosses.add("boss_fortress");
        GameWorld w = enter(map, s, map.room("temple_boss"));
        PlayerInput in = idle(w);
        run(w, in, b.bossIntro.cameraTime + b.bossIntro.readyTime + 0.05f);
        Enemy boss = w.combat.bossEnemy();
        assertTrue(java.util.Arrays.asList(boss.bossDef.pattern).contains("summon"), "마지막 보스는 부하를 부른다");
        w.hero.invuln = 999;
        // 소환 공격을 시작시킨다
        Balance.BossAttackDef sm = b.bossAttack("summon");
        boss.enter(Enemy.State.TELEGRAPH);
        boss.attackId = sm.id;
        boss.telegraphTime = 0.01f;
        boss.activeTime = sm.active;
        boss.recoverTime = 5;
        boss.shape = null;
        boss.extraShapes.clear();
        run(w, in, 0.05f);
        assertEquals(sm.summon.count, w.combat.marks.size(), "보스 둘레에 소환 표식");
        run(w, in, b.dungeon.spawnMarkTime + 0.05f);
        long helpers = w.combat.enemies.stream().filter(e -> !e.isBoss()).count();
        assertEquals(sm.summon.count, helpers);
        assertNotNull(w.combat, "부하가 있어도 보스가 살아 있으면 전투는 계속된다");
        // 보스가 쓰러지면 부하도 함께 쓰러지고 보상이 확정된다
        boss.hp = 0;
        run(w, in, b.feel.bossDeathTime + 0.2f);
        assertNull(w.combat);
        assertTrue(s.isBossDefeated("boss_temple"));
    }

    // ---- 회복, 접촉 피해, 개발자 모드 ----

    @Test
    void touchingMeleeEnemyHurtsButRangedAndFreshOnesDoNot() {
        DungeonMap map = TestData.map();
        GameWorld w = enter(map, armed(map, "sword", 0), map.firstCombatRoom("mine"));
        PlayerInput in = idle(w);
        run(w, in, b.dungeon.spawnMarkTime + 0.05f);
        Enemy slime = w.combat.enemies.get(0);
        w.combat.enemies.get(1).x = slime.x + 400;
        w.combat.enemies.get(1).grace = 999;
        assertTrue(slime.contactMul() > 0, "슬라임은 근접 적");
        // 막 나온 적은 닿아도 다치지 않는다
        w.hero.x = slime.x;
        w.hero.y = slime.y;
        slime.grace = 999;
        w.step(dt, in);
        assertEquals(w.hero.maxHp, w.hero.hp);
        // 공격 예고가 없어도, 몸이 닿으면 바로 다친다
        slime.grace = 0;
        slime.enter(Enemy.State.RECOVER);
        slime.recoverTime = 99;
        w.hero.x = slime.x;
        w.hero.y = slime.y;
        w.step(dt, in);
        assertEquals(w.hero.maxHp - Math.round(slime.damage * slime.contactMul()), w.hero.hp);
        // 한 걸음 떨어져 있으면 다치지 않는다
        w.hero.invuln = 0;
        w.hero.kbTime = 0;
        int hp = w.hero.hp;
        w.hero.x = slime.x + slime.radius + w.hero.hurtRadius() + 6;
        w.hero.y = slime.y;
        w.step(dt, in);
        assertEquals(hp, w.hero.hp);
        // 원거리 적과 얼어붙은 적은 닿아도 다치지 않는다
        assertEquals(0f, new Enemy(b.enemy("turret"), 0, 0).contactMul());
        assertEquals(0f, new Enemy(b.enemy("watcher"), 0, 0).contactMul());
    }

    @Test
    void chestsMayHoldHealingButBossChestsDoNot() {
        float total = 0;
        for (Balance.ChestHealDef h : b.chestHeals) {
            assertTrue(h.heal > 0 && h.heal <= 1 && h.chance > 0, h.id);
            total += h.chance;
        }
        assertEquals(3, b.chestHeals.length, "작은 / 절반 / 큰 회복");
        assertTrue(total < 1f, "회복약이 없는 상자도 있다");
        DungeonMap map = TestData.map();
        GameState s = armed(map, "sword", 0);
        GameWorld w = new GameWorld(b, map, s);
        java.util.Set<String> seen = new java.util.HashSet<>();
        RoomDef withHeal = null;
        Balance.ChestHealDef heal = null;
        for (RoomDef r : map.rooms) {
            if (r.kind == RoomDef.Kind.BOSS) assertNull(w.chestHeal(r), "보스방 상자에는 회복약이 없다");
            if (r.kind != RoomDef.Kind.ROOM || !r.enemies) continue;
            Balance.ChestHealDef h = w.chestHeal(r);
            assertSame(h, w.chestHeal(r), "같은 방의 상자는 항상 같다");
            if (h == null) continue;
            seen.add(h.id);
            if (withHeal == null || h.heal < heal.heal) {
                withHeal = r;
                heal = h;
            }
        }
        assertTrue(seen.size() >= 2, "던전에 회복약 종류가 여럿 있다: " + seen);
        // 그 방을 돌파한 뒤 상자를 연다
        s.clearedRooms.add(withHeal.id);
        com.badlogic.gdx.math.Vector2 spot = w.chestSpot(withHeal, new com.badlogic.gdx.math.Vector2());
        w.hero.x = spot.x;
        w.hero.y = spot.y - 20;
        w.hero.hp = 10;
        PlayerInput in = idle(w);
        w.step(dt, in);
        in.interactPressed = true;
        w.step(dt, in);
        assertEquals(Math.min(w.hero.maxHp, 10 + Math.round(w.hero.maxHp * heal.heal)), w.hero.hp);
        assertTrue(has(w, WorldEvent.Type.CHEST_HEAL));
        assertEquals(b.zone(withHeal.zone).chestGold, s.gold, "골드는 다가갔을 때 이미 들어왔다");
        assertNull(w.pendingHeal(withHeal), "마신 회복약은 사라진다");
    }

    @Test
    void potionStaysUntilNeeded() {
        DungeonMap map = TestData.map();
        GameState s = armed(map, "sword", 0);
        GameWorld w = new GameWorld(b, map, s);
        RoomDef room = null;
        for (RoomDef r : map.rooms) {
            if (r.kind == RoomDef.Kind.ROOM && r.enemies && w.chestHeal(r) != null) room = r;
        }
        assertNotNull(room);
        s.clearedRooms.add(room.id);
        com.badlogic.gdx.math.Vector2 spot = w.chestSpot(room, new com.badlogic.gdx.math.Vector2());
        w.hero.x = spot.x;
        w.hero.y = spot.y - 20;
        PlayerInput in = idle(w);
        w.step(dt, in);
        assertTrue(s.gold > 0);
        assertNotNull(w.pendingHeal(room), "회복약은 상자 자리에 남는다");
        // 체력이 가득하면 마시지 않고, 회복 알림도 뜨지 않는다
        w.step(dt, in);
        assertFalse(w.focus.enabled);
        in.interactPressed = true;
        w.events.clear();
        w.step(dt, in);
        in.clearEdges();
        assertFalse(has(w, WorldEvent.Type.CHEST_HEAL));
        assertNotNull(w.pendingHeal(room));
        // 나중에 다쳐서 돌아오면 마실 수 있다
        w.hero.hp = 1;
        w.step(dt, in);
        assertTrue(w.focus.enabled);
        in.interactPressed = true;
        w.step(dt, in);
        assertTrue(w.hero.hp > 1);
        assertNull(w.pendingHeal(room));
        assertTrue(s.takenHeals.contains(room.id));
    }

    @Test
    void combatRoomsHaveCoverThatStopsShotsAndSight() {
        DungeonMap map = TestData.map();
        assertFalse(map.covers.isEmpty());
        int rooms = 0, withCover = 0;
        for (RoomDef r : map.rooms) {
            if (r.kind != RoomDef.Kind.ROOM || !r.enemies) continue;
            rooms++;
            int n = 0;
            for (com.badlogic.gdx.math.Rectangle c : map.covers) {
                if (!r.bounds.contains(c)) continue;
                n++;
                assertTrue(map.solids.contains(c), "엄폐물은 지나갈 수 없다");
                // 방 한가운데(상자 자리)와 문 앞은 비어 있다
                assertFalse(c.contains(r.centerX(), r.centerY()));
                for (com.yongsa.world.DoorDef d : map.doorsOf(r.id)) {
                    float dx = d.bounds.x + d.bounds.width / 2 - (c.x + c.width / 2), dy = d.bounds.y + d.bounds.height / 2 - (c.y + c.height / 2);
                    assertTrue(Math.hypot(dx, dy) > 2.5 * map.tileSize, r.id);
                }
            }
            assertTrue(n <= b.dungeon.coverMax);
            if (n > 0) withCover++;
        }
        assertTrue(withCover >= rooms * 0.8f, "전투방 대부분에 엄폐물이 있다: " + withCover + "/" + rooms);
        for (RoomDef r : map.rooms) {
            if (r.kind == RoomDef.Kind.BOSS || r.kind == RoomDef.Kind.CAMP) {
                for (com.badlogic.gdx.math.Rectangle c : map.covers) assertFalse(r.bounds.overlaps(c), r.id);
            }
        }
        // 엄폐물 뒤는 적의 시야에서 가려진다
        com.badlogic.gdx.math.Rectangle c = map.covers.get(0);
        GameWorld w = new GameWorld(b, map, armed(map, "sword", 0));
        float y = c.y + c.height / 2;
        assertTrue(w.rayLength(c.x - 60, y, 1, 0, 200) < 70);
        assertTrue(map.coverAt(c.x + 2, y));
    }

    @Test
    void braziersNeverStandInBossDoorways() {
        for (long seed = 1; seed <= 12; seed++) {
            DungeonMap map = com.yongsa.world.DungeonGenerator.generate(seed, b);
            for (RoomDef r : map.rooms) {
                if (r.kind != RoomDef.Kind.ANTE && r.kind != RoomDef.Kind.BOSS) continue;
                for (com.yongsa.world.MapObject o : map.decor) {
                    if (!o.type.startsWith("brazier") || !r.bounds.contains(o.x(), o.y())) continue;
                    for (com.yongsa.world.DoorDef d : map.doorsOf(r.id)) {
                        double dist = Math.hypot(d.bounds.x + d.bounds.width / 2 - o.x(), d.bounds.y + d.bounds.height / 2 - o.y());
                        assertTrue(dist > 3 * map.tileSize, r.id + " seed " + seed);
                    }
                }
            }
        }
    }

    @Test
    void knightRalliesThreeChargersThenRestsSixAttacks() {
        Balance.SummonDef rally = b.bossAttack("rally").summon;
        assertEquals(3, rally.count);
        assertTrue(rally.restAttacks >= 6);
        DungeonMap map = TestData.map();
        GameWorld w = enter(map, armed(map, "sword", 0), map.room("fortress_boss"));
        w.devInvincible = true;
        PlayerInput in = idle(w);
        Enemy boss = w.combat.bossEnemy();
        int summons = 0, others = 0, othersAtSecond = -1;
        boolean cleared = false;
        Enemy.State prev = boss.state;
        for (int f = 0; f < 60 * 120 && summons < 2; f++) {
            // 용사는 보스 곁에 서서 모든 공격이 나오게 한다
            w.hero.x = boss.x + 40;
            w.hero.y = boss.y;
            w.step(dt, in);
            if (prev == Enemy.State.TELEGRAPH && boss.state == Enemy.State.ATTACK) {
                if ("rally".equals(boss.attackId)) {
                    summons++;
                    if (summons == 2) othersAtSecond = others;
                } else if (cleared) others++;
            }
            prev = boss.state;
            // 첫 소환으로 나온 돌격병 셋이 모두 나오면 쓰러뜨린다
            if (summons == 1 && !cleared && w.combat.marks.isEmpty() && w.combat.enemies.size() == 4) {
                for (Enemy e : w.combat.enemies) if (!e.isBoss()) e.hp = 0;
                cleared = true;
            }
        }
        assertEquals(2, summons, "쉬고 나면 다시 부른다");
        assertTrue(othersAtSecond >= rally.restAttacks, "돌격병이 다 쓰러진 뒤 다른 공격 " + othersAtSecond + "번");
    }

    @Test
    void wideBossesCanBeHitFromTheSideWithoutTouching() {
        for (String id : new String[]{"boss_mine", "boss_fortress"}) {
            Balance.BossDef def = b.boss(id);
            Enemy boss = new Enemy(def, 0, 0);
            Balance.WeaponDef sword = b.weapon("sword");
            // 몸이 닿아 다치는 거리보다 넉넉히 떨어진 옆자리에서 휘두른 철검이 닿는다
            float stand = def.hurtRadius + def.hurtExtend + sword.reach - 6;
            assertTrue(stand > def.radius + 60, id);
            com.yongsa.combat.HitShape swing = com.yongsa.combat.HitShape.box(-stand, boss.hurtY(), 1, 0, sword.reach, sword.width);
            swing.type = com.yongsa.combat.HitShape.Type.SECTOR;
            assertTrue(boss.hitBy(swing), id);
            swing.set(-stand - 30, boss.hurtY(), 1, 0, sword.reach, sword.width);
            assertFalse(boss.hitBy(swing), id + ": 닿지 않는 거리");
        }
    }

    @Test
    void rollingPassesThroughShotsAndBodies() {
        DungeonMap map = TestData.map();
        GameWorld w = enter(map, armed(map, "sword", 0), map.firstCombatRoom("mine"));
        PlayerInput in = idle(w);
        run(w, in, b.dungeon.spawnMarkTime + 0.05f);
        for (Enemy e : w.combat.enemies) {
            e.grace = 0;
            e.enter(Enemy.State.RECOVER);
            e.recoverTime = 99;
        }
        Enemy slime = w.combat.enemies.get(0);
        // 적 바로 옆에서 적 쪽으로 구른다: 몸을 지나쳐도 다치지 않는다
        w.hero.x = slime.x - 40;
        w.hero.y = slime.y;
        in.moveX = 1;
        in.dodgePressed = true;
        w.step(dt, in);
        in.clearEdges();
        // 구르는 동안 날아온 탄은 맞지 않고 그대로 지나간다
        com.yongsa.combat.Projectile shot = new com.yongsa.combat.Projectile(w.hero.x + 4, w.hero.hurtY(), 0f, 1f, 30);
        w.combat.projectiles.add(shot);
        for (int i = 0; i < 6; i++) w.step(dt, in);
        assertTrue(w.hero.x > slime.x - 20, "적 쪽으로 굴러 들어갔다");
        assertEquals(w.hero.maxHp, w.hero.hp);
        assertTrue(w.combat.projectiles.contains(shot), "탄은 사라지지 않고 지나간다");
        assertTrue(b.hero.dodgeInvuln > b.hero.dodgeDuration, "구른 직후에도 잠깐 안전하다");
    }

    @Test
    void developerModeFlags() {
        DungeonMap map = TestData.map();
        GameState s = armed(map, "sword", 0);
        GameWorld w = new GameWorld(b, map, s);
        assertFalse(w.canShockwave(), "첫 보스 전에는 충격파가 없다");
        w.devUnlockAll = true;
        assertTrue(w.canShockwave(), "개발자 모드: 해금 조건 무시");
        w.devInvincible = true;
        w.damageHero(999, w.hero.x + 10, w.hero.y);
        assertEquals(w.hero.maxHp, w.hero.hp, "개발자 모드: 무적");
        // 보스 앞으로 바로 이동 (진행 중인 전투는 버린다)
        w.devTeleport(map.point("shortcut_temple_ante"));
        assertNull(w.combat);
        w.step(dt, idle(w));
        assertEquals("temple_ante", w.room.id);
        w.devTeleport(w.spawnPoint());
        w.step(dt, idle(w));
        assertEquals("camp", w.room.id);
    }

    @Test
    void bossKillRegrowsVeins() {
        DungeonMap map = TestData.map();
        GameState s = armed(map, "sword", 0);
        GameWorld w = enter(map, s, map.room("mine_boss"));
        PlayerInput in = idle(w);
        run(w, in, b.bossIntro.cameraTime + b.bossIntro.readyTime + 0.05f);
        int cycle = s.miningCycle;
        w.hero.hp = 7;
        w.combat.bossEnemy().hp = 0;
        run(w, in, b.feel.bossDeathTime + 0.2f);
        assertEquals(cycle + 1, s.miningCycle, "보스를 쓰러뜨리면 광맥이 새로 자란다");
        assertEquals(w.hero.maxHp, w.hero.hp);
    }

    @Test
    void validationCatchesIdsThatPointNowhere() {
        Balance bad = TestData.liveBalance();
        bad.weapons[0].forms[0].status = "no_such_status";
        assertThrows(IllegalStateException.class, bad::validate);
        Balance bad2 = TestData.liveBalance();
        bad2.zone("temple").shrine.blessings = new String[]{"no_such_blessing"};
        assertThrows(IllegalStateException.class, bad2::validate);
        Balance bad3 = TestData.liveBalance();
        bad3.weapons[0].ore = "no_such_zone";
        assertThrows(IllegalArgumentException.class, bad3::validate);
        Balance bad4 = TestData.liveBalance();
        bad4.bosses[0].pattern = new String[]{"no_such_attack"};
        assertThrows(IllegalArgumentException.class, bad4::validate);
    }
}
