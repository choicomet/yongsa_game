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
        // 방 한가운데에 용사, 그 오른쪽에 골렘 (벽에 걸리지 않게)
        com.yongsa.world.Room r = g.map.room("L6");
        g.hero.x = (r.centerTileX() - 1.5f) * b.tileSize;
        g.hero.y = (r.centerTileY() + 0.5f) * b.tileSize;
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
        assertEquals(List.of("quake", "boulder", "pound"), golem.phase2Patterns);
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
    void poundSlamsThreeTimesTowardTheHero() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "pound", 6 * b.tileSize);
        run(g, idle, 2 / 60f);
        List<Hazard> slams = new ArrayList<>();
        for (Hazard h : g.hazards) if ("pound".equals(h.style)) slams.add(h);
        assertEquals(b.monster.poundCount, slams.size(), "쿵, 쿵, 쿵");
        for (int i = 1; i < slams.size(); i++) {
            Hazard prev = slams.get(i - 1), cur = slams.get(i);
            assertTrue(cur.delay > prev.delay, "차례로 내려찍는다");
            assertTrue(cur.x < prev.x, "용사 쪽으로 나아간다");
            assertTrue(cur.radius > prev.radius, "갈수록 넓다");
        }
        assertEquals(Monster.State.RECOVER, m.state);
        assertTrue(m.stateT > slams.get(slams.size() - 1).delay + 0.5f, "다 찍고 나서 오래 숨을 고른다 (반격 기회)");
    }

    @Test
    void eachPoundShakesTheScreen() {
        GameState s = hero();
        GameSession g = golemFight(s);
        forcePattern(g, "pound", 6 * b.tileSize);
        g.hero.invuln = 99f;
        List<GameEvent> ev = run(g, idle, b.monster.poundWarning + b.monster.poundGap * b.monster.poundCount + 0.1f);
        long shakes = ev.stream().filter(e -> e.type == GameEvent.Type.SLAM_FX).count();
        assertTrue(shakes >= b.monster.poundCount, "무거운 한 방마다 화면이 울린다: " + shakes);
    }

    @Test
    void steppingAsideAvoidsThePound() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "pound", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        m.patternCd = 99f;
        s.hp = 999;
        Controls side = new Controls();
        side.moveY = 1;
        run(g, side, 0.6f);
        run(g, idle, b.monster.poundWarning + b.monster.poundGap * b.monster.poundCount);
        assertEquals(999, s.hp, "옆으로 비키면 안 맞는다");
    }

    @Test
    void standingInLineGetsPounded() {
        GameState s = hero();
        GameSession g = golemFight(s);
        Monster m = forcePattern(g, "pound", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        m.patternCd = 99f;
        s.hp = 999;
        run(g, idle, b.monster.poundWarning + b.monster.poundGap * b.monster.poundCount);
        assertTrue(s.hp < 999);
    }

    @Test
    void golemNoLongerBurrows() {
        assertFalse(b.dungeon("LEFT").boss.phase2Patterns.contains("burrow"));
    }
}
