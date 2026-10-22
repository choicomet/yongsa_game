package com.yongsa.config;

/**
 * 강화 표 한 줄. success는 성공 확률(%), keep/down/destroy는 "실패했을 때" 나뉘는 비중.
 * 예: +6은 성공 53%, 실패 47%를 유지 40 : 하락 45 : 파괴 15로 나눈다 → 18.8 / 21.15 / 7.05%.
 */
public class EnhanceRuleDef {
    public int success;
    public int keep;
    public int down;
    public int destroy;
    public int cost;
}
