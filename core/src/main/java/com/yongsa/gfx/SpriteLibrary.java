package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 스프라이트 묶음. data/sprites.json 에 적힌 CraftPix 시트가 실제로 있으면 그것을 쓰고,
 * 없으면 placeholder_art.txt 로 만든 임시 그림을 쓴다.
 * 그래서 에셋 파일을 넣고 sprites.json 의 크기·프레임 수만 맞추면 코드 수정 없이 교체된다.
 */
public class SpriteLibrary implements Disposable {

    /** 한 대상(용사, 슬라임, 모루 등)의 그리기 정보. */
    public static class Entry {
        public final Map<String, Animation<TextureRegion>> anims = new HashMap<>();
        /** 발 위치 기준 원점 (그림 왼쪽 아래에서의 거리, 그려진 픽셀 단위). */
        public float originX, originY;
        public float scale = 1f;
        /** 원본 그림이 왼쪽을 보고 있으면 true. */
        public boolean facesLeft;
        /** 방향별 동작(idle_down, walk_left …)을 갖고 있으면 true. 이때는 좌우 뒤집기를 하지 않는다. */
        public boolean directional;
        /** 무기 그림이 원래 가리키는 각도(도). 오른쪽=0, 위=90. */
        public float pointAngle = 45f;
        public boolean fromAsset;

        /** 방향이 있는 그림이면 "이름_방향"을 먼저 찾는다. */
        public TextureRegion frame(String name, String dir, float time) {
            if (directional && dir != null) {
                Animation<TextureRegion> a = anims.get(name + "_" + dir);
                if (a == null) a = anims.get("idle_" + dir);
                if (a != null) return a.getKeyFrame(time);
            }
            return frame(name, time);
        }

        public Animation<TextureRegion> anim(String name) {
            Animation<TextureRegion> a = anims.get(name);
            if (a == null && name.equals("walk")) a = anims.get("move");
            if (a == null && name.equals("move")) a = anims.get("walk");
            if (a == null && directional) a = anims.get(name + "_down");
            if (a == null && directional) a = anims.get("idle_down");
            if (a == null) a = anims.get("idle");
            if (a == null && !anims.isEmpty()) a = anims.values().iterator().next();
            return a;
        }

        public TextureRegion frame(String name, float time) {
            Animation<TextureRegion> a = anim(name);
            return a == null ? null : a.getKeyFrame(time);
        }
    }

    private final Map<String, Entry> entries = new HashMap<>();
    private final List<Texture> textures = new ArrayList<>();
    private final TextureAtlas atlas;
    private final Map<String, TextureRegion> placeholders = new HashMap<>();
    private final Map<String, Texture> textureCache = new HashMap<>();

    public SpriteLibrary(FileHandle artFile, FileHandle... manifests) {
        // 1) 임시 그림을 하나의 텍스처로 묶는다
        Map<String, Pixmap> pix = PixelArt.load(artFile, 2);
        PixmapPacker packer = new PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, false);
        for (Map.Entry<String, Pixmap> e : pix.entrySet()) {
            packer.pack(e.getKey(), e.getValue());
        }
        atlas = packer.generateTextureAtlas(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest, false);
        packer.dispose();
        for (Pixmap p : pix.values()) p.dispose();
        // PixmapPacker 는 이름 끝의 _숫자를 인덱스로 분리하므로 원래 이름으로 되돌린다
        for (TextureAtlas.AtlasRegion r : atlas.getRegions()) {
            placeholders.put(r.index >= 0 ? r.name + "_" + r.index : r.name, r);
        }

        // 2) 기본(임시) 정의
        definePlaceholders();

        // 3) 매니페스트에 적힌 실제 에셋으로 덮어쓴다 (뒤에 오는 것이 우선)
        for (FileHandle manifest : manifests) {
            if (manifest != null && manifest.exists()) applyManifest(manifest);
        }
    }

    public TextureRegion placeholder(String name) {
        return placeholders.get(name);
    }

    public Entry get(String key) {
        return entries.get(key);
    }

    private void definePlaceholders() {
        actor("hero", 6, new String[][]{{"idle", "2", "hero_0", "hero_1"},
                {"walk", "8", "hero_walk_0", "hero_walk_1", "hero_walk_2", "hero_walk_1"}});
        actor("slime", 4, new String[][]{{"idle", "3", "slime_0", "slime_1"}, {"move", "6", "slime_0", "slime_1"}});
        actor("charger", 2, new String[][]{{"idle", "2", "charger_0", "charger_1"}, {"move", "6", "charger_0", "charger_1"}});
        actor("watcher", 2, new String[][]{{"idle", "3", "watcher_0", "watcher_1"}});
        actor("boss_mine", 2, new String[][]{{"idle", "2", "boss_mine_0", "boss_mine_1"}, {"move", "4", "boss_mine_0", "boss_mine_1"}});
        actor("boss_fortress", 2, new String[][]{{"idle", "2", "boss_fortress_0", "boss_fortress_1"}, {"move", "4", "boss_fortress_0", "boss_fortress_1"}});
        actor("boss_temple", 2, new String[][]{{"idle", "2", "boss_temple_0", "boss_temple_1"}});
        for (String s : new String[]{"sword", "hammer", "vein", "campfire_log", "bedroll", "furnace", "anvil", "workbench",
                "crate", "lantern_table", "torch", "brazier", "pillar", "door_bars_h", "door_bars_v", "shortcut_door",
                "rune_circle", "icon_mineral", "icon_heart", "icon_shock"}) {
            actor(s, 0, new String[][]{{"idle", "1", s}});
        }
        // 무기는 손잡이를 원점으로 둔다
        entries.get("sword").originX = 2;
        entries.get("sword").originY = 2;
        entries.get("hammer").originX = 8;
        entries.get("hammer").originY = 2;
    }

    private void actor(String key, int originY, String[][] anims) {
        Entry e = new Entry();
        for (String[] a : anims) {
            Array<TextureRegion> frames = new Array<>();
            for (int i = 2; i < a.length; i++) {
                TextureRegion r = placeholders.get(a[i]);
                if (r == null) throw new IllegalStateException("임시 그림 없음: " + a[i]);
                frames.add(r);
            }
            Animation<TextureRegion> anim = new Animation<>(1f / Float.parseFloat(a[1]), frames, Animation.PlayMode.LOOP);
            e.anims.put(a[0], anim);
        }
        TextureRegion first = e.anims.values().iterator().next().getKeyFrame(0);
        e.originX = first.getRegionWidth() / 2f;
        e.originY = originY;
        entries.put(key, e);
    }

    private void applyManifest(FileHandle manifest) {
        JsonValue root = new JsonReader().parse(manifest);
        for (JsonValue item = root.child; item != null; item = item.next) {
            if (item.name.startsWith("_")) continue;
            try {
                Entry e = loadEntry(item);
                if (e != null) entries.put(item.name, e);
            } catch (Exception ex) {
                Gdx.app.error("SpriteLibrary", item.name + " 에셋을 읽지 못해 임시 그림을 씁니다: " + ex.getMessage());
            }
        }
    }

    /** 파일이 하나도 없으면 null (임시 그림 유지). */
    private Entry loadEntry(JsonValue item) {
        Entry base = entries.get(item.name);
        Entry e = new Entry();
        e.scale = item.getFloat("scale", 1f);
        e.facesLeft = item.getBoolean("facesLeft", false);
        e.directional = item.getBoolean("directional", false);
        e.pointAngle = item.getFloat("pointAngle", base != null ? base.pointAngle : 45f);
        JsonValue anims = item.get("anims");
        boolean any = false;
        if (anims != null) {
            for (JsonValue a = anims.child; a != null; a = a.next) {
                Animation<TextureRegion> anim = loadAnim(a, item);
                if (anim != null) {
                    e.anims.put(a.name, anim);
                    any = true;
                }
            }
        } else {
            Animation<TextureRegion> anim = loadAnim(item, item);
            if (anim != null) {
                e.anims.put("idle", anim);
                any = true;
            }
        }
        if (!any) return null;
        // 일부 동작만 넣은 경우 나머지는 임시 그림을 유지
        if (base != null && !e.directional) {
            for (Map.Entry<String, Animation<TextureRegion>> b : base.anims.entrySet()) {
                e.anims.putIfAbsent(b.getKey(), b.getValue());
            }
        }
        TextureRegion first = e.anims.values().iterator().next().getKeyFrame(0);
        e.originX = item.getFloat("originX", first.getRegionWidth() / 2f);
        e.originY = item.getFloat("originY", 0);
        e.fromAsset = true;
        return e;
    }

    private Animation<TextureRegion> loadAnim(JsonValue a, JsonValue parent) {
        String file = a.getString("file", null);
        if (file == null) return null;
        FileHandle fh = Gdx.files.internal(file);
        if (!fh.exists()) return null;
        Texture tex = textureCache.get(file);
        if (tex == null) {
            tex = new Texture(fh);
            tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
            textures.add(tex);
            textureCache.put(file, tex);
        }
        int fw = a.getInt("frameWidth", parent.getInt("frameWidth", tex.getWidth()));
        int fh2 = a.getInt("frameHeight", parent.getInt("frameHeight", tex.getHeight()));
        int frames = a.getInt("frames", Math.max(1, tex.getWidth() / fw));
        int row = a.getInt("row", 0);
        int col = a.getInt("col", 0);
        float fps = a.getFloat("fps", 8f);
        Array<TextureRegion> sheet = new Array<>();
        int perRow = Math.max(1, tex.getWidth() / fw);
        for (int i = 0; i < frames; i++) {
            int idx = col + i;
            int r = row + idx / perRow;
            int c = idx % perRow;
            sheet.add(new TextureRegion(tex, c * fw, r * fh2, fw, fh2));
        }
        // frameOrder: [0,1,2,1] 처럼 프레임 재생 순서를 지정할 수 있다
        Array<TextureRegion> regions = sheet;
        JsonValue order = a.get("frameOrder");
        if (order != null) {
            regions = new Array<>();
            for (JsonValue o = order.child; o != null; o = o.next) regions.add(sheet.get(o.asInt()));
        }
        boolean loop = a.getBoolean("loop", true);
        return new Animation<>(1f / fps, regions, loop ? Animation.PlayMode.LOOP : Animation.PlayMode.NORMAL);
    }

    @Override
    public void dispose() {
        atlas.dispose();
        for (Texture t : textures) t.dispose();
    }
}
