package com.yongsa.model;

import com.yongsa.config.EnhanceDef;

import java.util.Random;

/** 공격력 = (무기 기본 + 단계 × 단계당) × (1 + 흡수한 각성 무기 수 × 10%) (명세서 5.4). */
public final class DamageCalc {
    private DamageCalc() {
    }

    public static int attack(Weapon w, Hero hero, EnhanceDef def) {
        return attack(w.baseAttack(), hero, def);
    }

    public static int attack(int base, Hero hero, EnhanceDef def) {
        return Math.round(base * (1f + hero.absorbedCount() * def.absorbAttackBonus));
    }

    /** 기준값의 (1-v)~(1+v) 무작위, 최소 1 (FR-전투-09). */
    public static int roll(float base, float variance, Random random) {
        float k = 1f - variance + random.nextFloat() * variance * 2f;
        return Math.max(1, Math.round(base * k));
    }
}
