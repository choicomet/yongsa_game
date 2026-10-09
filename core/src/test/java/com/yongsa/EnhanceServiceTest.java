package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.EnhanceService.Odds;
import com.yongsa.model.EnhanceService.Outcome;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.model.Rules;
import com.yongsa.model.Weapon;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EnhanceServiceTest {
    private final Balance b = TestData.balance();
    private final EnhanceService es = new EnhanceService(b);

    @Test
    void oddsAlwaysSumTo100() {
        for (int lv = 0; lv < b.enhance.maxLevel; lv++) {
            assertEquals(100.0, es.odds(lv).total(), 1e-9, "+" + lv);
        }
    }

    @Test
    void oddsMatchSpecTable() {
        Odds o = es.odds(6);
        assertEquals(53, o.success, 1e-9);
        assertEquals(18.8, o.keep, 1e-9);
        assertEquals(21.15, o.down, 1e-9);
        assertEquals(7.05, o.destroy, 1e-9);
        Odds o3 = es.odds(3);
        assertEquals(74, o3.success, 1e-9);
        assertEquals(13, o3.keep, 1e-9);
        assertEquals(13, o3.down, 1e-9);
        assertEquals(0, o3.destroy, 1e-9);
        assertEquals(5, es.odds(0).keep, 1e-9);
    }

    @Test
    void destroyOnlyFromPlusSix() {
        for (int lv = 0; lv < 6; lv++) assertEquals(0, es.odds(lv).destroy, 1e-9);
        for (int lv = 6; lv < 10; lv++) assertTrue(es.odds(lv).destroy > 0);
    }

    @Test
    void costsByBand() {
        assertEquals(1, es.cost(0));
        assertEquals(1, es.cost(2));
        assertEquals(2, es.cost(3));
        assertEquals(3, es.cost(9));
    }

    @Test
    void reachingPlusTenAwakensWeapon() {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon("blackiron");
        s.equippedUid = w.uid;
        s.addOre(OreType.BLACKIRON, 100000);
        Random rnd = new Random(1);
        boolean awakened = false;
        for (int i = 0; i < 10000 && s.weapons.contains(w) && !w.awakened; i++) {
            EnhanceService.Result r = es.enhance(s, w, rnd);
            if (r.awakened) awakened = true;
        }
        if (s.weapons.contains(w)) {
            assertTrue(awakened);
            assertEquals(10, w.level);
            assertNotNull(es.blockReason(s, w), "+10 은 더 강화할 수 없다");
        }
    }

    @Test
    void destructionRefundsHalfAndEquipsHolySword() {
        GameState s = NewGame.create(b);
        Weapon w = s.newWeapon("blackiron");
        s.equippedUid = w.uid;
        w.level = 9;
        s.addOre(OreType.BLACKIRON, 3);
        // 파괴 구간 맨 끝 값이 나오게 한다
        Random always99 = new Random() {
            @Override
            public double nextDouble() {
                return 0.9999;
            }
        };
        EnhanceService.Result r = es.enhance(s, w, always99);
        assertEquals(Outcome.DESTROY, r.outcome);
        assertEquals(4, r.refund);
        assertEquals(4, s.ore(OreType.BLACKIRON));
        assertFalse(s.weapons.contains(w));
        assertEquals(Rules.HOLY_SEALED, s.equipped().defId);
        assertEquals(1, s.stats.destroyed);
    }

    @Test
    void holySwordCannotBeEnhanced() {
        GameState s = NewGame.create(b);
        assertNotNull(es.blockReason(s, s.equipped()));
    }
}
