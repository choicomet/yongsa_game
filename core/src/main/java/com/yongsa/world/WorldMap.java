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
 * 방은 지도 칸 하나의 가운데에 떨어져 놓이고, 사이 통로 없이 문으로 이어진다:
 * 문(그림 속 계단·갱도 입구)을 지나가면 이어진 방의 짝 문 앞으로 옮겨 간다.
 * 캠프와 던전 방은 배경 그림 한 장이고 그림의 바닥 경계(다각형)가 벽이다.
 */
public class WorldMap {
    public static final int VOID = 0, FLOOR = 1, WALL = 2, DOOR = 3;
    /** 방 배치가 바뀌면 올린다. 저장 파일의 방 기록(광맥 자리)·이어하기 위치가 다르면 버린다. */
    public static final int VERSION = 4;

    public final int width, height;
    public final int tileSize;
    private final int[] tiles;
    private final Door[] doorAt;
    private final Room[] roomAt;
    private final boolean[] blocked;

    public final List<Room> rooms = new ArrayList<>();
    public final List<Link> links = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();
    private final Map<String, Room> byId = new HashMap<>();
    private final MapDef def;
    private final RoomSceneLayout scenes;

    public WorldMap(MapDef def, RoomSceneLayout scenes, int tileSize) {
        this.def = def;
        this.scenes = scenes;
        this.tileSize = tileSize;
        int cellsX = def.maxCellX - def.minCellX + 1;
        int cellsY = def.maxCellY - def.minCellY + 1;
        width = cellsX * def.slotW;
        height = cellsY * def.slotH;
        tiles = new int[width * height];
        doorAt = new Door[width * height];
        roomAt = new Room[width * height];
        blocked = new boolean[width * height];

        for (RoomDef rd : def.rooms) {
            if (rd.scene == null) buildTileRoom(rd);
            else buildSceneRoom(rd);
        }
        for (LinkDef ld : def.links) buildLink(ld);
        buildCampProps();
    }

    private int ox(int cx) {
        return (cx - def.minCellX) * def.slotW;
    }

    private int oy(int cy) {
        return (cy - def.minCellY) * def.slotH;
    }

    private void addRoom(Room room) {
        if (byId.put(room.id, room) != null) throw new IllegalArgumentException("같은 방 id: " + room.id);
        rooms.add(room);
    }

    // ───────────────────────── 타일 방 (캠프)

    private void buildTileRoom(RoomDef rd) {
        RoomType type = RoomType.valueOf(rd.type);
        int w = def.campSize[0] + 2, h = def.campSize[1] + 2;
        int x0 = ox(rd.cx) + (def.slotW - w) / 2;
        int y0 = oy(rd.cy) + (def.slotH - h) / 2;
        Room room = new Room(rd.id, type, rd.dungeon, rd.cx, rd.cy, x0, y0, x0 + w - 1, y0 + h - 1);
        addRoom(room);
        for (int y = room.y0; y <= room.y1; y++) {
            for (int x = room.x0; x <= room.x1; x++) {
                boolean edge = x == room.x0 || x == room.x1 || y == room.y0 || y == room.y1;
                set(x, y, edge ? WALL : FLOOR);
                roomAt[idx(x, y)] = room;
            }
        }
    }

    /** 캠프 벽 가운데에 3칸짜리 문과 짧은 통로 끝을 낸다. 통로 끝까지 걸어가면 이어진 방으로 넘어간다. */
    private Door makeTileDoor(Room r, Door.Side side) {
        int cx, cy;
        switch (side) {
            case LEFT: cx = r.x0; cy = (r.y0 + r.y1) / 2; break;
            case RIGHT: cx = r.x1; cy = (r.y0 + r.y1) / 2; break;
            case DOWN: cx = (r.x0 + r.x1) / 2; cy = r.y0; break;
            default: cx = (r.x0 + r.x1) / 2; cy = r.y1; break;
        }
        int[] tx = new int[3], ty = new int[3];
        boolean horizontalWall = side == Door.Side.UP || side == Door.Side.DOWN;
        for (int i = 0; i < 3; i++) {
            tx[i] = horizontalWall ? cx - 1 + i : cx;
            ty[i] = horizontalWall ? cy : cy - 1 + i;
        }
        int dx = side == Door.Side.LEFT ? -1 : side == Door.Side.RIGHT ? 1 : 0;
        int dy = side == Door.Side.DOWN ? -1 : side == Door.Side.UP ? 1 : 0;
        int T = tileSize;
        Door d = new Door(r, side.name(), side, tx, ty, cx - dx, cy - dy, cx + dx, cy + dy,
                (cx + 0.5f) * T, (cy + 0.5f) * T, dx, dy);
        d.halfWidth = 1.5f * T;
        d.triggerDepth = 0.55f * T;
        d.arriveX = (cx - dx + 0.5f) * T;
        d.arriveY = (cy - dy + 0.5f) * T;
        r.doors.add(d);
        for (int i = 0; i < 3; i++) {
            set(tx[i], ty[i], DOOR);
            doorAt[idx(tx[i], ty[i])] = d;
            // 문 밖 두 칸짜리 통로 끝 (어둠 속으로 이어지는 것처럼 보인다)
            for (int k = 1; k <= 2; k++) set(tx[i] + dx * k, ty[i] + dy * k, FLOOR);
        }
        for (int k = 1; k <= 2; k++) {
            int px = horizontalWall ? 1 : 0, py = horizontalWall ? 0 : 1;
            set(cx + dx * k - px * 2, cy + dy * k - py * 2, WALL);
            set(cx + dx * k + px * 2, cy + dy * k + py * 2, WALL);
        }
        return d;
    }

    // ───────────────────────── 그림 방

    private void buildSceneRoom(RoomDef rd) {
        RoomSceneLayout.Scene sc = scenes.scene(rd.scene);
        if (sc == null) throw new IllegalArgumentException("없는 장면: " + rd.scene);
        int T = tileSize;
        float w = rd.width * T, h = sc.height(w);
        float ox = Math.round(ox(rd.cx) * T + (def.slotW * T - w) / 2f);
        float oy = Math.round(oy(rd.cy) * T + (def.slotH * T - h) / 2f);
        int x0 = (int) Math.floor(ox / T), x1 = (int) Math.ceil((ox + w) / T) - 1;
        int y0 = (int) Math.floor(oy / T), y1 = (int) Math.ceil((oy + h) / T) - 1;
        Room room = new Room(rd.id, RoomType.valueOf(rd.type), rd.dungeon, rd.cx, rd.cy, x0, y0, x1, y1,
                sc, ox, oy, w, h, rd.flip);
        room.buildMasks(T);
        addRoom(room);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, room.floor(x, y) ? FLOOR : WALL);
                roomAt[idx(x, y)] = room;
            }
        }
        for (RoomSceneLayout.Portal p : sc.portals) room.doors.add(makeSceneDoor(room, p));
    }

    private Door makeSceneDoor(Room room, RoomSceneLayout.Portal p) {
        int T = tileSize;
        float fx = room.sceneX(p.foot[0]), fy = room.sceneY(p.foot[1]);
        float[] dir = facing(p.facing, room.flip);
        float dx = dir[0], dy = dir[1];
        Door.Side side = Math.abs(dx) > Math.abs(dy) ? (dx < 0 ? Door.Side.LEFT : Door.Side.RIGHT)
                : (dy < 0 ? Door.Side.DOWN : Door.Side.UP);

        // 출구 폭: 출구 자리에서 옆으로 바닥이 끝나는 데까지
        float half = 0;
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            float s = 0;
            while (s < 4 * T && room.allowsFoot(fx - dy * s * sgn, fy + dx * s * sgn, 0)) s += 2;
            half = Math.max(half, s);
        }
        // 출구 안으로 걸어 들어갈 수 있는 깊이
        float reach = -T;
        for (float s = 0; s < 5 * T; s += 2) {
            if (!room.allowsFoot(fx + dx * s, fy + dy * s, 10)) break;
            reach = s;
        }
        float trigger = Math.max(-0.5f * T, Math.min(1.0f * T, reach - 0.4f * T));

        // 들어올 때 서는 자리: 출구에서 방 안쪽으로 두 칸쯤, 발이 들어가는 곳
        float ax = room.spawnX(T), ay = room.spawnY(T);
        float[] dists = {2f, 1.6f, 2.5f, 3f, 1.3f, 3.6f, 4.3f};
        float[] offs = {0f, 0.5f, -0.5f, 1f, -1f};
        search:
        for (float k : dists) {
            for (float o : offs) {
                float x = fx - dx * k * T - dy * o * T, y = fy - dy * k * T + dx * o * T;
                if (room.allowsFoot(x, y, 14)) {
                    ax = x;
                    ay = y;
                    break search;
                }
            }
        }
        int inX = (int) Math.floor(ax / T), inY = (int) Math.floor(ay / T);
        int ftx = (int) Math.floor(fx / T), fty = (int) Math.floor(fy / T);
        Door d = new Door(room, p.id, side, new int[]{ftx}, new int[]{fty}, inX, inY, inX, inY, fx, fy, dx, dy);
        d.halfWidth = Math.max(0.8f * T, Math.min(3f * T, half));
        d.triggerDepth = trigger;
        d.arriveX = ax;
        d.arriveY = ay;
        return d;
    }

    /** 그림 속 방향 이름 → 월드 방향 (위가 +y). */
    static float[] facing(String f, boolean flip) {
        float x = 0, y = 0;
        String s = f == null ? "DOWN" : f;
        if (s.contains("LEFT")) x = -1;
        if (s.contains("RIGHT")) x = 1;
        if (s.contains("UP")) y = 1;
        if (s.contains("DOWN")) y = -1;
        if (flip) x = -x;
        float l = (float) Math.sqrt(x * x + y * y);
        if (l < 0.01f) return new float[]{0, -1};
        return new float[]{x / l, y / l};
    }

    // ───────────────────────── 이음

    private void buildLink(LinkDef ld) {
        Door da = doorFor(ld.a, false), db = doorFor(ld.b, ld.oneWay);
        Link link = new Link(da.room, db.room, ld.gate);
        link.doorA = da;
        link.doorB = db;
        link.oneWay = ld.oneWay;
        da.link = link;
        if (!ld.oneWay) db.link = link;
        links.add(link);
        // 그림 문의 '바깥'은 이어진 방에서 서는 칸이다 (잠긴 방에서 저장하면 거기서 이어 한다)
        if (da.room.scene != null) {
            da.outsideX = (int) Math.floor(db.arriveX / tileSize);
            da.outsideY = (int) Math.floor(db.arriveY / tileSize);
        }
        if (db.room.scene != null && !ld.oneWay) {
            db.outsideX = (int) Math.floor(da.arriveX / tileSize);
            db.outsideY = (int) Math.floor(da.arriveY / tileSize);
        }
    }

    private Door doorFor(String ref, boolean shared) {
        int dot = ref.indexOf('.');
        if (dot < 0) throw new IllegalArgumentException("이음은 '방.출구' 꼴이어야 한다: " + ref);
        Room r = byId.get(ref.substring(0, dot));
        String name = ref.substring(dot + 1);
        if (r == null) throw new IllegalArgumentException("없는 방: " + ref);
        if (r.scene == null) return makeTileDoor(r, Door.Side.valueOf(name));
        for (Door d : r.doors) {
            if (!d.name.equals(name)) continue;
            if (d.link != null && !shared) throw new IllegalArgumentException("이미 이어진 출구: " + ref);
            return d;
        }
        throw new IllegalArgumentException("그림에 없는 출구: " + ref);
    }

    private void buildCampProps() {
        Room camp = byId.get("CAMP");
        if (camp.scene != null) {
            for (RoomSceneLayout.Facility f : camp.scene.facilities)
                props.add(new Prop(f.kind, camp.sceneX(f.center[0]), camp.sceneY(f.center[1]),
                        f.widthTiles * tileSize, f.heightTiles * tileSize, tileSize, f.baked));
            // Visible solid bases are already traced in scene.blocked; do not expand to whole tiles.
            return;
        }
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
            case "FINAL": return s.unsealed; // 캠프 아래 계단 ↔ 심연
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

    /**
     * 지나갈 수 없는 칸인가 (벽, 빈 곳, 닫힌 문, 광맥·시설). 투사체·바닥 공격처럼 칸 단위로 보는 곳에서 쓴다.
     * 그림 방은 칸 가운데가 바닥 경계 안이면 바닥이다.
     */
    public boolean solid(int x, int y) {
        int t = tile(x, y);
        if (t == VOID || t == WALL) return true;
        if (t == DOOR && doorAt[idx(x, y)].isClosed()) return true;
        return blocked[idx(x, y)];
    }

    /**
     * 발 사각형(가로 반 hw, 세로 반 hh)이 이 자리에 설 수 있는가.
     * 그림 방에서는 바닥 경계 다각형으로 정확히 보고, 닫힌 출구와 광맥 칸도 막는다.
     */
    public boolean free(float x, float y, float hw, float hh) {
        int T = tileSize;
        int tx0 = (int) Math.floor((x - hw) / T), tx1 = (int) Math.floor((x + hw - 0.01f) / T);
        int ty0 = (int) Math.floor((y - hh) / T), ty1 = (int) Math.floor((y + hh - 0.01f) / T);
        Room r = roomAt((int) Math.floor(x / T), (int) Math.floor(y / T));
        if (r != null && r.scene != null) {
            float radius = Math.max(hw, hh);
            if (!r.allowsFoot(x, y, radius)) return false;
            for (int ty = ty0; ty <= ty1; ty++)
                for (int tx = tx0; tx <= tx1; tx++)
                    if (isBlockedOverlay(tx, ty)) return false;
            for (Door d : r.doors) if (d.isClosed() && d.blocks(x, y, radius)) return false;
            return true;
        }
        for (int ty = ty0; ty <= ty1; ty++)
            for (int tx = tx0; tx <= tx1; tx++)
                if (solid(tx, ty)) return false;
        return true;
    }

    public Prop propAt(int tx, int ty) {
        for (Prop p : props) if (p.covers(tx, ty)) return p;
        return null;
    }
}
