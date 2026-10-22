package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.model.GameSession;
import com.yongsa.render.MapPainter;

/**
 * 오른쪽 위 원형 미니맵 (2026-10-10 결정). 용사를 가운데에 두고 주변 방과 길 안내를 그린다.
 * 지도를 작은 텍스처에 그린 뒤, 원 밖을 버리는 셰이더로 동그랗게 잘라 그린다.
 */
public class Minimap extends Actor implements Disposable {

    private static final int SIZE = 200;
    private static final float SCALE = 0.1f;
    private static final String VERT = """
            attribute vec4 a_position;
            attribute vec4 a_color;
            attribute vec2 a_texCoord0;
            uniform mat4 u_projTrans;
            varying vec4 v_color;
            varying vec2 v_texCoords;
            void main() {
                v_color = a_color;
                v_color.a = v_color.a * (255.0/254.0);
                v_texCoords = a_texCoord0;
                gl_Position = u_projTrans * a_position;
            }
            """;
    private static final String FRAG = """
            #ifdef GL_ES
            precision mediump float;
            #endif
            varying vec4 v_color;
            varying vec2 v_texCoords;
            uniform sampler2D u_texture;
            void main() {
                vec2 d = v_texCoords - vec2(0.5);
                if (dot(d, d) > 0.25) discard;
                gl_FragColor = v_color * texture2D(u_texture, v_texCoords);
            }
            """;

    private final GameSession session;
    private final MapPainter painter;
    private final FrameBuffer fbo;
    private final TextureRegion fboRegion;
    private final ShaderProgram circle;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Matrix4 fboMatrix = new Matrix4().setToOrtho2D(0, 0, SIZE, SIZE);
    private float time;

    public Minimap(GameSession session) {
        this.session = session;
        this.painter = new MapPainter(session);
        fbo = new FrameBuffer(Pixmap.Format.RGBA8888, SIZE, SIZE, false);
        fbo.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        fboRegion = new TextureRegion(fbo.getColorBufferTexture());
        fboRegion.flip(false, true);
        ShaderProgram.pedantic = false;
        circle = new ShaderProgram(VERT, FRAG);
        if (!circle.isCompiled()) Gdx.app.error("Minimap", circle.getLog());
        setSize(SIZE, SIZE);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        time += delta;
    }

    @Override
    public void draw(Batch batch, float alpha) {
        batch.end();

        fbo.begin();
        Gdx.gl.glClearColor(0.05f, 0.045f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(fboMatrix);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        painter.setTransform(session.unit.centerX(), session.unit.centerY(), SIZE / 2f, SIZE / 2f, SCALE);
        painter.paint(shapes, MapPainter.Mode.MINI, time);
        shapes.end();
        fbo.end();
        getStage().getViewport().apply();

        batch.begin();
        batch.setShader(circle);
        batch.setColor(1, 1, 1, alpha);
        batch.draw(fboRegion, getX(), getY(), getWidth(), getHeight());
        batch.setShader(null);
        batch.end();

        // 테두리
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.setProjectionMatrix(batch.getProjectionMatrix());
        shapes.setTransformMatrix(batch.getTransformMatrix());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float cx = getX() + getWidth() / 2, cy = getY() + getHeight() / 2, r = getWidth() / 2;
        shapes.setColor(new Color(0.55f, 0.46f, 0.32f, alpha));
        int n = 64;
        for (int i = 0; i < n; i++) {
            float a1 = MathUtils.PI2 * i / n, a2 = MathUtils.PI2 * (i + 1) / n;
            shapes.rectLine(cx + r * MathUtils.cos(a1), cy + r * MathUtils.sin(a1), cx + r * MathUtils.cos(a2), cy + r * MathUtils.sin(a2), 4f);
        }
        shapes.end();
        batch.begin();
    }

    @Override
    public void dispose() {
        fbo.dispose();
        circle.dispose();
        shapes.dispose();
    }
}
