package com.yongsa.save;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import com.yongsa.config.Balance;
import com.yongsa.model.GameState;
import com.yongsa.model.MineRoomState;
import com.yongsa.model.Settings;
import com.yongsa.model.VeinState;
import com.yongsa.model.WeaponState;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;

/**
 * 자동 저장 슬롯 1개와 직전 정상 백업 1개를 관리한다.
 * 저장 순서: 임시 파일 쓰기 → 다시 읽어 필수 필드와 범위 검증 → 기존 정상 저장을 백업 → 새 저장으로 교체.
 */
public class SaveManager {

    public enum LoadStatus {
        /** 주 저장을 정상적으로 읽었다. */
        OK,
        /** 주 저장이 손상되어 정상 백업으로 복원했다. */
        RESTORED_FROM_BACKUP,
        /** 저장 파일이 없다. */
        NONE,
        /** 주 저장과 백업 모두 읽을 수 없다. */
        CORRUPT,
        /** 이 버전에서 해석할 수 없는 새 형식이다. 파일은 건드리지 않는다. */
        UNSUPPORTED_VERSION
    }

    public static class LoadResult {
        public LoadStatus status;
        public GameState state;
        public String message;

        LoadResult(LoadStatus status, GameState state, String message) {
            this.status = status;
            this.state = state;
            this.message = message;
        }
    }

    /** 검증 실패 사유를 담는 예외. */
    public static class InvalidSaveException extends Exception {
        public final boolean unsupportedVersion;

        InvalidSaveException(String msg, boolean unsupportedVersion) {
            super(msg);
            this.unsupportedVersion = unsupportedVersion;
        }
    }

    private final Path dir;
    private final Path main;
    private final Path backup;
    private final Path temp;
    private final Path settingsFile;
    private final Balance balance;
    private String lastError;
    /** 테스트용: 교체 직전에 중단된 상황을 흉내 낸다. */
    Runnable beforeReplaceHook;

    public SaveManager(Path dir, Balance balance) {
        this.dir = dir;
        this.balance = balance;
        this.main = dir.resolve("save.json");
        this.backup = dir.resolve("save.bak.json");
        this.temp = dir.resolve("save.tmp.json");
        this.settingsFile = dir.resolve("settings.json");
    }

    public Path directory() {
        return dir;
    }

    public String lastError() {
        return lastError;
    }

    public boolean hasAnySaveFile() {
        return Files.exists(main) || Files.exists(backup);
    }

    // ---- 직렬화 ----

    private static Json json() {
        Json j = new Json(JsonWriter.OutputType.json);
        j.setUsePrototypes(false);
        j.setIgnoreUnknownFields(true);
        j.setTypeName(null);
        j.setElementType(GameState.class, "weapons", WeaponState.class);
        j.setElementType(GameState.class, "clearedRooms", String.class);
        j.setElementType(GameState.class, "defeatedBosses", String.class);
        j.setElementType(GameState.class, "shortcuts", String.class);
        j.setElementType(GameState.class, "visitedRooms", String.class);
        j.setElementType(GameState.class, "openedChests", String.class);
        j.setElementType(GameState.class, "takenHeals", String.class);
        j.setElementType(GameState.class, "blessings", String.class);
        j.setElementType(GameState.class, "usedShrines", String.class);
        j.setElementType(GameState.class, "mineRooms", MineRoomState.class);
        j.setElementType(MineRoomState.class, "veins", VeinState.class);
        return j;
    }

    public String serialize(GameState s) {
        return json().prettyPrint(json().toJson(s, GameState.class));
    }

    public GameState parse(String text) throws InvalidSaveException {
        JsonValue root;
        try {
            root = new JsonReader().parse(text);
        } catch (Exception e) {
            throw new InvalidSaveException("JSON 형식 오류", false);
        }
        if (root == null || !root.isObject()) throw new InvalidSaveException("저장 형식이 아닙니다", false);
        if (!root.has("saveVersion")) throw new InvalidSaveException("saveVersion 없음", false);
        int version = root.getInt("saveVersion", -1);
        if (version > GameState.SAVE_VERSION) {
            throw new InvalidSaveException("지원하지 않는 저장 버전입니다 (" + version + ")", true);
        }
        for (String f : new String[]{"minerals", "weapons", "equipped", "clearedRooms", "defeatedBosses",
                "shortcuts", "miningCycle", "mineRooms", "dungeonSeed", "enhanceSeed", "enhanceUses", "veinSeed", "veinUses"}) {
            if (!root.has(f)) throw new InvalidSaveException("필수 항목 없음: " + f, false);
        }
        GameState s;
        try {
            s = json().readValue(GameState.class, root);
        } catch (Exception e) {
            throw new InvalidSaveException("값을 해석할 수 없습니다", false);
        }
        validate(s);
        return s;
    }

    /** 필수 필드와 범위 검증. */
    public void validate(GameState s) throws InvalidSaveException {
        if (s.saveVersion < 1) fail("saveVersion 범위 오류");
        if (s.minerals < 0 || s.gold < 0) fail("광물 수 범위 오류");
        if (s.ore == null) s.ore = new int[balance.zones.length];
        for (int n : s.ore) if (n < 0) fail("광물 수 범위 오류");
        // 광물이 한 종류이던 예전 저장: 첫 구역 광물로 옮긴다
        if (s.minerals > 0) {
            s.addOre(0, s.minerals);
            s.minerals = 0;
        }
        if (s.weapons == null || s.clearedRooms == null || s.defeatedBosses == null
                || s.shortcuts == null || s.mineRooms == null) fail("목록 항목 누락");
        Set<String> types = new HashSet<>();
        for (WeaponState w : s.weapons) {
            if (w == null || w.type == null) fail("무기 항목 오류");
            try {
                balance.weapon(w.type);
            } catch (IllegalArgumentException e) {
                fail("알 수 없는 무기: " + w.type);
            }
            if (!types.add(w.type)) fail("같은 종류 무기 중복");
            if (w.level < 0 || w.level > balance.maxLevel()) fail("강화 단계 범위 오류");
            if (w.level < balance.maxLevel()) {
                int maxTries = balance.enhanceStep(w.level + 1).maxTries;
                if (w.failCount < 0 || w.failCount >= maxTries) fail("실패 누적 범위 오류");
            } else if (w.failCount != 0) {
                fail("실패 누적 범위 오류");
            }
        }
        if (s.equipped == null) s.equipped = "";
        if (s.visitedRooms == null) s.visitedRooms = new java.util.ArrayList<>();
        // 이전 버전 저장에는 없는 항목
        if (s.openedChests == null) s.openedChests = new java.util.ArrayList<>();
        if (s.takenHeals == null) s.takenHeals = new java.util.ArrayList<>();
        if (s.blessings == null) s.blessings = new java.util.ArrayList<>();
        if (s.usedShrines == null) s.usedShrines = new java.util.ArrayList<>();
        if (s.weapons.isEmpty() && !s.equipped.isEmpty()) fail("장착 무기 오류");
        if (!s.weapons.isEmpty() && s.weapon(s.equipped) == null) fail("장착 무기 오류");
        for (String boss : s.defeatedBosses) {
            try {
                balance.boss(boss);
            } catch (IllegalArgumentException e) {
                fail("알 수 없는 보스: " + boss);
            }
        }
        if (s.miningCycle < 0 || s.enhanceUses < 0 || s.veinUses < 0) fail("카운터 범위 오류");
        for (MineRoomState m : s.mineRooms) {
            if (m == null || m.roomId == null || m.veins == null) fail("광맥 항목 오류");
            Set<String> ids = new HashSet<>();
            for (VeinState v : m.veins) {
                if (v == null || v.candidateId == null || !ids.add(v.candidateId)) fail("광맥 후보 중복");
            }
        }
        if (s.settings == null) s.settings = Settings.defaults(balance);
        s.settings.clamp();
    }

    private static void fail(String msg) throws InvalidSaveException {
        throw new InvalidSaveException(msg, false);
    }

    // ---- 저장 ----

    /** 성공하면 true. 실패해도 메모리의 상태는 건드리지 않는다. */
    public boolean save(GameState s) {
        lastError = null;
        try {
            Files.createDirectories(dir);
            String text = serialize(s);
            Files.write(temp, text.getBytes(StandardCharsets.UTF_8));

            // 다시 읽어 검증
            String reread = new String(Files.readAllBytes(temp), StandardCharsets.UTF_8);
            parse(reread);

            // 기존 저장이 정상일 때만 백업으로 보존한다. 손상된 주 저장으로 정상 백업을 덮어쓰지 않는다.
            if (Files.exists(main) && isReadable(main)) {
                Path bakTemp = dir.resolve("save.bak.tmp.json");
                Files.copy(main, bakTemp, StandardCopyOption.REPLACE_EXISTING);
                move(bakTemp, backup);
            }
            if (beforeReplaceHook != null) beforeReplaceHook.run();
            move(temp, main);
            return true;
        } catch (InvalidSaveException e) {
            lastError = "저장 검증 실패: " + e.getMessage();
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
        }
        return false;
    }

    private static void move(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean isReadable(Path p) {
        try {
            parse(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---- 불러오기 ----

    public LoadResult load() {
        boolean mainExists = Files.exists(main);
        boolean backupExists = Files.exists(backup);
        if (!mainExists && !backupExists) return new LoadResult(LoadStatus.NONE, null, null);

        String mainError = null;
        if (mainExists) {
            try {
                GameState s = parse(new String(Files.readAllBytes(main), StandardCharsets.UTF_8));
                return new LoadResult(LoadStatus.OK, s, null);
            } catch (InvalidSaveException e) {
                if (e.unsupportedVersion) {
                    return new LoadResult(LoadStatus.UNSUPPORTED_VERSION, null,
                            "이 버전에서 읽을 수 없는 저장입니다. 파일은 그대로 보존됩니다.");
                }
                mainError = e.getMessage();
            } catch (IOException e) {
                mainError = e.getMessage();
            }
        }
        if (backupExists) {
            try {
                GameState s = parse(new String(Files.readAllBytes(backup), StandardCharsets.UTF_8));
                String msg = mainExists
                        ? "저장 파일이 손상되어 직전 정상 백업으로 복원했습니다."
                        : "주 저장 파일이 없어 직전 정상 백업으로 복원했습니다.";
                return new LoadResult(LoadStatus.RESTORED_FROM_BACKUP, s, msg);
            } catch (InvalidSaveException e) {
                if (e.unsupportedVersion) {
                    return new LoadResult(LoadStatus.UNSUPPORTED_VERSION, null,
                            "이 버전에서 읽을 수 없는 저장입니다. 파일은 그대로 보존됩니다.");
                }
            } catch (IOException ignored) {
                // 아래에서 손상으로 처리
            }
        }
        return new LoadResult(LoadStatus.CORRUPT, null,
                "저장 파일을 읽을 수 없습니다" + (mainError != null ? " (" + mainError + ")" : "") + ".");
    }

    /** 새 게임으로 덮어쓰기 전, 읽을 수 없는 기존 파일을 지우지 않고 다른 이름으로 보존한다. */
    public void archiveUnreadable() {
        try {
            long t = System.currentTimeMillis();
            if (Files.exists(main)) Files.move(main, dir.resolve("save.unreadable." + t + ".json"));
            if (Files.exists(backup)) Files.move(backup, dir.resolve("save.bak.unreadable." + t + ".json"));
        } catch (IOException ignored) {
            // 보존에 실패하면 새 저장이 기존 파일을 백업 규칙에 따라 다룬다
        }
    }

    // ---- 사용자 설정 (타이틀에서도 쓰이므로 별도 파일에도 둔다) ----

    public Settings loadSettings() {
        try {
            if (Files.exists(settingsFile)) {
                Settings s = json().fromJson(Settings.class,
                        new String(Files.readAllBytes(settingsFile), StandardCharsets.UTF_8));
                if (s != null) {
                    s.clamp();
                    return s;
                }
            }
        } catch (Exception ignored) {
            // 설정 파일이 손상되면 기본값을 쓴다
        }
        return Settings.defaults(balance);
    }

    public boolean saveSettings(Settings s) {
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve("settings.tmp.json");
            Files.write(tmp, json().prettyPrint(json().toJson(s, Settings.class)).getBytes(StandardCharsets.UTF_8));
            move(tmp, settingsFile);
            return true;
        } catch (Exception e) {
            lastError = e.getMessage();
            return false;
        }
    }
}
