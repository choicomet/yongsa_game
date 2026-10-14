package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.yongsa.YongsaGame;

/**
 * 이야기 그림 화면 (시작·엔딩 공통): 그림 한 장 + 아래 자막.
 * 그림은 천천히 확대되고(켄 번스), 아무 키나 누르면 다음 장, Esc는 건너뛰기.
 */
public class StoryScreen extends BaseScreen {

    /** 한 장. image는 assets/story/ 아래 파일 이름 (null이면 바로 앞 그림을 어둡게). */
    public record Page(String image, String text) {
    }

    private final Page[] pages;
    private final Runnable onDone;
    private final SpriteBatch batch = new SpriteBatch();
    private final Label text, page;
    private Texture current, previous;
    private float t, fade = 1f, dim;
    private int index = -1;
    private boolean finished;

    public StoryScreen(YongsaGame game, Page[] pages, String music, Runnable onDone) {
        super(game);
        this.pages = pages;
        this.onDone = onDone;
        Table root = new Table();
        root.setFillParent(true);
        root.bottom();
        Table box = new Table(game.skin);
        box.setBackground(game.skin.getDrawable("panel-hud"));
        text = new Label("", game.skin, "large");
        text.setAlignment(Align.center);
        text.setWrap(true);
        page = new Label("", game.skin, "small");
        box.add(text).width(1040).pad(14, 24, 6, 24).row();
        box.add(page).padBottom(8);
        root.add(box).padBottom(26);
        stage.addActor(root);
        if (music != null) game.sounds.playMusic(music);
        next();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) index = pages.length - 1;
                next();
                return true;
            }

            @Override
            public boolean touchDown(int x, int y, int p, int b) {
                next();
                return true;
            }
        });
    }

    private void next() {
        if (finished) return;
        index++;
        if (index >= pages.length) {
            finished = true;
            onDone.run();
            return;
        }
        Page p = pages[index];
        if (p.image() != null) {
            if (previous != null && previous != current) previous.dispose();
            previous = current;
            current = new Texture(Gdx.files.internal("story/" + p.image()));
            current.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            t = 0;
            fade = 0;
            dim = 0;
        } else {
            dim = 0.0001f;
        }
        text.setText(p.text());
        page.setText((index + 1) + " / " + pages.length + "     아무 키나 누르면 다음  ·  Esc 건너뛰기");
        text.clearActions();
        text.getColor().a = 0;
        text.addAction(Actions.fadeIn(0.6f));
    }

    @Override
    public void render(float delta) {
        t += delta;
        fade = Math.min(1f, fade + delta * 1.4f);
        if (dim > 0) dim = Math.min(0.55f, dim + delta);
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
        stage.getViewport().apply();
        batch.setProjectionMatrix(stage.getCamera().combined);
        batch.begin();
        float w = stage.getWidth(), h = stage.getHeight();
        if (previous != null && fade < 1f) drawImage(previous, w, h, t + 8f, 1f);
        if (current != null) drawImage(current, w, h, t, fade);
        if (dim > 0) {
            batch.setColor(0, 0, 0, dim);
            batch.draw(game.assets.white(), 0, 0, w, h);
        }
        batch.setColor(Color.WHITE);
        batch.end();
        stage.act(delta);
        stage.draw();
    }

    /** 화면을 꽉 채우고(비율 유지) 천천히 확대. */
    private void drawImage(Texture tex, float w, float h, float time, float alpha) {
        float scale = Math.max(w / tex.getWidth(), h / tex.getHeight()) * (1f + Math.min(0.08f, time * 0.008f));
        float iw = tex.getWidth() * scale, ih = tex.getHeight() * scale;
        batch.setColor(1, 1, 1, alpha);
        batch.draw(tex, (w - iw) / 2, (h - ih) / 2 + Math.min(20, time * 1.5f), iw, ih);
    }

    @Override
    public void dispose() {
        super.dispose();
        batch.dispose();
        if (current != null) current.dispose();
        if (previous != null && previous != current) previous.dispose();
    }
}
