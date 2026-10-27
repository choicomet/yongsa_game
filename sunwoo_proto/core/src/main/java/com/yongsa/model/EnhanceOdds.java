package com.yongsa.model;

/** 한 번 시도 기준 다섯 결과의 확률(%): 성공 +1 · 대성공 +2 · 유지 · 하락 -1 · 파괴. 합은 항상 100 (NFR-08). */
public record EnhanceOdds(double success, double great, double keep, double down, double destroy) {
    public double total() {
        return success + great + keep + down + destroy;
    }

    public boolean risky() {
        return destroy > 0;
    }
}
