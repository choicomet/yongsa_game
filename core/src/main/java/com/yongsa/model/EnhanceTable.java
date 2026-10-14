package com.yongsa.model;

import com.yongsa.config.EnhanceRuleDef;

/** 단계별 강화 규칙. index = 지금 단계(+0 ~ +9). */
public final class EnhanceTable {

    private final EnhanceRuleDef[] rules;

    public EnhanceTable(EnhanceRuleDef[] rules) {
        for (int i = 0; i < rules.length; i++) {
            EnhanceRuleDef r = rules[i];
            if (r.success < 0 || r.success > 100) throw new IllegalArgumentException("+" + i + " 성공률 오류");
            if (r.success < 100 && r.keep + r.down + r.destroy <= 0) throw new IllegalArgumentException("+" + i + " 실패 비중 없음");
            if (r.keep < 0 || r.down < 0 || r.destroy < 0 || r.cost < 0) throw new IllegalArgumentException("+" + i + " 음수 값");
        }
        this.rules = rules.clone();
    }

    public int maxLevel() {
        return rules.length;
    }

    public EnhanceRuleDef rule(int level) {
        if (level < 0 || level >= rules.length) throw new IllegalArgumentException("강화할 수 없는 단계: +" + level);
        return rules[level];
    }

    public int cost(int level) {
        return rule(level).cost;
    }

    /** 강화창에 보여줄 한 번 시도 기준 확률. */
    public EnhanceOdds odds(int level) {
        EnhanceRuleDef r = rule(level);
        double fail = 100 - r.success;
        double weights = r.keep + r.down + r.destroy;
        if (weights == 0) return new EnhanceOdds(r.success, 0, 0, 0);
        return new EnhanceOdds(r.success, fail * r.keep / weights, fail * r.down / weights, fail * r.destroy / weights);
    }
}
