package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.config.WeaponDef;
import com.yongsa.model.DismantleService;
import com.yongsa.model.EnhanceOdds;
import com.yongsa.model.EnhanceOutcome;
import com.yongsa.model.EnhanceResult;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.EnhanceTable;
import com.yongsa.model.Inventory;
import com.yongsa.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** 강화·분해 규칙 (명세서 5.2, FR-대장간-06~14). */
class EnhanceTest {

    private final Balance balance = TestFiles.balance();
    private final EnhanceTable table = balance.enhanceTable();
    private final WeaponDef black = balance.weapon("blackiron");

    private EnhanceService service(long seed) {
        return new EnhanceService(table, balance.enhance, new Random(seed));
    }

    private Inventory inv(int ore) {
        Inventory inv = new Inventory();
        inv.add(balance.sealedSword());
        inv.addOre("blackiron", ore);
        return inv;
    }

    @Test
    void 단계마다_다섯_확률의_합은_100퍼센트() {
        for (int lv = 0; lv < table.maxLevel(); lv++) assertEquals(100.0, table.odds(lv).total(), 1e-9, "+" + lv);
    }

    @Test
    void 확률_표_2026_10_27() {
        // 성공 / 대성공 3% / 유지 / 하락 / 파괴 (숙련도 0)
        EnhanceOdds o6 = table.odds(6);
        assertEquals(50, o6.success(), 1e-9);
        assertEquals(3, o6.great(), 1e-9);
        assertEquals(19, o6.keep(), 1e-9);
        assertEquals(21, o6.down(), 1e-9);
        assertEquals(7, o6.destroy(), 1e-9);
        EnhanceOdds o9 = table.odds(9);
        assertEquals(29, o9.success(), 1e-9);
        assertEquals(10, o9.destroy(), 1e-9);
        for (int lv = 0; lv < 10; lv++) assertEquals(3, table.odds(lv).great(), 1e-9);
        for (int lv = 0; lv < 3; lv++) assertEquals(0, table.odds(lv).down(), 1e-9);
        assertEquals(0, table.odds(5).destroy(), 1e-9);
        assertEquals(5, table.odds(0).keep(), 1e-9);
    }

    @Test
    void 비용은_1_1_1_2_2_2_3_3_3_3() {
        int[] expect = {1, 1, 1, 2, 2, 2, 3, 3, 3, 3};
        for (int lv = 0; lv < 10; lv++) assertEquals(expect[lv], table.cost(lv));
    }

    @Test
    void 파괴는_6단계_이상에서만() {
        EnhanceService s = service(1);
        Inventory inv = inv(1_000_000);
        for (int lv = 0; lv <= 5; lv++)
            for (int i = 0; i < 3000; i++) {
                Weapon w = inv.add(black);
                inv.equip(w);
                setLevel(w, lv);
                assertNotEquals(EnhanceOutcome.DESTROY, s.enhance(w, inv).outcome());
            }
    }

    @Test
    void 파괴되면_무기가_사라지고_성검을_들고_광물_4개를_돌려받는다() {
        Inventory inv = inv(1000);
        EnhanceService s = service(7);
        EnhanceResult r = null;
        Weapon w = null;
        for (int i = 0; i < 5000; i++) {
            w = inv.add(black);
            inv.equip(w);
            setLevel(w, 9);
            int before = inv.ore("blackiron");
            r = s.enhance(w, inv);
            if (r.outcome() == EnhanceOutcome.DESTROY) {
                assertEquals(before - 3 + 4, inv.ore("blackiron"));
                break;
            }
        }
        assertEquals(EnhanceOutcome.DESTROY, r.outcome());
        assertFalse(inv.weapons().contains(w));
        assertTrue(inv.equipped().def().starter);
    }

    @Test
    void 십단계에서_각성하고_이름과_그림이_바뀐다() {
        Inventory inv = inv(1000);
        Weapon w = inv.add(black);
        inv.equip(w);
        setLevel(w, 9);
        EnhanceService s = service(3);
        while (w.level() < 10 && inv.weapons().contains(w)) {
            EnhanceResult r = s.enhance(w, inv);
            if (r.outcome() == EnhanceOutcome.DESTROY) {
                w = inv.add(black);
                inv.equip(w);
                setLevel(w, 9);
            }
        }
        assertTrue(w.awakened());
        assertEquals("흑철 분쇄검", w.name());
        assertEquals("weapon_blackiron_aw", w.icon());
        assertEquals(52, w.baseAttack());
        assertEquals(EnhanceOutcome.MAX_LEVEL, s.enhance(w, inv).outcome());
    }

    @Test
    void 성검은_강화할_수_없다() {
        Inventory inv = inv(100);
        assertEquals(EnhanceOutcome.NOT_ENHANCEABLE, service(1).enhance(inv.sword(), inv).outcome());
    }

    @Test
    void 분해는_4개_더하기_강화에_쓴_광물의_절반() {
        DismantleService d = new DismantleService(balance.enhance);
        Inventory inv = inv(0);
        Weapon w = inv.restore(9, black, 3, false, 7);
        assertEquals(4 + 3, d.refund(w));
        assertEquals(7, d.dismantle(w, inv));
        assertEquals(7, inv.ore("blackiron"));
        assertEquals(-1, d.dismantle(inv.sword(), inv));
    }

    @Test
    void 결과_비율이_표와_비슷하다() {
        EnhanceService s = service(2026);
        Inventory inv = inv(100_000_000);
        int n = 100_000;
        int[] c = new int[EnhanceOutcome.values().length];
        for (int i = 0; i < n; i++) {
            Weapon w = inv.add(black);
            setLevel(w, 7);
            inv.equip(w);
            c[s.enhance(w, inv).outcome().ordinal()]++;
        }
        EnhanceOdds o = table.odds(7);
        assertEquals(o.success(), c[EnhanceOutcome.SUCCESS.ordinal()] * 100.0 / n, 0.5);
        assertEquals(o.great(), c[EnhanceOutcome.GREAT.ordinal()] * 100.0 / n, 0.5);
        assertEquals(o.keep(), c[EnhanceOutcome.KEEP.ordinal()] * 100.0 / n, 0.5);
        assertEquals(o.down(), c[EnhanceOutcome.DOWN.ordinal()] * 100.0 / n, 0.5);
        assertEquals(o.destroy(), c[EnhanceOutcome.DESTROY.ordinal()] * 100.0 / n, 0.5);
    }

    private static void setLevel(Weapon w, int lv) {
        try {
            var m = Weapon.class.getDeclaredMethod("setLevel", int.class);
            m.setAccessible(true);
            m.invoke(w, lv);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
