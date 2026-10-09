package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.CraftService;
import com.yongsa.model.GameState;
import com.yongsa.model.Progress;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgressTest {
    final Balance b = TestData.balance();

    @Test
    void capsAndUnlocksFollowBossCount() {
        GameState s = TestData.newGame();
        assertEquals(3, Progress.enhanceCap(b, s));
        assertTrue(Progress.isZoneUnlocked(b, s, "mine"));
        assertFalse(Progress.isZoneUnlocked(b, s, "fortress"));
        assertEquals("폐광의 수호자 처치 후 개방", Progress.zoneLockReason(b, s, "fortress"));
        assertFalse(Progress.isAwakeningUnlocked(b, s));
        Progress.onBossDefeated(b, s, "boss_mine");
        assertEquals(6, Progress.enhanceCap(b, s));
        assertTrue(Progress.isZoneUnlocked(b, s, "fortress"));
        assertFalse(Progress.isZoneUnlocked(b, s, "temple"));
        assertTrue(Progress.isAwakeningUnlocked(b, s));
        Progress.onBossDefeated(b, s, "boss_fortress");
        assertEquals(10, Progress.enhanceCap(b, s));
        assertTrue(Progress.isZoneUnlocked(b, s, "temple"));
    }

    @Test
    void T10_duplicateClearAppliesOnce() {
        GameState s = TestData.newGame();
        assertEquals(3, Progress.onRoomCleared(b, s, "mine_battle_a", "mine"));
        assertEquals(0, Progress.onRoomCleared(b, s, "mine_battle_a", "mine"));
        assertEquals(3, s.minerals);
        assertEquals(12, Progress.onBossDefeated(b, s, "boss_mine"));
        assertEquals(0, Progress.onBossDefeated(b, s, "boss_mine"));
        assertEquals(15, s.minerals);
        assertEquals(1, s.defeatedBosses.size());
    }

    @Test
    void objectivesFollowPriority() {
        GameState s = TestData.newGame();
        assertEquals("왼쪽 폐광 입구 광물방에서 광물 6개 모으기", Progress.objective(b, s, "mine_01"));
        s.minerals = 6;
        assertEquals("캠프 대장간에서 철검 제작하기", Progress.objective(b, s, "mine_01"));
        CraftService.craft(b, s, "sword");
        assertEquals("폐광의 첫 전투방 돌파하기", Progress.objective(b, s, "mine_01"));
        Progress.onRoomCleared(b, s, "mine_01", "mine");
        assertEquals("대장간에서 강화해 보기", Progress.objective(b, s, "mine_01"));
        Progress.onAntechamberReached(s, "mine"); // 보스 앞 도달 시 강화 안내는 넘어간다
        assertEquals("지름길을 이용해 폐광의 수호자 공략하기", Progress.objective(b, s, "mine_01"));
        Progress.onBossDefeated(b, s, "boss_mine");
        assertEquals("중앙 캠프로 돌아가 오른쪽 지하 요새로 향하기", Progress.objective(b, s, "mine_01"));
        Progress.onRoomCleared(b, s, "fortress_00", "fortress");
        assertEquals("방을 돌파하며 지하 요새 깊은 곳의 보스 앞까지 이동하기", Progress.objective(b, s, "mine_01"));
        Progress.onBossDefeated(b, s, "boss_fortress");
        assertEquals("중앙 캠프로 돌아가 위쪽 봉인의 신전으로 향하기", Progress.objective(b, s, "mine_01"));
        Progress.onBossDefeated(b, s, "boss_temple");
        assertEquals("모든 봉인을 해제했습니다", Progress.objective(b, s, "mine_01"));
    }

    @Test
    void bedAdvancesCycleButKeepsProgress() {
        GameState s = TestData.newGame();
        Progress.onRoomCleared(b, s, "mine_battle_a", "mine");
        s.mineRooms.add(new com.yongsa.model.MineRoomState("mine_ore"));
        Progress.onBedUsed(s);
        assertEquals(1, s.miningCycle);
        assertTrue(s.mineRooms.isEmpty());
        assertTrue(s.isRoomCleared("mine_battle_a"));
    }
}
