package com.yongsa.model;

import com.yongsa.config.MasteryDef;

/**
 * 강화 숙련도 (2026-10-27 김선우 결정): 용사 한 명의 대장장이 실력이라 모든 검이 함께 쓴다.
 * 검이 파괴되거나 분해·흡수되어도, 새 검을 만들어도 그대로 남고, 새 게임에서만 0 이 된다 (Progress 에 저장).
 * 성공·대성공·하락·파괴 때마다 경험치가 쌓이고 (0강에서도), 레벨이 오를수록 필요한 경험치가 늘어난다.
 */
public final class EnhanceMastery {

    private final MasteryDef def;
    private int level, exp;

    public EnhanceMastery(MasteryDef def) {
        this.def = def;
    }

    public int level() { return level; }
    public int exp() { return exp; }
    public int maxLevel() { return def.maxLevel; }
    public boolean maxed() { return level >= def.maxLevel; }
    public MasteryDef def() { return def; }

    /** lv 에서 다음 레벨까지 필요한 경험치 (최대 레벨이면 0). */
    public int need(int lv) {
        if (lv >= def.maxLevel) return 0;
        return Math.round(def.needBase + def.needLinear * lv + def.needSquare * lv * lv);
    }

    /** 지금 레벨에서 다음 레벨까지 필요한 경험치. */
    public int need() {
        return need(level);
    }

    /** 0 부터 lv 까지 쌓아야 하는 경험치 합. */
    public int totalTo(int lv) {
        int sum = 0;
        for (int l = 0; l < lv; l++) sum += need(l);
        return sum;
    }

    /** 이 결과로 얻는 경험치: 결과별 값 × 강화 전 단계 배율. */
    public int expFor(EnhanceOutcome o, int levelBefore) {
        int base = switch (o) {
            case SUCCESS -> def.expSuccess;
            case GREAT -> def.expGreat;
            case KEEP -> def.expKeep;
            case DOWN -> def.expDown;
            case DESTROY -> def.expDestroy;
            default -> 0;
        };
        float mul = def.expMultiplier.length == 0 ? 1f : def.expMultiplier[Math.max(0, Math.min(levelBefore, def.expMultiplier.length - 1))];
        return Math.round(base * mul);
    }

    /** 경험치를 더한다. 넘치면 레벨이 오른다. 올랐으면 true. */
    public boolean add(int amount) {
        if (maxed()) {
            exp = 0;
            return false;
        }
        int before = level;
        exp += Math.max(0, amount);
        while (!maxed() && exp >= need()) {
            exp -= need();
            level++;
        }
        if (maxed()) exp = 0;
        return level > before;
    }

    /** 저장에서 복원 (범위를 벗어난 값은 바로잡는다). */
    public void restore(int lv, int ex) {
        level = Math.max(0, Math.min(lv, def.maxLevel));
        exp = maxed() ? 0 : Math.max(0, Math.min(ex, need() - 1));
    }

    /** 개발자 도구용: 레벨을 바로 정한다. */
    public void set(int lv) {
        restore(lv, 0);
    }
}
