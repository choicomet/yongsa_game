package com.yongsa.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 저장되는 게임 상태 전부. 화면 코드와 무관한 순수 데이터다.
 * 영구 기록(보스, 흡수, 봉인)과 침대로 초기화되는 방 기록(rooms)을 나눠 둔다.
 */
public class GameState {
    public Map<OreType, Integer> ores = new EnumMap<>(OreType.class);
    public List<Weapon> weapons = new ArrayList<>();
    public int equippedUid;
    public int nextUid = 1;

    /** 용사에게 흡수된 각성 무기의 정의 ID. 무기는 사라지고 힘만 용사에게 남는다. */
    public List<String> imprints = new ArrayList<>();
    /** 힘을 흡수한 보스의 던전 ID (잡은 순서). */
    public List<String> bossesDefeated = new ArrayList<>();
    /** 아직 열지 않은 보스 보물상자가 있는 방 ID (보스를 잡을 때마다 하나 생긴다). */
    public List<String> chests = new ArrayList<>();
    public boolean unsealed;
    /** 마왕 전에 마을로 나가려다 되돌아선 횟수 (선우 버전: 다섯 번 뒤에는 체념하고 나간다). */
    public int exitRefusals;
    public boolean finalBossDefeated;

    public int hp;

    public Map<String, RoomRecord> rooms = new HashMap<>();
    public boolean firstOreRoomDone;

    /** 이어하기 위치. */
    public float resumeX, resumeY;
    /** 저장할 때의 맵 버전 (WorldMap.VERSION). 다르면 이어하기 위치와 광맥 자리를 버린다. */
    public int mapVersion;

    public List<String> enhanceLog = new ArrayList<>();
    public Stats stats = new Stats();

    public int ore(OreType t) {
        return ores.getOrDefault(t, 0);
    }

    public void addOre(OreType t, int n) {
        ores.put(t, Math.max(0, ore(t) + n));
    }

    public Weapon equipped() {
        return weapon(equippedUid);
    }

    public Weapon weapon(int uid) {
        for (Weapon w : weapons) if (w.uid == uid) return w;
        return null;
    }

    public Weapon weaponByDef(String defId) {
        for (Weapon w : weapons) if (w.defId.equals(defId)) return w;
        return null;
    }

    public Weapon newWeapon(String defId) {
        Weapon w = new Weapon(nextUid++, defId);
        weapons.add(w);
        return w;
    }

    public RoomRecord room(String id) {
        return rooms.computeIfAbsent(id, k -> new RoomRecord());
    }

    public boolean bossDefeated(String dungeonId) {
        return bossesDefeated.contains(dungeonId);
    }
}
