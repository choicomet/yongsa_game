package com.yongsa.model;

public enum EnhanceOutcome {
    SUCCESS, KEEP, DOWN, DESTROY,
    /** 아래는 시도하지 않은 경우 */
    MAX_LEVEL, NOT_ENOUGH_ORE, NOT_ENHANCEABLE
}
