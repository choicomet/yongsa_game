package com.yongsa;

import com.yongsa.combat.Boss;
import com.yongsa.combat.Monster;
import com.yongsa.combat.Projectile;
import com.yongsa.model.Controls;
import com.yongsa.model.GameSession;
import com.yongsa.world.Room;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 리치 개편, 보스 밀기 방지, 보스 공격 막기 (2026-10-10 세 번째 결정). */
class LichTest {

    private static final float DT = 1f / 60f;

    private static void step(GameSession s, Controls c, int frames) {
        for (int i = 0; i < frames; i++) {
            s.update(DT, c);
            c.clearPresses();
        }
    }

    /** 리치방에 들어가 리치가 움직이기 시작할 때까지. */
    private static Boss lich(GameSession s) {
        Room r = s.roomById.get("R6");
        s.unit.teleport(r.def.entry().x, r.def.entry().y);
        step(s, new Controls(), 2);
        if (s.awaitingBossConfirm()) s.answerBossConfirm(true);
        step(s, new Controls(), 50);
        Boss b = s.activeBoss();
        assertNotNull(b);
        assertEquals("lich", b.def.id);
        return b;
    }

    @Test
    void 보스는_회피와_공격으로_밀리지_않는다() {
        GameSession s = TestFiles.session(40);
        Boss b = lich(s);
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        for (int i = 0; i < 6; i++) {
            float bx = b.footX(), by = b.footY();
            s.unit.teleport(bx - 40, b.footY() + 2);
            s.unit.lookAt(1, 0);
            Controls c = new Controls();
            c.moveX = 1;
            c.dodge = true;
            step(s, c, 1);
            c.attack = true;
            step(s, c, 1);
            step(s, new Controls(), 1);
            if (b.currentPattern() == null) assertEquals(bx, b.footX(), 0.01f, "보스가 밀렸다");
            assertEquals(by, b.footY(), 0.01f);
        }
    }

    @Test
    void 보스_공격은_막아도_한_번에_막기가_깨진다() {
        GameSession s = TestFiles.session(41);
        Controls c = new Controls();
        s.unit.lookAt(1, 0);
        c.guard = true;
        step(s, c, 2);
        int hp = s.hero.hp();
        assertFalse(s.hitHeroHeavy(40, s.unit.centerX() + 30, s.unit.centerY()));
        assertEquals(hp, s.hero.hp(), "막은 공격은 피해가 없다");
        assertFalse(s.unit.guarding);
        assertTrue(s.unit.guardBroken > 0);
        // 깨진 동안은 못 막는다
        step(s, c, 2);
        assertTrue(s.hitHeroHeavy(40, s.unit.centerX() + 30, s.unit.centerY()));
    }

    @Test
    void 보스_탄을_막아도_막기가_깨진다() {
        GameSession s = TestFiles.session(42);
        Controls c = new Controls();
        s.unit.lookAt(1, 0);
        c.guard = true;
        step(s, c, 2);
        s.projectiles.add(new Projectile(s.unit.centerX() + 30, s.unit.centerY() + 8, -200, 0, 8, 30, "bolt_green", 3f).boss());
        int hp = s.hero.hp();
        step(s, c, 20);
        assertEquals(hp, s.hero.hp());
        assertTrue(s.unit.guardBroken > 0);
    }

    @Test
    void 뼈갑옷이_있으면_리치_체력이_닳지_않고_깨지면_휘청인다() {
        GameSession s = TestFiles.session(43);
        Boss b = lich(s);
        b.armorMax = 300;
        b.armor = 300;
        int hp = b.hp();
        assertFalse(b.damage(200, 0, 0, 0));
        assertEquals(hp, b.hp());
        assertEquals(100, b.armor);
        b.damage(150, 0, 0, 0);
        assertEquals(0, b.armor);
        assertEquals(hp, b.hp(), "갑옷을 깬 공격은 체력까지 넘어가지 않는다");
        assertTrue(b.stagger > 0);
        assertTrue(b.consumeArmorBroken());
        b.damage(100, 0, 0, 0);
        assertEquals(hp - 100, b.hp());
    }

    @Test
    void 둔화는_밟고_있어도_늘지_않고_풀린_뒤_2초를_버티면_다시_걸린다() {
        GameSession s = TestFiles.session(44);
        var unit = s.unit;
        var st = s.balance.statuses;
        // 처음 밟으면 바로 5초
        assertTrue(unit.updateSwamp(true, DT, st.slowDuration, st.slowRatio, st.slowRetouchTime));
        assertEquals(5f, unit.slow, 1e-4);
        // 계속 밟고 있어도 시간이 늘지 않는다
        for (int i = 0; i < 60; i++) {
            unit.update(DT, 0, 0, false, s.collision);
            assertFalse(unit.updateSwamp(true, DT, st.slowDuration, st.slowRatio, st.slowRetouchTime));
        }
        assertEquals(4f, unit.slow, 0.05f);
        // 늪 위에서 풀린다
        for (int i = 0; i < 60 * 4 + 5; i++) {
            unit.update(DT, 0, 0, false, s.collision);
            unit.updateSwamp(true, DT, st.slowDuration, st.slowRatio, st.slowRetouchTime);
        }
        assertFalse(unit.slowed());
        // 2초를 채우기 전에는 다시 걸리지 않는다
        boolean again = false;
        int frames = 0;
        while (!again && frames < 400) {
            unit.update(DT, 0, 0, false, s.collision);
            again = unit.updateSwamp(true, DT, st.slowDuration, st.slowRatio, st.slowRetouchTime);
            frames++;
        }
        assertTrue(again);
        assertEquals(2f, frames * DT, 0.1f);
        // 나갔다가 다시 들어오면 바로 걸린다 (둔화가 끝난 뒤)
        unit.clearStatuses();
        unit.updateSwamp(false, DT, st.slowDuration, st.slowRatio, st.slowRetouchTime);
        assertTrue(unit.updateSwamp(true, DT, st.slowDuration, st.slowRatio, st.slowRetouchTime));
    }

    @Test
    void 둔화되면_이동_속도가_30퍼센트_줄어든다() {
        GameSession s = TestFiles.session(45);
        float x0 = s.unit.footX();
        s.unit.update(0.5f, 1, 0, false, s.collision);
        float normal = s.unit.footX() - x0;
        s.unit.teleport(x0, s.unit.footY());
        s.unit.updateSwamp(true, DT, 5, 0.3f, 2);
        s.unit.update(0.5f, 1, 0, false, s.collision);
        float slowed = s.unit.footX() - x0;
        assertEquals(normal * 0.7f, slowed, 1f);
    }

    @Test
    void 리치는_가시나_소환을_쓰면_5초_실명을_건다() {
        GameSession s = TestFiles.session(46);
        Boss b = lich(s);
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        // 패턴이 몇 번 돌 때까지 기다린다
        boolean blinded = false;
        for (int i = 0; i < 60 * 20 && !blinded; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
            blinded = s.unit.blinded();
        }
        assertTrue(blinded);
        assertTrue(s.unit.blind <= 5f);
        assertNotNull(b);
    }

    @Test
    void 리치는_소환할_때마다_해골_기사와_병사를_늘려_부른다() {
        GameSession s = TestFiles.session(47);
        Boss b = lich(s);
        // 1번째 1·5, 2번째 2·10, 3번째 3·15, 그 뒤로는 최대 4·20
        int[][] want = {{5, 1}, {10, 2}, {15, 3}, {20, 4}, {20, 4}};
        for (int i = 0; i < want.length; i++) {
            int[] n = b.summonCounts(i + 1);
            assertEquals(want[i][0], n[0], (i + 1) + "번째 해골 병사");
            assertEquals(want[i][1], n[1], (i + 1) + "번째 해골 기사");
        }
        // 실제로 부르기: 두 번 소환하면 기사 1+2, 병사 5+10
        s.hero.setMaxHp(999999);
        s.hero.fullHeal();
        s.hero.invulnerable = true;
        for (int wave = 1; wave <= 2; wave++) {
            forceSummon(s, b);
            for (int i = 0; i < 80; i++) {
                holdPatterns(b);   // 그동안 리치가 스스로 다른 소환을 하지 않게
                step(s, new Controls(), 1);
            }
        }
        long knights = b.room.monsters.stream().filter(m -> m.summoned && m.def.id.equals("skeletonknight")).count();
        long soldiers = b.room.monsters.stream().filter(m -> m.summoned && m.def.id.equals("minion")).count();
        assertEquals(3, knights);
        assertEquals(15, soldiers);
        // 해골 기사가 해골 병사보다 강하다
        var knight = s.balance.monster("skeletonknight");
        var soldier = s.balance.monster("minion");
        assertTrue(knight.hp > soldier.hp && knight.attack > soldier.attack);
    }

    private static void holdPatterns(Boss b) {
        try {
            var f = Boss.class.getDeclaredField("patternTimer");
            f.setAccessible(true);
            f.setFloat(b, 0f);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void forceSummon(GameSession s, Boss b) {
        try {
            var m = Boss.class.getDeclaredMethod("startPattern", com.yongsa.combat.BossPattern.class, com.yongsa.combat.CombatContext.class);
            m.setAccessible(true);
            m.invoke(b, com.yongsa.combat.BossPattern.SUMMON, s);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void 구석에서_계속_붙어_있으면_리치가_멀리_순간이동한다() {
        GameSession s = TestFiles.session(48);
        Boss b = lich(s);
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        boolean blinked = false;
        for (int i = 0; i < 60 * 12 && !blinked; i++) {
            s.unit.teleport(b.footX() - 30, b.footY());
            s.hero.fullHeal();
            s.hazards.clear();
            step(s, new Controls(), 1);
            blinked = b.consumeBlinked();
        }
        assertTrue(blinked);
        assertTrue(Math.hypot(b.footX() - s.unit.footX(), b.footY() - s.unit.footY()) > 150);
        assertTrue(s.projectiles.stream().anyMatch(p -> p.fromBoss));
        assertNotEquals(Monster.State.DYING, b.state());
    }
}
