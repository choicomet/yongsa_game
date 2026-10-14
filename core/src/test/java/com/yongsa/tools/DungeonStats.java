package com.yongsa.tools;

import com.yongsa.config.Balance;
import com.yongsa.world.DungeonGenerator;
import com.yongsa.world.DungeonMap;
import com.yongsa.world.RoomDef;
import com.yongsa.world.Waves;

import java.util.List;

/** 개발용: 씨앗값 여러 개의 던전에서 구역별 전투량과 보상 방 수를 출력한다. ./gradlew core:dungeonStats */
public final class DungeonStats {
    public static void main(String[] args) {
        Balance b = Balance.loadFromPath("data/balance.json");
        int seeds = 20;
        for (Balance.ZoneDef z : b.zones) {
            int rooms = 0, waves = 0, enemies = 0, elites = 0, ranged = 0, oreOnly = 0, all = 0;
            for (long seed = 1; seed <= seeds; seed++) {
                DungeonMap m = DungeonGenerator.generate(seed, b);
                for (RoomDef r : m.zoneRooms(z.id)) {
                    if (r.kind != RoomDef.Kind.ROOM) continue;
                    all++;
                    if (!r.enemies && r.veins) oreOnly++;
                    if (!r.enemies) continue;
                    rooms++;
                    List<List<Waves.Spawn>> plan = Waves.plan(b, r, seed);
                    waves += plan.size();
                    for (List<Waves.Spawn> w : plan) for (Waves.Spawn s : w) {
                        enemies++;
                        if (s.elite) elites++;
                        if ("shoot".equals(s.def.kind) || "mark".equals(s.def.kind)) ranged++;
                    }
                }
            }
            System.out.printf("%-9s rooms %.1f (fight %.1f, ore-only %.1f) | waves/room %.2f | enemies/room %.1f (ranged %.0f%%) | elites %.1f%n",
                    z.id, all / (float) seeds, rooms / (float) seeds, oreOnly / (float) seeds, waves / (float) rooms,
                    enemies / (float) rooms, 100f * ranged / enemies, elites / (float) seeds);
        }
    }
}
