package com.yongsa;

import com.yongsa.combat.Boss;
import com.yongsa.combat.Hazard;
import com.yongsa.model.Controls;
import com.yongsa.model.GameSession;
import com.yongsa.model.Weapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 성검·흡혈·마왕 2페이즈·뼈 가시 (2026-10-10 두 번째 결정). */
class PowerTest {

    private static void step(GameSession s, Controls c, int frames) {
        for (int i = 0; i < frames; i++) {
            s.update(1f / 60f, c);
            c.clearPresses();
        }
    }

    @Test
    void 봉인된_검은_공격력_10이고_이름이_봉인된_검이다() {
        GameSession s = TestFiles.session(30);
        Weapon w = s.inventory.equipped();
        assertEquals("봉인된 검", w.displayName());
        assertEquals(10, s.attackOf(w));
        assertEquals(0f, s.lifestealOf(w));
    }

    @Test
    void 성검_공격력은_150에_종류별_최고_검의_40퍼센트를_더한다() {
        GameSession s = TestFiles.session(31);
        Weapon holy = s.inventory.add(s.balance.holySword());
        assertEquals(150, s.attackOf(holy));
        Weapon b1 = s.inventory.add(s.balance.weapon("blackiron"));   // 22
        Weapon b2 = s.inventory.add(s.balance.weapon("blackiron"));
        TestFiles.setLevel(b2, 4);                                       // 22 + 12 = 34 (같은 종류는 가장 센 것만)
        Weapon sky = s.inventory.add(s.balance.weapon("sky"));          // 110
        assertEquals(150 + Math.round((34 + 110) * 0.4f), s.attackOf(holy));
        assertEquals("성검", holy.displayName());
        assertNotNull(b1);
        assertNotNull(sky);
    }

    @Test
    void 흡혈은_6강부터_표대로_성검은_절반의_합과_최고값_중_큰_쪽() {
        GameSession s = TestFiles.session(32);
        Weapon holy = s.inventory.add(s.balance.holySword());
        Weapon a = s.inventory.add(s.balance.weapon("blackiron"));
        Weapon b = s.inventory.add(s.balance.weapon("bloodmoon"));
        Weapon c = s.inventory.add(s.balance.weapon("sky"));
        float[] expect = {0, 0, 0, 0, 0, 0, 0.5f, 0.7f, 1.0f, 1.25f, 1.5f};
        for (int lv = 0; lv <= 10; lv++) {
            TestFiles.setLevel(a, lv);
            assertEquals(expect[lv], s.lifestealOf(a), 1e-6);
        }
        TestFiles.setLevel(a, 10);   // 1.5
        TestFiles.setLevel(b, 6);    // 0.5
        TestFiles.setLevel(c, 0);    // 0
        // 절반의 합 = 1.0 < 최고 1.5 → 1.5
        assertEquals(1.5f, s.lifestealOf(holy), 1e-6);
        TestFiles.setLevel(b, 10);
        TestFiles.setLevel(c, 10);
        // 절반의 합 = 2.25 > 1.5 → 2.25
        assertEquals(2.25f, s.lifestealOf(holy), 1e-6);
    }

    @Test
    void 마왕은_1페이즈_체력이_다하면_1점5배로_다시_차고_대사가_나온다() {
        GameSession s = TestFiles.session(33);
        var ev = new java.util.ArrayList<com.yongsa.model.GameEvent>();
        s.addListener(ev::add);
        TestFiles.toArena(s);
        Boss king = s.demonKing;
        int max1 = king.maxHp();
        step(s, new Controls(), 60);
        king.damage(max1 * 2, 0, 0, 0);
        assertTrue(king.alive());
        assertTrue(king.transitioning());
        step(s, new Controls(), 2);
        assertTrue(ev.stream().anyMatch(e -> e.type() == com.yongsa.model.GameEvent.Type.SPEECH
                && e.text().contains("본격적으로")));
        assertFalse(king.damage(100, 0, 0, 0)); // 바뀌는 동안은 무적
        step(s, new Controls(), (int) (60 * Boss.TRANSITION_TIME) + 5);
        assertEquals(2, king.phase);
        assertEquals(Math.round(max1 * 1.5f), king.maxHp());
        assertEquals(king.maxHp(), king.hp());
        assertEquals("demon_eldritch", king.sprite());
    }

    @Test
    void 마왕에게_죽으면_마왕_대사만_나오고_검_강화_독백은_없다() {
        GameSession s = TestFiles.session(34);
        var ev = new java.util.ArrayList<com.yongsa.model.GameEvent>();
        s.addListener(ev::add);
        TestFiles.toArena(s);
        s.hero.damage(99999);
        step(s, new Controls(), 2);
        assertTrue(ev.stream().anyMatch(e -> "한심하긴.".equals(e.text())));
        s.respawn();
        // 2026-10-12 결정: "검을 더 강화해야겠어" 독백은 뺐다
        assertTrue(ev.stream().noneMatch(e -> e.type() == com.yongsa.model.GameEvent.Type.SPEECH
                && "용사".equals(e.id()) && e.text().contains("강화")));
    }

    @Test
    void 뼈_가시는_예고가_뜬_자리에서_솟고_용사를_따라가지_않는다() {
        GameSession s = TestFiles.session(35);
        Hazard h = new Hazard(Hazard.Type.BONE_SPIKE, s.unit.footX(), s.unit.footY(), 40, 0, 0.9f, 0, 50);
        s.addHazard(h);
        float x = h.x;
        Controls c = new Controls();
        c.moveX = 1;
        int hp = s.hero.hp();
        step(s, c, 60); // 0.9초 안에 오른쪽으로 걸어서 피한다
        assertEquals(x, h.x);
        assertEquals(hp, s.hero.hp());
    }

    @Test
    void F1_광물_무한_모드는_제작_강화에_광물이_줄지_않고_끄면_원래대로() {
        GameSession s = TestFiles.session(36);
        s.inventory.addOre("blackiron", 3);
        assertTrue(s.toggleInfiniteOre());
        Weapon w = s.craft(s.balance.weapon("blackiron")); // 8개 필요하지만 무한
        assertNotNull(w);
        s.equip(w);
        for (int i = 0; i < 20; i++) s.enhanceEquipped();
        assertEquals("무한", s.inventory.oreText("blackiron"));
        assertFalse(s.toggleInfiniteOre());
        // 실제 개수는 줄지 않았다 (파괴 보상으로 늘었을 수는 있다)
        int real = s.inventory.ore("blackiron");
        assertTrue(real >= 3 && real < com.yongsa.model.Inventory.INFINITE);
        // 저장에도 실제 개수만 남는다
        assertEquals(real, (int) com.yongsa.save.SaveMapper.capture(s).ores.get("blackiron"));
    }
}
