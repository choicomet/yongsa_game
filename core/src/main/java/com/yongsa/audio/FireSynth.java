package com.yongsa.audio;

import java.util.Random;

/**
 * 모닥불 장작 타는 소리를 코드로 만든다 (끊김 없이 반복되는 20초짜리 모노 파형).
 * 세 겹을 섞는다.
 *  1. 불꽃이 일렁이는 낮은 "화르르" (걸러 낸 잡음, 천천히 커졌다 작아졌다)
 *  2. 자잘하게 타닥거리는 소리 (아주 짧은 딸깍 소리, 가끔 몇 개씩 몰려서)
 *  3. 가끔 장작이 "탁" 하고 튀는 소리 (낮은 울림 + 날카로운 딱 소리)
 * 소리 장치 없이도 돌아가서 테스트에서 길이·크기·이음매를 잴 수 있다.
 */
final class FireSynth {
    static final int RATE = 44100;
    static final float SECONDS = 20f;
    /** 반복 이음매에서 앞뒤를 겹쳐 섞는 길이 (초). */
    private static final float SEAM = 0.6f;

    private final Random rnd;
    private final int n = (int) (SECONDS * RATE);
    private final float[] out = new float[n];

    private FireSynth(long seed) {
        rnd = new Random(seed);
    }

    static float[] build() {
        return build(11);
    }

    static float[] build(long seed) {
        FireSynth s = new FireSynth(seed);
        s.bed();
        s.crackles();
        s.pops();
        s.hiss();
        s.limit();
        s.normalize(0.9f);
        return s.out;
    }

    /** 불꽃 세기 (0.4~1). 반복 길이에 딱 맞는 주기의 사인을 섞어서 처음과 끝이 이어진다. */
    private float intensity(int i) {
        double t = i / (double) n * Math.PI * 2;
        double v = 0.7 + 0.14 * Math.sin(t * 3 + 0.4) + 0.1 * Math.sin(t * 7 + 2.1) + 0.06 * Math.sin(t * 13 + 4.3);
        return (float) Math.max(0.4, Math.min(1.0, v));
    }

    /** 1. 낮게 깔리는 화르르 소리. 이음매는 겹쳐 섞어서 튀지 않게 한다. */
    private void bed() {
        int seam = (int) (SEAM * RATE);
        float[] raw = new float[n + seam];
        // 두 번 낮게 거른 잡음 (약 300Hz 아래) + 조금 높은 바람 소리 (약 1.5kHz)
        double lp1 = 0, lp2 = 0, mid1 = 0, mid2 = 0;
        double a = onePole(300), b = onePole(1500), c = onePole(500);
        for (int i = 0; i < raw.length; i++) {
            double w = rnd.nextGaussian();
            lp1 += (w - lp1) * a;
            lp2 += (lp1 - lp2) * a;
            mid1 += (w - mid1) * b;
            mid2 += (mid1 - mid2) * c;
            double band = mid1 - mid2; // 500Hz~1.5kHz 쯤
            raw[i] = (float) (lp2 * 1.6 + band * 0.22);
        }
        // 뒤에 더 만든 seam 만큼을 앞부분에 겹쳐 섞는다 (같은 크기 유지되게 제곱근 곡선)
        for (int i = 0; i < seam; i++) {
            double t = i / (double) seam;
            raw[i] = (float) (raw[i] * Math.sqrt(t) + raw[n + i] * Math.sqrt(1 - t));
        }
        for (int i = 0; i < n; i++) {
            float in = intensity(i);
            out[i] += raw[i] * 0.24f * in * in;
        }
    }

    /** 2. 타닥타닥. 불이 셀수록 자주, 작은 것은 많고 큰 것은 드물다. */
    private void crackles() {
        double hp = onePole(1800);
        int i = 0;
        while (i < n) {
            float in = intensity(i);
            // 평균 1초에 약 9~22번
            double rate = 9 + 13 * in;
            i += (int) (-Math.log(1 - rnd.nextDouble()) / rate * RATE) + 1;
            if (i >= n) break;
            int burst = rnd.nextFloat() < 0.18f ? 2 + rnd.nextInt(6) : 1;
            int at = i;
            for (int k = 0; k < burst; k++) {
                // 크기는 거듭제곱 분포: 대부분 작고 가끔 크다
                float amp = (float) (0.03 + 0.32 * Math.pow(rnd.nextDouble(), 3.5)) * (0.6f + 0.4f * in);
                float dur = 0.0006f + rnd.nextFloat() * 0.0035f;
                click(at, amp, dur, hp);
                at += (int) ((0.004 + rnd.nextDouble() * 0.03) * RATE);
            }
        }
    }

    /** 아주 짧게 터지고 빨리 사라지는 딸깍 소리 (잡음에서 1.5~6kHz 쯤만 남긴다. 너무 높으면 지글거린다). */
    private void click(int at, float amp, float dur, double hp) {
        int len = (int) (dur * RATE * 5);
        double low = 0, top = 0;
        double lp = onePole(6000);
        double decay = Math.exp(-1.0 / (dur * RATE));
        double env = 1;
        for (int j = 0; j < len; j++) {
            double w = rnd.nextGaussian();
            top += (w - top) * lp;
            low += (top - low) * hp;
            out[(at + j) % n] += (float) ((top - low) * amp * env * 1.6);
            env *= decay;
        }
    }

    /** 3. 장작이 "탁" 튀는 소리. 몇 초에 한 번. */
    private void pops() {
        double hp = onePole(1200);
        int i = (int) (rnd.nextDouble() * 1.5 * RATE);
        while (i < n) {
            float amp = 0.35f + rnd.nextFloat() * 0.45f;
            double freq = 140 + rnd.nextDouble() * 260;
            double decay = Math.exp(-1.0 / (0.012 * RATE + rnd.nextDouble() * 0.01 * RATE));
            int len = (int) (0.09 * RATE);
            double env = 1, phase = rnd.nextDouble();
            for (int j = 0; j < len; j++) {
                // 나무가 울리는 낮은 소리 (음이 살짝 내려간다)
                phase += freq * (1 - 0.25 * j / (double) len) / RATE;
                out[(i + j) % n] += (float) (Math.sin(phase * Math.PI * 2) * amp * 0.55 * env);
                env *= decay;
            }
            // 앞머리의 날카로운 딱 소리 + 따라오는 잔 불똥
            click(i, amp, 0.0025f, hp);
            int sparks = 2 + rnd.nextInt(5);
            for (int k = 0; k < sparks; k++) {
                int at = i + (int) ((0.015 + rnd.nextDouble() * 0.18) * RATE);
                click(at, amp * (0.15f + rnd.nextFloat() * 0.3f), 0.0008f + rnd.nextFloat() * 0.0015f, hp);
            }
            i += (int) ((1.6 + rnd.nextDouble() * 3.2) * RATE);
        }
    }

    /** 가끔 젖은 나무에서 "치이~" 하고 김 빠지는 소리 (아주 작게). */
    private void hiss() {
        double a = onePole(3500), b = onePole(7000);
        int count = 2;
        for (int k = 0; k < count; k++) {
            int at = (int) ((k + 0.2 + rnd.nextDouble() * 0.6) * n / count);
            int len = (int) ((0.5 + rnd.nextDouble() * 0.7) * RATE);
            double l1 = 0, l2 = 0;
            for (int j = 0; j < len; j++) {
                double w = rnd.nextGaussian();
                l1 += (w - l1) * a;
                l2 += (w - l2) * b;
                double t = j / (double) len;
                double env = Math.sin(Math.PI * t) * (0.7 + 0.3 * Math.sin(t * 40));
                out[(at + j) % n] += (float) ((l2 - l1) * 0.03 * env);
            }
        }
    }

    /**
     * 가장 센 "탁" 몇 개만 부드럽게 눌러서 전체를 키울 여유를 만든다.
     * 위에서 0.05% 쯤 되는 크기를 기준으로, 그보다 큰 소리는 tanh 곡선으로 둥글게 깎는다.
     */
    private void limit() {
        float[] abs = new float[n / 16];
        for (int i = 0; i < abs.length; i++) abs[i] = Math.abs(out[i * 16]);
        java.util.Arrays.sort(abs);
        float knee = abs[(int) (abs.length * 0.9995)];
        if (knee <= 0) return;
        for (int i = 0; i < n; i++) out[i] = (float) (Math.tanh(out[i] / knee) * knee);
    }

    private void normalize(float peak) {
        float max = 0;
        for (float v : out) max = Math.max(max, Math.abs(v));
        if (max <= 0) return;
        float g = peak / max;
        for (int i = 0; i < n; i++) out[i] *= g;
    }

    /** 한 번 거르기(one-pole) 계수. 이 주파수보다 높은 소리를 줄인다. */
    private static double onePole(double hz) {
        return 1 - Math.exp(-2 * Math.PI * hz / RATE);
    }

    /** 16비트 모노 WAV 파일 내용. */
    static byte[] wav(float[] samples) {
        return SfxSynth.wav(samples, RATE);
    }
}
