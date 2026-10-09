package com.yongsa.model;

import com.yongsa.config.Balance;

/** 공통 피해 계산. 전투·UI 모두 이 함수만 호출한다. */
public final class DamageCalc {
    private DamageCalc() {}

    /** 기본 공격력 × (1 + 계수 × 강화 단계) 를 소수 첫째 자리에서 반올림(0.5 이상 올림)한 정수. */
    public static int damage(int baseAttack, int level, double perLevel) {
        double raw = baseAttack * (1.0 + perLevel * level);
        // 부동소수 오차로 x.5 가 x.4999.. 로 떨어지는 것을 막는다.
        return (int) Math.floor(raw + 0.5 + 1e-9);
    }

    public static int weaponDamage(Balance b, String weaponType, int level) {
        Balance.WeaponDef def = b.weapon(weaponType);
        return damage(def.baseAttack, level, b.damagePerLevel);
    }

    public static int weaponDamage(Balance b, WeaponState w) {
        return weaponDamage(b, w.type, w.level);
    }

    public static int shockwaveDamage(Balance b, WeaponState w) {
        return weaponDamage(b, w) * b.shockwave.damageMultiplier;
    }
}
