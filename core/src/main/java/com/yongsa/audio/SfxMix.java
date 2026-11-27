package com.yongsa.audio;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 효과음 믹스 규칙 (소리 장치 없이 돌아가서 테스트할 수 있다).
 * <ul>
 *   <li>소리마다 목표 크기를 정해 두고, 만든 파형의 실제 크기를 재서 맞춘다. 예전에는 휘두르기가 쿵 소리보다 16dB 작았다.</li>
 *   <li>같은 소리가 너무 촘촘히 나면 건너뛴다 (한 번 휘둘러 셋을 때리면 타격음이 세 겹으로 쌓이던 것).</li>
 *   <li>짧은 시간에 소리가 많이 몰리면 덜 중요한 소리부터 줄인다.</li>
 *   <li>포효·각성 같은 큰 소리가 나는 동안에는 작은 소리를 줄이고, 배경음악도 잠깐 줄인다 (덕킹).</li>
 *   <li>상황 변형: 보스 타격은 낮고 무겁게, 체력이 적을 때 맞으면 더 크게, 연타하면 타격음이 조금씩 올라간다.</li>
 * </ul>
 */
final class SfxMix {

    /** 소리 한 종류의 규칙. */
    static final class Cue {
        /** 원본 파형 이름 (변형 소리는 다른 소리의 파형을 빌려 쓴다). */
        final String base;
        /** 목표 크기: 가장 큰 50ms 구간의 RMS, dBFS. */
        final float targetDb;
        /** 같은 소리 사이 최소 간격 (초). */
        final float minGap;
        /** 높낮이 흔들기 폭 (0.1 이면 ±10%). */
        final float vary;
        /** 높낮이 기준. */
        final float pitch;
        /** 우선순위: 0 잔소리(발소리·메뉴), 1 보통 전투, 2 중요(맞음·타격), 3 큰 사건. */
        final int priority;
        /** 배경음악을 얼마나 줄일지 (0~1)와 그 시간 (초). */
        final float duck, duckHold;

        Cue(String base, float targetDb, float minGap, float vary, float pitch, int priority, float duck, float duckHold) {
            this.base = base;
            this.targetDb = targetDb;
            this.minGap = minGap;
            this.vary = vary;
            this.pitch = pitch;
            this.priority = priority;
            this.duck = duck;
            this.duckHold = duckHold;
        }
    }

    /** 실제로 틀 소리. */
    static final class Play {
        String base;
        float gain;
        float pitch;
        float duck, duckHold;
    }

    static final Map<String, Cue> CUES = new HashMap<>();

    private static void cue(String name, String base, float db, float gap, float vary, float pitch, int prio, float duck, float hold) {
        CUES.put(name, new Cue(base, db, gap, vary, pitch, prio, duck, hold));
    }

    private static void cue(String name, float db, float gap, float vary, int prio) {
        cue(name, name, db, gap, vary, 1f, prio, 0f, 0f);
    }

    static {
        // ── 전투: 손맛을 주는 소리. 배경음악 위로 또렷해야 한다
        cue("swing", -15f, 0.05f, 0.08f, 1);
        cue("hit", -12f, 0.045f, 0.06f, 2);
        cue("hitBoss", "hit", -10.5f, 0.06f, 0.05f, 0.76f, 2, 0.12f, 0.08f);
        cue("kill", -13f, 0.06f, 0.08f, 2);
        cue("bossDown", "kill", -9f, 0.3f, 0f, 0.62f, 3, 0.55f, 0.6f);
        cue("block", -14f, 0.06f, 0.05f, 1);
        cue("hurt", "hurt", -11f, 0.15f, 0.06f, 1f, 2, 0.15f, 0.12f);
        cue("hurtLow", "hurt", -9.5f, 0.15f, 0.04f, 0.82f, 3, 0.3f, 0.25f);
        cue("monsterAttack", -17f, 0.09f, 0.1f, 1);
        cue("dash", -17f, 0.1f, 0.06f, 1);
        cue("spin", -16f, 0.1f, 0.05f, 1);
        cue("cast", -15f, 0.08f, 0.06f, 1);
        cue("slam", "slam", -11f, 0.12f, 0.05f, 1f, 2, 0.2f, 0.15f);
        // ── 큰 사건: 배경음악을 잠깐 내려 길을 내 준다
        cue("roar", "roar", -11f, 0.5f, 0.03f, 1f, 3, 0.45f, 0.7f);
        for (String boss : new String[]{"golem", "knight", "warden", "demonlord"}) {
            var intro = SfxNames.bossIntro(boss);
            cue(intro.arrival(), intro.arrival(), -10f, .4f, .015f, 1f, 3, .34f, .5f);
            cue(intro.windup(), intro.windup(), -7.5f, .3f, .03f, 1f, 2, .15f, .25f);
        }
        // 번개 두 번은 길어서(2.6초) 끝날 때까지 곡을 조금 더 줄인다
        cue("bs_intro_warden_bolts", "bs_intro_warden_bolts", -9f, .4f, 0f, 1f, 3, .45f, 2.2f);
        // 마왕 변신: 1페이즈 곡이 끊긴 사이 '콰아앙' + 낮은 웃음 (GameScreen 이 2페이즈 곡도 잠깐 줄인다)
        cue("bs_demonlord_phase2", "bs_demonlord_phase2", -7f, 1f, 0f, 1f, 3, 0f, 0f);
        cue("intro_hit_pound", "hz_pound", -7.5f, .12f, .04f, 1f, 2, .25f, .2f);
        cue("intro_hit_spin", "hz_spin", -8.5f, .08f, .06f, 1f, 1, 0f, 0f);
        cue("intro_hit_bolt", "hz_bolt", -8.5f, .08f, .06f, 1f, 1, 0f, 0f);
        cue("intro_hit_whip", "hz_whip", -8.5f, .08f, .06f, 1f, 1, 0f, 0f);
        cue("awaken", "awaken", -12f, 0.3f, 0f, 1f, 3, 0.4f, 0.5f);
        cue("unseal", "unseal", -11f, 0.5f, 0f, 1f, 3, 0.5f, 1.1f);
        cue("destroy", "destroy", -11f, 0.3f, 0f, 1f, 3, 0.45f, 0.55f);
        cue("death", "death", -12f, 0.5f, 0f, 1f, 3, 0.5f, 0.8f);
        cue("warp", "warp", -14f, 0.3f, 0f, 1f, 3, 0.35f, 0.5f);
        // ── 대장간·아이템
        cue("mine", -15f, 0.05f, 0.08f, 1);
        cue("ore", -15f, 0.08f, 0f, 1);
        cue("success", "success", -13f, 0.1f, 0f, 1f, 3, 0.25f, 0.3f);
        cue("keep", -15f, 0.1f, 0f, 1);
        cue("down", -14f, 0.1f, 0f, 2);
        cue("craft", -14f, 0.1f, 0f, 2);
        cue("potion", -15f, 0.1f, 0f, 2);
        // ── 문·발소리·메뉴: 배경에 깔리는 소리
        cue("door", -16f, 0.15f, 0.04f, 1);
        cue("step", -22f, 0.12f, 0.06f, 0);
        cue("select", -20f, 0.03f, 0f, 0);
        // ── 용사 스킬 (해성 2026-11-26: 효과음은 기본 공격·구르기 대신 스킬·보스 패턴에 쓴다)
        // 파일: assets/audio/sfx/sk_*.wav (tools/make_skill_sfx.py 로 만든 것, 크게 구움 -7dB)
        cue("sk_cleave", "sk_cleave", -7f, 0.1f, 0.03f, 1f, 3, 0.3f, 0.35f);         // 흑철 대지 가르기: 바위가 갈라지는 내려찍기
        cue("sk_cleaveHit", "sk_cleaveHit", -9f, 0.08f, 0.06f, 1f, 2, 0f, 0f);
        cue("sk_crescent", "sk_crescent", -7f, 0.1f, 0.03f, 1f, 3, 0.25f, 0.3f);     // 혈월참: 휘어지는 바람 + 어두운 단조 울림
        cue("sk_crescentHit", "sk_crescentHit", -9f, 0.06f, 0.06f, 1f, 2, 0f, 0f);
        cue("sk_strike", "sk_strike", -8f, 0.1f, 0.02f, 1f, 2, 0.15f, 0.3f);         // 천공 낙뢰: 전기가 모이는 소리
        cue("sk_strikeHit", "sk_strikeHit", -7f, 0.07f, 0.06f, 1f, 3, 0.35f, 0.4f);  // 번개 '딱' + 천둥
        cue("sk_beam", "sk_beam", -7f, 0.1f, 0f, 1f, 3, 0.3f, 0.45f);                // 성광 검기: 종 + 밝은 장조 반짝임
        cue("sk_beamHit", "sk_beamHit", -9f, 0.06f, 0.05f, 1f, 2, 0f, 0f);
        // ── 대장간 망치 (fg_hammer): 모루를 치는 "깡". 망치가 내려칠 때만. 강화 결과음은 원래 녹음 그대로
        cue("fg_hammer", "fg_hammer", -7f, 0.05f, 0.05f, 1f, 2, 0f, 0f);
        // ── 보스·몹 장판 패턴이 터질 때 (hz_<모양>). 여러 개가 한꺼번에 터져도 최소 간격으로 한 번만
        cue("hz_boulder", "destroy", -10f, 0.12f, 0.05f, 0.55f, 2, 0.25f, 0.2f); // 골렘 바위: 낮게 부서지는 소리
        cue("hz_rock", "destroy", -12f, 0.1f, 0.08f, 0.65f, 1, 0f, 0f);
        cue("hz_pound", "slam", -10f, 0.12f, 0.04f, 0.7f, 2, 0.25f, 0.2f);     // 골렘 연속 내려찍기
        cue("hz_quake", "slam", -12f, 0.12f, 0.05f, 0.6f, 1, 0f, 0f);
        cue("hz_punch", "destroy", -14f, 0.06f, 0.08f, 0.6f, 1, 0f, 0f);       // 골렘 주먹 충격파: 차례로 부서지는 소리
        cue("hz_leap", "slam", -11f, 0.12f, 0.05f, 0.75f, 2, 0.15f, 0.12f);
        cue("hz_spear", "block", -13f, 0.08f, 0.06f, 0.85f, 1, 0f, 0f);        // 혈월의 기사 창: 쇳소리
        cue("hz_flame", "cast", -13f, 0.1f, 0.06f, 0.7f, 1, 0f, 0f);
        cue("hz_spin", "swing", -11f, 0.12f, 0.04f, 0.7f, 2, 0f, 0f);          // 회전 베기
        cue("hz_counter", "swing", -11f, 0.1f, 0.04f, 0.85f, 2, 0f, 0f);       // 혈월의 기사 방패 반격
        cue("hz_whip", "monsterAttack", -11f, 0.08f, 0.06f, 0.7f, 2, 0f, 0f);  // 마왕 채찍
        cue("hz_bolt", "destroy", -12f, 0.07f, 0.1f, 1.45f, 1, 0f, 0f);        // 번개: 높고 날카롭게 깨지는 소리
        cue("hz_cage", "destroy", -12f, 0.1f, 0.08f, 1.3f, 1, 0f, 0f);
        cue("hz_bone", "kill", -12f, 0.08f, 0.08f, 1.2f, 1, 0f, 0f);           // 뼈 가시
        cue("hz_tentacle", "slam", -12f, 0.1f, 0.06f, 0.65f, 1, 0f, 0f);
        cue("hz_brand", "destroy", -10f, 0.15f, 0.04f, 0.8f, 2, 0.2f, 0.2f);   // 마왕의 낙인 폭발
        for (String d : new String[]{"LEFT", "RIGHT", "TOP"}) {
            cue("gateClose_" + d, -15f, 0.2f, 0.03f, 1);
            cue("gateOpen_" + d, -16f, 0.2f, 0.03f, 1);
        }
    }

    /** 파일로 넣은 효과음은 이 크기(가장 큰 50ms 구간 RMS)로 맞춰 왔다고 보고 목표 크기와의 차이만큼 줄이거나 키운다. */
    static final float FILE_REFERENCE_DB = -12f;
    /** 큰 소리가 나는 동안 작은 소리(우선순위 0~1)를 줄이는 정도. */
    static final float UNDER_BIG_GAIN = 0.6f;
    /** 0.25초 안에 이만큼 넘게 나면 덜 중요한 소리를 줄인다. */
    static final int CROWD = 6;
    static final float CROWD_GAIN = 0.65f;
    /** 연타: 이 시간 안에 다시 때리면 높낮이가 한 단계 올라간다 (최대 4단계). */
    static final float COMBO_WINDOW = 0.6f;
    static final float COMBO_STEP = 0.025f;

    /** 만든 파형은 이 크기로 미리 키우거나 줄여 WAV 로 굽는다 (재생 음량은 1을 넘을 수 없어서 여유를 둔다). */
    static final float BAKE_DB = -10f;

    /** 원본 파형 이름 → 구운 뒤의 크기 (dBFS). */
    private final Map<String, Float> level = new HashMap<>();
    private final Map<String, Double> lastPlayed = new HashMap<>();
    private final Random rnd = new Random(11);
    private final double[] recent = new double[16];

    {
        java.util.Arrays.fill(recent, -10);
    }
    private int recentHead;
    private double bigUntil = -1;
    private double lastHit = -10;
    private int combo;

    /**
     * 만든 파형을 재서 BAKE_DB 에 맞추는 배율을 돌려준다 (꼭대기가 0.98 을 넘지 않게).
     * 파형에 이 배율을 곱해 구우면 된다.
     */
    float bake(String base, float[] samples) {
        double db = loudestWindowDb(samples);
        float peak = 1e-6f;
        for (float v : samples) peak = Math.max(peak, Math.abs(v));
        float scale = (float) Math.min(Math.pow(10, (BAKE_DB - db) / 20.0), 0.98f / peak);
        level.put(base, (float) (db + 20 * Math.log10(scale)));
        return scale;
    }

    /** 파일로 바꿔 넣은 소리. 직접 만든 소리(sk_ 스킬, fg_ 대장간 망치)는 더 크게 구워 두었다 (LEVEL_DB). */
    void measureFile(String base) {
        level.put(base, base.startsWith("sk_") || base.startsWith("fg_") || base.startsWith("bs_") || base.startsWith("hz_") ? SKILL_FILE_DB : FILE_REFERENCE_DB);
    }

    static final float SKILL_FILE_DB = -7f;

    /** 코드로 만드는 파형이 없고 파일로만 있는 소리 이름들. */
    static java.util.Set<String> fileOnlyBases(java.util.Set<String> synth) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (Cue c : CUES.values()) if (!synth.contains(c.base)) out.add(c.base);
        return out;
    }

    /**
     * name 을 지금(now 초) 틀어도 되는지 정하고 크기·높낮이를 돌려준다. 건너뛸 때는 null.
     * 규칙이 없는 이름은 원본 그대로 보통 크기로 튼다.
     */
    Play decide(String name, double now) {
        Cue c = CUES.get(name);
        if (c == null) c = new Cue(name, -14f, 0.03f, 0f, 1f, 1, 0f, 0f);
        Float baked = level.get(c.base);
        if (baked == null) return null;
        Double last = lastPlayed.get(name);
        if (last != null && now - last < c.minGap) return null;
        lastPlayed.put(name, now);

        float gain = (float) Math.pow(10, (c.targetDb - baked) / 20.0);
        if (c.priority <= 1 && now < bigUntil) gain *= UNDER_BIG_GAIN;
        int crowd = 0;
        for (double t : recent) if (now - t < 0.25) crowd++;
        if (c.priority <= 1 && crowd >= CROWD) gain *= CROWD_GAIN;
        recent[recentHead] = now;
        recentHead = (recentHead + 1) % recent.length;
        if (c.priority >= 3) bigUntil = Math.max(bigUntil, now + Math.max(0.3f, c.duckHold));

        float pitch = c.pitch;
        if (c.vary > 0) pitch *= 1f + (rnd.nextFloat() * 2f - 1f) * c.vary;
        if ("hit".equals(c.base) && !"kill".equals(name)) {
            combo = now - lastHit < COMBO_WINDOW ? Math.min(4, combo + 1) : 0;
            lastHit = now;
            pitch *= 1f + combo * COMBO_STEP;
        }

        Play p = new Play();
        p.base = c.base;
        p.gain = Math.min(gain, 1f);
        p.pitch = pitch;
        p.duck = c.duck;
        p.duckHold = c.duckHold;
        return p;
    }

    /** 지금 연타 단계 (0~4). */
    int combo() {
        return combo;
    }

    /** 가장 큰 50ms 구간의 RMS (dBFS). 짧은 소리와 긴 소리를 비슷한 기준으로 잰다. */
    static double loudestWindowDb(float[] s) {
        int w = Math.max(1, SfxSynth.RATE / 20);
        double best = 1e-9;
        for (int i = 0; i == 0 || i + w <= s.length; i += Math.max(1, w / 4)) {
            double q = 0;
            int n = 0;
            for (int j = i; j < Math.min(s.length, i + w); j++) {
                q += s[j] * s[j];
                n++;
            }
            best = Math.max(best, Math.sqrt(q / Math.max(1, n)));
        }
        return 20 * Math.log10(best);
    }
}
