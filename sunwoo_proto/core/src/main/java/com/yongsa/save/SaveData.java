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
    /** 흡수한 검 종류 → 강화 단계 (성검이 나눠 갖는 힘). */
    public Map<String, Integer> absorbedSwords = new LinkedHashMap<>();
    public int maxHp;
    public float hp;

    public List<String> defeatedBosses = new ArrayList<>();
    public List<String> orbs = new ArrayList<>();
    public List<OrbSave> orbDrops = new ArrayList<>();
    /** 아직 열지 않은 보스 보물상자. */
    public List<ChestSave> chests = new ArrayList<>();
    public boolean sealBroken, finalDefeated, firstOreRoomUsed, tutorialDone;
    public int enhanceCount, destroyCount, awakenCount, kills, deaths;
    public int greatCount, downCount;
    /** 강화 숙련도 레벨과 그 레벨에서 쌓은 경험치 (모든 검 공유). 예전 저장은 0. */
    public int masteryLevel, masteryExp;
    /** 반전 마법진이 바꾸는 키 (한 번 정해지면 고정). */
    public String inversion = "";
    /** 마왕 전에 던전 출구에서 되돌아선 횟수. */
    public int exitRefusals;

    public List<RoomSave> rooms = new ArrayList<>();
    public float heroX, heroY;
    /** 안전한 곳(캠프·마을·출구 통로)에서 저장했는지: 그러면 그 자리에서 다시 시작. */
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

    public static class ChestSave {
        public String dungeon;
        public float x, y;
        public int ore;
        public boolean essence;
    }

    public static class OrbSave {
        public String dungeon;
        public float x, y;
    }
}
