package com.yongsa.world;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.XmlReader;
import com.yongsa.config.Balance;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 통합 Tiled 맵(dungeon.tmx)을 게임 규칙에 필요한 형태로 해석한다.
 * 그리기는 libGDX TiledMapRenderer 가 같은 파일을 읽어 처리하고, 이 클래스는 그래픽 없이 동작한다.
 * Tiled 좌표(y 아래로 증가)를 월드 좌표(y 위로 증가)로 바꿔 저장한다.
 */
public class DungeonMap {
    public final int widthTiles;
    public final int heightTiles;
    public final int tileSize;
    public final float widthPx;
    public final float heightPx;

    /** [ty * width + tx], ty=0 이 맨 아래 줄. */
    private final boolean[] solidTiles;

    public final List<RoomDef> rooms = new ArrayList<>();
    public final Map<String, RoomDef> roomById = new HashMap<>();
    public final List<DoorDef> doors = new ArrayList<>();
    public final List<MapObject> spawns = new ArrayList<>();
    public final List<MapObject> veins = new ArrayList<>();
    public final Map<String, Vector2> points = new HashMap<>();
    public final List<MapObject> interacts = new ArrayList<>();
    public final List<Rectangle> solids = new ArrayList<>();
    public final List<MapObject> decor = new ArrayList<>();

    private DungeonMap(int w, int h, int tile) {
        widthTiles = w;
        heightTiles = h;
        tileSize = tile;
        widthPx = w * tile;
        heightPx = h * tile;
        solidTiles = new boolean[w * h];
    }

    public static DungeonMap load(String path) {
        return load(new FileHandle(new File(path)));
    }

    public static DungeonMap load(FileHandle file) {
        XmlReader.Element root = new XmlReader().parse(file);
        int w = root.getIntAttribute("width");
        int h = root.getIntAttribute("height");
        int tile = root.getIntAttribute("tilewidth");
        DungeonMap m = new DungeonMap(w, h, tile);

        int[] floor = null;
        int[] walls = null;
        for (XmlReader.Element layer : root.getChildrenByName("layer")) {
            int[] data = parseCsv(layer.getChildByName("data").getText(), w * h);
            String name = layer.getAttribute("name");
            if ("floor".equals(name)) floor = data;
            else if ("walls".equals(name)) walls = data;
        }
        if (floor == null) throw new IllegalStateException("floor 레이어가 없습니다");
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < w; tx++) {
                int idx = (h - 1 - ty) * w + tx; // Tiled 행 → 월드 행
                boolean solid = floor[idx] == 0 || (walls != null && walls[idx] != 0);
                m.solidTiles[ty * w + tx] = solid;
            }
        }

        for (XmlReader.Element group : root.getChildrenByName("objectgroup")) {
            String layerName = group.getAttribute("name");
            for (XmlReader.Element o : group.getChildrenByName("object")) {
                String name = o.getAttribute("name", "");
                String type = o.getAttribute("type", o.getAttribute("class", ""));
                float x = o.getFloatAttribute("x");
                float yDown = o.getFloatAttribute("y");
                float ow = o.getFloatAttribute("width", 0);
                float oh = o.getFloatAttribute("height", 0);
                Rectangle r = new Rectangle(x, m.heightPx - yDown - oh, ow, oh);
                Map<String, String> props = readProps(o);
                switch (layerName) {
                    case "rooms": {
                        RoomDef.Kind kind = RoomDef.Kind.valueOf(type.toUpperCase());
                        RoomDef room = new RoomDef(name, props.getOrDefault("zone", ""), kind, r);
                        m.rooms.add(room);
                        m.roomById.put(name, room);
                        break;
                    }
                    case "doors":
                        m.doors.add(new DoorDef(name, r, props.get("rule"), props.get("combat")));
                        break;
                    case "spawns":
                        m.spawns.add(new MapObject(name, type, r, props));
                        break;
                    case "veins":
                        m.veins.add(new MapObject(name, type, r, props));
                        break;
                    case "points":
                        m.points.put(name, new Vector2(r.x, r.y));
                        break;
                    case "interact":
                        m.interacts.add(new MapObject(name, type, r, props));
                        break;
                    case "solids":
                        m.solids.add(r);
                        break;
                    case "decor":
                        m.decor.add(new MapObject(name, type, r, props));
                        break;
                    default:
                        break;
                }
            }
        }
        return m;
    }

    private static Map<String, String> readProps(XmlReader.Element o) {
        Map<String, String> props = new LinkedHashMap<>();
        XmlReader.Element pe = o.getChildByName("properties");
        if (pe != null) {
            for (XmlReader.Element p : pe.getChildrenByName("property")) {
                props.put(p.getAttribute("name"), p.getAttribute("value", p.getText()));
            }
        }
        return props;
    }

    private static int[] parseCsv(String text, int expected) {
        int[] out = new int[expected];
        int i = 0;
        for (String tok : text.split(",")) {
            String t = tok.trim();
            if (t.isEmpty()) continue;
            // 뒤집기 플래그(상위 비트)를 제거한 타일 ID
            long v = Long.parseLong(t) & 0x1FFFFFFFL;
            if (i < expected) out[i] = (int) v;
            i++;
        }
        if (i != expected) throw new IllegalStateException("타일 레이어 크기 불일치: " + i + " / " + expected);
        return out;
    }

    // ---- 질의 ----

    public boolean isSolidTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= widthTiles || ty >= heightTiles) return true;
        return solidTiles[ty * widthTiles + tx];
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

    public RoomDef roomAt(float x, float y) {
        for (RoomDef r : rooms) if (r.bounds.contains(x, y)) return r;
        return null;
    }

    public RoomDef room(String id) {
        return roomById.get(id);
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

    public MapObject vein(String candidateId) {
        for (MapObject v : veins) if (v.name.equals(candidateId)) return v;
        return null;
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

    /**
     * 개발 검증. 문제 목록을 돌려준다(비어 있으면 통과).
     * 후보 지점이 부족한 맵은 실행 시 임의로 보정하지 않고 여기서 오류로 처리한다.
     */
    public List<String> validate(Balance b) {
        List<String> errors = new ArrayList<>();
        if (!points.containsKey("camp_spawn")) {
            errors.add("camp_spawn 없음");
        } else {
            Vector2 sp = points.get("camp_spawn");
            RoomDef camp = roomAt(sp.x, sp.y);
            if (camp == null || camp.kind != RoomDef.Kind.CAMP) errors.add("camp_spawn 이 중앙 캠프 밖에 있음");
            Rectangle around = new Rectangle(sp.x - tileSize * 1.5f, sp.y - tileSize * 1.5f, tileSize * 3, tileSize * 3);
            if (overlapsStatic(around)) errors.add("camp_spawn 주변 한 타일 안에 고체 오브젝트가 있음");
        }
        for (Balance.ZoneDef z : b.zones) {
            for (String rid : new String[]{z.id + "_ore", z.id + "_battle_a", z.id + "_battle_b", z.id + "_ante", z.id + "_boss"}) {
                if (!roomById.containsKey(rid)) errors.add("방 없음: " + rid);
            }
            List<MapObject> cands = veinCandidates(z.id + "_ore");
            if (cands.size() < b.mining.minCandidates) errors.add(z.id + " 광맥 후보 부족: " + cands.size());
            boolean anyVisible = false;
            for (MapObject c : cands) {
                if ("true".equals(c.prop("visible"))) anyVisible = true;
                Rectangle vr = VeinGeometry.solidRect(c.x(), c.y());
                if (overlapsStatic(vr)) errors.add("광맥 후보가 고체와 겹침: " + c.name);
                RoomDef r = roomAt(c.x(), c.y());
                if (r == null || !r.id.equals(z.id + "_ore")) errors.add("광맥 후보가 광물방 밖: " + c.name);
            }
            if (!anyVisible) errors.add(z.id + " 입구에서 보이는 광맥 후보가 없음");
            for (String p : new String[]{"shortcut_" + z.id + "_camp", "shortcut_" + z.id + "_ante"}) {
                if (!points.containsKey(p)) errors.add("점 없음: " + p);
            }
            RoomDef boss = roomById.get(z.id + "_boss");
            if (boss != null && (boss.bounds.width > 24 * tileSize || boss.bounds.height > 12 * tileSize))
                errors.add("보스방이 24x12 타일보다 큼: " + boss.id);
            if (spawnsIn(z.id + "_boss").isEmpty()) errors.add("보스 생성점 없음: " + z.id);
        }
        return errors;
    }
}
