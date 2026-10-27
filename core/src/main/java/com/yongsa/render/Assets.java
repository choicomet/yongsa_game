package com.yongsa.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.tiles.StaticTiledMapTile;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * 그림 불러오기. 스프라이트는 프레임을 가로로 이어 붙인 PNG다.
 * 진짜 그림으로 바꿀 때는 같은 이름으로 덮어쓰고, 프레임 수는 sprites/manifest.json에 적는다.
 */
public class Assets implements Disposable {

    /** sprites/manifest.json: 그림 이름 → 프레임 수 (tools/gen_assets.py가 만든다). */
    private final ObjectMap<String, Integer> frameCounts = new ObjectMap<>();

    private final ObjectMap<String, Texture> textures = new ObjectMap<>();
    private final ObjectMap<String, TextureRegion[]> frames = new ObjectMap<>();
    private Texture whiteTex;
    private TextureRegion white;
    private TiledMap tiledMap;
    private Texture lightTex;
    private TextureRegion light;

    public void load() {
        Pixmap p = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        p.setColor(Color.WHITE);
        p.fill();
        whiteTex = new Texture(p);
        p.dispose();
        white = new TextureRegion(whiteTex);
        TmxMapLoader.Parameters params = new TmxMapLoader.Parameters();
        params.textureMinFilter = Texture.TextureFilter.Nearest;
        params.textureMagFilter = Texture.TextureFilter.Nearest;
        tiledMap = new TmxMapLoader().load("maps/world.tmx", params);
        JsonValue manifest = new JsonReader().parse(Gdx.files.internal("sprites/manifest.json"));
        for (JsonValue v = manifest.child; v != null; v = v.next) frameCounts.put(v.name, v.asInt());
        light = radial(128);
        applyMainFloors();
    }

    /**
     * main 저장소의 바닥 그림 (tools/team_art.py 가 만든 maps/floors_main.png, 테마마다 n x n칸, 높이 = n·32).
     * 바닥 칸을 위치(x%n, y%n)로 골라 그림이 이어지게 깐다. 캠프·왼쪽·오른쪽·위쪽 테마만 바꾸고 투기장·마을은 그대로.
     */
    private void applyMainFloors() {
        if (!Gdx.files.internal("maps/floors_main.png").exists()) return;
        Texture t = texture("maps/floors_main.png");
        int n = t.getHeight() / 32, size = n * 32;
        int themes = t.getWidth() / size;
        StaticTiledMapTile[][] tiles = new StaticTiledMapTile[themes][n * n];
        for (int th = 0; th < themes; th++)
            for (int i = 0; i < n * n; i++)
                tiles[th][i] = new StaticTiledMapTile(new TextureRegion(t, th * size + (i % n) * 32, (i / n) * 32, 32, 32));
        TiledMapTileLayer ground = (TiledMapTileLayer) tiledMap.getLayers().get("ground");
        if (ground == null) return;
        for (int y = 0; y < ground.getHeight(); y++) {
            for (int x = 0; x < ground.getWidth(); x++) {
                TiledMapTileLayer.Cell c = ground.getCell(x, y);
                if (c == null || c.getTile() == null) continue;
                int idx = c.getTile().getId() - 2;          // gid 2 = 캠프 바닥 0 (tools/props.py tile_index + 1)
                if (idx < 0) continue;
                int th = idx / 5, kind = idx % 5;
                if (th >= themes || kind > 2) continue;
                // Tiled 의 y는 아래에서 위로 늘어난다: 그림의 위쪽 줄이 위에 오도록 뒤집는다
                c.setTile(tiles[th][(n - 1 - Math.floorMod(y, n)) * n + Math.floorMod(x, n)]);
            }
        }
    }

    /** 빛 그림: 가운데가 밝고 가장자리로 갈수록 부드럽게 사라지는 원. */
    private TextureRegion radial(int size) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        float c = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.hypot(x - c, y - c) / c;
                float a = d >= 1 ? 0 : (1 - d) * (1 - d) * (3 - 2 * (1 - d)) ;
                a = Math.max(0, Math.min(1, a));
                p.drawPixel(x, y, Color.rgba8888(a, a, a, 1f));
            }
        }
        lightTex = new Texture(p);
        lightTex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return new TextureRegion(lightTex);
    }

    public TextureRegion light() { return light; }

    public boolean has(String name) {
        return frameCounts.containsKey(name);
    }

    public TextureRegion[] frames(String name) {
        TextureRegion[] f = frames.get(name);
        if (f != null) return f;
        Texture t = texture("sprites/" + name + ".png");
        int n = frameCounts.get(name, 1);
        int fw = t.getWidth() / n;
        f = new TextureRegion[n];
        for (int i = 0; i < n; i++) f[i] = new TextureRegion(t, i * fw, 0, fw, t.getHeight());
        frames.put(name, f);
        return f;
    }

    public TextureRegion region(String name) {
        return frames(name)[0];
    }

    public TextureRegion frame(String name, float time, float frameTime, int from, int to) {
        TextureRegion[] f = frames(name);
        to = Math.min(to, f.length - 1);
        from = Math.min(from, to);
        return f[from + (int) (time / frameTime) % (to - from + 1)];
    }

    private Texture texture(String path) {
        Texture t = textures.get(path);
        if (t == null) {
            t = new Texture(Gdx.files.internal(path));
            t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textures.put(path, t);
        }
        return t;
    }

    public TextureRegion white() { return white; }
    public TiledMap tiledMap() { return tiledMap; }

    @Override
    public void dispose() {
        for (Texture t : textures.values()) t.dispose();
        textures.clear();
        frames.clear();
        if (whiteTex != null) whiteTex.dispose();
        if (lightTex != null) lightTex.dispose();
        if (tiledMap != null) tiledMap.dispose();
    }
}
