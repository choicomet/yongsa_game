package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;

import java.util.Locale;

/** 스킬 칸: 아이콘, 키, 쿨타임 동안 어둡게 + 남은 초 (FR-전투-04). */
public class SkillSlot extends Actor {
    private final TextureRegion icon, white;
    private final String key, name;
    private final BitmapFont small, number;
    private final GlyphLayout layout = new GlyphLayout();
    private float remain, total;
    /** 감전으로 못 쓰는 중. */
    private boolean sealed;
    /** 막기 칸: 게이지(0~1)를 아래에서 위로 채워 보여준다. 음수면 안 씀. */
    private float meter = -1;
    private boolean broken, active;

    public SkillSlot(TextureRegion icon, TextureRegion white, String key, String name, BitmapFont small, BitmapFont number) {
        this.icon = icon;
        this.white = white;
        this.key = key;
        this.name = name;
        this.small = small;
        this.number = number;
        setSize(64, 64);
    }

    public void setCooldown(float remain, float total) {
        this.remain = remain;
        this.total = total;
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
    }

    public void setMeter(float ratio, boolean broken, boolean active) {
        this.meter = ratio;
        this.broken = broken;
        this.active = active;
    }

    @Override
    public void draw(Batch b, float a) {
        float x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (active) b.setColor(0.5f, 0.85f, 1f, a);
        else b.setColor(0.55f, 0.47f, 0.33f, a);
        b.draw(white, x - 2, y - 2, w + 4, h + 4);
        b.setColor(0.07f, 0.06f, 0.1f, 0.92f * a);
        b.draw(white, x, y, w, h);
        b.setColor(1, 1, 1, a);
        b.draw(icon, x + 8, y + 8, w - 16, h - 16);
        if (remain > 0 && total > 0) {
            float k = remain / total;
            b.setColor(0, 0, 0, 0.65f * a);
            b.draw(white, x, y, w, h * k);
            String t = remain >= 1 ? String.valueOf((int) Math.ceil(remain)) : String.format(Locale.ROOT, "%.1f", remain);
            layout.setText(number, t);
            number.setColor(Color.WHITE);
            number.draw(b, t, x + (w - layout.width) / 2, y + (h + layout.height) / 2);
        }
        if (meter >= 0) {
            // 막기 게이지: 비어 있는 만큼 어둡게, 오른쪽에 세로 막대
            b.setColor(0, 0, 0, 0.6f * a);
            b.draw(white, x, y + h * meter, w, h * (1 - meter));
            if (broken) b.setColor(0.95f, 0.3f, 0.25f, a);
            else b.setColor(0.45f, 0.8f, 1f, a);
            b.draw(white, x + w - 6, y + 2, 4, (h - 4) * meter);
        }
        if (sealed) {
            b.setColor(0.35f, 0.6f, 1f, 0.45f * a);
            b.draw(white, x, y, w, h);
            b.setColor(1f, 0.95f, 0.4f, a);
            b.draw(white, x + 6, y + h / 2 - 2, w - 12, 4, w - 12, 4, 1, 1, 0);
            layout.setText(number, "감전");
            number.setColor(1f, 0.95f, 0.5f, a);
            number.draw(b, "감전", x + (w - layout.width) / 2, y + (h + layout.height) / 2);
        }
        small.setColor(1f, 0.85f, 0.45f, a);
        layout.setText(small, key);
        b.setColor(0, 0, 0, 0.7f * a);
        b.draw(white, x + 2, y + h - 20, layout.width + 8, 18);
        small.draw(b, key, x + 6, y + h - 5);
        small.setColor(0.8f, 0.8f, 0.85f, a);
        layout.setText(small, name);
        small.draw(b, name, x + (w - layout.width) / 2, y - 6);
        b.setColor(Color.WHITE);
    }
}
