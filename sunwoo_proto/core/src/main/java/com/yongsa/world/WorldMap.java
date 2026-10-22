package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlReader.Element;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * world.tmx에서 규칙에 필요한 정보만 읽은 것. 그리기와 무관해서 화면 없이 테스트할 수 있다.
 * 좌표는 libGDX 기준(왼쪽 아래 0,0, 단위 px). 그리기용 TiledMap은 render 쪽에서 따로 읽는다.
 */
public class WorldMap {

    public final int widthTiles, heightTiles, tileSize;
    /** solid[x][y]: 벽 타일. */
    public final boolean[][] solid;

    public final Rectangle camp = new Rectangle();
    public final Rectangle bed = new Rectangle();
    public final Rectangle forge = new Rectangle();
    public final Vector2 sigil = new Vector2();
    public final Vector2 spawn = new Vector2();
    public final Rectangle arena = new Rectangle();
    public final Vector2 arenaLand = new Vector2();
    public final Vector2 finalBossSpawn = new Vector2();
    public final Vector2 arenaPortal = new Vector2();

    public final List<RoomDef> rooms = new ArrayList<>();
    public final List<DoorDef> doors = new ArrayList<>();
    public final List<CorridorDef> corridors = new ArrayList<>();
    public final List<GateDef> gates = new ArrayList<>();
    public final List<SpawnDef> monsterSpawns = new ArrayList<>();
    public final Map<String, List<Vector2>> veinSpots = new HashMap<>();
    public final Map<String, Vector2> portals = new HashMap<>();

    public record RoomDef(String id, RoomKind kind, String dungeon, String parent, int order,
                          Rectangle bounds, Vector2 entry, Vector2 outside) {
    }

    public record DoorDef(String roomId, Rectangle bounds) {
    }

    public record CorridorDef(String a, String b, Rectangle bounds) {
    }

    public record GateDef(String dungeon, Rectangle bounds) {
    }

    public record SpawnDef(String roomId, boolean boss, Vector2 pos) {
    }

    public WorldMap(int w, int h, int tileSize) {
        this.widthTiles = w;
        this.heightTiles = h;
        this.tileSize = tileSize;
        this.solid = new boolean[w][h];
    }

    public float widthPx() { return widthTiles * tileSize; }
    public float heightPx() { return heightTiles * tileSize; }

    public boolean solidTile(int tx, int ty) {
        if (tx < 0 || ty < 0 || tx >= widthTiles || ty >= heightTiles) return true;
        return solid[tx][ty];
    }

    public RoomDef room(String id) {
        for (RoomDef r : rooms) if (r.id.equals(id)) return r;
        throw new IllegalArgumentException("없는 방: " + id);
    }

    // ================================================================== TMX 읽기

    public static WorldMap parse(String xml) {
        Element map = new XmlReader().parse(xml);
        int w = map.getIntAttribute("width"), h = map.getIntAttribute("height"), ts = map.getIntAttribute("tilewidth");
        WorldMap m = new WorldMap(w, h, ts);

        for (Element layer : map.getChildrenByName("layer")) {
            if (!"walls".equals(layer.getAttribute("name"))) continue;
            String[] cells = layer.getChildByName("data").getText().replace("\n", "").replace("\r", "").split(",");
            for (int i = 0; i < cells.length; i++) {
                if (Integer.parseInt(cells[i].trim()) != 0) m.solid[i % w][h - 1 - i / w] = true;
            }
        }

        float mapH = h * ts;
        for (Element group : map.getChildrenByName("objectgroup")) {
            for (Element o : group.getChildrenByName("object")) {
                String type = o.getAttribute("type", o.getAttribute("class", ""));
                String name = o.getAttribute("name", "");
                float x = o.getFloatAttribute("x"), y = o.getFloatAttribute("y");
                float ow = o.getFloatAttribute("width", 0), oh = o.getFloatAttribute("height", 0);
                Rectangle rect = new Rectangle(x, mapH - y - oh, ow, oh);
                Vector2 pt = new Vector2(x, mapH - y);
                switch (type) {
                    case "camp" -> m.camp.set(rect);
                    case "bed" -> m.bed.set(rect);
                    case "forge" -> m.forge.set(rect);
                    case "sigil" -> m.sigil.set(pt);
                    case "spawn" -> m.spawn.set(pt);
                    case "arena" -> m.arena.set(rect);
                    case "arena_land" -> m.arenaLand.set(pt);
                    case "final_boss" -> m.finalBossSpawn.set(pt);
                    case "arena_portal" -> m.arenaPortal.set(pt);
                    case "room" -> m.rooms.add(new RoomDef(name, RoomKind.parse(prop(o, "kind")), prop(o, "dungeon"),
                            prop(o, "parent"), Integer.parseInt(prop(o, "order")), rect,
                            tile(m, prop(o, "entryX"), prop(o, "entryY")), tile(m, prop(o, "outX"), prop(o, "outY"))));
                    case "door" -> m.doors.add(new DoorDef(prop(o, "room"), rect));
                    case "corridor" -> m.corridors.add(new CorridorDef(prop(o, "a"), prop(o, "b"), rect));
                    case "gate" -> m.gates.add(new GateDef(prop(o, "dungeon"), rect));
                    case "monster" -> m.monsterSpawns.add(new SpawnDef(prop(o, "room"), Boolean.parseBoolean(prop(o, "boss")), pt));
                    case "vein_spot" -> m.veinSpots.computeIfAbsent(prop(o, "room"), k -> new ArrayList<>()).add(pt);
                    case "portal" -> m.portals.put(prop(o, "room"), pt);
                    default -> { }
                }
            }
        }
        return m;
    }

    /** 생성기가 적어 둔 타일 좌표(실수) → px. */
    private static Vector2 tile(WorldMap m, String tx, String ty) {
        return new Vector2(Float.parseFloat(tx) * m.tileSize, Float.parseFloat(ty) * m.tileSize);
    }

    private static String prop(Element o, String key) {
        Element props = o.getChildByName("properties");
        if (props != null) {
            for (Element p : props.getChildrenByName("property")) {
                if (key.equals(p.getAttribute("name"))) return p.getAttribute("value");
            }
        }
        throw new IllegalArgumentException("오브젝트 '" + o.getAttribute("name", "?") + "'에 속성 " + key + " 없음");
    }
}
