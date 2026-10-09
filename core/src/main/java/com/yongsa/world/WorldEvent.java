package com.yongsa.world;

/** 시뮬레이션이 화면에 알리는 사건. 화면이 매 프레임 꺼내 처리한다. */
public class WorldEvent {
    public enum Type {
        TOAST, SFX, DAMAGE_NUMBER, HERO_HURT, ENEMY_DIED, ROOM_CLEARED, BOSS_INTRO, BOSS_DEFEATED,
        AWAKEN_GUIDE, ENDING, OPEN_FORGE, RESPAWNED, DIED, MINED, SHORTCUT_UNLOCKED, TELEPORT, COMBAT_STARTED
    }

    public final Type type;
    public final String text;
    public final float x, y;
    public final int amount;

    public WorldEvent(Type type, String text, float x, float y, int amount) {
        this.type = type;
        this.text = text;
        this.x = x;
        this.y = y;
        this.amount = amount;
    }

    public static WorldEvent of(Type type) {
        return new WorldEvent(type, null, 0, 0, 0);
    }

    public static WorldEvent text(Type type, String text) {
        return new WorldEvent(type, text, 0, 0, 0);
    }

    @Override
    public String toString() {
        return type + (text != null ? "(" + text + ")" : "") + (amount != 0 ? "[" + amount + "]" : "");
    }
}
