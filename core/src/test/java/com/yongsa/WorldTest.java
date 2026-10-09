package com.yongsa;

import com.badlogic.gdx.math.Vector2;
import com.yongsa.combat.AttackInstance;
import com.yongsa.combat.Enemy;
import com.yongsa.combat.HitShape;
import com.yongsa.config.Balance;
import com.yongsa.model.CraftService;
import com.yongsa.model.GameState;
import com.yongsa.model.MineRoomState;
import com.yongsa.model.Progress;
import com.yongsa.model.VeinState;
import com.yongsa.world.DoorDef;
import com.yongsa.world.DungeonMap;
import com.yongsa.world.FixedStepper;
import com.yongsa.world.GameWorld;
import com.yongsa.world.MapObject;
import com.yongsa.world.PlayerInput;
import com.yongsa.world.RoomDef;
import com.yongsa.world.WorldEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {
    final Balance b = TestData.balance();
    final DungeonMap map = TestData.map();
    final float dt = GameWorld.STEP;

    GameState armed() {
        GameState s = TestData.newGame();
        s.minerals = 6;
        CraftService.craft(b, s, "sword");
        return s;
    }

    void run(GameWorld w, PlayerInput in, float seconds) {
        int n = Math.round(seconds / dt);
        for (int i = 0; i < n; i++) {
            w.step(dt, in);
            in.clearEdges();
        }
    }

    void put(GameWorld w, RoomDef r) {
        w.hero.x = r.centerX();
        w.hero.y = r.centerY();
    }

    PlayerInput idle(GameWorld w) {
        PlayerInput in = new PlayerInput();
        in.aimX = w.hero.x;
        in.aimY = w.hero.y - 100;
        return in;
    }

    @Test
    void T21_newGameStartsAtCampSpawn() {
        GameWorld w = new GameWorld(b, map, TestData.newGame());
        Vector2 sp = map.point("camp_spawn");
        assertEquals(sp.x, w.hero.x);
        assertEquals(sp.y, w.hero.y);
        assertEquals(100, w.hero.hp);
        assertEquals("중앙 캠프", w.locationName());
        assertFalse(w.blockedForHero(w.hero.body()));
    }

    @Test
    void noWeaponCannotEnterFirstBattleRoom() {
        GameWorld w = new GameWorld(b, map, TestData.newGame());
        DoorDef d = map.door("mine_door_a");
        assertTrue(w.isDoorLocked(d));
        assertNotNull(w.ruleLockReason(d));
        assertTrue(w.isDoorLocked(map.door("fortress_gate")));
        assertEquals("폐광의 수호자 처치 후 개방", w.doorLockReason(map.door("fortress_gate")));
    }

    @Test
    void walkLeftReachesOreRoomWithoutWeapon() {
        GameWorld w = new GameWorld(b, map, TestData.newGame());
        PlayerInput in = idle(w);
        // 모닥불 아래에서 왼쪽 → 통로 높이로 위 → 왼쪽 통로로 걷는다 (무기 없이)
        RoomDef camp = map.room("camp");
        RoomDef ore = map.room("mine_ore");
        in.moveX = -1;
        while (w.hero.x > camp.bounds.x + 48) {
            w.step(dt, in);
            assertTrue(w.time < 5, "왼쪽 이동이 막힘");
        }
        in.moveX = 0;
        float target = ore.centerY();
        while (Math.abs(w.hero.y - target) > 3) {
            in.moveY = target > w.hero.y ? 1 : -1;
            w.step(dt, in);
            assertTrue(w.time < 10, "세로 이동이 막힘");
        }
        in.moveY = 0;
        in.moveX = -1;
        run(w, in, 3f);
        assertNotNull(w.room);
        assertEquals("mine_ore", w.room.id);
        assertNotNull(w.state.mineRoom("mine_ore"));
        assertTrue(w.saveRequested);
    }

    @Test
    void miningRequiresHoldingStill() {
        GameState s = TestData.newGame();
        GameWorld w = new GameWorld(b, map, s);
        RoomDef ore = map.room("mine_ore");
        put(w, ore);
        PlayerInput in = idle(w);
        w.step(dt, in);
        MineRoomState m = s.mineRoom("mine_ore");
        VeinState v = m.veins.get(0);
        MapObject o = map.vein(v.candidateId);
        // 광맥 바로 옆(아래/위)에 선다
        w.hero.x = o.x();
        w.hero.y = o.y() + (o.y() > ore.centerY() ? -24 : 24);
        in.interactHeld = true;
        run(w, in, 0.5f);
        assertEquals(0, s.minerals);
        in.interactHeld = false; // 놓으면 취소
        w.step(dt, in);
        assertEquals(0f, w.miningProgress);
        in.interactHeld = true;
        run(w, in, 1.05f);
        assertEquals(3, s.minerals);
        assertTrue(v.mined);
    }

    @Test
    void battleRoomLocksSpawnsAndClearsOnce() {
        GameState s = armed();
        GameWorld w = new GameWorld(b, map, s);
        RoomDef a = map.room("mine_battle_a");
        put(w, a);
        PlayerInput in = idle(w);
        w.step(dt, in);
        assertNotNull(w.combat);
        assertEquals(2, w.combat.enemies.size());
        assertTrue(w.isDoorLocked(map.door("mine_door_a")));
        assertFalse(w.canReturnToCamp());
        for (Enemy e : w.combat.enemies) e.hp = 0;
        w.step(dt, in);
        assertNull(w.combat);
        assertTrue(s.isRoomCleared("mine_battle_a"));
        assertEquals(6, s.minerals);
        assertFalse(w.isDoorLocked(map.door("mine_door_a")));
        // 다시 들어와도 적이 없다
        w.hero.x += 1;
        w.step(dt, in);
        assertNull(w.combat);
        assertEquals(6, s.minerals);
    }

    @Test
    void enemiesDoNotAttackDuringSpawnGrace() {
        GameWorld w = new GameWorld(b, map, armed());
        put(w, map.room("mine_battle_a"));
        PlayerInput in = idle(w);
        w.step(dt, in);
        Enemy e = w.combat.enemies.get(0);
        w.hero.x = e.x + 20;
        w.hero.y = e.y;
        run(w, in, 0.7f);
        for (Enemy x : w.combat.enemies) assertFalse(x.aggressive());
        run(w, in, 0.3f);
        assertTrue(w.combat.enemies.stream().anyMatch(Enemy::aggressive));
    }

    @Test
    void T07_oneHitPerAttackPerEnemy() {
        GameWorld w = new GameWorld(b, map, armed());
        put(w, map.room("mine_battle_a"));
        PlayerInput in = idle(w);
        w.step(dt, in);
        Enemy e = w.combat.enemies.get(0);
        w.combat.enemies.get(1).hp = 999;
        w.combat.enemies.get(1).x = e.x + 300;
        e.hp = 1000;
        e.grace = 999;
        w.hero.x = e.x;
        w.hero.y = e.y - 40;
        in.aimX = e.x;
        in.aimY = e.y + 100;
        in.attackHeld = true;
        // 판정 0.10초 동안 여러 간격에 걸쳐 겹쳐도 한 번만
        float cycle = b.weapon("sword").cycle();
        w.step(dt, in);
        AttackInstance atk = w.hero.attack;
        assertNotNull(atk);
        in.attackHeld = false;
        run(w, in, cycle);
        assertEquals(1000 - 12, e.hp);
    }

    @Test
    void T08_invulnerabilityBlocksExtraHits() {
        GameWorld w = new GameWorld(b, map, armed());
        put(w, map.room("mine_battle_a"));
        PlayerInput in = idle(w);
        w.step(dt, in);
        w.damageHero(10, w.hero.x + 10, w.hero.y);
        float x = w.hero.x;
        w.damageHero(10, w.hero.x + 10, w.hero.y);
        w.damageHero(10, w.hero.x - 10, w.hero.y);
        assertEquals(90, w.hero.hp);
        run(w, in, 0.2f);
        float moved = Math.abs(w.hero.x - x);
        assertTrue(moved <= b.hero.knockbackDistance + 0.5f, "밀림 최대 20px: " + moved);
    }

    @Test
    void T09_simultaneousDeathTakesPriority() {
        GameState s = armed();
        GameWorld w = new GameWorld(b, map, s);
        put(w, map.room("mine_battle_a"));
        PlayerInput in = idle(w);
        w.step(dt, in);
        Enemy last = w.combat.enemies.get(0);
        w.combat.enemies.remove(1);
        last.grace = 0;
        last.hp = 1;
        w.hero.hp = 1;
        // 적의 공격이 이번 간격에 용사에게 맞도록 직접 배치
        last.x = w.hero.x + 20;
        last.y = w.hero.y;
        last.enter(Enemy.State.ATTACK);
        last.attackId = "melee";
        last.activeTime = 1f;
        last.shape = HitShape.circle(w.hero.hurtX(), w.hero.hurtY(), 30);
        // 용사의 공격도 같은 간격에 판정 시작
        w.hero.attack = new AttackInstance(AttackInstance.Kind.NORMAL, 0, 0.0001f, 0.1f, 0.3f, 12, 64, 64);
        w.hero.attack.t = 0.0001f;
        in.aimX = last.x;
        in.aimY = w.hero.hurtY();
        w.step(dt, in);
        assertTrue(w.hero.dead);
        assertFalse(s.isRoomCleared("mine_battle_a"), "사망 우선: 클리어 없음");
        assertEquals(0, s.minerals);
        run(w, in, b.hero.deathTime + 0.05f);
        assertFalse(w.hero.dead);
        assertNull(w.combat, "실패한 방 초기화");
        assertEquals(100, w.hero.hp);
        Vector2 sp = map.point("camp_spawn");
        assertEquals(sp.x, w.hero.x);
        assertEquals(sp.y, w.hero.y);
        assertTrue(s.owns("sword"));
    }

    @Test
    void T11_bedDeathReturnRules() {
        GameState s = armed();
        GameWorld w = new GameWorld(b, map, s);
        put(w, map.room("mine_ore"));
        PlayerInput in = idle(w);
        w.step(dt, in);
        MineRoomState m = s.mineRoom("mine_ore");
        m.veins.get(0).mined = true;
        int cycle = s.miningCycle;
        // 귀환: 위치만, 광맥 유지, HP 유지
        w.hero.hp = 50;
        assertTrue(w.returnToCamp());
        assertEquals(50, w.hero.hp);
        assertSame(m, s.mineRoom("mine_ore"));
        assertEquals(cycle, s.miningCycle);
        // 사망: 광맥 재생 없음
        w.hero.hp = 1;
        w.damageHero(10, w.hero.x, w.hero.y);
        w.step(dt, in);
        run(w, in, 1.1f);
        assertEquals(100, w.hero.hp);
        assertTrue(s.mineRoom("mine_ore").veins.get(0).mined);
        // 침대: 새 회차
        w.hero.hp = 40;
        w.useBed();
        assertEquals(100, w.hero.hp);
        assertEquals(cycle + 1, s.miningCycle);
        assertNull(s.mineRoom("mine_ore"));
    }

    @Test
    void T16_shockwaveStopsAtWalls() {
        GameState s = armed();
        Progress.onBossDefeated(b, s, "boss_mine");
        GameWorld w = new GameWorld(b, map, s);
        RoomDef a = map.room("mine_battle_a");
        put(w, a);
        PlayerInput in = idle(w);
        w.step(dt, in);
        // 방 왼쪽 벽 가까이에서 왼쪽으로 충격파
        w.hero.x = a.bounds.x + 40;
        w.hero.y = a.centerY();
        Enemy e = w.combat.enemies.get(0);
        e.hp = 999;
        e.grace = 999;
        e.x = a.bounds.x - 100; // 벽 너머 (방 밖)
        e.y = a.centerY();
        w.combat.enemies.get(1).hp = 999;
        w.combat.enemies.get(1).grace = 999;
        in.aimX = w.hero.x - 200;
        in.aimY = w.hero.hurtY();
        in.shockwavePressed = true;
        w.step(dt, in);
        in.clearEdges();
        assertEquals(AttackInstance.Kind.SHOCKWAVE, w.hero.attack.kind);
        run(w, in, 0.4f);
        assertEquals(999, e.hp);
        assertTrue(w.hero.attack == null || w.hero.attack.effectiveLength <= 48f);
        assertTrue(w.hero.shockCooldown > 5f);
    }

    @Test
    void shockwaveLockedBeforeFirstBoss() {
        GameWorld w = new GameWorld(b, map, armed());
        PlayerInput in = idle(w);
        in.shockwavePressed = true;
        w.step(dt, in);
        assertNull(w.hero.attack);
    }

    @Test
    void T20_frameRateIndependence() {
        float[] distances = new float[3];
        int[] attacks = new int[3];
        int[] fps = {30, 60, 120};
        for (int k = 0; k < 3; k++) {
            GameState s = armed();
            GameWorld w = new GameWorld(b, map, s);
            put(w, map.room("mine_ore"));
            PlayerInput in = idle(w);
            w.step(dt, in);
            float x0 = w.hero.x;
            in.moveY = 0;
            in.attackHeld = true;
            FixedStepper st = new FixedStepper(GameWorld.STEP, 0.25f);
            AttackInstance last = null;
            float travelled = 0;
            for (int f = 0; f < fps[k] * 10; f++) {
                int n = st.advance(1f / fps[k]);
                for (int i = 0; i < n; i++) {
                    in.moveX = ((int) (w.time / 2f)) % 2 == 0 ? 1 : -1; // 왕복
                    float px = w.hero.x;
                    w.step(GameWorld.STEP, in);
                    travelled += Math.abs(w.hero.x - px);
                    if (w.hero.attack != null && w.hero.attack != last) {
                        attacks[k]++;
                        last = w.hero.attack;
                    }
                }
            }
            distances[k] = travelled;
        }
        for (int k = 1; k < 3; k++) {
            assertEquals(distances[0], distances[k], distances[0] * 0.01f, "이동 거리");
            assertTrue(Math.abs(attacks[0] - attacks[k]) <= 1, "공격 횟수");
        }
    }

    @Test
    void T24_shortcutBothWaysAndSpawnStaysInCamp() {
        GameState s = armed();
        GameWorld w = new GameWorld(b, map, s);
        PlayerInput in = idle(w);
        put(w, map.room("mine_ante"));
        w.step(dt, in);
        assertTrue(s.hasShortcut("mine"));
        assertTrue(w.saveRequested);
        // 귀환문으로 캠프 쪽 지름길 도착
        MapObject ret = map.interact("return_mine");
        w.hero.x = ret.x();
        w.hero.y = ret.bounds.y - 4;
        w.step(dt, in);
        assertNotNull(w.focus);
        assertEquals(GameWorld.FocusKind.RETURN, w.focus.kind);
        in.interactPressed = true;
        w.step(dt, in);
        in.clearEdges();
        assertEquals("camp", w.room.id);
        // 캠프 → 보스 앞
        MapObject sc = map.interact("shortcut_mine");
        w.hero.x = sc.x() + 24;
        w.hero.y = sc.y();
        w.step(dt, in);
        assertEquals(GameWorld.FocusKind.SHORTCUT, w.focus.kind);
        in.interactPressed = true;
        w.step(dt, in);
        in.clearEdges();
        assertEquals("mine_ante", w.room.id);
        assertEquals(map.point("camp_spawn"), w.spawnPoint());
    }

    @Test
    void bossEntryHealsAndKillGrantsRewardAndUnlock() {
        GameState s = armed();
        GameWorld w = new GameWorld(b, map, s);
        PlayerInput in = idle(w);
        w.hero.hp = 30;
        put(w, map.room("mine_boss"));
        w.step(dt, in);
        assertNotNull(w.combat);
        assertTrue(w.combat.boss);
        assertEquals(100, w.hero.hp);
        Enemy boss = w.combat.bossEnemy();
        assertEquals(650, boss.hp);
        run(w, in, 0.5f);
        assertEquals(Enemy.State.IDLE, boss.state, "준비 시간 동안 대기");
        boss.hp = 0;
        w.events.clear();
        run(w, in, 1.5f);
        assertTrue(s.isBossDefeated("boss_mine"));
        assertEquals(12, s.minerals);
        assertTrue(w.events.stream().anyMatch(e -> e.type == WorldEvent.Type.AWAKEN_GUIDE));
        assertFalse(w.isDoorLocked(map.door("fortress_gate")));
        assertTrue(Progress.isAwakeningUnlocked(b, s));
    }

    @Test
    void bossPatternsCycleWithoutError() {
        for (String zone : new String[]{"mine", "fortress", "temple"}) {
            GameState s = armed();
            s.defeatedBosses.clear();
            GameWorld w = new GameWorld(b, map, s);
            PlayerInput in = idle(w);
            put(w, map.room(zone + "_boss"));
            w.step(dt, in);
            Enemy boss = w.combat.bossEnemy();
            boss.hp = boss.maxHp / 2 - 1; // 신전은 강화 패턴 사용
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < 60 * 40; i++) {
                // 용사가 보스 주변을 돌며 버틴다
                w.hero.hp = 100;
                in.moveX = (float) Math.cos(i / 40.0);
                in.moveY = (float) Math.sin(i / 40.0);
                w.step(dt, in);
                if (w.combat == null) break;
                if (boss.state == Enemy.State.TELEGRAPH) seen.add(boss.attackId);
            }
            assertTrue(seen.size() >= 2, zone + " 패턴: " + seen);
        }
    }
}
