package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;

/** 체력 막대. 줄어들면 흰 잔상이 천천히 따라온다. */
public class HpBar extends Actor {
    private final TextureRegion white;
    private final Color fill;
    private float ratio = 1, trail = 1;

    public HpBar(TextureRegion white, Color fill) {
        this.white = white;
        this.fill = new Color(fill);
    }

    public void setRatio(float r) {
        ratio = Math.max(0, Math.min(1, r));
        if (ratio > trail) trail = ratio;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (trail > ratio) trail = Math.max(ratio, trail - delta * 0.5f);
    }

    @Override
    public void draw(Batch b, float a) {
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        Color old = b.getColor().cpy();
        b.setColor(0, 0, 0, 0.8f * a);
        b.draw(white, x - 2, y - 2, w + 4, h + 4);
        b.setColor(0.22f, 0.08f, 0.1f, a);
        b.draw(white, x, y, w, h);
        b.setColor(1, 1, 1, 0.75f * a);
        b.draw(white, x, y, w * trail, h);
        b.setColor(fill.r, fill.g, fill.b, a);
        b.draw(white, x, y, w * ratio, h);
        b.setColor(1, 1, 1, 0.18f * a);
        b.draw(white, x, y + h * 0.55f, w * ratio, h * 0.3f);
        b.setColor(old);
    }
}
