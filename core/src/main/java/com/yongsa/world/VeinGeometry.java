package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

/** 광맥의 충돌 크기. 렌더링과 검증이 같은 값을 쓴다. */
public final class VeinGeometry {
    private VeinGeometry() {}

    public static final float WIDTH = 26f;
    public static final float HEIGHT = 14f;

    public static Rectangle solidRect(float cx, float cy) {
        return new Rectangle(cx - WIDTH / 2f, cy - HEIGHT / 2f, WIDTH, HEIGHT);
    }
}
