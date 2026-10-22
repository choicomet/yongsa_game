package com.yongsa;

import com.yongsa.combat.Boss;
import com.yongsa.combat.Monster;
import com.yongsa.model.AbsorbService;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.save.SaveData;
import com.yongsa.save.SaveMapper;
import com.yongsa.world.Door;
import com.yongsa.world.Gate;
import com.yongsa.world.Room;
import com.yongsa.world.RoomState;
import com.yongsa.world.Vein;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** GameSession을 화면 없이 프레임 단위로 돌려 본다. */
class SessionTest {

    private static final float DT = 1f / 60f;

    private static void step(GameSession s, Controls c, int frames) {
        for (int i = 0; i < frames; i++) {
            s.update(DT, c);
            c.clearPresses();
        }
    }

    private static void enter(GameSession s, Room r) {
        s.unit.teleport(r.bounds.x + r.bounds.width / 2, r.bounds.y + r.bounds.height / 2 - 40);
    }

    /** 등장 연출(0.6초)이 끝날 때까지 기다린 뒤 모두 처치. */
    private static void killAll(GameSession s, Room r) {
        step(s, new Controls(), 40);
        for (Monster m : r.monsters) m.damage(999999, 0, 0, 0);
    }

    private static List<GameEvent> record(GameSession s) {
        List<GameEvent> events = new ArrayList<>();
        s.addListener(events::add);
        return events;
    }

    @Test
    void 잡몹방에_들어가면_문이_잠기고_다_잡으면_열린다() {
        GameSession s = TestFiles.session(1);
        Room l1 = s.roomById.get("L1");
        enter(s, l1);
        step(s, new Controls(), 2);
        assertEquals(RoomState.FIGHTING, l1.state());
        for (Door d : l1.doors) assertTrue(d.locked());
        step(s, new Controls(), 50);
        killAll(s, l1);
        step(s, new Controls(), 60);
        assertEquals(RoomState.CLEARED, l1.state());
        for (Door d : l1.doors) assertFalse(d.locked());
    }

    @Test
    void 공격_한_번은_같은_몬스터를_한_번만_때린다() {
        GameSession s = TestFiles.session(2);
        Room l1 = s.roomById.get("L1");
        enter(s, l1);
        step(s, new Controls(), 50);
        Monster m = l1.monsters.get(0);
        s.unit.teleport(m.footX(), m.footY() - 30);
        s.unit.lookAt(0, 1);
        int before = m.hp();
        Controls c = new Controls();
        c.attack = true;
        step(s, c, 1);
        int after = m.hp();
        assertTrue(before - after >= 9 && before - after <= 11, "봉인된 성검 10 ±10%: " + (before - after));
    }

    @Test
    void 광물방_광맥은_벽과_겹치지_않고_첫_방은_8개_이상() {
        for (int seed = 0; seed < 40; seed++) {
            GameSession s = TestFiles.session(seed);
            Room ore = s.roomById.get("L3");
            enter(s, ore);
            step(s, new Controls(), 1);
            assertTrue(ore.veinsGenerated);
            assertTrue(ore.veins.size() >= 6 && ore.veins.size() <= 10, "개수 " + ore.veins.size());
            int total = 0;
            for (Vein v : ore.veins) {
                total += v.amount();
                assertFalse(s.map.solidTile((int) (v.x / 32), (int) ((v.y + 4) / 32)));
                assertTrue(ore.bounds.contains(v.x, v.y));
            }
            assertTrue(total >= 8, "첫 광물방 합계 " + total);
        }
    }

    @Test
    void 나갔다_들어와도_광맥은_새로_생기지_않고_침대를_쓰면_다시_생긴다() {
        GameSession s = TestFiles.session(3);
        Room ore = s.roomById.get("L3");
        enter(s, ore);
        step(s, new Controls(), 1);
        Vein first = ore.veins.get(0);
        for (int i = 0; i < 3; i++) first.hit();
        s.unit.teleport(s.map.spawn.x, s.map.spawn.y);
        step(s, new Controls(), 2);
        enter(s, ore);
        step(s, new Controls(), 2);
        assertTrue(ore.veins.contains(first) && first.depleted());
        s.useBed();
        assertFalse(ore.veinsGenerated);
        assertFalse(ore.seen);
    }

    @Test
    void 보스를_처음_잡으면_체력이_오르고_구슬과_포탈이_나오고_잡몹이_한_번만_강해진다() {
        GameSession s = TestFiles.session(4);
        List<GameEvent> ev = record(s);
        Room l1 = s.roomById.get("L1");
        enter(s, l1);
        step(s, new Controls(), 2);
        int baseHp = l1.monsters.get(0).maxHp();
        int baseAtk = l1.monsters.get(0).attack;
        killAll(s, l1);
        step(s, new Controls(), 60);

        Room boss = s.roomById.get("L5");
        s.unit.teleport(boss.def.entry().x, boss.def.entry().y);
        step(s, new Controls(), 2);
        assertEquals(RoomState.FIGHTING, boss.state());
        step(s, new Controls(), 40);
        killAll(s, boss);
        step(s, new Controls(), 90); // 보스 쓰러지는 연출 1.1초

        assertEquals(250, s.hero.maxHp());
        assertEquals(5, s.inventory.ore("blackiron"));
        assertTrue(boss.portal.active());
        assertEquals(1, s.orbDrops.size());
        AbsorbService.BossAbsorb result = (AbsorbService.BossAbsorb) ev.stream()
                .filter(e -> e.type() == GameEvent.Type.BOSS_DEFEATED).findFirst().orElseThrow().data();
        assertTrue(result.firstClear());
        assertEquals("right", result.opened().id);
        Gate right = s.gates.stream().filter(g -> g.dungeon.equals("right")).findFirst().orElseThrow();
        assertTrue(right.open());

        // 첫 처치 뒤 캠프가 한 단계 밝아진다
        assertEquals(1, s.progress.firstClears());
        assertTrue(ev.stream().anyMatch(e -> e.type() == GameEvent.Type.CAMP_BRIGHTER));

        // 잡몹 강화는 1.3배 / 1.2배 한 번만
        s.useBed();
        enter(s, l1);
        step(s, new Controls(), 2);
        assertEquals(Math.round(baseHp * 1.3f), l1.monsters.get(0).maxHp());
        assertEquals(Math.round(baseAtk * 1.2f), l1.monsters.get(0).attack);
        // 침대로 쉬면 보스는 다시 생기고 포탈은 닫힌다
        assertEquals(RoomState.IDLE, boss.state());
        assertFalse(boss.portal.active());
    }

    @Test
    void 다시_생긴_보스는_광물_4에서_5개만_준다() {
        GameSession s = TestFiles.session(15);
        List<GameEvent> ev = record(s);
        Room boss = s.roomById.get("L5");
        for (int round = 0; round < 2; round++) {
            s.unit.teleport(boss.def.entry().x, boss.def.entry().y);
            step(s, new Controls(), 2);
            assertEquals(RoomState.FIGHTING, boss.state());
            killAll(s, boss);
            step(s, new Controls(), 90);
            assertEquals(RoomState.CLEARED, boss.state());
            if (round == 0) {
                s.orbDrops.clear();
                s.useBed();
            }
        }
        List<AbsorbService.BossAbsorb> results = ev.stream().filter(e -> e.type() == GameEvent.Type.BOSS_DEFEATED)
                .map(e -> (AbsorbService.BossAbsorb) e.data()).toList();
        assertEquals(2, results.size());
        assertFalse(results.get(1).firstClear());
        assertEquals(0, results.get(1).hpGain());
        assertTrue(results.get(1).oreReward() >= 4 && results.get(1).oreReward() <= 5);
        assertEquals(250, s.hero.maxHp());         // 체력은 한 번만
        assertTrue(s.orbDrops.isEmpty());          // 구슬도 한 번만
        assertEquals(1, s.progress.firstClears()); // 캠프 밝기도 한 번만
    }

    @Test
    void 앞에서_오는_공격은_막고_뒤에서_오는_공격은_못_막는다() {
        GameSession s = TestFiles.session(16);
        Controls c = new Controls();
        s.unit.lookAt(1, 0);
        c.guard = true;
        step(s, c, 2);
        assertTrue(s.unit.guarding);
        int hp = s.hero.hp();
        float meter = s.unit.guardMeter;
        assertFalse(s.hitHero(30, s.unit.centerX() + 40, s.unit.centerY()));
        assertEquals(hp, s.hero.hp());
        assertTrue(s.unit.guardMeter < meter - 0.3f);
        assertTrue(s.hitHero(30, s.unit.centerX() - 40, s.unit.centerY()));
        assertTrue(s.hero.hp() < hp);
    }

    @Test
    void 막기는_3초를_넘게_유지할_수_없다() {
        GameSession s = TestFiles.session(17);
        Controls c = new Controls();
        c.guard = true;
        step(s, c, 60 * 3 + 10);
        assertFalse(s.unit.guarding);
        assertTrue(s.unit.guardBroken > 0);
        step(s, c, 60 * 2 + 10); // 깨진 시간이 지나야 다시 막는다
        step(s, new Controls(), 60 * 4);
        assertEquals(s.balance.guard.max, s.unit.guardMeter, 0.01f);
    }

    @Test
    void 감전되면_스킬은_못_쓰고_기본_공격과_이동은_된다() {
        GameSession s = TestFiles.session(18);
        s.unit.applyShock(5);
        Controls c = new Controls();
        c.whirl = true;
        step(s, c, 1);
        assertEquals(0, s.unit.whirlCooldown, 0.001f);
        c.attack = true;
        step(s, c, 1);
        assertTrue(s.unit.attackCooldown > 0);
        float x = s.unit.footX();
        c.moveX = 1;
        step(s, c, 10);
        assertTrue(s.unit.footX() > x);
    }

    @Test
    void 독은_초당_최대_체력의_1퍼센트이고_다시_닿으면_5초로_돌아간다() {
        GameSession s = TestFiles.session(19);
        s.unit.teleport(s.map.spawn.x + 200, s.map.spawn.y); // 캠프 회복 영향 없게 캠프 밖으로
        s.unit.teleport(s.roomById.get("L1").bounds.x + 40, s.roomById.get("L1").bounds.y + 40);
        s.unit.applyPoison(s.balance.statuses.poisonDuration);
        float hp = s.hero.hpExact();
        step(s, new Controls(), 60);
        assertEquals(hp - s.hero.maxHp() * 0.01f, s.hero.hpExact(), 0.2f);
        step(s, new Controls(), 120);
        assertTrue(s.unit.poison < 3.1f);
        s.unit.applyPoison(s.balance.statuses.poisonDuration);
        assertEquals(5f, s.unit.poison, 0.01f);
    }

    @Test
    void 구슬_세_개를_모아_성검을_해방하면_마왕전으로_떨어진다() {
        GameSession s = TestFiles.session(5);
        for (String d : new String[]{"left", "right", "top"}) {
            s.absorbService.absorbBoss(s.balance.dungeon(d), s.hero, s.progress, s.inventory, s.random);
            s.progress.addOrb(d);
        }
        assertEquals(700, s.hero.maxHp());
        s.unit.teleport(s.map.sigil.x, s.map.sigil.y - 10);
        step(s, new Controls(), 2);
        assertEquals(GameSession.Interact.SIGIL_UNSEAL, s.focus());
        Controls c = new Controls();
        c.interact = true;
        step(s, c, 1);
        assertNotNull(s.cutscene());
        assertTrue(s.inventory.equipped().def().holy);
        step(s, new Controls(), 60 * 10);
        assertNull(s.cutscene());
        assertTrue(s.inArena());
        assertNotNull(s.demonKing);
    }

    @Test
    void 마왕은_깨어난_성검이_아니면_피해를_받지_않는다() {
        GameSession s = TestFiles.session(6);
        for (String d : new String[]{"left", "right", "top"}) {
            s.absorbService.absorbBoss(s.balance.dungeon(d), s.hero, s.progress, s.inventory, s.random);
            s.progress.addOrb(d);
        }
        s.unit.teleport(s.map.sigil.x, s.map.sigil.y - 10);
        step(s, new Controls(), 2);
        Controls c = new Controls();
        c.interact = true;
        step(s, c, 1);
        step(s, new Controls(), 60 * 10);
        Boss king = s.demonKing;
        step(s, new Controls(), 60);
        var sky = s.inventory.add(s.balance.weapon("sky"));
        s.equip(sky);
        s.unit.teleport(king.footX(), king.footY() - 40);
        s.unit.lookAt(0, 1);
        int hp = king.hp();
        c.attack = true;
        step(s, c, 1);
        assertEquals(hp, king.hp());
        s.equip(s.inventory.sword());
        step(s, new Controls(), 30);
        s.unit.teleport(king.footX(), king.footY() - 40);
        s.unit.lookAt(0, 1);
        c.attack = true;
        step(s, c, 1);
        assertTrue(king.hp() < hp);
    }

    @Test
    void 죽으면_싸우던_방만_처음_상태로_돌아간다() {
        GameSession s = TestFiles.session(7);
        List<GameEvent> ev = record(s);
        Room l1 = s.roomById.get("L1"), l2 = s.roomById.get("L2");
        enter(s, l1);
        step(s, new Controls(), 2);
        killAll(s, l1);
        step(s, new Controls(), 60);
        enter(s, l2);
        step(s, new Controls(), 2);
        assertEquals(RoomState.FIGHTING, l2.state());
        s.hero.damage(9999);
        step(s, new Controls(), 2);
        assertTrue(ev.stream().anyMatch(e -> e.type() == GameEvent.Type.HERO_DIED));
        s.respawn();
        assertEquals(RoomState.IDLE, l2.state());
        assertEquals(RoomState.CLEARED, l1.state());
        assertTrue(s.inCamp());
        assertEquals(s.hero.maxHp(), s.hero.hp());
    }

    @Test
    void 안개_지금_방과_비운_방만_보이고_침대를_쓰면_다시_가려진다() {
        GameSession s = TestFiles.session(8);
        Room l1 = s.roomById.get("L1"), l2 = s.roomById.get("L2");
        step(s, new Controls(), 1);
        assertFalse(s.visibility.room(l1));
        enter(s, l1);
        step(s, new Controls(), 2);
        assertTrue(s.visibility.room(l1));
        assertFalse(s.visibility.room(l2));
        killAll(s, l1);
        step(s, new Controls(), 60);
        s.unit.teleport(s.map.spawn.x, s.map.spawn.y);
        step(s, new Controls(), 2);
        assertTrue(s.visibility.room(l1), "비운 방은 보인다");
        s.useBed();
        step(s, new Controls(), 1);
        assertFalse(s.visibility.room(l1), "침대 뒤에는 다시 가려진다");
    }

    @Test
    void 저장하고_불러오면_광맥_비운_방_체력_위치가_같다() {
        GameSession a = TestFiles.session(9);
        Room l1 = a.roomById.get("L1"), ore = a.roomById.get("L3");
        enter(a, l1);
        step(a, new Controls(), 2);
        killAll(a, l1);
        step(a, new Controls(), 60);
        enter(a, ore);
        step(a, new Controls(), 2);
        ore.veins.get(0).hit();
        ore.veins.get(1).hit();
        ore.veins.get(1).hit();
        ore.veins.get(1).hit();
        a.hero.damage(30);
        a.inventory.addOre("blackiron", 20);
        var w = a.craft(a.balance.weapon("blackiron"));
        a.equip(w);
        String json = SaveMapper.toJson(SaveMapper.capture(a));

        GameSession b = TestFiles.session(99);
        SaveData d = SaveMapper.fromJson(json);
        SaveMapper.restore(d, b);
        Room ore2 = b.roomById.get("L3");
        assertEquals(ore.veins.size(), ore2.veins.size());
        for (int i = 0; i < ore.veins.size(); i++) assertEquals(ore.veins.get(i).hitsLeft(), ore2.veins.get(i).hitsLeft());
        assertEquals(RoomState.CLEARED, b.roomById.get("L1").state());
        assertEquals(a.hero.hp(), b.hero.hp());
        assertEquals(a.inventory.ore("blackiron"), b.inventory.ore("blackiron"));
        assertEquals("blackiron", b.inventory.equipped().def().id);
        // 던전에서 저장 → 마지막 방(광물방) 입구 안쪽에서 시작
        assertTrue(ore2.bounds.contains(b.unit.footX(), b.unit.footY()));
    }

    @Test
    void 전투_중_저장하면_그_방은_처음_상태가_되고_문_밖에서_시작한다() {
        GameSession a = TestFiles.session(10);
        Room l1 = a.roomById.get("L1");
        enter(a, l1);
        step(a, new Controls(), 2);
        assertEquals(RoomState.FIGHTING, l1.state());
        GameSession b = TestFiles.session(11);
        SaveMapper.restore(SaveMapper.fromJson(SaveMapper.toJson(SaveMapper.capture(a))), b);
        Room r = b.roomById.get("L1");
        assertEquals(RoomState.IDLE, r.state());
        assertFalse(r.bounds.contains(b.unit.footX(), b.unit.footY()));
    }

    @Test
    void 회피하는_동안은_맞지_않는다() {
        GameSession s = TestFiles.session(12);
        Controls c = new Controls();
        c.dodge = true;
        c.moveX = 1;
        step(s, c, 1);
        int hp = s.hero.hp();
        s.hitHero(50, 0, 0);
        assertEquals(hp, s.hero.hp());
    }

    @Test
    void 다음_할_일이_단계대로_바뀐다() {
        GameSession s = TestFiles.session(13);
        step(s, new Controls(), 1);
        assertEquals("무너진 갱도로 이동", s.goal().text());
        s.inventory.addOre("blackiron", 3);
        assertEquals("흑철석 모으기 3/8", s.goal().text());
        s.inventory.addOre("blackiron", 5);
        assertEquals("캠프 대장간에서 흑철검 만들기", s.goal().text());
        s.craft(s.balance.weapon("blackiron"));
        assertEquals("흑철검 장착하기 (I 가방)", s.goal().text());
        s.equip(s.inventory.weapons().get(1));
        assertTrue(s.goal().text().contains("좀비킹"));
        assertFalse(s.routeToGoal().isEmpty());
        assertEquals("L5", s.routeToGoal().get(s.routeToGoal().size() - 1));
    }

    @Test
    void 봉인된_입구는_지나갈_수_없다() {
        GameSession s = TestFiles.session(14);
        Controls c = new Controls();
        s.unit.teleport(s.map.camp.x + s.map.camp.width - 20, 36 * 32);
        c.moveX = 1;
        step(s, c, 120);
        assertTrue(s.inCamp());
    }
}
