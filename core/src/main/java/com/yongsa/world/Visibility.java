package com.yongsa.world;

import java.util.Map;

/**
 * 던전 안개 (2026-10-10 결정): 용사가 들어가 있는 방만 보이고, 이미 비운 방은 보인다.
 * 침대를 쓰면 다시 가려진다(Room.seen 초기화). 캠프와 최종 전장은 항상 보인다.
 */
public class Visibility {

    private final Map<String, Room> rooms;
    private Room heroRoom;
    private WorldMap.CorridorDef heroCorridor;

    public Visibility(Map<String, Room> rooms) {
        this.rooms = rooms;
    }

    /** 매 프레임: 용사 몸이 걸쳐 있는 방(없으면 null)과 통로. */
    public void setHeroArea(Room room, WorldMap.CorridorDef corridor) {
        heroRoom = room;
        heroCorridor = corridor;
    }

    public boolean room(Room r) {
        return r == heroRoom || (r.seen && r.clearedOrOre());
    }

    public boolean node(String id) {
        if ("camp".equals(id)) return true;
        Room r = rooms.get(id);
        return r != null && room(r);
    }

    public boolean corridor(WorldMap.CorridorDef c) {
        return c == heroCorridor || node(c.a()) || node(c.b());
    }
}
