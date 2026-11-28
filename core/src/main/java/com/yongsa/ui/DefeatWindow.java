package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.yongsa.gfx.Art;

/**
 * 용사가 쓰러졌을 때 뜨는 패배 화면 (지용 2026-11-28, 시안 defeat-redesign 1번 v2).
 * 상자 없이 화면 전체를 붉게 어둡게 덮고, 가운데 가로 띠에 "패배"와 한 줄 안내를 띄운다.
 * E·Enter·Space·Esc 또는 "다시 일어서기"를 누르면 닫힌다. 열리자마자 잘못 눌려 닫히지 않게 잠깐 키를 받지 않는다.
 */
public class DefeatWindow extends Modal {
    /** 띠 높이(배치 전 기본값), 키를 받지 않는 시간(초), 나타나는 시간(초). */
    private static final float BAND_H = 220, INPUT_DELAY = 0.6f, FADE_IN = 0.45f;
    private static final Color LINE = new Color(0xc9473cff);
    private static final Color TAG = new Color(0x8b7a66ff);

    private final Art art;
    private static final Color SHADOW = new Color(0x3a0a06ff);
    private Texture bandTex, lineTex;
    private Label title;
    private Table rise;
    private final com.badlogic.gdx.math.Vector2 tmp = new com.badlogic.gdx.math.Vector2();
    private float t;

    public DefeatWindow(Skin skin, Art art) {
        super(skin);
        this.art = art;
    }

    @Override
    public void build() {
        bandTex = ramp(0.9f);
        lineTex = ramp(1f);
        Table c = new Table(skin);
        c.add(label(spaced("용사의 숨이 멎었다"), "small", TAG)).padBottom(34).row();
        // 제목: 어두운 붉은 그림자를 아래로 3px 깔아 시안처럼 도드라지게
        com.badlogic.gdx.scenes.scene2d.ui.Stack titleStack = new com.badlogic.gdx.scenes.scene2d.ui.Stack();
        Table shadow = new Table();
        shadow.add(label("패 배", "title", SHADOW)).padTop(6);
        titleStack.add(shadow);
        title = label("패 배", "title", Ui.RED);
        titleStack.add(title);
        c.add(titleStack).padBottom(14).row();
        c.add(label("모닥불 곁에서 다시 눈을 뜬다.", "default", Ui.TEXT)).padBottom(16).row();
        Table rise = new Table(skin);
        rise.add(label("E", "default", Ui.TEXT)).padRight(10);
        Label riseLabel = label("다시 일어서기", "default", Ui.GOLD);
        rise.add(riseLabel);
        rise.setTouchable(Touchable.enabled);
        rise.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (t >= INPUT_DELAY) close();
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor from) {
                super.enter(event, x, y, pointer, from);
                riseLabel.setColor(Ui.TEXT);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor to) {
                super.exit(event, x, y, pointer, to);
                riseLabel.setColor(Ui.GOLD);
            }
        });
        c.add(rise);
        this.rise = rise;
        add(c).center().padTop(-6);
        getColor().a = 0f;
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        t += delta;
        getColor().a = Math.min(1f, t / FADE_IN);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float a = getColor().a * parentAlpha;
        float w = getWidth(), h = getHeight();
        TextureRegion white = art.white();
        Color old = batch.getColor().cpy();
        // 화면 전체: 붉은 기가 도는 어둠 + 가장자리는 더 어둡게
        batch.setColor(0.06f, 0.008f, 0.008f, 0.74f * a);
        batch.draw(white, 0, 0, w, h);
        batch.setColor(0f, 0f, 0f, a);
        batch.draw(art.vignette, 0, 0, w, h);
        // 가운데 가로 띠: 제목 조금 위부터 "다시 일어서기" 조금 아래까지. 작은 머리글은 띠 위에 둔다. 양 끝은 투명하게 사라진다
        float by = h / 2f - BAND_H / 2f, bh = BAND_H;
        if (title != null && rise != null) {
            title.localToStageCoordinates(tmp.set(0, title.getHeight()));
            float top = tmp.y + 22;
            rise.localToStageCoordinates(tmp.set(0, 0));
            by = tmp.y - 34;
            bh = top - by;
        }
        batch.setColor(0.03f, 0.016f, 0.024f, a);
        batch.draw(bandTex, 0, by, w, bh);
        batch.setColor(LINE.r, LINE.g, LINE.b, 0.8f * a);
        batch.draw(lineTex, w * 0.18f, by, w * 0.64f, 1);
        batch.draw(lineTex, w * 0.18f, by + bh - 1, w * 0.64f, 1);
        batch.setColor(old);
        super.draw(batch, parentAlpha);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.E || keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE
                || keycode == Input.Keys.NUMPAD_ENTER || keycode == Input.Keys.ESCAPE) {
            if (t >= INPUT_DELAY) close();
            return true;
        }
        return true; // 패배 화면이 떠 있는 동안 다른 키는 무시
    }

    @Override
    public void close() {
        super.close();
        if (bandTex != null) bandTex.dispose();
        if (lineTex != null) lineTex.dispose();
        bandTex = lineTex = null;
    }

    /** 가로로 양 끝 0 → 22%·78% 에서 peak → 가운데까지 peak 인 흰색 알파 띠 (색은 batch 색으로 입힌다). */
    private static Texture ramp(float peak) {
        int n = 256;
        Pixmap p = new Pixmap(n, 1, Pixmap.Format.RGBA8888);
        p.setBlending(Pixmap.Blending.None);
        for (int x = 0; x < n; x++) {
            float u = (x + 0.5f) / n;
            float edge = Math.min(u, 1f - u) / 0.22f;
            float s = Math.min(1f, edge);
            float alpha = peak * s * s * (3 - 2 * s);
            p.drawPixel(x, 0, Color.rgba8888(1f, 1f, 1f, alpha));
        }
        Texture tex = new Texture(p);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        p.dispose();
        return tex;
    }

    /** 글자 사이를 띄운다 (낱자 사이 한 칸, 낱말 사이 세 칸). */
    static String spaced(String s) {
        StringBuilder b = new StringBuilder();
        for (String word : s.split(" ")) {
            if (b.length() > 0) b.append("   ");
            for (int i = 0; i < word.length(); i++) {
                if (i > 0) b.append(' ');
                b.append(word.charAt(i));
            }
        }
        return b.toString();
    }
}
