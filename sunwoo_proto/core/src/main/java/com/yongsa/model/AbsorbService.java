package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.DungeonDef;

/**
 * 힘 흡수를 한곳에서 처리한다 (구현안 1장). 흡수 장소·시점(명세서 7.1)이 정해지면 여기만 바꾼다.
 * 지금은 "바로 흡수": 보스는 잡는 순간, 각성 무기는 대장간 창의 흡수 버튼.
 */
public class AbsorbService {

    /** 보스 처치로 흡수한 결과 (결과창에 그대로 보여준다). */
    public record BossAbsorb(DungeonDef dungeon, int hpGain, int oreReward, DungeonDef opened, boolean firstClear) {
    }

    private final Balance balance;

    public AbsorbService(Balance balance) {
        this.balance = balance;
    }

    /** 보스를 처음 잡았을 때만 흡수가 일어난다. 이미 잡은 보스면 null. */
    public BossAbsorb absorbBoss(DungeonDef d, Hero hero, Progress progress, Inventory inv) {
        if (progress.defeated(d.id)) return null;
        progress.markDefeated(d.id);
        hero.raiseMaxHp(d.maxHpGain);
        inv.addOre(d.ore, d.bossOreReward);
        DungeonDef opened = null;
        for (DungeonDef other : balance.dungeons) if (d.id.equals(other.requires)) opened = other;
        return new BossAbsorb(d, d.maxHpGain, d.bossOreReward, opened, true);
    }

    public boolean canAbsorbWeapon(Weapon w) {
        return w != null && w.awakened() && w.ability() != null;
    }

    /** 각성 무기를 흡수: 무기는 사라지고 능력과 공격력 보너스가 용사에게 남는다. */
    public boolean absorbWeapon(Weapon w, Inventory inv, Hero hero) {
        if (!canAbsorbWeapon(w)) return false;
        hero.absorb(w.ability());
        inv.remove(w);
        return true;
    }

    public boolean canUnseal(Progress p) {
        return p.allOrbs(balance) && !p.sealBroken();
    }

    /** 성검 봉인 해제: 봉인된 성검이 깨어난 성검이 되고 장착한다. */
    public void unseal(Progress p, Inventory inv) {
        if (p.sealBroken()) return;
        p.breakSeal();
        Weapon sealed = inv.sword();
        if (sealed != null && sealed.def().starter) inv.remove(sealed);
        Weapon holy = inv.add(balance.holySword());
        inv.equip(holy);
    }
}
