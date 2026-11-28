package com.yongsa.audio;

/** 게임 쪽에서 상황에 맞는 효과음 이름을 고를 때 쓴다 (규칙이 없는 이름이 나가 소리가 안 나는 일이 없게). */
public final class SfxNames {
    private SfxNames() {
    }

    /** windup 이 null 이면 힘 모으기 때 소리를 내지 않는다. */
    public record BossIntro(String arrival, String windup, String impact) {}

    private static final java.util.Map<String, BossIntro> INTROS = java.util.Map.of(
            "golem", new BossIntro("bs_intro_golem", "bs_slam", "intro_hit_pound"),
            "knight", new BossIntro("bs_intro_knight", "bs_spin", "intro_hit_spin"),
            // 탑의 수문장 등장은 실제 벼락 녹음 (지용 11/27, tools/make_combat_sfx.py).
            // 벼락 뒤 힘 모으는 '올라오는' 소리(bs_lightning)는 지용 요청으로 뺐다 → windup 없음(null)
            "warden", new BossIntro("bs_intro_warden_bolts", null, "intro_hit_bolt"),
            "demonlord", new BossIntro("bs_intro_demonlord", "bs_darkorb", "intro_hit_whip"));
    private static final BossIntro DEFAULT_INTRO = new BossIntro("roar", "cast", "slam");

    public static BossIntro bossIntro(String sprite) {
        return sprite == null ? DEFAULT_INTRO : INTROS.getOrDefault(sprite, DEFAULT_INTRO);
    }

    /** 보스·몹 장판 패턴이 터질 때의 소리. 모양(style)마다 다르고, 규칙이 없으면 쿵 소리. */
    public static String hazard(String style) {
        String n = "hz_" + style;
        return SfxMix.CUES.containsKey(n) ? n : "slam";
    }

    /**
     * 보스가 구슬·검기를 쏠 때의 소리 (지용 2026-11-27): 혈월의 기사 shot_knight, 탑의 수문장 shot_warden, 마왕 shot_demonlord.
     * 다른 보스와 몹은 예전 소리(fallback).
     */
    public static String bossShot(String sprite, String fallback) {
        return boss("shot_", sprite, fallback);
    }

    /** 마왕 어둠 구체가 터질 때의 소리 (orbBurst_demonlord). */
    public static String bossOrbBurst(String sprite, String fallback) {
        return boss("orbBurst_", sprite, fallback);
    }

    /**
     * 보스 포효 (지용 2026-11-28): roar_golem, roar_knight, roar_warden, roar_demonlord 녹음.
     * 규칙이 없는 보스와 몹은 예전 합성 포효(roar).
     */
    public static String bossRoar(String sprite) {
        return boss("roar_", sprite, "roar");
    }

    private static String boss(String prefix, String sprite, String fallback) {
        String n = prefix + sprite;
        return sprite != null && SfxMix.CUES.containsKey(n) ? n : fallback;
    }
}
