package com.yongsa.model;

import java.util.ArrayList;

/**
 * 저장되는 영구 게임 상태 전체.
 * 다음 구역 해금, 강화 상한, 각성 사용 가능 여부는 저장하지 않고 defeatedBosses 에서 계산한다 (Progress 참고).
 * 현재 전투, 적 위치, 정확한 용사 위치는 저장하지 않는다.
 */
public class GameState {
    public static final int SAVE_VERSION = 1;

    public int saveVersion = SAVE_VERSION;
    public int dataVersion;

    // 자원과 장비
    /** 구역별 광물 (칸 번호 = balance.json 의 zones 순서). 색마다 만들 수 있는 장비가 다르다. */
    public int[] ore = new int[3];
    /** 상자에서 얻고 제단에 바치는 골드. */
    public int gold;
    /** 예전 저장의 단일 광물. 불러올 때 첫 구역 광물로 옮기고 0으로 둔다 (SaveManager.validate). */
    public int minerals;
    public ArrayList<WeaponState> weapons = new ArrayList<>();
    public String equipped = "";

    // 영구 진행
    /** 클리어와 보상 지급이 함께 기록된 일반 전투방 ID. */
    public ArrayList<String> clearedRooms = new ArrayList<>();
    /** 처치한 보스 ID. 보스 보상 지급 기록을 겸한다. */
    public ArrayList<String> defeatedBosses = new ArrayList<>();
    /** 보스 앞 공간에 도달해 지름길이 해금된 구역 ID. */
    public ArrayList<String> shortcuts = new ArrayList<>();
    /** 미니맵에 표시할, 한 번이라도 들어간 방. */
    public ArrayList<String> visitedRooms = new ArrayList<>();
    public boolean awakenGuideSeen;
    public boolean firstEnhanceDone;
    /** 상자를 연 방 ID (상자는 방을 돌파하면 나타난다). */
    public ArrayList<String> openedChests = new ArrayList<>();
    /** 상자에서 나온 회복약을 이미 마신 방 ID (마시지 않은 회복약은 그 자리에 남아 있다). */
    public ArrayList<String> takenHeals = new ArrayList<>();

    // 이번 목숨 동안만 유지되는 것 (죽으면 비운다)
    /** 제단에서 받은 축복 ID. */
    public ArrayList<String> blessings = new ArrayList<>();
    /** 이미 기도한 제단 ID. */
    public ArrayList<String> usedShrines = new ArrayList<>();

    // 채굴
    public int miningCycle;
    public ArrayList<MineRoomState> mineRooms = new ArrayList<>();

    // 무작위 상태
    /** 던전 배치 씨앗값. 새 게임마다 바뀌고, 같은 저장은 항상 같은 던전을 만든다. */
    public long dungeonSeed;
    public long enhanceSeed;
    public long enhanceUses;
    public long veinSeed;
    public long veinUses;

    // 사용자 설정
    public Settings settings = new Settings();

    public static GameState newGame(long seed, int dataVersion, Settings settings) {
        GameState s = new GameState();
        s.dataVersion = dataVersion;
        s.dungeonSeed = seed;
        s.enhanceSeed = seed ^ 0x2545F4914F6CDD1DL;
        s.veinSeed = seed * 31L + 0x5DEECE66DL;
        s.settings = settings == null ? new Settings() : settings.copy();
        return s;
    }

    public int ore(int index) {
        return index >= 0 && index < ore.length ? ore[index] : 0;
    }

    public void addOre(int index, int amount) {
        if (index >= ore.length) ore = java.util.Arrays.copyOf(ore, index + 1);
        ore[index] += amount;
    }

    public WeaponState weapon(String type) {
        for (WeaponState w : weapons) if (w.type.equals(type)) return w;
        return null;
    }

    public boolean owns(String type) {
        return weapon(type) != null;
    }

    public WeaponState equippedWeapon() {
        return equipped == null || equipped.isEmpty() ? null : weapon(equipped);
    }

    public boolean hasAnyWeapon() {
        return !weapons.isEmpty();
    }

    public boolean isRoomCleared(String roomId) {
        return clearedRooms.contains(roomId);
    }

    public boolean isBossDefeated(String bossId) {
        return defeatedBosses.contains(bossId);
    }

    public boolean hasShortcut(String zoneId) {
        return shortcuts.contains(zoneId);
    }

    public MineRoomState mineRoom(String roomId) {
        for (MineRoomState m : mineRooms) if (m.roomId.equals(roomId)) return m;
        return null;
    }
}
