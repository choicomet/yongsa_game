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
        s.ore[0] = 6;
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
        DoorDef d = TestData.doorOf(TestData.firstBattle());
        assertTrue(w.isDoorLocked(d));
        assertNotNull(w.ruleLockReason(d));
        assertTrue(w.isDoorLocked(TestData.gate("fortress")));
        assertEquals("폐광의 수호자 처치 후 개방", w.doorLockReason(TestData.gate("fortress")));
    }

    /** 바닥 타일을 따라 목표까지 걷는다 (테스트용 간단한 길찾기). */
    static void walkTo(GameWorld w, float tx, float ty, float maxSeconds) {
        DungeonMap m = w.map;
        int T = m.tileSize;
        int W = m.widthTiles;
        int start = (int) (w.hero.y / T) * W + (int) (w.hero.x / T);
        int goal = (int) (ty / T) * W + (int) (tx / T);
        int[] prev = new int[W * m.heightTiles];
        java.util.Arrays.fill(prev, -2);
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        q.add(start);
        prev[start] = -1;
        while (!q.isEmpty() && prev[goal] == -2) {
            int c = q.poll();
            int cx = c % W, cy = c / W;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = cx + d[0], ny = cy + d[1];
                int n = ny * W + nx;
                if (!m.isFloor(nx, ny) || prev[n] != -2) continue;
                if (m.overlapsStatic(new com.badlogic.gdx.math.Rectangle(nx * T + 4, ny * T + 8, T - 8, T - 16))) continue;
                prev[n] = c;
                q.add(n);
            }
        }
        assertNotEquals(-2, prev[goal], "목표까지 길이 없음");
        java.util.List<Integer> path = new java.util.ArrayList<>();
        for (int c = goal; c != -1; c = prev[c]) path.add(0, c);
        PlayerInput in = new PlayerInput();
        float t = 0;
        for (int c : path) {
            float px = (c % W + 0.5f) * T, py = (c / W + 0.5f) * T;
            while (Math.abs(w.hero.x - px) > 3 || Math.abs(w.hero.y - py) > 3) {
                in.moveX = Math.abs(w.hero.x - px) > 3 ? Math.signum(px - w.hero.x) : 0;
                in.moveY = Math.abs(w.hero.y - py) > 3 ? Math.signum(py - w.hero.y) : 0;
                in.aimX = w.hero.x;
                in.aimY = w.hero.y - 100;
                w.step(GameWorld.STEP, in);
                t += GameWorld.STEP;
                assertTrue(t < maxSeconds, "이동이 막힘");
            }
        }
    }

    @Test
    void walkFromCampToOreRoomWithoutWeapon() {
        GameWorld w = new GameWorld(b, map, TestData.newGame());
        RoomDef ore = TestData.oreRoom();
        walkTo(w, ore.centerX(), ore.centerY(), 30f);
        assertNotNull(w.room);
        assertEquals(ore.id, w.room.id);
        assertNotNull(w.state.mineRoom(ore.id));
        assertTrue(w.saveRequested);
    }

    @Test
    void miningRequiresHoldingStill() {
        GameState s = TestData.newGame();
        GameWorld w = new GameWorld(b, map, s);
        RoomDef ore = TestData.oreRoom();
        put(w, ore);
        PlayerInput in = idle(w);
        w.step(dt, in);
        MineRoomState m = s.mineRoom(TestData.oreRoom().id);
        VeinState v = m.veins.get(0);
        MapObject o = map.vein(v.candidateId);
        // 광맥 바로 옆(아래/위)에 선다
        w.hero.x = o.x();
        w.hero.y = o.y() + (o.y() > ore.centerY() ? -24 : 24);
        in.interactHeld = true;
        run(w, in, 0.5f);
        assertEquals(0, s.ore[0]);
        in.interactHeld = false; // 놓으면 취소
        w.step(dt, in);
        assertEquals(0f, w.miningProgress);
        in.interactHeld = true;
        run(w, in, 1.05f);
        assertEquals(b.zone("mine").veinYield, s.ore[0]);
        assertTrue(v.mined);
    }

    @Test
    void battleRoomLocksSpawnsAndClearsOnce() {
        GameState s = armed();
        GameWorld w = new GameWorld(b, map, s);
        RoomDef a = TestData.firstBattle();
        put(w, a);
        PlayerInput in = idle(w);
        w.step(dt, in);
        assertNotNull(w.combat);
        assertEquals(2, w.combat.enemies.size());
        assertTrue(w.isDoorLocked(TestData.doorOf(TestData.firstBattle())));
        assertFalse(w.canReturnToCamp());
        for (Enemy e : w.combat.enemies) e.hp = 0;
        w.step(dt, in);
        assertNull(w.combat);
        assertTrue(s.isRoomCleared(TestData.firstBattle().id));
        assertEquals(b.zone("mine").roomReward, s.ore[0]);
        assertFalse(w.isDoorLocked(TestData.doorOf(TestData.firstBattle())));
        // 다시 들어와도 적이 없다
        w.hero.x += 1;
        w.step(dt, in);
        assertNull(w.combat);
        assertEquals(b.zone("mine").roomReward, s.ore[0]);
    }

    @Test
    void enemiesDoNotAttackDuringSpawnGrace() {
        GameWorld w = new GameWorld(b, map, armed());
        put(w, TestData.firstBattle());
        PlayerInput in = idle(w);
        w.step(dt, in);
        Enemy e = w.combat.enemies.get(0);
        w.hero.x = e.x + 20;
        w.hero.y = e.y;
        run(w, in, b.enemyCombat.spawnGrace - 0.1f);
        for (Enemy x : w.combat.enemies) assertFalse(x.aggressive());
        run(w, in, 0.3f);
        assertTrue(w.combat.enemies.stream().anyMatch(Enemy::aggressive));
    }

    @Test
    void T07_oneHitPerAttackPerEnemy() {
        GameWorld w = new GameWorld(b, map, armed());
        put(w, TestData.firstBattle());
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
        assertEquals(1000 - b.weapon("sword").baseAttack, e.hp);
    }

    @Test
    void T08_invulnerabilityBlocksExtraHits() {
        GameWorld w = new GameWorld(b, map, armed());
        put(w, TestData.firstBattle());
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
        put(w, TestData.firstBattle());
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
        assertFalse(s.isRoomCleared(TestData.firstBattle().id), "사망 우선: 클리어 없음");
        assertEquals(0, s.ore[0]);
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
    void T11_returnAndDeathRules() {
        GameState s = armed();
        GameWorld w = new GameWorld(b, map, s);
        put(w, TestData.oreRoom());
        PlayerInput in = idle(w);
        w.step(dt, in);
        MineRoomState m = s.mineRoom(TestData.oreRoom().id);
        m.veins.get(0).mined = true;
        int cycle = s.miningCycle;
        // 귀환: 위치만, 광맥 유지, HP 유지
        w.hero.hp = 50;
        assertTrue(w.returnToCamp());
        assertEquals(50, w.hero.hp);
        assertSame(m, s.mineRoom(TestData.oreRoom().id));
        assertEquals(cycle, s.miningCycle);
        // 캠프에는 쉬는 곳도 대장간도 없다: 다시 일어나는 곳일 뿐이다
        assertNull(map.interact("bed"));
        assertNull(map.interact("forge"));
        run(w, in, 0.5f);
        assertEquals(50, w.hero.hp, "캠프에 있어도 회복되지 않는다");
        // 사망: 캠프에서 체력이 가득 찬 채 다시 일어나고, 광맥이 새로 자란다
        w.hero.hp = 1;
        w.damageHero(10, w.hero.x, w.hero.y);
        w.step(dt, in);
        run(w, in, 1.1f);
        assertEquals(100, w.hero.hp);
        assertEquals(cycle + 1, s.miningCycle);
        assertNull(s.mineRoom(TestData.oreRoom().id));
    }

    @Test
    void T16_shockwaveHitsEveryoneInTheRoomOnceAndPushesThemAway() {
        GameState s = armed();
        Progress.onBossDefeated(b, s, "boss_mine");
        GameWorld w = new GameWorld(b, map, s);
        RoomDef a = TestData.firstBattle();
        put(w, a);
        PlayerInput in = idle(w);
        w.step(dt, in);
        run(w, in, b.dungeon.spawnMarkTime + 0.05f);
        // 방 가운데에서 터뜨린다: 조준 방향과 거리에 상관없이 방 안의 모든 적이 한 번씩 맞는다
        w.hero.x = a.centerX();
        w.hero.y = a.centerY();
        Enemy near = w.combat.enemies.get(0), far = w.combat.enemies.get(1);
        for (Enemy e : w.combat.enemies) {
            e.hp = 999;
            e.grace = 999;
        }
        near.x = w.hero.x + 60;
        near.y = w.hero.y;
        far.x = a.bounds.x + 80; // 용사 뒤쪽 먼 구석
        far.y = a.bounds.y + 80;
        float nearX = near.x;
        w.combat.projectiles.add(new com.yongsa.combat.Projectile(w.hero.x + 150, w.hero.y + 150, 0f, 1f, 5));
        in.aimX = w.hero.x + 200;
        in.aimY = w.hero.hurtY();
        in.shockwavePressed = true;
        w.step(dt, in);
        in.clearEdges();
        assertEquals(AttackInstance.Kind.SHOCKWAVE, w.hero.attack.kind);
        run(w, in, b.shockwave.windup + 0.2f);
        assertTrue(near.x > nearX + b.shockwave.knockback * 0.6f, "용사에게서 멀어지는 쪽으로 밀려난다");
        run(w, in, 0.3f);
        int dmg = com.yongsa.model.DamageCalc.shockwaveDamage(b, s.equippedWeapon());
        assertEquals(999 - dmg, near.hp, "한 번만 맞는다");
        assertEquals(999 - dmg, far.hp, "등 뒤의 먼 적도 맞는다");
        assertTrue(w.combat.projectiles.isEmpty(), "날아오던 탄이 지워진다");
        assertTrue(w.hero.shockCooldown > 5f);
        // 보스: 골렘은 조금 밀리고, 마왕은 밀리지 않는다
        assertTrue(b.boss("boss_mine").shockPush > 0 && b.boss("boss_mine").shockPush < b.shockwave.knockback);
        assertEquals(0f, b.boss("boss_temple").shockPush);
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
            put(w, TestData.oreRoom());
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
    void bossKillHealsAndGrantsRewardAndUnlock() {
        GameState s = armed();
        GameWorld w = new GameWorld(b, map, s);
        PlayerInput in = idle(w);
        w.hero.hp = 30;
        put(w, map.room("mine_boss"));
        w.step(dt, in);
        assertNotNull(w.combat);
        assertTrue(w.combat.boss);
        assertEquals(30, w.hero.hp, "보스방에 들어간다고 회복되지 않는다");
        Enemy boss = w.combat.bossEnemy();
        assertEquals(b.boss("boss_mine").hp, boss.hp);
        run(w, in, 0.5f);
        assertEquals(Enemy.State.IDLE, boss.state, "준비 시간 동안 대기");
        boss.hp = 0;
        w.events.clear();
        run(w, in, 1.5f);
        assertTrue(s.isBossDefeated("boss_mine"));
        assertEquals(100, w.hero.hp, "보스를 쓰러뜨리면 체력이 모두 찬다");
        assertEquals(b.zone("mine").bossReward, s.ore[0]);
        assertTrue(w.events.stream().anyMatch(e -> e.type == WorldEvent.Type.AWAKEN_GUIDE));
        assertFalse(w.isDoorLocked(TestData.gate("fortress")));
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
            assertTrue(seen.size() >= 3, zone + " 패턴: " + seen + " (맴돌며 버텨도 여러 공격이 나와야 한다)");
        }
    }
}
