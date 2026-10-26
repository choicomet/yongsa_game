package com.yongsa.gfx;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.yongsa.combat.Facing;
import java.util.LinkedHashMap;
import java.util.Map;

/** Measured source rectangles and ground anchors; no source PNG needs resizing or repacking. */
public final class HeroAtlasData {
    public record Source(String path, int width, int height, String sha256) {}
    public record Frame(String source, int x, int y, int width, int height,
                        float footX, float footY, float pixelsPerWorld) {
        /** Placement inside the renderer's common 64x72 quad, in top-left coordinates. */
        public float[] placement() {
            return new float[]{.5f + (x-footX)/pixelsPerWorld/HeroMotion.WIDTH,
                    1-HeroMotion.FOOT_OFFSET/HeroMotion.HEIGHT + (y-footY)/pixelsPerWorld/HeroMotion.HEIGHT,
                    width/pixelsPerWorld/HeroMotion.WIDTH, height/pixelsPerWorld/HeroMotion.HEIGHT};
        }
    }
    public final Map<String, Source> sources = new LinkedHashMap<>();
    public final Map<String, Frame[][]> clips = new LinkedHashMap<>();

    public static int row(Facing facing) {
        if (facing.diagonal()) return facing.sy < 0 ? 3 : 4;
        return HeroMotion.row(facing);
    }

    public HeroAtlasData(String json) {
        JsonValue root = new JsonReader().parse(json);
        require(root.getInt("version") == 1, "Unsupported hero atlas version");
        for (JsonValue s : root.get("sources")) {
            Source source = new Source(s.getString("path"), s.getInt("width"), s.getInt("height"), s.getString("sha256"));
            require(source.width > 0 && source.height > 0, "Invalid source dimensions");
            sources.put(s.name, source);
        }
        for (String action : new String[]{"walk", "roll"}) {
            JsonValue rows = root.get(action);
            require(rows != null && rows.size == 5, "Five directions required for " + action);
            Frame[][] clip = new Frame[5][6];
            for (int r=0; r<5; r++) {
                require(rows.get(r).size == 6, "Six poses required for " + action + " row " + r);
                for (int c=0; c<6; c++) {
                    JsonValue f = rows.get(r).get(c);
                    int[] rect = f.get("rect").asIntArray();
                    float[] foot = f.get("foot").asFloatArray();
                    require(rect.length == 4 && foot.length == 2, "Invalid frame coordinates");
                    Frame frame = new Frame(f.getString("source"), rect[0], rect[1], rect[2], rect[3],
                            foot[0], foot[1], f.getFloat("pixelsPerWorld"));
                    validate(frame);
                    clip[r][c] = frame;
                }
            }
            clips.put(action, clip);
        }
    }

    private void validate(Frame f) {
        Source s = sources.get(f.source);
        require(s != null, "Missing source " + f.source);
        require(f.x >= 0 && f.y >= 0 && f.width > 0 && f.height > 0
                && (long)f.x+f.width <= s.width && (long)f.y+f.height <= s.height, "Frame outside " + f.source);
        require(Float.isFinite(f.pixelsPerWorld) && f.pixelsPerWorld > 0
                && Float.isFinite(f.footX) && Float.isFinite(f.footY), "Invalid ground anchor or scale");
        require(f.footX >= f.x && f.footX <= f.x+f.width && f.footY >= f.y && f.footY <= f.y+f.height,
                "Ground anchor outside frame");
        float[] p = f.placement();
        require(p[0] >= 0 && p[1] >= 0 && p[0]+p[2] <= 1 && p[1]+p[3] <= 1,
                "Pose would be clipped by the hero quad: " + f.source);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
