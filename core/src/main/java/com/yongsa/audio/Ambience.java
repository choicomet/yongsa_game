package com.yongsa.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

import java.io.File;
import java.io.FileOutputStream;

/**
 * 배경에 깔리는 환경 소리 (지금은 캠프 모닥불). 배경음악(Bgm)과 따로 돌아서,
 * 나중에 캠프 곡이 생기면 game.bgm.play("camp") 로 곡을 틀고 장작 소리는 그 밑에 그대로 깔린다.
 * 기본 소리는 FireSynth 로 코드에서 만들고, assets/audio/ambience/campfire.ogg (또는 .wav, .mp3) 가 있으면 그 파일을 쓴다.
 * 음량은 설정의 배경음악 크기를 따른다. 화면이 매 프레임 setFire(크기) 와 update(delta) 를 부른다.
 */
public class Ambience implements Disposable {
    private static final String DIR = "audio/ambience/";
    private static final String[] EXTS = {".ogg", ".wav", ".mp3"};
    /** 효과음·곡보다 한 발 뒤에 깔리도록 낮춰 둔다 (설정 70% 에서 대략 -30 LUFS 쯤). */
    static final float MIX = 0.7f;
    /** 0 → 1 까지 커지는 데 걸리는 시간, 1 → 0 까지 작아지는 시간 (초). */
    private static final float RISE = 1.5f, FALL = 1.0f;

    private Music fire;
    private float volume = 0.7f;
    private float level = 0f, target = 0f, pan = 0f;

    public Ambience() {
        try {
            FileHandle file = find("campfire");
            if (file == null) file = synthFile();
            fire = Gdx.audio.newMusic(file);
            fire.setLooping(true);
        } catch (Exception e) {
            fire = null;
            if (Gdx.app != null) Gdx.app.log("Ambience", "모닥불 소리를 만들지 못했습니다: " + e.getMessage());
        }
    }

    /** 모닥불 소리를 얼마나 크게 들려줄지 (0 = 안 들림, 1 = 불 바로 옆). pan 은 -1 왼쪽 ~ 1 오른쪽. */
    public void setFire(float amount, float pan) {
        target = Math.max(0f, Math.min(1f, amount));
        this.pan = Math.max(-1f, Math.min(1f, pan));
    }

    /** 매 프레임 부른다. 크기를 천천히 따라가고, 다 작아지면 잠시 멈춘다. */
    public void update(float delta) {
        if (fire == null) return;
        if (level < target) level = Math.min(target, level + delta / RISE);
        else if (level > target) level = Math.max(target, level - delta / FALL);
        if (level <= 0f) {
            if (fire.isPlaying()) fire.pause();
            return;
        }
        apply();
        if (!fire.isPlaying()) fire.play();
    }

    /** 바로 끈다 (화면을 나갈 때). */
    public void stop() {
        target = level = 0f;
        if (fire != null && fire.isPlaying()) fire.pause();
    }

    public void setVolume(float volume) {
        this.volume = volume;
        if (fire != null && level > 0) apply();
    }

    private void apply() {
        // 귀에 고르게 줄어드는 것처럼 들리도록 제곱으로 줄인다 (Bgm 과 같은 방식)
        fire.setPan(pan, volume * MIX * level * level);
    }

    private static FileHandle synthFile() throws Exception {
        File f = File.createTempFile("yongsa_campfire", ".wav");
        f.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(FireSynth.wav(FireSynth.build()));
        }
        return new FileHandle(f);
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
        if (fire != null) fire.dispose();
        fire = null;
    }
}
