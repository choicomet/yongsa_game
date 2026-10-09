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
    public boolean awakenGuideSeen;
    public boolean firstEnhanceDone;

    // 채굴
    public int miningCycle;
    public ArrayList<MineRoomState> mineRooms = new ArrayList<>();

    // 무작위 상태
    public long enhanceSeed;
    public long enhanceUses;
    public long veinSeed;
    public long veinUses;

    // 사용자 설정
    public Settings settings = new Settings();

    public static GameState newGame(long seed, int dataVersion, Settings settings) {
        GameState s = new GameState();
        s.dataVersion = dataVersion;
        s.enhanceSeed = seed;
        s.veinSeed = seed * 31L + 0x5DEECE66DL;
        s.settings = settings == null ? new Settings() : settings.copy();
        return s;
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
