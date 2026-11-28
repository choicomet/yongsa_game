package com.yongsa.audio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * 효과음 파형을 코드로 만든다 (소리 장치 없이도 돌아가서 테스트에서 크기를 잴 수 있다).
 * 순서와 씨앗(7)이 같아야 예전과 똑같은 소리가 나므로, 새 소리는 항상 맨 뒤에 붙인다.
 */
final class SfxSynth {
    static final int RATE = 22050;
    private final Random rnd = new Random(7);
    private final Map<String, float[]> out = new LinkedHashMap<>();

    static Map<String, float[]> build() {
        SfxSynth s = new SfxSynth();
        s.all();
        return s.out;
    }

    private void add(String name, float[] samples) {
        out.put(name, samples);
    }

    private void all() {
        add("swing", mix(noise(0.09f, 0.35f, 0.25f), sweep(900, 300, 0.09f, 0.12f, 1)));
        add("spin", mix(noise(0.25f, 0.35f, 0.2f), sweep(500, 1200, 0.25f, 0.12f, 1)));
        add("dash", mix(noise(0.18f, 0.4f, 0.15f), sweep(300, 900, 0.18f, 0.1f, 1)));
        add("hit", mix(sweep(260, 90, 0.08f, 0.5f, 2), noise(0.05f, 0.35f, 0.6f)));
        add("hurt", sweep(420, 120, 0.22f, 0.45f, 2));
        add("mine", mix(sweep(1800, 1500, 0.08f, 0.35f, 0), noise(0.04f, 0.3f, 0.9f)));
        add("ore", seq(new float[]{880, 1175, 1568}, 0.07f, 0.3f, 0));
        add("door", mix(sweep(110, 60, 0.25f, 0.6f, 2), noise(0.15f, 0.25f, 0.1f)));
        add("success", seq(new float[]{523, 659, 784, 1046}, 0.07f, 0.35f, 0));
        add("keep", sweep(330, 300, 0.2f, 0.3f, 2));
        add("down", seq(new float[]{523, 415, 330}, 0.09f, 0.35f, 2));
        add("destroy", mix(noise(0.6f, 0.6f, 0.4f), sweep(200, 40, 0.6f, 0.4f, 2)));
        add("awaken", mix(seq(new float[]{523, 659, 784, 1046, 1318, 1568}, 0.09f, 0.3f, 0), sweep(262, 262, 0.6f, 0.15f, 0)));
        add("block", mix(sweep(1400, 1300, 0.12f, 0.35f, 2), noise(0.05f, 0.25f, 0.8f)));
        add("slam", mix(sweep(90, 30, 0.45f, 0.7f, 0), noise(0.35f, 0.5f, 0.12f)));
        add("monsterAttack", mix(noise(0.12f, 0.25f, 0.2f), sweep(200, 120, 0.12f, 0.2f, 2)));
        add("select", sweep(660, 880, 0.05f, 0.25f, 0));
        add("potion", mix(seq(new float[]{392, 494, 587, 784}, 0.06f, 0.25f, 0), noise(0.2f, 0.08f, 0.2f)));
        add("craft", seq(new float[]{392, 523, 659}, 0.08f, 0.35f, 2));
        add("unseal", mix(seq(new float[]{262, 330, 392, 523, 659, 784, 1046}, 0.16f, 0.3f, 0), sweep(131, 131, 1.2f, 0.2f, 0)));
        add("warp", mix(sweep(200, 1600, 0.7f, 0.18f, 0), mix(sweep(300, 2400, 0.7f, 0.08f, 0), noise(0.5f, 0.12f, 0.3f))));
        add("kill", mix(sweep(520, 140, 0.16f, 0.35f, 2), noise(0.14f, 0.4f, 0.35f)));
        add("roar", mix(sweep(140, 70, 0.9f, 0.45f, 1), noise(0.8f, 0.35f, 0.08f)));
        add("cast", mix(sweep(500, 1300, 0.18f, 0.18f, 0), noise(0.12f, 0.15f, 0.5f)));
        add("step", mix(noise(0.22f, 0.16f, 0.12f), sweep(240, 150, 0.22f, 0.08f, 2)));
        // Same short duration as the gate movement; opening has a lighter rising tail.
        // Append after existing cues so their seeded noise stays unchanged.
        add("gateClose_LEFT", mix(sweep(125, 55, .28f, .38f, 2), noise(.28f, .22f, .13f)));
        add("gateOpen_LEFT", mix(sweep(70, 145, .36f, .25f, 2), noise(.27f, .15f, .10f)));
        add("gateClose_RIGHT", mix(sweep(150, 65, .28f, .32f, 2), sweep(1600, 900, .16f, .17f, 0)));
        add("gateOpen_RIGHT", mix(sweep(90, 170, .36f, .23f, 2), sweep(700, 1100, .25f, .10f, 0)));
        add("gateClose_TOP", mix(sweep(540, 190, .28f, .25f, 0), sweep(810, 285, .28f, .12f, 0)));
        add("gateOpen_TOP", mix(sweep(260, 780, .36f, .21f, 0), sweep(390, 1170, .36f, .10f, 0)));
    }

    /** wave: 0 사인, 1 톱니, 2 사각. 끝으로 갈수록 작아진다. */
    private float[] sweep(float f0, float f1, float dur, float vol, int wave) {
        int n = (int) (dur * RATE);
        float[] s = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            double f = f0 + (f1 - f0) * t;
            phase += f / RATE;
            double p = phase - Math.floor(phase);
            double v;
            if (wave == 0) v = Math.sin(p * Math.PI * 2);
            else if (wave == 1) v = p * 2 - 1;
            else v = p < 0.5 ? 0.6 : -0.6;
            float env = Math.min(1f, i / (RATE * 0.005f)) * (1 - t);
            s[i] = (float) v * vol * env;
        }
        return s;
    }

    private float[] noise(float dur, float vol, float smooth) {
        int n = (int) (dur * RATE);
        float[] s = new float[n];
        float last = 0;
        for (int i = 0; i < n; i++) {
            float t = i / (float) n;
            last = last + (rnd.nextFloat() * 2 - 1 - last) * smooth;
            s[i] = last * vol * (1 - t);
        }
        return s;
    }

    private float[] seq(float[] notes, float each, float vol, int wave) {
        float[] out = new float[0];
        for (float f : notes) {
            float[] tone = sweep(f, f, each * 1.6f, vol, wave);
            int start = out.length == 0 ? 0 : out.length - (int) (each * 0.6f * RATE);
            float[] next = new float[Math.max(out.length, start + tone.length)];
            System.arraycopy(out, 0, next, 0, out.length);
            for (int i = 0; i < tone.length; i++) next[start + i] += tone[i];
            out = next;
        }
        return out;
    }

    private float[] mix(float[] a, float[] b) {
        float[] out = new float[Math.max(a.length, b.length)];
        for (int i = 0; i < out.length; i++) {
            out[i] = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
        }
        return out;
    }

    /** 16비트 모노 WAV 파일 내용. */
    static byte[] wav(float[] samples) {
        return wav(samples, RATE);
    }

    /** 16비트 모노 WAV 파일 내용 (초당 rate 개). */
    static byte[] wav(float[] samples, int rate) {
        int dataLen = samples.length * 2;
        byte[] b = new byte[44 + dataLen];
        writeStr(b, 0, "RIFF");
        writeInt(b, 4, 36 + dataLen);
        writeStr(b, 8, "WAVE");
        writeStr(b, 12, "fmt ");
        writeInt(b, 16, 16);
        writeShort(b, 20, 1);
        writeShort(b, 22, 1);
        writeInt(b, 24, rate);
        writeInt(b, 28, rate * 2);
        writeShort(b, 32, 2);
        writeShort(b, 34, 16);
        writeStr(b, 36, "data");
        writeInt(b, 40, dataLen);
        for (int i = 0; i < samples.length; i++) {
            int v = (int) (Math.max(-1, Math.min(1, samples[i])) * 32000);
            writeShort(b, 44 + i * 2, v);
        }
        return b;
    }

    private static void writeStr(byte[] b, int off, String s) {
        for (int i = 0; i < 4; i++) b[off + i] = (byte) s.charAt(i);
    }

    private static void writeInt(byte[] b, int off, int v) {
        for (int i = 0; i < 4; i++) b[off + i] = (byte) (v >> (8 * i));
    }

    private static void writeShort(byte[] b, int off, int v) {
        b[off] = (byte) v;
        b[off + 1] = (byte) (v >> 8);
    }

}
