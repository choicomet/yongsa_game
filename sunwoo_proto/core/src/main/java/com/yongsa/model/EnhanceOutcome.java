package com.yongsa.model;

public enum EnhanceOutcome {
    /** 성공 +1, 대성공 +2 (마지막 단계에서는 +1), 유지, 하락 -1, 파괴. */
    SUCCESS, GREAT, KEEP, DOWN, DESTROY,
    /** 아래는 시도하지 않은 경우 */
    MAX_LEVEL, NOT_ENOUGH_ORE, NOT_ENHANCEABLE
}
