package com.yongsa.model;

/**
 * GameSession이 화면에 알리는 일 (FR-공통-06). 화면은 이 이벤트만 보고 HUD·창·소리를 바꾼다.
 */
public record GameEvent(Type type, String text, String id, int amount, Object data) {

    public enum Type {
        TOAST, SOUND, ORE_GAINED, ROOM_LOCKED, ROOM_CLEARED, BOSS_CONFIRM, BOSS_DEFEATED,
        ORB_PICKED, HERO_DIED, OPEN_FORGE, SAVE_REQUEST, AREA_CHANGED, CAMERA_SHAKE,
        CUTSCENE_STARTED, ARENA_ENTERED, FINAL_DEFEATED, GATE_SEALED, TELEPORTED,
        /** 대사. text=내용, id=말하는 사람, data=보여줄 시간(Float). */
        SPEECH,
        /** 캠프가 밝아졌다 (보스 첫 처치). amount=처음 잡은 수. */
        CAMP_BRIGHTER
    }

    public static GameEvent of(Type t) {
        return new GameEvent(t, null, null, 0, null);
    }

    public static GameEvent text(Type t, String text) {
        return new GameEvent(t, text, null, 0, null);
    }

    public static GameEvent speech(String speaker, String text, float seconds) {
        return new GameEvent(Type.SPEECH, text, speaker, 0, seconds);
    }

    public static GameEvent sound(String id) {
        return new GameEvent(Type.SOUND, null, id, 0, null);
    }
}
