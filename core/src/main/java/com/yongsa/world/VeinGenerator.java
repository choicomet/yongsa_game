package com.yongsa.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 광맥 배치 (FR-채집-03, 04, 09, 구현안 3장).
 * 방마다 미리 정해 둔 후보 칸 중에서 뽑고, 입구에서 넓이 우선 탐색(BFS)으로 모든 광맥에 닿는지 검사한다.
 * 막히면 다시 뽑는다. 광맥마다 나올 광물 수도 여기서 정하고, 첫 광물방은 합계를 보장한다.
 */
public final class VeinGenerator {

    public interface Blocked {
        boolean at(int tx, int ty);
    }

    public record Cell(int x, int y) {
    }

    public record Placement(Cell cell, int amount) {
    }

    private VeinGenerator() {
    }

    /**
     * @param candidates 후보 칸(타일 좌표)
     * @param room       방 안쪽 타일 범위 (x0, y0, x1, y1)
     * @param entry      입구 바로 안쪽 타일
     * @param minTotal   광물 합계 보장 (없으면 0)
     */
    public static List<Placement> generate(List<Cell> candidates, int count, int[] room, Cell entry, Blocked walls,
                                           int oreMin, int oreMax, int minTotal, Random random) {
        count = Math.min(count, candidates.size());
        for (int attempt = 0; attempt < 50; attempt++) {
            List<Cell> pool = new ArrayList<>(candidates);
            List<Cell> chosen = new ArrayList<>();
            while (chosen.size() < count && !pool.isEmpty()) {
                Cell c = pool.remove(random.nextInt(pool.size()));
                if (touches(chosen, c)) continue;
                chosen.add(c);
            }
            if (chosen.size() < count) continue;
            if (!allReachable(chosen, room, entry, walls)) continue;

            List<Placement> result = new ArrayList<>();
            int total = 0;
            for (Cell c : chosen) {
                int amount = oreMin + random.nextInt(oreMax - oreMin + 1);
                result.add(new Placement(c, amount));
                total += amount;
            }
            // 보장: 모자라면 1개짜리 광맥을 최대치로 올린다
            for (int i = 0; i < result.size() && total < minTotal; i++) {
                Placement p = result.get(i);
                if (p.amount() < oreMax) {
                    total += oreMax - p.amount();
                    result.set(i, new Placement(p.cell(), oreMax));
                }
            }
            return result;
        }
        return List.of();
    }

    private static boolean touches(List<Cell> chosen, Cell c) {
        for (Cell o : chosen) if (Math.abs(o.x - c.x) <= 1 && Math.abs(o.y - c.y) <= 1) return true;
        return false;
    }

    /** 광맥을 막힌 칸으로 두고 입구에서 BFS. 모든 광맥의 이웃 칸 중 하나에 닿아야 한다. */
    static boolean allReachable(List<Cell> veins, int[] room, Cell entry, Blocked walls) {
        Set<Cell> blocked = new HashSet<>(veins);
        Set<Cell> seen = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(entry);
        seen.add(entry);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            Cell c = queue.poll();
            for (int[] d : dirs) {
                Cell n = new Cell(c.x + d[0], c.y + d[1]);
                if (n.x < room[0] || n.y < room[1] || n.x > room[2] || n.y > room[3]) continue;
                if (seen.contains(n) || blocked.contains(n) || walls.at(n.x, n.y)) continue;
                seen.add(n);
                queue.add(n);
            }
        }
        for (Cell v : veins) {
            boolean ok = false;
            for (int[] d : dirs) if (seen.contains(new Cell(v.x + d[0], v.y + d[1]))) ok = true;
            if (!ok) return false;
        }
        return true;
    }
}
