package com.yongsa.world;

import com.google.gson.Gson;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Cropped image-space masks compiled from the offline movement/layer review. */
public final class ReviewedOcclusion {
    public int version;
    public Map<String, Scene> scenes = new LinkedHashMap<>();

    public static final class Scene {
        public String image;
        public int[] size;
        public List<Piece> pieces;
    }

    public static final class Piece {
        public String mask, mode;
        /** Original-image pixel rectangle, top-left origin. Never scaled during import. */
        public int[] rect;
        public float depth;
        public boolean always() { return "always".equals(mode); }
        public float left(int imageWidth, boolean flip) {
            return flip ? 1f - (rect[0] + rect[2]) / (float) imageWidth : rect[0] / (float) imageWidth;
        }
    }

    public static ReviewedOcclusion parse(String json) {
        var data = new Gson().fromJson(json, ReviewedOcclusion.class);
        if (data == null || data.version != 1 || data.scenes == null)
            throw new IllegalArgumentException("Invalid reviewed occlusion manifest");
        for (var entry : data.scenes.entrySet()) {
            var s = entry.getValue();
            if (!entry.getKey().matches("(rooms|passages|joints)/[a-zA-Z0-9-]+") || s == null
                    || s.image == null || s.size == null || s.size.length != 2
                    || s.size[0] <= 0 || s.size[1] <= 0 || s.pieces == null)
                throw new IllegalArgumentException("Invalid reviewed scene: " + entry.getKey());
            for (var p : s.pieces) {
                if (p == null || p.mask == null || !p.mask.startsWith("occlusion/review/")
                        || p.mask.contains("..") || p.rect == null || p.rect.length != 4
                        || !("always".equals(p.mode) || "behind".equals(p.mode))
                        || !Float.isFinite(p.depth) || p.depth < 0 || p.depth > 1
                        || p.rect[0] < 0 || p.rect[1] < 0 || p.rect[2] <= 0 || p.rect[3] <= 0
                        || p.rect[0] + p.rect[2] > s.size[0] || p.rect[1] + p.rect[3] > s.size[1])
                    throw new IllegalArgumentException("Invalid reviewed piece: " + entry.getKey());
            }
        }
        return data;
    }
}
