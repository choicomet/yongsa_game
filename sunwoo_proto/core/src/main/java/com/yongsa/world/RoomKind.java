package com.yongsa.world;

public enum RoomKind {
    MOB, ORE, BOSS;

    public static RoomKind parse(String s) {
        return switch (s) {
            case "mob" -> MOB;
            case "ore" -> ORE;
            case "boss" -> BOSS;
            default -> throw new IllegalArgumentException("방 종류: " + s);
        };
    }
}
