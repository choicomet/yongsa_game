package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
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
import com.yongsa.ui.KeyboardInput;

/** 타이틀: 새 게임, 이어하기, 설정, 종료. 정상 저장이 없으면 이어하기를 비활성화한다. */
public class TitleScreen extends ScreenAdapter {
    private final YongsaGame g;
    private final Stage stage;
    private final Windows windows;
    private final GlyphLayout layout = new GlyphLayout();
    private SaveManager.LoadResult load;
    private float t;
    /** 배경: 실제 플레이 화면을 찍은 그림 (없으면 어두운 바탕만). 로고: tools/make_title.py 가 만든 그림. */
    private Texture background, logo;

    public TitleScreen(YongsaGame g) {
        this.g = g;
        this.stage = new Stage(new FitViewport(960, 540), g.batch);
        this.windows = new Windows(stage, g.skin);
        windows.onClick = () -> g.audio.play("ui_click");
    }

    @Override
    public void show() {
        load = g.saves.load();
        if (background == null) background = texture("ui/title_bg.png", Texture.TextureFilter.Linear);
        if (logo == null) logo = texture("ui/title_logo.png", Texture.TextureFilter.Nearest);
        buildMenu();
        Gdx.input.setInputProcessor(new KeyboardInput(windows::keyDown));
        g.audio.music("bgm_title");
    }

    private static Texture texture(String path, Texture.TextureFilter filter) {
        if (!Gdx.files.internal(path).exists()) return null;
        Texture tex = new Texture(Gdx.files.internal(path));
        tex.setFilter(filter, filter);
        return tex;
    }

    private void buildMenu() {
        stage.clear();
        Table root = new Table();
        root.setFillParent(true);
        // 메뉴는 왼쪽 아래: 오른쪽은 배경(플레이 화면)이 보이게 비워 둔다
        root.left().bottom().padLeft(72).padBottom(52);
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
        windows.setBaseRoot(root);
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
        // 배경: 플레이 화면을 천천히 훑는다
        if (background != null) {
            float zoom = 1.12f, bw = 960 * zoom, bh = 540 * zoom;
            float px = (bw - 960) / 2f * (1 + MathUtils.sin(t * 0.11f)), py = (bh - 540) / 2f * (1 + MathUtils.sin(t * 0.07f + 1f));
            b.setColor(Color.WHITE);
            b.draw(background, -px, -py, bw, bh);
        }
        // 글씨가 놓이는 왼쪽과 아래쪽을 어둡게
        for (int i = 0; i < 24; i++) {
            b.setColor(0.02f, 0.015f, 0.03f, 0.05f);
            b.draw(g.textures.white, 0, 0, 640 - i * 22, 540);
        }
        b.setColor(0.02f, 0.015f, 0.03f, 0.35f);
        b.draw(g.textures.white, 0, 0, 960, 540);
        b.setColor(Color.WHITE);
        b.draw(g.textures.vignette, 0, 0, 960, 540);

        float top = 540 - 64;
        if (logo != null) {
            // 로고는 살짝 떠오르내린다
            float ly = top - logo.getHeight() + MathUtils.sin(t * 1.6f) * 2f;
            b.draw(logo, 64, ly, logo.getWidth(), logo.getHeight());
            top = ly - 14;
        } else {
            BitmapFont tf = g.fonts.title;
            tf.setColor(1f, 0.82f, 0.4f, 1);
            tf.draw(b, "용사가 다한다", 64, top);
            tf.setColor(Color.WHITE);
            top -= 60;
        }
        BitmapFont sf = g.fonts.body;
        sf.setColor(0.9f, 0.86f, 0.78f, 1);
        sf.draw(b, "캐고, 만들고, 강화하고, 쓰러뜨린다.", 72, top);
        sf.setColor(1f, 0.6f, 0.45f, 1);
        sf.draw(b, "전부 혼자서.", 72, top - 24);
        sf.setColor(Color.WHITE);
        b.end();

        stage.act(delta);
        stage.draw();
        windows.drawKeyboardFocus(g.batch, g.textures, g.fonts);
    }

    @Override
    public void dispose() {
        stage.dispose();
        if (background != null) background.dispose();
        if (logo != null) logo.dispose();
    }
}
