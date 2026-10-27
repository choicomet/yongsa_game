package com.yongsa.model;

/**
 * 보스 보물상자 (2026-10-12 결정). 보스를 쓰러뜨리면 보스방에 나타나고, E로 열면 그 던전 광물 30~40개가 나온다.
 * 처음 쓰러뜨린 보스의 상자에서는 보스의 정수도 튀어나온다 (주우면 성검 해방에 쓰인다).
 * 그림은 던전 테마를 따른다 (chest_left / chest_right / chest_top).
 */
public class TreasureChest {
    public final String dungeon;
    public final float x, y;
    /** 들어 있는 광물 수. */
    public int ore;
    /** 보스의 정수가 들어 있는지. */
    public boolean essence;
    public boolean opened;
    /** 열린 뒤 지난 시간 (뚜껑·빛 연출). */
    public float openTime;

    public TreasureChest(String dungeon, float x, float y, int ore, boolean essence) {
        this.dungeon = dungeon;
        this.x = x;
        this.y = y;
        this.ore = ore;
        this.essence = essence;
    }

    public String sprite() {
        return "chest_" + dungeon;
    }
}
