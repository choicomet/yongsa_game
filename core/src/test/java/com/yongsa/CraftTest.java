package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.CraftService;
import com.yongsa.model.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CraftTest {
    final Balance b = TestData.balance();

    @Test
    void firstCraftAutoEquipsAndOwnedIsBlocked() {
        GameState s = TestData.newGame();
        s.minerals = 20;
        CraftService.Result r = CraftService.craft(b, s, "sword");
        assertTrue(r.executed);
        assertTrue(r.autoEquipped);
        assertEquals("sword", s.equipped);
        assertEquals(14, s.minerals);
        assertEquals(CraftService.Block.OWNED, CraftService.craftBlock(b, s, "sword"));
        assertEquals(CraftService.Block.LOCKED, CraftService.craftBlock(b, s, "hammer"), "망치는 첫 보스 처치 후");
    }

    @Test
    void T05_cannotDismantleEquippedOrLast() {
        GameState s = TestData.newGame();
        s.minerals = 100;
        CraftService.craft(b, s, "sword");
        assertEquals(CraftService.Block.LAST_WEAPON, CraftService.dismantle(b, s, "sword", false).block);
        s.defeatedBosses.add("boss_mine");
        CraftService.Result h = CraftService.craft(b, s, "hammer");
        assertTrue(h.executed);
        assertFalse(h.autoEquipped, "두 번째 무기는 가방으로");
        assertEquals("sword", s.equipped);
        assertEquals(CraftService.Block.EQUIPPED, CraftService.dismantle(b, s, "sword", false).block);
        assertEquals(CraftService.Block.IN_COMBAT, CraftService.dismantle(b, s, "hammer", true).block);
        assertEquals(CraftService.Block.IN_COMBAT, CraftService.equip(s, "hammer", true).block);
        assertNotNull(s.equippedWeapon());
        assertEquals(2, s.weapons.size());
    }

    @Test
    void T06_dismantleRefundAndRecraftAtZero() {
        GameState s = TestData.newGame();
        s.minerals = 100;
        s.defeatedBosses.add("boss_mine");
        CraftService.craft(b, s, "sword");
        CraftService.craft(b, s, "hammer");
        s.weapon("hammer").level = 4;
        s.weapon("hammer").failCount = 2;
        int before = s.minerals;
        CraftService.Result d = CraftService.dismantle(b, s, "hammer", false);
        assertTrue(d.executed);
        assertEquals(5, d.minerals);
        assertEquals(before + 5, s.minerals);
        assertEquals(3, CraftService.refund(b, "sword"));
        CraftService.Result again = CraftService.craft(b, s, "hammer");
        assertTrue(again.executed);
        assertEquals(0, s.weapon("hammer").level);
        assertEquals(0, s.weapon("hammer").failCount);
    }
}
