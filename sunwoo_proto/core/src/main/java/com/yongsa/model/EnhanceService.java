package com.yongsa.model;

import com.yongsa.config.EnhanceDef;
import com.yongsa.config.EnhanceRuleDef;

import java.util.Random;

/**
 * 강화 한 번 판정: 비용 확인 → 광물 소모 → 성공/유지/하락/파괴 → 결과 반환 (구현안 6장).
 * libGDX에 의존하지 않고, Random을 밖에서 받아 테스트에서 결과를 고정한다 (NFR-05, 07).
 */
public class EnhanceService {

    private final EnhanceTable table;
    private final EnhanceDef def;
    private final Random random;

    public EnhanceService(EnhanceTable table, EnhanceDef def, Random random) {
        this.table = table;
        this.def = def;
        this.random = random;
    }

    /** 성검 공격력·흡혈 계산 (없으면 무기 자체 값만). */
    private SwordPower power;

    public void setPower(SwordPower power) {
        this.power = power;
    }

    public EnhanceTable table() {
        return table;
    }

    public int destroyRefund(Weapon w) {
        return (int) Math.floor(w.def().craftCost * def.destroyRefundRatio);
    }

    public EnhancePreview preview(Weapon w, Inventory inv, Hero hero) {
        int atkNow = power != null ? power.attack(w, inv, hero) : DamageCalc.attack(w, hero, def);
        float lsNow = power != null ? power.lifesteal(w, inv, hero) : lifesteal(w.level());
        boolean maxed = w.level() >= table.maxLevel();
        if (!w.def().enhanceable || maxed) {
            return new EnhancePreview(w.def().enhanceable, maxed, new EnhanceOdds(0, 0, 0, 0), 0,
                    inv.ore(w.ore()), atkNow, atkNow, 0, 0, lsNow, lsNow);
        }
        int atkAfter = DamageCalc.attack(w.baseAttack() + w.def().attackPerLevel, hero, def);
        return new EnhancePreview(true, false, table.odds(w.level()), table.cost(w.level()), inv.ore(w.ore()),
                atkNow, atkAfter, destroyRefund(w), table.maxLevel() - w.level(), lsNow, lifesteal(w.level() + 1));
    }

    private float lifesteal(int level) {
        float[] t = def.lifesteal;
        if (t.length == 0) return 0;
        return t[Math.max(0, Math.min(t.length - 1, level))];
    }

    public EnhanceResult enhance(Weapon w, Inventory inv) {
        int before = w.level();
        if (!w.def().enhanceable) return none(EnhanceOutcome.NOT_ENHANCEABLE, w);
        if (before >= table.maxLevel()) return none(EnhanceOutcome.MAX_LEVEL, w);
        int cost = table.cost(before);
        if (!inv.spendOre(w.ore(), cost)) return none(EnhanceOutcome.NOT_ENOUGH_ORE, w);
        w.addEnhanceSpent(cost);

        String name = w.displayName();
        EnhanceOutcome outcome = roll(table.rule(before));
        switch (outcome) {
            case SUCCESS -> w.setLevel(before + 1);
            case DOWN -> w.setLevel(Math.max(0, before - 1));
            case DESTROY -> {
                int refund = destroyRefund(w);
                inv.remove(w);
                inv.addOre(w.ore(), refund);
                return new EnhanceResult(outcome, name, before, before, cost, refund, false);
            }
            default -> { }
        }
        boolean awakenedNow = false;
        if (w.level() == table.maxLevel() && !w.awakened()) {
            w.awaken();
            awakenedNow = true;
        }
        return new EnhanceResult(outcome, name, before, w.level(), cost, 0, awakenedNow);
    }

    private static EnhanceResult none(EnhanceOutcome o, Weapon w) {
        return new EnhanceResult(o, w.displayName(), w.level(), w.level(), 0, 0, false);
    }

    /** 1차: 성공 여부. 2차: 실패를 유지·하락·파괴 비중으로 나눈다. */
    private EnhanceOutcome roll(EnhanceRuleDef r) {
        if (random.nextInt(100) < r.success) return EnhanceOutcome.SUCCESS;
        int total = r.keep + r.down + r.destroy;
        int x = random.nextInt(total);
        if (x < r.keep) return EnhanceOutcome.KEEP;
        if (x < r.keep + r.down) return EnhanceOutcome.DOWN;
        return EnhanceOutcome.DESTROY;
    }
}
