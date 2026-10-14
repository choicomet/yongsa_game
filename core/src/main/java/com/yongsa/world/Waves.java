package com.yongsa.world;

import com.yongsa.config.Balance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 전투방의 웨이브 구성. 구역의 WaveDef(웨이브 수, 예산)와 적의 cost 로만 정해지며,
 * 같은 씨앗값·같은 방이면 항상 같은 구성이 나온다 (다시 도전해도 방이 바뀌지 않는다).
 */
public final class Waves {
    private Waves() {}

    /** 소환될 적 하나. */
    public static class Spawn {
        public final Balance.EnemyDef def;
        public final boolean elite;

        Spawn(Balance.EnemyDef def, boolean elite) {
            this.def = def;
            this.elite = elite;
        }
    }

    public static List<List<Spawn>> plan(Balance b, RoomDef room, long dungeonSeed) {
        Balance.ZoneDef zone = b.zone(room.zone);
        Balance.WaveDef w = zone.waves;
        Random rnd = new Random(dungeonSeed * 31L + room.id.hashCode());
        int count = w.minWaves;
        if (room.depth > b.dungeon.shallowDepth && w.maxWaves > w.minWaves) {
            count += rnd.nextInt(w.maxWaves - w.minWaves + 1);
        }
        float budget = budget(b, room);
        if (room.guard) count += w.guardExtraWaves;
        List<List<Spawn>> waves = new ArrayList<>();
        for (int i = 0; i < count; i++) waves.add(wave(b, zone, room, budget, rnd));
        return waves;
    }

    /** 이 방의 웨이브 하나가 쓰는 예산. */
    public static float budget(Balance b, RoomDef room) {
        Balance.WaveDef w = b.zone(room.zone).waves;
        return (w.budget + w.budgetPerDepth * room.depth) * (room.guard ? w.guardBudgetMul : 1f);
    }

    /** 이 깊이의 방에 나올 수 있는 적 (구역 표에서 minDepth 로 거른다). */
    static List<Balance.EnemyDef> table(Balance b, Balance.ZoneDef zone, int depth) {
        List<Balance.EnemyDef> out = new ArrayList<>();
        for (String id : zone.enemyTable) {
            Balance.EnemyDef d = b.enemy(id);
            if (d.minDepth <= depth) out.add(d);
        }
        if (out.isEmpty()) for (String id : zone.enemyTable) out.add(b.enemy(id));
        return out;
    }

    private static List<Spawn> wave(Balance b, Balance.ZoneDef zone, RoomDef room, float budget, Random rnd) {
        Balance.WaveDef w = zone.waves;
        Balance.EliteDef el = b.elite;
        float eliteChance = room.depth < el.minDepth ? 0 : room.mainPath ? el.chance : el.branchChance;
        List<Balance.EnemyDef> table = table(b, zone, room.depth);
        List<Spawn> out = new ArrayList<>();
        boolean hasElite = false;
        float left = budget;
        while (out.size() < w.maxPerWave) {
            Balance.EnemyDef def = table.get(rnd.nextInt(table.size()));
            if (def.cost > left + 1e-3f) def = cheapest(table);
            // 첫 한 마리는 예산이 모자라도 넣는다 (빈 웨이브 방지)
            if (def.cost > left + 1e-3f && !out.isEmpty()) break;
            // 제단을 지키는 방은 웨이브마다 강화 개체가 반드시 하나 있다
            boolean elite = !hasElite && (room.guard || rnd.nextFloat() < eliteChance);
            hasElite |= elite;
            out.add(new Spawn(def, elite));
            left -= def.cost;
        }
        return out;
    }

    private static Balance.EnemyDef cheapest(List<Balance.EnemyDef> table) {
        Balance.EnemyDef best = null;
        for (Balance.EnemyDef d : table) if (best == null || d.cost < best.cost) best = d;
        return best;
    }
}
