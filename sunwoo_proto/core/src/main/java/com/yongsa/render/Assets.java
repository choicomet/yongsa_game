package com.yongsa.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TmxMapLoader;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;

/**
 * 그림 불러오기. 스프라이트는 프레임을 가로로 이어 붙인 PNG다.
 * 진짜 그림으로 바꿀 때는 같은 이름으로 덮어쓰고, 프레임 수가 바뀌면 FRAMES만 고친다.
 */
public class Assets implements Disposable {

    private static final ObjectMap<String, Integer> FRAMES = new ObjectMap<>();

    static {
        for (String d : new String[]{"hero_down", "hero_up", "hero_side"}) FRAMES.put(d, 3); // 대기, 걷기1, 걷기2
        for (String m : new String[]{"mob_left", "mob_right", "mob_top", "boss_left", "boss_right", "boss_top", "demon_king"}) {
            FRAMES.put(m, 2);
        }
        FRAMES.put("portal", 4);
    }

    private final ObjectMap<String, Texture> textures = new ObjectMap<>();
    private final ObjectMap<String, TextureRegion[]> frames = new ObjectMap<>();
    private Texture whiteTex;
    private TextureRegion white;
    private TiledMap tiledMap;

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
    }

    public TextureRegion[] frames(String name) {
        TextureRegion[] f = frames.get(name);
        if (f != null) return f;
        Texture t = texture("sprites/" + name + ".png");
        int n = FRAMES.get(name, 1);
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
        if (tiledMap != null) tiledMap.dispose();
    }
}
