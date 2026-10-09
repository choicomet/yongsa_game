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

    /** 공격력 = (기본 + 단계 × 단계당) × (1 + 흡수한 각성 무기 수 × 보너스). 명세서 5.4. */
    public int attack(GameState s, Weapon w) {
        if (w == null) return 0;
        WeaponDef d = b.weapon(w.defId);
        float raw = d.baseAttack + w.level * d.perLevel;
        return Math.round(raw * (1f + s.imprints.size() * b.absorb.attackBonusPerAwakenedWeapon));
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

    /** 각성 능력은 각성한 무기를 들고 있거나, 그 힘을 용사가 흡수했으면 쓸 수 있다. */
    public boolean hasAbility(GameState s, String weaponDefId) {
        if (s.imprints.contains(weaponDefId)) return true;
        Weapon w = s.equipped();
        return w != null && w.awakened && w.defId.equals(weaponDefId);
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
