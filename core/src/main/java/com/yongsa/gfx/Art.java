package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.model.OreType;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 게임의 모든 그림. 그림 파일 없이 data/sprites.txt 의 픽셀 그림과
 * 코드로 그린 바닥·벽 타일을 한 장의 아틀라스로 묶는다. 화면에는 2배로 그린다.
 */
public class Art implements Disposable {
    public static final int SCALE = 2;
    public static final String[] THEMES = {"CAMP", "LEFT", "RIGHT", "TOP"};

    private final Map<Character, Integer> palette = new HashMap<>();
    private final Map<String, String[]> sources = new LinkedHashMap<>();
    private final TextureAtlas atlas;
    private final Map<String, TextureRegion> cache = new HashMap<>();

    public Art() {
        parse(Gdx.files.internal("data/sprites.txt").readString("UTF-8"));
        PixmapPacker packer = new PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, false);

        Pixmap white = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        white.setColor(Color.WHITE);
        white.fill();
        packer.pack("white", white);
        packer.pack("shadow", shadow());

        for (Map.Entry<String, String[]> e : sources.entrySet()) {
            packer.pack(e.getKey(), draw(e.getValue(), null));
        }
        // 광물마다 색을 바꾼 광맥과 보석
        packOreVariant(packer, OreType.BLACKIRON, 0x5d6678ff, 0xb7c3d8ff);
        packOreVariant(packer, OreType.BLOODMOON, 0xa3182eff, 0xff7d92ff);
        packOreVariant(packer, OreType.SKY, 0x2f8fd8ff, 0xc6f0ffff);
        // 무기마다 날 색을 바꾼 검
        packSword(packer, "blackiron", "sword", 0x4b5160ff, 0x9aa3b5ff);
        packSword(packer, "bloodmoon", "sword", 0xa3182eff, 0xff7d92ff);
        packSword(packer, "skyfall", "sword", 0x2f8fd8ff, 0xbdf3ffff);
        packSword(packer, "blackiron_awakened", "sword", 0x2a2f45ff, 0x8fe3ffff);
        packSword(packer, "bloodmoon_awakened", "sword", 0xd4143aff, 0xffd0d8ff);
        packSword(packer, "skyfall_awakened", "sword", 0x6a4cffff, 0xfff6a6ff);
        Map<Character, Integer> holy = new HashMap<>();
        holy.put('w', 0xfff3b0ff);
        holy.put('W', 0xf2c94cff);
        packer.pack("sword_holy_awakened", draw(sources.get("sword_holy"), holy));

        for (String theme : THEMES) {
            for (int v = 0; v < 4; v++) packer.pack("floor_" + theme + "_v" + v, floor(theme, v));
            packer.pack("walltop_" + theme, wallTop(theme));
            packer.pack("wallfront_" + theme, wallFront(theme));
        }
        packer.pack("bars", bars());

        atlas = packer.generateTextureAtlas(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest, false);
        packer.dispose();
    }

    private void parse(String text) {
        String name = null;
        java.util.List<String> rows = new java.util.ArrayList<>();
        for (String raw : text.split("\n")) {
            String line = raw.replace("\r", "");
            if (line.startsWith("#")) continue;
            if (line.startsWith("palette ")) {
                String[] p = line.split(" ");
                palette.put(p[1].charAt(0), (int) Long.parseLong(p[2], 16));
                continue;
            }
            if (line.startsWith("sprite ")) {
                if (name != null) sources.put(name, rows.toArray(new String[0]));
                name = line.split(" ")[1];
                rows.clear();
                continue;
            }
            if (name != null && !line.isEmpty()) rows.add(line);
        }
        if (name != null) sources.put(name, rows.toArray(new String[0]));
    }

    private Pixmap draw(String[] rows, Map<Character, Integer> override) {
        int w = rows[0].length(), h = rows.length;
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                char c = x < rows[y].length() ? rows[y].charAt(x) : '.';
                Integer col = override != null && override.containsKey(c) ? override.get(c) : palette.get(c);
                p.drawPixel(x, y, col == null ? 0 : col);
            }
        }
        return p;
    }

    private void packOreVariant(PixmapPacker packer, OreType ore, int dark, int light) {
        Map<Character, Integer> m = new HashMap<>();
        m.put('o', dark);
        m.put('O', light);
        packer.pack("vein_" + ore.name(), draw(sources.get("vein"), m));
        packer.pack("gem_" + ore.name(), draw(sources.get("gem"), m));
    }

    private void packSword(PixmapPacker packer, String name, String base, int dark, int light) {
        Map<Character, Integer> m = new HashMap<>();
        m.put('o', dark);
        m.put('O', light);
        packer.pack("sword_" + name, draw(sources.get(base), m));
    }

    private Pixmap shadow() {
        Pixmap p = new Pixmap(16, 6, Pixmap.Format.RGBA8888);
        p.setColor(0, 0, 0, 0.35f);
        p.fillRectangle(3, 0, 10, 6);
        p.fillRectangle(1, 1, 14, 4);
        return p;
    }

    private static int[] themeColors(String theme) {
        switch (theme) {
            case "CAMP":  return new int[]{0x6b5a45ff, 0x7a684fff, 0x594a39ff, 0x3a2f28ff, 0x5b4636ff, 0x47372bff};
            case "LEFT":  return new int[]{0x4d4440ff, 0x5a504bff, 0x3e3633ff, 0x2a2523ff, 0x4a403aff, 0x37302bff};
            case "RIGHT": return new int[]{0x4a2c30ff, 0x56343aff, 0x3b2226ff, 0x241215ff, 0x5c2a2fff, 0x431d22ff};
            default:      return new int[]{0x3b3350ff, 0x463d5eff, 0x2f2842ff, 0x1d182cff, 0x4a3d6bff, 0x362c52ff};
        }
    }

    private Pixmap floor(String theme, int variant) {
        int[] c = themeColors(theme);
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setColor(new Color(c[0]));
        p.fill();
        Random r = new Random(theme.hashCode() * 31L + variant);
        for (int i = 0; i < 14; i++) {
            p.drawPixel(r.nextInt(16), r.nextInt(16), r.nextBoolean() ? c[1] : c[2]);
        }
        if (theme.equals("RIGHT") || theme.equals("TOP")) {
            // 돌바닥 이음매
            p.setColor(new Color(c[2]));
            p.drawLine(0, 15, 15, 15);
            p.drawLine(variant % 2 == 0 ? 7 : 3, 8, variant % 2 == 0 ? 7 : 3, 15);
            p.drawLine(0, 7, 15, 7);
            p.drawLine(variant % 2 == 0 ? 11 : 13, 0, variant % 2 == 0 ? 11 : 13, 7);
        } else if (variant == 3) {
            p.setColor(new Color(c[2]));
            p.drawLine(3, 4, 7, 6);
            p.drawLine(7, 6, 9, 5);
        }
        return p;
    }

    private Pixmap wallTop(String theme) {
        int[] c = themeColors(theme);
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setColor(new Color(c[3]));
        p.fill();
        Random r = new Random(theme.hashCode());
        for (int i = 0; i < 6; i++) p.drawPixel(r.nextInt(16), r.nextInt(16), c[5]);
        return p;
    }

    private Pixmap wallFront(String theme) {
        int[] c = themeColors(theme);
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setColor(new Color(c[3]));
        p.fill();
        p.setColor(new Color(c[4]));
        p.fillRectangle(0, 4, 16, 12);
        p.setColor(new Color(c[5]));
        for (int row = 0; row < 3; row++) {
            int y = 4 + row * 4;
            p.drawLine(0, y, 15, y);
            int off = row % 2 == 0 ? 0 : 4;
            for (int x = off; x < 16; x += 8) p.drawLine(x, y, x, y + 3);
        }
        p.setColor(0, 0, 0, 0.35f);
        p.drawLine(0, 15, 15, 15);
        return p;
    }

    private Pixmap bars() {
        Pixmap p = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0x2a2a33ff));
        p.fillRectangle(0, 1, 16, 2);
        p.fillRectangle(0, 13, 16, 2);
        p.setColor(new Color(0x8a8f9cff));
        for (int x = 1; x < 16; x += 4) p.fillRectangle(x, 0, 2, 16);
        p.setColor(new Color(0xc9ced8ff));
        for (int x = 1; x < 16; x += 4) p.drawLine(x, 0, x, 15);
        return p;
    }

    public TextureRegion get(String name) {
        TextureRegion r = cache.get(name);
        if (r == null) {
            r = atlas.findRegion(name);
            if (r == null) throw new IllegalArgumentException("없는 그림: " + name);
            cache.put(name, r);
        }
        return r;
    }

    public TextureRegion white() {
        return get("white");
    }

    public TextureRegion vein(OreType t) {
        return get("vein_" + t.name());
    }

    public TextureRegion gem(OreType t) {
        return get("gem_" + t.name());
    }

    /** 무기 그림. 성검은 봉인 여부, 다른 검은 각성 여부에 따라 다르다. */
    public TextureRegion sword(String defId, boolean awakened) {
        switch (defId) {
            case "holy_sealed": return get("sword_holy");
            case "holy_awakened": return get("sword_holy_awakened");
            default: return get("sword_" + defId + (awakened ? "_awakened" : ""));
        }
    }

    public TextureRegion floor(String theme, int x, int y) {
        int v = Math.floorMod(x * 7 + y * 13 + (x * y) % 5, 9);
        return get("floor_" + theme + "_v" + (v < 6 ? 0 : v - 5));
    }

    @Override
    public void dispose() {
        atlas.dispose();
    }
}
