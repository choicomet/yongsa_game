package com.yongsa.audio;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SfxMixTest {

    private static SfxMix baked() {
        SfxMix mix = new SfxMix();
        for (Map.Entry<String, float[]> e : SfxSynth.build().entrySet()) {
            float[] s = e.getValue();
            float k = mix.bake(e.getKey(), s);
            for (int i = 0; i < s.length; i++) s[i] *= k;
            assertTrue(peak(s) <= 0.981f, e.getKey() + " 이 깨진다");
        }
        return mix;
    }

    private static float peak(float[] s) {
        float p = 0;
        for (float v : s) p = Math.max(p, Math.abs(v));
        return p;
    }

    /** 실제로 들리는 크기 = 구운 파형 크기 + 재생 배율. */
    private static double heardDb(String name) {
        SfxMix mix = new SfxMix();
        Map<String, float[]> all = SfxSynth.build();
        for (Map.Entry<String, float[]> e : all.entrySet()) {
            float[] s = e.getValue();
            float k = mix.bake(e.getKey(), s);
            for (int i = 0; i < s.length; i++) s[i] *= k;
        }
        SfxMix.Play p = mix.decide(name, 100);
        return SfxMix.loudestWindowDb(all.get(p.base)) + 20 * Math.log10(p.gain);
    }

    @Test
    void everySoundHasARuleAndReachesItsTarget() {
        for (String name : SfxSynth.build().keySet()) {
            assertTrue(SfxMix.CUES.containsKey(name), name + " 규칙이 없다");
            double heard = heardDb(name);
            assertEquals(SfxMix.CUES.get(name).targetDb, heard, 1.5, name + " 크기");
        }
    }

    @Test
    void swingIsNoLongerBuriedUnderSlam() {
        // 예전: 휘두르기 -23dB, 쿵 -7dB (16dB 차이). 이제는 4dB 차이
        assertTrue(heardDb("slam") - heardDb("swing") < 6);
        assertTrue(heardDb("step") < heardDb("swing"));
        assertTrue(heardDb("hurt") > heardDb("swing"));
    }

    @Test
    void sameSoundTooSoonIsSkipped() {
        SfxMix mix = baked();
        assertNotNull(mix.decide("hit", 1.0));
        assertNull(mix.decide("hit", 1.01), "한 번 휘둘러 셋을 맞혀도 타격음은 한 번");
        assertNotNull(mix.decide("hit", 1.06));
    }

    @Test
    void bigSoundQuietsSmallOnesAndDucksMusic() {
        SfxMix mix = baked();
        float before = mix.decide("swing", 1.0).gain;
        SfxMix.Play roar = mix.decide("roar", 2.0);
        assertTrue(roar.duck > 0.3f && roar.duckHold > 0.5f);
        float during = mix.decide("swing", 2.2).gain;
        assertEquals(before * SfxMix.UNDER_BIG_GAIN, during, 1e-4);
        float hurt = mix.decide("hurt", 2.3).gain;
        assertEquals(baked().decide("hurt", 0).gain, hurt, 1e-4, "맞는 소리는 줄이지 않는다");
    }

    @Test
    void bossHitIsLowerAndLouder() {
        SfxMix mix = baked();
        SfxMix.Play hit = mix.decide("hit", 1.0);
        SfxMix.Play boss = mix.decide("hitBoss", 3.0);
        assertEquals("hit", boss.base);
        assertTrue(boss.pitch < hit.pitch * 0.9f);
        assertTrue(boss.gain > hit.gain);
    }

    @Test
    void comboRaisesHitPitchStepByStep() {
        SfxMix mix = baked();
        mix.decide("hit", 0.0);
        assertEquals(0, mix.combo());
        mix.decide("hit", 0.3);
        mix.decide("hit", 0.6);
        assertEquals(2, mix.combo());
        mix.decide("hit", 2.0);
        assertEquals(0, mix.combo(), "잠깐 쉬면 처음 높이로");
    }

    @Test
    void crowdedMomentsThinOutMinorSounds() {
        SfxMix mix = baked();
        float alone = mix.decide("monsterAttack", 0.0).gain;
        String[] burst = {"hit", "kill", "block", "cast", "slam", "dash"};
        for (int i = 0; i < burst.length; i++) mix.decide(burst[i], 5.0 + i * 0.01);
        float crowded = mix.decide("monsterAttack", 5.1).gain;
        assertEquals(alone * SfxMix.CROWD_GAIN, crowded, 1e-4);
    }
}
