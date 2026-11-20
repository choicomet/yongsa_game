package com.yongsa.combat;

import com.yongsa.map.WorldMap;

/** 타일과 발 사각형의 겹침으로 막힌 곳을 처리한다. 물리 엔진은 쓰지 않는다. */
public final class Physics {
    private Physics() {
    }

    /** 축마다 따로, 한 번에 최대 4픽셀씩 나눠 움직여 벽을 뚫지 않게 한다. 실제로 움직인 거리 비율을 돌려준다. */
    public static boolean move(WorldMap map, Actor a, float dx, float dy, int tileSize) {
        float dist = Math.max(Math.abs(dx), Math.abs(dy));
        int steps = Math.max(1, (int) Math.ceil(dist / 4f));
        float sx = dx / steps, sy = dy / steps;
        boolean blocked = false;
        for (int i = 0; i < steps; i++) {
            if (sx != 0) {
                if (free(map, a.x + sx, a.y, a, tileSize)) a.x += sx;
                else blocked = true;
            }
            if (sy != 0) {
                if (free(map, a.x, a.y + sy, a, tileSize)) a.y += sy;
                else blocked = true;
            }
        }
        return blocked;
    }

    private static final float[] SLIDE_COS = {(float) Math.cos(Math.PI / 8), (float) Math.cos(Math.PI / 4), (float) Math.cos(3 * Math.PI / 8)};
    private static final float[] SLIDE_SIN = {(float) Math.sin(Math.PI / 8), (float) Math.sin(Math.PI / 4), (float) Math.sin(3 * Math.PI / 8)};

    /**
     * 용사 걷기·구르기: 막히면 벽을 따라 미끄러진다.
     * 비스듬한 담이나 작은 돌출부에 정면으로 닿아도 멈추지 않고, 벽 방향 성분만큼 옆으로 흘러간다.
     * 진행 방향을 22.5°씩 돌려 보고 cos 만큼 줄인 걸음(벽에 투영한 속도)을 쓴다. 정면 벽이면 그대로 멈춘다.
     */
    public static boolean walk(WorldMap map, Actor a, float dx, float dy, int tileSize) {
        float dist = Math.max(Math.abs(dx), Math.abs(dy));
        int steps = Math.max(1, (int) Math.ceil(dist / 4f));
        float sx = dx / steps, sy = dy / steps;
        boolean blocked = false;
        for (int i = 0; i < steps; i++) {
            if (free(map, a.x + sx, a.y + sy, a, tileSize)) { a.x += sx; a.y += sy; continue; }
            blocked = true;
            if (slide(map, a, sx, sy, tileSize)) continue;
            // 마지막으로 예전처럼 축마다 따로 시도한다.
            if (sx != 0 && free(map, a.x + sx, a.y, a, tileSize)) a.x += sx;
            else if (sy != 0 && free(map, a.x, a.y + sy, a, tileSize)) a.y += sy;
            else break;
        }
        return blocked;
    }

    private static boolean slide(WorldMap map, Actor a, float sx, float sy, int tileSize) {
        for (int k = 0; k < SLIDE_COS.length; k++) {
            float c = SLIDE_COS[k], s = SLIDE_SIN[k];
            float lx = (sx * c - sy * s) * c, ly = (sx * s + sy * c) * c;
            float rx = (sx * c + sy * s) * c, ry = (-sx * s + sy * c) * c;
            boolean left = free(map, a.x + lx, a.y + ly, a, tileSize);
            boolean right = free(map, a.x + rx, a.y + ry, a, tileSize);
            if (left && right) {
                // 기둥 정면처럼 양쪽이 다 열려 있으면 더 멀리 열린 쪽으로 돈다.
                boolean leftFar = free(map, a.x + lx * 3, a.y + ly * 3, a, tileSize);
                boolean rightFar = free(map, a.x + rx * 3, a.y + ry * 3, a, tileSize);
                if (rightFar && !leftFar) left = false;
            }
            if (left) { a.x += lx; a.y += ly; return true; }
            if (right) { a.x += rx; a.y += ry; return true; }
        }
        return false;
    }

    public static boolean free(WorldMap map, float x, float y, Actor a, int tileSize) {
        return map.free(x, y, a.hw, a.hh);
    }
}
