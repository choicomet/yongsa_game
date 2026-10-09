package com.yongsa.world;

import com.yongsa.model.OreType;
import com.yongsa.model.Vein;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 광물방의 광맥을 무작위로 놓는다 (FR-채집-03, 04, 09).
 * 아무 좌표가 아니라 후보 칸(벽에서 한 칸, 문 앞 두 칸을 비운 바닥) 중에서 뽑고,
 * 광맥끼리 붙지 않게 한다. 뽑은 뒤 입구에서 넓이 우선 탐색(BFS)으로
 * 모든 문과 모든 광맥에 닿는지 검사하고, 막히면 다시 뽑는다.
 */
public final class VeinGenerator {
    private VeinGenerator() {
    }

    public static List<Vein> generate(Room room, Random rnd, int count, OreType ore,
                                      int minAmount, int maxAmount, int hits, int guarantee) {
        List<int[]> candidates = candidates(room);
        count = Math.min(count, candidates.size());
        for (int attempt = 0; attempt < 200; attempt++) {
            Collections.shuffle(candidates, rnd);
            List<int[]> picked = new ArrayList<>();
            for (int[] c : candidates) {
                if (picked.size() >= count) break;
                if (touches(picked, c)) continue;
                picked.add(c);
            }
            if (picked.size() < count) continue;
            if (!reachable(room, picked)) continue;
            List<Vein> veins = new ArrayList<>();
            int total = 0;
            for (int[] p : picked) {
                int amount = minAmount + rnd.nextInt(maxAmount - minAmount + 1);
                total += amount;
                veins.add(new Vein(p[0], p[1], ore, amount, hits));
            }
            // 첫 광물방 보장: 합계가 모자라면 아직 최대가 아닌 광맥을 하나씩 올린다
            while (total < guarantee) {
                List<Vein> low = new ArrayList<>();
                for (Vein v : veins) if (v.amount < maxAmount) low.add(v);
                if (low.isEmpty()) break;
                low.get(rnd.nextInt(low.size())).amount++;
                total++;
            }
            return veins;
        }
        throw new IllegalStateException("광맥을 놓을 자리가 없습니다: " + room.id);
    }

    static List<int[]> candidates(Room room) {
        List<int[]> out = new ArrayList<>();
        for (int y = room.y0 + 2; y <= room.y1 - 2; y++) {
            for (int x = room.x0 + 2; x <= room.x1 - 2; x++) {
                if (nearDoor(room, x, y)) continue;
                out.add(new int[]{x, y});
            }
        }
        return out;
    }

    private static boolean nearDoor(Room room, int x, int y) {
        for (Door d : room.doors) {
            if (Math.abs(d.insideX - x) <= 2 && Math.abs(d.insideY - y) <= 2) return true;
        }
        return false;
    }

    private static boolean touches(List<int[]> picked, int[] c) {
        for (int[] p : picked) {
            if (Math.abs(p[0] - c[0]) <= 1 && Math.abs(p[1] - c[1]) <= 1) return true;
        }
        return false;
    }

    /** 광맥을 막힌 칸으로 보고, 문 안쪽에서 시작해 모든 문과 광맥 옆에 닿는지 본다. */
    public static boolean reachable(Room room, List<int[]> veins) {
        int w = room.x1 - room.x0 + 1, h = room.y1 - room.y0 + 1;
        boolean[] block = new boolean[w * h];
        for (int[] v : veins) block[(v[0] - room.x0) + (v[1] - room.y0) * w] = true;
        boolean[] seen = new boolean[w * h];
        if (room.doors.isEmpty()) return true;
        Door first = room.doors.get(0);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{first.insideX, first.insideY});
        seen[(first.insideX - room.x0) + (first.insideY - room.y0) * w] = true;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : dirs) {
                int nx = p[0] + d[0], ny = p[1] + d[1];
                if (!room.inside(nx, ny)) continue;
                int i = (nx - room.x0) + (ny - room.y0) * w;
                if (seen[i] || block[i]) continue;
                seen[i] = true;
                q.add(new int[]{nx, ny});
            }
        }
        for (Door d : room.doors) {
            if (!seen[(d.insideX - room.x0) + (d.insideY - room.y0) * w]) return false;
        }
        for (int[] v : veins) {
            boolean ok = false;
            for (int[] d : dirs) {
                int nx = v[0] + d[0], ny = v[1] + d[1];
                if (room.inside(nx, ny) && seen[(nx - room.x0) + (ny - room.y0) * w]) ok = true;
            }
            if (!ok) return false;
        }
        return true;
    }
}
