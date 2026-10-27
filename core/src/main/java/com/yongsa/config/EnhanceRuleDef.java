package com.yongsa.config;

/**
 * 강화 표 한 줄: 한 번 시도 기준 확률(%). 다섯 값의 합은 100 (2026-10-27 김선우 결정).
 * 성공 +1 · 대성공 +2 (마지막 단계에서는 +1) · 유지 · 하락 -1 · 파괴. 강화 숙련도 0 기준이다.
 */
public class EnhanceRuleDef {
    public double success;
    public double great;
    public double keep;
    public double down;
    public double destroy;
    public int cost;
}
