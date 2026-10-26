package com.yongsa;

import com.yongsa.combat.HeroActor;
import com.yongsa.combat.Physics;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.world.Corridor;
import com.yongsa.world.WorldMap;
import org.junit.jupiter.api.Test;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 그림 바닥을 굳힌 충돌 격자: 실제 걸음으로 길목을 끝까지 지나가고, 막힌 곳에 끼면 빠져나온다. */
class CollisionGridTest {
    private WorldMap unlockedMap() {
        WorldMap map = TestData.map();
        GameState s = NewGame.create(TestData.balance());
        s.bossesDefeated.addAll(List.of("LEFT", "RIGHT", "TOP"));
        s.unsealed = true;
        s.mapVersion = WorldMap.VERSION;
        map.updateGates(s);
        return map;
    }

    @Test
    void heroWalksEveryPassageFromEndToEndWithoutGettingStuck() {
        WorldMap map = unlockedMap();
        for (Corridor c : map.corridors) {
            HeroActor hero = new HeroActor();
            float[] start = c.centerline.get(0);
            hero.x = start[0];
            hero.y = start[1];
            int next = 1, stalled = 0;
            for (int tick = 0; tick < 6000 && next < c.centerline.size(); tick++) {
                float[] goal = c.centerline.get(next);
                float dx = goal[0] - hero.x, dy = goal[1] - hero.y, len = (float) Math.hypot(dx, dy);
                if (len < 4) { next++; stalled = 0; continue; }
                float px = hero.x, py = hero.y, step = Math.min(len, 128f / 60f);
                Physics.move(map, hero, dx / len * step, dy / len * step, map.tileSize);
                stalled = Math.hypot(hero.x - px, hero.y - py) < .01f ? stalled + 1 : 0;
                assertTrue(stalled < 30, c.key() + " stuck at " + hero.x + "," + hero.y + " heading to point " + next);
            }
            assertEquals(c.centerline.size(), next, c.key() + " did not reach the far end");
        }
    }

    @Test
    void heroPushedIntoAWallStepsBackOntoTheNearestFloor() {
        WorldMap map = unlockedMap();
        Corridor c = map.corridors.get(0);
        float[] p = c.centerline.get(c.centerline.size() / 2);
        HeroActor hero = new HeroActor();
        // 길목 가운데에서 옆으로 나가다 처음 막히는 자리 바로 너머 (벽 속)
        float x = p[0], y = p[1];
        while (map.free(x, y, hero.hw, hero.hh)) x += 1;
        hero.x = x + 3;
        hero.y = y;
        assertFalse(map.free(hero.x, hero.y, hero.hw, hero.hh));
        Physics.move(map, hero, 0, 0, map.tileSize);
        assertTrue(map.free(hero.x, hero.y, hero.hw, hero.hh), "Feet must be back on the floor");
        assertTrue(Math.hypot(hero.x - (x + 3), hero.y - y) <= 24, "Only a short step, never through a wall");
    }
}
