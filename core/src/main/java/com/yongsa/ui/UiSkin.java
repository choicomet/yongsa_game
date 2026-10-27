package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.yongsa.render.Fonts;

/**
 * 코드로 만드는 Scene2D 스킨. UI 그림이 오면 여기서 9-patch PNG를 읽도록 바꾸면 된다.
 * 결과 색은 명세서대로: 성공 파랑, 유지 회색, 하락 주황, 파괴 빨강 (FR-대장간-13). 글자와 함께 쓴다.
 */
public final class UiSkin {

    public static final Color GOLD = new Color(1f, 0.83f, 0.4f, 1f);
    public static final Color DIM = new Color(0.7f, 0.7f, 0.76f, 1f);
    public static final Color SUCCESS = new Color(0.4f, 0.7f, 1f, 1f);
    public static final Color KEEP = new Color(0.72f, 0.72f, 0.75f, 1f);
    public static final Color DOWN = new Color(1f, 0.6f, 0.2f, 1f);
    public static final Color DESTROY = new Color(1f, 0.3f, 0.3f, 1f);
    public static final Color SHORT = new Color(1f, 0.6f, 0.2f, 1f);
    public static final Color GOOD = new Color(0.5f, 0.92f, 0.5f, 1f);

    private UiSkin() {
    }

    public static Skin create(Fonts f) {
        Skin skin = new Skin();
        skin.add("small", f.small);
        skin.add("body", f.body);
        skin.add("large", f.large);
        skin.add("heading", f.heading);
        skin.add("title-font", f.title);
        skin.add("number", f.number);

        Pixmap px = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE);
        px.fill();
        Texture white = new Texture(px);
        px.dispose();
        skin.add("white-tex", white);
        skin.add("white", new TextureRegion(white));

        panel(skin, "panel", new Color(0.08f, 0.07f, 0.11f, 0.94f), new Color(0.55f, 0.46f, 0.32f, 1f));
        panel(skin, "panel-light", new Color(0.15f, 0.14f, 0.19f, 0.96f), new Color(0.33f, 0.31f, 0.4f, 1f));
        panel(skin, "panel-hud", new Color(0.06f, 0.05f, 0.09f, 0.78f), new Color(0.36f, 0.32f, 0.26f, 1f));
        panel(skin, "btn-up", new Color(0.25f, 0.22f, 0.31f, 1f), new Color(0.5f, 0.46f, 0.6f, 1f));
        panel(skin, "btn-over", new Color(0.34f, 0.3f, 0.43f, 1f), GOLD);
        panel(skin, "btn-down", new Color(0.18f, 0.16f, 0.23f, 1f), GOLD);
        panel(skin, "btn-checked", new Color(0.44f, 0.35f, 0.18f, 1f), GOLD);
        panel(skin, "btn-disabled", new Color(0.14f, 0.14f, 0.16f, 1f), new Color(0.27f, 0.27f, 0.3f, 1f));
        panel(skin, "btn-danger", new Color(0.55f, 0.13f, 0.13f, 1f), new Color(1f, 0.45f, 0.4f, 1f));
        panel(skin, "btn-danger-over", new Color(0.7f, 0.18f, 0.16f, 1f), new Color(1f, 0.75f, 0.6f, 1f));
        panel(skin, "focus", new Color(0, 0, 0, 0), new Color(1f, 0.95f, 0.55f, 1f));

        Label.LabelStyle body = new Label.LabelStyle(f.body, Color.WHITE);
        skin.add("default", body);
        skin.add("small", new Label.LabelStyle(f.small, DIM));
        skin.add("small-white", new Label.LabelStyle(f.small, Color.WHITE));
        skin.add("dim", new Label.LabelStyle(f.body, DIM));
        skin.add("gold", new Label.LabelStyle(f.body, GOLD));
        skin.add("large", new Label.LabelStyle(f.large, Color.WHITE));
        skin.add("large-gold", new Label.LabelStyle(f.large, GOLD));
        skin.add("heading", new Label.LabelStyle(f.heading, GOLD));
        skin.add("title", new Label.LabelStyle(f.title, GOLD));
        skin.add("number", new Label.LabelStyle(f.number, Color.WHITE));

        TextButton.TextButtonStyle btn = new TextButton.TextButtonStyle();
        btn.font = f.body;
        btn.up = skin.getDrawable("btn-up");
        btn.over = skin.getDrawable("btn-over");
        btn.down = skin.getDrawable("btn-down");
        btn.checked = skin.getDrawable("btn-checked");
        btn.disabled = skin.getDrawable("btn-disabled");
        btn.fontColor = Color.WHITE;
        btn.disabledFontColor = new Color(0.45f, 0.45f, 0.5f, 1f);
        skin.add("default", btn);

        TextButton.TextButtonStyle danger = new TextButton.TextButtonStyle(btn);
        danger.up = skin.getDrawable("btn-danger");
        danger.over = skin.getDrawable("btn-danger-over");
        danger.down = skin.getDrawable("btn-danger");
        skin.add("danger", danger);

        TextButton.TextButtonStyle tab = new TextButton.TextButtonStyle(btn);
        tab.font = f.large;
        skin.add("tab", tab);

        skin.add("default", new Window.WindowStyle(f.heading, GOLD, skin.getDrawable("panel")));

        ScrollPane.ScrollPaneStyle scroll = new ScrollPane.ScrollPaneStyle();
        scroll.vScrollKnob = skin.newDrawable("white", new Color(0.5f, 0.46f, 0.6f, 1f));
        scroll.vScrollKnob.setMinWidth(6);
        skin.add("default", scroll);
        return skin;
    }

    private static void panel(Skin skin, String name, Color fill, Color border) {
        int n = 12;
        Pixmap p = new Pixmap(n, n, Pixmap.Format.RGBA8888);
        // 투명색을 "덮어쓰기" 하려면 섞기를 꺼야 한다 (안 그러면 테두리 색이 그대로 남는다)
        p.setBlending(Pixmap.Blending.None);
        p.setColor(border);
        p.fill();
        p.setColor(fill);
        p.fillRectangle(2, 2, n - 4, n - 4);
        p.setColor(0, 0, 0, 0);
        p.drawPixel(0, 0);
        p.drawPixel(n - 1, 0);
        p.drawPixel(0, n - 1);
        p.drawPixel(n - 1, n - 1);
        Texture t = new Texture(p);
        p.dispose();
        skin.add(name + "-tex", t);
        NinePatchDrawable d = new NinePatchDrawable(new NinePatch(t, 4, 4, 4, 4));
        d.setLeftWidth(12);
        d.setRightWidth(12);
        d.setTopHeight(8);
        d.setBottomHeight(8);
        skin.add(name, d, Drawable.class);
    }
}
