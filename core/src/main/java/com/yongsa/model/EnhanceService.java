package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.Balance.Band;
import com.yongsa.config.Balance.WeaponDef;

import java.util.Random;

/**
 * 강화 한 번을 판정한다 (FR-대장간-05~10, 명세서 5.2).
 * 성공률 = 95 − 7 × 단계. 실패분은 단계 구간마다 파괴·하락·유지 비율로 나눈다.
 */
public class EnhanceService {

    public enum Outcome { SUCCESS, KEEP, DOWN, DESTROY }

    /** 한 번 시도 기준 확률(%), 네 값의 합은 100. */
    public static final class Odds {
        public final double success, keep, down, destroy;

        Odds(double success, double keep, double down, double destroy) {
            this.success = success;
            this.keep = keep;
            this.down = down;
            this.destroy = destroy;
        }

        public double total() {
            return success + keep + down + destroy;
        }
    }

    public static final class Result {
        public final Outcome outcome;
        public final int levelBefore, levelAfter;
        public final boolean awakened;
        public final int refund;
        public final OreType refundOre;

        Result(Outcome outcome, int before, int after, boolean awakened, int refund, OreType refundOre) {
            this.outcome = outcome;
            this.levelBefore = before;
            this.levelAfter = after;
            this.awakened = awakened;
            this.refund = refund;
            this.refundOre = refundOre;
        }
    }

    private final Balance b;
    private final Rules rules;

    public EnhanceService(Balance b) {
        this.b = b;
        this.rules = new Rules(b);
    }

    private Band band(int level) {
        for (Band band : b.enhance.bands) {
            if (level >= band.fromLevel && level <= band.toLevel) return band;
        }
        throw new IllegalArgumentException("강화 구간 없음: +" + level);
    }

    public Odds odds(int level) {
        Band band = band(level);
        double success = b.enhance.successBasePercent - b.enhance.successStepPercent * level;
        double fail = 100 - success;
        int shares = band.destroyShare + band.downShare + band.keepShare;
        double destroy = fail * band.destroyShare / shares;
        double down = fail * band.downShare / shares;
        double keep = fail - destroy - down;
        return new Odds(success, keep, down, destroy);
    }

    public int cost(int level) {
        return band(level).cost;
    }

    public boolean isMax(Weapon w) {
        return w.level >= b.enhance.maxLevel;
    }

    public boolean canDestroy(Weapon w) {
        return !isMax(w) && odds(w.level).destroy > 0;
    }

    public OreType oreOf(Weapon w) {
        WeaponDef d = b.weapon(w.defId);
        return d.ore == null ? null : OreType.valueOf(d.ore);
    }

    public int destroyRefund(Weapon w) {
        return (int) Math.floor(b.weapon(w.defId).craftCost * b.enhance.destroyRefundRatio);
    }

    /** 강화할 수 없는 이유. 할 수 있으면 null. */
    public String blockReason(GameState s, Weapon w) {
        if (w == null) return "장착한 무기가 없습니다";
        WeaponDef d = b.weapon(w.defId);
        if (!d.enhanceable) return d.name + "은 강화할 수 없습니다";
        if (isMax(w)) return "이미 최대 단계입니다";
        if (s.ore(oreOf(w)) < cost(w.level)) return oreOf(w).displayName + "이 모자랍니다";
        return null;
    }

    public Result enhance(GameState s, Weapon w, Random rnd) {
        String reason = blockReason(s, w);
        if (reason != null) throw new IllegalStateException(reason);
        OreType ore = oreOf(w);
        int before = w.level;
        s.addOre(ore, -cost(before));
        s.stats.enhanceTries++;

        Odds o = odds(before);
        double r = rnd.nextDouble() * 100.0;
        Result result;
        if (r < o.success) {
            w.level++;
            boolean awakenedNow = false;
            if (isMax(w) && !w.awakened) {
                w.awakened = true;
                awakenedNow = true;
            }
            s.stats.enhanceSuccess++;
            result = new Result(Outcome.SUCCESS, before, w.level, awakenedNow, 0, null);
        } else if (r < o.success + o.keep) {
            result = new Result(Outcome.KEEP, before, before, false, 0, null);
        } else if (r < o.success + o.keep + o.down) {
            w.level = Math.max(0, w.level - 1);
            result = new Result(Outcome.DOWN, before, w.level, false, 0, null);
        } else {
            int refund = destroyRefund(w);
            s.addOre(ore, refund);
            s.weapons.remove(w);
            Weapon holy = holySword(s);
            s.equippedUid = holy == null ? 0 : holy.uid;
            s.stats.destroyed++;
            result = new Result(Outcome.DESTROY, before, -1, false, refund, ore);
        }
        log(s, w, result);
        return result;
    }

    private Weapon holySword(GameState s) {
        for (Weapon x : s.weapons) if (rules.isHoly(x)) return x;
        return null;
    }

    private void log(GameState s, Weapon w, Result r) {
        String name = b.weapon(w.defId).name;
        String line;
        switch (r.outcome) {
            case SUCCESS:
                line = name + " +" + r.levelBefore + " → +" + r.levelAfter + " 성공" + (r.awakened ? " (각성!)" : "");
                break;
            case KEEP:
                line = name + " +" + r.levelBefore + " 유지";
                break;
            case DOWN:
                line = name + " +" + r.levelBefore + " → +" + r.levelAfter + " 하락";
                break;
            default:
                line = name + " +" + r.levelBefore + " 파괴 (" + r.refundOre.displayName + " " + r.refund + "개 돌려받음)";
        }
        s.enhanceLog.add(0, line);
        while (s.enhanceLog.size() > 5) s.enhanceLog.remove(s.enhanceLog.size() - 1);
    }
}
