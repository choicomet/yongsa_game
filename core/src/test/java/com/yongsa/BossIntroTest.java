package com.yongsa;

import com.yongsa.combat.Physics;
import com.yongsa.config.Balance;
import com.yongsa.enemy.Monster;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 보스 등장 연출 (카메라 줌인 + 특수 공격 동작 한 번)과 1.2배로 커진 보스가 방에 맞는지. */
class BossIntroTest {
    private final Balance b = TestData.balance();

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    private GameSession bossFight(String dungeon) {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        g.bossIntroEnabled = true;
        Room boss = null;
        for (Room r : g.map.rooms) if (r.type == RoomType.BOSS && dungeon.equals(r.dungeonId)) boss = r;
        assertNotNull(boss, dungeon + " 보스방");
        TestData.walkIn(g, boss);
        assertTrue(g.fighting());
        return g;
    }

    private GameSession demonFight() {
        GameState s = NewGame.create(b);
        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) s.bossesDefeated.add(id);
        s.unsealed = true;
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        g.bossIntroEnabled = true;
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

    private void checkIntro(GameSession g, String name) {
        Monster m = g.activeBoss();
        assertNotNull(m, name);
        assertTrue(g.inBossIntro(), name + ": 들어서면 등장 연출부터");
        float hx = g.hero.x, hy = g.hero.y;
        int heroHp = g.state.hp, bossHp = m.hp;
        Controls push = new Controls();
        push.moveX = 1;
        push.attack = true;
        List<GameEvent> ev = new ArrayList<>();
        boolean wound = false, struck = false;
        float maxFocus = 0;
        while (g.inBossIntro()) {
            maxFocus = Math.max(maxFocus, g.bossIntroFocus());
            ev.addAll(run(g, push, 1 / 60f));
            if (m.state == Monster.State.CAST_TELEGRAPH) wound = true;
            if (m.state == Monster.State.RECOVER) struck = true;
        }
        assertEquals(hx, g.hero.x, 0.01f, name + ": 연출 중엔 용사가 움직이지 않는다");
        assertEquals(hy, g.hero.y, 0.01f, name);
        assertEquals(heroHp, g.state.hp, name + ": 연출 동작은 용사를 다치게 하지 않는다");
        assertEquals(bossHp, m.hp, name + ": 연출 중엔 보스도 맞지 않는다");
        assertTrue(wound && struck, name + ": 특수 공격 준비 → 내리치기 동작을 한 번 보여 준다");
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.SLAM_FX), name + ": 충격파");
        assertTrue(maxFocus > 0.99f, name + ": 카메라가 보스까지 간다");
        assertEquals(0f, g.bossIntroFocus(), name + ": 끝나면 카메라는 용사에게");
        assertEquals(Monster.State.CHASE, m.state, name);

        // 커진 몸(발 사각형)이 선 자리에 끼지 않고, 싸움이 시작되면 실제로 움직여 용사에게 다가온다
        assertTrue(Physics.free(g.map, m.x, m.y, m, g.balance.tileSize), name + ": 보스가 벽에 끼지 않았다");
        float bx = m.x, by = m.y;
        run(g, new Controls(), 3f);
        assertTrue(Math.hypot(m.x - bx, m.y - by) > 4 || g.state.hp < heroHp, name + ": 싸움이 시작되면 보스가 움직인다");
    }

    @Test
    void everyBossShowsOffOnceThenTheFightStarts() {
        for (String d : new String[]{"LEFT", "RIGHT", "TOP"}) checkIntro(bossFight(d), d);
        checkIntro(demonFight(), "FINAL");
    }

    @Test
    void bossesAreAFifthBigger() {
        GameSession g = bossFight("LEFT");
        Monster m = g.activeBoss();
        assertEquals(12 * 2.0f * 1.2f, m.hurtRadius, 0.01f);
        assertEquals(9 * 2.0f * 1.2f, m.hw, 0.01f);
    }

    @Test
    void withoutTheScreenThereIsNoIntro() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 7);
        Room boss = null;
        for (Room r : g.map.rooms) if (r.type == RoomType.BOSS) boss = r;
        TestData.walkIn(g, boss);
        assertTrue(g.fighting());
        assertFalse(g.inBossIntro());
    }
}
