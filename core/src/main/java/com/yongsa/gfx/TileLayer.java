package com.yongsa.gfx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.yongsa.world.DungeonMap;

/**
 * 던전 바닥과 벽을 그린다 (비스듬히 내려다보는 시점).
 * 칸마다 역할을 정해 두고(바닥 / 벽 정면 위·아래 / 벽 윗면 16종), data/tileset.json 의 타일을 고른다.
 * CraftPix 타일셋으로 바꿀 때는 tileset.json 의 이미지와 좌표만 바꾸면 된다.
 */
public class TileLayer implements Disposable {
    private final DungeonMap map;
    private final Texture texture;
    private final int src;
    private final float scale;
    /** 칸마다 그릴 바탕 타일 (null 이면 빈 공간). */
    private final TextureRegion[] base;
    /** 칸마다 겹칠 그림자 비트: 1 북, 2 서, 4 동, 8 남. */
    private final byte[] shadow;
    private final TextureRegion[] shadowTiles = new TextureRegion[4];

    public TileLayer(DungeonMap map, FileHandle tilesetJson) {
        this.map = map;
        JsonValue root = new JsonReader().parse(tilesetJson);
        FileHandle img = com.badlogic.gdx.Gdx.files.internal(root.getString("image"));
        texture = new Texture(img);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        src = root.getInt("tileSize");
        scale = root.getFloat("scale", map.tileSize / (float) src);

        JsonValue themes = root.get("themes");
        int themeCount = themes.size;
        TextureRegion[][] floors = new TextureRegion[themeCount][];
        int[][] floorWeights = new int[themeCount][];
        TextureRegion[][] faceU = new TextureRegion[themeCount][];
        TextureRegion[][] faceL = new TextureRegion[themeCount][];
        TextureRegion[][] tops = new TextureRegion[themeCount][];
        TextureRegion[][] faceEnds = new TextureRegion[themeCount][4]; // 위L, 위R, 아래L, 아래R
        boolean[] checker = new boolean[themeCount];
        for (int t = 0; t < themeCount; t++) {
            JsonValue th = themes.get(t);
            floors[t] = regions(th.get("floor"));
            faceU[t] = regions(th.get("faceUpper"));
            faceL[t] = regions(th.get("faceLower"));
            tops[t] = regions(th.get("top"));
            String[] ends = {"faceUpperL", "faceUpperR", "faceLowerL", "faceLowerR"};
            for (int k = 0; k < 4; k++) faceEnds[t][k] = th.has(ends[k]) ? region(th.get(ends[k])) : null;
            checker[t] = "checker".equals(th.getString("pattern", ""));
            JsonValue w = th.get("floorWeights");
            floorWeights[t] = w != null ? w.asIntArray() : null;
        }
        JsonValue sh = root.get("shadow");
        String[] keys = {"n", "w", "e", "s"};
        for (int i = 0; i < 4; i++) shadowTiles[i] = sh != null && sh.has(keys[i]) ? region(sh.get(keys[i])) : null;

        int w = map.widthTiles, h = map.heightTiles;
        base = new TextureRegion[w * h];
        shadow = new byte[w * h];
        for (int ty = 0; ty < h; ty++) {
            for (int tx = 0; tx < w; tx++) {
                int i = ty * w + tx;
                byte c = map.cell(tx, ty);
                int t = Math.min(themeCount - 1, map.theme(tx, ty));
                int hash = hash(tx, ty);
                if (c == DungeonMap.FLOOR) {
                    base[i] = checker[t] ? floors[t][(tx + ty) % floors[t].length] : pick(floors[t], floorWeights[t], hash);
                    byte s = 0;
                    if (!map.isFloor(tx, ty + 1)) s |= 1;
                    if (!map.isFloor(tx - 1, ty)) s |= 2;
                    if (!map.isFloor(tx + 1, ty)) s |= 4;
                    if (!map.isFloor(tx, ty - 1)) s |= 8;
                    shadow[i] = s;
                } else if (c == DungeonMap.WALL) {
                    if (map.isFloor(tx, ty - 1)) {
                        base[i] = faceL[t][hash % faceL[t].length];
                        // 벽 정면이 끝나는 곳에는 마감 타일
                        if (!isFace(tx - 1, ty) && faceEnds[t][2] != null) base[i] = faceEnds[t][2];
                        else if (!isFace(tx + 1, ty) && faceEnds[t][3] != null) base[i] = faceEnds[t][3];
                    } else if (isFace(tx, ty - 1)) {
                        base[i] = faceU[t][(hash >> 3) % faceU[t].length];
                        if (!isFace(tx - 1, ty - 1) && faceEnds[t][0] != null) base[i] = faceEnds[t][0];
                        else if (!isFace(tx + 1, ty - 1) && faceEnds[t][1] != null) base[i] = faceEnds[t][1];
                    } else {
                        int mask = (open(tx, ty + 1) ? 1 : 0) | (open(tx + 1, ty) ? 2 : 0)
                                | (open(tx, ty - 1) ? 4 : 0) | (open(tx - 1, ty) ? 8 : 0);
                        base[i] = tops[t][mask];
                    }
                }
            }
        }
    }

    /** 벽 정면 아래 칸 (바로 아래가 바닥인 벽). */
    private boolean isFace(int tx, int ty) {
        return map.cell(tx, ty) == DungeonMap.WALL && map.isFloor(tx, ty - 1);
    }

    /** 벽 윗면 기준으로 '열린' 칸: 바닥이거나 벽 정면. */
    private boolean open(int tx, int ty) {
        if (map.isFloor(tx, ty)) return true;
        if (map.cell(tx, ty) != DungeonMap.WALL) return false;
        return map.isFloor(tx, ty - 1) || isFace(tx, ty - 1);
    }

    private static int hash(int x, int y) {
        int h = x * 73856093 ^ y * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }

    private static TextureRegion pick(TextureRegion[] list, int[] weights, int hash) {
        if (weights == null || weights.length != list.length) return list[hash % list.length];
        int total = 0;
        for (int w : weights) total += w;
        int r = hash % total;
        for (int i = 0; i < list.length; i++) {
            r -= weights[i];
            if (r < 0) return list[i];
        }
        return list[0];
    }

    private TextureRegion[] regions(JsonValue arr) {
        TextureRegion[] out = new TextureRegion[arr.size];
        for (int i = 0; i < arr.size; i++) out[i] = region(arr.get(i));
        return out;
    }

    private TextureRegion region(JsonValue xy) {
        return new TextureRegion(texture, xy.getInt(0) * src, xy.getInt(1) * src, src, src);
    }

    public void render(SpriteBatch batch, Camera cam) {
        int T = map.tileSize;
        int x0 = Math.max(0, (int) ((cam.position.x - cam.viewportWidth / 2f) / T) - 1);
        int x1 = Math.min(map.widthTiles - 1, (int) ((cam.position.x + cam.viewportWidth / 2f) / T) + 1);
        int y0 = Math.max(0, (int) ((cam.position.y - cam.viewportHeight / 2f) / T) - 1);
        int y1 = Math.min(map.heightTiles - 1, (int) ((cam.position.y + cam.viewportHeight / 2f) / T) + 1);
        int w = map.widthTiles;
        for (int ty = y1; ty >= y0; ty--) {
            for (int tx = x0; tx <= x1; tx++) {
                TextureRegion r = base[ty * w + tx];
                if (r != null) batch.draw(r, tx * T, ty * T, T, T);
            }
        }
        for (int ty = y1; ty >= y0; ty--) {
            for (int tx = x0; tx <= x1; tx++) {
                byte s = shadow[ty * w + tx];
                if (s == 0) continue;
                for (int k = 0; k < 4; k++) {
                    if ((s & (1 << k)) != 0 && shadowTiles[k] != null) batch.draw(shadowTiles[k], tx * T, ty * T, T, T);
                }
            }
        }
    }

    @Override
    public void dispose() {
        texture.dispose();
    }
}
