package com.yongsa.map;

import com.badlogic.gdx.graphics.Pixmap;

import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/** 개발용: 그림 계산 결과가 바뀌지 않았는지 확인하려고, 올리기 직전 그림의 지문을 모은다 (-Dyongsa.bakeDigest=true). */
public final class BakeDigest {
    public static final boolean ON = Boolean.getBoolean("yongsa.bakeDigest");
    private static final Map<String, String> seen = new TreeMap<>();

    private BakeDigest() {}

    static void add(String key, Pixmap p) {
        if (!ON || p == null) return;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            var buf = p.getPixels().duplicate();
            buf.position(0);
            byte[] b = new byte[buf.remaining()];
            buf.get(b);
            md.update(b);
            StringBuilder sb = new StringBuilder(p.getWidth() + "x" + p.getHeight() + ":");
            for (byte x : md.digest()) sb.append(String.format("%02x", x));
            synchronized (seen) { seen.put(key, sb.toString()); }
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static Map<String, String> all() {
        synchronized (seen) { return new TreeMap<>(seen); }
    }
}
