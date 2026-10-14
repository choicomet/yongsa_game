package com.yongsa;

import com.yongsa.combat.Hazard;
import com.yongsa.combat.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.world.Prop;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 팀원 버전에서 가져온 보스 패턴: 방패, 바닥 공격, 연속 돌진, 마왕 2페이즈, 캠프 문양에서 심연으로. */
class BossPatternTest {
    private final Balance b = TestData.balance();
    private final Controls idle = new Controls();

    private GameSession fightIn(String roomId, GameState s) {
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        TestData.walkIn(g, g.map.room(roomId));
        assertTrue(g.fighting());
        return g;
    }

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    /** 보스를 용사 바로 오른쪽에 세우고 한 번 벤다. */
    private List<GameEvent> hitBoss(GameSession g, Monster m) {
        m.x = g.hero.x + 40;
        m.y = g.hero.y;
        if (m.state != Monster.State.PHASE_SHIFT) m.enter(Monster.State.RECOVER, 5f);
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        g.hero.attackCd = 0;
        Controls atk = new Controls();
        atk.attack = true;
        List<GameEvent> ev = run(g, atk, 2 / 60f);
        ev.addAll(run(g, idle, 1 / 60f));
        return ev;
    }

    private GameState strongHero() {
        GameState s = NewGame.create(b);
        s.equippedUid = s.newWeapon("skyfall").uid;
        return s;
    }

    @Test
    void shieldTakesDamageFirstThenStaggersTheBoss() {
        GameState s = strongHero();
        s.bossesDefeated.add("LEFT");
        GameSession g = fightIn("R6", s);
        Monster m = g.activeBoss();
        run(g, idle, 2.0f);
        m.shield = m.shieldMax = 100000;
        int hp = m.hp;
        hitBoss(g, m);
        assertEquals(hp, m.hp, "방패가 남아 있으면 체력은 그대로");
        assertTrue(m.shield < m.shieldMax);

        m.shield = 1;
        List<GameEvent> ev = hitBoss(g, m);
        assertEquals(0, m.shield);
        assertEquals(Monster.State.STUNNED, m.state, "방패가 깨지면 잠깐 휘청인다");
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.SHIELD_BREAK));
    }

    @Test
    void groundAttackHitsOnlyIfHeroStaysInIt() {
        GameState s = strongHero();
        GameSession g = fightIn("L6", s);
        Monster m = g.activeBoss();
        m.enter(Monster.State.SPAWN, 99f); // 보스는 가만히
        s.hp = 100;

        Hazard stay = rock(g.hero.x, g.hero.y);
        g.hazards.add(stay);
        run(g, idle, 0.3f);
        assertEquals(100, s.hp, "예고 중에는 아프지 않다");
        run(g, idle, 0.4f);
        assertTrue(s.hp < 100, "그 자리에 있으면 맞는다");
        assertTrue(g.hazards.isEmpty() || stay.dead);

        g.hero.invuln = 0;
        int hp = s.hp;
        g.hazards.add(rock(g.hero.x, g.hero.y));
        Controls walk = new Controls();
        walk.moveY = 1;
        run(g, walk, 0.7f);
        assertEquals(hp, s.hp, "걸어서 벗어나면 안 맞는다");
    }

    private Hazard rock(float x, float y) {
        Hazard h = new Hazard(Hazard.Kind.CIRCLE, "rock");
        h.x = x;
        h.y = y;
        h.radius = 1.25f * b.tileSize;
        h.delay = h.warning = 0.5f;
        h.damage = 20;
        h.multiplier = 1f;
        return h;
    }

    @Test
    void bloodPoolSlowsAndDrains() {
        GameState s = strongHero();
        GameSession g = fightIn("L6", s);
        g.activeBoss().enter(Monster.State.SPAWN, 99f);
        s.hp = 100;
        Hazard pool = new Hazard(Hazard.Kind.POOL, "blood");
        pool.x = g.hero.x;
        pool.y = g.hero.y;
        pool.radius = pool.startRadius = 2 * b.tileSize;
        pool.maxRadius = 2 * b.tileSize;
        pool.grow = 1;
        pool.delay = pool.warning = 0.1f;
        pool.life = 5;
        g.hazards.add(pool);
        run(g, idle, 2.0f);
        assertTrue(s.hp < 100, "밟고 있으면 체력이 닳는다");
        assertTrue(g.hero.slowT > 0, "느려진다");
    }

    @Test
    void golemRushesThreeTimesReAimingEachTime() {
        GameState s = strongHero();
        GameSession g = fightIn("L6", s);
        Monster m = g.activeBoss();
        run(g, idle, 2.0f);
        Room r = g.map.room("L6");
        m.x = (r.centerTileX() + 0.5f) * b.tileSize;
        m.y = (r.centerTileY() + 0.5f) * b.tileSize;
        m.rushLeft = b.monster.rushCount;
        m.enter(Monster.State.CHARGE_TELEGRAPH, 0.3f);
        int charges = 0;
        Monster.State last = m.state;
        for (int i = 0; i < 60 * 8 && m.state != Monster.State.STUNNED && m.state != Monster.State.RECOVER; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
            s.hp = 999;
            g.hero.invuln = 1;
            if (m.state == Monster.State.CHARGING && last != Monster.State.CHARGING) charges++;
            last = m.state;
        }
        assertEquals(b.monster.rushCount, charges);
    }

    @Test
    void demonLordRisesAgainInsteadOfDying() {
        GameState s = NewGame.create(b);
        s.equippedUid = s.newWeapon("holy_awakened").uid; // 마왕은 깨어난 성검으로만 벨 수 있다
        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) s.bossesDefeated.add(id);
        s.unsealed = true;
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        g.dropToAbyss();
        assertEquals(RoomType.FINAL, g.heroRoom().type);
        Controls up = new Controls();
        up.moveY = 1;
        for (int i = 0; i < 600 && !g.fighting(); i++) {
            g.update(1 / 60f, up);
            g.pollEvents();
        }
        assertTrue(g.fighting(), "심연에 떨어져 조금 걸으면 마왕과 싸움이 시작된다");
        Monster m = g.activeBoss();
        assertTrue(m.finalBoss);
        s.hp = 999;
        int max1 = m.maxHp;
        m.hp = 1;
        List<GameEvent> ev = hitBoss(g, m);
        assertFalse(m.dead, "한 번 쓰러뜨려도 끝나지 않는다");
        assertTrue(m.phase2);
        assertEquals(Monster.State.PHASE_SHIFT, m.state);
        assertTrue(m.invulnerable());
        assertEquals(Math.round(max1 * b.monster.finalPhase2HpMultiplier), m.maxHp);
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.BOSS_PHASE2));
        for (int i = 0; i < 60 * 4 && m.state == Monster.State.PHASE_SHIFT; i++) {
            g.update(1 / 60f, idle);
            g.pollEvents();
            s.hp = 999;
        }
        assertEquals(m.maxHp, m.hp, "변신이 끝나면 체력이 다시 가득 찬다");
        assertFalse(s.finalBossDefeated);
        m.hp = 1;
        List<GameEvent> death = hitBoss(g, m);
        GameEvent corpse = death.stream().filter(e -> e.type == GameEvent.Type.MONSTER_DIED).findFirst().orElseThrow();
        assertTrue(m.dead);
        assertTrue(corpse.boss);
        assertEquals(2.3f, corpse.size, .001f, "2페이즈 사망 그림이 1페이즈 크기로 줄면 안 된다");
    }

    @Test
    void campSealUnsealsThenDropsIntoTheAbyss() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        Prop seal = null;
        for (Prop p : g.map.props) if (p.kind == Prop.Kind.SEAL) seal = p;
        assertNotNull(seal);
        assertTrue(seal.walkable(), "문양은 바닥에 그려져 있어 밟고 지나갈 수 있다");
        g.hero.x = (seal.tileX + 1) * b.tileSize;
        g.hero.y = (seal.tileY + 1) * b.tileSize;
        Controls e = new Controls();
        e.interact = true;

        List<GameEvent> ev = run(g, e, 1 / 60f);
        assertTrue(ev.stream().noneMatch(x -> x.type == GameEvent.Type.UNSEAL_READY), "보스 셋을 잡기 전엔 풀 수 없다");

        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) s.bossesDefeated.add(id);
        ev = run(g, e, 1 / 60f);
        assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.UNSEAL_READY));

        s.unsealed = true;
        ev = run(g, e, 1 / 60f);
        assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.FALL));
        g.dropToAbyss();
        assertEquals(RoomType.FINAL, g.heroRoom().type);
    }

    @Test
    void mobsInAClearedDungeonAreTougher() {
        GameState fresh = NewGame.create(b);
        GameSession a = fightIn("L1", fresh);
        int hp0 = a.monsters.get(0).maxHp;

        GameState s = NewGame.create(b);
        s.bossesDefeated.add("LEFT");
        GameSession g = fightIn("L1", s);
        Monster m = g.monsters.get(0);
        assertEquals(Math.round(hp0 * b.monster.clearedMobHpMultiplier), m.maxHp);
        assertEquals(b.monster.clearedMobAttackMultiplier, m.attackMul, 0.001f);
    }
}
