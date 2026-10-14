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
        for (Door d : map.room("F").doors) {
            assertTrue(d.link.oneWay, "심연 계단은 캠프로 올라가기만 한다 (내려오는 길은 봉인 문양)");
            assertEquals("CAMP", d.partner().room.id);
        }
        assertEquals(RoomType.VILLAGE, map.room("CAMP").doors.stream()
                .filter(d -> d.name.equals("down")).findFirst().get().partner().room.type, "캠프 아래 문은 마을로 나간다");
        for (Room r : map.rooms)
            for (Door d : r.doors) assertFalse(d.dead(), "그림 속 출구는 모두 어딘가로 이어진다: " + r.id + "." + d.name);
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
        Room r = map.room("L3");
        for (Door d : r.doors) {
            assertFalse(d.dead(), "갱도 방의 세 출구는 모두 이어져 있다");
            assertNull(d.link.gate);
            float px = d.footX, py = d.footY;
            assertTrue(map.free(px, py, 9, 6), d.name);
            r.setFightLock(true);
            assertFalse(map.free(px, py, 9, 6), d.name);
            assertTrue(map.free(d.arriveX, d.arriveY, 9, 6), "문 앞 들어온 자리는 막지 않는다");
            r.setFightLock(false);
            assertTrue(map.free(px, py, 9, 6));
        }
    }

    @Test
    void everyLinkedDoorArrivesOnFloorAwayFromItsOwnTrigger() {
        WorldMap map = TestData.map();
        GameState all = NewGame.create(TestData.balance());
        for (String id : new String[]{"LEFT", "RIGHT", "TOP"}) all.bossesDefeated.add(id);
        all.unsealed = true;
        map.updateGates(all);
        assertEquals(25, map.links.size());
        for (Link l : map.links) {
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                String name = d.room.id + "." + d.name;
                assertSame(d.room, map.roomAt((int) Math.floor(d.arriveX / 32), (int) Math.floor(d.arriveY / 32)), name);
                assertTrue(map.free(d.arriveX, d.arriveY, 12, 12), name + " 들어온 자리가 바닥");
                assertFalse(d.triggers(d.arriveX, d.arriveY), name + " 들어오자마자 되돌아가지 않는다");
                // 들어온 자리에서 출구 쪽으로 걸어가면 넘어갈 수 있어야 한다
                float x = d.arriveX, y = d.arriveY;
                boolean reached = false;
                for (int i = 0; i < 400 && !reached; i++) {
                    float tx = d.footX + d.dirX * 64 - x, ty = d.footY + d.dirY * 64 - y;
                    float len = (float) Math.sqrt(tx * tx + ty * ty);
                    float nx = x + tx / len * 2, ny = y + ty / len * 2;
                    if (map.free(nx, y, 9, 6)) x = nx;
                    if (map.free(x, ny, 9, 6)) y = ny;
                    reached = d.triggers(x, y);
                }
                assertTrue(reached, name + " 출구까지 걸어갈 수 있다");
            }
        }
    }

    @Test
    void roomSizesVary() {
        WorldMap map = TestData.map();
        java.util.Set<Integer> widths = new java.util.HashSet<>();
        int minBoss = Integer.MAX_VALUE, maxMob = 0;
        for (Room r : map.rooms) {
            if (r.scene == null) continue;
            widths.add(r.x1 - r.x0);
            int floor = 0;
            for (int y = r.y0; y <= r.y1; y++) for (int x = r.x0; x <= r.x1; x++) if (r.floor(x, y)) floor++;
            if (r.type == RoomType.BOSS) minBoss = Math.min(minBoss, floor);
            if (r.type == RoomType.MOB) maxMob = Math.max(maxMob, floor);
            assertTrue(floor > 90, r.id + " 바닥 " + floor);
        }
        assertTrue(widths.size() >= 4, "방 크기가 여러 가지");
        assertTrue(minBoss > maxMob, "보스방이 가장 넓다");
    }

    private Link gate(WorldMap map, String gate) {
        for (Link l : map.links) if (gate.equals(l.gate)) return l;
        throw new AssertionError(gate);
    }

    @Test
    void veinsAreSpreadReachableAndGuaranteed() {
        WorldMap map = TestData.map();
        for (String id : new String[]{"L2", "L4", "R2", "R4", "T2", "T4"}) {
            Room r = map.room(id);
            for (int seed = 0; seed < 300; seed++) {
                Random rnd = new Random(seed);
                int count = 6 + rnd.nextInt(5);
                List<Vein> veins = VeinGenerator.generate(r, rnd, count, OreType.BLACKIRON, 1, 2, 3, 8);
                assertTrue(veins.size() >= Math.min(count, 6), id + " 광맥 " + veins.size() + "/" + count);
                int total = 0;
                List<int[]> cells = new ArrayList<>();
                for (Vein v : veins) {
                    assertTrue(r.insideWithMargin(v.tileX, v.tileY, 1));
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
