package com.yongsa.model;

import com.yongsa.config.EnhanceRuleDef;
import com.yongsa.config.MasteryDef;

/** 단계별 강화 규칙. index = 지금 단계(+0 ~ +9). 숙련도에 따라 하락·파괴가 줄어든 확률도 여기서 계산한다. */
public final class EnhanceTable {

    private final EnhanceRuleDef[] rules;
    private final MasteryDef mastery;

    public EnhanceTable(EnhanceRuleDef[] rules) {
        this(rules, new MasteryDef());
    }

    public EnhanceTable(EnhanceRuleDef[] rules, MasteryDef mastery) {
        for (int i = 0; i < rules.length; i++) {
            EnhanceRuleDef r = rules[i];
            if (r.success < 0 || r.great < 0 || r.keep < 0 || r.down < 0 || r.destroy < 0 || r.cost < 0)
                throw new IllegalArgumentException("+" + i + " 음수 값");
            double sum = r.success + r.great + r.keep + r.down + r.destroy;
            if (Math.abs(sum - 100) > 1e-6) throw new IllegalArgumentException("+" + i + " 확률 합이 100이 아님: " + sum);
        }
        this.rules = rules.clone();
        this.mastery = mastery == null ? new MasteryDef() : mastery;
    }

    public int maxLevel() {
        return rules.length;
    }

    public MasteryDef mastery() {
        return mastery;
    }

    public EnhanceRuleDef rule(int level) {
        if (level < 0 || level >= rules.length) throw new IllegalArgumentException("강화할 수 없는 단계: +" + level);
        return rules[level];
    }

    public int cost(int level) {
        return rule(level).cost;
    }

    /** 숙련도 0 기준 확률. */
    public EnhanceOdds odds(int level) {
        return odds(level, 0);
    }

    /**
     * 숙련도 masteryLevel 에서의 확률. 하락 × (1 − Lv/downZeroLevel), 파괴 × (1 − Lv/destroyZeroLevel),
     * 줄어든 만큼은 유지로 간다. greatBonusFromLevel 부터는 레벨마다 유지 몫 일부가 대성공으로 간다.
     */
    public EnhanceOdds odds(int level, int masteryLevel) {
        EnhanceRuleDef r = rule(level);
        int lv = Math.max(0, Math.min(masteryLevel, mastery.maxLevel));
        double down = r.down * Math.max(0, 1 - (double) lv / mastery.downZeroLevel);
        double destroy = r.destroy * Math.max(0, 1 - (double) lv / mastery.destroyZeroLevel);
        double keep = r.keep + (r.down - down) + (r.destroy - destroy);
        double bonus = Math.min(greatBonus(lv), keep);
        return new EnhanceOdds(r.success, r.great + bonus, keep - bonus, down, destroy);
    }

    /** 이 숙련도에서 대성공에 더하는 %p. */
    public double greatBonus(int masteryLevel) {
        if (masteryLevel < mastery.greatBonusFromLevel) return 0;
        return (masteryLevel - mastery.greatBonusFromLevel + 1) * mastery.greatBonusStep;
    }
}
