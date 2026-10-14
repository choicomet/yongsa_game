package com.yongsa;

import com.yongsa.combat.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 검 스킬 (Q): 강화 단계에서 열리고, 검마다 모양이 다르고, 각성하면 강해진다. */
class SkillTest {
    private final Balance b = TestData.balance();

    private GameSession fightWith(String weaponId, int level, boolean awakened) {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon(weaponId);
        w.level = level;
        w.awakened = awakened;
        s.equippedUid = w.uid;
        GameSession g = new GameSession(b, TestData.map(), s, 11);
        TestData.walkIn(g, g.map.room("L1"));
        assertTrue(g.fighting());
        run(g, new Controls(), 1.5f); // 마법진이 사라질 때까지
        return g;
    }

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
            c.skill = false;
        }
        return all;
    }

    /** 몬스터 하나만 남겨 용사 오른쪽 dx 에 세운다. */
    private Monster lineUp(GameSession g, float dx) {
        com.yongsa.world.Room r = g.map.room("L1");
        g.hero.x = (r.centerTileX() + 0.5f) * b.tileSize;
        g.hero.y = (r.centerTileY() + 0.5f) * b.tileSize;
        Monster m = g.monsters.get(0);
        for (int i = 1; i < g.monsters.size(); i++) g.monsters.get(i).dead = true;
        m.x = g.hero.x + dx;
        m.y = g.hero.y;
        m.hp = m.maxHp = 100000;
        m.attackCd = 99;
        m.patternCd = 99;
        g.hero.aimX = 1;
        g.hero.aimY = 0;
        return m;
    }

    private Controls skill() {
        Controls c = new Controls();
        c.skill = true;
        return c;
    }

    @Test
    void skillIsLockedBelowUnlockLevel() {
        GameSession g = fightWith("blackiron", b.enhance.skillUnlockLevel - 1, false);
        lineUp(g, 60);
        List<GameEvent> ev = run(g, skill(), 0.1f);
        assertTrue(g.shots.isEmpty());
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.TOAST && e.text.contains("강화하면")));
    }

    @Test
    void blackironCleaveHitsAndStunsAndHasCooldown() {
        GameSession g = fightWith("blackiron", b.enhance.skillUnlockLevel, false);
        Monster m = lineUp(g, 70);
        run(g, skill(), 0.02f);
        assertEquals(1, g.shots.size());
        assertTrue(g.hero.skillCd > 0);
        run(g, new Controls(), 0.4f);
        assertTrue(m.hp < m.maxHp, "충격파에 맞는다");
        assertEquals(Monster.State.STUNNED, m.state);
        run(g, skill(), 0.02f);
        assertTrue(g.shots.isEmpty(), "쿨타임 동안은 다시 못 쓴다");
    }

    @Test
    void awakenedCleaveSplitsIntoThree() {
        GameSession g = fightWith("blackiron", 10, true);
        lineUp(g, 190);
        run(g, skill(), 0.01f); // 한 프레임: 벽에 닿기 전에 센다
        assertEquals(3, g.shots.size());
    }

    @Test
    void bloodmoonCrescentHealsOnHit() {
        GameSession g = fightWith("bloodmoon", b.enhance.skillUnlockLevel, false);
        lineUp(g, 80);
        g.state.hp = 10;
        run(g, skill(), 0.5f);
        assertTrue(g.state.hp > 10, "맞힌 만큼 회복");
    }

    @Test
    void skyfallStrikesNearestEnemies() {
        GameSession g = fightWith("skyfall", b.enhance.skillUnlockLevel, false);
        Monster m = lineUp(g, 120);
        m.y += 60; // 바라보는 방향이 아니어도 맞는다
        run(g, skill(), 0.02f);
        assertFalse(g.shots.isEmpty());
        run(g, new Controls(), 0.6f);
        assertTrue(m.hp < m.maxHp);
    }
}
