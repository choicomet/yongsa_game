package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.yongsa.YongsaGame;
import com.yongsa.ui.WindowHost;

/** UI(Stage)가 있는 화면의 공통 부분. UI 좌표는 1280×720 기준, 크기를 바꿔도 비율 유지 (NFR-01). */
public abstract class BaseScreen extends ScreenAdapter {

    protected final YongsaGame game;
    protected final Stage stage;
    protected final WindowHost windows;

    protected BaseScreen(YongsaGame game) {
        this.game = game;
        this.stage = new Stage(new ExtendViewport(1280, 720));
        this.windows = new WindowHost(stage, game.skin);
        windows.setClickSound(() -> game.sounds.play("ui_click"));
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.055f, 0.047f, 0.078f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.getViewport().apply();
        stage.draw();
    }

    @Override
    public void resize(int w, int h) {
        stage.getViewport().update(w, h, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
