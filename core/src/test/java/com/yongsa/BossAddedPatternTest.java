package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.enemy.Hazard;
import com.yongsa.enemy.Monster;
import com.yongsa.map.Room;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-11-27 선우: 보스마다 하나씩 더한 패턴 (기존 동작 그림을 그대로 쓴다). */
class BossAddedPatternTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private GameSession fightIn(String roomId, GameState s) {
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room(roomId));
        assertTrue(g.fighting());
        return g;
    }

    private GameState hero() {
        GameState s = NewGame.create(b);
        s.equippedUid = s.newWeapon("skyfall").uid;
        return s;
    }

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    /** 보스를 방 한가운데, 용사를 dx 만큼 왼쪽에 세우고 다음 패턴을 하나로 정한다. */
    private Monster forcePattern(GameSession g, String roomId, String pattern, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        for (Monster o : g.monsters) if (!o.boss) o.dead = true;
        m.def.patterns = new ArrayList<>(List.of(pattern));
        m.def.phase2Patterns = new ArrayList<>(List.of(pattern));
        m.lastPattern = null;
        Room r = g.map.room(roomId);
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        g.hero.x = m.x - dx;
        g.hero.y = m.y;
        g.hazards.clear();
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    // ---- 골렘: 주먹 날리기 ----

    @Test
    void golemPunchSendsAStraightShockwaveTowardTheHero() {
        GameState s = hero();
        GameSession g = fightIn("L6", s);
        Monster m = forcePattern(g, "L6", "punch", 4 * b.tileSize);
        List<GameEvent> ev = run(g, idle, 2 / 60f);
        assertEquals(Monster.State.PUNCH_TELEGRAPH, m.state, "주먹을 뒤로 당긴다 (기본 공격 줄 앞 4칸)");
        assertTrue(m.warning(), "머리 위에 ! 가 뜬다");
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.TOAST && e.text.contains("주먹")));

        List<Hazard> wave = new ArrayList<>();
        for (Hazard h : g.hazards) if ("punch".equals(h.style)) wave.add(h);
        assertFalse(wave.isEmpty());
        assertTrue(wave.size() <= b.monster.punchSegments);
        for (Hazard h : wave) {
            assertEquals(Hazard.Kind.LINE, h.kind);
            assertTrue(h.x2 < h.x, "용사가 있는 왼쪽으로 뻗는다");
            assertEquals(m.y, h.y, 0.5f);
        }
        for (int i = 1; i < wave.size(); i++) assertTrue(wave.get(i).delay > wave.get(i - 1).delay, "앞에서부터 차례로 터진다");
        assertTrue(wave.stream().anyMatch(h -> h.covers(g.hero.x, g.hero.y, 0)), "용사 자리를 지난다");

        // 가만히 있으면 맞는다
        m.patternCd = 99;
        s.hp = 999;
        g.hero.invuln = 0;
        run(g, idle, b.monster.punchWindup + b.monster.punchStep * b.monster.punchSegments + 0.1f);
        assertTrue(s.hp < 999, "충격파 줄에 서 있으면 맞는다");
        assertEquals(Monster.State.RECOVER, m.state, "주먹을 뻗은 뒤 숨을 고른다 (기본 공격 줄 뒤 4칸)");
    }

    @Test
    void golemPunchMissesIfTheHeroStepsAside() {
        GameState s = hero();
        GameSession g = fightIn("L6", s);
        Monster m = forcePattern(g, "L6", "punch", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        m.patternCd = 99;
        s.hp = 999;
        g.hero.invuln = 0;
        Controls side = new Controls();
        side.moveY = 1;
        run(g, side, b.monster.punchWindup + b.monster.punchStep * b.monster.punchSegments + 0.2f);
        assertEquals(999, s.hp, "옆으로 비키면 안 맞는다");
    }

    @Test
    void golemDoesNotPunchIntoAWallRightInFront() {
        GameState s = hero();
        GameSession g = fightIn("L6", s);
        Monster m = forcePattern(g, "L6", "punch", 4 * b.tileSize);
        Room r = g.map.room("L6");
        // 골렘을 왼쪽 벽에 붙이고 용사를 벽 너머 쪽(왼쪽)에 둔다
        m.x = (r.x0 + 1.5f) * b.tileSize;
        while (!g.map.solid((int) ((m.x - m.hurtRadius) / b.tileSize), (int) (m.y / b.tileSize)) && m.x > 0) m.x -= b.tileSize / 4f;
        g.hero.x = m.x - 3 * b.tileSize;
        run(g, idle, 2 / 60f);
        for (Hazard h : g.hazards) {
            if (!"punch".equals(h.style)) continue;
            assertFalse(g.map.solid((int) (h.x2 / b.tileSize), (int) (h.y2 / b.tileSize)), "벽 안으로는 뻗지 않는다");
        }
    }

    @Test
    void golemHasPunchInItsPatterns() {
        assertTrue(b.dungeon("LEFT").boss.patterns.contains("punch"));
    }

    // ---- 혈월의 기사: 방패 반격 ----

    /** 용사 바로 오른쪽에 있는 보스를 한 번 벤다 (상태는 그대로 둔다). */
    private List<GameEvent> swingAt(GameSession g, Monster m) {
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        Controls atk = new Controls();
        atk.attack = true;
        List<GameEvent> ev = run(g, atk, 2 / 60f);
        ev.addAll(run(g, idle, 1 / 60f));
        return ev;
    }

    @Test
    void knightBlocksAndCountersWhenHitWhileCrouching() {
        GameState s = hero();
        GameSession g = fightIn("R6", s);
        Monster m = forcePattern(g, "R6", "counter", 2 * b.tileSize);
        List<GameEvent> ev = run(g, idle, 2 / 60f);
        assertEquals(Monster.State.GUARD, m.state, "방패 뒤로 웅크린다");
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.TOAST && e.text.contains("반격")));
        m.patternCd = 99;

        int hp = m.hp;
        ev = swingAt(g, m);
        assertEquals(hp, m.hp, "웅크린 동안 맞으면 피해가 없다");
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.FLOAT_TEXT && "막았다!".equals(e.text)));
        assertEquals(Monster.State.RECOVER, m.state, "막자마자 반격한다 (방패 불꽃 → 베기)");
        assertTrue(g.hazards.stream().anyMatch(h -> "counter".equals(h.style) && h.covers(g.hero.x, g.hero.y, 0)),
                "반격이 바로 앞의 용사를 노린다");

        s.hp = 999;
        g.hero.invuln = 0;
        run(g, idle, b.monster.counterWarning + 0.1f);
        assertTrue(s.hp < 999, "가만히 있으면 반격에 맞는다");
    }

    @Test
    void knightStandsUpWithoutCounteringIfLeftAlone() {
        GameState s = hero();
        GameSession g = fightIn("R6", s);
        Monster m = forcePattern(g, "R6", "counter", 3 * b.tileSize);
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.GUARD, m.state);
        m.patternCd = 99;
        m.attackCd = 99;
        s.hp = 999;
        run(g, idle, b.monster.counterGuard + 0.1f);
        assertNotEquals(Monster.State.GUARD, m.state, "기다리면 일어선다");
        assertTrue(g.hazards.stream().noneMatch(h -> "counter".equals(h.style)), "안 때리면 반격도 없다");
        assertEquals(999, s.hp);

        // 일어선 뒤에는 평소처럼 맞는다
        int hp = m.hp;
        m.enter(Monster.State.RECOVER, 5f);
        swingAt(g, m);
        assertTrue(m.hp < hp);
    }

    @Test
    void knightHasCounterInItsPatterns() {
        assertTrue(b.dungeon("RIGHT").boss.patterns.contains("counter"));
    }
}
