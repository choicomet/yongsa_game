package com.yongsa;

import com.yongsa.combat.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 마왕 2페이즈: 돌진·낙뢰를 빼고 삼키기, 마력 흡수, 최후의 발악. */
class Mao2PhaseTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    private GameSession demonFight(GameState s) {
        s.equippedUid = s.newWeapon("holy_awakened").uid;
        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) s.bossesDefeated.add(id);
        s.unsealed = true;
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        g.dropToAbyss();
        Controls up = new Controls();
        up.moveY = 1;
        for (int i = 0; i < 600 && !g.fighting(); i++) {
            g.update(1 / 60f, up);
            g.pollEvents();
        }
        assertTrue(g.fighting());
        return g;
    }

    /** 2페이즈 마왕을 방 한가운데 세우고 다음 패턴을 하나로 정한다. 용사는 dx 만큼 왼쪽. */
    private Monster phaseTwo(GameSession g, String pattern, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.phase2 = true;
        m.enraged = true;
        m.def.phase2Patterns = new ArrayList<>(List.of(pattern));
        m.lastPattern = null;
        Room r = null;
        for (Room o : g.map.rooms) if (o.type == RoomType.FINAL) r = o;
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        g.hero.x = m.x - dx;
        g.hero.y = m.y;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    private void hit(GameSession g, Monster m) {
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        m.enter(Monster.State.RECOVER, 5f);
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        Controls atk = new Controls();
        atk.attack = true;
        run(g, atk, 2 / 60f);
        run(g, idle, 1 / 60f);
    }

    @Test
    void phaseTwoPatternList() {
        List<String> p2 = b.finalBoss.phase2Patterns;
        assertFalse(p2.contains("rush"), "연속 돌진은 뺐다");
        assertFalse(p2.contains("lightning"), "낙뢰는 뺐다");
        assertTrue(p2.contains("devour"));
        assertTrue(p2.contains("drain"));
        assertFalse(b.finalBoss.patterns.contains("rush"));
        assertFalse(b.finalBoss.patterns.contains("lightning"));
    }

    @Test
    void devourPullsTheHeroIn() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = phaseTwo(g, "devour", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.CHANNEL, m.state);
        assertEquals("devour", m.channelKind);
        float d0 = Math.abs(m.x - g.hero.x);
        g.hero.invuln = 99f;
        run(g, idle, 0.6f);
        assertTrue(Math.abs(m.x - g.hero.x) < d0 - b.tileSize, "가만히 있으면 끌려간다");
    }

    @Test
    void reachingTheMawHurtsBadly() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = phaseTwo(g, "devour", 3 * b.tileSize);
        s.hp = 9999;
        run(g, idle, b.monster.devourTime);
        assertTrue(s.hp < 9999, "아가리에 닿으면 맞는다");
        assertNotEquals(Monster.State.CHANNEL, m.state, "삼키면 패턴이 끝난다");
    }

    @Test
    void walkingAwayEscapesTheDevour() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = phaseTwo(g, "devour", 4 * b.tileSize);
        run(g, idle, 2 / 60f);
        Controls away = new Controls();
        away.moveX = -1;
        s.hp = 9999;
        run(g, away, b.monster.devourTime / b.monster.phase2Speed + 0.2f);
        assertEquals(9999, s.hp, "바깥으로 걸으면 버틸 수 있다");
        assertNotEquals(Monster.State.CHANNEL, m.state);
        assertTrue(g.hazards.stream().noneMatch(h -> "maw".equals(h.style)), "끝나면 아가리 표시가 사라진다");
    }

    @Test
    void drainClonesHealTheDemonUntilDestroyed() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = phaseTwo(g, "drain", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        List<Monster> clones = new ArrayList<>();
        for (Monster o : g.monsters) if (!o.dead && "totem".equals(o.ai())) clones.add(o);
        assertEquals(b.monster.drainCount, clones.size());
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
        g.hero.invuln = 99f;
        run(g, idle, b.monster.spawnSeconds * 1.5f);
        m.hp = m.maxHp / 2;
        int hp = m.hp;
        float cx = clones.get(0).x, cy = clones.get(0).y;
        run(g, idle, 1f);
        assertTrue(m.hp > hp, "분신이 살아 있으면 회복한다");
        assertEquals(cx, clones.get(0).x, 0.5f, "분신은 움직이지 않는다");
        assertEquals(cy, clones.get(0).y, 0.5f);
        for (Monster c : clones) c.dead = true;
        hp = m.hp;
        run(g, idle, 1f);
        assertEquals(hp, m.hp, "분신을 부수면 회복이 멈춘다");
    }

    @Test
    void lastStandShortensThePatternGap() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = phaseTwo(g, "tentacle", 5 * b.tileSize);
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
        m.hp = Math.round(m.maxHp * b.monster.lastStandHpRatio) + 1;
        g.hero.invuln = 99f;
        hit(g, m);
        assertTrue(m.lastStand, "체력 20% 아래에서 최후의 발악");
        assertTrue(m.patternCd <= 0.6f);
        // 다음 패턴부터는 간격이 짧다
        m.x = g.hero.x + 5 * b.tileSize;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        run(g, idle, 2 / 60f);
        assertEquals(b.monster.bossPatternGap * b.monster.lastStandGapMultiplier, m.patternCd, 0.1f);
    }

    @Test
    void notInPhaseOne() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.enter(Monster.State.RECOVER, 99f);
        m.hp = 10;
        g.hero.invuln = 99f;
        hit(g, m);
        assertFalse(m.lastStand, "1페이즈에서는 발악하지 않는다");
    }
}
