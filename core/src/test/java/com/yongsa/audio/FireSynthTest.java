package com.yongsa.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FireSynthTest {
    @Test
    void loopHasExpectedLengthAndStaysInRange() {
        float[] s = FireSynth.build();
        assertEquals((int) (FireSynth.SECONDS * FireSynth.RATE), s.length);
        float max = 0;
        for (float v : s) {
            assertFalse(Float.isNaN(v));
            max = Math.max(max, Math.abs(v));
        }
        assertEquals(0.9f, max, 1e-4f);
    }

    @Test
    void sameSeedGivesSameSound() {
        assertArrayEquals(FireSynth.build(3), FireSynth.build(3));
    }

    @Test
    void loopSeamDoesNotJump() {
        float[] s = FireSynth.build();
        // 끝에서 처음으로 넘어가는 한 걸음이 보통 한 걸음보다 크게 튀지 않아야 한다
        double sum = 0;
        for (int i = 1; i < s.length; i++) sum += Math.abs(s[i] - s[i - 1]);
        double avg = sum / (s.length - 1);
        assertTrue(Math.abs(s[0] - s[s.length - 1]) < avg * 8, "이음매에서 소리가 튄다");
    }

    @Test
    void hasQuietBedAndLoudCrackles() {
        float[] s = FireSynth.build();
        double sq = 0;
        for (float v : s) sq += v * v;
        double rms = Math.sqrt(sq / s.length);
        // 타닥 소리(봉우리)가 깔린 소리(평균)보다 확실히 커야 장작 소리처럼 들린다
        assertTrue(rms > 0.02 && rms < 0.2, "rms=" + rms);
        assertTrue(0.9 / rms > 5, "crest=" + 0.9 / rms);
    }
}
