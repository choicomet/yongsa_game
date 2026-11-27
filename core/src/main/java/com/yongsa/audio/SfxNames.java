package com.yongsa.audio;

/** 게임 쪽에서 상황에 맞는 효과음 이름을 고를 때 쓴다 (규칙이 없는 이름이 나가 소리가 안 나는 일이 없게). */
public final class SfxNames {
    private SfxNames() {
    }

    public record BossIntro(String arrival, String windup, String impact) {}

    private static final java.util.Map<String, BossIntro> INTROS = java.util.Map.of(
            "golem", new BossIntro("bs_intro_golem", "bs_slam", "intro_hit_pound"),
            "knight", new BossIntro("bs_intro_knight", "bs_spin", "intro_hit_spin"),
            // 탑의 수문장 등장은 번개 두 번 (지용 11/27, tools/make_combat_sfx.py)
            "warden", new BossIntro("bs_intro_warden_bolts", "bs_lightning", "intro_hit_bolt"),
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
}
