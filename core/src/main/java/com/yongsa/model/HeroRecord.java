package com.yongsa.model;

/**
 * 엔딩 “THE HERO'S RECORD”의 직업 등급 (견습 → 숙련 → 장인 → 전설). 기준은 화면에 보이지 않는다.
 * 광부는 캔 광물, 대장장이는 강화 성공, 용사는 쓰러진 횟수(적을수록 높다)로 정한다.
 */
public final class HeroRecord {
    public static final String[] GRADES = {"견습", "숙련", "장인", "전설"};
    public static final int[] MINER = {60, 120, 200};
    public static final int[] SMITH = {10, 20, 40};
    /** 용사: 쓰러진 횟수가 이 값 이하이면 숙련·장인·전설. */
    public static final int[] HERO = {15, 10, 5};

    private HeroRecord() {
    }

    public static int minerGrade(Stats s) {
        return up(s.oreMined, MINER);
    }

    public static int smithGrade(Stats s) {
        return up(s.enhanceSuccess, SMITH);
    }

    public static int heroGrade(Stats s) {
        int g = 0;
        for (int i = 0; i < HERO.length; i++) if (s.deaths <= HERO[i]) g = i + 1;
        return g;
    }

    /** 다음 등급까지 얼마나 왔나 (0~1, 전설이면 1). */
    public static float minerProgress(Stats s) {
        return progress(s.oreMined, MINER);
    }

    public static float smithProgress(Stats s) {
        return progress(s.enhanceSuccess, SMITH);
    }

    public static float heroProgress(Stats s) {
        int g = heroGrade(s);
        if (g >= GRADES.length - 1) return 1f;
        // 한 단계 위 기준까지 남은 쓰러짐 수를 거꾸로 센다
        int next = HERO[g], from = g == 0 ? Math.max(s.deaths, next + 1) * 2 : HERO[g - 1];
        return Math.max(0f, Math.min(1f, (float) (from - s.deaths) / Math.max(1, from - next)));
    }

    private static int up(int v, int[] t) {
        int g = 0;
        for (int i = 0; i < t.length; i++) if (v >= t[i]) g = i + 1;
        return g;
    }

    private static float progress(int v, int[] t) {
        int g = up(v, t);
        if (g >= t.length) return 1f;
        int from = g == 0 ? 0 : t[g - 1], to = t[g];
        return Math.max(0f, Math.min(1f, (float) (v - from) / (to - from)));
    }
}
