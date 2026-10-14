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
    static final int GRID_W = 17, GRID_H = 13;
    static final int CAMP_GX = 8, CAMP_GY = 6;
    static final int MARGIN = 2;
    static final int CORRIDOR = 2;

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
        boolean shrine;
        int x, y, w, h; // 타일

        Gen(String id, String zone, RoomDef.Kind kind, int gx, int gy) {
            this.id = id;
            this.zone = zone;
            this.kind = kind;
            this.gx = gx;
            this.gy = gy;
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

        Map<String, List<Gen>> laid = layoutAll(b, rnd, grid);
        if (laid == null) return null;
        for (Balance.ZoneDef z : b.zones) {
            List<Gen> rooms = laid.get(z.id);
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
            carve(map, g.x, g.y, g.w, g.h, g.kind == RoomDef.Kind.BOSS ? (byte) (themeOf(g.zone) + 3) : themeOf(g.zone));
            Rectangle r = new Rectangle(g.x * TILE, g.y * TILE, g.w * TILE, g.h * TILE);
            RoomDef room = new RoomDef(g.id, g.zone, g.kind, r);
            room.enemies = g.enemies;
            room.veins = g.veins;
            room.veinCount = g.veinCount;
            room.depth = g.depth;
            room.mainPath = g.main;
            if (g.shrine) {
                room.feature = "shrine";
                room.guard = g.enemies;
            }
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

    // ---- 배치 (아이작 방식) ----

    /** 캠프 옆의 구역 입구 칸: 폐광 왼쪽, 요새 오른쪽, 신전 위쪽. */
    static int[] entranceOf(String zone) {
        switch (zone) {
            case "mine": return new int[]{CAMP_GX - 1, CAMP_GY};
            case "fortress": return new int[]{CAMP_GX + 1, CAMP_GY};
            default: return new int[]{CAMP_GX, CAMP_GY + 1};
        }
    }

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static boolean inGrid(int x, int y) {
        return x >= 1 && y >= 1 && x < GRID_W - 1 && y < GRID_H - 1;
    }

    private static boolean nextToCamp(int x, int y) {
        return Math.abs(x - CAMP_GX) + Math.abs(y - CAMP_GY) == 1;
    }

    /**
     * 세 구역을 캠프 둘레에 번갈아 키운다. 새 방은 같은 구역 방 하나에만 맞닿게 해서(고리 없음) 갈래가 생기고,
     * 서로 다른 구역 방은 벽을 사이에 두고 붙을 수 있어 전체가 한 덩어리로 모인다.
     * 그다음 각 구역에서 가장 깊은 막다른 방 뒤에 보스 앞 공간과 보스방을 붙인다.
     */
    static Map<String, List<Gen>> layoutAll(Balance b, Random rnd, Gen[][] grid) {
        int rooms = b.dungeon.mainPathRooms + b.dungeon.branchRooms; // 일반 방 수
        Map<String, List<Gen>> byZone = new LinkedHashMap<>();
        Map<String, Integer> counter = new HashMap<>();
        for (Balance.ZoneDef z : b.zones) {
            int[] e = entranceOf(z.id);
            Gen g = new Gen(String.format("%s_%02d", z.id, 0), z.id, RoomDef.Kind.ROOM, e[0], e[1]);
            g.depth = 0;
            grid[e[0]][e[1]] = g;
            List<Gen> list = new ArrayList<>();
            list.add(g);
            byZone.put(z.id, list);
            counter.put(z.id, 1);
        }
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Balance.ZoneDef z : b.zones) {
                List<Gen> list = byZone.get(z.id);
                if (list.size() >= rooms) continue;
                List<int[]> cands = new ArrayList<>();
                List<Gen> parents = new ArrayList<>();
                List<Float> weights = new ArrayList<>();
                for (Gen p : list) {
                    for (int[] d : DIRS) {
                        int x = p.gx + d[0], y = p.gy + d[1];
                        if (!inGrid(x, y) || grid[x][y] != null || nextToCamp(x, y)) continue;
                        int own = 0, total = 0;
                        for (int[] d2 : DIRS) {
                            Gen n = gridAt(grid, x + d2[0], y + d2[1]);
                            if (n == null) continue;
                            total++;
                            if (n.zone.equals(z.id)) own++;
                        }
                        if (own != 1) continue;
                        cands.add(new int[]{x, y});
                        parents.add(p);
                        // 이웃이 적을수록, 너무 깊지 않을수록 선호 → 덩어리지면서도 갈래가 생긴다
                        float w = (total == 1 ? 3f : total == 2 ? 1.5f : 0.6f) * (p.depth < 7 ? 1.4f : 1f);
                        weights.add(w);
                    }
                }
                if (cands.isEmpty()) continue;
                int pick = weighted(weights, rnd);
                int[] c = cands.get(pick);
                Gen parent = parents.get(pick);
                int n = counter.get(z.id);
                counter.put(z.id, n + 1);
                Gen g = new Gen(String.format("%s_%02d", z.id, n), z.id, RoomDef.Kind.ROOM, c[0], c[1]);
                g.parent = parent;
                g.depth = parent.depth + 1;
                grid[c[0]][c[1]] = g;
                list.add(g);
                grew = true;
            }
        }
        for (Balance.ZoneDef z : b.zones) {
            List<Gen> list = byZone.get(z.id);
            if (list.size() < rooms) return null;
            if (!placeBoss(z.id, list, grid, rnd)) return null;
            // 본 경로 = 입구 → 보스 앞
            Gen ante = list.get(list.size() - 2);
            for (Gen g = ante; g != null; g = g.parent) g.main = true;
        }
        return byZone;
    }

    private static Gen gridAt(Gen[][] grid, int x, int y) {
        if (x < 0 || y < 0 || x >= GRID_W || y >= GRID_H) return null;
        return grid[x][y];
    }

    private static int weighted(List<Float> w, Random rnd) {
        float total = 0;
        for (float f : w) total += f;
        float r = rnd.nextFloat() * total;
        for (int i = 0; i < w.size(); i++) {
            r -= w.get(i);
            if (r < 0) return i;
        }
        return w.size() - 1;
    }

    /** 가장 깊은 막다른 방부터 살펴, 한 방향으로 빈 칸 두 개가 있으면 보스 앞 공간과 보스방을 놓는다. */
    static boolean placeBoss(String zone, List<Gen> list, Gen[][] grid, Random rnd) {
        List<Gen> leaves = new ArrayList<>();
        for (Gen g : list) {
            boolean leaf = true;
            for (Gen o : list) if (o.parent == g) leaf = false;
            if (leaf && g.depth >= 4) leaves.add(g);
        }
        Collections.shuffle(leaves, rnd);
        leaves.sort((a, c) -> c.depth - a.depth);
        for (Gen leaf : leaves) {
            List<int[]> dirs = new ArrayList<>();
            for (int[] d : DIRS) dirs.add(d);
            Collections.shuffle(dirs, rnd);
            for (int[] d : dirs) {
                int ax = leaf.gx + d[0], ay = leaf.gy + d[1];
                int bx = ax + d[0], by = ay + d[1];
                if (!inGrid(ax, ay) || !inGrid(bx, by) || grid[ax][ay] != null || grid[bx][by] != null) continue;
                if (nextToCamp(ax, ay) || nextToCamp(bx, by)) continue;
                // 보스 앞 공간과 보스방은 같은 구역의 다른 방과 맞닿지 않게 (입구는 하나뿐)
                if (ownNeighbors(grid, ax, ay, zone) != 1 || ownNeighbors(grid, bx, by, zone) != 0) continue;
                Gen ante = new Gen(zone + "_ante", zone, RoomDef.Kind.ANTE, ax, ay);
                ante.parent = leaf;
                ante.depth = leaf.depth + 1;
                Gen boss = new Gen(zone + "_boss", zone, RoomDef.Kind.BOSS, bx, by);
                boss.parent = ante;
                boss.depth = ante.depth + 1;
                grid[ax][ay] = ante;
                grid[bx][by] = boss;
                list.add(ante);
                list.add(boss);
                return true;
            }
        }
        return false;
    }

    private static int ownNeighbors(Gen[][] grid, int x, int y, String zone) {
        int n = 0;
        for (int[] d : DIRS) {
            Gen g = gridAt(grid, x + d[0], y + d[1]);
            if (g != null && g.zone.equals(zone)) n++;
        }
        return n;
    }

    /** 방 성격: 몬스터+광맥(기본), 전투만, 광맥만(안전). */
    static void assignContent(List<Gen> rooms, Balance.ZoneDef z, Balance b, Random rnd) {
        Balance.DungeonDef d = b.dungeon;
        boolean firstZone = z == b.zones[0];
        for (Gen g : rooms) {
            if (g.kind != RoomDef.Kind.ROOM) continue;
            if (g.main && g.depth == 0 && firstZone) {
                // 무기 없이도 재료를 모을 수 있는 첫 광물방
                g.enemies = false;
                g.veins = true;
                g.veinCount = b.mining.firstRoomVeins;
                continue;
            }
            float roll = rnd.nextFloat();
            boolean lastBeforeAnte = false;
            for (Gen o : rooms) if (o.parent == g && o.kind == RoomDef.Kind.ANTE) lastBeforeAnte = true;
            boolean prevSafe = g.parent != null && g.parent.kind == RoomDef.Kind.ROOM && !g.parent.enemies;
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
        }
        placeShrine(rooms, z, rnd);
    }

    /**
     * 구역마다 제단 하나. 곁가지의 가장 깊은 방에 두고, 곁가지가 없으면 본 경로 중간 방에 둔다.
     * guarded 면 강한 적이 지키는 전투방(축복+몬스터), 아니면 적이 없는 방(축복)이 된다. 제단 방에는 광맥이 없다.
     */
    static void placeShrine(List<Gen> rooms, Balance.ZoneDef z, Random rnd) {
        if (z.shrine == null) return;
        Gen best = null;
        for (Gen g : rooms) {
            if (g.kind != RoomDef.Kind.ROOM || g.main) continue;
            if (best == null || g.depth > best.depth || (g.depth == best.depth && rnd.nextBoolean())) best = g;
        }
        if (best == null) {
            List<Gen> mid = new ArrayList<>();
            for (Gen g : rooms) {
                if (g.kind != RoomDef.Kind.ROOM || g.depth < 2) continue;
                boolean beforeAnte = false;
                for (Gen o : rooms) if (o.parent == g && o.kind == RoomDef.Kind.ANTE) beforeAnte = true;
                if (!beforeAnte) mid.add(g);
            }
            if (mid.isEmpty()) return;
            best = mid.get(mid.size() / 2);
        }
        best.shrine = true;
        best.enemies = z.shrine.guarded;
        best.veins = false;
        best.veinCount = 0;
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
            addDoor(m, edge + "-L", l, new Rectangle(x0 * TILE, ly * TILE, TILE, CORRIDOR * TILE), r, "E");
            addDoor(m, edge + "-R", r, new Rectangle((x1 - 1) * TILE, ry * TILE, TILE, CORRIDOR * TILE), l, "W");
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
            addDoor(m, edge + "-D", lo, new Rectangle(lx * TILE, y0 * TILE, CORRIDOR * TILE, TILE), hiR, "N");
            addDoor(m, edge + "-U", hiR, new Rectangle(hx * TILE, (y1 - 1) * TILE, CORRIDOR * TILE, TILE), lo, "S");
        }
    }

    /** 방의 변 위에서 통로가 붙을 위치 (모서리를 피한다). */
    private static int corridorPos(Gen g, boolean horizontalCorridor, Random rnd) {
        if (horizontalCorridor) return g.y + 2 + rnd.nextInt(Math.max(1, g.h - 6));
        return g.x + 2 + rnd.nextInt(Math.max(1, g.w - 6));
    }

    /** room 쪽 입구의 문. 캠프 쪽 문은 구역 봉인, 적이 있는 방 쪽 문은 무기 필요 규칙을 갖는다. */
    private static void addDoor(DungeonMap m, String id, Gen room, Rectangle r, Gen other, String wall) {
        String rule = "";
        String combat = "";
        if (room.kind == RoomDef.Kind.CAMP) {
            if (!"mine".equals(other.zone)) rule = "zone:" + other.zone;
        } else {
            combat = room.id;
            if (room.enemies || room.kind == RoomDef.Kind.BOSS) rule = "weapon";
        }
        m.doors.add(new DoorDef(id, r, rule, combat, wall));
    }

    // ---- 캠프 ----

    /**
     * 중앙 캠프: 쓰러지면 다시 일어나는 곳. 쉬거나 장비를 만드는 시설은 없다 (대장간은 어디서든 주머니에서 꺼낸다).
     * 가운데 모닥불 둘레로 돌기둥이 둥글게 서 있고, 구역으로 가는 문마다 그 구역 색의 화로가 길을 알린다.
     */
    static void furnishCamp(DungeonMap m, Gen c) {
        float cx = c.x * TILE, cy = c.y * TILE;
        float mx = cx + 14 * TILE, my = cy + 7 * TILE;
        m.points.put("camp_spawn", new Vector2(mx, cy + 5.2f * TILE));
        decor(m, "campfire", mx, my + 0.6f * TILE);
        m.solids.add(new Rectangle(mx - 22, my + 0.6f * TILE - 10, 44, 22));
        // 둘레의 돌기둥 (타원형으로 여덟 개. 문 앞은 비운다)
        float[][] ring = {{-10.5f, 4.2f}, {-5.5f, 5.4f}, {5.5f, 5.4f}, {10.5f, 4.2f}, {-11.5f, -4.2f}, {-4f, -5.6f}, {4f, -5.6f}, {11.5f, -4.2f}};
        for (float[] p : ring) {
            float x = mx + p[0] * TILE, y = my + p[1] * TILE;
            decor(m, "camp_pillar", x, y);
            m.solids.add(new Rectangle(x - 14, y - 8, 28, 16));
        }
        // 문마다 구역 색의 화로: 왼쪽 폐광(주황), 오른쪽 요새(파랑), 위 신전(보라)
        // 통로(폭 2칸)의 가운데를 기준으로 양옆에 나란히 세워, 길을 막지 않고 길목을 표시한다
        float sideY = cy + (5 + CORRIDOR / 2f) * TILE, topX = cx + (12 + CORRIDOR / 2f) * TILE;
        for (float dy : new float[]{-3.1f, 2.5f}) {
            decor(m, "brazier", cx + 1.3f * TILE, sideY + dy * TILE);
            decor(m, "brazier_blue", cx + 26.7f * TILE, sideY + dy * TILE);
        }
        for (float dx : new float[]{-2.6f, 2.6f}) decor(m, "brazier_violet", topX + dx * TILE, cy + 12.8f * TILE);
        decor(m, "banner_red", cx + 7.6f * TILE, (c.y + c.h) * TILE + 60);
        decor(m, "banner_blue", cx + 20.4f * TILE, (c.y + c.h) * TILE + 60);
        torches(m, roomRect(c), "warm", new ArrayList<>());

        // 지름길: 방 아래쪽 바닥에 구역 방향대로 나란히 (왼쪽 폐광, 가운데 신전, 오른쪽 요새).
        // 화면 위쪽의 정보창이나 기둥에 가리지 않는 자리에 둔다.
        float sy = cy + 2.5f * TILE;
        float[] sx = {mx - 7.5f * TILE, mx + 7.5f * TILE, mx};
        String[] zones = {"mine", "fortress", "temple"};
        for (int i = 0; i < zones.length; i++) {
            addShortcut(m, zones[i], new Rectangle(sx[i] - 26, sy - 22, 52, 44), new Vector2(sx[i], sy + 1.5f * TILE));
        }
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
            // 화로는 위쪽 벽 앞, 문에서 먼 자리에 (길목을 막아 보이지 않게)
            float by = r.y + r.height - 1.2f * TILE;
            for (float fx : new float[]{0.5f, 0.25f, 0.75f, 0.12f, 0.88f}) {
                Vector2 bp = new Vector2(r.x + r.width * fx, by);
                if (nearAny(bp, doorCenters, 3.2f * TILE) || nearAny(bp, taken, 2f * TILE)) continue;
                decor(m, "brazier", bp.x, bp.y);
                break;
            }
            return;
        }
        if (room.kind == RoomDef.Kind.BOSS) {
            // 큰 장애물 없이 모서리에 화로만
            int from = m.decor.size();
            decor(m, "brazier", r.x + 1.4f * TILE, r.y + r.height - 1.4f * TILE);
            decor(m, "brazier", r.x + r.width - 1.4f * TILE, r.y + r.height - 1.4f * TILE);
            decor(m, "brazier", r.x + 1.4f * TILE, r.y + 1.4f * TILE);
            decor(m, "brazier", r.x + r.width - 1.4f * TILE, r.y + 1.4f * TILE);
            bossDecor(m, room, rnd);
            // 들어오는 길목에 걸리는 장식은 뺀다
            for (int i = m.decor.size() - 1; i >= from; i--) {
                MapObject o = m.decor.get(i);
                if (nearAny(new Vector2(o.x(), o.y()), doorCenters, 3.2f * TILE)) m.decor.remove(i);
            }
            decor(m, "boss_seal", room.centerX(), room.centerY());
            Map<String, String> p = new HashMap<>();
            p.put("room", room.id);
            p.put("enemy", b.zone(room.zone).boss);
            m.spawns.add(new MapObject(room.id + "_boss", "spawn",
                    new Rectangle(room.centerX(), r.y + r.height - 5f * TILE, 0, 0), p));
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

        // 제단: 방 한가운데 조금 위 (상자는 방을 돌파하면 그 아래에 나타난다)
        if ("shrine".equals(room.feature)) {
            float fx = room.centerX(), fy = room.centerY() + 0.6f * TILE;
            Map<String, String> p = new HashMap<>();
            p.put("room", room.id);
            p.put("zone", room.zone);
            interact(m, room.id + "_shrine", "shrine", new Rectangle(fx - 26, fy - 16, 52, 40), p);
            m.solids.add(new Rectangle(fx - 22, fy - 8, 44, 20));
            taken.add(new Vector2(fx, fy));
        }

        if (room.enemies) placeCover(m, room, b, rnd, doorCenters, taken);

        // 장식물: 구역마다 정해진 몇 종류만, 벽 가까이에 조금씩
        String[] props = propsFor(room.zone);
        int count = 2 + rnd.nextInt(3);
        for (int i = 0, tries = 0; i < count && tries < 120; tries++) {
            String type = props[rnd.nextInt(props.length)];
            float[] fp = footprint(type);
            Vector2 p = wallSpot(r, rnd, fp != null && fp[0] > 40 ? 0 : rnd.nextInt(4));
            float clear = fp != null ? Math.max(1.4f * TILE, fp[0] * 0.6f + 16) : 1.4f * TILE;
            if (nearAny(p, doorCenters, 3.4f * TILE) || nearAny(p, taken, clear)) continue;
            if (fp != null && m.overlapsStatic(new Rectangle(p.x - fp[0] / 2, p.y - fp[1] / 2, fp[0], fp[1]))) continue;
            prop(m, type, p.x, p.y, true);
            taken.add(p);
            i++;
        }
        // 벽에 거는 장식
        String banner = "fortress".equals(room.zone) ? "banner_blue" : "temple".equals(room.zone) ? "banner_red" : null;
        if (banner != null && rnd.nextFloat() < 0.7f) {
            float bx0 = r.x + (0.25f + rnd.nextFloat() * 0.5f) * r.width;
            if (!nearAny(new Vector2(bx0, r.y + r.height), doorCenters, 2.5f * TILE)) decor(m, banner, bx0, r.y + r.height + 60);
        }

        // 적 생성점: 모든 문에서 떨어진 곳. 어떤 적이 몇 번에 걸쳐 나올지는 전투가 시작될 때 Waves 가 정한다.
        if (room.enemies) {
            int n = b.dungeon.spawnSlots;
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
                m.spawns.add(new MapObject(room.id + "_s" + i, "spawn", new Rectangle(p.x, p.y, 0, 0), pr));
            }
        }
    }

    /** 엄폐물 배치 틀: 방 가운데에서 본 위치 (방 절반 크기에 대한 비율). 가운데는 상자 자리라 비운다. */
    private static final float[][][] COVER_LAYOUTS = {
            {{-0.46f, 0.36f}, {0.46f, -0.36f}, {0.46f, 0.36f}, {-0.46f, -0.36f}},
            {{-0.5f, 0f}, {0.5f, 0f}, {-0.2f, 0.5f}, {0.2f, -0.5f}},
            {{-0.3f, 0.42f}, {0.3f, -0.42f}, {-0.56f, -0.2f}, {0.56f, 0.2f}},
            {{-0.52f, 0.3f}, {0.52f, 0.3f}, {0f, -0.52f}, {0f, 0.52f}},
    };

    /** 구역별 엄폐물 그림: 폐광은 상자와 통, 요새는 보급품 더미, 신전은 돌기둥. */
    private static String[] coverFor(String zone) {
        switch (zone) {
            case "mine": return new String[]{"crates", "barrel"};
            case "fortress": return new String[]{"supplies", "crates"};
            default: return new String[]{"camp_pillar"};
        }
    }

    private static float coverWidth(String type) {
        switch (type) {
            case "supplies": return 52;
            case "camp_pillar": return 32;
            default: return 46;
        }
    }

    /**
     * 전투방 안쪽에 엄폐물을 몇 개 놓는다. 지나갈 수 없고, 탄과 적의 시야·돌진을 막는다.
     * 문 앞과 방 한가운데(상자·제단 자리)는 비운다.
     */
    static void placeCover(DungeonMap m, RoomDef room, Balance b, Random rnd, List<Vector2> doors, List<Vector2> taken) {
        Balance.DungeonDef d = b.dungeon;
        int want = d.coverMin + rnd.nextInt(Math.max(1, d.coverMax - d.coverMin + 1));
        float[][] layout = COVER_LAYOUTS[rnd.nextInt(COVER_LAYOUTS.length)];
        String[] types = coverFor(room.zone);
        Rectangle r = room.bounds;
        float hw = r.width / 2f - 2 * TILE, hh = r.height / 2f - 2 * TILE;
        Vector2 center = new Vector2(room.centerX(), room.centerY());
        for (int i = 0, placed = 0; i < layout.length && placed < want; i++) {
            Vector2 p = new Vector2(center.x + layout[i][0] * hw * 2f, center.y + layout[i][1] * hh * 2f);
            p.x = Math.max(r.x + 3 * TILE, Math.min(r.x + r.width - 3 * TILE, p.x));
            p.y = Math.max(r.y + 2.6f * TILE, Math.min(r.y + r.height - 3 * TILE, p.y));
            if (nearAny(p, doors, 3.4f * TILE) || nearAny(p, taken, 2.2f * TILE) || p.dst(center) < 2.4f * TILE) continue;
            String type = types[rnd.nextInt(types.length)];
            float w = coverWidth(type);
            Rectangle solid = new Rectangle(p.x - w / 2f, p.y - 8, w, 26);
            if (m.overlapsStatic(solid)) continue;
            decor(m, type, p.x, p.y);
            m.solids.add(solid);
            m.covers.add(solid);
            taken.add(p);
            placed++;
        }
    }

    /** 구역별 장식 종류. 수정·광석 모양은 캘 수 있는 광맥에만 쓰므로 여기에 넣지 않는다. */
    private static String[] propsFor(String zone) {
        switch (zone) {
            case "mine": return new String[]{"barrel", "crate", "crates", "sacks"};
            case "fortress": return new String[]{"weapon_rack", "armor", "barrel", "crates"};
            default: return new String[]{"vases", "pot", "grave", "candle"};
        }
    }

    /** 고체 장식의 바닥 충돌 크기(px). null 이면 지나갈 수 있는 장식. */
    static float[] footprint(String type) {
        switch (type) {
            case "shelf": return new float[]{84, 16};
            case "supplies": return new float[]{56, 16};
            case "weapon_rack": return new float[]{70, 14};
            case "crates": return new float[]{48, 16};
            case "barrel": return new float[]{40, 14};
            case "armor": return new float[]{28, 12};
            case "crate":
            case "vases":
            case "grave":
                return new float[]{26, 12};
            case "pot":
                return new float[]{18, 10};
            default:
                return null;
        }
    }

    /** 장식 하나를 놓는다. solid 이면 크기만큼 충돌을 더한다. */
    private static void prop(DungeonMap m, String type, float x, float y, boolean solid) {
        decor(m, type, x, y);
        float[] fp = footprint(type);
        if (solid && fp != null) m.solids.add(new Rectangle(x - fp[0] / 2, y - fp[1] / 2, fp[0], fp[1]));
    }

    /** 보스방마다 다른 분위기. 전투 공간을 막지 않도록 벽 가장자리에만 둔다. */
    static void bossDecor(DungeonMap m, RoomDef room, Random rnd) {
        Rectangle r = room.bounds;
        float top = r.y + r.height - 0.9f * TILE, bottom = r.y + 0.7f * TILE;
        switch (room.zone) {
            case "mine":
                decor(m, "sacks", r.x + 3 * TILE, top);
                decor(m, "sacks", r.x + r.width - 3 * TILE, top);
                decor(m, "cauldron", r.x + 1.5f * TILE, r.y + r.height / 2);
                decor(m, "cauldron", r.x + r.width - 1.5f * TILE, r.y + r.height / 2);
                break;
            case "fortress":
                for (int i = 0; i < 4; i++) {
                    float x = r.x + (4 + i * 5.3f) * TILE;
                    decor(m, i % 2 == 0 ? "banner_red" : "banner_blue", x, r.y + r.height + 60);
                    decor(m, "armor", x + 1.6f * TILE, top);
                }
                decor(m, "dragon_skull", r.x + 2.2f * TILE, r.y + r.height / 2);
                decor(m, "dragon_skull", r.x + r.width - 2.2f * TILE, r.y + r.height / 2);
                decor(m, "weapon_rack", r.x + 5 * TILE, bottom);
                decor(m, "weapon_rack", r.x + r.width - 5 * TILE, bottom);
                break;
            default:
                decor(m, "lich", r.x + 5.5f * TILE, r.y + r.height - 3.2f * TILE);
                for (int i = 0; i < 7; i++) decor(m, "grave", r.x + (9 + i * 2f) * TILE, top);
                for (int i = 0; i < 4; i++) decor(m, "candle", r.x + (2 + i * 6.5f) * TILE, r.y + r.height / 2 + (i % 2 == 0 ? 60 : -60));
                break;
        }
    }

    /** 벽에서 한 칸 안쪽의 무작위 위치. side 0 위, 1 아래, 2 왼쪽, 3 오른쪽. */
    private static Vector2 wallSpot(Rectangle r, Random rnd, int side) {
        float t = 0.1f + rnd.nextFloat() * 0.8f;
        switch (side) {
            case 0: return new Vector2(r.x + r.width * t, r.y + r.height - 0.8f * TILE);
            case 1: return new Vector2(r.x + r.width * t, r.y + 0.7f * TILE);
            case 2: return new Vector2(r.x + 0.9f * TILE, r.y + r.height * t);
            default: return new Vector2(r.x + r.width - 0.9f * TILE, r.y + r.height * t);
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
