package com.yongsa.model;

import com.yongsa.config.EnhanceDef;

/** 분해: 4개 + 강화에 쓴 광물의 절반 (2026-10-10 결정). 장착한 무기와 성검은 분해할 수 없다. */
public class DismantleService {

    private final EnhanceDef def;

    public DismantleService(EnhanceDef def) {
        this.def = def;
    }

    public boolean canDismantle(Weapon w, Inventory inv) {
        return w.def().isSword() && w != inv.equipped();
    }

    public int refund(Weapon w) {
        return def.dismantleBase + (int) Math.floor(w.enhanceSpent() * def.dismantleEnhanceRatio);
    }

    /** 돌려받은 광물 수. 분해할 수 없으면 -1. */
    public int dismantle(Weapon w, Inventory inv) {
        if (!canDismantle(w, inv)) return -1;
        int r = refund(w);
        inv.remove(w);
        inv.addOre(w.ore(), r);
        return r;
    }
}
