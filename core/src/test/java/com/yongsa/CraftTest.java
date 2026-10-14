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
        s.ore[0] = 20;
        CraftService.Result r = CraftService.craft(b, s, "sword");
        assertTrue(r.executed);
        assertTrue(r.autoEquipped);
        assertEquals("sword", s.equipped);
        assertEquals(14, s.ore[0]);
        assertEquals(CraftService.Block.OWNED, CraftService.craftBlock(b, s, "sword"));
        assertEquals(CraftService.Block.LOCKED, CraftService.craftBlock(b, s, "hammer"), "망치는 첫 보스 처치 후");
    }

    @Test
    void T05_secondWeaponGoesToBagAndCombatBlocksEquip() {
        GameState s = TestData.newGame();
        s.ore[0] = 100;
        s.ore[1] = 100;
        CraftService.craft(b, s, "sword");
        s.defeatedBosses.add("boss_mine");
        CraftService.Result h = CraftService.craft(b, s, "hammer");
        assertTrue(h.executed);
        assertFalse(h.autoEquipped, "두 번째 무기는 가방으로");
        assertEquals("sword", s.equipped);
        assertEquals(CraftService.Block.IN_COMBAT, CraftService.equip(s, "hammer", true).block);
        assertTrue(CraftService.equip(s, "hammer", false).executed);
        assertEquals("hammer", s.equipped);
        assertEquals(2, s.weapons.size(), "만든 장비는 없앨 수 없다 (분해 없음)");
    }

    @Test
    void T06_eachWeaponNeedsItsOwnOreColor() {
        GameState s = TestData.newGame();
        s.defeatedBosses.add("boss_mine");
        s.defeatedBosses.add("boss_fortress");
        // 노란 광석만 잔뜩 있어도 망치와 쌍단검은 만들 수 없다
        s.ore[0] = 100;
        assertEquals(CraftService.Block.NOT_ENOUGH, CraftService.craftBlock(b, s, "hammer"));
        assertEquals(CraftService.Block.NOT_ENOUGH, CraftService.craftBlock(b, s, "daggers"));
        assertTrue(CraftService.craft(b, s, "sword").executed);
        s.ore[1] = b.weapon("hammer").craftCost;
        s.ore[2] = b.weapon("daggers").craftCost;
        assertTrue(CraftService.craft(b, s, "hammer").executed);
        assertTrue(CraftService.craft(b, s, "daggers").executed);
        assertEquals(0, s.ore[1]);
        assertEquals(0, s.ore[2]);
        assertEquals(100 - b.weapon("sword").craftCost, s.ore[0], "다른 색 광석은 줄지 않는다");
        assertEquals("mine", b.weapon("sword").ore);
        assertEquals("fortress", b.weapon("hammer").ore);
        assertEquals("temple", b.weapon("daggers").ore);
    }
}
