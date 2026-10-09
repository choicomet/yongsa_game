package com.yongsa.model;

import com.yongsa.config.Balance;

/** 사용자 설정. 음량은 0~100. */
public class Settings {
    public int masterVolume = 80;
    public int musicVolume = 60;
    public int sfxVolume = 80;
    public boolean fullscreen = false;

    public static Settings defaults(Balance b) {
        Settings s = new Settings();
        if (b != null && b.settingsDefault != null) {
            s.masterVolume = b.settingsDefault.masterVolume;
            s.musicVolume = b.settingsDefault.musicVolume;
            s.sfxVolume = b.settingsDefault.sfxVolume;
            s.fullscreen = b.settingsDefault.fullscreen;
        }
        return s;
    }

    public Settings copy() {
        Settings s = new Settings();
        s.masterVolume = masterVolume;
        s.musicVolume = musicVolume;
        s.sfxVolume = sfxVolume;
        s.fullscreen = fullscreen;
        return s;
    }

    public void clamp() {
        masterVolume = Math.max(0, Math.min(100, masterVolume));
        musicVolume = Math.max(0, Math.min(100, musicVolume));
        sfxVolume = Math.max(0, Math.min(100, sfxVolume));
    }

    public float musicGain() {
        return masterVolume / 100f * musicVolume / 100f;
    }

    public float sfxGain() {
        return masterVolume / 100f * sfxVolume / 100f;
    }
}
