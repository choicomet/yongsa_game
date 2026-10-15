package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.world.Corridor;
import com.yongsa.world.WorldMap;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GPT 길 그림(assets/scenes/passages/)으로 방 사이를 그린다.
 * <ul>
 *   <li>땅: 방과 길 바깥 빈 곳 전체를 그 던전의 땅 그림으로 채운다. 던전 경계에서는 두 땅이 섞인다.</li>
 *   <li>길: 통로의 곧은 구간마다 가로(h)·세로(v) 길 그림을 길이 방향으로 거울 반복해 깐다.
 *       그림의 바닥 띠가 걸을 수 있는 3칸 폭에 맞도록 크기를 맞춘다.</li>
 *   <li>바닥 띠: 모든 길 그림을 깐 뒤 바닥 띠만 한 번 더 그려서, 꺾이는 곳이나 이웃 길의 벽이 바닥을 덮지 않게 한다.
 *       방 그림의 출구 쪽으로는 바닥 띠가 흐려지며 이어진다.</li>
 * </ul>
 * 그림이 없으면 아무것도 하지 않는다 (예전처럼 타일 통로를 그린다).
 */
class PassagePainter implements Disposable {
    private static final String DIR = "scenes/passages/";
    /** 방 그림과 같은 밀도 (30칸 방 = 그림 1672 픽셀). 땅 그림에 쓴다. */
    private static final float SCENE_SCALE = 30f * 32f / 1672f;
    /** 그림 속 바닥 폭 (칸). 걸을 수 있는 폭 3칸보다 조금 넓게 그려서 벽 위를 걷는 것처럼 보이지 않게 한다. */
    private static final float FLOOR_TILES = 3.4f;
    /** 땅 섞는 칸 묶음 크기와 섞이는 거리 (칸). */
    private static final int BLOCK = 2;
    private static final float BLEND = 10f;
    private static final float GROUND_TINT = 0.78f;
    private static final String[] THEMES = {"CAMP", "LEFT", "RIGHT", "TOP"};

    /**
     * 테마별 그림 이름과 바닥 띠 위치 (그림 크기 대비 0~1):
     * 가로 그림은 위에서부터 바닥 띠 시작·끝, 세로 그림은 왼쪽에서부터 시작·끝. 그림을 보고 잰 값이다.
     */
    private static final Object[][] DEFS = {
            {"CAMP", "camp", 0.28f, 0.68f, 0.27f, 0.70f},
            {"LEFT", "mine", 0.30f, 0.70f, 0.25f, 0.72f},
            {"RIGHT", "citadel", 0.30f, 0.72f, 0.23f, 0.77f},
            {"TOP", "tower", 0.30f, 0.72f, 0.13f, 0.85f},
    };

    private static final class Skin {
        Texture h, hFloor, v, vFloor, ground;
        int hw, hh, vw, vh;
        float hTop, hBot, vL, vR;
        /** 바닥 띠 그림이 원본에서 차지하는 범위 (0~1). */
        float hfTop, hfBot, vfL, vfR;
    }

    /** 통로의 곧은 구간. */
    private static final class Seg {
        boolean horizontal;
        /** 가운데 줄 좌표 (월드 픽셀): 가로면 y, 세로면 x. */
        float c;
        /** 길이 방향 범위 (월드 픽셀, a < b). */
        float a, b;
        /** 바닥 띠 범위: 방 출구 쪽은 방 그림 바닥이 끝나는 곳까지. */
        float fa, fb;
        /** 양 끝이 방 출구인가 (아니면 꺾이는 곳). */
        boolean mouthA, mouthB;
        String theme;
    }

    private final WorldMap map;
    private final float T;
    private final Map<String, Skin> skins = new HashMap<>();
    private final List<Seg> segs = new ArrayList<>();
    /** 칸 묶음 꼭짓점마다 테마별 땅 비중 [(by * gw + bx) * 4 + 테마]. */
    private float[] weight;
    private int gw, gh;
    private final float[] verts = new float[20];
    final boolean active;

    PassagePainter(WorldMap map, float tileSize, String[] theme) {
        this.map = map;
        this.T = tileSize;
        boolean all = true;
        for (Object[] d : DEFS) {
            String n = (String) d[1];
            all &= Gdx.files.internal(DIR + n + "-h.png").exists() && Gdx.files.internal(DIR + n + "-v.png").exists()
                    && Gdx.files.internal(DIR + n + "-ground.png").exists();
        }
        active = all;
        if (!active) return;
        for (Object[] d : DEFS) skins.put((String) d[0], load(d));
        for (Corridor c : map.corridors) buildSegs(c);
        buildWeights(theme);
    }

    // ───────────────────────── 불러오기

    private static Skin load(Object[] d) {
        String n = (String) d[1];
        Skin s = new Skin();
        s.hTop = (Float) d[2];
        s.hBot = (Float) d[3];
        s.vL = (Float) d[4];
        s.vR = (Float) d[5];
        float m = 0.05f;
        s.hfTop = Math.max(0f, s.hTop - m);
        s.hfBot = Math.min(1f, s.hBot + m);
        s.vfL = Math.max(0f, s.vL - m);
        s.vfR = Math.min(1f, s.vR + m);

        Pixmap h = rgba(Gdx.files.internal(DIR + n + "-h.png"));
        s.hw = h.getWidth();
        s.hh = h.getHeight();
        s.hFloor = texture(band(h, true, s.hfTop, s.hfBot, m), true, false);
        featherAcross(h, true, 0.14f);
        s.h = texture(h, true, false);

        Pixmap v = rgba(Gdx.files.internal(DIR + n + "-v.png"));
        s.vw = v.getWidth();
        s.vh = v.getHeight();
        s.vFloor = texture(band(v, false, s.vfL, s.vfR, m), false, true);
        featherAcross(v, false, 0.14f);
        s.v = texture(v, false, true);

        s.ground = new Texture(seamless(rgba(Gdx.files.internal(DIR + n + "-ground.png"))), true);
        s.ground.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        s.ground.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
        return s;
    }

    private static Pixmap rgba(FileHandle f) {
        return rgba(new Pixmap(f));
    }

    private static Pixmap rgba(Pixmap p) {
        if (p.getFormat() == Pixmap.Format.RGBA8888) return p;
        Pixmap q = new Pixmap(p.getWidth(), p.getHeight(), Pixmap.Format.RGBA8888);
        q.setBlending(Pixmap.Blending.None);
        q.drawPixmap(p, 0, 0);
        p.dispose();
        return q;
    }

    /** 거울 반복 텍스처 (반복하는 방향만), 밉맵. pixmap 은 여기서 버린다. */
    private static Texture texture(Pixmap p, boolean repeatU, boolean repeatV) {
        Texture t = new Texture(p, true);
        t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        t.setWrap(repeatU ? Texture.TextureWrap.MirroredRepeat : Texture.TextureWrap.ClampToEdge,
                repeatV ? Texture.TextureWrap.MirroredRepeat : Texture.TextureWrap.ClampToEdge);
        p.dispose();
        return t;
    }

    /** 길 폭 방향 바깥 가장자리(벽 바깥쪽)를 투명하게 흐려서 땅과 이어지게 한다. */
    private static void featherAcross(Pixmap p, boolean horizontal, float frac) {
        int w = p.getWidth(), h = p.getHeight(), n = horizontal ? h : w;
        int f = Math.max(1, Math.round(n * frac));
        ByteBuffer px = p.getPixels();
        for (int i = 0; i < f; i++) {
            float a = (i + 0.5f) / f;
            a = a * a * (3 - 2 * a);
            scaleAlpha(px, w, h, horizontal, i, a);
            scaleAlpha(px, w, h, horizontal, n - 1 - i, a);
        }
    }

    private static void scaleAlpha(ByteBuffer px, int w, int h, boolean row, int line, float a) {
        int count = row ? w : h;
        for (int k = 0; k < count; k++) {
            int x = row ? k : line, y = row ? line : k;
            int i = (y * w + x) * 4 + 3;
            px.put(i, (byte) Math.round((px.get(i) & 0xff) * a));
        }
    }

    /**
     * 땅 그림을 이음매 없이 반복되게: 반 칸 밀어 놓은 같은 그림과 섞는데, 가장자리로 갈수록 밀어 놓은 쪽 비중을 키운다.
     * 가장자리에서는 밀어 놓은 그림(그 자리에서 이어지는 안쪽 무늬)만 남아 맞은편 가장자리와 이어진다.
     * 거울 반복보다 대칭 무늬가 덜 보인다. 받은 pixmap 은 버리고 새 것을 돌려준다.
     */
    static Pixmap seamless(Pixmap src) {
        int w = src.getWidth(), h = src.getHeight();
        Pixmap out = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        ByteBuffer a = src.getPixels(), o = out.getPixels();
        for (int y = 0; y < h; y++) {
            int sy = (y + h / 2) % h;
            float wy = edge(y, h), wsy = edge(sy, h);
            for (int x = 0; x < w; x++) {
                int sx = (x + w / 2) % w;
                float w1 = edge(x, w) * wy, w2 = edge(sx, w) * wsy;
                float k = w1 + w2 <= 0 ? 0.5f : w2 / (w1 + w2);
                int i = (y * w + x) * 4, j = (sy * w + sx) * 4;
                for (int c = 0; c < 3; c++) {
                    float v = (a.get(i + c) & 0xff) * (1 - k) + (a.get(j + c) & 0xff) * k;
                    o.put(i + c, (byte) Math.round(v));
                }
                o.put(i + 3, (byte) 255);
            }
        }
        src.dispose();
        return out;
    }

    /** 가장자리에서 0, 가운데에서 1. */
    private static float edge(int i, int n) {
        float d = Math.min(i + 0.5f, n - i - 0.5f) / (n / 2f);
        return d * d;
    }

    /** 방 그림 테두리를 투명하게 흐린다 (가로·세로 모두 그림 짧은 변의 8%). 받은 pixmap 은 버리고 새 것을 돌려준다. */
    static Pixmap featherEdges(Pixmap src) {
        Pixmap p = rgba(src);
        int w = p.getWidth(), h = p.getHeight();
        int f = Math.max(2, Math.round(Math.min(w, h) * 0.08f));
        ByteBuffer px = p.getPixels();
        for (int i = 0; i < f; i++) {
            float a = (i + 0.5f) / f;
            a = a * a * (3 - 2 * a);
            scaleAlpha(px, w, h, true, i, a);
            scaleAlpha(px, w, h, true, h - 1 - i, a);
            scaleAlpha(px, w, h, false, i, a);
            scaleAlpha(px, w, h, false, w - 1 - i, a);
        }
        return p;
    }

    /** 바닥 띠만 잘라낸 그림: 띠 바깥 m 만큼은 투명해지며 흐려진다. */
    private static Pixmap band(Pixmap src, boolean horizontal, float f0, float f1, float m) {
        int w = src.getWidth(), h = src.getHeight();
        int n = horizontal ? h : w;
        int i0 = Math.round(f0 * n), i1 = Math.round(f1 * n);
        int bw = horizontal ? w : i1 - i0, bh = horizontal ? i1 - i0 : h;
        Pixmap p = new Pixmap(bw, bh, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        p.drawPixmap(src, 0, 0, horizontal ? 0 : i0, horizontal ? i0 : 0, bw, bh);
        int f = Math.max(1, Math.round(m * n));
        ByteBuffer px = p.getPixels();
        int len = horizontal ? bh : bw;
        for (int i = 0; i < f && i < len / 2; i++) {
            float a = (i + 0.5f) / f;
            a = a * a * (3 - 2 * a);
            scaleAlpha(px, bw, bh, horizontal, i, a);
            scaleAlpha(px, bw, bh, horizontal, len - 1 - i, a);
        }
        return p;
    }

    // ───────────────────────── 통로 구간

    private void buildSegs(Corridor c) {
        List<int[]> p = c.path;
        if (p.size() < 2) return;
        int fa = firstOpen(p, false), fb = firstOpen(p, true);
        int start = 0;
        while (start < p.size() - 1) {
            int dx = Integer.signum(p.get(start + 1)[0] - p.get(start)[0]);
            int dy = Integer.signum(p.get(start + 1)[1] - p.get(start)[1]);
            int end = start + 1;
            while (end + 1 < p.size() && p.get(end + 1)[0] - p.get(end)[0] == dx && p.get(end + 1)[1] - p.get(end)[1] == dy) end++;
            Seg s = new Seg();
            s.theme = c.theme;
            s.horizontal = dy == 0;
            int[] ps = p.get(start), pe = p.get(end);
            s.c = ((s.horizontal ? ps[1] : ps[0]) + 0.5f) * T;
            float u0 = ((s.horizontal ? ps[0] : ps[1]) + 0.5f) * T, u1 = ((s.horizontal ? pe[0] : pe[1]) + 0.5f) * T;
            boolean firstSeg = start == 0, lastSeg = end == p.size() - 1;
            // 바닥 띠 시작·끝: 출구 쪽이면 방 그림 바닥이 끝나는 칸, 꺾이는 곳이면 그 칸 가운데
            float f0 = u0, f1 = u1;
            if (firstSeg) f0 = along(p.get(Math.min(fa, end)), s.horizontal);
            if (lastSeg) f1 = along(p.get(Math.max(fb, start)), s.horizontal);
            s.mouthA = firstSeg;
            s.mouthB = lastSeg;
            if (u0 <= u1) {
                s.a = u0; s.b = u1; s.fa = f0; s.fb = f1;
            } else {
                s.a = u1; s.b = u0; s.fa = f1; s.fb = f0;
                boolean t = s.mouthA; s.mouthA = s.mouthB; s.mouthB = t;
            }
            segs.add(s);
            start = end;
        }
    }

    private float along(int[] cell, boolean horizontal) {
        return ((horizontal ? cell[0] : cell[1]) + 0.5f) * T;
    }

    /** 방 그림 바닥이 끝나고 통로 칸이 시작되는 가운데 줄 칸 (끝에서부터 찾으면 fromEnd). */
    private int firstOpen(List<int[]> p, boolean fromEnd) {
        int n = p.size();
        for (int k = 0; k < n; k++) {
            int i = fromEnd ? n - 1 - k : k;
            int[] c = p.get(i);
            if (map.tile(c[0], c[1]) == WorldMap.CORRIDOR) return i;
        }
        return fromEnd ? n - 1 : 0;
    }

    // ───────────────────────── 땅 테마 섞기

    private void buildWeights(String[] theme) {
        int w = map.width, h = map.height;
        gw = w / BLOCK + 2;
        gh = h / BLOCK + 2;
        weight = new float[gw * gh * 4];
        float[][] dist = new float[4][];
        for (int t = 0; t < 4; t++) dist[t] = distance(theme, THEMES[t], w, h);
        for (int by = 0; by < gh; by++) {
            for (int bx = 0; bx < gw; bx++) {
                int x = Math.min(w - 1, bx * BLOCK), y = Math.min(h - 1, by * BLOCK);
                float best = Float.MAX_VALUE;
                for (int t = 0; t < 4; t++) best = Math.min(best, dist[t][x + y * w]);
                float sum = 0;
                float[] ws = new float[4];
                for (int t = 0; t < 4; t++) {
                    float k = 1f - (dist[t][x + y * w] - best) / BLEND;
                    ws[t] = k <= 0 ? 0 : k * k;
                    sum += ws[t];
                }
                for (int t = 0; t < 4; t++) weight[(by * gw + bx) * 4 + t] = sum > 0 ? ws[t] / sum : (t == 0 ? 1 : 0);
            }
        }
    }

    /** 그 테마 칸까지의 거리 (칸, 네 방향 걸음). 그 테마가 없으면 아주 멀다. */
    private static float[] distance(String[] theme, String th, int w, int h) {
        float[] d = new float[w * h];
        java.util.Arrays.fill(d, 1e6f);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < w * h; i++) {
            String t = theme[i];
            if (th.equals(t)) {
                d[i] = 0;
                q.add(i);
            }
        }
        while (!q.isEmpty()) {
            int i = q.poll(), x = i % w, y = i / w;
            float nd = d[i] + 1;
            if (x > 0 && d[i - 1] > nd) { d[i - 1] = nd; q.add(i - 1); }
            if (x < w - 1 && d[i + 1] > nd) { d[i + 1] = nd; q.add(i + 1); }
            if (y > 0 && d[i - w] > nd) { d[i - w] = nd; q.add(i - w); }
            if (y < h - 1 && d[i + w] > nd) { d[i + w] = nd; q.add(i + w); }
        }
        return d;
    }

    private float weightAt(int bx, int by, int t) {
        bx = Math.max(0, Math.min(gw - 1, bx));
        by = Math.max(0, Math.min(gh - 1, by));
        return weight[(by * gw + bx) * 4 + t];
    }

    // ───────────────────────── 그리기

    /** 화면 전체 땅. 테마 순서대로 덮으며, 뒤 테마는 (지금까지 비중 합 대비) 제 비중만큼 비친다. */
    void drawGround(SpriteBatch batch, float x0, float y0, float x1, float y1) {
        float B = BLOCK * T;
        int bx0 = (int) Math.floor(x0 / B), by0 = (int) Math.floor(y0 / B);
        int bx1 = (int) Math.floor(x1 / B), by1 = (int) Math.floor(y1 / B);
        for (int t = 0; t < 4; t++) {
            Texture tex = skins.get(THEMES[t]).ground;
            float rep = tex.getWidth() * SCENE_SCALE, repV = tex.getHeight() * SCENE_SCALE;
            for (int by = by0; by <= by1; by++) {
                for (int bx = bx0; bx <= bx1; bx++) {
                    float a00 = layerAlpha(bx, by, t), a10 = layerAlpha(bx + 1, by, t);
                    float a11 = layerAlpha(bx + 1, by + 1, t), a01 = layerAlpha(bx, by + 1, t);
                    if (a00 + a10 + a11 + a01 <= 0.001f) continue;
                    float px = bx * B, py = by * B;
                    quad(batch, tex, px, py, px + B, py + B,
                            px / rep, -py / repV, (px + B) / rep, -(py + B) / repV,
                            GROUND_TINT, a00, a10, a11, a01);
                }
            }
        }
    }

    private float layerAlpha(int bx, int by, int t) {
        if (t == 0) return 1f;
        float acc = 0;
        for (int k = 0; k <= t; k++) acc += weightAt(bx, by, k);
        return acc <= 0.0001f ? 0f : weightAt(bx, by, t) / acc;
    }

    /** 길 그림 전체 (벽 포함). 방 그림보다 먼저 그려서, 방 그림 가장자리가 길 위를 덮는다. */
    void drawWalls(SpriteBatch batch, float x0, float y0, float x1, float y1) {
        for (Seg s : segs) {
            Skin k = skins.getOrDefault(s.theme, skins.get("CAMP"));
            if (s.horizontal) {
                float ph = FLOOR_TILES * T / (k.hBot - k.hTop), rep = ph * k.hw / k.hh;
                float top = s.c + ph * ((k.hTop + k.hBot) / 2f), bot = top - ph;
                float a = s.mouthA ? s.a : s.a - 1.5f * T, b = s.mouthB ? s.b : s.b + 1.5f * T;
                if (b + T < x0 || a - T > x1 || top < y0 || bot > y1) continue;
                strip(batch, k.h, true, a, b, bot, top, rep, 0f, 1f, s.mouthA ? 0 : T, s.mouthB ? 0 : T);
            } else {
                float pw = FLOOR_TILES * T / (k.vR - k.vL), rep = pw * k.vh / k.vw;
                float left = s.c - pw * ((k.vL + k.vR) / 2f), right = left + pw;
                float a = s.mouthA ? s.a : s.a - 1.5f * T, b = s.mouthB ? s.b : s.b + 1.5f * T;
                if (b + T < y0 || a - T > y1 || right < x0 || left > x1) continue;
                strip(batch, k.v, false, a, b, left, right, rep, 0f, 1f, s.mouthA ? 0 : T, s.mouthB ? 0 : T);
            }
        }
    }

    /** 바닥 띠. 방 그림 위에 그리며, 출구 쪽은 방 그림 안으로 1.5칸 흐려지며 들어간다. */
    void drawFloors(SpriteBatch batch, float x0, float y0, float x1, float y1) {
        for (Seg s : segs) {
            Skin k = skins.getOrDefault(s.theme, skins.get("CAMP"));
            float a = s.mouthA ? s.fa - 0.5f * T : s.a - 1.7f * T, b = s.mouthB ? s.fb + 0.5f * T : s.b + 1.7f * T;
            float fadeA = s.mouthA ? 1.5f * T : 0.4f * T, fadeB = s.mouthB ? 1.5f * T : 0.4f * T;
            if (s.horizontal) {
                float ph = FLOOR_TILES * T / (k.hBot - k.hTop), rep = ph * k.hw / k.hh;
                float top = s.c + ph * ((k.hTop + k.hBot) / 2f);
                float bTop = top - ph * k.hfTop, bBot = top - ph * k.hfBot;
                if (b + fadeB < x0 || a - fadeA > x1 || bTop < y0 || bBot > y1) continue;
                strip(batch, k.hFloor, true, a, b, bBot, bTop, rep, 0f, 1f, fadeA, fadeB);
            } else {
                float pw = FLOOR_TILES * T / (k.vR - k.vL), rep = pw * k.vh / k.vw;
                float left = s.c - pw * ((k.vL + k.vR) / 2f);
                float bL = left + pw * k.vfL, bR = left + pw * k.vfR;
                if (b + fadeB < y0 || a - fadeA > y1 || bR < x0 || bL > x1) continue;
                strip(batch, k.vFloor, false, a, b, bL, bR, rep, 0f, 1f, fadeA, fadeB);
            }
        }
    }

    /**
     * 길이 방향 [a, b] 에 그림을 깐다 (폭 방향 [c0, c1]). 길이 방향 무늬는 월드 좌표에 맞춰 거울 반복한다.
     * 양 끝에서 fadeA / fadeB 만큼 바깥으로 흐려지며 더 나간다.
     */
    private void strip(SpriteBatch batch, Texture tex, boolean horizontal, float a, float b, float c0, float c1,
                       float rep, float s0, float s1, float fadeA, float fadeB) {
        if (fadeA > 0) piece(batch, tex, horizontal, a - fadeA, a, c0, c1, rep, s0, s1, 0f, 1f);
        if (b > a) piece(batch, tex, horizontal, a, b, c0, c1, rep, s0, s1, 1f, 1f);
        if (fadeB > 0) piece(batch, tex, horizontal, b, b + fadeB, c0, c1, rep, s0, s1, 1f, 0f);
    }

    private void piece(SpriteBatch batch, Texture tex, boolean horizontal, float l0, float l1, float c0, float c1,
                       float rep, float s0, float s1, float alpha0, float alpha1) {
        if (horizontal) {
            // 가로 그림: u = 길이(x) 방향 반복, v = 위(0) ~ 아래(1)
            quad(batch, tex, l0, c0, l1, c1, l0 / rep, s1, l1 / rep, s0, 1f, alpha0, alpha1, alpha1, alpha0);
        } else {
            // 세로 그림: u = 왼쪽(0) ~ 오른쪽(1), v = 길이(y) 방향 반복 (위가 작은 v)
            quad(batch, tex, c0, l0, c1, l1, s0, -l0 / rep, s1, -l1 / rep, 1f, alpha0, alpha0, alpha1, alpha1);
        }
    }

    /**
     * 꼭짓점마다 투명도가 다른 사각형. (x0,y0) 왼쪽 아래 ~ (x1,y1) 오른쪽 위.
     * u0,v0 는 왼쪽 아래 꼭짓점, u1,v1 은 오른쪽 위 꼭짓점의 텍스처 좌표.
     * a00 왼쪽 아래, a10 오른쪽 아래, a11 오른쪽 위, a01 왼쪽 위.
     */
    private void quad(SpriteBatch batch, Texture tex, float x0, float y0, float x1, float y1,
                      float u0, float v0, float u1, float v1, float tint, float a00, float a10, float a11, float a01) {
        float[] v = verts;
        put(v, 0, x0, y0, tint, a00, u0, v0);
        put(v, 5, x0, y1, tint, a01, u0, v1);
        put(v, 10, x1, y1, tint, a11, u1, v1);
        put(v, 15, x1, y0, tint, a10, u1, v0);
        batch.draw(tex, v, 0, 20);
    }

    private static void put(float[] v, int i, float x, float y, float tint, float a, float u, float tv) {
        v[i] = x;
        v[i + 1] = y;
        v[i + 2] = Color.toFloatBits(tint, tint, tint, a);
        v[i + 3] = u;
        v[i + 4] = tv;
    }

    @Override
    public void dispose() {
        for (Skin s : skins.values()) {
            s.h.dispose();
            s.hFloor.dispose();
            s.v.dispose();
            s.vFloor.dispose();
            s.ground.dispose();
        }
        skins.clear();
    }
}
