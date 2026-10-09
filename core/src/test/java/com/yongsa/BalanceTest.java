package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.DamageCalc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 피해량 표와 강화 확률표를 설정 데이터에서 자동 검증한다. */
class BalanceTest {
    final Balance b = TestData.balance();

    @Test
    void damageTableMatchesDesign() {
        int[][] table = {{0, 12, 19}, {2, 15, 24}, {3, 16, 26}, {5, 19, 30}, {6, 21, 33}, {8, 24, 37}, {10, 26, 42}};
        for (int[] row : table) {
            assertEquals(row[1], DamageCalc.weaponDamage(b, "sword", row[0]), "철검 +" + row[0]);
            assertEquals(row[2], DamageCalc.weaponDamage(b, "hammer", row[0]), "망치 +" + row[0]);
        }
    }

    @Test
    void roundHalfUp() {
        assertEquals(3, DamageCalc.damage(5, 0, 0.0) - 2);
        assertEquals(15, DamageCalc.damage(10, 5, 0.1)); // 15.0
        assertEquals(3, DamageCalc.damage(1, 5, 0.3)); // 2.5 는 올림
    }

    @Test
    void enhanceTableIsConsistent() {
        int[] cost = {2, 3, 4, 5, 6, 7, 8, 9, 10, 12};
        int[] tries = {1, 1, 2, 3, 3, 4, 4, 5, 5, 6};
        for (int i = 0; i < 10; i++) {
            Balance.EnhanceStep s = b.enhanceStep(i + 1);
            assertEquals(cost[i], s.cost);
            assertEquals(tries[i], s.maxTries);
            // 확률 합계 검사: 성공과 실패만 합쳐 100%
            float fail = 1f - s.chance;
            assertEquals(1f, s.chance + fail, 1e-6);
            assertTrue(s.chance > 0 && s.chance <= 1);
        }
        assertEquals(10, b.maxLevel());
    }

    @Test
    void firstVeinsReachSwordPlusTwo() {
        // 첫 채굴 12개 - 철검 6 = 6, +1과 +2 비용 합계 5
        int harvest = b.zone("mine").veinYield * b.mining.veinsPerRoom;
        assertEquals(12, harvest);
        int left = harvest - b.weapon("sword").craftCost;
        assertTrue(left >= b.enhanceStep(1).cost + b.enhanceStep(2).cost);
    }
}
