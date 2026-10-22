package com.yongsa.model;

import com.yongsa.config.Balance;
import com.yongsa.config.DungeonDef;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 영구 기록 (구현안 4장): 처치한 보스, 가진 구슬, 성검 봉인 해제, 최종 보스. 침대로는 바뀌지 않는다.
 * 플레이 기록(엔딩 화면용)도 여기 둔다.
 */
public class Progress {

    private final Set<String> defeatedBosses = new LinkedHashSet<>();
    private final Set<String> orbs = new LinkedHashSet<>();
    private boolean sealBroken;
    private boolean finalDefeated;
    /** 처음 들어가는 광물방 보장(FR-채집-09)을 이미 썼는지. */
    private boolean firstOreRoomUsed;
    private boolean tutorialDone;
    /**
     * 마왕 본모습의 반전 마법진이 뒤바꾸는 키. 처음 밟을 때 무작위로 정하고 그 뒤로는 고정 (저장된다).
     * 예: "LR|WHIRL_DODGE" = 좌우 반전 + Q와 회피(Space) 교환. 비어 있으면 아직 안 정함.
     */
    private String inversion = "";

    public String inversion() { return inversion; }
    public void setInversion(String s) { inversion = s == null ? "" : s; }

    /** 마왕 전에 던전 출구로 나가려다 되돌아선 횟수 (5번을 넘기면 나갈 수 있다). */
    public int exitRefusals;

    public int enhanceCount, destroyCount, awakenCount, kills, deaths;

    public boolean defeated(String dungeonId) { return defeatedBosses.contains(dungeonId); }
    public Set<String> defeatedBosses() { return Collections.unmodifiableSet(defeatedBosses); }
    void markDefeated(String dungeonId) { defeatedBosses.add(dungeonId); }

    public boolean isOpen(DungeonDef d) {
        return d.requires.isEmpty() || defeated(d.requires);
    }

    public boolean hasOrb(String dungeonId) { return orbs.contains(dungeonId); }
    public Set<String> orbs() { return Collections.unmodifiableSet(orbs); }
    public void addOrb(String dungeonId) { orbs.add(dungeonId); }

    public boolean allOrbs(Balance b) {
        for (DungeonDef d : b.dungeons) if (!orbs.contains(d.id)) return false;
        return true;
    }

    public boolean sealBroken() { return sealBroken; }
    void breakSeal() { sealBroken = true; }

    public boolean finalDefeated() { return finalDefeated; }

    /** 처음 잡은 보스 수 (마왕 포함). 캠프가 이만큼 밝아진다. */
    public int firstClears() { return defeatedBosses.size() + (finalDefeated ? 1 : 0); }
    public void markFinalDefeated() { finalDefeated = true; }

    public boolean firstOreRoomUsed() { return firstOreRoomUsed; }
    public void useFirstOreRoom() { firstOreRoomUsed = true; }

    public boolean tutorialDone() { return tutorialDone; }
    public void finishTutorial() { tutorialDone = true; }

    /** 저장에서 복원. */
    public void restore(Collection<String> bosses, Collection<String> orbList, boolean seal, boolean fin,
                        boolean firstOre, boolean tutorial) {
        defeatedBosses.clear();
        defeatedBosses.addAll(bosses);
        orbs.clear();
        orbs.addAll(orbList);
        sealBroken = seal;
        finalDefeated = fin;
        firstOreRoomUsed = firstOre;
        tutorialDone = tutorial;
    }
}
