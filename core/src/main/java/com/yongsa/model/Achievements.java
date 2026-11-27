package com.yongsa.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 달성한 업적 목록과 그 효과. 목록은 설정 파일에 함께 저장돼 새 게임을 시작해도 남는다.
 * 효과는 달성한 것 모두가 함께 적용된다.
 */
public final class Achievements {
    public static final double ENHANCE_BONUS = 2.0;
    public static final int CRAFT_DISCOUNT = 3;
    public static final int VEIN_BONUS = 5;
    public static final int MAX_HP_BONUS = 5;
    public static final float MOB_DAMAGE = 1.1f;
    public static final float ROLL_COOLDOWN = 0.95f;

    /** 저장되는 목록 (업적 이름). 설정의 목록을 그대로 쓰므로 여기에 넣으면 설정에도 들어간다. */
    private final Collection<String> unlocked;

    public Achievements(Collection<String> unlocked) {
        this.unlocked = unlocked != null ? unlocked : new ArrayList<>();
    }

    /** 아무 업적도 없는 상태 (테스트, 예전 저장). */
    public static Achievements none() {
        return new Achievements(new ArrayList<>());
    }

    public boolean has(Achievement a) {
        return unlocked.contains(a.name());
    }

    public int count() {
        int n = 0;
        for (Achievement a : Achievement.values()) if (has(a)) n++;
        return n;
    }

    /** 한 판 도중에 새로 채워진 업적을 달성 처리하고 돌려준다. */
    public List<Achievement> checkDuringRun(Stats s) {
        List<Achievement> out = new ArrayList<>();
        for (Achievement a : Achievement.values()) {
            if (!has(a) && a.duringRun(s)) unlock(a, out);
        }
        return out;
    }

    /** 마왕을 쓰러뜨렸을 때 판정하는 업적. 도중 업적도 한 번 더 본다. */
    public List<Achievement> checkClear(Stats s) {
        List<Achievement> out = checkDuringRun(s);
        for (Achievement a : Achievement.values()) {
            if (!has(a) && a.atClear(s)) unlock(a, out);
        }
        return out;
    }

    public void unlock(Achievement a) {
        if (!has(a)) unlocked.add(a.name());
    }

    private void unlock(Achievement a, List<Achievement> out) {
        unlocked.add(a.name());
        out.add(a);
    }

    // ───────────────────────── 효과

    /** 강화 성공 확률에 더하는 %p. */
    public double enhanceBonus() {
        return has(Achievement.HAMMER) ? ENHANCE_BONUS : 0;
    }

    /** 검을 만들 때 깎아 주는 광물 수. */
    public int craftDiscount() {
        return has(Achievement.COLLECTOR) ? CRAFT_DISCOUNT : 0;
    }

    /** 광맥 하나를 다 캤을 때 더 받는 광물 수. */
    public int veinBonus() {
        return has(Achievement.MINER) ? VEIN_BONUS : 0;
    }

    public int maxHpBonus() {
        return has(Achievement.FLAWLESS) ? MAX_HP_BONUS : 0;
    }

    /** 보스가 아닌 몬스터에게 주는 피해 배율. */
    public float mobDamage() {
        return has(Achievement.UNYIELDING) ? MOB_DAMAGE : 1f;
    }

    /** 구르기 대기 시간 배율. */
    public float rollCooldown() {
        return has(Achievement.SWIFT) ? ROLL_COOLDOWN : 1f;
    }
}
