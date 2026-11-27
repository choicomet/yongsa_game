package com.yongsa.audio;

/** 게임 쪽에서 상황에 맞는 효과음 이름을 고를 때 쓴다 (규칙이 없는 이름이 나가 소리가 안 나는 일이 없게). */
public final class SfxNames {
    private SfxNames() {
    }

    /** 보스·몹 장판 패턴이 터질 때의 소리. 모양(style)마다 다르고, 규칙이 없으면 쿵 소리. */
    public static String hazard(String style) {
        String n = "hz_" + style;
        return SfxMix.CUES.containsKey(n) ? n : "slam";
    }

    /**
     * 보스가 패턴을 시작하며 힘을 모을 때의 소리 (지용 2026-11-27). 보스마다 다르다: charge_golem 등.
     * 0.8초 동안 모으다가 0.84초에 '딱' 하는 소리 (tools/make_boss_charge_sfx.py). 파일이 없는 보스는 fallback.
     */
    public static String bossCharge(String sprite, String fallback) {
        String n = "charge_" + sprite;
        return SfxMix.CUES.containsKey(n) ? n : fallback;
    }
}
