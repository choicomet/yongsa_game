package com.yongsa;

import com.yongsa.combat.Monster;
import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.model.OreType;
import com.yongsa.world.Room;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 잡몹방 보물상자는 가끔만 나오고, 상자에서 얻은 포션으로 회복한다 (2026-10-14 팀 결정). */
class PotionChestTest {
    private final Balance b = TestData.balance();

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
        }
        return all;
    }

    /** L1 에서 싸움을 시작하고 몬스터를 모두 쓰러뜨린다. 상자가 나왔으면 true. */
    private boolean clearL1(long seed, GameState s) {
        GameSession g = new GameSession(b, TestData.map(), s, seed);
        TestData.walkIn(g, g.map.room("L1"));
        assertTrue(g.fighting());
        for (Monster m : g.monsters) m.dead = true;
        run(g, new Controls(), 2 / 60f);
        assertFalse(g.fighting());
        return g.hasChest(g.map.room("L1"));
    }

    @Test
    void mobRoomChestsAreOnlySometimes() {
        int chests = 0, n = 40;
        for (int seed = 0; seed < n; seed++) if (clearL1(seed, NewGame.create(b))) chests++;
        assertTrue(chests > 0, "가끔은 나온다");
        assertTrue(chests < n * 0.6f, "늘 나오지는 않는다: " + chests + "/" + n);
    }

    @Test
    void chestGivesOreAndPotionAndPotionHeals() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 1);
        Room r = g.map.room("L1");
        s.room("L1").cleared = true;
        s.chests.add("L1");
        g.hero.x = g.chestX(r);
        g.hero.y = g.chestY(r) - 30;
        assertTrue(g.map.free(g.chestX(r), g.chestY(r), 4, 4), "상자는 바닥 위에 놓인다");
        run(g, new Controls(), 0.05f);
        int ore = s.ore(OreType.BLACKIRON);
        g.openChest(r);
        assertTrue(s.ore(OreType.BLACKIRON) - ore >= b.reward.mobChestOreMin);
        assertEquals(b.reward.mobChestPotions, s.potions);

        int max = g.rules.maxHp(s);
        s.hp = 10;
        Controls drink = new Controls();
        drink.potion = true;
        run(g, drink, 1 / 60f);
        assertEquals(0, s.potions);
        assertEquals(10 + Math.round(max * b.potion.healRatio), s.hp);
        List<GameEvent> ev = run(g, drink, 1.2f);
        assertTrue(ev.stream().anyMatch(e -> e.type == GameEvent.Type.TOAST && e.text.contains("포션이 없다")));
    }

    @Test
    void potionsAreCappedAndNotWastedAtFullHp() {
        GameState s = NewGame.create(b);
        s.potions = b.potion.max;
        GameSession g = new GameSession(b, TestData.map(), s, 1);
        Controls drink = new Controls();
        drink.potion = true;
        run(g, drink, 1 / 60f);
        assertEquals(b.potion.max, s.potions, "체력이 가득하면 마시지 않는다");
        s.chests.add("L6");
        g.openChest(g.map.room("L6"));
        assertEquals(b.potion.max, s.potions, "최대 개수를 넘지 않는다");
    }
}
