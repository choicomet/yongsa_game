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
    public static final Color GOLD = new Color(0xf2c94cff);
    public static final Color TEXT = new Color(0xeeeae0ff);
    public static final Color DIM = new Color(0x9a95a8ff);
    public static final Color BLUE = new Color(0x5aa0ffff);
    public static final Color ORANGE = new Color(0xffa040ff);
    public static final Color RED = new Color(0xff5a4eff);
    public static final Color GREEN = new Color(0x6fd38aff);
    public static final Color GREY = new Color(0xa8a8b0ff);
    public static final Color PURPLE = new Color(0xc9a0ffff);

    private Ui() {
    }

    public static Skin createSkin(Fonts fonts, Art art) {
        Skin skin = new Skin();
        skin.add("panel", patch(0x1b1824f2, 0x8a7442ff, 3), Drawable.class);
        skin.add("panelDark", patch(0x0f0d14e6, 0x3a3447ff, 2), Drawable.class);
        skin.add("dim", new TextureRegionDrawable(art.white()).tint(new Color(0, 0, 0, 0.55f)), Drawable.class);

        skin.add("default", buttonStyle(fonts, 0x3a3447ff, 0x5a5070ff, 0x4a4359ff, 0x2a2533ff), TextButton.TextButtonStyle.class);
        skin.add("focused", buttonStyle(fonts, 0x544a6eff, 0xf2c94cff, 0x5e5478ff, 0x2a2533ff), TextButton.TextButtonStyle.class);
        skin.add("danger", buttonStyle(fonts, 0x7a2219ff, 0xc0392bff, 0x8e2a20ff, 0x5a160fff), TextButton.TextButtonStyle.class);
        skin.add("dangerFocused", buttonStyle(fonts, 0xa0301fff, 0xf2c94cff, 0xb03a28ff, 0x5a160fff), TextButton.TextButtonStyle.class);
        skin.add("tab", buttonStyle(fonts, 0x2a2533ff, 0x3a3447ff, 0x3a3447ff, 0x2a2533ff), TextButton.TextButtonStyle.class);
        skin.add("tabOn", buttonStyle(fonts, 0x4d4560ff, 0xc9a45cff, 0x4d4560ff, 0x2a2533ff), TextButton.TextButtonStyle.class);

        skin.add("default", new Label.LabelStyle(fonts.normal, TEXT));
        skin.add("small", new Label.LabelStyle(fonts.small, TEXT));
        skin.add("large", new Label.LabelStyle(fonts.large, TEXT));
        skin.add("title", new Label.LabelStyle(fonts.title, GOLD));
        return skin;
    }

    private static TextButton.TextButtonStyle buttonStyle(Fonts fonts, int bg, int border, int over, int down) {
        TextButton.TextButtonStyle s = new TextButton.TextButtonStyle();
        s.font = fonts.normal;
        s.fontColor = TEXT;
        s.disabledFontColor = new Color(0x6a6575ff);
        s.up = patch(bg, border, 2);
        s.over = patch(over, border, 2);
        s.down = patch(down, border, 2);
        s.disabled = patch(0x221f29ff, 0x2f2b38ff, 2);
        return s;
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
}
