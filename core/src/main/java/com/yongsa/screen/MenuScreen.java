package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.gfx.Art;
import com.yongsa.ui.Modal;
import com.yongsa.ui.ModalStack;

/** 타이틀·소개·엔딩이 함께 쓰는 틀: 캠프 바닥과 모닥불 배경, 키로 고르는 창. */
public abstract class MenuScreen extends ScreenAdapter {
    protected final YongsaGame game;
    protected final Stage stage;
    protected final ModalStack modals;
    protected float time;

    protected MenuScreen(YongsaGame game) {
        this.game = game;
        this.stage = new Stage(new FitViewport(YongsaGame.WIDTH, YongsaGame.HEIGHT), game.batch);
        this.modals = new ModalStack(stage);
    }

    /** 바닥에 깔리는 창. 다른 창이 위에 열리면 그 창이 키를 받는다. */
    protected void open(Modal base) {
        modals.push(base);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                boolean handled = modals.keyDown(keycode);
                if (handled) game.sfx.play("select");
                return true;
            }
        }, stage));
    }

    @Override
    public void render(float delta) {
        time += delta;
        Gdx.gl.glClearColor(0.04f, 0.035f, 0.05f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.getViewport().apply();
        SpriteBatch b = game.batch;
        b.setProjectionMatrix(stage.getCamera().combined);
        b.begin();
        drawBackground(b);
        b.end();
        stage.act(delta);
        stage.draw();
    }

    protected void drawBackground(SpriteBatch b) {
        Art art = game.art;
        int T = 32;
        for (int y = 0; y < YongsaGame.HEIGHT / T + 1; y++) {
            for (int x = 0; x < YongsaGame.WIDTH / T + 1; x++) {
                b.setColor(0.45f, 0.42f, 0.42f, 1f);
                b.draw(art.floor("CAMP", x, y), x * T, y * T, T, T);
            }
        }
        float cx = YongsaGame.WIDTH / 2f, cy = 90;
        // 모닥불 빛
        for (int i = 6; i >= 1; i--) {
            float a = (0.05f + 0.015f * MathUtils.sin(time * 8)) * (7 - i) / 6f;
            b.setColor(1f, 0.6f, 0.25f, a);
            b.draw(art.white(), cx - 60 * i, cy - 30 * i + 20, 120 * i, 60 * i);
        }
        b.setColor(Color.WHITE);
        TextureRegion fire = art.get(((int) (time * 6)) % 2 == 0 ? "campfire" : "campfire2");
        b.draw(fire, cx - 32, cy - 10, 64, 64);
        TextureRegion hero = art.get("hero_side");
        b.draw(hero, cx - 130, cy - 6, 64, 80);
        b.draw(art.get("sword_holy"), cx - 92, cy + 24, 6, 6, 40, 40, 1, 1, 30);
        b.draw(art.get("chain"), cx - 88, cy + 26, 4, 4, 30, 30, 1, 1, 30);
        b.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
