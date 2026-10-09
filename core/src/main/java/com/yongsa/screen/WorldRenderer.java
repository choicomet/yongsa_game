package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.yongsa.combat.Facing;
import com.yongsa.combat.HeroActor;
import com.yongsa.combat.Monster;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Fonts;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.Vein;
import com.yongsa.model.Weapon;
import com.yongsa.ui.Ui;
import com.yongsa.world.Door;
import com.yongsa.world.Link;
import com.yongsa.world.Prop;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;
import com.yongsa.world.WorldMap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 맵과 그 위의 것들을 그린다. 2.5D 느낌은 비스듬히 내려다본 픽셀 그림과
 * 발 위치 기준 깊이 정렬(아래쪽에 있는 것이 앞)로 낸다 (FR-맵-02, NFR-02).
 */
public class WorldRenderer {
    private static final int S = Art.SCALE;

    private final GameSession g;
    private final Art art;
    private final Fonts fonts;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final WorldMap map;
    private final int T;
    private final String[] theme;
    private final GlyphLayout layout = new GlyphLayout();
    private final List<FloatText> floats = new ArrayList<>();
    private float time;

    private static class FloatText {
        float x, y, t;
        String text;
        Color color;
    }

    private interface DrawCall {
        void draw();
    }

    private static class Sorted {
        final float y;
        final DrawCall call;

        Sorted(float y, DrawCall call) {
            this.y = y;
            this.call = call;
        }
    }

    public WorldRenderer(GameSession g, Art art, Fonts fonts, SpriteBatch batch, ShapeRenderer shapes) {
        this.g = g;
        this.art = art;
        this.fonts = fonts;
        this.batch = batch;
        this.shapes = shapes;
        this.map = g.map;
        this.T = g.balance.tileSize;
        this.theme = new String[map.width * map.height];
        for (Room r : map.rooms) {
            String th = r.dungeonId == null ? "CAMP" : r.dungeonId;
            for (int y = r.y0; y <= r.y1; y++) for (int x = r.x0; x <= r.x1; x++) theme[x + y * map.width] = th;
        }
        for (Link l : map.links) {
            String th = l.b.dungeonId != null ? l.b.dungeonId : (l.a.dungeonId != null ? l.a.dungeonId : "CAMP");
            Door a = l.doorA, b = l.doorB;
            int x0 = Math.min(a.tilesX[0], b.tilesX[0]) - 1, x1 = Math.max(a.tilesX[2], b.tilesX[2]) + 1;
            int y0 = Math.min(a.tilesY[0], b.tilesY[0]) - 1, y1 = Math.max(a.tilesY[2], b.tilesY[2]) + 1;
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++)
                    if (x >= 0 && y >= 0 && x < map.width && y < map.height && theme[x + y * map.width] == null)
                        theme[x + y * map.width] = th;
        }
    }

    public void addFloat(GameEvent e) {
        FloatText f = new FloatText();
        f.x = e.x;
        f.y = e.y;
        f.text = e.text;
        switch (e.tone) {
            case HERO_HIT: f.color = Ui.RED; break;
            case MONSTER_HIT: f.color = Color.WHITE; break;
            case ORE: f.color = Ui.GOLD; break;
            case HEAL: f.color = Ui.GREEN; break;
            case BLOCKED: f.color = Ui.PURPLE; break;
            default: f.color = Ui.TEXT;
        }
        floats.add(f);
    }

    public void update(float dt) {
        time += dt;
        for (Iterator<FloatText> it = floats.iterator(); it.hasNext(); ) {
            FloatText f = it.next();
            f.t += dt;
            f.y += 34 * dt;
            if (f.t > 0.9f) it.remove();
        }
    }

    public void render(OrthographicCamera cam) {
        float vx0 = cam.position.x - cam.viewportWidth / 2f, vy0 = cam.position.y - cam.viewportHeight / 2f;
        int tx0 = Math.max(0, (int) (vx0 / T) - 1), ty0 = Math.max(0, (int) (vy0 / T) - 2);
        int tx1 = Math.min(map.width - 1, (int) ((vx0 + cam.viewportWidth) / T) + 1);
        int ty1 = Math.min(map.height - 1, (int) ((vy0 + cam.viewportHeight) / T) + 1);

        batch.setProjectionMatrix(cam.combined);
        shapes.setProjectionMatrix(cam.combined);

        batch.begin();
        batch.setColor(Color.WHITE);
        drawTiles(tx0, ty0, tx1, ty1);
        batch.end();

        drawGroundEffects();

        batch.begin();
        List<Sorted> list = new ArrayList<>();
        collect(list, tx0, ty0, tx1, ty1);
        list.sort((a, b) -> Float.compare(b.y, a.y));
        for (Sorted s : list) s.call.draw();
        batch.setColor(Color.WHITE);
        batch.end();

        drawOverlays();

        batch.begin();
        drawTexts();
        batch.end();
    }

    // ───────────────────────── 타일

    private String themeAt(int x, int y) {
        String th = theme[x + y * map.width];
        return th == null ? "CAMP" : th;
    }

    private void drawTiles(int tx0, int ty0, int tx1, int ty1) {
        for (int y = ty1; y >= ty0; y--) {
            for (int x = tx0; x <= tx1; x++) {
                int t = map.tile(x, y);
                if (t == WorldMap.VOID) continue;
                String th = themeAt(x, y);
                float px = x * T, py = y * T;
                if (t == WorldMap.FLOOR) {
                    batch.draw(art.floor(th, x, y), px, py, T, T);
                } else if (t == WorldMap.WALL) {
                    int below = map.tile(x, y - 1);
                    boolean front = below == WorldMap.FLOOR || below == WorldMap.DOOR;
                    batch.draw(art.get(front ? "wallfront_" + th : "walltop_" + th), px, py, T, T);
                } else {
                    Door d = map.door(x, y);
                    batch.draw(art.floor(th, x, y), px, py, T, T);
                    if (d.link != null && d.link.gateClosed) {
                        batch.draw(art.get("walltop_" + th), px, py, T, T);
                    } else if (d.lockedByFight) {
                        batch.draw(art.get("bars"), px, py, T, T);
                    }
                }
            }
        }
        // 잠긴 던전 입구의 봉인 무늬
        for (Link l : map.links) {
            if (!l.gateClosed) continue;
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float cx = d.tilesX[1] * T + T / 2f, cy = d.tilesY[1] * T + T / 2f;
                if (cx < tx0 * T - T || cx > (tx1 + 1) * T + T || cy < ty0 * T - T || cy > (ty1 + 1) * T + T) continue;
                float a = 0.55f + 0.35f * MathUtils.sin(time * 3f);
                Color c = "FINAL".equals(l.gate) ? new Color(1f, 0.85f, 0.3f, a) : new Color(0.75f, 0.55f, 1f, a);
                batch.setColor(c);
                batch.draw(art.get("seal"), cx - T * 1.5f, cy - T * 1.5f, T * 3, T * 3);
                batch.setColor(Color.WHITE);
            }
        }
    }

    // ───────────────────────── 바닥 효과 (보스 범위 공격 예고, 모닥불 빛)

    private void drawGroundEffects() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Prop p : map.props) {
            if (p.kind != Prop.Kind.CAMPFIRE) continue;
            float cx = (p.tileX + 0.5f) * T, cy = (p.tileY + 0.5f) * T;
            float flick = 0.08f + 0.03f * MathUtils.sin(time * 9f);
            for (int i = 4; i >= 1; i--) {
                shapes.setColor(1f, 0.6f, 0.2f, flick * (5 - i) / 4f);
                shapes.circle(cx, cy, 40 + i * 26, 40);
            }
        }
        for (Monster m : g.monsters) {
            if (m.dead || m.state != Monster.State.SLAM_TELEGRAPH) continue;
            float total = g.balance.monster.bossSlamTelegraph;
            float prog = 1f - Math.max(0, m.stateT) / total;
            shapes.setColor(1f, 0.15f, 0.1f, 0.18f + 0.12f * MathUtils.sin(time * 20f));
            shapes.circle(m.slamX, m.slamY, m.slamRadius, 48);
            shapes.setColor(1f, 0.2f, 0.1f, 0.35f);
            shapes.circle(m.slamX, m.slamY, m.slamRadius * prog, 48);
        }
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Monster m : g.monsters) {
            if (m.dead || m.state != Monster.State.SLAM_TELEGRAPH) continue;
            shapes.setColor(1f, 0.3f, 0.2f, 0.9f);
            shapes.circle(m.slamX, m.slamY, m.slamRadius, 48);
        }
        shapes.end();
    }

    // ───────────────────────── 깊이 정렬되는 것들

    private void collect(List<Sorted> list, int tx0, int ty0, int tx1, int ty1) {
        for (Prop p : map.props) {
            list.add(new Sorted(p.tileY * T + 2, () -> drawProp(p)));
        }
        Room hr = g.currentRoom();
        for (Room r : map.rooms) {
            if (r.type != RoomType.ORE) continue;
            if (r.x1 < tx0 || r.x0 > tx1 || r.y1 < ty0 || r.y0 > ty1) continue;
            List<Vein> veins = g.veinsOf(r);
            if (veins == null) continue;
            for (Vein v : veins) list.add(new Sorted(v.tileY * T + 4, () -> drawVein(v)));
        }
        HeroActor h = g.hero;
        list.add(new Sorted(h.y, this::drawHero));
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            list.add(new Sorted(m.y, () -> drawMonster(m)));
        }
    }

    private void drawShadow(float x, float y, float w) {
        batch.setColor(Color.WHITE);
        batch.draw(art.get("shadow"), x - w / 2f, y - w * 0.18f, w, w * 0.38f);
    }

    private void drawProp(Prop p) {
        batch.setColor(Color.WHITE);
        float px = p.tileX * T, py = p.tileY * T;
        switch (p.kind) {
            case FORGE: {
                TextureRegion r = art.get("forge");
                batch.draw(r, px - 8, py - 6, r.getRegionWidth() * S, r.getRegionHeight() * S);
                // 화로 불빛
                float a = 0.6f + 0.3f * MathUtils.sin(time * 7f);
                batch.setColor(1f, 0.6f, 0.2f, a * 0.5f);
                batch.draw(art.white(), px + 12, py + 36, 12, 8);
                batch.setColor(Color.WHITE);
                break;
            }
            case BED: {
                TextureRegion r = art.get("bed");
                batch.draw(r, px - 4, py - 22, r.getRegionWidth() * S, r.getRegionHeight() * S);
                break;
            }
            default: {
                TextureRegion r = art.get(((int) (time * 6)) % 2 == 0 ? "campfire" : "campfire2");
                batch.draw(r, px, py - 4, r.getRegionWidth() * S, r.getRegionHeight() * S);
            }
        }
    }

    private void drawVein(Vein v) {
        TextureRegion r = art.vein(v.ore);
        float px = v.tileX * T, py = v.tileY * T;
        int maxHits = g.balance.mining.hitsToBreak;
        float shade = 0.65f + 0.35f * v.hitsLeft / (float) maxHits;
        batch.setColor(shade, shade, shade, 1f);
        float jiggle = v.hitsLeft < maxHits ? MathUtils.sin(time * 40f) * 0.6f : 0;
        batch.draw(r, px + jiggle, py, T, T);
        batch.setColor(Color.WHITE);
    }

    private void drawHero() {
        HeroActor h = g.hero;
        drawShadow(h.x, h.y, 26);
        boolean blink = h.invuln > 0 && !h.dashing() && ((int) (h.invuln * 20)) % 2 == 0;
        float alpha = blink ? 0.35f : 1f;
        boolean walk = h.moving && ((int) (h.walkTime * 8)) % 2 == 1;
        String name;
        boolean flip = false;
        switch (h.facing) {
            case UP: name = "hero_up"; break;
            case LEFT: name = "hero_side"; flip = true; break;
            case RIGHT: name = "hero_side"; break;
            default: name = "hero_down";
        }
        TextureRegion r = art.get(walk ? name + "_walk" : name);
        float w = r.getRegionWidth() * S, hgt = r.getRegionHeight() * S;
        boolean swordBehind = h.facing == Facing.UP;
        if (swordBehind) drawSword(h, alpha);
        batch.setColor(1f, 1f, 1f, alpha);
        if (h.dashing()) batch.setColor(0.8f, 0.9f, 1f, 0.8f);
        float bob = h.moving ? (walk ? 1 : 0) : 0;
        if (flip) batch.draw(r, h.x + w / 2f, h.y - 2 + bob, -w, hgt);
        else batch.draw(r, h.x - w / 2f, h.y - 2 + bob, w, hgt);
        if (!swordBehind) drawSword(h, alpha);
        batch.setColor(Color.WHITE);
        if (h.mineSwingT > 0) {
            // 곡괭이질: 앞쪽에 작은 불똥
            float fx = h.x + (h.facing == Facing.LEFT ? -18 : h.facing == Facing.RIGHT ? 18 : 0);
            float fy = h.y + (h.facing == Facing.UP ? 30 : h.facing == Facing.DOWN ? 0 : 14);
            batch.setColor(1f, 0.9f, 0.4f, h.mineSwingT * 4f);
            batch.draw(art.white(), fx - 3, fy - 3, 6, 6);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawSword(HeroActor h, float alpha) {
        Weapon w = g.state.equipped();
        if (w == null) return;
        TextureRegion r = art.sword(w.defId, w.awakened);
        float aim = MathUtils.atan2(h.aimY, h.aimX) * MathUtils.radDeg;
        float angle;
        if (h.swingT > 0 && h.swingKind == 0) {
            float p = 1f - h.swingT / 0.18f;
            angle = aim + 70 - 140 * p;
        } else if (h.swingT > 0 && h.swingKind == 1) {
            angle = aim + 360 * (1f - h.swingT / 0.3f);
        } else if (h.swingKind == 2 && h.swingT > 0) {
            angle = aim;
        } else {
            switch (h.facing) {
                case LEFT: angle = 125; break;
                case RIGHT: angle = 55; break;
                case UP: angle = 100; break;
                default: angle = 75;
            }
        }
        float hx = h.x + (h.facing == Facing.LEFT ? -8 : 8), hy = h.y + 16;
        if (h.facing == Facing.UP) hx = h.x + 8;
        batch.setColor(1f, 1f, 1f, alpha);
        // 그림 속 칼날은 45도로 누워 있으므로 그만큼 빼고 돌린다. 손잡이(왼쪽 아래)를 회전 중심으로.
        batch.draw(r, hx - 6, hy - 6, 6, 6, 32, 32, 1f, 1f, angle - 45);
        if ("holy_sealed".equals(w.defId) && h.swingT <= 0) {
            batch.setColor(1f, 1f, 1f, alpha * 0.8f);
            batch.draw(art.get("chain"), hx - 4, hy, 4, 4, 18, 18, 1f, 1f, angle - 45);
        }
    }

    private String spriteFor(Monster m) {
        return m.def.sprite;
    }

    private void drawMonster(Monster m) {
        TextureRegion r = art.get(spriteFor(m));
        float scale = S * (m.finalBoss ? 2.0f : m.boss ? 1.7f : 1f);
        float w = r.getRegionWidth() * scale, hgt = r.getRegionHeight() * scale;
        drawShadow(m.x, m.y, w * 0.8f);
        float bob = m.state == Monster.State.CHASE ? Math.abs(MathUtils.sin(m.walkTime * 9f)) * 3f : 0;
        if ("bat".equals(m.def.sprite)) bob = 6 + MathUtils.sin(time * 8f + m.x) * 4f;
        if (m.flash > 0) batch.setColor(1f, 0.45f, 0.45f, 1f);
        else if (m.state == Monster.State.TELEGRAPH) batch.setColor(1f, 0.75f, 0.6f, 1f);
        else batch.setColor(Color.WHITE);
        boolean flip = m.dirX < -0.2f;
        if (flip) batch.draw(r, m.x + w / 2f, m.y - 2 + bob, -w, hgt);
        else batch.draw(r, m.x - w / 2f, m.y - 2 + bob, w, hgt);
        batch.setColor(Color.WHITE);
    }

    // ───────────────────────── 공격 효과, 체력 막대

    private void drawOverlays() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        HeroActor h = g.hero;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (h.swingT > 0) {
            Weapon w = g.state.equipped();
            Color c = swingColor(w == null ? "" : w.defId);
            float cy = h.centerY();
            if (h.swingKind == 0) {
                float aim = MathUtils.atan2(h.aimY, h.aimX) * MathUtils.radDeg;
                float arc = g.balance.attack.basicArcDegrees;
                shapes.setColor(c.r, c.g, c.b, 0.32f * (h.swingT / 0.18f) + 0.1f);
                shapes.arc(h.x, cy, g.basicRange(), aim - arc / 2f, arc, 24);
            } else if (h.swingKind == 1) {
                float rad = g.balance.attack.spinRadiusTiles * T;
                shapes.setColor(c.r, c.g, c.b, 0.3f * (h.swingT / 0.3f));
                shapes.circle(h.x, cy, rad, 40);
            } else {
                shapes.setColor(c.r, c.g, c.b, 0.35f);
                shapes.circle(h.x, cy, h.hurtRadius + 10, 20);
            }
        }
        for (Monster m : g.monsters) {
            if (m.dead || m.boss || m.hp >= m.maxHp) continue;
            float w = 30, y = m.y + m.hurtRadius * 2.4f + 8;
            shapes.setColor(0, 0, 0, 0.7f);
            shapes.rect(m.x - w / 2 - 1, y - 1, w + 2, 6);
            shapes.setColor(0.9f, 0.25f, 0.2f, 1f);
            shapes.rect(m.x - w / 2, y, w * m.hp / (float) m.maxHp, 4);
        }
        shapes.end();
    }

    private Color swingColor(String defId) {
        switch (defId) {
            case "blackiron": return new Color(0.75f, 0.85f, 1f, 1f);
            case "bloodmoon": return new Color(1f, 0.4f, 0.5f, 1f);
            case "skyfall": return new Color(0.5f, 0.85f, 1f, 1f);
            case "holy_awakened": return new Color(1f, 0.95f, 0.6f, 1f);
            default: return new Color(1f, 1f, 1f, 1f);
        }
    }

    // ───────────────────────── 글자

    private void drawTexts() {
        BitmapFont f = fonts.normal;
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.state == Monster.State.TELEGRAPH || m.state == Monster.State.SLAM_TELEGRAPH) {
                f.setColor(Ui.RED);
                float top = m.y + m.hurtRadius * 2.4f + 26;
                layout.setText(fonts.large, "!");
                fonts.large.setColor(Ui.RED);
                fonts.large.draw(batch, "!", m.x - layout.width / 2f, top + 10);
            }
        }
        for (FloatText ft : floats) {
            BitmapFont font = ft.color == Ui.PURPLE || ft.color == Ui.GOLD ? fonts.normal : fonts.large;
            font.setColor(ft.color.r, ft.color.g, ft.color.b, 1f - Math.max(0, ft.t - 0.6f) / 0.3f);
            layout.setText(font, ft.text);
            font.draw(batch, ft.text, ft.x - layout.width / 2f, ft.y + layout.height);
        }
        if (g.prompt != null) {
            layout.setText(fonts.normal, g.prompt);
            float w = layout.width + 20, hgt = layout.height + 14;
            float x = g.promptX - w / 2f, y = g.promptY;
            batch.setColor(0, 0, 0, 0.7f);
            batch.draw(art.white(), x, y, w, hgt);
            batch.setColor(Color.WHITE);
            fonts.normal.setColor(Ui.GOLD);
            fonts.normal.draw(batch, g.prompt, x + 10, y + hgt - 7);
        }
        fonts.normal.setColor(Color.WHITE);
        fonts.large.setColor(Color.WHITE);
    }
}
