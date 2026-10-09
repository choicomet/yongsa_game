package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.yongsa.gfx.Fonts;

/** 코드로 만드는 UI 스킨. 어두운 반투명 바탕에 밝은 테두리(참고 이미지의 HUD 상자 느낌). */
public final class UiSkin {
    private UiSkin() {}

    public static final Color TEXT = new Color(0.94f, 0.92f, 0.88f, 1f);
    public static final Color DIM = new Color(0.6f, 0.58f, 0.56f, 1f);
    public static final Color GOLD = new Color(1f, 0.82f, 0.4f, 1f);
    public static final Color GOOD = new Color(0.55f, 0.9f, 0.55f, 1f);
    public static final Color BAD = new Color(1f, 0.45f, 0.4f, 1f);

    public static Skin build(Fonts fonts) {
        Skin skin = new Skin();
        skin.add("small", fonts.small);
        skin.add("body", fonts.body);
        skin.add("large", fonts.large);
        skin.add("title", fonts.title);

        Drawable panel = box(skin, "panel", rgba(16, 14, 20, 238), rgba(186, 176, 160, 255), rgba(40, 36, 44, 255));
        Drawable panelLight = box(skin, "panel-light", rgba(34, 30, 38, 230), rgba(110, 104, 98, 255), null);
        Drawable up = box(skin, "btn-up", rgba(42, 38, 46, 255), rgba(150, 140, 128, 255), null);
        Drawable over = box(skin, "btn-over", rgba(64, 58, 68, 255), rgba(235, 212, 170, 255), null);
        Drawable down = box(skin, "btn-down", rgba(28, 24, 32, 255), rgba(255, 220, 140, 255), null);
        Drawable disabled = box(skin, "btn-disabled", rgba(30, 28, 32, 255), rgba(76, 72, 76, 255), null);
        Drawable checked = box(skin, "btn-checked", rgba(74, 62, 50, 255), rgba(255, 210, 130, 255), null);
        Drawable dim = solid(skin, "dim", rgba(0, 0, 0, 150));
        solid(skin, "white", rgba(255, 255, 255, 255));
        skin.add("panel", panel, Drawable.class);
        skin.add("panel-light", panelLight, Drawable.class);
        skin.add("dim", dim, Drawable.class);

        skin.add("default", new Label.LabelStyle(fonts.body, TEXT));
        skin.add("small", new Label.LabelStyle(fonts.small, TEXT));
        skin.add("dim", new Label.LabelStyle(fonts.small, DIM));
        skin.add("large", new Label.LabelStyle(fonts.large, TEXT));
        skin.add("title", new Label.LabelStyle(fonts.title, GOLD));
        skin.add("gold", new Label.LabelStyle(fonts.body, GOLD));
        skin.add("good", new Label.LabelStyle(fonts.large, GOOD));
        skin.add("bad", new Label.LabelStyle(fonts.large, BAD));
        skin.add("warn", new Label.LabelStyle(fonts.small, BAD));

        TextButton.TextButtonStyle tb = new TextButton.TextButtonStyle(up, down, null, fonts.body);
        tb.over = over;
        tb.disabled = disabled;
        tb.fontColor = TEXT;
        tb.overFontColor = Color.WHITE;
        tb.disabledFontColor = new Color(0.45f, 0.43f, 0.45f, 1f);
        skin.add("default", tb);

        TextButton.TextButtonStyle tab = new TextButton.TextButtonStyle(up, down, checked, fonts.body);
        tab.over = over;
        tab.fontColor = DIM;
        tab.checkedFontColor = GOLD;
        tab.overFontColor = Color.WHITE;
        skin.add("tab", tab);

        TextButton.TextButtonStyle small = new TextButton.TextButtonStyle(up, down, checked, fonts.small);
        small.over = over;
        small.disabled = disabled;
        small.fontColor = TEXT;
        small.checkedFontColor = GOLD;
        small.disabledFontColor = new Color(0.45f, 0.43f, 0.45f, 1f);
        skin.add("small", small);

        Slider.SliderStyle ss = new Slider.SliderStyle();
        ss.background = sized(box(skin, "slider-bg", rgba(30, 28, 32, 255), rgba(120, 112, 104, 255), null), 120, 8);
        ss.knob = sized(box(skin, "slider-knob", rgba(235, 212, 170, 255), rgba(60, 50, 40, 255), null), 10, 18);
        ss.knobOver = sized(box(skin, "slider-knob-over", rgba(255, 235, 190, 255), rgba(60, 50, 40, 255), null), 10, 18);
        ss.knobBefore = sized(solid(skin, "slider-before", rgba(200, 150, 80, 255)), 1, 6);
        skin.add("default-horizontal", ss);

        CheckBox.CheckBoxStyle cb = new CheckBox.CheckBoxStyle();
        cb.checkboxOff = sized(box(skin, "cb-off", rgba(30, 28, 32, 255), rgba(150, 140, 128, 255), null), 16, 16);
        cb.checkboxOn = sized(box(skin, "cb-on", rgba(220, 170, 90, 255), rgba(235, 212, 170, 255), rgba(30, 28, 32, 255)), 16, 16);
        cb.font = fonts.body;
        cb.fontColor = TEXT;
        skin.add("default", cb);
        return skin;
    }

    private static int rgba(int r, int g, int b, int a) {
        return (r << 24) | (g << 16) | (b << 8) | a;
    }

    /** 2px 테두리 + 안쪽 1px 어두운 선이 있는 9-patch. */
    private static Drawable box(Skin skin, String name, int fill, int border, Integer inner) {
        Pixmap p = new Pixmap(12, 12, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        p.setColor(fill);
        p.fill();
        p.setColor(border);
        p.drawRectangle(0, 0, 12, 12);
        p.drawRectangle(1, 1, 10, 10);
        if (inner != null) {
            p.setColor(inner);
            p.drawRectangle(2, 2, 8, 8);
        }
        Texture t = new Texture(p);
        p.dispose();
        skin.add(name + "-tex", t);
        NinePatch np = new NinePatch(new TextureRegion(t), 4, 4, 4, 4);
        NinePatchDrawable d = new NinePatchDrawable(np);
        d.setLeftWidth(8);
        d.setRightWidth(8);
        d.setTopHeight(5);
        d.setBottomHeight(5);
        return d;
    }

    private static Drawable solid(Skin skin, String name, int color) {
        Pixmap p = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        p.setColor(color);
        p.fill();
        Texture t = new Texture(p);
        p.dispose();
        skin.add(name + "-tex", t);
        TextureRegionDrawable d = new TextureRegionDrawable(new TextureRegion(t));
        skin.add(name, d, Drawable.class);
        return d;
    }

    private static Drawable sized(Drawable d, float w, float h) {
        d.setMinWidth(w);
        d.setMinHeight(h);
        return d;
    }
}
