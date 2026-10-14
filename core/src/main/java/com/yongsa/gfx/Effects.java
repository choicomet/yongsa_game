package com.yongsa.gfx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** 피해 숫자, 파편 같은 짧은 연출. 게임 시간이 멈추면 함께 멈춘다. */
public class Effects {
    static class Particle {
        float x, y, vx, vy, life, maxLife, size;
        Color color;
    }

    static class FloatText {
        float x, y, life, maxLife;
        String text;
        Color color;
        float scale;
    }

    private final List<Particle> particles = new ArrayList<>();
    /** 공중에 떠다니는 먼지 (분위기). */
    private final List<Particle> dust = new ArrayList<>();
    private final List<FloatText> texts = new ArrayList<>();

    static class FloatIcon {
        float x, y, life, maxLife, size;
        com.badlogic.gdx.graphics.g2d.TextureRegion region;
    }

    private final List<FloatIcon> icons = new ArrayList<>();

    /** 그 자리에서 떠오르는 그림 (상자에서 나온 회복약 등). */
    public void icon(com.badlogic.gdx.graphics.g2d.TextureRegion region, float x, float y, float size) {
        if (region == null) return;
        FloatIcon i = new FloatIcon();
        i.region = region;
        i.x = x;
        i.y = y;
        i.size = size;
        i.maxLife = i.life = 1.3f;
        icons.add(i);
    }

    public void drawIcons(SpriteBatch batch) {
        for (FloatIcon i : icons) {
            float k = 1f - i.life / i.maxLife;
            float pop = k < 0.2f ? k / 0.2f : 1f;
            float w = i.size * pop, h = w * i.region.getRegionHeight() / i.region.getRegionWidth();
            batch.setColor(1, 1, 1, Math.min(1, i.life * 3));
            batch.draw(i.region, i.x - w / 2f, i.y + k * 34, w, h);
        }
        batch.setColor(Color.WHITE);
    }
    private final Random rnd = new Random();
    private final GlyphLayout layout = new GlyphLayout();
    public float shake;
    /** 화면 전체 흰 번쩍임의 남은 시간. */
    public float flash;

    public void burst(float x, float y, Color color, int count, float speed) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double a = rnd.nextDouble() * Math.PI * 2;
            float s = speed * (0.4f + rnd.nextFloat() * 0.8f);
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(a) * s;
            p.vy = (float) Math.sin(a) * s + speed * 0.3f;
            p.maxLife = p.life = 0.35f + rnd.nextFloat() * 0.35f;
            p.size = 2 + rnd.nextInt(3) * 2;
            p.color = color;
            particles.add(p);
        }
    }

    public void text(float x, float y, String text, Color color, float scale) {
        FloatText t = new FloatText();
        t.x = x + (rnd.nextFloat() - 0.5f) * 10;
        t.y = y;
        t.text = text;
        t.color = color;
        t.maxLife = t.life = 0.9f;
        t.scale = scale;
        texts.add(t);
    }

    /** 카메라 주변에 먼지를 유지한다. */
    public void updateDust(float camX, float camY, float dt) {
        while (dust.size() < 46) {
            Particle p = new Particle();
            p.x = camX + (rnd.nextFloat() - 0.5f) * 1000;
            p.y = camY + (rnd.nextFloat() - 0.5f) * 580;
            p.vx = (rnd.nextFloat() - 0.5f) * 8;
            p.vy = 3 + rnd.nextFloat() * 6;
            p.maxLife = p.life = 4 + rnd.nextFloat() * 5;
            p.size = rnd.nextFloat() < 0.3f ? 2 : 1;
            p.color = rnd.nextFloat() < 0.5f ? new Color(1f, 0.9f, 0.7f, 1) : new Color(0.85f, 0.85f, 0.9f, 1);
            dust.add(p);
        }
        for (Iterator<Particle> it = dust.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            p.x += (p.vx + (float) Math.sin(p.life * 1.3f) * 4) * dt;
            p.y += p.vy * dt;
            if (p.life <= 0 || Math.abs(p.x - camX) > 560 || Math.abs(p.y - camY) > 330) it.remove();
        }
    }

    public void drawDust(SpriteBatch batch, Textures tx) {
        for (Particle p : dust) {
            float k = p.life / p.maxLife;
            float a = Math.min(1, Math.min(k * 3, (1 - k) * 3)) * 0.35f;
            batch.setColor(p.color.r, p.color.g, p.color.b, a);
            batch.draw(tx.white, p.x, p.y, p.size, p.size);
        }
        batch.setColor(Color.WHITE);
    }

    public void update(float dt) {
        if (shake > 0) shake = Math.max(0, shake - dt);
        if (flash > 0) flash = Math.max(0, flash - dt);
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) {
                it.remove();
                continue;
            }
            p.vy -= 500 * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
        }
        icons.removeIf(i -> (i.life -= dt) <= 0);
        for (Iterator<FloatText> it = texts.iterator(); it.hasNext(); ) {
            FloatText t = it.next();
            t.life -= dt;
            if (t.life <= 0) {
                it.remove();
                continue;
            }
            t.y += 28 * dt;
        }
    }

    public void drawParticles(SpriteBatch batch, Textures tx) {
        for (Particle p : particles) {
            float a = Math.min(1, p.life / p.maxLife * 1.5f);
            batch.setColor(p.color.r, p.color.g, p.color.b, a);
            batch.draw(tx.white, p.x - p.size / 2f, p.y - p.size / 2f, p.size, p.size);
        }
        batch.setColor(Color.WHITE);
    }

    public void drawTexts(SpriteBatch batch, BitmapFont font) {
        float base = font.getData().scaleX;
        for (FloatText t : texts) {
            float a = Math.min(1, t.life / t.maxLife * 2f);
            font.getData().setScale(base * t.scale);
            font.setColor(t.color.r, t.color.g, t.color.b, a);
            layout.setText(font, t.text);
            font.draw(batch, t.text, t.x - layout.width / 2f, t.y + layout.height);
        }
        font.getData().setScale(base);
        font.setColor(Color.WHITE);
    }

    public void clear() {
        particles.clear();
        texts.clear();
    }
}
