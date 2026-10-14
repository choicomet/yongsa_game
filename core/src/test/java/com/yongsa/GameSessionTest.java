package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.world.Door;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GameSessionTest {
    private final Balance b = TestData.balance();

    private GameSession session(GameState s) {
        return new GameSession(b, TestData.map(), s, 42);
    }

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    private boolean has(List<GameEvent> events, GameEvent.Type type) {
        for (GameEvent e : events) if (e.type == type) return true;
        return false;
    }

    /** 캠프 왼쪽 문(왼쪽 던전 입구) 바로 안쪽에 세운다. */
    private Door standAtCampLeftDoor(GameSession g) {
        Door d = door(g.map.room("CAMP"), "LEFT");
        g.hero.x = d.arriveX;
        g.hero.y = d.arriveY;
        return d;
    }

    private static Door door(Room r, String name) {
        for (Door x : r.doors) if (x.name.equals(name)) return x;
        throw new AssertionError(r.id + "." + name);
    }

    @Test
    void walkingThroughADoorMovesToTheLinkedRoom() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        Door d = standAtCampLeftDoor(g);
        Controls left = new Controls();
        left.moveX = -1;
        for (int i = 0; i < 60 && !g.inDungeon(); i++) run(g, left, 1 / 60f);
        assertEquals("L1", g.heroRoom().id, "캠프 왼쪽 문을 지나면 왼쪽 던전 첫 방");
        Door to = d.partner();
        assertTrue(Math.hypot(g.hero.x - to.arriveX, g.hero.y - to.arriveY) < 0.5f * b.tileSize, "짝 문 앞에 선다");
        assertTrue(s.room("L1").visited);

        // 지연 모드(화면 연출)에서는 신호만 보내고 기다린다
        GameState s2 = NewGame.create(b);
        GameSession g2 = session(s2);
        g2.deferDoors = true;
        standAtCampLeftDoor(g2);
        List<GameEvent> ev = run(g2, left, 0.8f);
        assertTrue(has(ev, GameEvent.Type.DOOR_ENTER));
        assertTrue(g2.currentRoom().id.equals("CAMP"), "화면이 어두워질 때까지 아직 캠프");
        assertTrue(g2.doorPending());
        g2.passPendingDoor();
        run(g2, new Controls(), 1 / 60f);
        assertEquals("L1", g2.heroRoom().id);
    }

    @Test
    void closedGatesBlockTheWayUntilProgressOpensThem() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        Door right = door(g.map.room("CAMP"), "RIGHT");
        g.hero.x = right.arriveX;
        g.hero.y = right.arriveY;
        Controls go = new Controls();
        go.moveX = 1;
        run(g, go, 1.0f);
        assertTrue(g.inCamp(), "오른쪽 던전은 왼쪽 보스를 잡아야 열린다");

        // 캠프 아래 문은 마을로 나간다 (잠기지 않지만 용사가 처음엔 나가려 하지 않는다)
        Door down = door(g.map.room("CAMP"), "down");
        assertEquals("V", down.partner().room.id);
        assertTrue(g.map.free(down.footX, down.footY, g.hero.hw, g.hero.hh));
    }

    @Test
    void devWarpPutsHeroAtTheBossRoomEntrance() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        for (String id : new String[]{"LEFT", "RIGHT", "TOP", "FINAL"}) {
            assertTrue(g.devWarpToBoss(id), id);
            run(g, new Controls(), 1 / 60f);
            RoomType want = "FINAL".equals(id) ? RoomType.FINAL : RoomType.BOSS;
            assertEquals(want, g.heroRoom().type, id);
            assertFalse(g.fighting(), "입구에 서기만 해서는 싸움이 시작되지 않는다");
            if (!"FINAL".equals(id)) assertEquals(id, g.heroRoom().dungeonId);
        }
    }

    @Test
    void enteringMobRoomLocksDoorsAndClearingOpensThem() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        standAtCampLeftDoor(g);
        Controls left = new Controls();
        left.moveX = -1;
        List<GameEvent> ev = run(g, left, 1.6f);
        assertTrue(has(ev, GameEvent.Type.ROOM_LOCKED));
        assertTrue(g.fighting());
        assertEquals(b.dungeon("LEFT").mobsPerRoom, g.monsters.size());
        assertFalse(g.canExitDungeon(), "잠긴 방에서는 던전을 나갈 수 없다");

        g.debugKillAll();
        ev = run(g, new Controls(), 0.1f);
        assertTrue(has(ev, GameEvent.Type.ROOM_CLEARED));
        assertFalse(g.fighting());
        assertTrue(s.room("L1").cleared);
        assertTrue(g.canExitDungeon());
    }

    @Test
    void savingDuringFightResumesOutsideTheDoor() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        Door d = standAtCampLeftDoor(g);
        Controls left = new Controls();
        left.moveX = -1;
        run(g, left, 1.6f);
        assertTrue(g.fighting());
        g.prepareSave();
        assertEquals(d.arriveX, s.resumeX, 1e-3, "잠긴 방은 처음 상태로 돌아가고 들어오기 전 문 앞에서 이어 한다");
        assertEquals(d.arriveY, s.resumeY, 1e-3);
        assertFalse(s.room("L1").cleared);
    }

    @Test
    void bedResetsRoomsAndRespawnsBossUnlessChestIsUnopened() {
        GameState s = NewGame.create(b);
        s.room("L1").cleared = true;
        s.room("L6").cleared = true;
        s.room("R6").cleared = true;
        s.bossesDefeated.add("LEFT");
        s.bossesDefeated.add("RIGHT");
        s.chests.add("R6");
        s.hp = 10;
        GameSession g = session(s);
        g.useBed();
        assertFalse(s.room("L1").cleared);
        assertFalse(s.room("L6").cleared, "잡은 보스도 침대 뒤 다시 나온다 (선우 버전)");
        assertTrue(s.room("R6").cleared, "열지 않은 상자가 있는 보스방은 그대로");
        assertTrue(s.bossDefeated("LEFT"), "흡수한 힘은 그대로");
        assertEquals(450, s.hp);
        assertTrue(has(g.pollEvents(), GameEvent.Type.AUTOSAVE));
    }

    @Test
    void firstOreRoomIsGeneratedOnEntryWithGuarantee() {
        GameState s = NewGame.create(b);
        s.room("L1").cleared = true;
        GameSession g = session(s);
        Door west = door(g.map.room("L1"), "west");
        g.hero.x = west.arriveX;
        g.hero.y = west.arriveY;
        Controls left = new Controls();
        left.moveX = -1;
        assertNull(g.veinsOf(g.map.room("L2")));
        run(g, left, 1.5f); // L1 왼쪽 문을 지나 L2 로
        assertEquals("L2", g.heroRoom().id);
        assertNotNull(g.veinsOf(g.map.room("L2")));
        int total = g.veinsOf(g.map.room("L2")).stream().mapToInt(v -> v.amount).sum();
        assertTrue(total >= 8);
    }

    @Test
    void oldSaveFromBeforeTheRoomPaintingsResumesInCamp() {
        GameState s = NewGame.create(b);
        s.mapVersion = 0; // 그림 방 이전 저장
        s.resumeX = 30 * b.tileSize;
        s.resumeY = 40 * b.tileSize;
        s.room("L2").veins = new ArrayList<>(List.of(new com.yongsa.model.Vein(3, 3, com.yongsa.model.OreType.BLACKIRON, 1, 3)));
        GameSession g = session(s);
        assertTrue(g.inCamp(), "예전 위치는 새 맵에 맞지 않아 캠프에서 시작");
        assertNull(s.room("L2").veins, "예전 광맥 자리는 버린다");
        assertEquals(com.yongsa.world.WorldMap.VERSION, s.mapVersion);
    }

    @Test
    void campRegeneratesHp() {
        GameState s = NewGame.create(b);
        s.hp = 10;
        GameSession g = session(s);
        run(g, new Controls(), 2f);
        assertTrue(s.hp > 10);
    }

    @Test
    void rollMovesFasterThanWalkingAndGivesInvulnerability() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        float x0 = g.hero.x;
        Controls c = new Controls();
        c.moveX = -1;
        c.roll = true;
        g.update(1 / 60f, c);
        assertTrue(g.hero.rolling());
        assertTrue(g.hero.invuln > 0, "구르는 동안은 맞지 않는다");
        c.roll = false;
        run(g, c, b.attack.rollTime);
        float rolled = x0 - g.hero.x;
        float walked = b.hero.moveSpeedTiles * b.tileSize * b.attack.rollTime;
        assertTrue(rolled > walked * 1.5f, "구르기는 같은 시간 걷는 것보다 멀리 간다: " + rolled + " vs " + walked);
        c.roll = true;
        g.update(1 / 60f, c);
        assertFalse(g.hero.rolling(), "쿨타임 중에는 다시 구를 수 없다");
    }

    @Test
    void defeatedBossRoomHasReturnPortalToCamp() {
        GameState s = NewGame.create(b);
        s.bossesDefeated.add("LEFT");
        s.room("L6").cleared = true;
        GameSession g = session(s);
        Room boss = g.map.room("L6");
        assertTrue(g.hasPortal(boss));
        assertFalse(g.hasPortal(g.map.room("R6")));
        g.hero.x = g.portalX(boss);
        g.hero.y = g.portalY(boss);
        Controls c = new Controls();
        c.interact = true;
        List<GameEvent> ev = run(g, c, 1 / 60f);
        assertTrue(has(ev, GameEvent.Type.WARP));
        g.warpToCamp();
        assertTrue(g.inCamp());
    }
}
