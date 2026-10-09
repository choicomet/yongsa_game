package com.yongsa.model;

import com.yongsa.config.Balance;

/**
 * 진행 상태에서 파생되는 값과 영구 진행을 바꾸는 규칙.
 * 목표 문구, 잠금 이유, 강화 상한은 모두 이 클래스의 같은 계산을 사용한다.
 */
public final class Progress {
    private Progress() {}

    public static int bossCount(GameState s) {
        return s.defeatedBosses.size();
    }

    public static int enhanceCap(Balance b, GameState s) {
        int n = Math.min(bossCount(s), b.enhanceCapByBossCount.length - 1);
        return Math.min(b.enhanceCapByBossCount[n], b.maxLevel());
    }

    public static boolean isZoneUnlocked(Balance b, GameState s, String zoneId) {
        Balance.ZoneDef z = b.zone(zoneId);
        return z.requiresBoss == null || z.requiresBoss.isEmpty() || s.isBossDefeated(z.requiresBoss);
    }

    /** 잠긴 구역 문에 표시할 문구. 열려 있으면 null. */
    public static String zoneLockReason(Balance b, GameState s, String zoneId) {
        if (isZoneUnlocked(b, s, zoneId)) return null;
        Balance.ZoneDef z = b.zone(zoneId);
        return b.boss(z.requiresBoss).name + " 처치 후 개방";
    }

    public static boolean isWeaponUnlocked(Balance b, GameState s, String weaponType) {
        Balance.WeaponDef w = b.weapon(weaponType);
        return w.unlockBoss == null || w.unlockBoss.isEmpty() || s.isBossDefeated(w.unlockBoss);
    }

    public static String weaponLockReason(Balance b, GameState s, String weaponType) {
        if (isWeaponUnlocked(b, s, weaponType)) return null;
        return b.boss(b.weapon(weaponType).unlockBoss).name + " 처치 후 해금";
    }

    /** 각성 충격파는 첫 보스 처치로 영구 해금된다. */
    public static boolean isAwakeningUnlocked(Balance b, GameState s) {
        return s.isBossDefeated(b.zones[0].boss);
    }

    public static boolean isComplete(Balance b, GameState s) {
        for (Balance.ZoneDef z : b.zones) if (!s.isBossDefeated(z.boss)) return false;
        return true;
    }

    /** 아직 보스를 처치하지 않은 가장 앞 구역. 모두 처치했으면 null. */
    public static Balance.ZoneDef currentZone(Balance b, GameState s) {
        for (Balance.ZoneDef z : b.zones) if (!s.isBossDefeated(z.boss)) return z;
        return null;
    }

    // ---- 영구 진행 변경 (한 번만 적용되도록 모두 멱등) ----

    /** 일반 방 클리어. 처음일 때만 보상을 지급하고 지급한 광물 수를 돌려준다. */
    public static int onRoomCleared(Balance b, GameState s, String roomId, String zoneId) {
        if (s.isRoomCleared(roomId)) return 0;
        int reward = b.zone(zoneId).roomReward;
        s.clearedRooms.add(roomId);
        s.minerals += reward;
        return reward;
    }

    /** 보스 최초 처치. 보상과 해금이 같은 상태 변경 안에서 확정된다. */
    public static int onBossDefeated(Balance b, GameState s, String bossId) {
        if (s.isBossDefeated(bossId)) return 0;
        int reward = b.zoneOfBoss(bossId).bossReward;
        s.defeatedBosses.add(bossId);
        s.minerals += reward;
        return reward;
    }

    /** 보스 앞 공간 최초 도달. 새로 해금되면 true. */
    public static boolean onAntechamberReached(GameState s, String zoneId) {
        if (s.hasShortcut(zoneId)) return false;
        s.shortcuts.add(zoneId);
        return true;
    }

    /** 침대 사용: 채굴 회차를 1 올리고 이전 회차의 광맥 상태를 지운다. 영구 진행은 그대로다. */
    public static void onBedUsed(GameState s) {
        s.miningCycle++;
        s.mineRooms.clear();
    }

    // ---- 다음 할 일 ----

    /** 화면 왼쪽 위에 표시할 현재 목표 하나. */
    public static String objective(Balance b, GameState s) {
        Balance.ZoneDef first = b.zones[0];
        if (!s.hasAnyWeapon()) {
            int need = b.weapon(Ids.SWORD).craftCost;
            if (s.minerals < need) return first.direction + " 광물방에서 광물 " + need + "개 모으기";
            return "캠프 대장간에서 " + b.weapon(Ids.SWORD).name + " 제작하기";
        }
        Balance.ZoneDef zone = currentZone(b, s);
        if (zone == null) return "모든 봉인을 해제했습니다";

        if (zone == first) {
            if (!s.isRoomCleared(Ids.battleA(zone.id))) return first.direction + " " + first.name + "의 첫 전투방 돌파하기";
            if (!s.firstEnhanceDone && !s.hasShortcut(zone.id)) return "대장간에서 강화해 보기";
        } else if (!s.isRoomCleared(Ids.battleA(zone.id)) && !s.hasShortcut(zone.id)) {
            return "중앙 캠프로 돌아가 " + zone.direction + " " + Ko.ro(zone.name) + " 향하기";
        }
        if (!s.hasShortcut(zone.id)) return "남은 전투방을 지나 보스 앞까지 이동하기";
        return "지름길을 이용해 " + b.boss(zone.boss).name + " 공략하기";
    }
}
