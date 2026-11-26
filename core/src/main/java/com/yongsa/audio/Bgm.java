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
    /**
     * 곡 자체가 크게 마스터링돼 있어서(약 -12 LUFS) 효과음이 묻히지 않게 미리 낮춰 둔다.
     * 설정 70% 에서 대략 -19 LUFS: 휘두르기·타격음이 음악 위로 4~6dB 정도 올라온다.
     */
    static final float MIX = 0.6f;
    /** 덕킹: 큰 효과음이 날 때 잠깐 줄이는 배율 (1 = 그대로). */
    private float duck = 1f;
    private float duckTarget = 1f;
    private float duckHold = 0f;
    /** 줄일 때는 빨리(0.06초), 돌아올 때는 천천히(0.6초). */
    private static final float DUCK_ATTACK = 0.06f, DUCK_RELEASE = 0.6f;
    /** playAfter 로 예약한 다음 곡. */
    private String pendingName;
    private float pendingDelay, pendingFadeIn, pendingStart;

    /** 이름의 곡을 반복 재생한다. 이미 그 곡이면 처음부터 다시 틀지 않는다. */
    public void play(String name) {
        play(name, 0f);
    }

    /** 이름의 곡을 fadeInSeconds 동안 서서히 키우며 반복 재생한다. */
    public void play(String name, float fadeInSeconds) {
        play(name, fadeInSeconds, 0f);
    }

    /**
     * 이름의 곡을 startSeconds 지점부터 fadeInSeconds 동안 키우며 튼다.
     * 시작 지점이 있으면 곡이 끝났을 때도 처음이 아니라 그 지점으로 돌아가 반복한다.
     */
    public void play(String name, float fadeInSeconds, float startSeconds) {
        pendingName = null;
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
            final Music m = current;
            if (startSeconds > 0) {
                m.setLooping(false);
                m.setOnCompletionListener(done -> {
                    if (current != m) return;
                    m.play();
                    m.setPosition(startSeconds);
                });
            } else {
                m.setLooping(true);
            }
            if (fadeInSeconds > 0) {
                fade = 0f;
                fadeSpeed = 1f / fadeInSeconds;
            }
            applyVolume();
            current.play();
            if (startSeconds > 0) current.setPosition(startSeconds);
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
        pendingName = null;
        if (current == null) return;
        if (seconds <= 0) {
            stop();
            return;
        }
        fadeSpeed = -1f / seconds;
    }

    /**
     * 지금 곡을 fadeOutSeconds 동안 줄이고, delaySeconds 뒤에 name 을 fadeInSeconds 동안 키우며 튼다.
     * 예: 마왕 변신 중에는 1페이즈 곡을 거두고, 변신이 끝날 때 2페이즈 곡을 올린다.
     * 그 사이 fadeOut·play·stop 을 부르면 예약은 취소된다.
     */
    public void playAfter(String name, float fadeOutSeconds, float delaySeconds, float fadeInSeconds, float startSeconds) {
        fadeOut(fadeOutSeconds);
        pendingName = name;
        pendingDelay = delaySeconds;
        pendingFadeIn = fadeInSeconds;
        pendingStart = startSeconds;
    }

    /**
     * 큰 효과음이 나는 동안 음악을 depth 만큼(0.4 면 40%) hold 초 줄였다가 천천히 되돌린다.
     * 겹치면 더 깊고 긴 쪽을 따른다.
     */
    public void duck(float depth, float hold) {
        if (current == null || depth <= 0) return;
        duckTarget = Math.min(duckTarget, 1f - Math.min(0.9f, depth));
        duckHold = Math.max(duckHold, hold);
    }

    /** 매 프레임 부른다 (페이드·덕킹 진행). */
    public void update(float delta) {
        if (pendingName != null) {
            pendingDelay -= delta;
            if (pendingDelay <= 0) {
                String n = pendingName;
                play(n, pendingFadeIn, pendingStart);
                return;
            }
        }
        if (current == null) return;
        boolean changed = false;
        if (fadeSpeed != 0) {
            fade += fadeSpeed * delta;
            if (fade <= 0) {
                stopTrack();
                return;
            }
            if (fade >= 1) {
                fade = 1f;
                fadeSpeed = 0f;
            }
            changed = true;
        }
        if (duckHold > 0) {
            duckHold -= delta;
            if (duck > duckTarget) duck = Math.max(duckTarget, duck - delta / DUCK_ATTACK);
            changed = true;
        } else if (duck < 1f || duckTarget < 1f) {
            duckTarget = 1f;
            duck = Math.min(1f, duck + delta / DUCK_RELEASE);
            changed = true;
        }
        if (changed) applyVolume();
    }

    public void stop() {
        pendingName = null;
        stopTrack();
    }

    private void stopTrack() {
        if (current != null) {
            current.stop();
            current.dispose();
        }
        current = null;
        currentName = null;
        fade = 1f;
        fadeSpeed = 0f;
        duck = duckTarget = 1f;
        duckHold = 0f;
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
        if (current != null) current.setVolume(volume * MIX * fade * fade * duck);
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
