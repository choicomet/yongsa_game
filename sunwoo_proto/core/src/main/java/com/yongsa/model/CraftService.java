package com.yongsa.model;

import com.yongsa.config.WeaponDef;

/** 대장간 제작: 그 던전의 광물로만 만든다 (FR-대장간-02). */
public class CraftService {

    public boolean canCraft(WeaponDef def, Inventory inv) {
        return def.isSword() && inv.hasOre(def.ore, def.craftCost);
    }

    public Weapon craft(WeaponDef def, Inventory inv) {
        if (!canCraft(def, inv)) return null;
        inv.spendOre(def.ore, def.craftCost);
        return inv.add(def);
    }
}
