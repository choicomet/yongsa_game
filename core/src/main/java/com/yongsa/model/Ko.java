package com.yongsa.model;

/** 한국어 조사 처리. */
public final class Ko {
    private Ko() {}

    private static int jong(String word) {
        if (word == null || word.isEmpty()) return 0;
        char c = word.charAt(word.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return 0;
        return (c - 0xAC00) % 28;
    }

    /** "으로" / "로" (받침이 없거나 ㄹ 받침이면 "로"). */
    public static String ro(String word) {
        int j = jong(word);
        return word + (j == 0 || j == 8 ? "로" : "으로");
    }

    /** "을" / "를". */
    public static String eul(String word) {
        return word + (jong(word) == 0 ? "를" : "을");
    }

    /** "이" / "가". */
    public static String i(String word) {
        return word + (jong(word) == 0 ? "가" : "이");
    }
}
