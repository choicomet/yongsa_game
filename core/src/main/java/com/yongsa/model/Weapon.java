package com.yongsa.model;

import com.yongsa.config.WeaponDef;

/** 가진 무기 한 자루. 같은 종류를 여러 자루 가질 수 있어서 uid로 구분한다. */
public class Weapon {

    private final int uid;
    private final WeaponDef def;
    private int level;
    private boolean awakened;
    /** 이 무기 강화에 지금까지 쓴 광물 (분해 보상 계산용). */
    private int enhanceSpent;

    public Weapon(int uid, WeaponDef def) {
        this(uid, def, 0, false, 0);
    }

    public Weapon(int uid, WeaponDef def, int level, boolean awakened, int enhanceSpent) {
        this.uid = uid;
        this.def = def;
        this.level = level;
        this.awakened = awakened;
        this.enhanceSpent = enhanceSpent;
    }

    public int uid() { return uid; }
    public WeaponDef def() { return def; }
    public int level() { return level; }
    public boolean awakened() { return awakened; }
    public int enhanceSpent() { return enhanceSpent; }
    public String ore() { return def.ore; }

    public String name() {
        return awakened && def.awakenedName != null ? def.awakenedName : def.name;
    }

    /** "+3 흑철검", 각성하면 "흑철 분쇄검 (+10)". */
    public String displayName() {
        if (awakened) return name() + " (+" + level + ")";
        return (level > 0 ? "+" + level + " " : "") + def.name;
    }

    public String icon() {
        return awakened && def.awakenedIcon != null ? def.awakenedIcon : def.icon;
    }

    /** 각성했다면 그 능력, 아니면 null. */
    public AwakenAbility ability() {
        return awakened && def.ability != null ? AwakenAbility.valueOf(def.ability) : null;
    }

    /** 강화 단계를 반영한 기본 공격력 (흡수 보너스 제외). */
    public int baseAttack() {
        return def.attack + level * def.attackPerLevel;
    }

    void setLevel(int level) { this.level = level; }
    void awaken() { this.awakened = true; }
    void addEnhanceSpent(int n) { this.enhanceSpent += n; }
}
