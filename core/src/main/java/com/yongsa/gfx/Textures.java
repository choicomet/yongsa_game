package com.yongsa.gfx;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

/** 코드로 만드는 기본 텍스처: 흰 점, 원형 빛, 그림자, 불꽃. */
public class Textures implements Disposable {
    public final Texture whiteTex;
    public final TextureRegion white;
    public final Texture glow;
    public final Texture shadow;
    public final Texture flame;

    public Textures() {
        Pixmap p = new Pixmap(4, 4, Pixmap.Format.RGBA8888);
        p.setColor(1, 1, 1, 1);
        p.fill();
        whiteTex = new Texture(p);
        p.dispose();
        white = new TextureRegion(whiteTex, 1, 1, 2, 2);

        glow = radial(128, 2.0f);
        shadow = ellipse(32, 16);
        flame = flame(16, 24);
    }

    private static Texture radial(int size, float power) {
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        float c = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float d = (float) Math.sqrt((x - c) * (x - c) + (y - c) * (y - c)) / c;
                float a = (float) Math.pow(Math.max(0, 1 - d), power);
                p.setColor(1, 1, 1, a);
                p.drawPixel(x, y);
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return t;
    }

    private static Texture ellipse(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        float cx = (w - 1) / 2f, cy = (h - 1) / 2f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float dx = (x - cx) / cx, dy = (y - cy) / cy;
                float d = dx * dx + dy * dy;
                p.setColor(0, 0, 0, d <= 1 ? 0.45f * (1 - d * 0.5f) : 0);
                p.drawPixel(x, y);
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return t;
    }

    /** 위로 뾰족한 불꽃 모양 (흰색, 색은 그릴 때 입힌다). */
    private static Texture flame(int w, int h) {
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        float cx = (w - 1) / 2f;
        for (int y = 0; y < h; y++) {
            float t = y / (float) (h - 1); // 0 위, 1 아래
            float half = (float) (Math.sin(Math.min(1, t * 1.15) * Math.PI * 0.62) * (w / 2f));
            for (int x = 0; x < w; x++) {
                float d = Math.abs(x - cx);
                float a = d <= half ? 1f - (d / Math.max(1, half)) * 0.5f : 0;
                p.setColor(1, 1, 1, a);
                p.drawPixel(x, y);
            }
        }
        Texture tex = new Texture(p);
        tex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        p.dispose();
        return tex;
    }

    @Override
    public void dispose() {
        whiteTex.dispose();
        glow.dispose();
        shadow.dispose();
        flame.dispose();
    }
}
