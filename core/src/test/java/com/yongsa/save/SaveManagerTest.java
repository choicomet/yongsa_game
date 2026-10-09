package com.yongsa.save;

import com.yongsa.TestData;
import com.yongsa.config.Balance;
import com.yongsa.model.CraftService;
import com.yongsa.model.GameState;
import com.yongsa.model.MineRoomState;
import com.yongsa.model.Progress;
import com.yongsa.model.VeinState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SaveManagerTest {
    final Balance b = TestData.balance();

    GameState sample() {
        GameState s = TestData.newGame();
        s.minerals = 40;
        CraftService.craft(b, s, "sword");
        s.weapon("sword").level = 3;
        s.weapon("sword").failCount = 1;
        Progress.onRoomCleared(b, s, "mine_battle_a", "mine");
        Progress.onBossDefeated(b, s, "boss_mine");
        Progress.onAntechamberReached(s, "mine");
        s.miningCycle = 2;
        MineRoomState m = new MineRoomState("mine_ore");
        VeinState v = new VeinState("mine_vein_01");
        v.mined = true;
        m.veins.add(v);
        m.veins.add(new VeinState("mine_vein_04"));
        s.mineRooms.add(m);
        s.enhanceUses = 17;
        s.veinUses = 5;
        s.awakenGuideSeen = true;
        return s;
    }

    @Test
    void T17_roundTripKeepsEverything() throws Exception {
        Path dir = Files.createTempDirectory("ys");
        SaveManager sm = new SaveManager(dir, b);
        GameState s = sample();
        assertTrue(sm.save(s), sm.lastError());
        SaveManager.LoadResult r = sm.load();
        assertEquals(SaveManager.LoadStatus.OK, r.status);
        GameState l = r.state;
        assertEquals(sm.serialize(s), sm.serialize(l));
        assertEquals(s.minerals, l.minerals);
        assertEquals(3, l.weapon("sword").level);
        assertEquals(1, l.weapon("sword").failCount);
        assertTrue(l.isRoomCleared("mine_battle_a"));
        assertTrue(l.isBossDefeated("boss_mine"));
        assertTrue(l.hasShortcut("mine"));
        assertTrue(l.mineRoom("mine_ore").vein("mine_vein_01").mined);
        assertFalse(l.mineRoom("mine_ore").vein("mine_vein_04").mined);
        assertEquals(17, l.enhanceUses);
    }

    @Test
    void secondSaveKeepsPreviousAsBackup() throws Exception {
        Path dir = Files.createTempDirectory("ys");
        SaveManager sm = new SaveManager(dir, b);
        GameState s = sample();
        assertTrue(sm.save(s));
        s.minerals = 99;
        assertTrue(sm.save(s));
        String bak = new String(Files.readAllBytes(dir.resolve("save.bak.json")), StandardCharsets.UTF_8);
        assertEquals(40 - 6 + 6 + 12, sm.parse(bak).minerals);
        assertEquals(99, sm.load().state.minerals);
    }

    @Test
    void T18_interruptedReplaceRecovers() throws Exception {
        Path dir = Files.createTempDirectory("ys");
        SaveManager sm = new SaveManager(dir, b);
        GameState s = sample();
        assertTrue(sm.save(s));
        s.minerals = 77;
        sm.beforeReplaceHook = () -> {
            throw new RuntimeException("프로세스 중단");
        };
        assertFalse(sm.save(s));
        // 교체 전에 중단 → 주 저장은 이전 정상 상태 그대로
        SaveManager fresh = new SaveManager(dir, b);
        SaveManager.LoadResult r = fresh.load();
        assertEquals(SaveManager.LoadStatus.OK, r.status);
        assertEquals(52, r.state.minerals);

        // 주 저장이 반쯤 쓰인 채 손상된 경우 → 백업에서 복원
        Files.write(dir.resolve("save.json"), "{\"saveVersion\":1,\"miner".getBytes(StandardCharsets.UTF_8));
        SaveManager.LoadResult r2 = fresh.load();
        assertEquals(SaveManager.LoadStatus.RESTORED_FROM_BACKUP, r2.status);
        assertNotNull(r2.message);
        assertEquals(52, r2.state.minerals);
    }

    @Test
    void T19_corruptAndUnsupportedAreReportedNotReset() throws Exception {
        Path dir = Files.createTempDirectory("ys");
        SaveManager sm = new SaveManager(dir, b);
        Files.write(dir.resolve("save.json"), "garbage".getBytes(StandardCharsets.UTF_8));
        SaveManager.LoadResult r = sm.load();
        assertEquals(SaveManager.LoadStatus.CORRUPT, r.status);
        assertTrue(Files.exists(dir.resolve("save.json")), "기존 파일 보존");

        Files.write(dir.resolve("save.json"), "{\"saveVersion\":99}".getBytes(StandardCharsets.UTF_8));
        SaveManager.LoadResult u = sm.load();
        assertEquals(SaveManager.LoadStatus.UNSUPPORTED_VERSION, u.status);
        assertEquals("{\"saveVersion\":99}", new String(Files.readAllBytes(dir.resolve("save.json")), StandardCharsets.UTF_8));

        // 범위 오류: 존재하지 않는 장착 무기
        GameState bad = sample();
        String text = sm.serialize(bad).replace("\"equipped\": \"sword\"", "\"equipped\": \"axe\"");
        assertThrows(SaveManager.InvalidSaveException.class, () -> sm.parse(text));
    }

    @Test
    void T19_saveFailureLeavesMemoryAndFiles() throws Exception {
        Path file = Files.createTempFile("ys", ".blocker");
        // 폴더 자리에 파일이 있어 저장 폴더를 만들 수 없음
        SaveManager sm = new SaveManager(file.resolve("sub"), b);
        GameState s = sample();
        int before = s.minerals;
        assertFalse(sm.save(s));
        assertNotNull(sm.lastError());
        assertEquals(before, s.minerals);
    }

    @Test
    void noSaveMeansNone() throws Exception {
        SaveManager sm = new SaveManager(Files.createTempDirectory("ys"), b);
        assertEquals(SaveManager.LoadStatus.NONE, sm.load().status);
    }
}
