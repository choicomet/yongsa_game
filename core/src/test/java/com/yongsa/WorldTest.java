package com.yongsa;

import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.model.Vein;
import com.yongsa.world.Door;
import com.yongsa.world.Link;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;
import com.yongsa.world.VeinGenerator;
import com.yongsa.world.WorldMap;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class WorldTest {

    @Test
    void mapHasCampAndThreeDungeonsAndFinalRoom() {
        WorldMap map = TestData.map();
        assertNotNull(map.room("CAMP"));
        int boss = 0;
        for (Room r : map.rooms) if (r.type == RoomType.BOSS) boss++;
        assertEquals(3, boss);
        assertEquals(RoomType.FINAL, map.room("F").type);
        assertTrue(map.room("F").doors.isEmpty(), "마왕의 심연은 어느 방과도 이어지지 않는다 (캠프 문양에서 떨어져서 간다)");
        Room camp = map.room("CAMP");
        assertTrue(map.room("L1").x1 < camp.x0, "왼쪽 던전은 캠프 왼쪽");
        assertTrue(map.room("R1").x0 > camp.x1, "오른쪽 던전은 캠프 오른쪽");
        assertTrue(map.room("T1").y0 > camp.y1, "위쪽 던전은 캠프 위쪽");
    }

    @Test
    void gatesOpenWithProgress() {
        WorldMap map = TestData.map();
        GameState s = NewGame.create(TestData.balance());
        map.updateGates(s);
        assertFalse(gate(map, "LEFT").gateClosed);
        assertTrue(gate(map, "RIGHT").gateClosed);
        assertTrue(gate(map, "TOP").gateClosed);
        s.bossesDefeated.add("LEFT");
        map.updateGates(s);
        assertFalse(gate(map, "RIGHT").gateClosed);
        s.bossesDefeated.add("RIGHT");
        map.updateGates(s);
        assertFalse(gate(map, "TOP").gateClosed);
    }

    @Test
    void fightLockClosesDoors() {
        WorldMap map = TestData.map();
        Room r = map.room("L2");
        Door d = r.doors.get(0);
        assertNull(d.link.gate);
        assertFalse(map.solid(d.tilesX[1], d.tilesY[1]));
        r.setFightLock(true);
        assertTrue(map.solid(d.tilesX[1], d.tilesY[1]));
        r.setFightLock(false);
        assertFalse(map.solid(d.tilesX[1], d.tilesY[1]));
    }

    private Link gate(WorldMap map, String gate) {
        for (Link l : map.links) if (gate.equals(l.gate)) return l;
        throw new AssertionError(gate);
    }

    @Test
    void veinsAreSpreadReachableAndGuaranteed() {
        WorldMap map = TestData.map();
        for (String id : new String[]{"L2", "R2", "R4", "T2", "T5"}) {
            Room r = map.room(id);
            for (int seed = 0; seed < 300; seed++) {
                Random rnd = new Random(seed);
                int count = 6 + rnd.nextInt(5);
                List<Vein> veins = VeinGenerator.generate(r, rnd, count, OreType.BLACKIRON, 1, 2, 3, 8);
                assertEquals(count, veins.size());
                int total = 0;
                List<int[]> cells = new ArrayList<>();
                for (Vein v : veins) {
                    assertTrue(r.inside(v.tileX, v.tileY));
                    total += v.amount;
                    for (int[] c : cells) {
                        assertFalse(Math.abs(c[0] - v.tileX) <= 1 && Math.abs(c[1] - v.tileY) <= 1, "광맥끼리 붙지 않는다");
                    }
                    cells.add(new int[]{v.tileX, v.tileY});
                }
                assertTrue(total >= 8, "첫 광물방 보장");
                assertTrue(VeinGenerator.reachable(r, cells));
            }
        }
    }
}
