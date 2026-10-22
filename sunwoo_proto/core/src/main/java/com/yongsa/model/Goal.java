package com.yongsa.model;

/**
 * "다음 할 일" 한 줄과 지도 길 안내 목적지 (FR-공통-07).
 *
 * @param target   ROOM이면 targetId가 방 id, 그 밖에는 캠프 시설·위치 종류
 */
public record Goal(String text, Target target, String targetId) {
    public enum Target { NONE, ROOM, FORGE, BAG, SIGIL, ORB, PORTAL, CAMP }
}
