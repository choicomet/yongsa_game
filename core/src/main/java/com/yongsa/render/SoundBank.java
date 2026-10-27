package com.yongsa.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.ObjectMap;
import com.yongsa.Settings;

/**
 * 소리. assets/sounds/<id>.ogg|wav|mp3 가 있으면 재생하고 없으면 조용히 넘어간다.
 * 그래서 파일을 넣기만 하면 코드 수정 없이 소리가 난다 (목록은 assets/sounds/README.txt).
 */
public class SoundBank implements Disposable {

    private static final String[] EXT = {".ogg", ".wav", ".mp3"};
    private final Settings settings;
    private final ObjectMap<String, Sound> sounds = new ObjectMap<>();
    private final ObjectMap<String, Boolean> missing = new ObjectMap<>();
    private Music music;
    private String musicId = "";

    public SoundBank(Settings settings) {
        this.settings = settings;
    }

    public void play(String id) {
        Sound s = sound(id);
        if (s != null) s.play(settings.sfx());
    }

    public void playMusic(String id) {
        if (id.equals(musicId)) return;
        stopMusic();
        musicId = id;
        FileHandle f = find(id);
        if (f == null) return;
        music = Gdx.audio.newMusic(f);
        music.setLooping(true);
        music.setVolume(settings.music());
        music.play();
    }

    public void stopMusic() {
        if (music != null) {
            music.stop();
            music.dispose();
            music = null;
        }
        musicId = "";
    }

    public void applyVolume() {
        if (music != null) music.setVolume(settings.music());
    }

    private Sound sound(String id) {
        Sound s = sounds.get(id);
        if (s != null || missing.containsKey(id)) return s;
        FileHandle f = find(id);
        if (f == null) {
            missing.put(id, true);
            return null;
        }
        s = Gdx.audio.newSound(f);
        sounds.put(id, s);
        return s;
    }

    private static FileHandle find(String id) {
        for (String e : EXT) {
            FileHandle f = Gdx.files.internal("sounds/" + id + e);
            if (f.exists()) return f;
        }
        return null;
    }

    @Override
    public void dispose() {
        stopMusic();
        for (Sound s : sounds.values()) s.dispose();
        sounds.clear();
    }
}
