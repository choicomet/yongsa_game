package com.yongsa.gfx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** placeholder_art.txt 의 문자 그림을 Pixmap 으로 만든다. scale 배로 확대한다. */
public final class PixelArt {
    private PixelArt() {}

    public static Map<String, Pixmap> load(FileHandle file, int scale) {
        Map<Character, Integer> palette = new HashMap<>();
        Map<String, java.util.List<String>> sprites = new LinkedHashMap<>();
        String mode = null;
        String current = null;
        for (String raw : file.readString("UTF-8").split("\\r?\\n")) {
            String line = raw.replaceAll("\\s+$", "");
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("@palette")) {
                mode = "p";
                continue;
            }
            if (line.startsWith("@sprite")) {
                mode = "s";
                current = line.split("\\s+")[1];
                sprites.put(current, new java.util.ArrayList<>());
                continue;
            }
            if (line.startsWith("@end")) {
                mode = null;
                continue;
            }
            if ("p".equals(mode)) {
                String[] parts = line.trim().split("\\s+");
                int rgb = Integer.parseInt(parts[1], 16);
                palette.put(parts[0].charAt(0), (rgb << 8) | 0xFF);
            } else if ("s".equals(mode)) {
                sprites.get(current).add(line);
            }
        }
        Map<String, Pixmap> out = new LinkedHashMap<>();
        for (Map.Entry<String, java.util.List<String>> e : sprites.entrySet()) {
            java.util.List<String> rows = e.getValue();
            int w = 0;
            for (String r : rows) w = Math.max(w, r.length());
            int h = rows.size();
            Pixmap p = new Pixmap(w * scale, h * scale, Pixmap.Format.RGBA8888);
            p.setBlending(Pixmap.Blending.None);
            p.setColor(0, 0, 0, 0);
            p.fill();
            for (int y = 0; y < h; y++) {
                String r = rows.get(y);
                for (int x = 0; x < r.length(); x++) {
                    char c = r.charAt(x);
                    if (c == '.') continue;
                    Integer col = palette.get(c);
                    if (col == null) continue;
                    p.setColor(col);
                    p.fillRectangle(x * scale, y * scale, scale, scale);
                }
            }
            out.put(e.getKey(), p);
        }
        return out;
    }
}
