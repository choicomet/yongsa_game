package com.yongsa.model;

/**
 * 강화 한 번의 결과.
 *
 * @param refund   파괴됐을 때 돌려받은 광물 수
 * @param awakened 이번 강화로 각성했는지
 */
public record EnhanceResult(EnhanceOutcome outcome, String weaponName, int levelBefore, int levelAfter,
                            int oreSpent, int refund, boolean awakened) {

    public boolean attempted() {
        return outcome == EnhanceOutcome.SUCCESS || outcome == EnhanceOutcome.KEEP
                || outcome == EnhanceOutcome.DOWN || outcome == EnhanceOutcome.DESTROY;
    }
}
