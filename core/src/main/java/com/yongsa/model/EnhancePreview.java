package com.yongsa.model;

/** 강화 버튼을 누르기 전에 보여줄 정보 (FR-대장간-10, 12, 18). 화면은 이 값을 그대로 쓴다. */
public record EnhancePreview(boolean enhanceable, boolean maxed, EnhanceOdds odds, int cost, int have,
                             int attackNow, int attackAfter, int refundIfDestroyed, int levelsToAwaken,
                             float lifestealNow, float lifestealAfter) {

    public boolean enoughOre() {
        return have >= cost;
    }

    public boolean canEnhance() {
        return enhanceable && !maxed && enoughOre();
    }
}
