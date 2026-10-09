package com.yongsa.model;

/**
 * 저장 후에도 바뀌지 않는 ID 규칙.
 * 방 ID는 "{구역}_{역할}" 형식이며 Tiled 맵의 rooms 오브젝트 이름과 같다.
 */
public final class Ids {
    private Ids() {}

    public static final String SWORD = "sword";
    public static final String HAMMER = "hammer";

    public static final String ZONE_MINE = "mine";
    public static final String ZONE_FORTRESS = "fortress";
    public static final String ZONE_TEMPLE = "temple";

    public static final String BOSS_MINE = "boss_mine";
    public static final String BOSS_FORTRESS = "boss_fortress";
    public static final String BOSS_TEMPLE = "boss_temple";

    public static final String CAMP = "camp";

    public static String mineRoom(String zone) { return zone + "_ore"; }
    public static String battleA(String zone) { return zone + "_battle_a"; }
    public static String battleB(String zone) { return zone + "_battle_b"; }
    public static String antechamber(String zone) { return zone + "_ante"; }
    public static String bossRoom(String zone) { return zone + "_boss"; }
}
