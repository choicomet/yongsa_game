package com.yongsa.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * 효과음. 기본은 짧은 파형을 코드로 만들어(SfxSynth) WAV 로 쓴 뒤 불러온다.
 * assets/audio/sfx/&lt;이름&gt;.ogg (또는 .wav, .mp3) 가 있으면 그 파일을 대신 쓴다.
 * 크기·간격·덕킹 같은 믹스 규칙은 SfxMix 에 있다. 소리 장치가 없으면 조용히 넘어간다.
 */
public class Sfx implements Disposable {
    private static final String DIR = "audio/sfx/";
    private static final String[] EXTS = {".ogg", ".wav", ".mp3"};

    private final Map<String, Sound> sounds = new HashMap<>();
    private final SfxMix mix = new SfxMix();
    private final long startNanos = System.nanoTime();
    /** 설정 화면의 효과음 크기 (0~1). */
    public float volume = 0.7f;
    /** 큰 소리가 날 때 잠깐 줄일 배경음악 (없어도 된다). */
    private Bgm bgm;

    public Sfx() {
        try {
            Map<String, float[]> synth = SfxSynth.build();
            // 파일로만 있는 소리 (스킬 소리 등)
            for (String name : SfxMix.fileOnlyBases(synth.keySet())) {
                FileHandle file = find(name);
                if (file == null) continue;
                sounds.put(name, Gdx.audio.newSound(file));
                mix.measureFile(name);
            }
            for (Map.Entry<String, float[]> e : synth.entrySet()) {
                String name = e.getKey();
                FileHandle file = find(name);
                if (file != null) {
                    sounds.put(name, Gdx.audio.newSound(file));
                    mix.measureFile(name);
                    continue;
                }
                float[] s = e.getValue();
                float scale = mix.bake(name, s);
                for (int i = 0; i < s.length; i++) s[i] *= scale;
                add(name, s);
            }
        } catch (Exception e) {
            Gdx.app.log("Sfx", "효과음을 만들지 못했습니다: " + e.getMessage());
        }
    }

    public void setBgm(Bgm bgm) {
        this.bgm = bgm;
    }

    public void play(String name) {
        if (volume <= 0) return;
        SfxMix.Play p = mix.decide(name, (System.nanoTime() - startNanos) / 1e9);
        if (p == null) return;
        Sound s = sounds.get(p.base);
        if (s == null) return;
        s.play(volume * p.gain, p.pitch, 0f);
        if (bgm != null && p.duck > 0) bgm.duck(p.duck, p.duckHold);
    }

    private void add(String name, float[] samples) throws IOException {
        File f = File.createTempFile("yongsa_" + name, ".wav");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(SfxSynth.wav(samples));
        }
        sounds.put(name, Gdx.audio.newSound(new FileHandle(f)));
    }

    private static FileHandle find(String name) {
        if (Gdx.files == null) return null;
        for (String ext : EXTS) {
            FileHandle f = Gdx.files.internal(DIR + name + ext);
            if (f.exists()) return f;
        }
        return null;
    }

    @Override
    public void dispose() {
        for (Sound s : sounds.values()) s.dispose();
    }
}
