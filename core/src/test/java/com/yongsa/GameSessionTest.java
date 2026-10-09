package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.world.Door;
import com.yongsa.world.Room;
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

    /** 왼쪽 첫 방의 오른쪽 문(캠프 쪽) 안쪽에 세운다. */
    private void standAtL1Entrance(GameSession g) {
        Room l1 = g.map.room("L1");
        Door d = null;
        for (Door x : l1.doors) if (x.side == Door.Side.RIGHT) d = x;
        g.hero.x = (d.outsideX + 0.5f) * b.tileSize;
        g.hero.y = (d.outsideY + 0.5f) * b.tileSize;
    }

    @Test
    void enteringMobRoomLocksDoorsAndClearingOpensThem() {
        GameState s = NewGame.create(b);
        GameSession g = session(s);
        standAtL1Entrance(g);
        Controls left = new Controls();
        left.moveX = -1;
        List<GameEvent> ev = run(g, left, 1.0f);
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
        standAtL1Entrance(g);
        float outX = g.hero.x;
        Controls left = new Controls();
        left.moveX = -1;
        run(g, left, 1.0f);
        assertTrue(g.fighting());
        g.prepareSave();
        assertEquals(outX, s.resumeX, 1e-3);
        assertFalse(s.room("L1").cleared);
    }

    @Test
    void bedResetsRoomsButNotDefeatedBoss() {
        GameState s = NewGame.create(b);
        s.room("L1").cleared = true;
        s.room("L5").cleared = true;
        s.bossesDefeated.add("LEFT");
        s.hp = 10;
        GameSession g = session(s);
        g.useBed();
        assertFalse(s.room("L1").cleared);
        assertTrue(s.room("L5").cleared, "잡은 보스는 다시 나오지 않는다");
        assertEquals(250, s.hp);
        assertTrue(has(g.pollEvents(), GameEvent.Type.AUTOSAVE));
    }

    @Test
    void firstOreRoomIsGeneratedOnEntryWithGuarantee() {
        GameState s = NewGame.create(b);
        s.room("L1").cleared = true;
        GameSession g = session(s);
        standAtL1Entrance(g);
        Controls left = new Controls();
        left.moveX = -1;
        assertNull(g.veinsOf(g.map.room("L2")));
        run(g, left, 10f); // L1 을 지나 L2 로
        assertNotNull(g.veinsOf(g.map.room("L2")));
        int total = g.veinsOf(g.map.room("L2")).stream().mapToInt(v -> v.amount).sum();
        assertTrue(total >= 8);
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
        GameSession g = session(s);
        Room boss = g.map.room("L5");
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
