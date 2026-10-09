package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;

/** 적 AI가 세계에 대해 알아야 하는 최소한의 정보. */
public interface Arena {
    Hero hero();

    /** 적 이동을 막는 사각형인지(벽, 고정 장애물, 모든 문). */
    boolean blockedForEnemy(Rectangle r);

    /** 벽과 닫힌 문에 막히기 전까지의 거리 (최대 max). */
    float rayLength(float ox, float oy, float dx, float dy, float max);

    Rectangle roomBounds();

    int aggressiveCount();

    void sfx(String id);
}
