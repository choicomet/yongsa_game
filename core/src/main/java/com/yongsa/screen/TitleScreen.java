package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.gfx.WorldRenderer;
import com.yongsa.model.GameState;
import com.yongsa.save.SaveManager;
import com.yongsa.ui.ForgeWindow;
import com.yongsa.ui.SettingsWindow;
import com.yongsa.ui.Windows;

/** 타이틀: 새 게임, 이어하기, 설정, 종료. 정상 저장이 없으면 이어하기를 비활성화한다. */
public class TitleScreen extends ScreenAdapter {
    private final YongsaGame g;
    private final Stage stage;
    private final Windows windows;
    private final GlyphLayout layout = new GlyphLayout();
    private SaveManager.LoadResult load;
    private float t;

    public TitleScreen(YongsaGame g) {
        this.g = g;
        this.stage = new Stage(new FitViewport(960, 540), g.batch);
        this.windows = new Windows(stage, g.skin);
        windows.onClick = () -> g.audio.play("ui_click");
    }

    @Override
    public void show() {
        load = g.saves.load();
        buildMenu();
        InputMultiplexer mux = new InputMultiplexer(stage, new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) return windows.escape();
                return false;
            }
        });
        Gdx.input.setInputProcessor(mux);
        g.audio.music("bgm_title");
    }

    private void buildMenu() {
        stage.clear();
        Table root = new Table();
        root.setFillParent(true);
        root.bottom().padBottom(56);
        boolean canContinue = load.status == SaveManager.LoadStatus.OK
                || load.status == SaveManager.LoadStatus.RESTORED_FROM_BACKUP;
        root.add(windows.button("새 게임", this::newGame)).width(220).padBottom(8).row();
        TextButton cont = windows.button("이어하기", this::continueGame);
        cont.setDisabled(!canContinue);
        root.add(cont).width(220).padBottom(8).row();
        root.add(windows.button("설정", () -> SettingsWindow.open(windows, g.settings, g::applySettings)))
                .width(220).padBottom(8).row();
        root.add(windows.button("종료", () -> Gdx.app.exit())).width(220).row();
        if (load.message != null && !canContinue) {
            Label l = windows.label(load.message, "warn");
            root.add(l).padTop(10).row();
        }
        stage.addActor(root);
    }

    private void newGame() {
        if (g.saves.hasAnySaveFile()) {
            windows.confirm("새 게임", "저장된 진행이 있습니다. 새 게임을 시작하면 진행이 초기화됩니다.\n계속할까요?",
                    "새로 시작", this::startNew);
        } else {
            startNew();
        }
    }

    private void startNew() {
        if (load.status == SaveManager.LoadStatus.CORRUPT || load.status == SaveManager.LoadStatus.UNSUPPORTED_VERSION) {
            // 읽을 수 없는 파일은 지우지 않고 다른 이름으로 보존한다
            g.saves.archiveUnreadable();
        }
        GameState s = GameState.newGame(System.nanoTime() ^ System.currentTimeMillis(), g.balance.dataVersion, g.settings);
        ForgeWindow.reset();
        boolean saved = g.saves.save(s); // 새 게임의 초기 상태 생성 직후 저장
        String msg = saved ? null : "저장하지 못했습니다: " + g.saves.lastError();
        // 소개를 보거나 건너뛰어도 같은 camp_spawn 에서 같은 초기 상태로 시작한다
        g.switchTo(new StoryScreen(g, StoryScreen.Kind.INTRO,
                () -> g.switchTo(new GameScreen(g, s, msg), true)), true);
    }

    private void continueGame() {
        load = g.saves.load();
        if (load.state == null) {
            buildMenu();
            return;
        }
        ForgeWindow.reset();
        g.switchTo(new GameScreen(g, load.state, load.message), true);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void render(float delta) {
        t += delta;
        Gdx.gl.glClearColor(0.03f, 0.025f, 0.04f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.getViewport().apply();
        SpriteBatch b = g.batch;
        b.setProjectionMatrix(stage.getCamera().combined);
        b.begin();
        // 배경: 모닥불과 용사
        b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        float fl = 1 + 0.08f * MathUtils.sin(t * 11) + 0.05f * MathUtils.sin(t * 23);
        b.setColor(1f, 0.45f, 0.15f, 0.35f);
        b.draw(g.textures.glow, 480 - 360 * fl, 260 - 300 * fl, 720 * fl, 600 * fl);
        b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        b.setColor(Color.WHITE);
        TextureRegion log = g.sprites.placeholder("campfire_log");
        b.draw(log, 480 - log.getRegionWidth() * 1.5f, 262, log.getRegionWidth() * 3, log.getRegionHeight() * 3);
        b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        b.setColor(1f, 0.5f, 0.15f, 0.95f);
        b.draw(g.textures.flame, 480 - 27, 290, 54, 78 * fl);
        b.setColor(1f, 0.85f, 0.4f, 0.85f);
        b.draw(g.textures.flame, 480 - 13, 290, 26, 44 * fl);
        b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        b.setColor(Color.WHITE);
        TextureRegion hero = WorldRenderer.frame(g.sprites, "hero", "idle", t);
        b.draw(hero, 560, 262, hero.getRegionWidth() * 3, hero.getRegionHeight() * 3);
        TextureRegion sword = g.sprites.get("sword").frame("idle", 0);
        b.draw(sword, 650, 268, 0, 0, sword.getRegionWidth() * 3, sword.getRegionHeight() * 3, 1, 1, -20);

        BitmapFont tf = g.fonts.title;
        String title = "용사가 다한다";
        layout.setText(tf, title);
        tf.setColor(0.1f, 0.05f, 0.02f, 1);
        tf.draw(b, title, 480 - layout.width / 2f + 3, 488 - 3);
        tf.setColor(1f, 0.82f, 0.4f, 1);
        tf.draw(b, title, 480 - layout.width / 2f, 488);
        BitmapFont sf = g.fonts.body;
        String sub = "캐고, 만들고, 강화하고, 쓰러뜨린다. 전부 혼자서.";
        layout.setText(sf, sub);
        sf.setColor(0.8f, 0.76f, 0.7f, 1);
        sf.draw(b, sub, 480 - layout.width / 2f, 432);
        sf.setColor(Color.WHITE);
        tf.setColor(Color.WHITE);
        b.end();

        stage.act(delta);
        stage.draw();
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
