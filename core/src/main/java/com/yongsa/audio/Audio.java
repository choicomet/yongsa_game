package com.yongsa.audio;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.model.Settings;

import java.util.HashMap;
import java.util.Map;

/**
 * 효과음과 배경음. assets/sfx/&lt;id&gt;.wav (또는 .ogg/.mp3)를 찾아 쓰고, 없으면 조용히 넘어간다.
 * 음량은 전체 × 종류별 음량으로 적용한다.
 */
public class Audio implements Disposable {
    private final Map<String, Sound> sounds = new HashMap<>();
    private final Map<String, Long> lastPlayed = new HashMap<>();
    private final Map<String, Music> musics = new HashMap<>();
    private Music current;
    private String currentId;
    private Settings settings;

    public Audio(Settings settings) {
        this.settings = settings;
        FileHandle dir = Gdx.files.internal("sfx");
        String[] ids = {"warn", "enemy_attack", "boss_attack", "dash", "swing_sword", "swing_hammer", "shockwave_charge",
                "shockwave", "hit", "hit_boss", "enemy_die", "hurt", "death", "clear", "victory", "door_lock", "mine_done",
                "rest", "teleport", "ui_click", "ui_open", "enhance_success", "enhance_fail", "craft", "dismantle"};
        for (String id : ids) {
            FileHandle f = find(dir, id);
            if (f == null) continue;
            try {
                sounds.put(id, Gdx.audio.newSound(f));
            } catch (Exception e) {
                Gdx.app.error("Audio", "효과음을 읽지 못했습니다: " + f.path());
            }
        }
    }

    private static FileHandle find(FileHandle dir, String id) {
        for (String ext : new String[]{".ogg", ".wav", ".mp3"}) {
            FileHandle f = dir.child(id + ext);
            if (f.exists()) return f;
        }
        return null;
    }

    public void setSettings(Settings s) {
        this.settings = s;
        if (current != null) current.setVolume(s.musicGain());
    }

    public void play(String id) {
        Sound s = sounds.get(id);
        if (s == null || settings == null) return;
        long now = System.currentTimeMillis();
        Long last = lastPlayed.get(id);
        if (last != null && now - last < 45) return; // 같은 소리가 겹쳐 터지지 않게
        lastPlayed.put(id, now);
        float v = settings.sfxGain();
        if (v > 0) s.play(v);
    }

    public void music(String id) {
        if (id == null) {
            stopMusic();
            return;
        }
        if (id.equals(currentId)) return;
        stopMusic();
        Music m = musics.get(id);
        if (m == null) {
            FileHandle f = find(Gdx.files.internal("music"), id);
            if (f == null) return;
            try {
                m = Gdx.audio.newMusic(f);
            } catch (Exception e) {
                return;
            }
            musics.put(id, m);
        }
        m.setLooping(true);
        m.setVolume(settings.musicGain());
        m.play();
        current = m;
        currentId = id;
    }

    public void stopMusic() {
        if (current != null) current.stop();
        current = null;
        currentId = null;
    }

    public void pauseMusic(boolean paused) {
        if (current == null) return;
        if (paused) current.pause();
        else current.play();
    }

    @Override
    public void dispose() {
        for (Sound s : sounds.values()) s.dispose();
        for (Music m : musics.values()) m.dispose();
    }
}
