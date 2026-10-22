package com.yongsa;

import com.badlogic.gdx.math.Rectangle;
import com.yongsa.combat.Boss;
import com.yongsa.combat.BossPattern;
import com.yongsa.combat.CombatContext;
import com.yongsa.combat.Hazard;
import com.yongsa.combat.Projectile;
import com.yongsa.model.Controls;
import com.yongsa.model.EnhanceOutcome;
import com.yongsa.model.EnhanceResult;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.TreasureChest;
import com.yongsa.model.Weapon;
import com.yongsa.save.SaveData;
import com.yongsa.save.SaveMapper;
import com.yongsa.world.Room;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-10-12 변경 묶음: 좀비킹 기절, 마룡 화염 구슬·날아가기, 마왕 공간 절단·여러 눈·좌우 반전, 침대, 보물상자, 강화 100%. */
class ChangeSetTest {

    private static final float DT = 1f / 60f;

    private static void step(GameSession s, Controls c, int frames) {
        for (int i = 0; i < frames; i++) {
            s.update(DT, c);
            c.clearPresses();
        }
    }

    private static void force(GameSession s, Boss b, BossPattern p) {
        try {
            Method m = Boss.class.getDeclaredMethod("startPattern", BossPattern.class, CombatContext.class);
            m.setAccessible(true);
            m.invoke(b, p, s);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Boss enterBoss(GameSession s, String roomId) {
        Room r = s.roomById.get(roomId);
        s.unit.teleport(r.def.entry().x, r.def.entry().y);
        step(s, new Controls(), 2);
        if (s.awaitingBossConfirm()) s.answerBossConfirm(true);
        step(s, new Controls(), 50);
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        return s.activeBoss();
    }

    private static Boss phase2(GameSession s) {
        TestFiles.toArena(s);
        Boss k = s.demonKing;
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        k.damage(k.maxHp() * 2, 0, 0, 0);
        step(s, new Controls(), (int) (60 * Boss.TRANSITION_TIME) + 10);
        assertEquals(2, k.phase);
        s.projectiles.clear();
        s.hazards.clear();
        return k;
    }

    // =================================================================== 수치

    @Test
    void 세_번째_보스_최대_체력은_150이고_마왕_체력이_늘었다() {
        GameSession s = TestFiles.session(100);
        assertEquals(150, s.balance.dungeon("top").maxHpGain);
        assertEquals(15000, s.balance.finalBoss.hp);
        for (String d : new String[]{"left", "right", "top"}) {
            s.absorbService.absorbBoss(s.balance.dungeon(d), s.hero, s.progress, s.inventory, s.random);
        }
        assertEquals(600, s.hero.maxHp());
    }

    // =================================================================== 좀비킹

    @Test
    void 좀비킹은_세_번째_돌진_뒤_2점5초_기절한다() {
        GameSession s = TestFiles.session(101);
        Boss k = enterBoss(s, "L5");
        s.hero.invulnerable = true;
        force(s, k, BossPattern.RUSH);
        boolean stunned = false;
        for (int i = 0; i < 60 * 8 && !stunned; i++) {
            step(s, new Controls(), 1);
            stunned = k.stunned;
        }
        assertTrue(stunned, "세 번 돌진한 뒤 기절");
        assertEquals(2.5f, k.stagger, 0.05f);
        float x = k.footX(), y = k.footY();
        step(s, new Controls(), 60 * 2);
        assertTrue(k.stagger > 0);
        assertEquals(x, k.footX(), 0.01f, "기절 중에는 움직이지 않는다");
        assertEquals(y, k.footY(), 0.01f);
        step(s, new Controls(), 40);
        assertFalse(k.stunned);
    }

    // =================================================================== 마룡

    @Test
    void 마룡은_뒤돌아선_뒤_빙글빙글_돌며_바라보는_쪽으로_화염_구슬을_뿌린다() {
        GameSession s = TestFiles.session(102);
        Boss d = enterBoss(s, "T7");
        s.hero.invulnerable = true;
        s.projectiles.clear();
        force(s, d, BossPattern.FIRE_SPIN);
        step(s, new Controls(), 10);
        assertEquals(Boss.Pose.BACK, d.pose, "먼저 뒤돌아선다");
        assertTrue(s.projectiles.isEmpty(), "뒤돌아서는 동안은 아직 안 뿌린다");
        step(s, new Controls(), 40);
        assertEquals(Boss.Pose.SPIN, d.pose);
        float a1 = d.spinAngle;
        step(s, new Controls(), 6);
        assertTrue(Math.abs(a1 - d.spinAngle) > 0.1f, "빙글빙글 돈다");
        assertTrue(s.projectiles.stream().anyMatch(p -> "fire_orb".equals(p.sprite)), "화염 구슬");
        // 막 나온 구슬은 지금 바라보는 쪽 (또는 꼬리 쪽)으로 날아간다
        boolean aligned = false;
        for (int i = 0; i < 12 && !aligned; i++) {
            int before = s.projectiles.size();
            step(s, new Controls(), 1);
            if (s.projectiles.size() <= before) continue;
            double face = Math.toRadians(d.spinAngle);
            float fx = (float) Math.sin(face), fy = (float) -Math.cos(face);
            for (Projectile p : s.projectiles.subList(before, s.projectiles.size())) {
                float len = (float) Math.hypot(p.vx, p.vy);
                if (Math.abs((p.vx * fx + p.vy * fy) / len) > 0.9f) aligned = true;
            }
        }
        assertTrue(aligned, "바라보는 쪽으로 뿌린다");
    }

    @Test
    void 바람기둥에_맞으면_빙글빙글_날아간다() {
        GameSession s = TestFiles.session(103);
        s.hero.setMaxHp(9999);
        s.hero.fullHeal();
        float x = s.unit.centerX(), y = s.unit.centerY() + 8;
        s.projectiles.add(new Projectile(x - 24, y, 165, 0, 12, 0, "tornado", 3f).knock(900).boss());
        boolean flying = false;
        for (int i = 0; i < 30 && !flying; i++) {
            step(s, new Controls(), 1);
            flying = s.unit.tumble > 0;
        }
        assertTrue(s.unit.tumble > 0, "날아가는 중");
        assertFalse(s.unit.free(), "날아가는 동안은 행동 못 함");
        assertEquals(1, s.unit.tumbleDir, 0.01f);
        step(s, new Controls(), 60);
        assertEquals(0, s.unit.tumble, 0.001f);
    }

    // =================================================================== 마왕

    @Test
    void 심연의_눈이_여러_개_떠서_차례로_빛을_뿜는다() {
        GameSession s = TestFiles.session(104);
        Boss k = phase2(s);
        force(s, k, BossPattern.EYES);
        step(s, new Controls(), 30);
        List<Hazard> eyes = s.hazards.stream().filter(h -> h.type == Hazard.Type.EYE).toList();
        assertEquals(3, eyes.size());
        List<Float> warns = eyes.stream().map(h -> h.warning).sorted().toList();
        assertEquals(warns.get(0) + 0.9f, warns.get(1), 0.01f);
        assertEquals(warns.get(1) + 0.9f, warns.get(2), 0.01f);
    }

    @Test
    void 반전_마법진에서_나오면_5초_동안_좌우가_뒤바뀐다() {
        GameSession s = TestFiles.session(105);
        s.hero.setMaxHp(9999);
        s.hero.fullHeal();
        s.hazards.add(new Hazard(Hazard.Type.ABYSS_SIGIL, s.unit.footX(), s.unit.footY(), 40, 0, 0, 3f, 0));
        step(s, new Controls(), 5);
        assertTrue(s.inverted());
        s.hazards.clear();
        step(s, new Controls(), 1);
        assertFalse(s.inverted());
        assertEquals(GameSession.MIRROR_AFTER_SIGIL, s.mirrorTime(), 0.05f);
        // 오른쪽을 누르면 왼쪽으로 간다
        Controls c = new Controls();
        c.moveX = 1;
        float x0 = s.unit.footX();
        step(s, c, 20);
        assertTrue(s.unit.footX() < x0 - 10, "좌우 반전");
        c.moveX = 0;
        step(s, c, 60 * 5);
        assertEquals(0, s.mirrorTime(), 0.001f);
        c.moveX = 1;
        float x1 = s.unit.footX();
        step(s, c, 20);
        assertTrue(s.unit.footX() > x1 + 10, "5초 뒤에는 원래대로");
    }

    @Test
    void 공간_절단은_남은_전장의_20퍼센트를_잘라_지나갈_수_없는_공허로_만든다() {
        GameSession s = TestFiles.session(106);
        Boss k = phase2(s);
        Rectangle before = new Rectangle(s.field());
        float area0 = before.area();
        force(s, k, BossPattern.SPACE_REND);
        assertNotNull(k.rendRect, "잘라낼 곳 예고");
        Rectangle cut = new Rectangle(k.rendRect);
        assertEquals(area0 * 0.2f, cut.area(), area0 * 0.01f);
        step(s, new Controls(), (int) (60 * (s.balance.finalBoss.rendWarning + 1f)));
        assertEquals(1, s.rends.size());
        assertEquals(area0 * 0.8f, s.field().area(), area0 * 0.01f);
        assertFalse(cut.overlaps(s.field()) && s.field().area() > 0 && intersectArea(cut, s.field()) > 1);
        // 잘린 곳으로는 걸어 들어갈 수 없다
        Rectangle probe = new Rectangle(cut.x + cut.width / 2 - 7, cut.y + cut.height / 2 - 4, 14, 8);
        assertTrue(s.collision.blocked(probe));
        // 다음 절단은 남은 곳의 20%
        float area1 = s.field().area();
        force(s, k, BossPattern.SPACE_REND);
        assertEquals(area1 * 0.2f, k.rendRect.area(), area1 * 0.01f);
        // 공허 가장자리에 닿으면 지속 피해
        step(s, new Controls(), (int) (60 * (s.balance.finalBoss.rendWarning + 1f)));
        Rectangle r = s.rends.get(s.rends.size() - 1);
        Rectangle f = s.field();
        float ex = r.x + r.width <= f.x + 1 ? f.x + 6 : (r.x >= f.x + f.width - 1 ? f.x + f.width - 10 : f.x + f.width / 2);
        float ey = r.y + r.height <= f.y + 1 ? f.y + 2 : (r.y >= f.y + f.height - 1 ? f.y + f.height - 12 : f.y + f.height / 2);
        s.hazards.clear();
        s.projectiles.clear();
        k.teleport(f.x + f.width / 2, f.y + f.height / 2);
        s.unit.teleport(ex, ey);
        s.hero.fullHeal();
        float hp0 = s.hero.hpExact();
        step(s, new Controls(), 10);
        assertTrue(s.hero.hpExact() < hp0, "공허에 닿으면 피해");
        assertEquals(2, s.rends.size());
        assertEquals(9f, s.rendReach(), 0.01f, "찢길수록 공허가 번진다 (5 → 9px)");
    }

    private static float intersectArea(Rectangle a, Rectangle b) {
        float w = Math.min(a.x + a.width, b.x + b.width) - Math.max(a.x, b.x);
        float h = Math.min(a.y + a.height, b.y + b.height) - Math.max(a.y, b.y);
        return w > 0 && h > 0 ? w * h : 0;
    }

    @Test
    void 공간_절단은_본모습에서_스스로_1분_안팎마다_쓴다() {
        GameSession s = TestFiles.session(107);
        Boss k = phase2(s);
        s.hero.invulnerable = true;
        boolean used = false;
        for (int i = 0; i < 60 * 30 && !used; i++) {
            step(s, new Controls(), 1);
            used = k.currentPattern() == BossPattern.SPACE_REND || !s.rends.isEmpty();
        }
        assertTrue(used, "본모습이 된 뒤 rendFirst초 안팎에 처음 쓴다");
        assertTrue(s.balance.finalBoss.rendIntervalMin >= 50 && s.balance.finalBoss.rendIntervalMax <= 70);
    }

    @Test
    void 마왕을_쓰러뜨리면_심연에_캠프행_포탈이_열린다() {
        // (2026-10-13 결정) 캠프가 마왕 맵으로 물드는 연출은 빼고, 원래대로 땅이 갈라져 떨어지는 따로 된 심연에서 싸운다
        GameSession s = TestFiles.session(108);
        List<GameEvent> ev = new ArrayList<>();
        s.addListener(ev::add);
        Boss k = phase2(s);
        s.hero.invulnerable = true;
        k.damage(k.maxHp() * 3, 0, 0, 0);
        step(s, new Controls(), (int) (60 * (Boss.FINAL_DEATH_TIME + 0.2f)));
        assertTrue(s.progress.finalDefeated());
        assertTrue(s.inArena(), "심연에 남아 있다");
        assertTrue(s.arenaPortal.active(), "캠프로 가는 포탈");
        assertTrue(s.rends.isEmpty(), "찢긴 공간도 사라진다");
        assertTrue(ev.stream().anyMatch(e -> e.type() == GameEvent.Type.FINAL_DEFEATED));
        assertTrue(s.map.arena.overlaps(s.unit.body), "심연은 캠프와 따로 떨어진 구역");
        assertFalse(s.map.arena.overlaps(s.map.camp));
    }

    // =================================================================== 침대·보물상자·강화

    @Test
    void 침대에서_E를_누르면_누워_자고_중간에_쉬고_일어난다() {
        GameSession s = TestFiles.session(109);
        s.hero.damage(50);
        s.unit.teleport(s.map.bed.x + s.map.bed.width / 2, s.map.bed.y - 6);
        step(s, new Controls(), 2);
        assertEquals(GameSession.Interact.BED, s.focus());
        Controls c = new Controls();
        c.interact = true;
        step(s, c, 1);
        assertTrue(s.sleeping());
        float x = s.unit.footX();
        c.moveX = 1;
        step(s, c, 30);
        assertEquals(x, s.unit.footX(), 0.01f, "자는 동안은 움직이지 않는다");
        c.moveX = 0;
        step(s, c, (int) (60 * GameSession.SLEEP_REST_AT));
        assertEquals(s.hero.maxHp(), s.hero.hp(), "푹 쉬었다");
        step(s, c, (int) (60 * GameSession.SLEEP_TIME));
        assertFalse(s.sleeping(), "일어났다");
    }

    @Test
    void 보물상자는_저장하고_불러와도_남아_있다() {
        GameSession a = TestFiles.session(110);
        Boss b = enterBoss(a, "L5");
        b.damage(999999, 0, 0, 0);
        step(a, new Controls(), 160);
        assertEquals(1, a.chests.size());
        TreasureChest c = a.chests.get(0);
        SaveData d = SaveMapper.fromJson(SaveMapper.toJson(SaveMapper.capture(a)));
        GameSession b2 = TestFiles.session(111);
        SaveMapper.restore(d, b2);
        assertEquals(1, b2.chests.size());
        assertEquals(c.ore, b2.chests.get(0).ore);
        assertTrue(b2.chests.get(0).essence);
        assertTrue(b2.orbDrops.isEmpty(), "정수는 상자 안에 있으니 따로 떨어뜨리지 않는다");
    }

    @Test
    void 개발자_도구_강화_성공_100퍼센트() {
        GameSession s = TestFiles.session(112);
        Weapon w = s.inventory.add(s.balance.weapon("blackiron"));
        s.inventory.equip(w);
        assertTrue(s.toggleSureEnhance());
        s.toggleInfiniteOre();
        assertEquals(100.0, s.previewEnhance().odds().success(), 0.001);
        for (int i = 0; i < 10; i++) {
            EnhanceResult r = s.enhanceEquipped();
            assertEquals(EnhanceOutcome.SUCCESS, r.outcome());
        }
        assertEquals(10, s.inventory.equipped().level());
        assertFalse(s.toggleSureEnhance());
    }

    // =================================================================== 해골 기사·해골 병사 (2026-10-13)

    /** 잡몹방 R1에 들어가 원래 몬스터를 치우고 하나만 세운다. */
    private static com.yongsa.combat.Monster lone(GameSession s, String id, float dx) {
        Room r = s.roomById.get("R1");
        s.progress.restore(java.util.List.of("left"), java.util.List.of(), false, false, true, true);
        s.refreshGates();
        s.unit.teleport(r.def.entry().x, r.def.entry().y);
        step(s, new Controls(), 50);
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        r.monsters.clear();
        var m = new com.yongsa.combat.Monster(s.balance.monster(id), r, s.unit.footX() + dx, s.unit.footY(), 1f, 1f);
        r.monsters.add(m);
        step(s, new Controls(), (int) (60 * com.yongsa.combat.Monster.SPAWN_TIME) + 2);
        return m;
    }

    @Test
    void 해골_기사는_찌르지_않고_제자리에서_크게_벤다() {
        GameSession s = TestFiles.session(120);
        var k = lone(s, "skeletonknight", 40);
        assertTrue(k.def.slashes());
        float hp0 = s.hero.hpExact();
        boolean sawSlash = false;
        float x0 = 0, y0 = 0;
        for (int i = 0; i < 240; i++) {
            step(s, new Controls(), 1);
            if (k.state() == com.yongsa.combat.Monster.State.ATTACK && !sawSlash) {
                sawSlash = true;
                x0 = k.footX();
                y0 = k.footY();
                assertTrue(s.effects.stream().anyMatch(e -> e.type == com.yongsa.combat.Effect.Type.MOB_SLASH), "베기 궤적");
            }
            if (sawSlash && k.state() != com.yongsa.combat.Monster.State.ATTACK) break;
        }
        assertTrue(sawSlash);
        assertEquals(x0, k.footX(), 2f, "찌르듯 돌진하지 않는다");
        assertEquals(y0, k.footY(), 2f);
        assertTrue(s.hero.hpExact() < hp0, "벤 범위 안이면 맞는다");
    }

    @Test
    void 해골_병사는_앞으로_내지르며_찌른다() {
        GameSession s = TestFiles.session(121);
        var m = lone(s, "minion", 40);
        assertFalse(m.def.slashes());
        float x0 = Float.NaN;
        for (int i = 0; i < 240; i++) {
            step(s, new Controls(), 1);
            if (m.state() == com.yongsa.combat.Monster.State.ATTACK && Float.isNaN(x0)) {
                x0 = m.footX();
                assertTrue(s.effects.stream().anyMatch(e -> e.type == com.yongsa.combat.Effect.Type.MOB_THRUST), "찌르기 궤적");
            }
            if (!Float.isNaN(x0) && m.state() != com.yongsa.combat.Monster.State.ATTACK) break;
        }
        assertFalse(Float.isNaN(x0));
        assertTrue(m.footX() < x0 - 3 || s.hero.hpExact() < s.hero.maxHp(), "용사 쪽으로 내지른다");
    }

    @Test
    void 해골_기사는_앞에서_오는_공격을_막고_반격한다_등_뒤는_못_막는다() {
        GameSession s = TestFiles.session(122);
        var k = lone(s, "skeletonknight", 30);
        k.def.blockChance = 1f;    // 이 테스트에서는 항상 막게
        s.hero.invulnerable = true;
        k.faceLeft = true;          // 용사(왼쪽)를 바라본다
        int hp0 = k.hp();
        s.unit.lookAt(1, 0);
        // 기사가 쫓는 중일 때 정면에서 벤다
        for (int i = 0; i < 120 && k.state() != com.yongsa.combat.Monster.State.CHASE; i++) step(s, new Controls(), 1);
        Controls c = new Controls();
        c.attack = true;
        step(s, c, 1);
        step(s, new Controls(), 3);
        assertEquals(hp0, k.hp(), "막았다");
        assertTrue(s.texts.stream().anyMatch(t -> t.text.equals("막음!")));
        assertTrue(k.state() == com.yongsa.combat.Monster.State.WINDUP || k.state() == com.yongsa.combat.Monster.State.ATTACK, "곧바로 반격");
        // 등 뒤에서는 못 막는다
        step(s, new Controls(), 90);
        k.faceLeft = false;
        k.teleport(s.unit.footX() + 24, s.unit.footY());
        k.faceLeft = false;  // 오른쪽(용사 반대)을 본다
        int hp1 = k.hp();
        Controls c2 = new Controls();
        c2.attack = true;
        s.unit.lookAt(1, 0);
        boolean hit = false;
        for (int i = 0; i < 40 && !hit; i++) {
            k.faceLeft = false;
            step(s, c2, 1);
            hit = k.hp() < hp1;
            c2.attack = (i % 10 == 9);
        }
        assertTrue(hit, "등 뒤에서는 벨 수 있다");
    }
}
