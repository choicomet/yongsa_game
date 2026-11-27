package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.map.Door;
import com.yongsa.map.RoomType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 선우 버전의 마을: 캠프 아래 계단에서 통로로 이어진다. 용사는 몇 번 망설이다 나가고, 마을 사람들은 마왕을 잡아야 수정에서 풀린다. */
class VillageTest {
    private final Balance b = TestData.balance();

    private List<GameEvent> run(GameSession g, Controls c, float seconds) {
        List<GameEvent> all = new ArrayList<>();
        for (float t = 0; t < seconds; t += 1 / 60f) {
            g.update(1 / 60f, c);
            all.addAll(g.pollEvents());
            c.interact = false;
        }
        return all;
    }

    private static boolean heroSaid(List<GameEvent> ev) {
        return ev.stream().anyMatch(e -> e.type == GameEvent.Type.BOSS_SPEECH && "용사".equals(e.speaker));
    }

    /** 캠프 아래 문 앞에서 통로를 따라 마을로 걸어가 본다. 마을에 나갔으면 true. */
    private boolean tryLeave(GameSession g, List<GameEvent> out) {
        Door down = g.map.room("CAMP").doors.stream().filter(d -> d.name.equals("down")).findFirst().get();
        g.hero.x = down.arriveX;
        g.hero.y = down.arriveY;
        TestData.walkThrough(g, down, out, () -> g.inVillage() || heroSaid(out));
        out.addAll(run(g, new Controls(), 0.6f));
        return g.inVillage();
    }

    @Test
    void heroRefusesToLeaveFiveTimesThenGivesUp() {
        GameState s = NewGame.create(b);
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        for (int i = 0; i < b.village.exitRefusals; i++) {
            List<GameEvent> ev = new ArrayList<>();
            assertFalse(tryLeave(g, ev), "아직은 나가지 않는다 " + i);
            assertTrue(g.inCamp());
            assertTrue(heroSaid(ev));
        }
        List<GameEvent> ev = new ArrayList<>();
        assertTrue(tryLeave(g, ev), "여섯 번째에는 체념하고 나간다");
        assertTrue(ev.stream().anyMatch(e -> b.village.giveUpLine.equals(e.text)));
    }

    @Test
    void villagersAreTrappedUntilTheDemonLordFalls() {
        GameState s = NewGame.create(b);
        s.exitRefusals = 99;
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        assertTrue(tryLeave(g, new ArrayList<>()));
        assertEquals(RoomType.VILLAGE, g.heroRoom().type);

        Balance.Villager v = b.village.villagers.get(0);
        g.hero.x = g.villagerX(v);
        g.hero.y = g.villagerY(v) - 1.0f * b.tileSize;
        Controls e = new Controls();
        e.interact = true;
        List<GameEvent> ev = run(g, e, 0.1f);
        assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.BOSS_SPEECH
                && "용사".equals(x.speaker) && x.text.contains(v.name)), "수정 속 사람을 살펴본다");

        s.finalBossDefeated = true;
        e.interact = true;
        ev = run(g, e, 0.1f);
        assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.BOSS_SPEECH
                && v.name.equals(x.speaker) && v.freeLines.contains(x.text)), "풀려난 사람과 이야기한다");
    }

    @Test
    void everyVillagerHasOwnMemoriesAndThanks() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Balance.Villager v : b.village.villagers) {
            assertTrue(v.trappedLines != null && v.trappedLines.size() >= 2, v.name + " 추억 대사");
            assertTrue(v.freeLines != null && v.freeLines.size() >= 2, v.name + " 고마움 대사");
            assertTrue(v.trappedLines.get(0).contains(v.name), v.name + " 첫 추억 대사에 이름이 나온다");
            for (String line : v.trappedLines) assertTrue(seen.add(line), "추억 대사가 사람마다 다르다: " + line);
        }
    }

    @Test
    void lookingAtTrappedVillagerCyclesThroughMemories() {
        GameState s = NewGame.create(b);
        s.exitRefusals = 99;
        GameSession g = new GameSession(b, TestData.map(), s, 3);
        assertTrue(tryLeave(g, new ArrayList<>()));
        Balance.Villager v = b.village.villagers.get(0);
        g.hero.x = g.villagerX(v);
        g.hero.y = g.villagerY(v) - 1.0f * b.tileSize;
        for (int i = 0; i < v.trappedLines.size() + 1; i++) {
            Controls e = new Controls();
            e.interact = true;
            List<GameEvent> ev = run(g, e, 0.1f);
            String want = v.trappedLines.get(i % v.trappedLines.size());
            assertTrue(ev.stream().anyMatch(x -> x.type == GameEvent.Type.BOSS_SPEECH
                    && "용사".equals(x.speaker) && want.equals(x.text)), "추억 대사 " + i);
        }
    }

    /** 엔딩 결과 창의 "마을로 가기": 마을 가는 길 통로에 서고, 마을 쪽으로 걸으면 풀려난 마을에 들어간다. */
    @Test
    void endingGoToVillageStartsOnVillageRoad() {
        GameState s = NewGame.create(b);
        s.finalBossDefeated = true;
        GameSession g = new GameSession(b, TestData.map(), s, 1);
        assertTrue(g.placeOnVillageRoad());
        assertNull(g.heroRoom(), "통로 위에 서야 한다");
        com.yongsa.map.Corridor c = g.map.corridorAtWorld(g.hero.x, g.hero.y);
        assertNotNull(c);
        assertTrue(c.link.a.type == RoomType.VILLAGE || c.link.b.type == RoomType.VILLAGE);
        // 바라보는 쪽(마을)으로 걸어가면 마을에 들어간다
        Controls walk = new Controls();
        walk.moveX = g.hero.aimX;
        walk.moveY = g.hero.aimY;
        boolean reached = false;
        for (int i = 0; i < 60 * 15 && !reached; i++) {
            float dx = g.village().spawnX(b.tileSize) - g.hero.x, dy = g.village().spawnY(b.tileSize) - g.hero.y;
            float len = (float) Math.hypot(dx, dy);
            walk.moveX = dx / len;
            walk.moveY = dy / len;
            g.update(1 / 60f, walk);
            g.pollEvents();
            reached = g.inVillage();
        }
        assertTrue(reached, "마을 가는 길에서 마을로 걸어 들어갈 수 있어야 한다");
        assertTrue(g.villagersFree());
    }
}
