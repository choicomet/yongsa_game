package com.yongsa.world;

import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

/** assets/data/map.json: 통합 맵의 방 배치 (방 배치는 고정, A10). */
public class MapDef {
    public int cellW, cellH;
    public int minCellX, maxCellX, minCellY, maxCellY;
    public Map<String, int[]> sizes;
    public List<RoomDef> rooms;
    public List<LinkDef> links;

    public static MapDef parse(String json) {
        return new Gson().fromJson(json, MapDef.class);
    }

    public static class RoomDef {
        public String id, type, dungeon;
        public int cx, cy;
    }

    public static class LinkDef {
        public String a, b, gate;
    }
}
