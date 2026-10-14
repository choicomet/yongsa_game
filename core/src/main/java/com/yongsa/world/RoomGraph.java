package com.yongsa.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 방 연결 그래프 ("camp" + 방 id). 지도의 길 안내에 쓴다. */
public class RoomGraph {

    private final Map<String, List<String>> adj = new HashMap<>();

    public RoomGraph(WorldMap map) {
        for (WorldMap.CorridorDef c : map.corridors) {
            adj.computeIfAbsent(c.a(), k -> new ArrayList<>()).add(c.b());
            adj.computeIfAbsent(c.b(), k -> new ArrayList<>()).add(c.a());
        }
    }

    /** from에서 to까지 거치는 노드 목록 (양 끝 포함). 길이 없으면 빈 목록. */
    public List<String> path(String from, String to) {
        if (from.equals(to)) return List.of(from);
        Map<String, String> prev = new HashMap<>();
        ArrayDeque<String> q = new ArrayDeque<>();
        q.add(from);
        prev.put(from, from);
        while (!q.isEmpty()) {
            String n = q.poll();
            for (String m : adj.getOrDefault(n, List.of())) {
                if (prev.containsKey(m)) continue;
                prev.put(m, n);
                if (m.equals(to)) {
                    List<String> path = new ArrayList<>();
                    for (String x = to; ; x = prev.get(x)) {
                        path.add(x);
                        if (x.equals(from)) break;
                    }
                    Collections.reverse(path);
                    return path;
                }
                q.add(m);
            }
        }
        return List.of();
    }
}
