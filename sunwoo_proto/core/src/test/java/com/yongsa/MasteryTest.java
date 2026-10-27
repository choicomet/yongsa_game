package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.config.WeaponDef;
import com.yongsa.model.EnhanceMastery;
import com.yongsa.model.EnhanceOdds;
import com.yongsa.model.EnhanceOutcome;
import com.yongsa.model.EnhanceResult;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.EnhanceTable;
import com.yongsa.model.GameSession;
import com.yongsa.model.Inventory;
import com.yongsa.model.Weapon;
import com.yongsa.save.SaveData;
import com.yongsa.save.SaveMapper;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 강화 숙련도·대성공·하락 (2026-10-27 김선우 결정). */
class MasteryTest {

    private final Balance balance = TestFiles.balance();
    private final EnhanceTable table = balance.enhanceTable();
    private final WeaponDef black = balance.weapon("blackiron");

    /** nextDouble 이 언제나 v 인 난수: 확률 표의 원하는 칸을 고른다. */
    private static Random fixed(double v) {
        return new Random() {
            @Override public double nextDouble() { return v; }
        };
    }

    private EnhanceService service(Random r, EnhanceMastery m) {
        EnhanceService s = new EnhanceService(table, balance.enhance, r);
        s.setMastery(m);
        return s;
    }

    private Inventory inv() {
        Inventory inv = new Inventory();
        inv.add(balance.sealedSword());
        inv.addOre("blackiron", 100000);
        inv.addOre("bloodmoon", 100000);
        return inv;
    }

    private Weapon sword(Inventory inv, WeaponDef d, int lv) {
        Weapon w = inv.add(d);
        inv.equip(w);
        TestFiles.setLevel(w, lv);
        return w;
    }

    @Test
    void 숙련도마다_확률_합은_100() {
        for (int m = 0; m <= 20; m++)
            for (int lv = 0; lv < 10; lv++) assertEquals(100.0, table.odds(lv, m).total(), 1e-9, "Lv" + m + " +" + lv);
    }

    @Test
    void 숙련도가_오르면_하락_파괴가_유지로_가고_성공은_그대로() {
        EnhanceOdds o0 = table.odds(8, 0), o10 = table.odds(8, 10);
        assertEquals(o0.success(), o10.success(), 1e-9);
        assertEquals(28 * (1 - 10 / 17.0), o10.down(), 1e-9);
        assertEquals(9 * (1 - 10 / 15.0), o10.destroy(), 1e-9);
        assertTrue(o10.keep() > o0.keep());
        assertTrue(table.odds(9, 14).destroy() > 0);
        assertEquals(0, table.odds(9, 15).destroy(), 1e-9, "Lv15 부터 파괴 없음");
        assertTrue(table.odds(9, 16).down() > 0);
        assertEquals(0, table.odds(9, 17).down(), 1e-9, "Lv17 부터 하락 없음");
        EnhanceOdds late = table.odds(9, 17);
        assertEquals(100, late.success() + late.great() + late.keep(), 1e-9, "유지·성공·대성공만");
        assertEquals(4, table.odds(6, 18).great(), 1e-9);
        assertEquals(6, table.odds(6, 20).great(), 1e-9);
    }

    @Test
    void 대성공은_두_단계_마지막에는_한_단계() {
        Inventory inv = inv();
        EnhanceService s = service(fixed(0.515), null); // 성공 50 다음 3%
        Weapon w = sword(inv, black, 6);
        EnhanceResult r = s.enhance(w, inv);
        assertEquals(EnhanceOutcome.GREAT, r.outcome());
        assertEquals(8, w.level());
        Weapon w9 = sword(inv, black, 9);
        EnhanceResult r9 = service(fixed(0.30), null).enhance(w9, inv); // 성공 29 다음
        assertEquals(EnhanceOutcome.GREAT, r9.outcome());
        assertEquals(10, w9.level());
        assertTrue(w9.awakened());
    }

    @Test
    void 하락은_한_단계() {
        Inventory inv = inv();
        Weapon w = sword(inv, black, 5);
        EnhanceResult r = service(fixed(0.85), null).enhance(w, inv); // 57+3+20 = 80 뒤
        assertEquals(EnhanceOutcome.DOWN, r.outcome());
        assertEquals(4, w.level());
    }

    @Test
    void 영강에서도_실패에서도_숙련도가_쌓인다() {
        EnhanceMastery m = new EnhanceMastery(balance.enhance.mastery);
        assertEquals(20, m.expFor(EnhanceOutcome.SUCCESS, 0));
        assertEquals(30, m.expFor(EnhanceOutcome.GREAT, 0));
        assertEquals(0, m.expFor(EnhanceOutcome.KEEP, 0));
        assertEquals(45, m.expFor(EnhanceOutcome.DOWN, 4));
        assertEquals(120, m.expFor(EnhanceOutcome.DESTROY, 8));

        Inventory inv = inv();
        EnhanceResult r = service(fixed(0.0), m).enhance(sword(inv, black, 0), inv);
        assertEquals(20, r.masteryGained());
        assertEquals(1, m.level());
        assertEquals(8, m.exp());
        assertTrue(r.masteryLeveledUp());

        EnhanceMastery m2 = new EnhanceMastery(balance.enhance.mastery);
        EnhanceResult d = service(fixed(0.9999), m2).enhance(sword(inv, black, 9), inv);
        assertEquals(EnhanceOutcome.DESTROY, d.outcome());
        assertEquals(120, m2.totalTo(m2.level()) + m2.exp(), "파괴에서 120");
    }

    @Test
    void 필요_경험치는_점점_늘고_마스터까지_2430() {
        EnhanceMastery m = new EnhanceMastery(balance.enhance.mastery);
        int prev = 0;
        for (int l = 0; l < 20; l++) {
            assertTrue(m.need(l) > prev);
            prev = m.need(l);
        }
        assertEquals(12, m.need(0));
        assertEquals(288, m.need(19));
        assertEquals(0, m.need(20));
        assertEquals(2430, m.totalTo(20));
        m.add(999999);
        assertEquals(20, m.level());
        assertEquals(0, m.exp());
        m.restore(99, -3);
        assertEquals(20, m.level());
    }

    @Test
    void 숙련도는_모든_검이_함께_쓰고_저장된다() {
        GameSession s = TestFiles.session(5);
        s.inventory.addOre("blackiron", 1000);
        s.inventory.addOre("bloodmoon", 1000);
        Weapon w = s.inventory.add(black);
        s.inventory.equip(w);
        TestFiles.setLevel(w, 2);
        for (int i = 0; i < 30 && s.mastery.level() < 2; i++) {
            if (!s.inventory.weapons().contains(s.inventory.equipped()) || s.inventory.equipped().level() >= 9) {
                Weapon n = s.inventory.add(black);
                s.inventory.equip(n);
            }
            s.enhanceEquipped();
        }
        int lv = s.mastery.level(), ex = s.mastery.exp();
        assertTrue(lv >= 1);
        Weapon blood = s.inventory.add(balance.weapon("bloodmoon"));
        s.inventory.equip(blood);
        assertEquals(lv, s.mastery.level(), "검을 바꿔도 그대로");

        SaveData d = SaveMapper.fromJson(SaveMapper.toJson(SaveMapper.capture(s)));
        GameSession t = TestFiles.session(6);
        SaveMapper.restore(d, t);
        assertEquals(lv, t.mastery.level());
        assertEquals(ex, t.mastery.exp());
        assertEquals(s.progress.greatCount, t.progress.greatCount);
    }

    @Test
    void 오래_돌려도_규칙이_지켜진다() {
        EnhanceMastery m = new EnhanceMastery(balance.enhance.mastery);
        Inventory inv = inv();
        EnhanceService s = service(new Random(7), m);
        Weapon w = sword(inv, black, 0);
        for (int i = 0; i < 3000; i++) {
            if (!inv.weapons().contains(w) || w.level() >= 10) w = sword(inv, black, 0);
            int before = w.level();
            EnhanceResult r = s.enhance(w, inv);
            if (r.outcome() == EnhanceOutcome.DOWN) {
                assertTrue(before >= 3);
                assertTrue(r.masteryBefore() < 17);
            }
            if (r.outcome() == EnhanceOutcome.DESTROY) {
                assertTrue(before >= 6);
                assertTrue(r.masteryBefore() < 15);
            }
        }
        assertEquals(20, m.level());
    }
}
