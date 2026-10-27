package com.yongsa.model;

/**
 * 강화 한 번의 결과.
 *
 * @param refund        파괴됐을 때 돌려받은 광물 수
 * @param awakened      이번 강화로 각성했는지
 * @param masteryGained 이번 강화로 얻은 강화 숙련도 경험치
 * @param masteryBefore 강화 전 숙련도 레벨
 * @param masteryAfter  강화 뒤 숙련도 레벨 (올랐으면 화면이 알려 준다)
 */
public record EnhanceResult(EnhanceOutcome outcome, String weaponName, int levelBefore, int levelAfter,
                            int oreSpent, int refund, boolean awakened,
                            int masteryGained, int masteryBefore, int masteryAfter) {

    public EnhanceResult(EnhanceOutcome outcome, String weaponName, int levelBefore, int levelAfter,
                         int oreSpent, int refund, boolean awakened) {
        this(outcome, weaponName, levelBefore, levelAfter, oreSpent, refund, awakened, 0, 0, 0);
    }

    public boolean attempted() {
        return outcome == EnhanceOutcome.SUCCESS || outcome == EnhanceOutcome.GREAT || outcome == EnhanceOutcome.KEEP
                || outcome == EnhanceOutcome.DOWN || outcome == EnhanceOutcome.DESTROY;
    }

    public boolean masteryLeveledUp() {
        return masteryAfter > masteryBefore;
    }
}
