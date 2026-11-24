package com.yongsa.map;

import java.util.ArrayList;
import java.util.List;

/**
 * 미리 준비할 범위 (렉 줄이기). near 에 걸치는 그림은 미리 계산하고, keep 밖으로 나간 그림만 내린다.
 * 화면 둘레 한 칸에 더해, 순간이동 문(동굴 입구 등) 너머의 방도 넣는다: 걸어서 다가갈 수 없는 곳이라서.
 */
final class PrefetchZones {
    private final List<float[]> near = new ArrayList<>(), keep = new ArrayList<>();

    void clear() { near.clear(); keep.clear(); }

    void addNear(float x0, float y0, float x1, float y1) { near.add(new float[]{x0, y0, x1, y1}); }

    void addKeep(float x0, float y0, float x1, float y1) { keep.add(new float[]{x0, y0, x1, y1}); }

    boolean near(float x0, float y0, float x1, float y1) { return hit(near, x0, y0, x1, y1); }

    boolean keep(float x0, float y0, float x1, float y1) { return hit(keep, x0, y0, x1, y1) || hit(near, x0, y0, x1, y1); }

    private static boolean hit(List<float[]> rects, float x0, float y0, float x1, float y1) {
        for (float[] r : rects) if (x0 <= r[2] && x1 >= r[0] && y0 <= r[3] && y1 >= r[1]) return true;
        return false;
    }
}
