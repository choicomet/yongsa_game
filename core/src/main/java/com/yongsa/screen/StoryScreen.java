package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.YongsaGame;
import com.yongsa.gfx.WorldRenderer;

/**
 * 시작 소개와 엔딩에 쓰는 세 장짜리 이야기 화면.
 * 클릭/Space/Enter 로 다음, Esc 로 건너뛰기.
 */
public class StoryScreen extends ScreenAdapter {
    public enum Kind { INTRO, ENDING }

    private final YongsaGame g;
    private final Kind kind;
    private final Runnable onFinish;
    private final OrthographicCamera cam = new OrthographicCamera();
    private final FitViewport viewport = new FitViewport(960, 540, cam);
    private final GlyphLayout layout = new GlyphLayout();
    private int page;
    private float t;
    private boolean finished;

    private static final String[][] TEXT = {
            {
                    "거대한 던전의 봉인이 다시 흔들리기 시작했다.\n용사는 홀로 그 입구 앞에 섰다.",
                    "함께할 동료도, 장비를 맡길 대장장이도 없다.\n필요한 것은 모두 스스로 마련해야 한다.",
                    "던전 한가운데, 이전 탐험대가 남긴 휴식처와 대장간.\n이제부터 이곳이 용사의 거점이다."
            },
            {
                    "세 수호자가 모두 쓰러지자\n던전 깊은 곳의 마지막 봉인이 풀렸다.",
                    "던전을 짓누르던 어둠이 걷히고\n멈춰 있던 바람이 다시 흐르기 시작한다.",
                    "광물을 캐고, 무기를 벼리고, 수호자를 쓰러뜨린 것은 모두 한 사람.\n용사가 다했다."
            }
    };

    public StoryScreen(YongsaGame g, Kind kind, Runnable onFinish) {
        this.g = g;
        this.kind = kind;
        this.onFinish = onFinish;
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    finish();
                } else if (keycode == Input.Keys.SPACE || keycode == Input.Keys.ENTER || keycode == Input.Keys.E) {
                    next();
                }
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                next();
                return true;
            }
        });
        g.audio.music(kind == Kind.INTRO ? "bgm_dungeon" : "bgm_title");
    }

    private void next() {
        if (t < 0.25f) return;
        g.audio.play("ui_click");
        page++;
        t = 0;
        if (page >= 3) finish();
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
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (finished) return;
        viewport.apply();
        ShapeRenderer s = g.shapes;
        SpriteBatch b = g.batch;
        s.setProjectionMatrix(cam.combined);
        b.setProjectionMatrix(cam.combined);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        float time = t + page * 10;
        drawScene(s, b, time);

        // 글
        float a = Math.min(1, t * 2);
        b.begin();
        b.setColor(0, 0, 0, 0.6f);
        b.draw(g.textures.white, 0, 0, 960, 130);
        b.setColor(Color.WHITE);
        BitmapFont f = g.fonts.large;
        f.setColor(0.95f, 0.92f, 0.85f, a);
        f.draw(b, TEXT[kind.ordinal()][Math.min(page, 2)], 0, 100, 960, Align.center, true);
        BitmapFont sm = g.fonts.small;
        sm.setColor(0.7f, 0.68f, 0.65f, 0.8f);
        String hint = (page + 1) + " / 3   ·   클릭 또는 Space: 다음   ·   Esc: 건너뛰기";
        layout.setText(sm, hint);
        sm.draw(b, hint, 960 - layout.width - 16, 22);
        sm.setColor(Color.WHITE);
        f.setColor(Color.WHITE);
        if (kind == Kind.ENDING && page == 2 && t > 1.5f) {
            BitmapFont tf = g.fonts.title;
            String end = "용사가 다한다";
            layout.setText(tf, end);
            tf.setColor(1f, 0.82f, 0.4f, Math.min(1, (t - 1.5f)));
            tf.draw(b, end, 480 - layout.width / 2f, 400);
            tf.setColor(Color.WHITE);
        }
        b.end();
    }

    private void drawScene(ShapeRenderer s, SpriteBatch b, float time) {
        int scene = kind == Kind.INTRO ? page : page + 3;
        s.begin(ShapeRenderer.ShapeType.Filled);
        switch (scene) {
            case 0: { // 외부: 밤하늘과 던전 입구
                s.rect(0, 130, 960, 410, new Color(0.05f, 0.05f, 0.12f, 1), new Color(0.05f, 0.05f, 0.12f, 1),
                        new Color(0.12f, 0.1f, 0.22f, 1), new Color(0.12f, 0.1f, 0.22f, 1));
                s.setColor(1, 1, 0.9f, 1);
                for (int i = 0; i < 40; i++) {
                    float x = (i * 137) % 960, y = 300 + (i * 71) % 240;
                    float tw = 0.5f + 0.5f * MathUtils.sin(time * 2 + i);
                    s.setColor(1, 1, 0.9f, tw);
                    s.rect(x, y, 2, 2);
                }
                s.setColor(0.08f, 0.07f, 0.1f, 1);
                s.triangle(0, 130, 260, 360, 520, 130);
                s.triangle(380, 130, 700, 400, 960, 130);
                s.setColor(0.18f, 0.16f, 0.2f, 1);
                s.rect(380, 130, 200, 210);
                s.setColor(0.03f, 0.02f, 0.05f, 1);
                s.rect(420, 130, 120, 150);
                s.arc(480, 280, 60, 0, 180, 20);
                float pulse = 0.6f + 0.4f * MathUtils.sin(time * 2);
                s.setColor(0.6f * pulse, 0.3f * pulse, 1f * pulse, 1);
                s.circle(480, 310, 14, 24);
                break;
            }
            case 1: { // 내부 통로
                s.setColor(0.07f, 0.06f, 0.08f, 1);
                s.rect(0, 130, 960, 410);
                s.setColor(0.16f, 0.14f, 0.16f, 1);
                s.triangle(0, 130, 380, 300, 0, 540);
                s.triangle(960, 130, 580, 300, 960, 540);
                s.setColor(0.12f, 0.1f, 0.1f, 1);
                s.triangle(0, 130, 960, 130, 480, 300);
                for (int i = 0; i < 3; i++) {
                    float k = 1f / (i + 1);
                    float fl = 0.8f + 0.2f * MathUtils.sin(time * 9 + i);
                    s.setColor(1f * fl, 0.55f * fl, 0.2f, 1);
                    s.circle(480 - 330 * k, 300 + 120 * k, 7 * k + 2, 12);
                    s.circle(480 + 330 * k, 300 + 120 * k, 7 * k + 2, 12);
                }
                break;
            }
            case 2: // 캠프
            case 5: {
                s.setColor(0.09f, 0.08f, 0.08f, 1);
                s.rect(0, 130, 960, 410);
                s.setColor(0.2f, 0.16f, 0.13f, 1);
                s.ellipse(180, 150, 600, 200);
                float fl = 0.85f + 0.15f * MathUtils.sin(time * 11);
                s.setColor(0.35f * fl, 0.18f * fl, 0.08f, 1);
                s.ellipse(330, 180, 300, 130);
                break;
            }
            case 3: { // 봉인 해제
                s.setColor(0.04f, 0.03f, 0.07f, 1);
                s.rect(0, 130, 960, 410);
                float r = Math.min(1, t / 2f);
                for (int i = 6; i >= 1; i--) {
                    s.setColor(0.5f + i * 0.06f, 0.3f, 1f, 0.12f * r);
                    s.circle(480, 330, 40 * i * (0.6f + r), 40);
                }
                break;
            }
            case 4: { // 빛
                float r = Math.min(1, t / 3f);
                s.rect(0, 130, 960, 410, new Color(0.1f, 0.08f, 0.1f, 1), new Color(0.1f, 0.08f, 0.1f, 1),
                        new Color(0.4f * r + 0.1f, 0.35f * r + 0.08f, 0.25f * r + 0.1f, 1),
                        new Color(0.4f * r + 0.1f, 0.35f * r + 0.08f, 0.25f * r + 0.1f, 1));
                s.setColor(1f, 0.95f, 0.8f, 0.15f * r);
                s.triangle(380, 540, 580, 540, 480, 130);
                break;
            }
            default:
                break;
        }
        s.end();

        b.begin();
        float hx = 480, hy = 150;
        if (scene == 2 || scene == 5) {
            TextureRegion log = g.sprites.placeholder("campfire_log");
            b.draw(log, 480 - log.getRegionWidth(), 210, log.getRegionWidth() * 2, log.getRegionHeight() * 2);
            b.draw(g.sprites.placeholder("bedroll"), 220, 190, 128, 40);
            b.draw(g.sprites.placeholder("anvil"), 650, 190, 64, 44);
            b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            float fl = 1 + 0.15f * MathUtils.sin(time * 13);
            b.setColor(1f, 0.55f, 0.15f, 0.9f);
            b.draw(g.textures.flame, 480 - 18, 228, 36, 52 * fl);
            b.setColor(1f, 0.85f, 0.4f, 0.8f);
            b.draw(g.textures.flame, 480 - 9, 228, 18, 30 * fl);
            b.setColor(1f, 0.5f, 0.2f, 0.25f);
            b.draw(g.textures.glow, 480 - 220, 240 - 200, 440, 400);
            b.setColor(Color.WHITE);
            b.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            hx = 560;
            hy = 160;
        }
        if (scene == 3) {
            TextureRegion boss = WorldRenderer.frame(g.sprites, "boss_temple", "idle", time);
            float fall = Math.min(1, t / 2f);
            b.setColor(1, 1, 1, 1 - fall);
            b.draw(boss, 480 - 64, 200, 128, 96);
            b.setColor(Color.WHITE);
            hy = 140;
            hx = 300;
        }
        if (scene == 1) {
            hx = 480 + MathUtils.sin(time) * 4;
            hy = 140 + (time % 4) * 6;
        }
        TextureRegion hero = WorldRenderer.frame(g.sprites, "hero", scene == 1 ? "walk" : "idle", time);
        b.draw(hero, hx - hero.getRegionWidth(), hy, hero.getRegionWidth() * 2, hero.getRegionHeight() * 2);
        b.end();
    }
}
