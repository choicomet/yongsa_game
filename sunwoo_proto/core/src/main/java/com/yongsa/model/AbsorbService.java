package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.DungeonDef;

/**
 * 힘 흡수를 한곳에서 처리한다 (구현안 1장). 흡수 장소·시점(명세서 7.1)이 정해지면 여기만 바꾼다.
 * 지금은 "바로 흡수": 보스는 잡는 순간, 각성 무기는 대장간 창의 흡수 버튼.
 */
public class AbsorbService {

    /**
     * 보스 처치 결과 (결과창에 그대로 보여준다).
     * 첫 처치면 흡수(최대 체력)·광물·다음 던전·구슬, 다시 생긴 보스면 광물만.
     */
    /** oreReward: 보물상자에 든 광물 수 (상자를 열어야 들어온다). */
    public record BossAbsorb(DungeonDef dungeon, int hpGain, int oreReward, DungeonDef opened, boolean firstClear) {
    }

    private final Balance balance;

    public AbsorbService(Balance balance) {
        this.balance = balance;
    }

    /** 보스 처치. 처음 잡았을 때만 흡수가 일어나고, 다시 생긴 보스는 광물 4~5개만 준다. */
    public BossAbsorb absorbBoss(DungeonDef d, Hero hero, Progress progress, Inventory inv, java.util.Random random) {
        // 광물은 보물상자에 들어 있다 (2026-10-12 결정: 처치할 때마다 chestOreMin~chestOreMax). 여기서는 개수만 정한다
        int ore = d.chestOreMin + random.nextInt(Math.max(1, d.chestOreMax - d.chestOreMin + 1));
        if (progress.defeated(d.id)) return new BossAbsorb(d, 0, ore, null, false);
        progress.markDefeated(d.id);
        hero.raiseMaxHp(d.maxHpGain);
        DungeonDef opened = null;
        for (DungeonDef other : balance.dungeons) if (d.id.equals(other.requires)) opened = other;
        return new BossAbsorb(d, d.maxHpGain, ore, opened, true);
    }

    public boolean canAbsorbWeapon(Weapon w) {
        return w != null && w.awakened() && w.ability() != null;
    }

    /** 각성 무기를 흡수: 무기는 사라지고 능력과 공격력 보너스가 용사에게 남는다. */
    public boolean absorbWeapon(Weapon w, Inventory inv, Hero hero) {
        if (!canAbsorbWeapon(w)) return false;
        hero.absorb(w.ability(), w.def().id, w.level());
        inv.remove(w);
        return true;
    }

    public boolean canUnseal(Progress p) {
        return p.allOrbs(balance) && !p.sealBroken();
    }

    /** 성검 해방: 봉인된 검이 성검이 되고 장착한다. */
    public void unseal(Progress p, Inventory inv) {
        if (p.sealBroken()) return;
        p.breakSeal();
        Weapon sealed = inv.sword();
        if (sealed != null && sealed.def().starter) inv.remove(sealed);
        Weapon holy = inv.add(balance.holySword());
        inv.equip(holy);
    }
}
