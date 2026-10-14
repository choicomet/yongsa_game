package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.Balance.WeaponDef;

/** 상태에서 바로 계산되는 값들 (공격력, 최대 체력, 능력 보유 여부). */
public final class Rules {
    public static final String HOLY_SEALED = "holy_sealed";
    public static final String HOLY_AWAKENED = "holy_awakened";

    private final Balance b;

    public Rules(Balance b) {
        this.b = b;
    }

    public Balance balance() {
        return b;
    }

    /** 공격력 = 기본 + 단계 × 단계당 (각성 무기 흡수는 2026-10-14 에 없앴다). */
    public int attack(GameState s, Weapon w) {
        if (w == null) return 0;
        WeaponDef d = b.weapon(w.defId);
        return d.baseAttack + w.level * d.perLevel;
    }

    public int attack(GameState s) {
        return attack(s, s.equipped());
    }

    /** 최대 체력은 흡수한 보스 수로만 정해진다 (100 → 250 → 450 → 700). */
    public int maxHp(GameState s) {
        int n = s.bossesDefeated.size();
        if (n <= 0) return b.hero.baseMaxHp;
        int[] t = b.hero.maxHpAfterBosses;
        return t[Math.min(n, t.length) - 1];
    }

    /** 각성 능력은 각성한 무기를 들고 있을 때만 쓴다. */
    public boolean hasAbility(GameState s, String weaponDefId) {
        Weapon w = s.equipped();
        return w != null && w.awakened && w.defId.equals(weaponDefId);
    }

    /** 들고 있는 검의 스킬 정의 (없는 검이면 null). 열렸는지는 skillUnlocked 로 본다. */
    public Balance.SkillDef skill(GameState s) {
        Weapon w = s.equipped();
        return w == null ? null : b.weapon(w.defId).skill;
    }

    /** 검 스킬은 강화 skillUnlockLevel 단계부터 (각성했거나 성검이면 바로) 쓸 수 있다. */
    public boolean skillUnlocked(GameState s) {
        Weapon w = s.equipped();
        if (w == null || skill(s) == null) return false;
        return isHoly(w) || w.awakened || w.level >= b.enhance.skillUnlockLevel;
    }

    /** 각성한 검이면 스킬도 강해진다. */
    public boolean skillAwakened(GameState s) {
        Weapon w = s.equipped();
        return w != null && w.awakened;
    }

    public String weaponName(Weapon w) {
        WeaponDef d = b.weapon(w.defId);
        String name = w.awakened && d.awakenedName != null ? d.awakenedName : d.name;
        return d.enhanceable ? name + " +" + w.level : name;
    }

    public boolean isHoly(Weapon w) {
        return w != null && b.weapon(w.defId).holy;
    }
}
