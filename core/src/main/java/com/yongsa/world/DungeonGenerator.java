package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.yongsa.config.Balance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 씨앗값으로 던전 전체를 만든다. 같은 씨앗값은 항상 같은 던전을 만든다(저장 후 다시 불러와도 동일).
 *
 * <pre>
 * 격자(13 x 9칸, 한 칸 = 방 하나):
 *   위쪽 띠(행 6~8)와 가운데 줄기(열 6, 행 1~5) = 봉인의 신전
 *   왼쪽(열 0~5, 행 0~5) = 폐광       오른쪽(열 7~12, 행 0~5) = 지하 요새
 *   (6, 0) = 중앙 캠프
 * </pre>
 * 각 구역은 입구에서 시작하는 본 경로(무작위 경로) 끝에 보스 앞 공간과 보스방을 두고,
 * 본 경로 중간에 곁가지 방을 붙인다. 일반 방은 대부분 몬스터와 광맥이 함께 있고,
 * 일부는 전투만, 일부는 안전한 광맥만 있다.
 */
public final class DungeonGenerator {
    private DungeonGenerator() {}

    public static final int TILE = 32;
    static final int GRID_W = 13, GRID_H = 9;
    static final int CAMP_GX = 6, CAMP_GY = 0;
    static final int MARGIN = 5;
    static final int CORRIDOR = 3;

    /** 생성 중의 방. */
    static class Gen {
        final String id;
        final String zone;
        final RoomDef.Kind kind;
        final int gx, gy;
        Gen parent;
        int depth;
        boolean main;
        boolean enemies, veins;
        int veinCount;
        int x, y, w, h; // 타일

        Gen(String id, String zone, RoomDef.Kind kind, int gx, int gy) {
            this.id = id;
            this.zone = zone;
            this.kind = kind;
            this.gx = gx;
            this.gy = gy;
        }
    }

    // ---- 구역 영역 ----

    static String regionOf(int gx, int gy) {
        if (gx == CAMP_GX && gy == CAMP_GY) return "camp";
        if (gx < 0 || gy < 0 || gx >= GRID_W || gy >= GRID_H) return null;
        if (gx <= 5 && gy <= 5) return "mine";
        if (gx >= 7 && gy <= 5) return "fortress";
        if (gx == 6 && gy >= 1 && gy <= 5) return "temple";
        if (gy >= 6 && gx >= 2 && gx <= 10) return "temple";
        return null;
    }

    static int[] entranceOf(String zone) {
        switch (zone) {
            case "mine": return new int[]{5, 0};
            case "fortress": return new int[]{7, 0};
            default: return new int[]{6, 1};
        }
    }

    // =====================================================================

    public static DungeonMap generate(long seed, Balance b) {
        List<String> last = null;
        for (int attempt = 0; attempt < 200; attempt++) {
            Random rnd = new Random(seed * 1_000_003L + attempt * 7919L);
            DungeonMap map = build(seed, b, rnd);
            if (map == null) continue;
            last = map.validate(b);
            if (last.isEmpty()) return map;
        }
        throw new IllegalStateException("던전을 만들지 못했습니다: " + last);
    }

    static DungeonMap build(long seed, Balance b, Random rnd) {
        Balance.DungeonDef d = b.dungeon;
        int cw = d.cellWidth, ch = d.cellHeight;
        Gen[][] grid = new Gen[GRID_W][GRID_H];
        List<Gen> all = new ArrayList<>();

        Gen camp = new Gen("camp", "camp", RoomDef.Kind.CAMP, CAMP_GX, CAMP_GY);
        grid[CAMP_GX][CAMP_GY] = camp;
        all.add(camp);
        Map<String, List<Gen>> byZone = new LinkedHashMap<>();

        for (Balance.ZoneDef z : b.zones) {
            List<Gen> rooms = layoutZone(z.id, b, rnd, grid);
            if (rooms == null) return null;
            assignContent(rooms, z, b, rnd);
            byZone.put(z.id, rooms);
            all.addAll(rooms);
        }

        // 방 크기와 위치
        for (Gen g : all) {
            switch (g.kind) {
                case CAMP: g.w = 28; g.h = 14; break;
                case BOSS: g.w = 24; g.h = 12; break;
                case ANTE: g.w = 14; g.h = 10; break;
                default:
                    g.w = d.roomMinWidth + rnd.nextInt(d.roomMaxWidth - d.roomMinWidth + 1);
                    g.h = d.roomMinHeight + rnd.nextInt(d.roomMaxHeight - d.roomMinHeight + 1);
                    break;
            }
            int m = g.kind == RoomDef.Kind.CAMP ? (cw - g.w) / 2 : MARGIN;
            int mv = g.kind == RoomDef.Kind.CAMP ? (ch - g.h) / 2 : MARGIN;
            int freeX = cw - 2 * m - g.w, freeY = ch - 2 * mv - g.h;
            g.x = g.gx * cw + m + (freeX > 0 ? rnd.nextInt(freeX + 1) : 0);
            g.y = g.gy * ch + mv + (freeY > 0 ? rnd.nextInt(freeY + 1) : 0);
        }

        DungeonMap map = new DungeonMap(seed, GRID_W * cw, GRID_H * ch, TILE);
        for (Gen g : all) {
            carve(map, g.x, g.y, g.w, g.h, themeOf(g.zone));
            Rectangle r = new Rectangle(g.x * TILE, g.y * TILE, g.w * TILE, g.h * TILE);
            RoomDef room = new RoomDef(g.id, g.zone, g.kind, r);
            room.enemies = g.enemies;
            room.veins = g.veins;
            room.veinCount = g.veinCount;
            room.depth = g.depth;
            room.mainPath = g.main;
            map.rooms.add(room);
            map.roomById.put(room.id, room);
        }
        for (Map.Entry<String, List<Gen>> e : byZone.entrySet()) {
            List<RoomDef> list = new ArrayList<>();
            for (Gen g : e.getValue()) list.add(map.room(g.id));
            map.zoneRooms.put(e.getKey(), list);
        }

        // 통로와 문
        for (Map.Entry<String, List<Gen>> e : byZone.entrySet()) {
            for (Gen g : e.getValue()) {
                Gen from = g.parent != null ? g.parent : camp;
                connect(map, from, g, b, rnd);
            }
        }
        buildWalls(map);

        // 캠프와 각 방의 내용물
        furnishCamp(map, camp);
        for (Map.Entry<String, List<Gen>> e : byZone.entrySet()) {
            for (Gen g : e.getValue()) furnishRoom(map, map.room(g.id), b, rnd);
        }
        return map;
    }

    private static byte themeOf(String zone) {
        switch (zone) {
            case "mine": return 1;
            case "fortress": return 2;
            case "temple": return 3;
            default: return 0;
        }
    }

    // ---- 구역 배치 ----

    /** 본 경로(입구 → … → 보스 앞 → 보스)와 곁가지를 격자에 놓는다. */
    static List<Gen> layoutZone(String zone, Balance b, Random rnd, Gen[][] grid) {
        int mainLen = b.dungeon.mainPathRooms + 2;
        int[] start = entranceOf(zone);
        List<int[]> path = new ArrayList<>();
        path.add(start);
        boolean[][] used = new boolean[GRID_W][GRID_H];
        for (int x = 0; x < GRID_W; x++) for (int y = 0; y < GRID_H; y++) used[x][y] = grid[x][y] != null;
        used[start[0]][start[1]] = true;
        if (!extend(path, used, zone, mainLen, rnd, new int[]{0})) return null;

        List<Gen> rooms = new ArrayList<>();
        Gen prev = null;
        for (int i = 0; i < path.size(); i++) {
            int[] c = path.get(i);
            RoomDef.Kind kind = i == path.size() - 1 ? RoomDef.Kind.BOSS
                    : i == path.size() - 2 ? RoomDef.Kind.ANTE : RoomDef.Kind.ROOM;
            String id = kind == RoomDef.Kind.BOSS ? zone + "_boss" : kind == RoomDef.Kind.ANTE ? zone + "_ante"
                    : String.format("%s_%02d", zone, i);
            Gen g = new Gen(id, zone, kind, c[0], c[1]);
            g.parent = prev;
            g.depth = i;
            g.main = true;
            grid[c[0]][c[1]] = g;
            rooms.add(g);
            prev = g;
        }

        // 곁가지: 본 경로의 일반 방에서 비어 있는 이웃 칸으로 1~2칸
        int added = 0, tries = 0;
        int next = b.dungeon.mainPathRooms;
        while (added < b.dungeon.branchRooms && tries++ < 200) {
            Gen base = rooms.get(1 + rnd.nextInt(b.dungeon.mainPathRooms - 1));
            if (rnd.nextFloat() < 0.35f) {
                // 이미 있는 곁가지를 더 길게
                List<Gen> branches = new ArrayList<>();
                for (Gen r : rooms) if (!r.main) branches.add(r);
                if (!branches.isEmpty()) base = branches.get(rnd.nextInt(branches.size()));
            }
            int[] n = freeNeighbor(base.gx, base.gy, zone, grid, rnd);
            if (n == null) continue;
            Gen g = new Gen(String.format("%s_%02d", zone, next++), zone, RoomDef.Kind.ROOM, n[0], n[1]);
            g.parent = base;
            g.depth = base.depth + 1;
            g.main = false;
            grid[n[0]][n[1]] = g;
            rooms.add(g);
            added++;
        }
        return rooms;
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** 무작위 깊이 우선 탐색으로 길이 len 의 단순 경로를 만든다. 캠프에서 멀어지는 쪽을 선호한다. */
    static boolean extend(List<int[]> path, boolean[][] used, String zone, int len, Random rnd, int[] budget) {
        if (path.size() == len) return true;
        if (++budget[0] > 4000) return false;
        int[] c = path.get(path.size() - 1);
        List<int[]> opts = new ArrayList<>();
        for (int[] dd : DIRS) {
            int nx = c[0] + dd[0], ny = c[1] + dd[1];
            if (!zone.equals(regionOf(nx, ny)) || used[nx][ny]) continue;
            opts.add(new int[]{nx, ny});
        }
        Collections.shuffle(opts, rnd);
        int cd = dist(c);
        opts.sort((p, q) -> Boolean.compare(dist(q) > cd && rnd.nextFloat() < 0.75f, dist(p) > cd && rnd.nextFloat() < 0.75f));
        for (int[] o : opts) {
            used[o[0]][o[1]] = true;
            path.add(o);
            if (extend(path, used, zone, len, rnd, budget)) return true;
            path.remove(path.size() - 1);
            used[o[0]][o[1]] = false;
        }
        return false;
    }

    private static int dist(int[] c) {
        return Math.abs(c[0] - CAMP_GX) + Math.abs(c[1] - CAMP_GY);
    }

    private static int[] freeNeighbor(int gx, int gy, String zone, Gen[][] grid, Random rnd) {
        List<int[]> opts = new ArrayList<>();
        for (int[] dd : DIRS) {
            int nx = gx + dd[0], ny = gy + dd[1];
            if (zone.equals(regionOf(nx, ny)) && grid[nx][ny] == null) opts.add(new int[]{nx, ny});
        }
        return opts.isEmpty() ? null : opts.get(rnd.nextInt(opts.size()));
    }

    /** 방 성격: 몬스터+광맥(기본), 전투만, 광맥만(안전). */
    static void assignContent(List<Gen> rooms, Balance.ZoneDef z, Balance b, Random rnd) {
        Balance.DungeonDef d = b.dungeon;
        boolean firstZone = z == b.zones[0];
        Gen prev = null;
        for (Gen g : rooms) {
            if (g.kind != RoomDef.Kind.ROOM) {
                prev = g;
                continue;
            }
            if (g.main && g.depth == 0 && firstZone) {
                // 무기 없이도 재료를 모을 수 있는 첫 광물방
                g.enemies = false;
                g.veins = true;
                g.veinCount = b.mining.oreRoomVeins;
                continue;
            }
            float roll = rnd.nextFloat();
            boolean lastBeforeAnte = g.main && g.depth == d.mainPathRooms - 1;
            boolean prevSafe = prev != null && prev.kind == RoomDef.Kind.ROOM && !prev.enemies;
            if (g.main) {
                if (!lastBeforeAnte && !prevSafe && g.depth >= 2 && roll < d.oreOnlyChance) {
                    g.enemies = false;
                    g.veins = true;
                    g.veinCount = b.mining.oreRoomVeins;
                } else if (roll < d.oreOnlyChance + d.battleOnlyChance) {
                    g.enemies = true;
                    g.veins = false;
                } else {
                    g.enemies = true;
                    g.veins = true;
                    g.veinCount = b.mining.mixedRoomVeins;
                }
            } else {
                // 곁가지는 광맥이 넉넉한 보상 방
                if (roll < d.branchOreChance) {
                    g.enemies = false;
                    g.veins = true;
                    g.veinCount = b.mining.oreRoomVeins;
                } else {
                    g.enemies = true;
                    g.veins = true;
                    g.veinCount = b.mining.mixedRoomVeins + 1;
                }
            }
            if (g.main) prev = g;
        }
    }

    // ---- 바닥 ----

    private static void carve(DungeonMap m, int x, int y, int w, int h, byte theme) {
        for (int ty = y; ty < y + h; ty++) {
            for (int tx = x; tx < x + w; tx++) {
                if (tx < 0 || ty < 0 || tx >= m.widthTiles || ty >= m.heightTiles) continue;
                int i = ty * m.widthTiles + tx;
                if (m.cells[i] != DungeonMap.FLOOR) m.theme[i] = theme;
                m.cells[i] = DungeonMap.FLOOR;
            }
        }
    }

    private static void buildWalls(DungeonMap m) {
        int w = m.widthTiles, h = m.heightTiles;
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < w; tx++) {
                if (m.cells[ty * w + tx] == DungeonMap.FLOOR) continue;
                byte t = -1;
                for (int dy = -3; dy <= 3 && t < 0; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int x = tx + dx, y = ty + dy;
                        if (x < 0 || y < 0 || x >= w || y >= h) continue;
                        if (m.cells[y * w + x] == DungeonMap.FLOOR) {
                            t = m.theme[y * w + x];
                            break;
                        }
                    }
                }
                if (t >= 0) {
                    m.cells[ty * w + tx] = DungeonMap.WALL;
                    m.theme[ty * w + tx] = t;
                }
            }
        }
    }

    /**
     * 두 방을 폭 3칸 통로로 잇는다. 범위가 겹치면 직선, 아니면 칸 경계에서 꺾는다.
     * 각 방 쪽 통로 입구에 문을 하나씩 둔다.
     */
    static void connect(DungeonMap m, Gen a, Gen b, Balance bal, Random rnd) {
        int cw = bal.dungeon.cellWidth, ch = bal.dungeon.cellHeight;
        byte theme = themeOf(b.zone);
        String edge = a.id + "-" + b.id;
        if (a.gy == b.gy) {
            Gen l = a.gx < b.gx ? a : b, r = a.gx < b.gx ? b : a;
            int ly = l.kind == RoomDef.Kind.CAMP ? l.y + 5 : corridorPos(l, true, rnd);
            int ry = r.kind == RoomDef.Kind.CAMP ? r.y + 5 : corridorPos(r, true, rnd);
            if (l.kind == RoomDef.Kind.CAMP && ly >= r.y + 2 && ly <= r.y + r.h - 5) ry = ly;
            else if (r.kind == RoomDef.Kind.CAMP && ry >= l.y + 2 && ry <= l.y + l.h - 5) ly = ry;
            else if (l.kind != RoomDef.Kind.CAMP && r.kind != RoomDef.Kind.CAMP) {
                int lo = Math.max(l.y + 2, r.y + 2), hi = Math.min(l.y + l.h - 5, r.y + r.h - 5);
                if (lo <= hi) ly = ry = lo + rnd.nextInt(hi - lo + 1);
            }
            int x0 = l.x + l.w, x1 = r.x; // [x0, x1)
            int bx = r.gx * cw - 1; // 칸 경계 근처에서 꺾는다
            if (ly == ry) {
                carve(m, x0, ly, x1 - x0, CORRIDOR, theme);
            } else {
                carve(m, x0, ly, bx + CORRIDOR - x0, CORRIDOR, theme);
                carve(m, bx, Math.min(ly, ry), CORRIDOR, Math.abs(ly - ry) + CORRIDOR, theme);
                carve(m, bx, ry, x1 - bx, CORRIDOR, theme);
            }
            addDoor(m, edge + "-L", l, new Rectangle(x0 * TILE, ly * TILE, TILE, CORRIDOR * TILE), r);
            addDoor(m, edge + "-R", r, new Rectangle((x1 - 1) * TILE, ry * TILE, TILE, CORRIDOR * TILE), l);
        } else {
            Gen lo = a.gy < b.gy ? a : b, hiR = a.gy < b.gy ? b : a;
            int lx = corridorPos(lo, false, rnd), hx = corridorPos(hiR, false, rnd);
            if (lo.kind == RoomDef.Kind.CAMP) lx = lo.x + 12;
            int mn = Math.max(lo.x + 2, hiR.x + 2), mx = Math.min(lo.x + lo.w - 5, hiR.x + hiR.w - 5);
            if (lo.kind == RoomDef.Kind.CAMP) {
                if (lx >= hiR.x + 2 && lx <= hiR.x + hiR.w - 5) hx = lx;
            } else if (mn <= mx) {
                lx = hx = mn + rnd.nextInt(mx - mn + 1);
            }
            int y0 = lo.y + lo.h, y1 = hiR.y;
            int by = hiR.gy * ch - 1;
            if (lx == hx) {
                carve(m, lx, y0, CORRIDOR, y1 - y0, theme);
            } else {
                carve(m, lx, y0, CORRIDOR, by + CORRIDOR - y0, theme);
                carve(m, Math.min(lx, hx), by, Math.abs(lx - hx) + CORRIDOR, CORRIDOR, theme);
                carve(m, hx, by, CORRIDOR, y1 - by, theme);
            }
            addDoor(m, edge + "-D", lo, new Rectangle(lx * TILE, y0 * TILE, CORRIDOR * TILE, TILE), hiR);
            addDoor(m, edge + "-U", hiR, new Rectangle(hx * TILE, (y1 - 1) * TILE, CORRIDOR * TILE, TILE), lo);
        }
    }

    /** 방의 변 위에서 통로가 붙을 위치 (모서리를 피한다). */
    private static int corridorPos(Gen g, boolean horizontalCorridor, Random rnd) {
        if (horizontalCorridor) return g.y + 2 + rnd.nextInt(Math.max(1, g.h - 6));
        return g.x + 2 + rnd.nextInt(Math.max(1, g.w - 6));
    }

    /** room 쪽 입구의 문. 캠프 쪽 문은 구역 봉인, 적이 있는 방 쪽 문은 무기 필요 규칙을 갖는다. */
    private static void addDoor(DungeonMap m, String id, Gen room, Rectangle r, Gen other) {
        String rule = "";
        String combat = "";
        if (room.kind == RoomDef.Kind.CAMP) {
            if (!"mine".equals(other.zone)) rule = "zone:" + other.zone;
        } else {
            combat = room.id;
            if (room.enemies || room.kind == RoomDef.Kind.BOSS) rule = "weapon";
        }
        m.doors.add(new DoorDef(id, r, rule, combat));
    }

    // ---- 캠프 ----

    static void furnishCamp(DungeonMap m, Gen c) {
        float cx = c.x * TILE, cy = c.y * TILE;
        m.points.put("camp_spawn", new Vector2(cx + 14 * TILE, cy + 4.5f * TILE));
        // 모닥불 (중앙)
        decor(m, "campfire", cx + 14 * TILE, cy + 7 * TILE);
        m.solids.add(new Rectangle(cx + 14 * TILE - 20, cy + 7 * TILE - 10, 40, 22));
        decor(m, "rug", cx + 14 * TILE, cy + 6 * TILE);
        // 침대 (왼쪽)
        float bx = cx + 9.5f * TILE, by = cy + 6.5f * TILE;
        interact(m, "bed", "bed", new Rectangle(bx - 30, by - 22, 60, 44), null);
        m.solids.add(new Rectangle(bx - 28, by - 12, 56, 26));
        decor(m, "bedroll", bx, by);
        decor(m, "lantern_table", cx + 9.5f * TILE, cy + 9.2f * TILE);
        m.solids.add(new Rectangle(cx + 9.5f * TILE - 16, cy + 9.2f * TILE - 8, 32, 14));
        decor(m, "crate", cx + 7 * TILE, cy + 8.6f * TILE);
        m.solids.add(new Rectangle(cx + 7 * TILE - 12, cy + 8.6f * TILE - 8, 24, 14));
        decor(m, "barrel", cx + 6.2f * TILE, cy + 7.6f * TILE);
        m.solids.add(new Rectangle(cx + 6.2f * TILE - 10, cy + 7.6f * TILE - 6, 20, 12));
        // 대장간 (오른쪽)
        float fx = cx + 18.5f * TILE, fy = cy + 6.5f * TILE;
        interact(m, "forge", "forge", new Rectangle(fx - 40, fy - 28, 80, 60), null);
        decor(m, "furnace", cx + 18 * TILE, cy + 8.8f * TILE);
        m.solids.add(new Rectangle(cx + 17 * TILE, cy + 8.3f * TILE, 64, 32));
        decor(m, "anvil", fx, fy - 4);
        m.solids.add(new Rectangle(fx - 16, fy - 10, 32, 16));
        decor(m, "workbench", cx + 21 * TILE, cy + 7.8f * TILE);
        m.solids.add(new Rectangle(cx + 20 * TILE - 8, cy + 7.4f * TILE, 48, 22));
        decor(m, "weapon_rack", cx + 22.6f * TILE, cy + 9.6f * TILE);
        // 기둥과 화로
        for (float px : new float[]{3, 25}) {
            for (float py : new float[]{3, 11}) {
                decor(m, "pillar", cx + px * TILE, cy + py * TILE);
                m.solids.add(new Rectangle(cx + px * TILE - 14, cy + py * TILE - 8, 28, 16));
            }
        }
        decor(m, "brazier", cx + 11 * TILE, cy + 12.6f * TILE);
        decor(m, "brazier", cx + 17 * TILE, cy + 12.6f * TILE);
        decor(m, "banner", cx + 6 * TILE, (c.y + c.h) * TILE + 4);
        decor(m, "banner", cx + 22 * TILE, (c.y + c.h) * TILE + 4);
        torches(m, roomRect(c), "warm", new ArrayList<>());

        // 지름길 문: 각 통로 옆 벽면
        addShortcut(m, "mine", new Rectangle(cx + 4, cy + 10.5f * TILE, 40, 56), new Vector2(cx + 40, cy + 10 * TILE));
        addShortcut(m, "fortress", new Rectangle(cx + 28 * TILE - 44, cy + 10.5f * TILE, 40, 56),
                new Vector2(cx + 28 * TILE - 40, cy + 10 * TILE));
        addShortcut(m, "temple", new Rectangle(cx + 8 * TILE, cy + 14 * TILE - 42, 56, 40),
                new Vector2(cx + 9 * TILE, cy + 12.2f * TILE));
    }

    private static void addShortcut(DungeonMap m, String zone, Rectangle r, Vector2 arrival) {
        Map<String, String> p = new HashMap<>();
        p.put("zone", zone);
        m.interacts.add(new MapObject("shortcut_" + zone, "shortcut", r, p));
        m.points.put("shortcut_" + zone + "_camp", arrival);
    }

    private static Rectangle roomRect(Gen g) {
        return new Rectangle(g.x * TILE, g.y * TILE, g.w * TILE, g.h * TILE);
    }

    // ---- 방 꾸미기 ----

    static void furnishRoom(DungeonMap m, RoomDef room, Balance b, Random rnd) {
        Rectangle r = room.bounds;
        List<DoorDef> doors = m.doorsOf(room.id);
        List<Vector2> doorCenters = new ArrayList<>();
        for (DoorDef d : doors) doorCenters.add(new Vector2(d.bounds.x + d.bounds.width / 2, d.bounds.y + d.bounds.height / 2));
        List<Vector2> taken = new ArrayList<>();
        String torch = room.zone.equals("fortress") ? "blue" : room.zone.equals("temple") ? "violet" : "warm";
        torches(m, r, torch, doorCenters);

        if (room.kind == RoomDef.Kind.ANTE) {
            // 귀환문: 문에서 가장 먼 모서리
            Vector2 best = null;
            float bestD = -1;
            for (float[] c : new float[][]{{1.6f, 1.6f}, {1.6f, -1.6f}, {-1.6f, 1.6f}, {-1.6f, -1.6f}}) {
                float x = c[0] > 0 ? r.x + c[0] * TILE : r.x + r.width + c[0] * TILE;
                float y = c[1] > 0 ? r.y + c[1] * TILE : r.y + r.height + c[1] * TILE;
                float dmin = Float.MAX_VALUE;
                for (Vector2 dc : doorCenters) dmin = Math.min(dmin, dc.dst(x, y));
                if (dmin > bestD) {
                    bestD = dmin;
                    best = new Vector2(x, y);
                }
            }
            Map<String, String> p = new HashMap<>();
            p.put("zone", room.zone);
            m.interacts.add(new MapObject("return_" + room.zone, "return",
                    new Rectangle(best.x - 24, best.y - 24, 48, 48), p));
            Vector2 toCenter = new Vector2(room.centerX() - best.x, room.centerY() - best.y).nor().scl(2.2f * TILE);
            m.points.put("shortcut_" + room.zone + "_ante", new Vector2(best.x + toCenter.x, best.y + toCenter.y));
            taken.add(best);
            decor(m, "brazier", room.centerX(), r.y + r.height - 1.2f * TILE);
            return;
        }
        if (room.kind == RoomDef.Kind.BOSS) {
            // 큰 장애물 없이 모서리에 화로만
            decor(m, "brazier", r.x + 1.4f * TILE, r.y + r.height - 1.4f * TILE);
            decor(m, "brazier", r.x + r.width - 1.4f * TILE, r.y + r.height - 1.4f * TILE);
            decor(m, "brazier", r.x + 1.4f * TILE, r.y + 1.4f * TILE);
            decor(m, "brazier", r.x + r.width - 1.4f * TILE, r.y + 1.4f * TILE);
            decor(m, "boss_seal", room.centerX(), room.centerY());
            Map<String, String> p = new HashMap<>();
            p.put("room", room.id);
            p.put("enemy", b.zone(room.zone).boss);
            m.spawns.add(new MapObject(room.id + "_boss", "spawn",
                    new Rectangle(room.centerX(), r.y + r.height - 3.5f * TILE, 0, 0), p));
            return;
        }

        // 광맥 후보: 위·아래 벽을 따라, 문 앞은 피한다
        if (room.veins) {
            List<Vector2> cands = new ArrayList<>();
            int cols = (int) (r.width / TILE);
            for (int i = 0; i < cols - 2; i += 2) {
                float x = r.x + (1.6f + i) * TILE;
                cands.add(new Vector2(x, r.y + r.height - 1.25f * TILE));
                cands.add(new Vector2(x, r.y + 1.15f * TILE));
            }
            for (float fy : new float[]{0.4f, 0.65f}) {
                cands.add(new Vector2(r.x + 1.3f * TILE, r.y + r.height * fy));
                cands.add(new Vector2(r.x + r.width - 1.3f * TILE, r.y + r.height * fy));
            }
            Collections.shuffle(cands, rnd);
            int n = 0;
            for (Vector2 c : cands) {
                if (nearAny(c, doorCenters, 3.6f * TILE) || nearAny(c, taken, 1.3f * TILE)) continue;
                if (m.overlapsStatic(VeinGeometry.solidRect(c.x, c.y))) continue;
                Map<String, String> p = new HashMap<>();
                p.put("room", room.id);
                p.put("visible", "true");
                m.veins.add(new MapObject(String.format("%s_v%02d", room.id, n++), "vein",
                        new Rectangle(c.x, c.y, 0, 0), p));
                taken.add(c);
                if (n >= Math.max(b.mining.minCandidates, room.veinCount + 4)) break;
            }
        }

        // 장식물: 벽 가까이에만 (전투방 가운데는 비워 둔다)
        String[] props = propsFor(room.zone);
        int count = 3 + rnd.nextInt(4);
        for (int i = 0, tries = 0; i < count && tries < 80; tries++) {
            String type = props[rnd.nextInt(props.length)];
            Vector2 p = wallSpot(r, rnd);
            if (nearAny(p, doorCenters, 3.2f * TILE) || nearAny(p, taken, 1.4f * TILE)) continue;
            decor(m, type, p.x, p.y);
            if (isSolidProp(type)) m.solids.add(new Rectangle(p.x - 12, p.y - 6, 24, 12));
            taken.add(p);
            i++;
        }
        // 구석 거미줄
        if (rnd.nextFloat() < 0.6f) decor(m, "cobweb", r.x + 14, r.y + r.height - 14);
        if (rnd.nextFloat() < 0.6f) decor(m, "cobweb_r", r.x + r.width - 14, r.y + r.height - 14);

        // 적 생성점: 모든 문에서 떨어진 곳
        if (room.enemies) {
            Balance.DungeonDef d = b.dungeon;
            Balance.ZoneDef z = b.zone(room.zone);
            int n = d.minEnemies + (room.depth >= 4 ? 1 : 0) + (room.depth >= 8 ? 1 : 0) + (rnd.nextFloat() < 0.3f ? 1 : 0);
            if (!room.mainPath) n = d.minEnemies + rnd.nextInt(2);
            n = Math.min(d.maxEnemies, n);
            if (room.depth <= 1 && "mine".equals(room.zone)) n = d.minEnemies; // 첫 전투는 가볍게
            List<Vector2> spawns = new ArrayList<>();
            for (int tries = 0; spawns.size() < n && tries < 400; tries++) {
                float x = r.x + (2 + rnd.nextFloat() * (r.width / TILE - 4)) * TILE;
                float y = r.y + (2 + rnd.nextFloat() * (r.height / TILE - 4)) * TILE;
                Vector2 p = new Vector2(x, y);
                float minDoor = tries < 300 ? 5.5f * TILE : 4f * TILE;
                if (nearAny(p, doorCenters, minDoor) || nearAny(p, spawns, 1.8f * TILE) || nearAny(p, taken, 1.2f * TILE)) continue;
                if (m.overlapsStatic(new Rectangle(x - 14, y - 8, 28, 16))) continue;
                spawns.add(p);
            }
            for (int i = 0; i < spawns.size(); i++) {
                Vector2 p = spawns.get(i);
                Map<String, String> pr = new HashMap<>();
                pr.put("room", room.id);
                pr.put("enemy", z.enemyTable[rnd.nextInt(z.enemyTable.length)]);
                m.spawns.add(new MapObject(room.id + "_s" + i, "spawn", new Rectangle(p.x, p.y, 0, 0), pr));
            }
        }
    }

    private static String[] propsFor(String zone) {
        switch (zone) {
            case "mine": return new String[]{"barrel", "crate", "rubble", "bones", "minecart", "pot", "crate"};
            case "fortress": return new String[]{"barrel", "crate", "weapon_rack", "bones", "skull", "pot", "rubble"};
            default: return new String[]{"pot", "candles", "statue", "bones", "skull", "rubble", "pot"};
        }
    }

    private static boolean isSolidProp(String type) {
        switch (type) {
            case "barrel":
            case "crate":
            case "pot":
            case "minecart":
            case "statue":
            case "weapon_rack":
                return true;
            default:
                return false;
        }
    }

    /** 벽에서 한 칸 안쪽의 무작위 위치. */
    private static Vector2 wallSpot(Rectangle r, Random rnd) {
        int side = rnd.nextInt(4);
        float t = 0.08f + rnd.nextFloat() * 0.84f;
        switch (side) {
            case 0: return new Vector2(r.x + r.width * t, r.y + r.height - 0.85f * TILE);
            case 1: return new Vector2(r.x + r.width * t, r.y + 0.7f * TILE);
            case 2: return new Vector2(r.x + 0.8f * TILE, r.y + r.height * t);
            default: return new Vector2(r.x + r.width - 0.8f * TILE, r.y + r.height * t);
        }
    }

    /** 위쪽 벽면에 일정 간격으로 횃불. 통로 입구는 피한다. */
    private static void torches(DungeonMap m, Rectangle r, String color, List<Vector2> doors) {
        float top = r.y + r.height;
        for (float x = r.x + 2.5f * TILE; x < r.x + r.width - 2 * TILE; x += 5 * TILE) {
            Vector2 p = new Vector2(x, top);
            if (nearAny(p, doors, 2.8f * TILE)) continue;
            decor(m, "torch_" + color, x, top + 22);
        }
    }

    private static boolean nearAny(Vector2 p, List<Vector2> list, float dist) {
        for (Vector2 q : list) if (q.dst2(p) < dist * dist) return true;
        return false;
    }

    private static void decor(DungeonMap m, String type, float x, float y) {
        m.decor.add(new MapObject("decor_" + m.decor.size(), type, new Rectangle(x, y, 0, 0), null));
    }

    private static void interact(DungeonMap m, String name, String type, Rectangle r, Map<String, String> props) {
        m.interacts.add(new MapObject(name, type, r, props));
    }
}
