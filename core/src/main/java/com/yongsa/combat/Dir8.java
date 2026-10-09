package com.yongsa.combat;

/** 8방향. 0=오른쪽부터 반시계 방향으로 45도씩. */
public final class Dir8 {
    private Dir8() {}

    private static final float D = (float) Math.sqrt(0.5);
    public static final float[] DX = {1, D, 0, -D, -1, -D, 0, D};
    public static final float[] DY = {0, D, 1, D, 0, -D, -1, -D};

    /** (dx, dy)에 가장 가까운 8방향. 길이가 너무 짧으면 fallback 을 유지한다. */
    public static int fromVector(float dx, float dy, int fallback, float deadZone) {
        if (dx * dx + dy * dy < deadZone * deadZone) return fallback;
        double a = Math.atan2(dy, dx);
        int idx = (int) Math.round(a / (Math.PI / 4));
        return ((idx % 8) + 8) % 8;
    }

    /** 4방향 그림 이름. 대각선은 좌우를 우선한다. */
    public static String name4(int dir) {
        switch (dir) {
            case 2: return "up";
            case 6: return "down";
            case 3: case 4: case 5: return "left";
            default: return "right";
        }
    }

    public static boolean facesLeft(int dir) {
        return dir >= 3 && dir <= 5;
    }
}
