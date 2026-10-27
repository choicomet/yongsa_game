package com.yongsa;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * 설정 (FR-공통-05): 배경음·효과음 크기, 창 모드/전체 화면, 연출 속도.
 * 세이브와 따로 Preferences에 저장해서 "새 게임"을 해도 남는다.
 */
public class Settings {

    public static final float[] EFFECT_SPEEDS = {1f, 1.5f, 2f};
    public static final String[] EFFECT_SPEED_NAMES = {"보통", "빠름", "아주 빠름"};

    private final Preferences prefs = Gdx.app.getPreferences("yongsa-settings");

    public float sfx() { return prefs.getFloat("sfx", 0.8f); }
    public float music() { return prefs.getFloat("music", 0.6f); }
    public boolean fullscreen() { return prefs.getBoolean("fullscreen", false); }
    public int effectSpeedIndex() { return Math.max(0, Math.min(2, prefs.getInteger("effectSpeed", 0))); }
    public float effectSpeed() { return EFFECT_SPEEDS[effectSpeedIndex()]; }

    public void setSfx(float v) { prefs.putFloat("sfx", clamp(v)).flush(); }
    public void setMusic(float v) { prefs.putFloat("music", clamp(v)).flush(); }
    public void setFullscreen(boolean f) { prefs.putBoolean("fullscreen", f).flush(); applyDisplay(); }
    public void setEffectSpeedIndex(int i) { prefs.putInteger("effectSpeed", (i + 3) % 3).flush(); }

    /** 창 모드/전체 화면을 바로 적용. */
    public void applyDisplay() {
        if (fullscreen()) Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        else if (Gdx.graphics.isFullscreen()) Gdx.graphics.setWindowedMode(1280, 720);
    }

    // ---- 조작 키 (2026-10-27): 사용자가 바꾼 키. 없으면 기본 키

    private KeyBindings keys;

    public KeyBindings keys() {
        if (keys == null) {
            java.util.Map<String, int[]> saved = null;
            try {
                String json = prefs.getString("keyBindings", "");
                if (!json.isEmpty()) saved = new com.google.gson.Gson().fromJson(json,
                        new com.google.gson.reflect.TypeToken<java.util.Map<String, int[]>>() { }.getType());
            } catch (RuntimeException e) {
                saved = null; // 깨진 값이면 기본 키
            }
            keys = KeyBindings.fromSaved(saved);
        }
        return keys;
    }

    /** 바꾼 조작 키를 저장한다. */
    public void saveKeys() {
        prefs.putString("keyBindings", new com.google.gson.Gson().toJson(keys().toSaved())).flush();
    }

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, Math.round(v * 10f) / 10f));
    }
}
