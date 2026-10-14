package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.ui.KeyboardInput;

import java.util.ArrayList;
import java.util.List;

/**
 * 시작과 마무리의 이야기 화면. 그림을 화면 가득 띄우고 아래 띠에 한 문장씩 적는다.
 * 장면(그림 파일과 문장)은 assets/data/story.json 에 있다: opening 은 새 게임 시작 전, ending 은 마지막 보스 처치 후.
 * J/Enter 로 다음, K/Esc 로 건너뛰기.
 */
public class StoryScreen extends ScreenAdapter {
    public enum Kind { INTRO, ENDING }

    private static final float FADE = 0.45f;

    private final YongsaGame g;
    private final Kind kind;
    private final Runnable onFinish;
    private final OrthographicCamera cam = new OrthographicCamera();
    private final FitViewport viewport = new FitViewport(960, 540, cam);
    private final GlyphLayout layout = new GlyphLayout();
    private final List<Texture> images = new ArrayList<>();
    private final List<String> captions = new ArrayList<>();
    private Texture logo;
    private int page;
    private float t;
    /** 다음 장면으로 넘어가는 중이면 0 이상 (어두워지는 시간). */
    private float leaving = -1;
    private boolean finished;

    public StoryScreen(YongsaGame g, Kind kind, Runnable onFinish) {
        this.g = g;
        this.kind = kind;
        this.onFinish = onFinish;
    }

    @Override
    public void show() {
        JsonValue root = new JsonReader().parse(Gdx.files.internal("data/story.json"));
        for (JsonValue scene = root.get(kind == Kind.INTRO ? "opening" : "ending").child; scene != null; scene = scene.next) {
            Texture tex = new Texture(Gdx.files.internal("story/" + scene.getString("image")));
            tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            images.add(tex);
            captions.add(scene.getString("caption"));
        }
        if (kind == Kind.ENDING && Gdx.files.internal("ui/title_logo.png").exists()) {
            logo = new Texture(Gdx.files.internal("ui/title_logo.png"));
            logo.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        Gdx.input.setInputProcessor(new KeyboardInput(keycode -> {
            if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.K) finish();
            else if (keycode == Input.Keys.J || keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) next();
            return true;
        }));
        g.audio.music(kind == Kind.INTRO ? "bgm_dungeon" : "bgm_title");
    }

    private void next() {
        if (t < 0.4f || leaving >= 0) return;
        g.audio.play("ui_click");
        leaving = 0;
    }

    private void finish() {
        if (finished) return;
        finished = true;
        onFinish.run();
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render(float delta) {
        t += delta;
        if (leaving >= 0) {
            leaving += delta;
            if (leaving >= FADE) {
                leaving = -1;
                page++;
                t = 0;
                if (page >= images.size()) finish();
            }
        }
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (finished || page >= images.size()) return;
        viewport.apply();
        SpriteBatch b = g.batch;
        b.setProjectionMatrix(cam.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        b.begin();

        // 그림: 화면 가득, 아주 천천히 다가간다
        float in = Math.min(1, t / FADE), out = leaving >= 0 ? 1 - leaving / FADE : 1;
        float alpha = in * out;
        float zoom = 1f + 0.035f * Math.min(1, t / 9f);
        float w = 960 * zoom, h = 540 * zoom;
        b.setColor(1, 1, 1, alpha);
        b.draw(images.get(page), 480 - w / 2f, 270 - h / 2f, w, h);

        // 아래 띠: 위로 갈수록 옅어지는 어둠 위에 문장
        for (int i = 0; i < 12; i++) {
            b.setColor(0.02f, 0.015f, 0.03f, 0.075f);
            b.draw(g.textures.white, 0, 0, 960, 150 - i * 6);
        }
        b.setColor(0.02f, 0.015f, 0.03f, 0.55f);
        b.draw(g.textures.white, 0, 0, 960, 84);
        b.setColor(0.85f, 0.7f, 0.4f, 0.8f * alpha);
        b.draw(g.textures.white, 0, 84, 960, 1);
        b.setColor(Color.WHITE);

        // 문장은 그림보다 조금 늦게 떠오른다
        float ta = Math.max(0, Math.min(1, (t - 0.35f) / 0.5f)) * out;
        BitmapFont f = g.fonts.body;
        f.setColor(0.97f, 0.94f, 0.86f, ta);
        layout.setText(f, captions.get(page), f.getColor(), 820, Align.center, true);
        f.draw(b, layout, 70, 42 + layout.height / 2f + 8);
        f.setColor(Color.WHITE);

        BitmapFont sm = g.fonts.small;
        sm.setColor(0.75f, 0.72f, 0.68f, 0.85f);
        String hint = (page + 1) + " / " + images.size() + "   ·   J 다음   ·   K 건너뛰기";
        layout.setText(sm, hint);
        sm.draw(b, hint, 960 - layout.width - 14, 18);
        sm.setColor(Color.WHITE);

        // 마지막 장면에는 제목을 띄운다
        if (kind == Kind.ENDING && page == images.size() - 1 && t > 2f && logo != null) {
            b.setColor(1, 1, 1, Math.min(1, (t - 2f) / 1.2f) * out);
            b.draw(logo, 480 - logo.getWidth() / 2f, 360, logo.getWidth(), logo.getHeight());
            b.setColor(Color.WHITE);
        }
        b.end();
    }

    @Override
    public void dispose() {
        for (Texture tex : images) tex.dispose();
        if (logo != null) logo.dispose();
    }
}
