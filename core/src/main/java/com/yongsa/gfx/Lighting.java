package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.utils.Disposable;

/**
 * 던전 조명. 어두운 바탕에 빛을 더한 빛 지도를 만든 뒤 장면에 곱한다.
 * 횃불·화로 주변만 밝게 보이는 실내 분위기를 만든다.
 */
public class Lighting implements Disposable {
    private final FrameBuffer fbo;
    private final TextureRegion region;
    private final Textures tx;
    private final int w, h;

    public Lighting(Textures tx, int w, int h) {
        this.tx = tx;
        this.w = w;
        this.h = h;
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, w, h, false);
        region = new TextureRegion(fbo.getColorBufferTexture());
        region.flip(false, true);
    }

    public void begin(SpriteBatch batch, Camera cam, Color ambient) {
        fbo.begin();
        Gdx.gl.glClearColor(ambient.r, ambient.g, ambient.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
    }

    public void light(SpriteBatch batch, float x, float y, float radius, float r, float g, float b, float intensity) {
        batch.setColor(r * intensity, g * intensity, b * intensity, 1f);
        batch.draw(tx.glow, x - radius, y - radius, radius * 2, radius * 2);
    }

    public void end(SpriteBatch batch) {
        batch.setColor(Color.WHITE);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        fbo.end();
    }

    /** 빛 지도를 현재 카메라 화면 전체에 곱한다. */
    public void apply(SpriteBatch batch, Camera cam) {
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        batch.setBlendFunction(GL20.GL_DST_COLOR, GL20.GL_ZERO);
        float vw = cam.viewportWidth, vh = cam.viewportHeight;
        batch.draw(region, cam.position.x - vw / 2f, cam.position.y - vh / 2f, vw, vh);
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    @Override
    public void dispose() {
        fbo.dispose();
    }
}
