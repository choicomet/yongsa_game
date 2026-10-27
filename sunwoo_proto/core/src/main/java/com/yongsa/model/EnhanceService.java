package com.yongsa.model;

import com.yongsa.config.EnhanceDef;

import java.util.Random;

/**
 * 강화 한 번 판정: 비용 확인 → 광물 소모 → 성공/대성공/유지/하락/파괴 → 숙련도 경험치 → 결과 반환 (구현안 6장).
 * 2026-10-27: 대성공(+2, 마지막 단계는 +1)과 강화 숙련도({@link EnhanceMastery})를 더했다.
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

    /** 강화 숙련도 (모든 검 공유, Progress 에 저장). 없으면 숙련도 0 으로 계산하고 쌓지 않는다. */
    private EnhanceMastery mastery;

    public void setMastery(EnhanceMastery mastery) {
        this.mastery = mastery;
    }

    public EnhanceMastery mastery() {
        return mastery;
    }

    private int masteryLevel() {
        return mastery == null ? 0 : mastery.level();
    }

    /** 지금 숙련도의 확률. */
    public EnhanceOdds odds(int level) {
        return table.odds(level, masteryLevel());
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
            return new EnhancePreview(w.def().enhanceable, maxed, new EnhanceOdds(0, 0, 0, 0, 0), 0,
                    inv.ore(w.ore()), atkNow, atkNow, 0, 0, lsNow, lsNow);
        }
        int atkAfter = DamageCalc.attack(w.baseAttack() + w.def().attackPerLevel, hero, def);
        // 개발자 도구 "강화 성공 100%"가 켜져 있으면 창에도 100%로 보인다
        EnhanceOdds odds = alwaysSucceed ? new EnhanceOdds(100, 0, 0, 0, 0) : odds(w.level());
        return new EnhancePreview(true, false, odds, table.cost(w.level()), inv.ore(w.ore()),
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
        EnhanceOutcome outcome = roll(odds(before));
        int mBefore = masteryLevel();
        int gained = 0;
        if (mastery != null) {
            gained = mastery.expFor(outcome, before);
            mastery.add(gained);
        }
        int mAfter = masteryLevel();
        switch (outcome) {
            case SUCCESS -> w.setLevel(before + 1);
            // 대성공 +2, 다만 최대 단계는 넘지 않는다 (+9 에서는 +1)
            case GREAT -> w.setLevel(Math.min(table.maxLevel(), before + 2));
            case DOWN -> w.setLevel(Math.max(0, before - 1));
            case DESTROY -> {
                int refund = destroyRefund(w);
                inv.remove(w);
                inv.addOre(w.ore(), refund);
                return new EnhanceResult(outcome, name, before, before, cost, refund, false, gained, mBefore, mAfter);
            }
            default -> { }
        }
        boolean awakenedNow = false;
        if (w.level() == table.maxLevel() && !w.awakened()) {
            w.awaken();
            awakenedNow = true;
        }
        return new EnhanceResult(outcome, name, before, w.level(), cost, 0, awakenedNow, gained, mBefore, mAfter);
    }

    private static EnhanceResult none(EnhanceOutcome o, Weapon w) {
        return new EnhanceResult(o, w.displayName(), w.level(), w.level(), 0, 0, false);
    }

    /** 개발자 도구: 강화가 항상 성공 (확률 100%). 저장하지 않는다. */
    private boolean alwaysSucceed;

    public boolean alwaysSucceed() {
        return alwaysSucceed;
    }

    public void setAlwaysSucceed(boolean on) {
        alwaysSucceed = on;
    }

    /** 한 번 굴려서 다섯 결과 중 하나. 확률은 숙련도가 반영된 값. */
    private EnhanceOutcome roll(EnhanceOdds o) {
        if (alwaysSucceed) return EnhanceOutcome.SUCCESS;
        double x = random.nextDouble() * 100.0;
        if (x < o.success()) return EnhanceOutcome.SUCCESS;
        x -= o.success();
        if (x < o.great()) return EnhanceOutcome.GREAT;
        x -= o.great();
        if (x < o.keep()) return EnhanceOutcome.KEEP;
        x -= o.keep();
        if (x < o.down()) return EnhanceOutcome.DOWN;
        return EnhanceOutcome.DESTROY;
    }
}
