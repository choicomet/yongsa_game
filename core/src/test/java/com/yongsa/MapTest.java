package com.yongsa;

import com.yongsa.world.RoomKind;
import com.yongsa.world.WorldMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 맵 규칙: 던전 구성(명세서 5.5), 모든 방에 문과 입구, 보스방은 캠프에서 가장 멀다. */
class MapTest {

    private final WorldMap map = TestFiles.map();

    private long count(String dungeon, RoomKind kind) {
        return map.rooms.stream().filter(r -> r.dungeon().equals(dungeon) && r.kind() == kind).count();
    }

    @Test
    void 던전_구성이_명세서와_같다() {
        assertEquals(3, count("left", RoomKind.MOB));
        assertEquals(1, count("left", RoomKind.ORE));
        assertEquals(3, count("right", RoomKind.MOB));
        assertEquals(2, count("right", RoomKind.ORE));
        assertEquals(4, count("top", RoomKind.MOB));
        assertEquals(2, count("top", RoomKind.ORE));
        for (String d : new String[]{"left", "right", "top"}) assertEquals(1, count(d, RoomKind.BOSS));
    }

    @Test
    void 모든_방에_문과_입구가_있고_입구는_벽이_아니다() {
        for (WorldMap.RoomDef r : map.rooms) {
            assertTrue(map.doors.stream().anyMatch(d -> d.roomId().equals(r.id())), r.id());
            assertTrue(r.bounds().contains(r.entry()), r.id() + " 입구가 방 안");
            assertFalse(r.bounds().contains(r.outside()), r.id() + " 바깥 지점이 방 밖");
            assertFalse(map.solidTile((int) (r.outside().x / 32), (int) (r.outside().y / 32)), r.id());
        }
    }

    @Test
    void 보스방은_캠프에서_가장_멀다() {
        var graph = new com.yongsa.world.RoomGraph(map);
        for (String d : new String[]{"left", "right", "top"}) {
            int bossDepth = 0, maxOther = 0;
            for (WorldMap.RoomDef r : map.rooms) {
                if (!r.dungeon().equals(d)) continue;
                int depth = graph.path("camp", r.id()).size();
                if (r.kind() == RoomKind.BOSS) bossDepth = depth;
                else maxOther = Math.max(maxOther, depth);
            }
            assertTrue(bossDepth >= maxOther, d);
        }
    }

    @Test
    void 캠프_시설과_최종_전장이_있다() {
        assertTrue(map.camp.contains(map.sigil));
        assertTrue(map.camp.contains(map.spawn));
        assertTrue(map.arena.contains(map.arenaLand));
        assertTrue(map.arena.contains(map.finalBossSpawn));
    }
}
