package com.yongsa.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;

/**
 * 빛 지도: 어두운 바탕(주변광)에 빛을 더해서(가산) 그린 뒤, 세계 그림 위에 곱한다.
 * 화면 크기(640×360) 버퍼 하나만 쓰므로 가볍다. 셰이더 없이 혼합 방식만 바꿔서 처리한다.
 * <pre>
 *   begin(ambient) → light(...) 여러 번 → end() → apply()
 * </pre>
 */
public class LightMap implements Disposable {

    private final FrameBuffer fbo;
    private final SpriteBatch batch;
    private final TextureRegion glow;
    private final OrthographicCamera camera;
    private final float w, h;
    private boolean drawing;

    public LightMap(SpriteBatch batch, TextureRegion glow, OrthographicCamera camera, int w, int h) {
        this.batch = batch;
        this.glow = glow;
        this.camera = camera;
        this.w = w;
        this.h = h;
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
    }

    /** 주변광 색으로 비우고 빛 그리기를 시작한다. */
    public void begin(Color ambient) {
        fbo.begin();
        Gdx.gl.glClearColor(ambient.r, ambient.g, ambient.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(camera.combined);
        batch.enableBlending();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        drawing = true;
    }

    /** (x, y)에 반지름 r의 빛. 세기는 0~1 정도 (겹치면 더해진다). */
    public void light(float x, float y, float r, float red, float green, float blue, float intensity) {
        if (!drawing || intensity <= 0 || r <= 0) return;
        float left = camera.position.x - w / 2 - r, bottom = camera.position.y - h / 2 - r;
        if (x < left || x > left + w + r * 2 || y < bottom || y > bottom + h + r * 2) return;
        batch.setColor(red * intensity, green * intensity, blue * intensity, 1f);
        batch.draw(glow, x - r, y - r, r * 2, r * 2);
    }

    public void light(float x, float y, float r, Color c, float intensity) {
        light(x, y, r, c.r, c.g, c.b, intensity);
    }

    public void end() {
        batch.end();
        batch.setColor(Color.WHITE);
        fbo.end();
        drawing = false;
    }

    /** 세계 그림 위에 곱한다. 호출 전에 뷰포트를 다시 적용해 둘 것. */
    public void apply() {
        Texture t = fbo.getColorBufferTexture();
        float x = camera.position.x - w / 2, y = camera.position.y - h / 2;
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        batch.begin();
        batch.setColor(Color.WHITE);
        batch.draw(t, x, y, w, h, 0, 0, 1, 1);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void dispose() {
        fbo.dispose();
    }
}
