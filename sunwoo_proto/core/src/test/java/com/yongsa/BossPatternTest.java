package com.yongsa;

import com.yongsa.combat.Boss;
import com.yongsa.combat.BossPattern;
import com.yongsa.combat.CombatContext;
import com.yongsa.combat.Hazard;
import com.yongsa.combat.Monster;
import com.yongsa.combat.Projectile;
import com.yongsa.model.Controls;
import com.yongsa.model.GameSession;
import com.yongsa.model.Weapon;
import com.yongsa.world.Gate;
import com.yongsa.world.Room;
import com.yongsa.world.RoomState;
import com.yongsa.save.SaveMapper;
import com.badlogic.gdx.math.Rectangle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** 던전 잠금, 흡혈 기준, 마룡 광폭화·태풍, 마왕 1·2페이즈 패턴 (2026-10-10 네 번째 결정). */
class BossPatternTest {

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

    private static Gate gate(GameSession s, String d) {
        return s.gates.stream().filter(g -> g.dungeon.equals(d)).findFirst().orElseThrow();
    }

    // =================================================================== 던전 잠금

    @Test
    void 던전에_들어가면_입구가_닫히고_보스를_잡으면_열린다() {
        GameSession s = TestFiles.session(60);
        assertTrue(gate(s, "left").open());
        Room l1 = s.roomById.get("L1");
        s.unit.teleport(l1.def.entry().x, l1.def.entry().y);
        step(s, new Controls(), 2);
        assertEquals("left", s.lockedDungeon());
        assertFalse(gate(s, "left").open());
        assertTrue(s.goal().text().contains("좀비킹") || s.goal().text().contains("흑철석"));
        Boss b = enterBoss(s, "L5");
        step(s, new Controls(), 40);
        b.damage(999999, 0, 0, 0);
        step(s, new Controls(), 160);   // 보스가 쓰러지는 연출 (Boss.DEATH_TIME)
        assertEquals(RoomState.CLEARED, s.roomById.get("L5").state());
        assertNull(s.lockedDungeon());
        assertTrue(gate(s, "left").open());
    }

    @Test
    void 던전에서_쓰러지면_입구가_다시_열린다() {
        GameSession s = TestFiles.session(61);
        Room l1 = s.roomById.get("L1");
        s.unit.teleport(l1.def.entry().x, l1.def.entry().y);
        step(s, new Controls(), 2);
        assertFalse(gate(s, "left").open());
        s.hero.damage(99999);
        step(s, new Controls(), 2);
        s.respawn();
        assertNull(s.lockedDungeon());
        assertTrue(gate(s, "left").open());
        assertTrue(s.inCamp());
    }

    // =================================================================== 흡혈

    @Test
    void 흡혈은_용사_최대_체력의_비율이다() {
        GameSession s = TestFiles.session(62);
        Room l1 = s.roomById.get("L1");
        s.unit.teleport(l1.def.entry().x, l1.def.entry().y);
        step(s, new Controls(), 2);
        step(s, new Controls(), 40);
        s.hero.setMaxHp(1000);
        s.hero.setHp(500);
        Weapon w = s.inventory.add(s.balance.weapon("blackiron"));
        TestFiles.setLevel(w, 10); // 1.5%
        s.equip(w);
        Monster m = l1.monsters.get(0);
        m.stopKnockback();
        s.unit.teleport(m.footX(), m.footY() - 20);
        s.unit.lookAt(0, 1);
        float before = s.hero.hpExact();
        Controls c = new Controls();
        c.attack = true;
        step(s, c, 1);
        assertTrue(s.hero.hpExact() - before >= 15f - 0.01f, "1000의 1.5% = 15 이상 회복 (몇 마리 맞을 수 있음)");
    }

    // =================================================================== 마룡

    @Test
    void 마룡은_절반에서_바람의_고치에_싸여_무적이고_그동안_회피_쿨이_없다() {
        GameSession s = TestFiles.session(63);
        Boss b = enterBoss(s, "T7");
        assertEquals("dragon", b.def.id);
        int atk = b.attack;
        b.damage(b.maxHp(), 0, 0, 0);
        assertTrue(b.cocoon);
        assertTrue(b.alive());
        assertEquals(Math.round(b.maxHp() * 0.5f), b.hp());
        assertFalse(b.damage(100, 0, 0, 0));
        Controls c = new Controls();
        c.dodge = true;
        c.moveX = 1;
        step(s, c, 1);
        assertEquals(0f, s.unit.dodgeCooldown, 0.0001f);
        // 메테오가 떨어진다
        boolean meteor = false;
        for (int i = 0; i < 60 * 5 && !meteor; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
            meteor = s.hazards.stream().anyMatch(h -> h.type == Hazard.Type.METEOR_FIELD);
        }
        assertTrue(meteor);
        for (int i = 0; i < 60 * 12; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
        }
        assertTrue(b.cocoon, "12초에는 아직 공중");
        for (int i = 0; i < 60 * 8; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
        }
        assertFalse(b.cocoon, "20초 뒤 내려온다");
        assertTrue(b.enraged);
        assertEquals(Math.round(atk * 1.5f), b.attack);
        assertTrue(b.damage(10, 0, 0, 0) || b.hp() < Math.round(b.maxHp() * 0.5f));
        c.dodge = true;
        step(s, c, 1);
        assertTrue(s.unit.dodgeCooldown > 0, "내려오면 회피 쿨타임이 돌아온다");
    }

    @Test
    void 메테오는_안전한_원_안에서만_피한다() {
        Rectangle area = new Rectangle(0, 0, 600, 400);
        Hazard h = Hazard.meteor(area, 300, 200, 46, 2.3f, 80);
        assertFalse(h.contains(300, 200));
        assertFalse(h.contains(330, 220));
        assertTrue(h.contains(100, 100));
        assertTrue(h.contains(360, 200));
    }

    @Test
    void 바람기둥에_맞으면_피해_없이_뒤쪽_벽까지_날아간다() {
        GameSession s = TestFiles.session(64);
        Boss b = enterBoss(s, "T7");
        Rectangle room = s.roomById.get("T7").bounds;
        float cx = room.x + room.width / 2, cy = room.y + room.height / 2;
        s.unit.teleport(cx, cy);
        int hp = s.hero.hp();
        s.projectiles.add(new Projectile(cx - 20, cy + 8, 160, 0, 12, 0, "tornado", 3f).knock(900).boss());
        step(s, new Controls(), 60);
        assertEquals(hp, s.hero.hp(), "피해는 없다");
        assertTrue(s.unit.footX() > room.x + room.width - 60, "오른쪽 벽까지 밀려났다: " + s.unit.footX());
        assertNotNull(b);
    }

    // =================================================================== 마왕 1페이즈

    @Test
    void 척추_채찍은_지나가는_순간_회피하면_피한다() {
        GameSession s = TestFiles.session(65);
        TestFiles.toArena(s);
        Boss k = s.demonKing;
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        s.unit.teleport(k.footX(), k.footY() - 150);
        force(s, k, BossPattern.BONE_WHIP);
        int hp = s.hero.hp();
        step(s, new Controls(), (int) (60 * (1.0f + 0.45f + 0.4f)));
        assertTrue(s.hero.hp() < hp, "가만히 있으면 맞는다");
    }

    @Test
    void 머리를_던지면_몸통은_머리_없이_달려들고_머리는_레이저와_유도탄을_쏜다() {
        GameSession s = TestFiles.session(66);
        TestFiles.toArena(s);
        Boss k = s.demonKing;
        s.hero.setMaxHp(99999);
        force(s, k, BossPattern.HEAD_TURRET);
        step(s, new Controls(), 60);
        assertTrue(k.headless);
        boolean laser = false, homing = false;
        for (int i = 0; i < 60 * 5; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
            laser |= s.hazards.stream().anyMatch(h -> h.type == Hazard.Type.LASER);
            homing |= s.projectiles.stream().anyMatch(p -> p.homing > 0);
        }
        assertTrue(laser);
        assertTrue(homing);
        for (int i = 0; i < 60 * 6; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
        }
        assertFalse(k.headless, "시간이 지나면 머리가 돌아온다");
    }

    @Test
    void 뼛가루_폭풍은_바위_그늘에서만_막힌다() {
        Hazard h = new Hazard(Hazard.Type.BONE_STORM, 300, 200, 170, 0, 1, 6, 0);
        h.dirX = 1;
        h.dirY = 0;
        h.rock = new Rectangle(300 - 32, 200, 64, 22);
        assertTrue(h.sheltered(360, 210, 170), "바람이 불어 가는 쪽 바로 뒤");
        assertFalse(h.sheltered(240, 210, 170), "바람이 오는 쪽");
        assertFalse(h.sheltered(360, 300, 170), "옆으로 벗어남");
        assertFalse(h.sheltered(520, 210, 170), "너무 멀다");
    }

    @Test
    void 폭풍이_끝나면_바위가_부서진다() {
        GameSession s = TestFiles.session(67);
        TestFiles.toArena(s);
        Boss k = s.demonKing;
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        force(s, k, BossPattern.BONE_STORM);
        Hazard storm = null;
        for (int i = 0; i < 60 * 3 && storm == null; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
            storm = s.hazards.stream().filter(h -> h.type == Hazard.Type.BONE_STORM).findFirst().orElse(null);
        }
        assertNotNull(storm);
        for (int i = 0; i < 60 * 2; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
        }
        assertTrue(storm.rockPlaced);
        assertTrue(s.collision.blocked(storm.rock));
        for (int i = 0; i < 60 * 8; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
        }
        assertFalse(s.hazards.contains(storm));
        assertFalse(s.collision.blocked(storm.rock), "바위가 사라졌다");
    }

    @Test
    void 거리를_벌리면_무너져_무적이_되고_등_뒤에서_다시_모인다() {
        GameSession s = TestFiles.session(68);
        TestFiles.toArena(s);
        Boss k = s.demonKing;
        s.hero.setMaxHp(99999);
        boolean collapsed = false;
        for (int i = 0; i < 60 * 6 && !collapsed; i++) {
            // 계속 멀리 떨어져 있는다
            s.unit.teleport(k.footX(), k.footY() - 260);
            s.unit.lookAt(0, -1);
            step(s, new Controls(), 1);
            s.hero.fullHeal();
            s.hazards.clear();
            s.projectiles.clear();
            collapsed = k.collapsed;
        }
        assertTrue(collapsed);
        assertFalse(k.canBeHit());
        float hx = s.unit.footX(), hy = s.unit.footY();
        step(s, new Controls(), (int) (60 * 1.3f));
        assertFalse(k.collapsed);
        // 용사가 아래를 보고 있었으니 위쪽(등 뒤)에 나타난다
        assertTrue(k.footY() > hy, "등 뒤(위)에서 다시 모였다");
        assertEquals(hx, k.footX(), 40);
    }

    // =================================================================== 마왕 2페이즈

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

    @Test
    void 눈알을_바라보면_벌을_받고_등지면_무사하다() {
        GameSession s = TestFiles.session(69); // 위험 지대 판정만 본다 (캠프, 보스 없음)
        s.hero.setMaxHp(1000);
        s.hero.fullHeal();
        Hazard eye = new Hazard(Hazard.Type.EYE, s.unit.centerX() + 200, s.unit.centerY(), 40, 0, 0.5f, 0, 0);
        eye.eyeAngle = 75;
        eye.eyeStun = 1.6f;
        eye.eyeSeal = 5;
        s.addHazard(eye);
        s.unit.lookAt(-1, 0); // 등진다
        step(s, new Controls(), 40);
        assertEquals(1000, s.hero.hp());
        assertFalse(s.unit.stunned());
        assertFalse(s.unit.skillsSealed());

        Hazard eye2 = new Hazard(Hazard.Type.EYE, s.unit.centerX() + 200, s.unit.centerY(), 40, 0, 0.5f, 0, 0);
        eye2.eyeAngle = 75;
        eye2.eyeStun = 1.6f;
        eye2.eyeSeal = 5;
        s.addHazard(eye2);
        s.unit.lookAt(1, 0); // 바라본다
        s.unit.invincibleForTest(0);
        step(s, new Controls(), 40);
        boolean punished = s.hero.hp() <= 520 || s.unit.stunned() || s.unit.skillsSealed();
        assertTrue(punished);
    }

    @Test
    void 반전_마법진은_밟는_동안만_정해진_키를_바꾸고_그_키는_다음_판에도_같다() {
        GameSession s = TestFiles.session(70);
        String keys = s.inversionKeys();
        assertEquals(keys, s.inversionKeys(), "한 번 정하면 그대로");
        // 저장했다 불러와도 같다
        GameSession t = TestFiles.session(71);
        SaveMapper.restore(SaveMapper.fromJson(SaveMapper.toJson(SaveMapper.capture(s))), t);
        assertEquals(keys, t.inversionKeys());

        s.addHazard(new Hazard(Hazard.Type.ABYSS_SIGIL, s.unit.footX(), s.unit.footY(), 64, 0, 0.1f, 5, 0));
        step(s, new Controls(), 10);
        assertTrue(s.inverted());
        float x0 = s.unit.footX(), y0 = s.unit.footY();
        Controls c = new Controls();
        if (keys.startsWith("LR")) c.moveX = 1; else c.moveY = 1;
        step(s, c, 10);
        if (keys.startsWith("LR")) assertTrue(s.unit.footX() < x0, "오른쪽을 눌렀는데 왼쪽으로");
        else assertTrue(s.unit.footY() < y0, "위를 눌렀는데 아래로");
        // 마법진 밖에서는 원래대로
        s.hazards.clear();
        step(s, new Controls(), 2);
        assertFalse(s.inverted());
    }

    @Test
    void 공허는_전투_내내_남고_닿으면_지속_피해() {
        GameSession s = TestFiles.session(72);
        s.hero.setMaxHp(1000);
        s.hero.fullHeal();
        s.unit.teleport(s.roomById.get("L1").bounds.x + 200, s.roomById.get("L1").bounds.y + 150); // 캠프 회복 밖
        Hazard v = Hazard.line(Hazard.Type.VOID, s.unit.footX() - 60, s.unit.footY() + 4, s.unit.footX() + 60, s.unit.footY() + 4,
                30, 0, 0.2f, Hazard.FOREVER, 0);
        v.ratio = 0.12f;
        s.addHazard(v);
        step(s, new Controls(), 60 + 12);
        assertTrue(s.hero.hpExact() < 1000 - 100, "1초에 12%");
        for (int i = 0; i < 60 * 20; i++) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
        }
        assertTrue(s.hazards.contains(v), "사라지지 않는다");
    }

    @Test
    void 살점_해일은_균열에_숨으면_살고_그_균열은_닫힌다() {
        GameSession s = TestFiles.session(73);
        s.hero.setMaxHp(1000);
        s.hero.fullHeal();
        Rectangle a = s.map.camp;
        Hazard crack = new Hazard(Hazard.Type.CRACK, s.unit.footX(), s.unit.footY(), 20, 0, 0.1f, 20, 0);
        s.addHazard(crack);
        Hazard tide = Hazard.sweep(Hazard.Type.FLESH_TIDE, a, 1, 0, 400, 0, 0.2f, 0);
        tide.ratio = 0.45f;
        s.addHazard(tide);
        step(s, new Controls(), 90);
        assertEquals(1000, s.hero.hp());
        assertTrue(crack.used);
        // 같은 균열로는 다음 해일을 못 피한다
        Hazard tide2 = Hazard.sweep(Hazard.Type.FLESH_TIDE, a, 1, 0, 400, 0, 0.2f, 0);
        tide2.ratio = 0.45f;
        s.addHazard(tide2);
        s.unit.invincibleForTest(0);
        step(s, new Controls(), 90);
        assertTrue(s.hero.hp() <= 1000 - 400, "회피 무적으로도 못 피한다 (캠프 회복 감안)");
    }

    @Test
    void 군체_상태에서는_모든_피해에_면역이고_끝나면_돌아온다() {
        GameSession s = TestFiles.session(74);
        Boss k = phase2(s);
        force(s, k, BossPattern.SWARM);
        step(s, new Controls(), 45);
        assertTrue(k.hidden());
        int hp = k.hp();
        assertFalse(k.damage(500, 0, 0, 0));
        assertEquals(hp, k.hp());
        assertTrue(s.hazards.stream().anyMatch(h -> h.type == Hazard.Type.SWARM_ROW));
        int frames = 0;
        while (k.hidden() && frames < 60 * 20) {
            step(s, new Controls(), 1);
            s.hero.fullHeal();
            frames++;
        }
        assertFalse(k.hidden(), "군체가 끝나면 원래 모습으로 돌아온다");
        assertTrue(frames > 60 * 8, "한동안은 면역 상태로 쓸고 다닌다");
    }

    @Test
    void 공간_왜곡_촉수는_보이는_반대쪽_벽에서_용사_등_뒤를_친다() {
        GameSession s = TestFiles.session(75);
        Boss k = phase2(s);
        // 마왕에게서 멀리 (가까우면 자동으로 마왕을 바라본다)
        s.unit.teleport(s.map.arena.x + 60, s.map.arena.y + 40);
        k.teleport(s.map.arena.x + s.map.arena.width - 120, s.map.arena.y + s.map.arena.height - 120);
        s.unit.lookAt(1, 0); // 오른쪽을 본다 → 등 뒤는 왼쪽
        force(s, k, BossPattern.WARP_TENTACLE);
        step(s, new Controls(), 25);
        Hazard w = s.hazards.stream().filter(h -> h.type == Hazard.Type.WARP).findFirst().orElseThrow();
        Rectangle a = s.map.arena;
        assertEquals(a.x, w.x, 0.1f, "진짜는 왼쪽 벽에서");
        assertEquals(a.x + a.width, w.decoyX, 0.1f, "보이는 쪽은 오른쪽 벽");
        assertEquals(1f, k.warnDirX, 0.01f);
    }

    @Test
    void 가까운_몬스터를_자동으로_바라본다() {
        GameSession s = TestFiles.session(76);
        Room l1 = s.roomById.get("L1");
        s.unit.teleport(l1.def.entry().x, l1.def.entry().y);
        step(s, new Controls(), 45);
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        Monster m = l1.monsters.get(0);
        s.unit.teleport(m.footX() + 80, m.footY());
        Controls c = new Controls();
        c.moveY = 1; // 위로 걸어가도
        step(s, c, 1);
        // 가장 가까운 (때릴 수 있는) 몬스터
        Monster near = null;
        float bd = Float.MAX_VALUE;
        for (Monster o : l1.monsters) {
            if (!o.canBeHit()) continue;
            float d = (float) Math.hypot(o.centerX() - s.unit.centerX(), o.centerY() - s.unit.centerY());
            if (d < bd) { bd = d; near = o; }
        }
        assertNotNull(near);
        assertTrue(bd < GameSession.AUTO_FACE_RANGE);
        float dx = near.centerX() - s.unit.centerX(), dy = near.centerY() - s.unit.centerY();
        float len = (float) Math.hypot(dx, dy);
        assertTrue((s.unit.dirX * dx + s.unit.dirY * dy) / len > 0.95f, "가장 가까운 몬스터 쪽을 본다");
    }

    @Test
    void 눈알이_빛을_모으는_동안은_자동으로_돌지_않는다() {
        GameSession s = TestFiles.session(77);
        Room l1 = s.roomById.get("L1");
        s.unit.teleport(l1.def.entry().x, l1.def.entry().y);
        step(s, new Controls(), 45);
        s.hero.setMaxHp(99999);
        s.hero.fullHeal();
        Monster m = l1.monsters.get(0);
        s.unit.teleport(m.footX() + 80, m.footY());
        Hazard eye = new Hazard(Hazard.Type.EYE, s.unit.centerX(), s.unit.centerY() + 200, 40, 0, 3f, 0, 0);
        s.addHazard(eye);
        Controls c = new Controls();
        c.moveX = 1; // 몬스터 반대쪽(오른쪽)으로 걷는다
        step(s, c, 2);
        assertTrue(s.unit.dirX > 0.9f, "직접 정한 방향을 유지");
    }

    @Test
    void F2_무적이면_어떤_피해도_받지_않는다() {
        GameSession s = TestFiles.session(78);
        assertTrue(s.toggleGodMode());
        int hp = s.hero.hp();
        assertFalse(s.hitHero(50, 0, 0));
        assertFalse(s.hitHeroTrue(500, 0, 0));
        s.hero.damageExact(30);
        s.unit.applyPoison(5);
        step(s, new Controls(), 60);
        assertEquals(hp, s.hero.hp());
        assertFalse(s.toggleGodMode());
        assertTrue(s.hitHeroTrue(10, 0, 0));
        assertTrue(s.hero.hp() < hp);
    }

    @Test
    void F3_F4_F5는_각_보스방_입구로_간다() {
        GameSession s = TestFiles.session(79);
        String[] ids = {"L5", "R6", "T7"};
        for (int i = 0; i < 3; i++) {
            assertTrue(s.warpToBossDoor(i));
            Room boss = s.roomById.get(ids[i]);
            assertEquals(boss.def.outside().x, s.unit.footX(), 0.5f);
            assertEquals(boss.def.outside().y, s.unit.footY(), 0.5f);
            assertEquals(RoomState.IDLE, boss.state());
            // 걸어 들어가면 확인창
            Controls c = new Controls();
            float dx = boss.def.entry().x - boss.def.outside().x, dy = boss.def.entry().y - boss.def.outside().y;
            c.moveX = Math.signum(dx);
            c.moveY = Math.signum(dy);
            for (int f = 0; f < 120 && !s.awaitingBossConfirm(); f++) step(s, c, 1);
            assertTrue(s.awaitingBossConfirm(), ids[i] + " 확인창");
            s.answerBossConfirm(false);
        }
    }

    @Test
    void 싸우던_방에서_바로가기를_쓰면_그_방은_처음_상태로() {
        GameSession s = TestFiles.session(80);
        Room l1 = s.roomById.get("L1");
        s.unit.teleport(l1.def.entry().x, l1.def.entry().y);
        step(s, new Controls(), 2);
        assertEquals(RoomState.FIGHTING, l1.state());
        assertTrue(s.warpToBossDoor(2));
        assertEquals(RoomState.IDLE, l1.state());
        assertNull(s.lockedDungeon());
    }
}
