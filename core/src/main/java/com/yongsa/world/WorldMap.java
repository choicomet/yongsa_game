package com.yongsa.world;

import com.yongsa.model.GameState;
import com.yongsa.world.MapDef.LinkDef;
import com.yongsa.world.MapDef.RoomDef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 캠프와 던전 3개가 들어 있는 통합 맵 하나 (FR-맵-03, 04).
 * 방은 칸(cell) 하나에 가운데 맞춰 놓이고, 이웃한 방은 문과 짧은 통로로 이어진다 (FR-맵-07).
 */
public class WorldMap {
    public static final int VOID = 0, FLOOR = 1, WALL = 2, DOOR = 3;

    public final int width, height;
    private final int[] tiles;
    private final Door[] doorAt;
    private final Room[] roomAt;
    private final boolean[] blocked;

    public final List<Room> rooms = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();
    private final Map<String, Room> byId = new HashMap<>();
    private final MapDef def;

    public WorldMap(MapDef def) {
        this.def = def;
        int cellsX = def.maxCellX - def.minCellX + 1;
        int cellsY = def.maxCellY - def.minCellY + 1;
        width = cellsX * def.cellW;
        height = cellsY * def.cellH;
        tiles = new int[width * height];
        doorAt = new Door[width * height];
        roomAt = new Room[width * height];
        blocked = new boolean[width * height];

        for (RoomDef rd : def.rooms) buildRoom(rd);
        for (LinkDef ld : def.links) buildLink(ld);
        buildCampProps();
    }

    private int ox(int cx) {
        return (cx - def.minCellX) * def.cellW;
    }

    private int oy(int cy) {
        return (cy - def.minCellY) * def.cellH;
    }

    private void buildRoom(RoomDef rd) {
        RoomType type = RoomType.valueOf(rd.type);
        int[] size = def.sizes.get(rd.type);
        int w = size[0] + 2, h = size[1] + 2;
        int x0 = ox(rd.cx) + (def.cellW - w) / 2;
        int y0 = oy(rd.cy) + (def.cellH - h) / 2;
        Room room = new Room(rd.id, type, rd.dungeon, rd.cx, rd.cy, x0, y0, x0 + w - 1, y0 + h - 1);
        rooms.add(room);
        byId.put(room.id, room);
        for (int y = room.y0; y <= room.y1; y++) {
            for (int x = room.x0; x <= room.x1; x++) {
                boolean edge = x == room.x0 || x == room.x1 || y == room.y0 || y == room.y1;
                set(x, y, edge ? WALL : FLOOR);
                roomAt[idx(x, y)] = room;
            }
        }
    }

    private void buildLink(LinkDef ld) {
        Room a = byId.get(ld.a), b = byId.get(ld.b);
        int dx = b.cellX - a.cellX, dy = b.cellY - a.cellY;
        if (Math.abs(dx) + Math.abs(dy) != 1) throw new IllegalArgumentException("이웃하지 않은 방: " + ld.a + "-" + ld.b);
        if (dx < 0 || dy < 0) { // 항상 a 가 왼쪽/아래가 되게
            Room t = a; a = b; b = t;
            dx = -dx; dy = -dy;
        }
        Link link = new Link(a, b, ld.gate);
        links.add(link);
        if (dx == 1) {
            int mid = oy(a.cellY) + def.cellH / 2 - 1;
            for (int x = a.x1 + 1; x < b.x0; x++) {
                for (int y = mid - 1; y <= mid + 1; y++) set(x, y, FLOOR);
                set(x, mid - 2, WALL);
                set(x, mid + 2, WALL);
            }
            link.doorA = makeDoor(a, Door.Side.RIGHT, a.x1, mid, link);
            link.doorB = makeDoor(b, Door.Side.LEFT, b.x0, mid, link);
        } else {
            int mid = ox(a.cellX) + def.cellW / 2 - 1;
            for (int y = a.y1 + 1; y < b.y0; y++) {
                for (int x = mid - 1; x <= mid + 1; x++) set(x, y, FLOOR);
                set(mid - 2, y, WALL);
                set(mid + 2, y, WALL);
            }
            link.doorA = makeDoor(a, Door.Side.UP, mid, a.y1, link);
            link.doorB = makeDoor(b, Door.Side.DOWN, mid, b.y0, link);
        }
    }

    private Door makeDoor(Room r, Door.Side side, int cx, int cy, Link link) {
        int[] tx = new int[3], ty = new int[3];
        boolean horizontalWall = side == Door.Side.UP || side == Door.Side.DOWN;
        for (int i = 0; i < 3; i++) {
            tx[i] = horizontalWall ? cx - 1 + i : cx;
            ty[i] = horizontalWall ? cy : cy - 1 + i;
        }
        int inX = cx, inY = cy, outX = cx, outY = cy;
        switch (side) {
            case LEFT: inX = cx + 1; outX = cx - 1; break;
            case RIGHT: inX = cx - 1; outX = cx + 1; break;
            case DOWN: inY = cy + 1; outY = cy - 1; break;
            default: inY = cy - 1; outY = cy + 1; break;
        }
        Door d = new Door(r, side, tx, ty, inX, inY, outX, outY);
        d.link = link;
        r.doors.add(d);
        for (int i = 0; i < 3; i++) {
            set(tx[i], ty[i], DOOR);
            doorAt[idx(tx[i], ty[i])] = d;
        }
        return d;
    }

    private void buildCampProps() {
        Room camp = byId.get("CAMP");
        // 캠프 가운데 모닥불 가까이에 둬서 들어오자마자 한 화면에 보이게 한다
        int cy = (int) camp.centerTileY();
        props.add(new Prop(Prop.Kind.FORGE, camp.x0 + 5, cy + 1, 2, 2));
        props.add(new Prop(Prop.Kind.BED, camp.x1 - 6, cy + 1, 2, 2));
        props.add(new Prop(Prop.Kind.CAMPFIRE, (int) camp.centerTileX(), (int) camp.centerTileY() - 1, 1, 1));
        // 캠프 아래쪽 바닥의 봉인 문양: 세 보스의 힘을 모아 성검을 풀면 땅이 갈라져 심연(마왕)으로 떨어진다
        props.add(new Prop(Prop.Kind.SEAL, (int) camp.centerTileX() - 1, camp.y0 + 2, 2, 2));
        for (Prop p : props) {
            if (p.walkable()) continue;
            for (int x = p.tileX; x < p.tileX + p.w; x++)
                for (int y = p.tileY; y < p.tileY + p.h; y++) setBlocked(x, y, true);
        }
    }

    /** 던전 입구를 진행 상황에 맞춰 연다 (FR-던전-01, FR-맵-08). */
    public void updateGates(GameState s) {
        for (Link l : links) {
            if (l.gate == null) continue;
            l.gateClosed = !gateOpen(s, l.gate);
        }
    }

    public static boolean gateOpen(GameState s, String gate) {
        switch (gate) {
            case "LEFT": return true;
            case "RIGHT": return s.bossDefeated("LEFT");
            case "TOP": return s.bossDefeated("RIGHT");
            default: return false;
        }
    }

    private int idx(int x, int y) {
        return x + y * width;
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private void set(int x, int y, int t) {
        if (inBounds(x, y)) tiles[idx(x, y)] = t;
    }

    public int tile(int x, int y) {
        return inBounds(x, y) ? tiles[idx(x, y)] : VOID;
    }

    public Door door(int x, int y) {
        return inBounds(x, y) ? doorAt[idx(x, y)] : null;
    }

    public Room roomAt(int x, int y) {
        return inBounds(x, y) ? roomAt[idx(x, y)] : null;
    }

    public Room room(String id) {
        return byId.get(id);
    }

    public void setBlocked(int x, int y, boolean b) {
        if (inBounds(x, y)) blocked[idx(x, y)] = b;
    }

    public boolean isBlockedOverlay(int x, int y) {
        return inBounds(x, y) && blocked[idx(x, y)];
    }

    /** 지나갈 수 없는 칸인가 (벽, 빈 곳, 닫힌 문, 광맥·시설). */
    public boolean solid(int x, int y) {
        int t = tile(x, y);
        if (t == VOID || t == WALL) return true;
        if (t == DOOR && doorAt[idx(x, y)].isClosed()) return true;
        return blocked[idx(x, y)];
    }

    public Prop propAt(int tx, int ty) {
        for (Prop p : props) if (p.covers(tx, ty)) return p;
        return null;
    }
}
