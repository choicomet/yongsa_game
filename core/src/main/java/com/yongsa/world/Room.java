package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.combat.Monster;

import java.util.ArrayList;
import java.util.List;

/** 방 하나의 실행 중 상태. 되돌릴 수 있는 상태(구현안 4장)만 가진다. */
public class Room {
    public final WorldMap.RoomDef def;
    public final String id;
    public final RoomKind kind;
    public final String dungeon;
    public final Rectangle bounds;
    public final List<Door> doors = new ArrayList<>();
    public final List<WorldMap.SpawnDef> spawns = new ArrayList<>();
    public final List<Vein> veins = new ArrayList<>();
    public final List<Monster> monsters = new ArrayList<>();
    public Portal portal;

    private RoomState state = RoomState.IDLE;
    /** 광맥을 만들었는지 (침대 이후 처음 들어갈 때 만든다). */
    public boolean veinsGenerated;
    /** 침대 이후 들어가 본 적 있는지 (안개용). */
    public boolean seen;

    public Room(WorldMap.RoomDef def) {
        this.def = def;
        this.id = def.id();
        this.kind = def.kind();
        this.dungeon = def.dungeon();
        this.bounds = new Rectangle(def.bounds());
    }

    public RoomState state() {
        return state;
    }

    public void setState(RoomState s) {
        state = s;
        for (Door d : doors) d.setLocked(s == RoomState.FIGHTING);
    }

    public boolean hasLivingMonsters() {
        for (Monster m : monsters) if (m.alive()) return true;
        return false;
    }

    /** 다 캤거나 몬스터를 다 잡은 상태. 광물방은 몬스터가 없으므로 들어가 본 순간 비운 것으로 본다. */
    public boolean clearedOrOre() {
        return state == RoomState.CLEARED || kind == RoomKind.ORE;
    }
}
