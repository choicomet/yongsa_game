package com.yongsa.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;

/**
 * 배경음악 (음악 담당). assets/audio/bgm/&lt;이름&gt;.ogg (또는 .mp3, .wav) 를 반복 재생한다.
 * 파일이 없거나 소리 장치가 없으면 조용히 넘어가므로, 음악 파일이 생기기 전에도 불러도 된다.
 * 예: game.bgm.play("title"), game.bgm.play("boss_golem", 0.8f), game.bgm.fadeOut(4f), game.bgm.stop().
 * 서서히 커지기·작아지기를 쓰려면 화면이 매 프레임 update(delta) 를 불러야 한다.
 */
public class Bgm implements Disposable {
    private static final String DIR = "audio/bgm/";
    private static final String[] EXTS = {".ogg", ".mp3", ".wav"};

    private Music current;
    private String currentName;
    /** 설정 화면의 음량 (0~1). */
    private float volume = 0.7f;
    /** 페이드 정도 (0 = 안 들림, 1 = 설정 음량 그대로). */
    private float fade = 1f;
    /** 1초에 fade 가 바뀌는 양. 0 이면 그대로, 음수면 작아지는 중 (0이 되면 멈춘다). */
    private float fadeSpeed = 0f;

    /** 이름의 곡을 반복 재생한다. 이미 그 곡이면 처음부터 다시 틀지 않는다. */
    public void play(String name) {
        play(name, 0f);
    }

    /** 이름의 곡을 fadeInSeconds 동안 서서히 키우며 반복 재생한다. */
    public void play(String name, float fadeInSeconds) {
        if (name == null) {
            stop();
            return;
        }
        // 같은 곡이 작아지는 중이 아니면 그대로 둔다 (작아지는 중이면 새로 튼다)
        if (name.equals(currentName) && current != null && fadeSpeed >= 0) return;
        stop();
        FileHandle file = find(name);
        if (file == null) return;
        try {
            current = Gdx.audio.newMusic(file);
            current.setLooping(true);
            if (fadeInSeconds > 0) {
                fade = 0f;
                fadeSpeed = 1f / fadeInSeconds;
            }
            applyVolume();
            current.play();
            currentName = name;
        } catch (RuntimeException e) {
            current = null;
            currentName = null;
            fade = 1f;
            fadeSpeed = 0f;
        }
    }

    /** 지금 곡을 seconds 동안 서서히 줄이다가 멈춘다. */
    public void fadeOut(float seconds) {
        if (current == null) return;
        if (seconds <= 0) {
            stop();
            return;
        }
        fadeSpeed = -1f / seconds;
    }

    /** 매 프레임 부른다 (페이드 진행). */
    public void update(float delta) {
        if (current == null || fadeSpeed == 0) return;
        fade += fadeSpeed * delta;
        if (fade <= 0) {
            stop();
            return;
        }
        if (fade >= 1) {
            fade = 1f;
            fadeSpeed = 0f;
        }
        applyVolume();
    }

    public void stop() {
        if (current != null) {
            current.stop();
            current.dispose();
        }
        current = null;
        currentName = null;
        fade = 1f;
        fadeSpeed = 0f;
    }

    public void setVolume(float volume) {
        this.volume = volume;
        applyVolume();
    }

    /** 지금 틀고 있는 곡 이름 (없으면 null). */
    public String playing() {
        return currentName;
    }

    private void applyVolume() {
        // 귀에는 음량이 고르게 줄어드는 것처럼 들리도록 제곱으로 줄인다
        if (current != null) current.setVolume(volume * fade * fade);
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
        stop();
    }
}
