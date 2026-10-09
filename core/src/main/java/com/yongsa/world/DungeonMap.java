package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.yongsa.config.Balance;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 던전 한 판의 공간 정보. DungeonGenerator 가 씨앗값으로 만든다.
 * 그래픽 없이 동작하며, 좌표는 y 가 위로 증가하는 월드 픽셀이다.
 */
public class DungeonMap {
    public static final byte VOID = 0, FLOOR = 1, WALL = 2;

    public final long seed;
    public final int widthTiles;
    public final int heightTiles;
    public final int tileSize;
    public final float widthPx;
    public final float heightPx;

    /** [ty * width + tx] 칸 종류 (VOID/FLOOR/WALL). */
    final byte[] cells;
    /** [ty * width + tx] 칸의 꾸밈 테마 (0 캠프, 1 폐광, 2 요새, 3 신전). */
    final byte[] theme;

    public final List<RoomDef> rooms = new ArrayList<>();
    public final Map<String, RoomDef> roomById = new HashMap<>();
    public final List<DoorDef> doors = new ArrayList<>();
    public final List<MapObject> spawns = new ArrayList<>();
    public final List<MapObject> veins = new ArrayList<>();
    public final Map<String, Vector2> points = new HashMap<>();
    public final List<MapObject> interacts = new ArrayList<>();
    public final List<Rectangle> solids = new ArrayList<>();
    public final List<MapObject> decor = new ArrayList<>();
    /** 구역별 방 순서 (본 경로 순서 → 곁가지). */
    public final Map<String, List<RoomDef>> zoneRooms = new HashMap<>();

    public DungeonMap(long seed, int w, int h, int tile) {
        this.seed = seed;
        widthTiles = w;
        heightTiles = h;
        tileSize = tile;
        widthPx = w * tile;
        heightPx = h * tile;
        cells = new byte[w * h];
        theme = new byte[w * h];
    }

    // ---- 타일 ----

    public byte cell(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= widthTiles || ty >= heightTiles) return VOID;
        return cells[ty * widthTiles + tx];
    }

    public int theme(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= widthTiles || ty >= heightTiles) return 0;
        return theme[ty * widthTiles + tx];
    }

    public boolean isFloor(int tx, int ty) {
        return cell(tx, ty) == FLOOR;
    }

    public boolean isSolidTile(int tx, int ty) {
        return cell(tx, ty) != FLOOR;
    }

    public boolean isSolidAt(float x, float y) {
        return isSolidTile((int) Math.floor(x / tileSize), (int) Math.floor(y / tileSize));
    }

    /** 사각형이 고체 타일이나 고정 장애물과 겹치는지. */
    public boolean overlapsStatic(Rectangle r) {
        int x0 = (int) Math.floor(r.x / tileSize);
        int x1 = (int) Math.floor((r.x + r.width - 0.001f) / tileSize);
        int y0 = (int) Math.floor(r.y / tileSize);
        int y1 = (int) Math.floor((r.y + r.height - 0.001f) / tileSize);
        for (int ty = y0; ty <= y1; ty++)
            for (int tx = x0; tx <= x1; tx++)
                if (isSolidTile(tx, ty)) return true;
        for (Rectangle s : solids) if (s.overlaps(r)) return true;
        return false;
    }

    // ---- 질의 ----

    public RoomDef roomAt(float x, float y) {
        for (RoomDef r : rooms) if (r.bounds.contains(x, y)) return r;
        return null;
    }

    public RoomDef room(String id) {
        return roomById.get(id);
    }

    public List<RoomDef> zoneRooms(String zone) {
        List<RoomDef> l = zoneRooms.get(zone);
        return l == null ? new ArrayList<>() : l;
    }

    /** 구역 본 경로의 첫 전투방. */
    public RoomDef firstCombatRoom(String zone) {
        RoomDef best = null;
        for (RoomDef r : zoneRooms(zone)) {
            if (r.mainPath && r.kind == RoomDef.Kind.ROOM && r.enemies && (best == null || r.depth < best.depth)) best = r;
        }
        return best;
    }

    public RoomDef entranceRoom(String zone) {
        for (RoomDef r : zoneRooms(zone)) if (r.mainPath && r.depth == 0) return r;
        return null;
    }

    public Vector2 point(String name) {
        Vector2 p = points.get(name);
        if (p == null) throw new IllegalStateException("맵에 점 오브젝트가 없습니다: " + name);
        return p;
    }

    public List<MapObject> veinCandidates(String roomId) {
        List<MapObject> out = new ArrayList<>();
        for (MapObject v : veins) if (roomId.equals(v.prop("room"))) out.add(v);
        return out;
    }

    private final Map<String, MapObject> veinIndex = new HashMap<>();

    public MapObject vein(String candidateId) {
        if (veinIndex.size() != veins.size()) {
            veinIndex.clear();
            for (MapObject v : veins) veinIndex.put(v.name, v);
        }
        return veinIndex.get(candidateId);
    }

    public MapObject interact(String name) {
        for (MapObject o : interacts) if (o.name.equals(name)) return o;
        return null;
    }

    public List<MapObject> spawnsIn(String roomId) {
        List<MapObject> out = new ArrayList<>();
        for (MapObject s : spawns) if (roomId.equals(s.prop("room"))) out.add(s);
        return out;
    }

    public DoorDef door(String id) {
        for (DoorDef d : doors) if (d.id.equals(id)) return d;
        return null;
    }

    public List<DoorDef> doorsOf(String roomId) {
        List<DoorDef> out = new ArrayList<>();
        for (DoorDef d : doors) if (d.combatRooms.contains(roomId)) out.add(d);
        return out;
    }

    /**
     * 개발 검증. 문제 목록을 돌려준다(비어 있으면 통과).
     */
    public List<String> validate(Balance b) {
        List<String> errors = new ArrayList<>();
        Vector2 sp = points.get("camp_spawn");
        if (sp == null) {
            errors.add("camp_spawn 없음");
            return errors;
        }
        RoomDef camp = roomAt(sp.x, sp.y);
        if (camp == null || camp.kind != RoomDef.Kind.CAMP) errors.add("camp_spawn 이 중앙 캠프 밖에 있음");
        Rectangle around = new Rectangle(sp.x - tileSize * 1.5f, sp.y - tileSize * 1.5f, tileSize * 3, tileSize * 3);
        if (overlapsStatic(around)) errors.add("camp_spawn 주변 한 타일 안에 고체 오브젝트가 있음");

        for (int i = 0; i < rooms.size(); i++) {
            for (int j = i + 1; j < rooms.size(); j++) {
                if (rooms.get(i).bounds.overlaps(rooms.get(j).bounds)) errors.add("방이 겹침: " + rooms.get(i).id + ", " + rooms.get(j).id);
            }
        }
        boolean[] reach = reachableFrom(sp.x, sp.y);
        for (Balance.ZoneDef z : b.zones) {
            List<RoomDef> zr = zoneRooms(z.id);
            int bosses = 0, antes = 0;
            for (RoomDef r : zr) {
                if (r.kind == RoomDef.Kind.BOSS) bosses++;
                if (r.kind == RoomDef.Kind.ANTE) antes++;
                int tx = (int) (r.centerX() / tileSize), ty = (int) (r.centerY() / tileSize);
                if (!reach[ty * widthTiles + tx]) errors.add("캠프에서 갈 수 없는 방: " + r.id);
                if (r.veins) {
                    int c = veinCandidates(r.id).size();
                    if (c < r.veinCount + 2) errors.add("광맥 후보 부족: " + r.id + " " + c);
                    for (MapObject v : veinCandidates(r.id)) {
                        if (overlapsStatic(VeinGeometry.solidRect(v.x(), v.y()))) errors.add("광맥 후보가 고체와 겹침: " + v.name);
                    }
                }
                if (r.enemies && spawnsIn(r.id).isEmpty()) errors.add("적 생성점 없음: " + r.id);
                if (r.kind == RoomDef.Kind.BOSS && (r.bounds.width > 24 * tileSize || r.bounds.height > 12 * tileSize))
                    errors.add("보스방이 24x12 타일보다 큼: " + r.id);
            }
            if (bosses != 1 || antes != 1) errors.add(z.id + " 보스방/보스 앞 공간 수 오류");
            RoomDef entrance = entranceRoom(z.id);
            if (entrance == null) errors.add(z.id + " 입구 방 없음");
            for (String p : new String[]{"shortcut_" + z.id + "_camp", "shortcut_" + z.id + "_ante"}) {
                if (!points.containsKey(p)) errors.add("점 없음: " + p);
            }
        }
        // 첫 구역 입구는 무기 없이 재료를 모을 수 있는 안전한 광물방
        RoomDef first = entranceRoom(b.zones[0].id);
        if (first != null) {
            if (first.enemies || !first.veins) errors.add("첫 구역 입구가 안전한 광물방이 아님");
            else if (veinCandidates(first.id).size() < b.mining.minCandidates) errors.add("첫 광물방 후보 부족");
            else if (first.veinCount * b.zones[0].veinYield < b.weapon("sword").craftCost)
                errors.add("첫 광물방만으로 철검을 만들 수 없음");
        }
        return errors;
    }

    /** 바닥 타일을 따라 (문을 모두 연 상태로) 갈 수 있는 칸. */
    public boolean[] reachableFrom(float x, float y) {
        boolean[] seen = new boolean[widthTiles * heightTiles];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int start = (int) (y / tileSize) * widthTiles + (int) (x / tileSize);
        seen[start] = true;
        q.add(start);
        while (!q.isEmpty()) {
            int c = q.poll();
            int cx = c % widthTiles, cy = c / widthTiles;
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] o : d) {
                int nx = cx + o[0], ny = cy + o[1];
                if (!isFloor(nx, ny)) continue;
                int n = ny * widthTiles + nx;
                if (seen[n]) continue;
                seen[n] = true;
                q.add(n);
            }
        }
        return seen;
    }
}
