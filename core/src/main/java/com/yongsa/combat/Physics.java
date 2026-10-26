package com.yongsa.combat;

import com.yongsa.world.WorldMap;

/** 타일과 발 사각형의 겹침으로 막힌 곳을 처리한다. 물리 엔진은 쓰지 않는다. */
public final class Physics {
    private Physics() {
    }

    /** 모서리에 살짝 걸렸을 때 옆으로 비켜 주는 최대 거리 (픽셀). */
    private static final int NUDGE = 6;
    /** 막힌 자리에서 빠져나올 빈자리를 찾는 최대 거리 (픽셀). */
    private static final int UNSTICK = 24;

    /** 축마다 따로, 한 번에 최대 4픽셀씩 나눠 움직여 벽을 뚫지 않게 한다. 실제로 움직인 거리 비율을 돌려준다. */
    public static boolean move(WorldMap map, Actor a, float dx, float dy, int tileSize) {
        boolean hero = a instanceof HeroActor;
        // 넉백·밀치기·문 잠김으로 발이 막힌 곳에 들어갔으면 가장 가까운 빈자리로 꺼낸다
        if (hero && !free(map, a.x, a.y, a, tileSize)) unstick(map, a, tileSize);
        float dist = Math.max(Math.abs(dx), Math.abs(dy));
        int steps = Math.max(1, (int) Math.ceil(dist / 4f));
        float sx = dx / steps, sy = dy / steps;
        boolean blocked = false;
        for (int i = 0; i < steps; i++) {
            if (sx != 0) {
                if (free(map, a.x + sx, a.y, a, tileSize)) a.x += sx;
                else if (hero && sy == 0 && nudge(map, a, sx, 0, tileSize)) a.x += sx;
                else blocked = true;
            }
            if (sy != 0) {
                if (free(map, a.x, a.y + sy, a, tileSize)) a.y += sy;
                else if (hero && sx == 0 && nudge(map, a, 0, sy, tileSize)) a.y += sy;
                else blocked = true;
            }
        }
        return blocked;
    }

    /**
     * 한 방향으로만 걷다가 모서리 끝에 걸렸으면, 옆으로 몇 픽셀 비켜서 지나갈 수 있는지 본다.
     * 비킬 수 있으면 옆으로 옮겨 두고 true (앞으로 가는 건 부른 쪽이 한다).
     */
    private static boolean nudge(WorldMap map, Actor a, float sx, float sy, int tileSize) {
        for (int o = 2; o <= NUDGE; o += 2) {
            for (int sign = -1; sign <= 1; sign += 2) {
                float ox = sx == 0 ? sign * o : 0, oy = sy == 0 ? sign * o : 0;
                if (free(map, a.x + ox, a.y + oy, a, tileSize) && free(map, a.x + ox + sx, a.y + oy + sy, a, tileSize)) {
                    a.x += ox;
                    a.y += oy;
                    return true;
                }
            }
        }
        return false;
    }

    /** 막힌 자리 둘레를 가까운 곳부터 돌며 설 수 있는 자리를 찾아 옮긴다. 못 찾으면 그대로 둔다. */
    public static boolean unstick(WorldMap map, Actor a, int tileSize) {
        for (int d = 2; d <= UNSTICK; d += 2) {
            for (int k = 0; k < 16; k++) {
                double angle = k * Math.PI / 8;
                float x = a.x + d * (float) Math.cos(angle), y = a.y + d * (float) Math.sin(angle);
                if (free(map, x, y, a, tileSize)) {
                    a.x = x;
                    a.y = y;
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean free(WorldMap map, float x, float y, Actor a, int tileSize) {
        return map.free(x, y, a.hw, a.hh);
    }
}
