package com.yongsa.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 저장 파일 내용 (FR-저장-02). Gson으로 JSON에 쓴다.
 * 설정(음량 등)은 "새 게임"에 날아가지 않도록 여기가 아니라 Preferences에 따로 둔다.
 */
public class SaveData {
    public static final int VERSION = 1;

    public int version = VERSION;
    public Map<String, Integer> ores = new LinkedHashMap<>();
    public List<WeaponSave> weapons = new ArrayList<>();
    public int equippedUid;
    public List<String> absorbed = new ArrayList<>();
    public int maxHp;
    public float hp;

    public List<String> defeatedBosses = new ArrayList<>();
    public List<String> orbs = new ArrayList<>();
    public List<OrbSave> orbDrops = new ArrayList<>();
    public boolean sealBroken, finalDefeated, firstOreRoomUsed, tutorialDone;
    public int enhanceCount, destroyCount, awakenCount, kills, deaths;

    public List<RoomSave> rooms = new ArrayList<>();
    public float heroX, heroY;
    public boolean inCamp;
    public String lastRoomId;
    public float playTime;

    public static class WeaponSave {
        public int uid;
        public String def;
        public int level;
        public boolean awakened;
        public int enhanceSpent;
    }

    public static class RoomSave {
        public String id;
        public boolean cleared, seen, veinsGenerated;
        public List<VeinSave> veins = new ArrayList<>();
    }

    public static class VeinSave {
        public float x, y;
        public int hitsLeft, amount;
    }

    public static class OrbSave {
        public String dungeon;
        public float x, y;
    }
}
