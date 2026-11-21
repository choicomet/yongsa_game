package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD 장식 그림을 코드로 만든다 (그림 파일 없음, 2026-11-22 지용 시안).
 * 모서리를 깎은 금테 판(9-패치), 원형 배지, 키 알약, 작은 아이콘(책·느낌표·마름모).
 * 픽셀마다 테두리까지의 거리를 재서 칠하므로 크기를 바꿔도 테두리 두께가 그대로다.
 */
public class HudFrames implements Disposable {
    /** 판 색 조합: 금테(기본), 보라(스킬·각성), 빨강(경고·보스). */
    public enum Tone { GOLD, PURPLE, RED }

    private final List<Texture> textures = new ArrayList<>();
    private final NinePatch[] frames = new NinePatch[Tone.values().length];
    private final NinePatch pill, keycap;
    public final TextureRegion badge, badgePurple, badgeRed, ring, book, bang, diamond, diamondOutline, headerFade;

    private static final int GOLD = 0xd8b46aff, GOLD_DARK = 0x6e5732ff, GOLD_HI = 0xf6dc9aff;

    public HudFrames() {
        frames[Tone.GOLD.ordinal()] = frame(0x120e1af4, GOLD, GOLD_DARK);
        frames[Tone.PURPLE.ordinal()] = frame(0x1a1028f6, 0xb58ae0ff, 0x4e3570ff);
        frames[Tone.RED.ordinal()] = frame(0x1c0d10f6, 0xd0574aff, 0x6a2a24ff);
        pill = pill();
        keycap = keycap();
        badge = circle(0x15101ef0, GOLD);
        badgePurple = circle(0x2a1640f4, 0xc9a0ffff);
        badgeRed = circle(0x2a0f12f4, 0xe0675aff);
        ring = ring();
        book = book();
        bang = bang();
        diamond = diamond();
        diamondOutline = diamondOutline();
        headerFade = fade(0x6a3fa0ff);
    }

    public void frame(Batch b, Tone tone, float x, float y, float w, float h) {
        b.setColor(Color.WHITE);
        frames[tone.ordinal()].draw(b, x, y, w, h);
    }

    /** 판 양 끝 가운데에 금 마름모 징 (보스 체력·안내 판의 뾰족한 끝 느낌). */
    public void endCaps(Batch b, float x, float y, float w, float h, float size) {
        b.setColor(Color.WHITE);
        b.draw(diamond, x - size / 2f, y + h / 2f - size / 2f, size, size);
        b.draw(diamond, x + w - size / 2f, y + h / 2f - size / 2f, size, size);
    }

    public void pill(Batch b, float x, float y, float w, float h) {
        b.setColor(Color.WHITE);
        pill.draw(b, x, y, w, h);
    }

    /** 키 한 개를 담는 둥근 사각 (글자 폭만큼 가로로 늘어난다). */
    public void keycap(Batch b, float x, float y, float w, float h) {
        b.setColor(Color.WHITE);
        keycap.draw(b, x, y, w, h);
    }

    // ───────────────────────── 그림 만들기

    private NinePatch keycap() {
        int S = 24;
        float r = 7f;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float cx = Math.max(r, Math.min(S - r, x + 0.5f)), cy = Math.max(r, Math.min(S - r, y + 0.5f));
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float edge = r - (float) Math.sqrt(dx * dx + dy * dy);
                int col;
                if (edge < -1) col = 0;
                else if (edge < 0) col = rgba(GOLD, edge + 1);
                else if (edge < 1.5f) col = GOLD;
                else col = 0x2a2134f4;
                p.drawPixel(x, y, col);
            }
        }
        Texture t = keep(p);
        return new NinePatch(new TextureRegion(t), 9, 9, 9, 9);
    }

    private Texture keep(Pixmap p) {
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        textures.add(t);
        return t;
    }

    private static int lighten(int c, float k) {
        int r = (c >>> 24) & 0xff, g = (c >>> 16) & 0xff, b = (c >>> 8) & 0xff, a = c & 0xff;
        r = Math.min(255, Math.round(r + (200 - r) * k));
        g = Math.min(255, Math.round(g + (180 - g) * k));
        b = Math.min(255, Math.round(b + (220 - b) * k));
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    private static int rgba(int c, float alphaScale) {
        int a = Math.round((c & 0xff) * Math.max(0, Math.min(1, alphaScale)));
        return (c & 0xffffff00) | a;
    }

    /** 모서리를 깎은 판: 바깥 금선 2px, 안쪽 어두운 금선 1px, 네 모서리에 금 마름모 징. */
    private NinePatch frame(int fill, int line, int inner) {
        int S = 48, c = 7; // c = 모서리를 깎는 크기
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                int rx = Math.min(x, S - 1 - x), ry = Math.min(y, S - 1 - y);
                // 테두리까지 거리: 가로·세로 변, 그리고 깎인 대각선 변
                float d = Math.min(rx, ry);
                d = Math.min(d, (rx + ry - c) * 0.7071f);
                // 속은 위가 조금 밝은 세로 그라데이션 (pixmap 은 y 가 아래로 커진다)
                int body = lighten(fill, 0.18f * (1f - y / (float) (S - 1)));
                int col;
                if (d < 0) col = 0;
                else if (d < 2) col = line;
                else if (d < 4) col = body;
                else if (d < 5) col = inner;
                else col = body;
                p.drawPixel(x, y, col);
            }
        }
        // 모서리 징 (깎인 대각선 가운데)
        p.setColor(new Color(GOLD_HI));
        int m = c / 2;
        int[][] corners = {{m, m}, {S - 1 - m, m}, {m, S - 1 - m}, {S - 1 - m, S - 1 - m}};
        for (int[] k : corners) {
            for (int dy = -3; dy <= 3; dy++)
                for (int dx = -3; dx <= 3; dx++)
                    if (Math.abs(dx) + Math.abs(dy) <= 3) p.drawPixel(k[0] + dx, k[1] + dy);
        }
        Texture t = keep(p);
        int split = 14;
        return new NinePatch(new TextureRegion(t), split, split, split, split);
    }

    /** 키 이름을 담는 알약 (가로로 늘어난다). */
    private NinePatch pill() {
        int W = 40, H = 32;
        float r = 15.5f;
        Pixmap p = new Pixmap(W, H, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float cx = Math.max(r, Math.min(W - r, x + 0.5f));
                float dx = x + 0.5f - cx, dy = y + 0.5f - H / 2f;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                float edge = r - dist; // 안쪽으로 얼마나 들어왔나
                int col;
                if (edge < 0) col = rgba(GOLD, edge + 1);
                else if (edge < 1.6f) col = GOLD;
                else col = 0x120e1ae0;
                if (edge < -1) col = 0;
                p.drawPixel(x, y, col);
            }
        }
        Texture t = keep(p);
        return new NinePatch(new TextureRegion(t), 17, 17, 0, 0);
    }

    /** 원형 배지: 속은 어둡고 금 고리. */
    private TextureRegion circle(int fill, int line) {
        int S = 64;
        float R = 31f;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float dx = x + 0.5f - S / 2f, dy = y + 0.5f - S / 2f;
                float edge = R - (float) Math.sqrt(dx * dx + dy * dy);
                int col;
                if (edge < 0) col = rgba(line, edge + 1);
                else if (edge < 3.2f) col = line;
                else if (edge < 4.5f) col = 0x00000000 | 0x0a0810ff;
                else col = fill;
                if (edge < -1) col = 0;
                p.drawPixel(x, y, col);
            }
        }
        return new TextureRegion(keep(p));
    }

    /** 초상화 고리: 가운데는 비어 있고 굵은 금 고리 + 안쪽 어두운 선. */
    private TextureRegion ring() {
        int S = 128;
        float R = 63f;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float dx = x + 0.5f - S / 2f, dy = y + 0.5f - S / 2f;
                float edge = R - (float) Math.sqrt(dx * dx + dy * dy);
                int col;
                if (edge < -1) col = 0;
                else if (edge < 0) col = rgba(GOLD, edge + 1);
                else if (edge < 2) col = GOLD_HI;
                else if (edge < 6) col = GOLD;
                else if (edge < 8) col = GOLD_DARK;
                else col = 0;
                p.drawPixel(x, y, col);
            }
        }
        return new TextureRegion(keep(p));
    }

    /** 펼친 책 (다음 여정). */
    private TextureRegion book() {
        Pixmap p = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        p.setColor(new Color(0x5a3b1eff));
        p.fillRectangle(3, 8, 26, 18);
        p.setColor(new Color(0xf1e2bcff));
        p.fillRectangle(5, 9, 10, 15);
        p.fillRectangle(17, 9, 10, 15);
        p.setColor(new Color(0x8a6a3cff));
        p.fillRectangle(15, 8, 2, 18);
        for (int i = 0; i < 4; i++) {
            p.drawLine(7, 12 + i * 3, 13, 12 + i * 3);
            p.drawLine(19, 12 + i * 3, 25, 12 + i * 3);
        }
        return new TextureRegion(keep(p));
    }

    /** 느낌표 (경고 안내). */
    private TextureRegion bang() {
        Pixmap p = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        p.setColor(new Color(GOLD_HI));
        p.fillRectangle(14, 6, 5, 14);
        p.fillRectangle(14, 23, 5, 4);
        return new TextureRegion(keep(p));
    }

    /** 마름모 (일반 안내·장식). */
    private TextureRegion diamond() {
        int S = 32;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float d = Math.abs(x + 0.5f - 16) + Math.abs(y + 0.5f - 16);
                if (d < 9) p.drawPixel(x, y, d < 6 ? GOLD_HI : GOLD);
            }
        return new TextureRegion(keep(p));
    }

    /** 속이 빈 마름모 (아직 못 모은 보스 힘). */
    private TextureRegion diamondOutline() {
        int S = 32;
        Pixmap p = new Pixmap(S, S, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < S; y++)
            for (int x = 0; x < S; x++) {
                float d = Math.abs(x + 0.5f - 16) + Math.abs(y + 0.5f - 16);
                if (d < 9 && d >= 6.5f) p.drawPixel(x, y, GOLD);
                else if (d < 6.5f) p.drawPixel(x, y, 0x0a0810a0);
            }
        return new TextureRegion(keep(p));
    }

    /** 왼쪽은 진하고 오른쪽으로 사라지는 띠 (다음 여정 머리줄). */
    private TextureRegion fade(int color) {
        Pixmap p = new Pixmap(64, 1, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int x = 0; x < 64; x++) p.drawPixel(x, 0, rgba(color, 0.75f * (1f - x / 63f)));
        return new TextureRegion(keep(p));
    }

    @Override
    public void dispose() {
        for (Texture t : textures) t.dispose();
        textures.clear();
    }
}
