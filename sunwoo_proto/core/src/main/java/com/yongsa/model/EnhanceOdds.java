package com.yongsa.model;

/** 한 번 시도 기준 네 결과의 확률(%). 합은 항상 100 (NFR-08). */
public record EnhanceOdds(double success, double keep, double down, double destroy) {
    public double total() {
        return success + keep + down + destroy;
    }

    public boolean risky() {
        return destroy > 0;
    }
}
