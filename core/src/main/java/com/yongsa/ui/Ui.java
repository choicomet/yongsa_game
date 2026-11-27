package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Fonts;

/** Scene2D 스킨을 코드로 만든다 (그림 파일 없음). */
public final class Ui {
    public static final Color GOLD = new Color(0xe3b875ff);
    public static final Color TEXT = new Color(0xf2eee5ff);
    public static final Color DIM = new Color(0xaeb5baff);
    public static final Color BLUE = new Color(0x7ac9e4ff);
    public static final Color ORANGE = new Color(0xffa040ff);
    public static final Color RED = new Color(0xff5a4eff);
    public static final Color GREEN = new Color(0x6fd38aff);
    public static final Color GREY = new Color(0xa8a8b0ff);
    public static final Color PURPLE = new Color(0xc9a0ffff);

    private Ui() {
    }

    public static Skin createSkin(Fonts fonts, Art art) {
        Skin skin = new Skin();
        skin.add("panel", patch(0x141b22fa, 0x75624aff, 1), Drawable.class);
        skin.add("panelDark", patch(0x0c1219ef, 0x35414aff, 1), Drawable.class);
        skin.add("dim", new TextureRegionDrawable(art.white()).tint(new Color(0, 0, 0, 0.55f)), Drawable.class);
        // 일시정지 · 설정 창: 모서리가 둥근 판과 버튼, 제목 아래 옅은 금색 줄
        skin.add("panelRound", rounded(0x121820f5, 0x8a7350ff, 2, 20, 14, 10), Drawable.class);
        skin.add("divider", new TextureRegionDrawable(art.white()).tint(new Color(0xe3b87559)), Drawable.class);
        // 대장간 창: 둥근 칸 · 무기 카드 · 작은 표 · 경고 상자
        skin.add("forgeCol", rounded(0x0c1219f0, 0x35414aff, 1, 14, 14, 14), Drawable.class);
        skin.add("forgeChip", rounded(0x0c1219ff, 0x35414aff, 1, 12, 10, 4), Drawable.class);
        skin.add("forgeCard", rounded(0x16202aff, 0x2d3842ff, 2, 12, 10, 6), Drawable.class);
        skin.add("forgeCardSel", rounded(0x2c271fff, 0xe3b875ff, 2, 12, 10, 6), Drawable.class);
        skin.add("forgeCardAwake", rounded(0x16202aff, 0x7a5faeff, 2, 12, 10, 6), Drawable.class);
        skin.add("forgeIcon", rounded(0x0b1016ff, 0x2d3842ff, 1, 10, 3, 3), Drawable.class);
        skin.add("forgeBadgeEq", rounded(0x3a3226ff, 0x8a7350ff, 1, 8, 7, 1), Drawable.class);
        skin.add("forgeBadgeSkill", rounded(0x2b2240ff, 0x5f4a8aff, 1, 8, 7, 1), Drawable.class);
        skin.add("forgeSeg", rounded(0x0b1016ff, 0x2d3842ff, 1, 12, 4, 4), Drawable.class);
        skin.add("forgeSegOn", rounded(0x3a3226ff, 0x8a7350ff, 1, 9, 6, 6), Drawable.class);
        skin.add("forgeWarn", rounded(0x2a1714ff, 0x6b2e2eff, 1, 10, 11, 8), Drawable.class);
        skin.add("forgeSkill", rounded(0x1d1830ff, 0x5f4a8aff, 1, 12, 14, 12), Drawable.class);
        skin.add("forgeKey", rounded(0x0b1016ff, 0x5a636cff, 1, 7, 7, 2), Drawable.class);
        skin.add("forgeLine", new TextureRegionDrawable(art.white()).tint(new Color(0x222b34ff)), Drawable.class);
        skin.add("roundDanger", roundStyle(fonts, 0x7a2219ff, 0xf2c94cff, 0x8e2a20ff, 0x5a160fff, Color.WHITE), TextButton.TextButtonStyle.class);
        skin.add("roundPurple", roundStyle(fonts, 0x3b2a5cff, 0xc9a0ffff, 0x4a3570ff, 0x2a1d42ff, new Color(0xf1e8ffff)), TextButton.TextButtonStyle.class);

        skin.add("default", buttonStyle(fonts, 0x202a33ff, 0x45515bff, 0x303d48ff, 0x131c25ff), TextButton.TextButtonStyle.class);
        skin.add("focused", buttonStyle(fonts, 0x3d352bff, 0xe3b875ff, 0x504431ff, 0x29241eff), TextButton.TextButtonStyle.class);
        skin.add("danger", buttonStyle(fonts, 0x7a2219ff, 0xc0392bff, 0x8e2a20ff, 0x5a160fff), TextButton.TextButtonStyle.class);
        skin.add("dangerFocused", buttonStyle(fonts, 0xa0301fff, 0xf2c94cff, 0xb03a28ff, 0x5a160fff), TextButton.TextButtonStyle.class);
        skin.add("round", roundStyle(fonts, 0x1d2730ff, 0x3c4852ff, 0x283440ff, 0x141c24ff, TEXT), TextButton.TextButtonStyle.class);
        skin.add("roundFocused", roundStyle(fonts, 0x3a3226ff, 0xe3b875ff, 0x463b2bff, 0x29231cff, CREAM), TextButton.TextButtonStyle.class);
        skin.add("tab", buttonStyle(fonts, 0x2a2533ff, 0x3a3447ff, 0x3a3447ff, 0x2a2533ff), TextButton.TextButtonStyle.class);
        skin.add("tabOn", buttonStyle(fonts, 0x4d4560ff, 0xc9a45cff, 0x4d4560ff, 0x2a2533ff), TextButton.TextButtonStyle.class);
        // 타이틀 메뉴: 그림 톤(어두운 보라 · 크림색 글씨 · 금색 테두리)에 맞춘 반투명 버튼
        skin.add("titleMenu", titleStyle(fonts, 0x16111fb8, 0x4a3e5cff, CREAM_DIM), TextButton.TextButtonStyle.class);
        skin.add("titleMenuFocused", titleStyle(fonts, 0x2a2138e0, 0xd8b46aff, CREAM), TextButton.TextButtonStyle.class);

        skin.add("default", new Label.LabelStyle(fonts.normal, TEXT));
        skin.add("small", new Label.LabelStyle(fonts.small, TEXT));
        skin.add("large", new Label.LabelStyle(fonts.large, TEXT));
        skin.add("title", new Label.LabelStyle(fonts.title, GOLD));
        AchievementArt.install(skin, art);
        return skin;
    }

    /** 타이틀 그림의 크림색 글씨. */
    public static final Color CREAM = new Color(0xf3e6c8ff);
    private static final Color CREAM_DIM = new Color(0xc9bba3ff);

    private static TextButton.TextButtonStyle titleStyle(Fonts fonts, int bg, int border, Color text) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = fonts.normal;
        s.fontColor = text;
        s.disabledFontColor = new Color(0x6c6276ff);
        s.up = patch(bg, border, 1);
        s.over = patch(0x2a2138e0, 0xd8b46aff, 1);
        s.down = patch(0x0f0b16e0, 0xd8b46aff, 1);
        s.disabled = patch(0x110d18a0, 0x2c2636ff, 1);
        return s;
    }

    private static TextButton.TextButtonStyle buttonStyle(Fonts fonts, int bg, int border, int over, int down) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = fonts.normal;
        s.fontColor = TEXT;
        s.disabledFontColor = new Color(0x6a6575ff);
        s.up = patch(bg, border, 1);
        s.over = patch(over, border, 1);
        s.down = patch(down, border, 1);
        s.disabled = patch(0x221f29ff, 0x2f2b38ff, 2);
        return s;
    }

    private static TextButton.TextButtonStyle roundStyle(Fonts fonts, int bg, int border, int over, int down, Color text) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = fonts.normal;
        s.fontColor = text;
        s.disabledFontColor = new Color(0x6a6575ff);
        s.up = rounded(bg, border, 2, 12, 10, 6);
        s.over = rounded(over, border, 2, 12, 10, 6);
        s.down = rounded(down, border, 2, 12, 10, 6);
        s.disabled = rounded(0x1c1a22ff, 0x2f2b38ff, 2, 12, 10, 6);
        return s;
    }

    /**
     * 모서리가 둥근 테두리 판 9-패치. 가장자리는 부드럽게(안티앨리어싱) 칠한다.
     * bw 테두리 두께, radius 모서리 반지름, padX/padY 안쪽 여백.
     */
    public static NinePatchDrawable rounded(int bg, int border, int bw, int radius, int padX, int padY) {
        int size = radius * 2 + 2;
        Color cb = new Color(bg), cr = new Color(border);
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        float half = size / 2f, inner = half - radius;
        Color c = new Color();
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float qx = Math.abs(x + 0.5f - half) - inner, qy = Math.abs(y + 0.5f - half) - inner;
                float ox = Math.max(qx, 0), oy = Math.max(qy, 0);
                // 둥근 사각형 경계까지 거리 (안쪽은 음수)
                float d = (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0) - radius;
                float outer = Math.max(0, Math.min(1, 0.5f - d));
                float fill = Math.max(0, Math.min(1, 0.5f - (d + bw)));
                c.set(cr).lerp(cb, fill);
                c.a = (cr.a + (cb.a - cr.a) * fill) * outer;
                p.drawPixel(x, y, Color.rgba8888(c));
            }
        }
        Texture t = new Texture(p);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        NinePatch np = new NinePatch(new TextureRegion(t), radius, radius, radius, radius);
        NinePatchDrawable d = new NinePatchDrawable(np);
        d.setLeftWidth(padX);
        d.setRightWidth(padX);
        d.setTopHeight(padY);
        d.setBottomHeight(padY);
        d.setMinWidth(size);
        d.setMinHeight(size);
        return d;
    }

    /** 테두리가 있는 단색 9-패치. */
    public static NinePatchDrawable patch(int bg, int border, int bw) {
        int size = bw * 2 + 4;
        Pixmap p = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        p.setColor(new Color(border));
        p.fill();
        p.setColor(new Color(bg));
        p.fillRectangle(bw, bw, size - bw * 2, size - bw * 2);
        Texture t = new Texture(p);
        p.dispose();
        NinePatch np = new NinePatch(new TextureRegion(t), bw + 1, bw + 1, bw + 1, bw + 1);
        NinePatchDrawable d = new NinePatchDrawable(np);
        d.setLeftWidth(bw + 10);
        d.setRightWidth(bw + 10);
        d.setTopHeight(bw + 6);
        d.setBottomHeight(bw + 6);
        return d;
    }

    public static Label label(Skin skin, String text, String style, Color color) {
        Label l = new Label(text, skin, style);
        if (color != null) l.setColor(color);
        return l;
    }

    public static com.badlogic.gdx.scenes.scene2d.ui.Image artImage(TextureRegion region) {
        com.badlogic.gdx.scenes.scene2d.ui.Image image = new com.badlogic.gdx.scenes.scene2d.ui.Image(region);
        image.setScaling(com.badlogic.gdx.utils.Scaling.fit);
        return image;
    }
}
