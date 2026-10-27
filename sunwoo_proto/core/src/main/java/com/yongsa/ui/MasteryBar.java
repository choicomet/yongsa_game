package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.yongsa.model.EnhanceMastery;

/**
 * 대장간 탭 줄 오른쪽의 강화 숙련도: "강화 숙련도 Lv 7   34 / 98" + 경험치 막대.
 * 모든 검이 함께 쓰는 값이라 어느 탭에서나 보인다. 마스터면 금색으로 가득 찬다.
 */
public class MasteryBar extends Widget {
    private final EnhanceMastery m;
    private final BitmapFont font;
    private final TextureRegion white;
    private final GlyphLayout layout = new GlyphLayout();

    public MasteryBar(EnhanceMastery mastery, Skin skin) {
        this.m = mastery;
        this.font = skin.getFont("small");
        this.white = skin.getRegion("white");
    }

    @Override public float getPrefWidth() { return 330; }
    @Override public float getPrefHeight() { return 44; }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean master = m.maxed();
        float ratio = master ? 1f : m.need() <= 0 ? 0f : Math.min(1f, m.exp() / (float) m.need());
        String title = master ? "강화 숙련도 MASTER" : "강화 숙련도 Lv " + m.level();
        String right = master ? "Lv " + m.level() + " / " + m.maxLevel() : m.exp() + " / " + m.need();
        Color old = batch.getColor().cpy();
        font.setColor(master ? UiSkin.GOLD : Color.WHITE);
        font.draw(batch, title, x, y + h - 4);
        layout.setText(font, right);
        font.setColor(UiSkin.DIM);
        font.draw(batch, right, x + w - layout.width, y + h - 4);
        float by = y + 3, bh = 10;
        batch.setColor(0.1f, 0.09f, 0.13f, parentAlpha);
        batch.draw(white, x, by, w, bh);
        Color c = master ? UiSkin.GOLD : new Color(0.75f, 0.55f, 1f, 1f);
        batch.setColor(c.r, c.g, c.b, parentAlpha);
        batch.draw(white, x, by, w * ratio, bh);
        batch.setColor(old);
        font.setColor(Color.WHITE);
    }
}
