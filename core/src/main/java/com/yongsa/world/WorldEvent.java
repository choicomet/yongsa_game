package com.yongsa.world;

/** 시뮬레이션이 화면에 알리는 사건. 화면이 매 프레임 꺼내 처리한다. */
public class WorldEvent {
    public enum Type {
        TOAST, SFX, DAMAGE_NUMBER, HERO_HURT, ENEMY_DIED, ROOM_CLEARED, BOSS_INTRO, BOSS_DEFEATED,
        AWAKEN_GUIDE, ENDING, RESPAWNED, DIED, MINED, SHORTCUT_UNLOCKED, TELEPORT, COMBAT_STARTED,
        /** 적이 표식에서 나왔다 (x, y). */
        ENEMY_SPAWNED,
        /** 다음 웨이브 (amount = 남은 웨이브 수). */
        WAVE_STARTED,
        /** 상태 이상 발동 (text = 상태 이름, x, y). */
        STATUS_APPLIED,
        /** 보스 체력이 0이 되어 사망 연출이 시작됐다 (x, y). */
        BOSS_DYING,
        /** 적 탄이 공격에 지워졌다 (x, y). */
        PROJECTILE_BROKEN,
        /** 회피 시작과 끝 (x, y). */
        DODGE,
        /** 방을 돌파해 상자가 나타났다 (x, y). */
        CHEST_APPEARED,
        /** 상자에서 회복약이 나왔다 (text = 회복약 id, amount = 회복량). */
        CHEST_HEAL,
        /** 상자 개봉 (x, y, amount = 골드). */
        CHEST_OPENED,
        /** 축복 획득 (text = 축복 id). */
        BLESSING
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
