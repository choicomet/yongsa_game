package com.yongsa;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.yongsa.audio.Audio;
import com.yongsa.config.Balance;
import com.yongsa.gfx.Fonts;
import com.yongsa.gfx.SpriteLibrary;
import com.yongsa.gfx.Textures;
import com.yongsa.model.Settings;
import com.yongsa.save.SaveManager;
import com.yongsa.screen.GameScreen;
import com.yongsa.screen.TitleScreen;
import com.yongsa.ui.UiSkin;

import java.nio.file.Path;

/** 용사가 다한다. 공용 자원을 갖고 화면을 전환한다. */
public class YongsaGame extends Game {
    private final Path saveDir;

    public Balance balance;
    public SaveManager saves;
    public Settings settings;

    public SpriteBatch batch;
    public ShapeRenderer shapes;
    public Fonts fonts;
    public Skin skin;
    public SpriteLibrary sprites;
    public Textures textures;
    public Audio audio;
    /** 개발용: 매 프레임 그린 뒤 호출된다 (스크린샷 자동 촬영 등). */
    public Runnable afterRender;

    public YongsaGame(Path saveDir) {
        this.saveDir = saveDir;
    }

    @Override
    public void create() {
        balance = Balance.load(Gdx.files.internal("data/balance.json"));

        saves = new SaveManager(saveDir, balance);
        settings = saves.loadSettings();

        batch = new SpriteBatch();
        shapes = new ShapeRenderer();
        fonts = new Fonts(Gdx.files.internal("fonts/Galmuri11.ttf"));
        skin = UiSkin.build(fonts);
        textures = new Textures();
        sprites = new SpriteLibrary(Gdx.files.internal("data/placeholder_art.txt"),
                Gdx.files.internal("data/sprites_generated.json"), Gdx.files.internal("data/sprites_craftpix.json"),
                Gdx.files.internal("data/sprites_bosses.json"),
                Gdx.files.internal("data/sprites.json"));
        audio = new Audio(settings);
        applyDisplay();
        setScreen(new TitleScreen(this));
    }

    @Override
    public void render() {
        super.render();
        if (afterRender != null) afterRender.run();
    }

    /** 설정 적용 직후 저장한다. */
    public void applySettings(Settings s) {
        settings = s.copy();
        audio.setSettings(settings);
        applyDisplay();
        saves.saveSettings(settings);
    }

    private void applyDisplay() {
        if (settings.fullscreen && !Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        } else if (!settings.fullscreen && Gdx.graphics.isFullscreen()) {
            Gdx.graphics.setWindowedMode(1280, 720);
        }
    }

    /** 앱이 포커스를 잃으면 자동으로 일시정지한다. */
    public void focusLost() {
        Screen s = getScreen();
        if (s instanceof GameScreen) ((GameScreen) s).autoPause();
    }

    @Override
    public void pause() {
        focusLost();
        super.pause();
    }

    /** 이전 화면을 정리하고 새 화면으로 바꾼다. 그리는 도중에 바꾸지 않도록 다음 프레임에 처리한다. */
    public void switchTo(Screen next, boolean disposeOld) {
        Gdx.app.postRunnable(() -> {
            Screen old = getScreen();
            setScreen(next);
            if (disposeOld && old != null && old != next) old.dispose();
        });
    }

    @Override
    public void dispose() {
        Screen s = getScreen();
        if (s != null) s.dispose();
        audio.dispose();
        sprites.dispose();
        textures.dispose();
        skin.dispose();
        fonts.dispose();
        shapes.dispose();
        batch.dispose();
    }
}
