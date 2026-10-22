package com.yongsa.world;

import com.badlogic.gdx.graphics.OrthographicCamera;

/**
 * 카메라가 용사를 부드럽게 따라간다 (FR-맵-07). 1 - e^(-k·delta)로 보간해 프레임 속도와 상관없이 같다 (NFR-04).
 * 맵 밖은 보이지 않게 막고, 픽셀이 떨리지 않게 정수 위치로 맞춘다. 흔들림(보스 공격·땅 갈라짐)도 여기서.
 */
public class CameraFollow {

    private final float sharpness;
    private float x, y, shake, shakeTime;
    private boolean initialized;

    public CameraFollow(float sharpness) {
        this.sharpness = sharpness;
    }

    public void snapTo(float tx, float ty) {
        x = tx;
        y = ty;
        initialized = true;
    }

    public void shake(float strength, float time) {
        shake = Math.max(shake, strength);
        shakeTime = Math.max(shakeTime, time);
    }

    public void update(OrthographicCamera cam, float tx, float ty, float delta, float mapW, float mapH) {
        if (!initialized) snapTo(tx, ty);
        float a = 1f - (float) Math.exp(-sharpness * delta);
        x += (tx - x) * a;
        y += (ty - y) * a;
        float hw = cam.viewportWidth * cam.zoom / 2f, hh = cam.viewportHeight * cam.zoom / 2f;
        x = clamp(x, hw, mapW - hw);
        y = clamp(y, hh, mapH - hh);
        float ox = 0, oy = 0;
        if (shakeTime > 0) {
            shakeTime -= delta;
            ox = (float) (Math.random() * 2 - 1) * shake;
            oy = (float) (Math.random() * 2 - 1) * shake;
            if (shakeTime <= 0) shake = 0;
        }
        cam.position.set(Math.round(x + ox), Math.round(y + oy), 0);
        cam.update();
    }

    private static float clamp(float v, float min, float max) {
        if (min > max) return (min + max) / 2f;
        return Math.max(min, Math.min(max, v));
    }
}
