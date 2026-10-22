package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.WeaponDef;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 검의 공격력·흡혈을 한곳에서 계산한다.
 * <ul>
 *   <li>봉인된 검: 공격력 10, 흡혈 없음.</li>
 *   <li>만든 검: (기본 + 단계 × 단계당), 흡혈은 강화 단계 표(6강부터).</li>
 *   <li>성검: 150 + (검 종류마다 가장 센 검의 공격력 합) × 0.4.
 *       흡혈은 종류마다 가장 높은 흡혈의 절반을 더하되, 가장 높은 한 자루보다 낮아지지 않는다.
 *       흡수해서 사라진 검도 그 단계 그대로 센다.</li>
 * </ul>
 * 최종 공격력에는 흡수한 각성 무기 수만큼 보너스(×1.1, ×1.2 …)가 붙는다.
 */
public final class SwordPower {

    /** 성검이 나눠 받는 검 한 종류. */
    public record Share(WeaponDef def, int level, int attack, float lifesteal, boolean absorbed) {
    }

    private final Balance b;

    public SwordPower(Balance b) {
        this.b = b;
    }

    /** 흡수 보너스를 빼고 무기 자체의 공격력. */
    public int baseAttack(Weapon w, Inventory inv, Hero hero) {
        if (w.def().holy) return holyBase(inv, hero);
        return w.baseAttack();
    }

    /** 실제로 쓰는 공격력 (흡수 보너스 포함). */
    public int attack(Weapon w, Inventory inv, Hero hero) {
        return DamageCalc.attack(baseAttack(w, inv, hero), hero, b.enhance);
    }

    public int holyBase(Inventory inv, Hero hero) {
        int sum = 0;
        for (Share s : shares(inv, hero).values()) sum += s.attack();
        return b.holySword().attack + Math.round(sum * b.holy.shareRatio);
    }

    /** 흡혈: 적 최대 체력의 %. */
    public float lifesteal(Weapon w, Inventory inv, Hero hero) {
        if (w.def().holy) return holyLifesteal(inv, hero);
        if (!w.def().isSword()) return 0;
        return levelLifesteal(w.level());
    }

    public float levelLifesteal(int level) {
        float[] t = b.enhance.lifesteal;
        return t[Math.max(0, Math.min(t.length - 1, level))];
    }

    public float holyLifesteal(Inventory inv, Hero hero) {
        float sum = 0, best = 0;
        for (Share s : shares(inv, hero).values()) {
            sum += s.lifesteal();
            best = Math.max(best, s.lifesteal());
        }
        return Math.max(sum * b.holy.lifestealShare, best);
    }

    /** 검 종류 id → 그 종류에서 가장 센 한 자루 (가진 검 + 흡수한 검). */
    public Map<String, Share> shares(Inventory inv, Hero hero) {
        Map<String, Share> out = new LinkedHashMap<>();
        for (WeaponDef def : b.weapons) {
            if (!def.isSword()) continue;
            int best = -1;
            boolean absorbed = false;
            for (Weapon w : inv.weapons()) if (w.def() == def) best = Math.max(best, w.level());
            Integer abs = hero.absorbedSwords().get(def.id);
            if (abs != null && abs > best) {
                best = abs;
                absorbed = true;
            }
            if (best < 0) continue;
            out.put(def.id, new Share(def, best, def.attack + best * def.attackPerLevel, levelLifesteal(best), absorbed));
        }
        return out;
    }
}
