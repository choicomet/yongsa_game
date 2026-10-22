package com.yongsa.model;

/** 한국어 조사 고르기: 받침에 따라 "으로/로", "을/를", "이/가", "은/는", "과/와". */
public final class Josa {
    private Josa() {
    }

    private static boolean batchim(String word) {
        if (word == null || word.isEmpty()) return false;
        char c = word.charAt(word.length() - 1);
        if (c < 0xAC00 || c > 0xD7A3) return false;
        return (c - 0xAC00) % 28 != 0;
    }

    private static boolean rieul(String word) {
        char c = word.charAt(word.length() - 1);
        return c >= 0xAC00 && c <= 0xD7A3 && (c - 0xAC00) % 28 == 8;
    }

    /** "갱도로", "성채로", "탑으로" */
    public static String ro(String w) {
        return w + (batchim(w) && !rieul(w) ? "으로" : "로");
    }

    public static String eul(String w) {
        return w + (batchim(w) ? "을" : "를");
    }

    public static String i(String w) {
        return w + (batchim(w) ? "이" : "가");
    }

    /** "촌장과", "꼬마와" */
    public static String wa(String w) {
        return w + (batchim(w) ? "과" : "와");
    }

    public static String eun(String w) {
        return w + (batchim(w) ? "은" : "는");
    }
}
