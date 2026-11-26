package com.yongsa;

import com.yongsa.combat.HeroActor;
import com.yongsa.combat.Physics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 용사가 비스듬한 담에 정면으로 닿아도 멈추지 않고 미끄러지는지, 막힌 곳으로는 절대 들어가지 않는지. */
class WallSlideTest {
    @Test void walkingSlidesAlongWallsAndNeverEntersBlockedFloor() {
        var map = TestData.map();
        for (var link : map.links) link.gateClosed = false;
        var hero = new HeroActor();
        float speed = 4 * map.tileSize, dt = 1 / 60f;
        int starts = 0, stuckMove = 0, stuckWalk = 0;
        for (var room : map.rooms) {
            if (room.scene == null) continue;
            for (float y = room.originY; y < room.originY + room.heightPx; y += 32)
                for (float x = room.originX; x < room.originX + room.widthPx; x += 32) {
                    if (!map.free(x, y, hero.hw, hero.hh)) continue;
                    for (int d = 0; d < 8; d++) {
                        float ux = (float) Math.cos(d * Math.PI / 4), uy = (float) Math.sin(d * Math.PI / 4);
                        if (!map.free(x + ux * 32, y + uy * 32, hero.hw, hero.hh)) continue;
                        starts++;
                        hero.x = x; hero.y = y;
                        for (int f = 0; f < 20; f++) {
                            Physics.walk(map, hero, ux * speed * dt, uy * speed * dt, map.tileSize);
                            assertTrue(map.free(hero.x, hero.y, hero.hw, hero.hh), "walked into blocked floor");
                        }
                        if (Math.hypot(hero.x - x, hero.y - y) < 6) stuckWalk++;
                        hero.x = x; hero.y = y;
                        for (int f = 0; f < 20; f++) Physics.move(map, hero, ux * speed * dt, uy * speed * dt, map.tileSize);
                        if (Math.hypot(hero.x - x, hero.y - y) < 6) stuckMove++;
                    }
                }
        }
        assertTrue(starts > 1000, "audit enough positions");
        assertTrue(stuckWalk <= stuckMove, "sliding must never stick more often than axis movement");
        assertTrue(stuckWalk * 2 < stuckMove + 2, "sliding should remove most snags: walk " + stuckWalk + " vs move " + stuckMove);
    }
}
