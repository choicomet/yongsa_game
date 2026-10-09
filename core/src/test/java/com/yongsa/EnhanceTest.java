package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.CraftService;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.GameState;
import com.yongsa.model.Rng;
import com.yongsa.model.WeaponState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EnhanceTest {
    final Balance b = TestData.balance();

    GameState withSword(int level, int minerals) {
        GameState s = TestData.newGame();
        s.minerals = 6;
        assertTrue(CraftService.craft(b, s, "sword").executed);
        s.weapon("sword").level = level;
        s.minerals = minerals;
        return s;
    }

    @Test
    void T01_notEnoughMineralsChangesNothing() {
        GameState s = withSword(0, 1);
        EnhanceService.Result r = EnhanceService.enhance(b, s, "sword", TestData.forbidden());
        assertFalse(r.executed);
        assertEquals(EnhanceService.Block.NOT_ENOUGH, r.block);
        assertEquals(1, s.minerals);
        assertEquals(0, s.weapon("sword").level);
        assertEquals(0, s.weapon("sword").failCount);

        GameState c = TestData.newGame();
        c.minerals = 5;
        CraftService.Result cr = CraftService.craft(b, c, "sword");
        assertFalse(cr.executed);
        assertEquals(5, c.minerals);
        assertTrue(c.weapons.isEmpty());
    }

    @Test
    void T02_probabilityBoundaryAndGuarantee() {
        // +3: 90%, 최대 2회 (첫 보스 처치 전 상한 +3)
        GameState s = withSword(2, 100);
        EnhanceService.Result fail = EnhanceService.enhance(b, s, "sword", TestData.fixed(0.90));
        assertTrue(fail.executed);
        assertFalse(fail.success, "경계값 0.90 은 실패");
        assertEquals(1, s.weapon("sword").failCount);
        assertEquals(96, s.minerals);
        // 두 번째 시도는 보장: 난수를 쓰지 않는다
        EnhanceService.Result ok = EnhanceService.enhance(b, s, "sword", TestData.forbidden());
        assertTrue(ok.success);
        assertTrue(ok.guaranteed);
        assertEquals(3, s.weapon("sword").level);
        assertEquals(0, s.weapon("sword").failCount);
        assertEquals(92, s.minerals, "보장 시도도 같은 비용");

        GameState t = withSword(2, 100);
        assertTrue(EnhanceService.enhance(b, t, "sword", TestData.fixed(0.8999)).success, "0.8999 는 성공");
    }

    @Test
    void T02_everyStepGuaranteesWithinMaxTries() {
        GameState s = withSword(0, 10_000);
        s.defeatedBosses.add("boss_mine");
        s.defeatedBosses.add("boss_fortress");
        for (int target = 1; target <= 10; target++) {
            Balance.EnhanceStep step = b.enhanceStep(target);
            int tries = 0;
            while (s.weapon("sword").level < target) {
                // 항상 실패하는 난수(0.9999)를 주어도 최대 시도 안에 성공해야 한다
                EnhanceService.enhance(b, s, "sword", () -> 0.9999);
                tries++;
                assertTrue(tries <= step.maxTries, "+" + target);
            }
            assertEquals(step.chance >= 0.9999 ? 1 : step.maxTries, tries, "+" + target);
        }
    }

    @Test
    void T03_failCountSurvivesSwapAndSaveLoad() throws Exception {
        GameState s = withSword(3, 100);
        s.defeatedBosses.add("boss_mine");
        s.minerals = 100;
        s.weapons.add(new WeaponState("hammer"));
        EnhanceService.enhance(b, s, "sword", Rng.enhanceSource(s)); // 결과 무관
        // 실패를 만들기 위해 고정 실패
        GameState t = withSword(3, 100);
        t.defeatedBosses.add("boss_mine");
        t.weapons.add(new WeaponState("hammer"));
        EnhanceService.enhance(b, t, "sword", TestData.fixed(0.99));
        assertEquals(1, t.weapon("sword").failCount);
        t.equipped = "hammer"; // 교체
        long uses = t.enhanceUses;
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("ys");
        com.yongsa.save.SaveManager sm = new com.yongsa.save.SaveManager(dir, b);
        assertTrue(sm.save(t), sm.lastError());
        GameState l = sm.load().state;
        assertEquals(1, l.weapon("sword").failCount);
        assertEquals(uses, l.enhanceUses);
        assertEquals(t.enhanceSeed, l.enhanceSeed);
    }

    @Test
    void sameSeedSameResults_noRerollAfterReload() {
        GameState a = withSword(3, 1000);
        a.defeatedBosses.add("boss_mine");
        GameState c = withSword(3, 1000);
        c.defeatedBosses.add("boss_mine");
        for (int i = 0; i < 3; i++) {
            EnhanceService.Result ra = EnhanceService.enhance(b, a, "sword", Rng.enhanceSource(a));
            EnhanceService.Result rc = EnhanceService.enhance(b, c, "sword", Rng.enhanceSource(c));
            assertEquals(ra.success, rc.success);
        }
        assertEquals(a.enhanceUses, c.enhanceUses);
    }

    @Test
    void T04_capAndMaxLevelBlockWithoutCost() {
        GameState s = withSword(3, 100);
        EnhanceService.Result r = EnhanceService.enhance(b, s, "sword", TestData.forbidden());
        assertEquals(EnhanceService.Block.CAP, r.block);
        assertEquals(100, s.minerals);

        GameState m = withSword(10, 100);
        m.defeatedBosses.add("boss_mine");
        m.defeatedBosses.add("boss_fortress");
        EnhanceService.Result r2 = EnhanceService.enhance(b, m, "sword", TestData.forbidden());
        assertEquals(EnhanceService.Block.MAX_LEVEL, r2.block);
        assertEquals(100, m.minerals);
        assertEquals(0, m.weapon("sword").failCount);
    }

    @Test
    void previewShowsGuaranteeAsHundredPercent() {
        GameState s = withSword(2, 100);
        s.weapon("sword").failCount = 1;
        EnhanceService.Preview p = EnhanceService.preview(b, s, "sword");
        assertEquals(1f, p.chance);
        assertTrue(p.guaranteed);
        assertEquals(1, p.triesToGuarantee);
        assertEquals(15, p.currentDamage);
        assertEquals(16, p.nextDamage);
    }
}
