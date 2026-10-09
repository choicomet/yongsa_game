package com.yongsa.model;

/**
 * GameSession 이 화면에 알리는 일 (FR-공통-06). 화면은 이벤트를 받아 창을 열거나 글자를 띄운다.
 */
public class GameEvent {
    public enum Type {
        TOAST, FLOAT_TEXT, ORE_GAINED, SOUND,
        ROOM_LOCKED, ROOM_CLEARED,
        OPEN_FORGE, BED_USED, AUTOSAVE,
        BOSS_DEFEATED, FINAL_DEFEATED, HERO_DIED,
        UNSEAL_READY, ENTERED_DUNGEON
    }

    /** 떠오르는 글자 색 구분. */
    public enum Tone { HERO_HIT, MONSTER_HIT, INFO, ORE, HEAL, BLOCKED }

    public final Type type;
    public String text;
    public float x, y;
    public Tone tone;
    public OreType ore;
    public int amount;
    public String dungeonId;
    public int hpGain;
    public String opened;

    public GameEvent(Type type) {
        this.type = type;
    }

    public static GameEvent toast(String text) {
        GameEvent e = new GameEvent(Type.TOAST);
        e.text = text;
        return e;
    }

    public static GameEvent floatText(float x, float y, String text, Tone tone) {
        GameEvent e = new GameEvent(Type.FLOAT_TEXT);
        e.x = x;
        e.y = y;
        e.text = text;
        e.tone = tone;
        return e;
    }

    public static GameEvent sound(String name) {
        GameEvent e = new GameEvent(Type.SOUND);
        e.text = name;
        return e;
    }
}
