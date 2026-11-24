package com.yongsa.map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.DepthSink;
import com.yongsa.gfx.Lighting;
import com.yongsa.gfx.Particles;
import com.yongsa.model.GameSession;
import com.yongsa.ui.Ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 맵 그리기 (맵 담당). 바닥·벽 타일, 통로, 방 배경 그림과 앞쪽 가림 레이어, 문·창살,
 * 캠프 봉인석, 횃불과 맵에 붙은 불빛을 그린다. WorldRenderer 가 그리는 순서에 맞춰 부른다.
 */
public class MapPainter implements Disposable {
    private static final Color SEAL_DARK = new Color(0.7f, 0.3f, 1f, 1f);

    private final GameSession g;
    private final Art art;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final WorldMap map;
    private final int T;
    private float time;
    private float abyssOpening;
    private Texture approachStones;
    private static final String STAIRWELL="world/portals/abyss-stairwell.png";
    private static final float STAIR_W=175, STAIR_H=STAIR_W*240/384;
    private float viewX0, viewY0, viewX1, viewY1;

    private final String[] theme;

    /** 횃불 자리 (x, y, 깜빡임 위상[, 통로 횃불이면 1]). 불빛과 불똥은 WorldRenderer 가 쓴다. */
    public final List<float[]> torches = new ArrayList<>();

    /** GPT 길·땅 그림. 그림이 있으면 통로 타일과 통로 횃불 대신 쓴다. */
    private final PassagePainter passages;

    private final RoomGates roomGates;
    private final PaintedDoorways doorways = new PaintedDoorways();

    /** 던전 방 배경 그림 (보이는 방만 읽어 두고, 오래 안 쓴 것은 버린다). */
    /** 다가가는 방 그림을 미리 계산해 두는 일꾼 (렉 줄이기). */
    private final MapLoader loader = new MapLoader();
    private final Map<String, Texture> sceneTex = new HashMap<>();

    private final Map<String, Float> sceneUsed = new HashMap<>();

    /** GPT 가 준 앞쪽 가림 레이어 (<장면>-front.png). 없는 장면은 null 로 기억한다. */
    private final Map<String, Texture> frontTex = new HashMap<>();

    private final SceneForegrounds sceneForegrounds;
    private final SceneLayerPainter sceneLayers;

    private final BossReturnDoors returnDoors;

    public Color ambientFor(Room r) {
        String th = r == null || r.dungeonId == null ? "CAMP" : r.dungeonId;
        if (r != null && r.scene != null) {
            // 그림에 이미 빛과 그늘이 그려져 있어서 조금만 어둡게 한다
            if (r.type == RoomType.FINAL) return new Color(0.74f, 0.68f, 0.82f, 1f);
            // 마을: 마왕 전에는 보랏빛 저녁, 뒤에는 한낮
            if (r.type == RoomType.VILLAGE) return g.villagersFree() ? new Color(1f, 0.98f, 0.94f, 1f) : new Color(0.62f, 0.52f, 0.82f, 1f);
            switch (th) {
                case "CAMP": return new Color(0.84f, 0.81f, 0.78f, 1f);
                case "LEFT": return new Color(0.8f, 0.8f, 0.84f, 1f);
                case "RIGHT": return new Color(0.84f, 0.76f, 0.78f, 1f);
                default: return new Color(0.78f, 0.76f, 0.88f, 1f);
            }
        }
        switch (th) {
            case "LEFT": return new Color(0.30f, 0.32f, 0.38f, 1f);
            case "RIGHT": return new Color(0.34f, 0.24f, 0.27f, 1f);
            case "TOP": return new Color(0.27f, 0.24f, 0.38f, 1f);
            default: return new Color(0.5f, 0.45f, 0.44f, 1f);
        }
    }

    public Color corridorAmbient(String th) {
        switch (th) {
            case "LEFT": return new Color(0.5f, 0.52f, 0.6f, 1f);
            case "RIGHT": return new Color(0.56f, 0.42f, 0.46f, 1f);
            case "TOP": return new Color(0.48f, 0.44f, 0.64f, 1f);
            default: return new Color(0.66f, 0.6f, 0.56f, 1f);
        }
    }

    public Color themeMote(String th) {
        switch (th) {
            case "RIGHT": return new Color(1f, 0.45f, 0.45f, 0.22f);
            case "TOP": return new Color(0.7f, 0.6f, 1f, 0.25f);
            default: return new Color(0.75f, 0.8f, 0.9f, 0.2f);
        }
    }

    public void drawWalkable() {
        final float step = 6;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.1f, 1f, 0.3f, 0.32f);
        for (float y = (float) Math.floor(viewY0 / step) * step; y < viewY1; y += step)
            for (float x = (float) Math.floor(viewX0 / step) * step; x < viewX1; x += step)
                if (map.free(x + step / 2, y + step / 2, 9, 6)) shapes.rect(x, y, step, step);
        shapes.end();
    }

    private String themeAt(int x, int y) {
        String th = theme[x + y * map.width];
        return th == null ? "CAMP" : th;
    }

    private void drawTiles(int tx0, int ty0, int tx1, int ty1) {
        for (int y = ty1; y >= ty0; y--) {
            for (int x = tx0; x <= tx1; x++) {
                int t = map.tile(x, y);
                if (t == WorldMap.VOID) continue;
                Room tr = map.roomAt(x, y);
                if (tr != null && tr.scene != null && t != WorldMap.CORRIDOR) continue; // 배경 그림이 바닥과 벽이다 (통로 입구만 덧그린다)
                String th = themeAt(x, y);
                float px = x * T, py = y * T;
                if (map.corridorAt(x, y) != null && (passages.active || drawCorridorTile(t, th, x, y, px, py))) continue;
                if (t == WorldMap.WALL) {
                    int below = map.tile(x, y - 1);
                    boolean front = below == WorldMap.FLOOR || below == WorldMap.DOOR || below == WorldMap.CORRIDOR;
                    batch.draw(art.world(front ? "wallfront_" + th : "walltop_" + th), px, py, T, T);
                    continue;
                }
                batch.draw(art.floor(th, x, y), px, py, T, T);
                if (t == WorldMap.FLOOR) {
                    TextureRegion d = art.decal(th, x, y);
                    if (d != null) batch.draw(d, px, py, T, T);
                }
                // 벽 옆 그늘
                if (map.tile(x, y + 1) == WorldMap.WALL) batch.draw(art.world("ao_top"), px, py, T, T);
                if (map.tile(x - 1, y) == WorldMap.WALL) batch.draw(art.world("ao_left"), px, py, T, T);
                if (map.tile(x + 1, y) == WorldMap.WALL) batch.draw(art.world("ao_right"), px, py, T, T);
                if (t == WorldMap.DOOR) {
                    Door d = map.door(x, y);
                    if (d.link != null && d.link.gateClosed) {
                        batch.setColor(0.05f, 0.03f, 0.08f, 0.65f);
                        batch.draw(art.white(), px, py, T, T);
                        batch.setColor(Color.WHITE);
                    } else if (d.lockedByFight) {
                        batch.draw(art.world("bars"), px, py, T, T);
                    }
                }
            }
        }
    }

    /**
     * 테마별 통로 질감: 그 던전 방 그림에서 바닥 한 조각과 바위 벽 한 조각을 잘라 거울 반복으로 깐다
     * (u0, v0, u1, v1: 그림 왼쪽 위 기준 0~1). 통로 타일 그림(docs/ART_REQUESTS.md 0-9)이 오면 바꾼다.
     */
    private static final String[][] CORRIDOR_SKIN = {
            {"CAMP", "scenes/natural-v1/central-camp.png", ".30,.50,.45,.68", ".25,.88,.42,.99"},
            {"LEFT", "scenes/natural-v1/mine.png", ".30,.33,.50,.58", ".20,.85,.40,.99"},
            {"RIGHT", "scenes/natural-v1/citadel.png", ".62,.32,.78,.58", ".05,.82,.25,.98"},
            {"TOP", "scenes/natural-v1/tower.png", ".24,.40,.38,.62", ".26,.88,.44,.99"},
    };

    /** 방 그림 한 칸이 월드에서 차지하는 크기 (30칸 방 = 그림 1672 칸). */
    private static final float SKIN_SCALE = 30f * 32f / 1672f;

    private final Map<String, Texture[]> corridorSkin = new HashMap<>();

    private Texture[] corridorSkin(String th) {
        Texture[] t = corridorSkin.get(th);
        if (t != null) return t;
        String[] def = CORRIDOR_SKIN[0];
        for (String[] d : CORRIDOR_SKIN) if (d[0].equals(th)) def = d;
        com.badlogic.gdx.graphics.Pixmap src = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(def[1]));
        t = new Texture[]{crop(src, def[2]), crop(src, def[3])};
        src.dispose();
        corridorSkin.put(th, t);
        return t;
    }

    private static Texture crop(com.badlogic.gdx.graphics.Pixmap src, String rect) {
        String[] r = rect.split(",");
        int x0 = Math.round(Float.parseFloat(r[0]) * src.getWidth()), y0 = Math.round(Float.parseFloat(r[1]) * src.getHeight());
        int x1 = Math.round(Float.parseFloat(r[2]) * src.getWidth()), y1 = Math.round(Float.parseFloat(r[3]) * src.getHeight());
        com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(x1 - x0, y1 - y0, src.getFormat());
        p.drawPixmap(src, 0, 0, x0, y0, x1 - x0, y1 - y0);
        Texture t = new Texture(p);
        p.dispose();
        t.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return t;
    }

    /** 통로 칸 하나: 바닥은 그림 바닥, 벽은 바위 (앞벽은 조금 밝게, 윗면은 어둡게). 월드 좌표에 맞춰 이어진다. */
    private boolean drawCorridorTile(int t, String th, int x, int y, float px, float py) {
        if (t != WorldMap.WALL && t != WorldMap.CORRIDOR) return false;
        Texture[] skin = corridorSkin(th);
        boolean wall = t == WorldMap.WALL;
        Texture tex = wall ? skin[1] : skin[0];
        float pw = tex.getWidth() * SKIN_SCALE, ph = tex.getHeight() * SKIN_SCALE;
        if (wall) {
            int below = map.tile(x, y - 1);
            boolean front = below == WorldMap.FLOOR || below == WorldMap.DOOR || below == WorldMap.CORRIDOR;
            float k = front ? 0.8f : 0.5f;
            batch.setColor(k, k, k, 1f);
        }
        batch.draw(tex, px, py, T, T, px / pw, -(py + T) / ph, (px + T) / pw, -py / ph);
        batch.setColor(Color.WHITE);
        if (!wall) {
            if (map.tile(x, y + 1) == WorldMap.WALL) batch.draw(art.world("ao_top"), px, py, T, T);
            if (map.tile(x - 1, y) == WorldMap.WALL) batch.draw(art.world("ao_left"), px, py, T, T);
            if (map.tile(x + 1, y) == WorldMap.WALL) batch.draw(art.world("ao_right"), px, py, T, T);
        }
        return true;
    }

    private void drawScenes() {
        for (Room r : map.rooms) {
            if (r.scene == null) continue;
            boolean camp = "central-camp".equals(r.scene.id);
            boolean towerCave="cave-tower".equals(r.scene.id);
            var inset = PaintedDoorways.exitFor(r);
            float overhang=towerCave?r.widthPx*.21f:camp?r.widthPx*.09f:inset!=null?r.widthPx*.18f:0;
            float top = r.originY + r.heightPx + overhang;
            if (r.originX > viewX1 || r.originX + r.widthPx < viewX0 || r.originY > viewY1 || top < viewY0) continue;
            if (camp) doorways.drawCrown(batch, r);
            Texture tex = sceneTexture(r);
            batch.draw(tex, r.originX, r.originY, r.widthPx, r.heightPx, 0, 0, tex.getWidth(), tex.getHeight(), r.flip, false);
            if (towerCave) drawTowerCaveMouth(r);
            if (inset != null) returnDoors.drawPainted(batch, r, inset, doorways);
        }
        // 오래 안 보인 그림은 메모리에서 내린다
        if (sceneTex.size() > 5) {
            for (Iterator<Map.Entry<String, Texture>> it = sceneTex.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Texture> en = it.next();
                if (time - sceneUsed.getOrDefault(en.getKey(), 0f) < 20f) continue;
                en.getValue().dispose();
                sceneUsed.remove(en.getKey());
                it.remove();
            }
        }
    }

    private void drawTowerCaveMouth(Room room) {
        for (Door door : room.doors) {
            if (!"exit".equals(door.name)) continue;
            TextureRegion mouth=art.cell("world/portals/tower-cave-mouth.png",1,1,0);
            // Register the throat to the actual transition. The roof extends
            // beyond the room painting, so bedrock cannot cut a flat cap across it.
            float w=door.halfWidth*4f, h=w*mouth.getRegionHeight()/mouth.getRegionWidth();
            float y=door.footY+door.dirY*door.triggerDepth;
            batch.setColor(.93f,.91f,.96f,1);
            batch.draw(mouth,door.gateX()-w*.5f,y-h*.265f,w,h);
            batch.setColor(Color.WHITE);
        }
    }

    /**
     * 앞쪽 가림 레이어: 그림 아래쪽 바위 테두리·난간·기둥을 용사와 몬스터 위에 한 번 더 그린다.
     * 걸을 수 있는 바닥(계단 포함) 위의 픽셀은 불러올 때 지워서, 서 있는 자리를 가리지 않는다.
     */
    private void drawSceneFronts() {
        for (Room r : map.rooms) {
            if (r.scene == null) continue;
            if (r.originX > viewX1 || r.originX + r.widthPx < viewX0 || r.originY > viewY1 || r.originY + r.heightPx < viewY0) continue;
            if(sceneLayers.hasFront("rooms/"+r.scene.id))continue;
            Texture tex = frontTexture(r);
            if (tex == null) continue;
            batch.setColor(Color.WHITE);
            batch.draw(tex, r.originX, r.originY, r.widthPx, r.heightPx, 0, 0, tex.getWidth(), tex.getHeight(), r.flip, false);
        }
        if (frontTex.size() > 4) {
            for (Iterator<Map.Entry<String, Texture>> it = frontTex.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Texture> en = it.next();
                if (en.getValue() == null || time - sceneUsed.getOrDefault(en.getKey(), 0f) < 20f) continue;
                en.getValue().dispose();
                it.remove();
            }
        }
    }

    private Texture frontTexture(Room r) {
        String key = frontKey(r); // 출구 혼합 마스크는 방 배치마다 다르다.
        if (frontTex.containsKey(key)) {
            sceneUsed.put(key, time);
            return frontTex.get(key);
        }
        return uploadFront(r, key, loader.take("front:" + key, () -> bakeFront(r)));
    }

    private static String frontKey(Room r) {
        return r.id + ":" + r.scene.image.replace(".png", "-front.png");
    }

    /** 다른 스레드에서 해도 되는 계산: 앞쪽 가림 그림을 읽고 바닥 자리를 지우고 가장자리를 흐린다. 그림이 없으면 main 이 null. */
    private Baked bakeFront(Room r) {
        Baked b = new Baked();
        String file = r.scene.image.replace(".png", "-front.png");
        if (Gdx.files.internal(file).exists()) {
            com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(file));
            WalkMask floor = WalkMask.get(r.scene.walkMask);
            if (floor == null) eraseWalkable(p, r.scene.walkable);
            else {
                // The reviewed pixel mask is authoritative, including reopened floor beside props.
                int w=p.getWidth(), h=p.getHeight();
                p.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
                for (int y=0;y<h;y++) for (int x=0;x<w;x++)
                    if ((p.getPixel(x,y)&255)!=0 && floor.floor((x+.5f)/w,(y+.5f)/h)) p.drawPixel(x,y,0);
            }
            if (passages.active) p = passages.joinForeground(p, r);
            b.main = p;
        }
        return b;
    }

    private Texture uploadFront(Room r, String key, Baked b) {
        Texture t = null;
        try {
            if (b.main != null) {
                BakeDigest.add("front:" + key, b.main);
                t = new Texture(b.main);
                t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            }
        } finally { b.dispose(); }
        frontTex.put(key, t);
        sceneUsed.put(key, time);
        return t;
    }

    /** 바닥 다각형(0~1 좌표) 안쪽 픽셀을 투명하게: 줄마다 다각형과 만나는 구간을 지운다. */
    private static void eraseWalkable(com.badlogic.gdx.graphics.Pixmap p, float[] poly) {
        int w = p.getWidth(), h = p.getHeight(), n = poly.length / 2;
        p.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
        p.setColor(0, 0, 0, 0);
        float[] xs = new float[n];
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            int k = 0;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float vi = poly[i * 2 + 1], vj = poly[j * 2 + 1];
                if ((vi > v) != (vj > v)) {
                    float u = poly[j * 2] + (v - vj) / (vi - vj) * (poly[i * 2] - poly[j * 2]);
                    xs[k++] = u * w;
                }
            }
            java.util.Arrays.sort(xs, 0, k);
            for (int i = 0; i + 1 < k; i += 2) {
                int x0 = Math.max(0, Math.round(xs[i])), x1 = Math.min(w, Math.round(xs[i + 1]));
                if (x1 > x0) p.fillRectangle(x0, y, x1 - x0, 1);
            }
        }
    }

    private Texture sceneTexture(Room r) {
        String file = r.scene.image;
        String key = r.id + ":" + file;
        sceneUsed.put(key, time);
        Texture t = sceneTex.get(key);
        if (t == null) t = uploadScene(key, loader.take("scene:" + key, () -> bakeScene(r)));
        return t;
    }

    /** 다른 스레드에서 해도 되는 계산: 방 그림을 읽고 가장자리를 흐려서 둘레의 땅·길 그림과 이어지게 한다. */
    private Baked bakeScene(Room r) {
        Baked b = new Baked();
        com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(r.scene.image));
        b.main = passages.active ? passages.joinRoom(p, r) : p;
        return b;
    }

    private Texture uploadScene(String key, Baked b) {
        Texture t;
        try {
            BakeDigest.add("scene:" + key, b.main);
            t = new Texture(b.main, true);
        } finally { b.dispose(); }
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        sceneTex.put(key, t);
        sceneUsed.put(key, time);
        return t;
    }

    /**
     * 렉 줄이기: 화면 둘레(near)에 들어온 방·길목·이음 그림은 미리 계산을 맡기고, 다 된 것은 한 프레임에 한 장씩 올린다.
     * 그래서 방에 들어설 때 그 자리에서 큰 그림을 계산하느라 멈추지 않는다.
     * 더 넓은 keep 범위 안의 그림은 내리지 않고, 그 밖으로 멀어진 미리 계산은 버린다.
     */
    private final java.util.List<Room> order = new java.util.ArrayList<>();
    private final PrefetchZones zones = new PrefetchZones();
    private final Map<Room, String[]> prefetchKeys = new HashMap<>();

    private void prefetch() {
        loader.newFrame(1);
        // near: 화면 밖 화면 폭의 0.6배까지 (걸어서 몇 초 거리) / keep: 1.25배까지는 내리지 않는다 (그래픽 메모리 절약)
        float w = viewX1 - viewX0, h = viewY1 - viewY0, m = Math.max(w, h), n = .6f * m, k = 1.25f * m;
        zones.clear();
        zones.addNear(viewX0 - n, viewY0 - n, viewX1 + n, viewY1 + n);
        zones.addKeep(viewX0 - k, viewY0 - k, viewX1 + k, viewY1 + k);
        // 순간이동 문(동굴 입구 등) 너머 방: 그 문이 near 안에 있으면 건너편 방도 미리 준비한다.
        for (Room r : map.rooms) for (Door d : r.doors) {
            if (d.link == null || !(d.link.transition || d.link.oneWay)) continue;
            if (d.footX < viewX0 - n || d.footX > viewX1 + n || d.footY < viewY0 - n || d.footY > viewY1 + n) continue;
            Door to = d.partner();
            if (to == null) continue;
            zones.addNear(to.arriveX - n / 2, to.arriveY - n / 2, to.arriveX + n / 2, to.arriveY + n / 2);
        }
        // 가까운 방부터 (화면 가운데에서 가까운 순)
        float cx = (viewX0 + viewX1) / 2f, cy = (viewY0 + viewY1) / 2f;
        order.clear();
        for (Room r : map.rooms) if (r.scene != null) order.add(r);
        order.sort(java.util.Comparator.comparingDouble(r -> Math.abs(r.originX + r.widthPx / 2f - cx) + Math.abs(r.originY + r.heightPx / 2f - cy)));
        for (Room r : order) {
            String[] keys = prefetchKeys.computeIfAbsent(r, q -> new String[]{q.id + ":" + q.scene.image, frontKey(q),
                    "scene:" + q.id + ":" + q.scene.image, "front:" + frontKey(q), "rooms/" + q.scene.id});
            String sceneKey = keys[0], fKey = keys[1], sceneJob = keys[2], frontJob = keys[3], layerKey = keys[4];
            // 캠프는 귀환문으로 순간이동해 오는 곳이라 미리 다가갈 수 없다: 늘 준비해 둔다.
            boolean near = r.type == RoomType.CAMP || zones.near(r.originX, r.originY, r.originX + r.widthPx, r.originY + r.heightPx);
            if (near) {
                if (!sceneTex.containsKey(sceneKey)) {
                    loader.request(sceneJob, () -> bakeScene(r));
                    if (loader.ready(sceneJob) && loader.spendUpload())
                        uploadScene(sceneKey, loader.take(sceneJob, () -> bakeScene(r)));
                } else sceneUsed.put(sceneKey, time);
                boolean layered = sceneLayers.has(layerKey);
                if (sceneLayers.hasFront(layerKey)) {
                    // 레이어 그림이 앞쪽을 대신 그리는 방: 옛 앞쪽 그림은 쓰지 않는다.
                } else if (!frontTex.containsKey(fKey)) {
                    loader.request(frontJob, () -> bakeFront(r));
                    if (loader.ready(frontJob) && loader.spendUpload())
                        uploadFront(r, fKey, loader.take(frontJob, () -> bakeFront(r)));
                } else sceneUsed.put(fKey, time);
                if (!layered) sceneForegrounds.prefetch(r, time);
            } else if (zones.keep(r.originX, r.originY, r.originX + r.widthPx, r.originY + r.heightPx)) {
                if (sceneTex.containsKey(sceneKey)) sceneUsed.put(sceneKey, time);
                if (frontTex.containsKey(fKey)) sceneUsed.put(fKey, time);
                sceneForegrounds.touch(r, time);
            } else {
                loader.drop(sceneJob);
                loader.drop(frontJob);
                sceneForegrounds.forget(r);
            }
        }
        passages.prefetch(zones);
        sceneLayers.prefetch(zones);
    }

    /** 이동 마스크를 용사 자리에서 가까운 것부터 미리 읽어 둔다 (처음 밟는 순간 멈칫하지 않게). */
    private void preloadWalkMasks() {
        float hx = g.hero.x, hy = g.hero.y;
        java.util.List<Object[]> items = new java.util.ArrayList<>();
        for (Room r : map.rooms) if (r.scene != null)
            items.add(new Object[]{r.scene.walkMask, dist(hx, hy, r.originX + r.widthPx / 2f, r.originY + r.heightPx / 2f)});
        for (Corridor c : map.corridors) if (c.scene != null)
            items.add(new Object[]{c.scene.walkMask, dist(hx, hy, c.originX + c.widthPx / 2f, c.originY + c.heightPx / 2f)});
        for (var j : map.paintedJoints)
            items.add(new Object[]{j.walkMask, dist(hx, hy, j.rect[0] + j.rect[2] / 2f, j.rect[1] + j.rect[3] / 2f)});
        items.sort(java.util.Comparator.comparingDouble(o -> (Float) o[1]));
        java.util.List<String> paths = new java.util.ArrayList<>();
        for (Object[] o : items) paths.add((String) o[0]);
        WalkMask.preloadInBackground(paths);
    }

    private static float dist(float ax, float ay, float bx, float by) {
        return Math.abs(ax - bx) + Math.abs(ay - by);
    }


    /** 그림 방의 출구: 잠기면 창살, 이어진 곳이 없으면 어둠으로 막혀 있다. */
    private void drawSceneDoors() {
        for (Room r : map.rooms) {
            if (r.scene == null) continue;
            for (Door d : r.doors) {
                if (!inView(d.footX, d.footY)) continue;
                if(roomGates.supports(d))continue;
                float px = -d.dirY, py = d.dirX;
                if (d.dead()) {
                    float size = d.halfWidth * 2f + 2.5f * T;
                    for (int k = 0; k < 3; k++) {
                        float cx = d.footX + d.dirX * T * (0.6f + k * 0.9f), cy = d.footY + d.dirY * T * (0.6f + k * 0.9f);
                        batch.setColor(0.01f, 0.01f, 0.02f, 0.8f);
                        batch.draw(art.glow, cx - size / 2f, cy - size / 2f, size, size);
                    }
                    batch.setColor(Color.WHITE);
                } else if (d.lockedByFight || (d.link != null && d.link.gateClosed)) {
                    if (drawGateArt(r, d)) continue;
                    int n = Math.max(2, Math.round(d.halfWidth * 2f / T));
                    for (int i = 0; i < n; i++) {
                        float o = -d.halfWidth + (i + 0.5f) * (d.halfWidth * 2f / n);
                        float cx = d.footX + px * o, cy = d.footY + py * o;
                        batch.draw(art.world("bars"), cx - T / 2f, cy - T / 2f, T, T);
                    }
                }
            }
        }
    }

    private final Map<String, Texture> gateTex = new HashMap<>();

    /**
     * 던전별 닫힌 출구 그림 (GPT 요청: sprites/gate_<던전>.png 정면, gate_<던전>_side.png 옆면).
     * 캠프의 던전 입구는 그 던전 것을 쓴다. 그림이 없으면 false (코드로 그린 창살).
     */
    private boolean drawGateArt(Room r, Door d) {
        String dungeon = r.dungeonId;
        if (r.type == RoomType.CAMP && d.link != null) dungeon = d.link.other(r).dungeonId;
        if (dungeon == null) return false;
        boolean side = !d.horizontal();
        String key = "sprites/gate_" + dungeon + (side ? "_side" : "") + ".png";
        Texture t = gateTex.get(key);
        if (t == null && !gateTex.containsKey(key)) {
            if (Gdx.files.internal(key).exists()) {
                t = new Texture(Gdx.files.internal(key), true); // 큰 그림을 작게 그리므로 밉맵
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            }
            gateTex.put(key, t);
        }
        if (t == null) return false;
        float span = d.halfWidth * 2f + T * 0.5f;
        if (!side) {
            // 정면: 출구 폭에 맞춰 가로로 늘이고, 아래 끝이 출구 발 자리에 온다
            float w = span, h = w * t.getHeight() / t.getWidth();
            batch.draw(t, d.footX - w / 2f, d.footY - T * 0.35f, w, h);
        } else {
            // 옆면: 출구 폭이 세로 길이, 왼쪽 출구면 좌우를 뒤집어 그린다
            float h = span + T, w = h * t.getWidth() / t.getHeight();
            batch.draw(t, d.footX - w / 2f, d.footY - span / 2f, w, h, 0, 0, t.getWidth(), t.getHeight(), d.dirX < 0, false);
        }
        return true;
    }

    /** 캠프의 봉인석과 열린 심연. 바닥 원근은 원화에 있으므로 회전/찌그러뜨리지 않는다. */
    private void drawFloorProps() {
        for (Prop p : map.props) {
            if (p.kind != Prop.Kind.SEAL) continue;
            float cx = p.centerX(T), cy = p.centerY(T);
            if (!inView(cx, cy)) continue;
            if (approachStones == null) approachStones = makeApproachStones();
            // Two broken courses pick up the camp paving between the rim and the south stairs.
            batch.setColor(.97f,.89f,.81f,.95f);
            batch.draw(approachStones,cx-48,cy-91,84,43);
            batch.draw(approachStones,cx+40,cy-63,-76,38);
            batch.setColor(.13f,.085f,.095f,.68f);
            batch.draw(art.glow,cx-83,cy-55,166,104);
            TextureRegion closed=art.cell(STAIRWELL,2,1,0), opened=art.cell(STAIRWELL,2,1,1);
            float a=abyssOpening*abyssOpening*(3-2*abyssOpening);
            batch.setColor(.96f,.92f,.90f,1);
            batch.draw(opened,cx-STAIR_W/2,cy-STAIR_H/2,STAIR_W,STAIR_H);
            // Keep the outer masonry planted. Two stone leaves recede under the side rails.
            if (a<1) {
                float left=.145f, right=.855f, half=.5f-left;
                drawCover(closed,cx,cy,left+half*a,.5f,left);
                drawCover(closed,cx,cy,.5f,right-half*a,.5f+half*a);
            }
            if (a>0) {
                // Ember tips sink toward the dark stairwell, below the hero layer.
                for (int i=0;i<7;i++) {
                    float t=(time*.38f+i*.38197f)%1f;
                    float angle=i*2.4f+t*.8f;
                    float radius=(1-t)*32;
                    float x=cx+MathUtils.cos(angle)*radius;
                    float y=cy+MathUtils.sin(angle)*radius*.48f+7-t*13;
                    float pulse=MathUtils.sin(t*MathUtils.PI)*a;
                    batch.setColor(.70f,.37f,.97f,.65f*pulse);
                    batch.draw(art.glow,x-3,y-2,6,4);
                    batch.setColor(.88f,.65f,1,pulse*.85f);
                    batch.draw(art.white(),x-1,y,2,1.5f);
                }
            }
            batch.setColor(Color.WHITE);
        }
    }

    /** A loose cluster of flagstones sampled from the camp's own left-hand path. */
    private static Texture makeApproachStones() {
        var source=new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal("scenes/natural-v1/central-camp.png"));
        int w=Math.round(source.getWidth()*.092f),h=Math.round(source.getHeight()*.076f);
        int left=Math.round(source.getWidth()*.269f),top=Math.round(source.getHeight()*.603f);
        var cut=new com.badlogic.gdx.graphics.Pixmap(w,h,com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        try {
            cut.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                float u=2*(x+.5f)/w-1,v=2*(y+.5f)/h-1;
                float fade=MathUtils.clamp((1-u*u-v*v)*2.6f,0,1);
                int rgba=source.getPixel(left+x,top+y);
                cut.drawPixel(x,y,(rgba & 0xffffff00) | Math.round((rgba & 255)*fade*fade*(3-2*fade)));
            }
            Texture texture=new Texture(cut,true);
            texture.setFilter(Texture.TextureFilter.MipMapLinearLinear,Texture.TextureFilter.Linear);
            return texture;
        } finally {cut.dispose();source.dispose();}
    }

    private void drawCover(TextureRegion closed,float cx,float cy,float from,float to,float dest) {
        float top=.157f,bottom=.76f;
        float u=closed.getU(),du=closed.getU2()-u;
        batch.draw(closed.getTexture(), cx+(dest-.5f)*STAIR_W, cy+(.5f-bottom)*STAIR_H,
                (to-from)*STAIR_W,(bottom-top)*STAIR_H, u+from*du,bottom,u+to*du,top);
    }

    /** Rivet centers measured on the closed stairwell tile. */
    private void drawSealSockets(float cx, float cy, float pulse) {
        float[] sockets={.303f,.497f,.695f};
        Color[] colors = {new Color(.5f,.75f,1f,1f), new Color(1f,.3f,.35f,1f), new Color(.4f,.95f,1f,1f)};
        for (int i = 0; i < 3; i++) {
            if (!g.state.bossDefeated(com.yongsa.model.AbsorbService.BOSS_ORDER[i])) continue;
            float x = cx + (sockets[i]-.5f)*STAIR_W;
            float y = cy + (.5f-.432f)*STAIR_H;
            Color c = g.absorb.canUnseal(g.state) ? Ui.GOLD : colors[i];
            batch.setColor(c.r,c.g,c.b,.65f * pulse);
            batch.draw(art.glow,x-10,y-7,20,14);
            batch.setColor(c.r,c.g,c.b,.9f);
            batch.draw(art.glow,x-3,y-2,6,4);
        }
    }

    /** 문양의 밝기 0~1: 흡수한 보스 수, 봉인이 풀리면 1. */
    public float sealPower() {
        if (g.state.unsealed) return 1f;
        int n = 0;
        for (String id : com.yongsa.model.AbsorbService.BOSS_ORDER) if (g.state.bossDefeated(id)) n++;
        return n / 3f;
    }

    public Color sealColor() {
        if (g.state.unsealed) return SEAL_DARK;
        return g.absorb.canUnseal(g.state) ? Ui.GOLD : new Color(0.55f, 0.45f, 0.8f, 1f);
    }

    private void drawTorches() {
        for (float[] t : torches) {
            if (!inView(t[0], t[1])) continue;
            if (t.length > 3 && passages.active) continue; // 길 그림에 등불이 그려져 있다 (빛만 남긴다)
            boolean alt = ((int) (time * 8 + t[2])) % 2 == 0;
            batch.draw(art.world(alt ? "torch_a" : "torch_b"), t[0] - 8, t[1], 16, 32);
        }
    }

    private static Color gateColor(Link l) {
        return new Color(0.7f, 0.5f, 1f, 1f);
    }

    public MapPainter(GameSession g, Art art, SpriteBatch batch, ShapeRenderer shapes) {
        this.g = g;
        abyssOpening = g.state.unsealed ? 1 : 0;
        this.art = art;
        this.batch = batch;
        this.shapes = shapes;
        this.map = g.map;
        this.T = g.balance.tileSize;
        this.theme = new String[map.width * map.height];
        for (Room r : map.rooms) {
            String th = r.dungeonId == null ? "CAMP" : r.dungeonId;
            for (int y = r.y0; y <= r.y1; y++) for (int x = r.x0; x <= r.x1; x++) theme[x + y * map.width] = th;
        }
        // 통로 바닥과 벽은 이어진 던전 테마. 앞벽(아래가 통로 바닥)에는 일정한 간격으로 횃불을 건다.
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                Corridor c = map.corridorAt(x, y);
                if (c == null) continue;
                theme[x + y * map.width] = c.theme;
                if (map.tile(x, y) == WorldMap.WALL && map.roomAt(x, y) == null && map.tile(x, y - 1) == WorldMap.CORRIDOR
                        && Math.floorMod(x * 7 + y * 3, 9) == 0)
                    torches.add(new float[]{x * T + T / 2f, y * T, MathUtils.random(10f), 1f});
            }
        }
        passages = new PassagePainter(map, T, loader);
        sceneForegrounds = new SceneForegrounds(passages, loader);
        sceneLayers = new SceneLayerPainter(map, passages, loader);
        preloadWalkMasks();
        returnDoors = new BossReturnDoors(g);
        roomGates = new RoomGates(map, doorways, art);
        // 타일 방(캠프) 윗벽에 일정한 간격으로 횃불을 건다. 문 바로 옆은 비운다. 그림 방은 그림에 불이 있다.
        for (Room r : map.rooms) {
            if (r.scene != null) continue;
            int y = r.y1;
            for (int x = r.x0 + 3; x <= r.x1 - 3; x += 5) {
                if (map.tile(x, y) != WorldMap.WALL || map.tile(x, y - 1) != WorldMap.FLOOR) continue;
                if (map.tile(x - 1, y) == WorldMap.DOOR || map.tile(x + 1, y) == WorldMap.DOOR
                        || map.tile(x - 2, y) == WorldMap.DOOR || map.tile(x + 2, y) == WorldMap.DOOR) continue;
                torches.add(new float[]{x * T + T / 2f, y * T, MathUtils.random(10f)});
            }
        }
    }

    /** 매 프레임: 문 움직임과 오래 안 쓴 그림 정리. */
    public void update(float dt, float time) {
        this.time = time;
        roomGates.update(dt);
        float target = g.state.unsealed ? 1 : 0;
        abyssOpening += MathUtils.clamp(target-abyssOpening, -dt/.65f, dt/.65f);
        sceneForegrounds.expire(time);
        returnDoors.update(dt);
    }

    public void setView(float x0, float y0, float x1, float y1) {
        viewX0 = x0;
        viewY0 = y0;
        viewX1 = x1;
        viewY1 = y1;
        prefetch();
    }

    private boolean inView(float x, float y) {
        return x > viewX0 - 64 && x < viewX1 + 64 && y > viewY0 - 64 && y < viewY1 + 64;
    }

    /** 바닥 층 (batch.begin 안에서): 길·땅 그림, 방 그림, 타일, 문턱, 출구, 봉인석, 횃불. */
    public void drawGround(int tx0, int ty0, int tx1, int ty1) {
        if (passages.active) {
            passages.drawGround(batch, viewX0, viewY0, viewX1, viewY1);
            passages.drawWalls(batch, viewX0, viewY0, viewX1, viewY1);
        }
        drawScenes();
        // In a painted world these tiles describe collision only. Drawing a wall tile
        // underneath a feathered joint leaves a straight masonry seam across bedrock.
        if (!passages.fullyPainted) drawTiles(tx0, ty0, tx1, ty1);
        if (passages.active) passages.drawFloors(batch, viewX0, viewY0, viewX1, viewY1);
        passages.joints.draw(batch, viewX0, viewY0, viewX1, viewY1);
        roomGates.drawThresholds(batch, viewX0, viewY0, viewX1, viewY1);
        drawSceneDoors();
        drawFloorProps();
        drawTorches();
    }

    /** 깊이 정렬에 끼는 맵 조각: 앞쪽 바위·기둥, 이음매 앞면, 문짝, 보스방 귀환 문. */
    public void collect(DepthSink out) {
        sceneLayers.collect(out,batch,viewX0,viewY0,viewX1,viewY1);
        for (Room r : map.rooms) {
            if (r.scene == null || r.originX > viewX1 || r.originX + r.widthPx < viewX0
                    || r.originY > viewY1 || r.originY + r.heightPx < viewY0) continue;
            if(sceneLayers.has("rooms/"+r.scene.id))continue;
            for (SceneForegrounds.Piece piece : sceneForegrounds.pieces(r, time)) {
                out.add(r.sceneY(piece.depth), () -> sceneForegrounds.draw(batch, r, piece));
            }
        }
        for (var front : passages.joints.fronts(viewX0, viewY0, viewX1, viewY1))
            out.add(front.depth, () -> front.draw(batch));
        for (var gate : roomGates.parts(batch, viewX0, viewY0, viewX1, viewY1))
            out.add(gate.depth(), () -> gate.draw().run());
        for (Room r : map.rooms) if (r.type == RoomType.BOSS && PaintedDoorways.exitFor(r) == null && inView(g.portalX(r), g.portalY(r)))
            out.add(g.portalY(r) + 8, () -> returnDoors.draw(batch, r));
    }

    /** 앞쪽 가림 레이어 (깊이 정렬한 것들 다음에 그린다). */
    public void drawLayerDebug() { sceneLayers.drawDebug(shapes); }

    public void drawFronts() {
        drawSceneFronts();
        sceneLayers.drawFronts(batch);
    }

    /** 맵의 불빛: 횃불, 모닥불, 화로, 봉인석, 던전 입구 봉인. */
    public void addLights(Lighting lighting, float blind) {
        for (float[] t : torches) {
            if (!inView(t[0], t[1] - 100)) continue;
            float fl = 0.85f + 0.1f * MathUtils.sin(time * 11f + t[2]) + 0.05f * MathUtils.sin(time * 23f + t[2] * 2);
            lighting.add(t[0], t[1] + 10, 175 * fl, 1f, 0.62f, 0.32f, 0.75f * (1f - blind));
        }
        for (Prop p : map.props) {
            float cx = p.centerX(T), cy = p.centerY(T);
            if (p.kind == Prop.Kind.CAMPFIRE) {
                float fl = 1f + 0.06f * MathUtils.sin(time * 9f) + 0.04f * MathUtils.sin(time * 17f);
                lighting.add(cx, cy, 360 * fl, 1f, 0.62f, 0.3f, 0.95f);
            } else if (p.kind == Prop.Kind.FORGE) {
                lighting.add(cx - 8, cy + 10, 150, 1f, 0.5f, 0.2f, 0.7f + 0.15f * MathUtils.sin(time * 7f));
            } else if (p.kind == Prop.Kind.SEAL) {
                Color c = sealColor();
                lighting.add(cx, cy, 150 + 60 * sealPower(), c.r, c.g, c.b, 0.35f + 0.4f * sealPower() + 0.08f * MathUtils.sin(time * 3f));
            }
        }
        for (Link l : map.links) {
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float cx = d.footX, cy = d.footY;
                if (!inView(cx, cy)) continue;
                if (l.gateClosed) {
                    Color c = gateColor(l);
                    lighting.add(cx, cy, 130, c.r, c.g, c.b, 0.6f + 0.15f * MathUtils.sin(time * 3f));
                } else if (d.lockedByFight) {
                    lighting.add(cx, cy, 80, 1f, 0.3f, 0.2f, 0.45f);
                }
            }
        }
    }

    /** 맵의 빛나는 효과 (더하기 섞기 batch 안에서): 횃불·모닥불·화로·봉인석, 잠긴 입구의 봉인 장벽. */
    public void drawGlow() {
        // 횃불, 모닥불, 화로의 불빛
        for (float[] t : torches) {
            if (!inView(t[0], t[1])) continue;
            float fl = 0.8f + 0.2f * MathUtils.sin(time * 13f + t[2]);
            batch.setColor(1f, 0.55f, 0.2f, 0.45f * fl);
            batch.draw(art.glow, t[0] - 22, t[1] + 2, 44, 44);
        }
        for (Prop p : map.props) {
            float px = p.x(T), py = p.y(T);
            if (p.kind == Prop.Kind.CAMPFIRE) {
                float fl = 0.85f + 0.15f * MathUtils.sin(time * 10f);
                batch.setColor(1f, 0.55f, 0.2f, 0.55f * fl);
                batch.draw(art.glow, p.centerX(T) - 48, p.centerY(T) - 32, 96, 80);
            } else if (p.kind == Prop.Kind.FORGE && !p.baked) {
                float fl = 0.7f + 0.3f * MathUtils.sin(time * 7f);
                batch.setColor(1f, 0.5f, 0.15f, 0.6f * fl);
                batch.draw(art.glow, px + 6, py + 26, 28, 28);
            } else if (p.kind == Prop.Kind.SEAL && sealPower() > 0) {
                Color c = sealColor();
                float cx = p.centerX(T), cy = p.centerY(T);
                float pulse = 0.75f + 0.25f * MathUtils.sin(time * 3f);
                float strength = g.state.finalBossDefeated ? .07f : .18f;
                batch.setColor(c.r, c.g, c.b, strength * sealPower() * pulse);
                batch.draw(art.glow, cx - 62, cy - 30, 124, 60);
                if (!g.state.unsealed) drawSealSockets(cx, cy, pulse);
            }
        }
        // 잠긴 던전 입구의 봉인 장벽
        for (Link l : map.links) {
            if (!l.gateClosed) continue;
            Color c = gateColor(l);
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float cx = d.footX, cy = d.footY;
                if (!inView(cx, cy)) continue;
                if (roomGates.supports(d)) continue; // The painted barrier carries its own seal.
                float pulse = 0.7f + 0.3f * MathUtils.sin(time * 3f);
                for (int i = 0; i < d.tilesX.length; i++) {
                    float x = d.tilesX[i] * T, y = d.tilesY[i] * T;
                    batch.setColor(c.r, c.g, c.b, 0.22f * pulse);
                    batch.draw(art.white(), x, y, T, T);
                    // 위로 흐르는 빛줄기
                    for (int k = 0; k < 3; k++) {
                        float off = ((time * 30f + k * 11 + i * 7) % T);
                        batch.setColor(c.r, c.g, c.b, 0.5f);
                        if (d.horizontal()) batch.draw(art.white(), x + 4 + k * 10, y + off, 2, 8);
                        else batch.draw(art.white(), x + off, y + 4 + k * 10, 8, 2);
                    }
                }
                batch.setColor(c.r, c.g, c.b, 0.9f * pulse);
                TextureRegion seal = art.world("seal");
                batch.draw(seal, cx - 24, cy - 24, 24, 24, 48, 48, 1f, 1f, time * 20f);
                batch.setColor(c.r, c.g, c.b, 0.35f * pulse);
                batch.draw(art.glow, cx - 60, cy - 60, 120, 120);
            }
        }
    }


    @Override
    public void dispose() {
        for (Texture t : sceneTex.values()) t.dispose();
        for (Texture t : frontTex.values()) if (t != null) t.dispose();
        for (Texture[] ts : corridorSkin.values()) for (Texture t : ts) t.dispose();
        for (Texture t : gateTex.values()) if (t != null) t.dispose();
        sceneForegrounds.dispose();
        sceneLayers.dispose();
        returnDoors.dispose();
        roomGates.dispose();
        doorways.dispose();
        if (approachStones!=null) approachStones.dispose();
        passages.dispose();
        loader.dispose();
    }
}
