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
        UNSEAL_READY, ENTERED_DUNGEON,
        /** 연출용: 맞은 자리 (tone 이 HERO_HIT 면 용사가 맞음), 쓰러진 몬스터, 보스 내려찍기, 곡괭이질. */
        HIT_FX, MONSTER_DIED, SLAM_FX, MINE_FX,
        /** 보스방 문이 잠기며 보스가 나타남 (text = 보스 이름). */
        BOSS_FIGHT_START,
        /** 귀환 포탈을 사용함. 화면이 어두워진 뒤 GameSession.warpToCamp 를 부른다. */
        WARP,
        /** 보스 체력이 절반 아래로 내려가 2페이즈가 됨 (text = 보스 이름). */
        BOSS_ENRAGED,
        /** 마족병이 순간이동한 자리. */
        BLINK_FX
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
    /** 연출용 값: 방향, 크기, 그림 이름. */
    public float dirX, dirY, size;
    public String sprite;
    public boolean boss, flip;

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

    public static GameEvent fx(Type type, float x, float y) {
        GameEvent e = new GameEvent(type);
        e.x = x;
        e.y = y;
        return e;
    }

    public static GameEvent sound(String name) {
        GameEvent e = new GameEvent(Type.SOUND);
        e.text = name;
        return e;
    }
}
