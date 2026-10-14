package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

/** 던전의 방 하나. 좌표는 y가 위로 증가하는 월드 픽셀. */
public class RoomDef {
    /** CAMP 중앙 캠프, ROOM 일반 방(몬스터·광맥 조합), ANTE 보스 앞 공간, BOSS 보스방. */
    public enum Kind { CAMP, ROOM, ANTE, BOSS }

    public final String id;
    public final String zone;
    public final Kind kind;
    public final Rectangle bounds;
    /** 들어가면 전투가 시작되는 방인지 (일반 방 중 적이 있는 방). */
    public boolean enemies;
    /** 광맥이 생성되는 방인지와 한 회차의 광맥 수. */
    public boolean veins;
    public int veinCount;
    /** 구역 입구에서부터의 거리(방 수). */
    public int depth;
    /** 보스까지 가는 본 경로에 있는지 (아니면 곁가지). */
    public boolean mainPath;
    /** 방에 놓인 것: "shrine"(제단) | "" (없음). */
    public String feature = "";
    /** 제단을 지키는 방: 적이 더 많이, 더 여러 번, 강화 개체와 함께 나온다. */
    public boolean guard;

    public RoomDef(String id, String zone, Kind kind, Rectangle bounds) {
        this.id = id;
        this.zone = zone;
        this.kind = kind;
        this.bounds = bounds;
    }

    public boolean isCombat() {
        return kind == Kind.BOSS || (kind == Kind.ROOM && enemies);
    }

    /** 화면 표시용 방 성격. */
    public String label() {
        switch (kind) {
            case CAMP: return "캠프";
            case ANTE: return "보스 앞";
            case BOSS: return "보스";
            default: return enemies ? (veins ? "전투·광맥" : "전투") : "광맥";
        }
    }

    public float centerX() {
        return bounds.x + bounds.width / 2f;
    }

    public float centerY() {
        return bounds.y + bounds.height / 2f;
    }
}
