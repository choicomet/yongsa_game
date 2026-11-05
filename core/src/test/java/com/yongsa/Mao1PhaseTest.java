package com.yongsa;

import com.yongsa.combat.Hazard;
import com.yongsa.combat.Monster;
import com.yongsa.combat.Projectile;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** 마왕 1페이즈: 리치 패턴(뼈 가시, 해골병 소환, 늪, 실명)과 어둠 구체, 마왕의 낙인, 어둠 기둥. */
class Mao1PhaseTest {
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

    private Monster forcePattern(GameSession g, String pattern, float dx) {
        Monster m = g.activeBoss();
        run(g, idle, 1.5f);
        m.def.patterns = new ArrayList<>(List.of(pattern));
        m.lastPattern = null;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.enter(Monster.State.CHASE, 0);
        m.patternCd = 0;
        return m;
    }

    /** 패턴을 쓰고 나면 보스를 멈춰 둔다 (다음 패턴이 섞이지 않게). */
    private void hold(Monster m) {
        m.enter(Monster.State.RECOVER, 99f);
        m.patternCd = 99f;
    }

    @Test
    void phaseOnePatternList() {
        Balance.MonsterDef d = b.finalBoss;
        assertFalse(d.patterns.contains("combo"), "콤보는 뺐다");
        for (String p : new String[]{"bonespike", "summon", "swamp", "darkorb", "brand", "pillars"}) {
            assertTrue(d.patterns.contains(p), p);
        }
        assertEquals("RIGHT", d.summonFrom, "해골병을 부른다");
        assertTrue(d.summonCount > 0);
    }

    @Test
    void boneSpikesRiseAndBlind() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        forcePattern(g, "bonespike", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        long spikes = g.hazards.stream().filter(h -> "bone".equals(h.style)).count();
        assertEquals(b.monster.spikeCount, spikes, "바닥 다섯 곳");
        assertTrue(g.blindT > 0, "실명");
    }

    @Test
    void summonsSkeletonSoldiersAndBlinds() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster boss = forcePattern(g, "summon", 5 * b.tileSize);
        // 실명은 뼈 가시를 가진 보스만 건다: 목록에 뼈 가시를 두되 이번에는 소환을 고르게 한다
        boss.def.patterns = new ArrayList<>(List.of("summon", "bonespike"));
        boss.lastPattern = "bonespike";
        run(g, idle, 2 / 60f);
        long skeletons = g.monsters.stream().filter(o -> !o.dead && !o.boss
                && o.def.sprite.equals(b.dungeon("RIGHT").mob.sprite)).count();
        assertEquals(boss.def.summonCount, skeletons);
        assertTrue(g.blindT > 0, "소환할 때도 캄캄해진다");
    }

    @Test
    void blindDoesNotRepeatWithinCooldown() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "bonespike", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        assertTrue(g.blindT > 0);
        run(g, idle, b.monster.blindSeconds + 0.1f);
        assertTrue(g.blindT <= 0);
        // 쿨타임(9초) 안에 다시 써도 실명은 걸리지 않는다
        m.enter(Monster.State.CHASE, 0);
        m.lastPattern = null;
        m.patternCd = 0;
        run(g, idle, 2 / 60f);
        assertTrue(g.blindT <= 0);
    }

    @Test
    void swampSpreadsThreePoolsThenBoneSpears() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        forcePattern(g, "swamp", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        List<Hazard> pools = new ArrayList<>();
        for (Hazard h : g.hazards) if (h.kind == Hazard.Kind.POOL && "swamp".equals(h.style)) pools.add(h);
        assertEquals(b.monster.poolCount, pools.size());
        assertTrue(pools.stream().anyMatch(h -> h.x == g.hero.x && h.y == g.hero.y), "하나는 용사 발밑");
        g.hero.invuln = 99f;
        run(g, idle, b.monster.spearDelay + 0.05f);
        long spears = g.hazards.stream().filter(h -> "bonespear".equals(h.style)).count();
        assertEquals(b.monster.spearCount, spears, "뼈창 세 개");
    }

    @Test
    void darkOrbBurstsIntoEightShots() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "darkorb", 6 * b.tileSize);
        g.hero.invuln = 99f;
        run(g, idle, 2 / 60f);
        assertEquals(Monster.State.CAST_TELEGRAPH, m.state);
        run(g, idle, b.monster.volleyTelegraph);
        hold(m);
        Projectile orb = null;
        for (Projectile p : g.projectiles) if (p.burst > 0) orb = p;
        assertNotNull(orb, "구체를 쏜다");
        // 용사를 비켜 세워 구체가 제 시간에 터지게 한다
        g.hero.y += 4 * b.tileSize;
        g.projectiles.removeIf(p -> p.burst == 0);
        run(g, idle, b.monster.orbFuse + 0.05f);
        long shots = g.projectiles.stream().filter(p -> p.burst == 0).count();
        assertTrue(shots >= b.monster.orbBurst - 1, "터지며 사방으로 탄: " + shots);
    }

    @Test
    void brandFollowsThenLocksAndExplodes() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "brand", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        hold(m);
        Hazard brand = null;
        for (Hazard h : g.hazards) if ("brand".equals(h.style)) brand = h;
        assertNotNull(brand);
        Controls walk = new Controls();
        walk.moveX = -1;
        run(g, walk, 0.5f);
        assertEquals(g.hero.x, brand.x, 1f, "낙인이 용사를 따라다닌다");
        run(g, idle, b.monster.brandFollow - 0.5f + 0.03f);
        float lx = brand.x;
        s.hp = 999;
        Controls away = new Controls();
        away.moveX = 1;
        run(g, away, b.monster.brandLock + 0.2f);
        assertEquals(lx, brand.x, 0.01f, "멈춘 뒤에는 따라오지 않는다");
        assertEquals(999, s.hp, "멈춘 자리를 벗어나면 안 맞는다");
    }

    @Test
    void darkPillarsLinesBurstOneAfterAnother() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "pillars", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        hold(m);
        List<Hazard> lines = new ArrayList<>();
        for (Hazard h : g.hazards) if ("darkline".equals(h.style)) lines.add(h);
        assertEquals(6, lines.size(), "변 넷 + 대각선 둘");
        Set<Integer> times = new HashSet<>();
        for (Hazard h : lines) times.add(Math.round(h.delay * 100));
        assertEquals(6, times.size(), "선이 하나씩 차례로 터진다");
        assertTrue(lines.stream().anyMatch(h -> h.covers(g.hero.x, g.hero.y, 0)), "대각선이 가운데를 지난다");
        s.hp = 999;
        run(g, idle, b.monster.pillarWarning + b.monster.pillarGap * 5 + 0.1f);
        assertTrue(s.hp < 999, "가만히 있으면 맞는다");
    }

    @Test
    void minionsCrumbleWhenTheDemonLordFalls() {
        GameState s = NewGame.create(b);
        GameSession g = demonFight(s);
        Monster m = forcePattern(g, "summon", 5 * b.tileSize);
        run(g, idle, 2 / 60f);
        assertTrue(g.monsters.stream().anyMatch(o -> !o.boss && !o.dead));
        // 2페이즈에서 마지막 일격
        m.phase2 = true;
        m.hp = 1;
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        m.enter(Monster.State.RECOVER, 5f);
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        g.hero.invuln = 99f;
        Controls atk = new Controls();
        atk.attack = true;
        run(g, atk, 2 / 60f);
        assertTrue(m.dead);
        assertTrue(g.monsters.stream().noneMatch(o -> !o.dead), "부하도 함께 무너진다");
    }
}
