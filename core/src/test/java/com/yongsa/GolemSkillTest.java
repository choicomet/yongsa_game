package com.yongsa;

import com.yongsa.combat.Hazard;
import com.yongsa.combat.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 골렘 2페이즈 패턴: 지진 파동, 바위 던지기, 땅속 잠행. */
class GolemSkillTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private GameSession golemFight(GameState s) {
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room("L6"));
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

    private Monster forcePattern(GameSession g, String pattern, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.def.patterns = new ArrayList<>(List.of(pattern));
        m.def.phase2Patterns = new ArrayList<>(List.of(pattern));
        m.lastPattern = null;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    @Test
    void golemGetsThreeNewPatternsOnlyInPhaseTwo() {
        Balance.MonsterDef golem = b.dungeon("LEFT").boss;
        assertEquals(List.of("rush", "rockfall", "slam"), golem.patterns);
        assertEquals(List.of("quake", "boulder", "burrow"), golem.phase2Patterns);
    }

    @Test
    void quakeRingsBurstFromTheInsideOut() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "quake", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        List<Hazard> rings = new ArrayList<>();
        for (Hazard h : g.hazards) if ("quake".equals(h.style)) rings.add(h);
        assertEquals(b.monster.quakeWaves, rings.size());
        for (int i = 1; i < rings.size(); i++) {
            assertTrue(rings.get(i).delay > rings.get(i - 1).delay, "바깥 고리가 더 늦게 터진다");
            assertEquals(rings.get(i - 1).radius, rings.get(i).innerRadius, 0.01f);
        }
        // 고리는 가운데가 비어 있다
        Hazard outer = rings.get(rings.size() - 1);
        assertFalse(outer.covers(m.x, m.y, 0), "바깥 고리의 가운데는 안전하다");
        float mid = (outer.innerRadius + outer.radius) / 2f;
        assertTrue(outer.covers(m.x + mid, m.y, 0));
    }

    @Test
    void boulderShattersIntoShards() {
        GameState s = hero();
        GameSession g = golemFight(s);
        forcePattern(g, "boulder", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        Hazard rock = null;
        for (Hazard h : g.hazards) if ("boulder".equals(h.style)) rock = h;
        assertNotNull(rock, "용사 자리에 바위가 떨어진다");
        assertTrue(rock.covers(g.hero.x, g.hero.y, 0));
        g.activeBoss().enter(Monster.State.RECOVER, 99f);
        // 바위를 피해 옆으로 비켜서 있는다
        g.hero.invuln = 99f;
        g.projectiles.clear();
        run(g, idle, b.monster.boulderWarning + 0.05f);
        long shards = g.projectiles.stream().filter(p -> "golem".equals(p.source)).count();
        assertTrue(shards >= b.monster.shardCount - 1, "바위가 깨지며 파편이 튄다: " + shards);
    }

    @Test
    void burrowFollowsTheHeroThenErupts() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "burrow", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.BURROW, m.state);
        assertTrue(m.invulnerable(), "땅속에서는 맞지 않는다");
        Hazard mark = m.burrowMark;
        assertNotNull(mark);

        // 용사를 따라온다
        Controls walk = new Controls();
        walk.moveY = 1;
        run(g, walk, 0.5f);
        assertEquals(g.hero.x, mark.x, 1f);
        assertEquals(g.hero.y, mark.y, 1f);

        // 따라오기가 끝나면 멈추고, 걸어서 벗어나면 안 맞는다
        run(g, idle, b.monster.burrowFollow - 0.5f + 0.03f);
        float lockX = mark.x, lockY = mark.y;
        s.hp = 999;
        Controls away = new Controls();
        away.moveY = -1;
        run(g, away, b.monster.burrowLock + 0.2f);
        assertEquals(lockX, mark.x, 0.01f);
        assertEquals(lockY, mark.y, 0.01f);
        assertEquals(999, s.hp, "멈춘 자리를 벗어나면 안 맞는다");
        assertEquals(Monster.State.RECOVER, m.state, "솟구친 뒤 숨을 고른다");
        assertFalse(m.invulnerable());
    }

    @Test
    void standingStillWhileBurrowedGetsYouHit() {
        GameState s = hero();
        GameSession g = golemFight(s);
        forcePattern(g, "burrow", 5 * b.tileSize);
        s.hp = 999;
        run(g, idle, b.monster.burrowFollow + b.monster.burrowLock + 0.2f);
        assertTrue(s.hp < 999, "가만히 있으면 솟구치는 골렘에게 맞는다");
    }
}
