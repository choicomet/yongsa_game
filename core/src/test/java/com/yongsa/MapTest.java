package com.yongsa;

import com.badlogic.gdx.math.Vector2;
import com.yongsa.config.Balance;
import com.yongsa.model.GameState;
import com.yongsa.model.MineRoomState;
import com.yongsa.model.Rng;
import com.yongsa.model.VeinState;
import com.yongsa.world.DungeonGenerator;
import com.yongsa.world.DungeonMap;
import com.yongsa.world.MapObject;
import com.yongsa.world.RoomDef;
import com.yongsa.world.VeinGenerator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MapTest {
    final Balance b = TestData.balance();
    final DungeonMap map = TestData.map();

    @Test
    void mapPassesValidation() {
        List<String> errors = map.validate(b);
        assertTrue(errors.isEmpty(), String.join("\n", errors));
    }

    @Test
    void campLayout() {
        RoomDef camp = map.room("camp");
        assertEquals(28 * 32, camp.bounds.width, 0.01);
        assertEquals(14 * 32, camp.bounds.height, 0.01);
        Vector2 sp = map.point("camp_spawn");
        MapObject bed = map.interact("bed");
        MapObject forge = map.interact("forge");
        // 리스폰에서 침대와 대장간까지 걸어서 2초 이내 (직선 거리 기준)
        assertTrue(sp.dst(bed.x(), bed.y()) / b.hero.moveSpeed < 2f);
        assertTrue(sp.dst(forge.x(), forge.y()) / b.hero.moveSpeed < 2f);
        assertTrue(bed.x() < sp.x && forge.x() > sp.x, "침대는 왼쪽, 대장간은 오른쪽");
    }

    @Test
    void manySeedsGenerateValidDungeons() {
        for (long seed = 1; seed <= 40; seed++) {
            DungeonMap m = DungeonGenerator.generate(seed, b);
            assertTrue(m.validate(b).isEmpty(), "seed " + seed + ": " + m.validate(b));
            int rooms = m.rooms.size() - 1;
            assertTrue(rooms >= 42 && rooms <= 54, "방 수 " + rooms);
            int mixed = 0, battle = 0, ore = 0;
            for (RoomDef r : m.rooms) {
                if (r.kind != RoomDef.Kind.ROOM) continue;
                if (r.enemies && r.veins) mixed++;
                else if (r.enemies) battle++;
                else ore++;
            }
            assertTrue(mixed > battle && mixed > ore, "몬스터+광맥 방이 가장 많아야 함");
        }
    }

    @Test
    void sameSeedSameDungeon_differentSeedDifferent() {
        DungeonMap a = DungeonGenerator.generate(777, b), c = DungeonGenerator.generate(777, b);
        assertEquals(a.rooms.size(), c.rooms.size());
        for (int i = 0; i < a.rooms.size(); i++) {
            assertEquals(a.rooms.get(i).id, c.rooms.get(i).id);
            assertEquals(a.rooms.get(i).bounds, c.rooms.get(i).bounds);
        }
        assertEquals(a.veins.size(), c.veins.size());
        DungeonMap d = DungeonGenerator.generate(778, b);
        boolean differs = false;
        for (RoomDef r : d.rooms) {
            RoomDef o = a.room(r.id);
            if (o == null || !o.bounds.equals(r.bounds)) differs = true;
        }
        assertTrue(differs);
    }

    @Test
    void bossRoomIsAtEndOfMainPath() {
        for (String z : new String[]{"mine", "fortress", "temple"}) {
            java.util.List<RoomDef> rooms = map.zoneRooms(z);
            int maxDepth = 0;
            for (RoomDef r : rooms) if (r.mainPath) maxDepth = Math.max(maxDepth, r.depth);
            assertEquals(RoomDef.Kind.BOSS, map.room(z + "_boss").kind);
            assertEquals(maxDepth, map.room(z + "_boss").depth);
            assertEquals(maxDepth - 1, map.room(z + "_ante").depth);
        }
    }

    @Test
    void T23_noSpawnsInCamp() {
        RoomDef camp = map.room("camp");
        for (MapObject s : map.spawns) {
            assertFalse(camp.bounds.contains(s.x(), s.y()), s.name);
            RoomDef r = map.roomAt(s.x(), s.y());
            assertNotNull(r);
            assertEquals(s.prop("room"), r.id);
        }
    }

    @Test
    void T12_veinGenerationUniqueAndStable() {
        GameState s = TestData.newGame();
        String ore = TestData.oreRoom().id;
        MineRoomState m = VeinGenerator.ensure(b, s, map, ore);
        assertEquals(4, m.veins.size());
        Set<String> ids = new HashSet<>();
        boolean visible = false;
        for (VeinState v : m.veins) {
            assertTrue(ids.add(v.candidateId));
            if ("true".equals(map.vein(v.candidateId).prop("visible"))) visible = true;
        }
        assertTrue(visible, "최소 한 개는 입구에서 보이는 후보");
        long uses = s.veinUses;
        // 재진입: 같은 결과, 난수 소비 없음
        assertSame(m, VeinGenerator.ensure(b, s, map, ore));
        assertEquals(uses, s.veinUses);
        assertEquals(0, s.enhanceUses, "광맥 생성은 강화 난수에 영향 없음");
    }

    @Test
    void pickNeverRepeats() {
        List<String> c = new ArrayList<>();
        for (int i = 0; i < 8; i++) c.add("c" + i);
        for (long seed = 0; seed < 200; seed++) {
            long sd = seed;
            long[] k = {0};
            List<String> p = VeinGenerator.pick(c, List.of("c0", "c1"), 4, () -> Rng.at(sd, k[0]++));
            assertEquals(4, new HashSet<>(p).size());
        }
    }
}
