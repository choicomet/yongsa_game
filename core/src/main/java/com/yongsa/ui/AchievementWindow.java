package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.yongsa.YongsaGame;
import com.yongsa.model.Achievement;
import com.yongsa.model.Achievements;

/**
 * 업적 창 (타이틀 → 업적). 카드 여섯 장: 달성한 업적은 금테 원 안에 그림, 오른쪽 위 보라색 “적용 중”,
 * 무엇을 해서 달성했는지와 효과가 보인다. 아직인 업적은 그림이 어두운 실루엣이고 이름은 ???, 힌트와 효과만 보인다.
 */
public class AchievementWindow extends Modal {
    private static final Color CARD_TEXT = new Color(0xb9b2a4ff);
    private static final Color LOCK_TEXT = new Color(0x77726aff);
    private static final Color LOCK_NAME = new Color(0x5d5a55ff);
    private static final Color SILHOUETTE = new Color(0.03f, 0.035f, 0.045f, 1f);
    private static final Color EFFECT = new Color(0xffcf7aff);

    private final YongsaGame game;
    private static Drawable cardOn, cardOff, ringOn, ringOff, ribbon, effectOn, effectOff;

    public AchievementWindow(YongsaGame game) {
        super(game.skin);
        this.game = game;
        if (cardOn == null) {
            cardOn = Ui.rounded(0x2a2219f5, 0xb8935aff, 2, 12, 14, 12);
            cardOff = Ui.rounded(0x11171df5, 0x2a323aff, 2, 12, 14, 12);
            ringOn = Ui.rounded(0x3a2c1aff, 0xe2b25aff, 2, 32, 0, 0);
            ringOff = Ui.rounded(0x2a323cff, 0x3a444eff, 2, 32, 0, 0);
            ribbon = ribbon();
            effectOn = Ui.rounded(0x0e141aff, 0x7a5a30ff, 1, 8, 8, 5);
            effectOff = Ui.rounded(0x0e141aff, 0x262c33ff, 1, 8, 8, 5);
        }
    }

    @Override
    public void build() {
        nav.style = "round";
        Achievements got = new Achievements(game.settings.achievements);
        Table p = panel("panelRound");
        p.pad(24, 32, 26, 32);
        Table head = new Table();
        head.add(label("업적", "large", Ui.GOLD));
        head.add(label(got.count() + " / " + Achievement.values().length, "default", Ui.DIM)).padLeft(12).padTop(6);
        p.add(head).padBottom(10).row();
        p.add(new Image(skin.getDrawable("divider"))).width(828).height(1).padBottom(16).row();

        Table grid = new Table();
        int i = 0;
        for (Achievement a : Achievement.values()) {
            grid.add(card(a, got.has(a))).width(268).height(226).pad(6);
            if (++i % 3 == 0) grid.row();
        }
        p.add(grid).padBottom(14).row();
        p.add(nav.add("닫기", this::close)).width(200).height(48);
    }

    /** 보라색 제비꼬리 리본: 위는 밝고 아래는 어둡게, 오른쪽 끝은 V자로 파인다. */
    private static Drawable ribbon() {
        int w = 40, h = 28, notch = 9, mid = h / 2;
        Color top = new Color(0x9468e6ff), bottom = new Color(0x5e36a8ff), c = new Color();
        Pixmap p = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h; y++) {
            float t = y / (float) (h - 1);
            c.set(top).lerp(bottom, t);
            if (y == 0) c.set(0xb593f5ff);            // 윗변 하이라이트
            if (y >= h - 2) c.set(0x45277eff);        // 아랫변 그림자
            for (int x = 0; x < w; x++) {
                // 오른쪽 V자 홈: 가운데로 갈수록 깊다
                float cut = w - notch * (1f - Math.abs(y + 0.5f - mid) / mid);
                float a = Math.max(0, Math.min(1, cut - (x + 0.5f) + 0.5f));
                    p.drawPixel(x, y, Color.rgba8888(c.r, c.g, c.b, a));
            }
        }
        Texture tex = new Texture(p);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        NinePatch np = new NinePatch(new TextureRegion(tex), 4, notch + 2, 0, 0);
        NinePatchDrawable d = new NinePatchDrawable(np);
        d.setLeftWidth(10);
        d.setRightWidth(notch + 8);
        d.setTopHeight(4);
        d.setBottomHeight(5);
        return d;
    }

    private Table card(Achievement a, boolean on) {
        Table c = new Table();
        c.setBackground(on ? cardOn : cardOff);
        c.top().left();

        Image icon = Ui.artImage(AchievementArt.icon(game.art, a));
        if (!on) icon.setColor(SILHOUETTE);
        Stack ring = new Stack();
        ring.add(new Image(on ? ringOn : ringOff));
        Table iconBox = new Table();
        iconBox.add(icon).size(40);
        ring.add(iconBox);

        Table top = new Table();
        top.add(ring).size(64).left().top();
        top.add().expandX();
        if (on) {
            Label tag = label("적용 중", "small", Ui.TEXT);
            Table t = new Table();
            t.setBackground(ribbon);
            t.add(tag);
            // 카드 오른쪽 끝에 붙는 제비꼬리 리본
            top.add(t).top().right().padRight(-12).padTop(2);
        }
        c.add(top).growX().row();

        c.add(label(on ? a.title : "???", "default", on ? Ui.TEXT : LOCK_NAME)).left().padTop(8).row();
        Label body = label(on ? a.done : a.hint, "small", on ? CARD_TEXT : LOCK_TEXT);
        body.setWrap(true);
        body.setAlignment(Align.topLeft);
        c.add(body).width(236).left().padTop(4).expandY().top().row();

        Table eff = new Table();
        eff.setBackground(on ? effectOn : effectOff);
        eff.add(label("효과", "small", Ui.DIM)).padRight(6);
        eff.add(label(a.effect, "small", on ? EFFECT : LOCK_TEXT)).left().expandX();
        c.add(eff).growX().height(30);
        return c;
    }
}
