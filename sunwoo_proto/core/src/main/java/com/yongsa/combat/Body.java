package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.world.CollisionWorld;

/** 맵 위에서 움직이는 것의 공통 부분. body는 발밑 판정 사각형이고, 그리는 순서도 발밑 y로 정한다. */
public abstract class Body {

    public final Rectangle body;
    public float flash;
    public boolean faceLeft;
    public float animTime;
    public boolean moving;
    protected float knockX, knockY;
    private static final float KNOCK_DECAY = 14f;

    protected Body(float footX, float footY, float w, float h) {
        body = new Rectangle(footX - w / 2f, footY, w, h);
    }

    public float footX() { return body.x + body.width / 2f; }
    public float footY() { return body.y; }
    public float centerX() { return body.x + body.width / 2f; }
    public float centerY() { return body.y + body.height / 2f; }

    /** 맞은 반대쪽으로 distance(px)만큼 밀려나게 한다 (FR-전투-07). */
    public void knockback(float fromX, float fromY, float distance) {
        float dx = centerX() - fromX, dy = centerY() - fromY;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.01f) { dx = 0; dy = -1; len = 1; }
        float v = distance * KNOCK_DECAY;
        knockX = dx / len * v;
        knockY = dy / len * v;
    }

    protected void updateKnockback(float delta, CollisionWorld c) {
        if (knockX == 0 && knockY == 0) return;
        c.move(body, knockX * delta, knockY * delta);
        float k = (float) Math.exp(-KNOCK_DECAY * delta);
        knockX *= k;
        knockY *= k;
        if (Math.abs(knockX) < 3 && Math.abs(knockY) < 3) knockX = knockY = 0;
    }

    public void stopKnockback() {
        knockX = knockY = 0;
    }

    public void teleport(float fx, float fy) {
        body.setPosition(fx - body.width / 2f, fy);
        stopKnockback();
    }

    public float distanceTo(Body o) {
        float dx = o.centerX() - centerX(), dy = o.centerY() - centerY();
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
