package com.yongsa.audio;

/** 게임 쪽에서 상황에 맞는 효과음 이름을 고를 때 쓴다 (규칙이 없는 이름이 나가 소리가 안 나는 일이 없게). */
public final class SfxNames {
    private SfxNames() {
    }

    /** 보스가 패턴을 시작할 때(예고 동안)의 소리. 패턴 이름이 소리 이름과 다른 것만 옮기고, 규칙이 없으면 포효. */
    public static String pattern(String p) {
        String n;
        switch (p) {
            case "spearstand": n = "bs_spear"; break;
            case "spinslash": n = "bs_spin"; break;
            case "rockfall": n = "bs_boulder"; break;
            case "bonespike": n = "bs_summon"; break;
            case "bloodpool": n = "bs_crescent"; break;
            case "whip": n = "bs_combo"; break;
            default: n = "bs_" + p;
        }
        return SfxMix.CUES.containsKey(n) ? n : "roar";
    }

    /** 보스·몹 장판 패턴이 터질 때의 소리. 모양(style)마다 다르고, 규칙이 없으면 쿵 소리. */
    public static String hazard(String style) {
        String n = "hz_" + style;
        return SfxMix.CUES.containsKey(n) ? n : "slam";
    }
}
