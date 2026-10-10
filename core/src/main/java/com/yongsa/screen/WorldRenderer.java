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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.YongsaGame;
import com.yongsa.combat.Facing;
import com.yongsa.combat.HeroActor;
import com.yongsa.combat.Monster;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Fonts;
import com.yongsa.gfx.Lighting;
import com.yongsa.gfx.Particles;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.model.OreType;
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
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 맵과 그 위의 것들을 그린다. 2.5D 느낌은 비스듬히 내려다본 픽셀 그림과
 * 발 위치 기준 깊이 정렬(아래쪽에 있는 것이 앞)로 낸다 (FR-맵-02, NFR-02).
 *
 * 그리는 순서: 빛 지도 → 바닥·벽 → 깊이 정렬한 것들 → 빛 지도 곱하기 → 빛나는 효과(더하기) → 글자.
 */
public class WorldRenderer implements Disposable {
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
    private final Lighting lighting = new Lighting(YongsaGame.WIDTH, YongsaGame.HEIGHT);
    private final Particles particles;
    private final Matrix4 screenProj = new Matrix4().setToOrtho2D(0, 0, YongsaGame.WIDTH, YongsaGame.HEIGHT);

    private final List<FloatText> floats = new ArrayList<>();
    private final List<float[]> torches = new ArrayList<>();
    private final List<Corpse> corpses = new ArrayList<>();
    private final List<Ghost> ghosts = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Flash> flashes = new ArrayList<>();
    private final Map<Monster, Float> ages = new IdentityHashMap<>();
    private final Color ambient = new Color(0.6f, 0.55f, 0.5f, 1f);
    private boolean ambientSet;
    private float time, ghostT, moteT, emberT;
    private int lastStep = -1;
    private float viewX0, viewY0, viewX1, viewY1;

    private static class FloatText {
        float x, y, t;
        String text;
        Color color;
        boolean big;
    }

    private static class Corpse {
        TextureRegion region;
        float x, y, w, h, t, dur;
        boolean flip, boss;
    }

    private static class Ghost {
        TextureRegion region;
        float x, y, w, h, t;
        boolean flip;
    }

    private static class Ring {
        float x, y, r0, r1, t, dur;
        Color color;
    }

    private static class Flash {
        float x, y, radius, t, dur;
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
        this.particles = new Particles(art.white(), art.glow);
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
        // 방 윗벽에 일정한 간격으로 횃불을 건다. 문 바로 옆은 비운다.
        for (Room r : map.rooms) {
            int y = r.y1;
            for (int x = r.x0 + 3; x <= r.x1 - 3; x += 5) {
                if (map.tile(x, y) != WorldMap.WALL || map.tile(x, y - 1) != WorldMap.FLOOR) continue;
                if (map.tile(x - 1, y) == WorldMap.DOOR || map.tile(x + 1, y) == WorldMap.DOOR
                        || map.tile(x - 2, y) == WorldMap.DOOR || map.tile(x + 2, y) == WorldMap.DOOR) continue;
                torches.add(new float[]{x * T + T / 2f, y * T, MathUtils.random(10f)});
            }
        }
    }

    // ───────────────────────── 이벤트 → 효과

    public void onEvent(GameEvent e) {
        switch (e.type) {
            case FLOAT_TEXT:
                addFloat(e);
                break;
            case HIT_FX:
                if (e.tone == GameEvent.Tone.HERO_HIT) {
                    particles.burst(e.x, e.y, 12, new Color(1f, 0.3f, 0.25f, 1f), 160, 0.35f, 3, true);
                    flash(e.x, e.y, 90, new Color(1f, 0.2f, 0.15f, 1f), 0.15f);
                } else {
                    Weapon w = g.state.equipped();
                    Color c = swingColor(w == null ? "" : w.defId);
                    particles.spray(e.x, e.y, e.dirX, e.dirY, e.boss ? 14 : 9, c, 260, 0.7f, 0.3f, 3);
                    particles.spray(e.x, e.y, e.dirX, e.dirY, 4, Color.WHITE, 340, 0.3f, 0.18f, 2);
                    flash(e.x, e.y, 70, c, 0.12f);
                }
                break;
            case MONSTER_DIED: {
                Corpse c = new Corpse();
                c.region = art.get(e.sprite);
                float scale = S * e.size;
                c.w = art.width(c.region) * scale;
                c.h = art.height(c.region) * scale;
                c.x = e.x;
                c.y = e.y;
                c.flip = e.flip;
                c.boss = e.boss;
                c.dur = e.boss ? 1.4f : 0.4f;
                corpses.add(c);
                Color tint = deathColor(e.sprite);
                particles.smoke(e.x, e.y + c.h * 0.3f, e.boss ? 26 : 9, new Color(0.25f, 0.22f, 0.3f, 0.7f), c.w * 0.4f, e.boss ? 26 : 12, e.boss ? 1.4f : 0.7f);
                particles.burst(e.x, e.y + c.h * 0.35f, e.boss ? 60 : 16, tint, e.boss ? 380 : 220, 0.6f, 3, true);
                particles.debris(e.x, e.y + 4, e.boss ? 24 : 6, tint, e.boss ? 200 : 120, 4);
                flash(e.x, e.y + c.h * 0.35f, e.boss ? 400 : 120, tint, e.boss ? 0.8f : 0.25f);
                if (e.boss) ring(e.x, e.y, 10, 360, 0.9f, tint);
                break;
            }
            case SLAM_FX:
                ring(e.x, e.y, e.size * 0.3f, e.size * 1.15f, 0.45f, new Color(1f, 0.55f, 0.3f, 1f));
                particles.debris(e.x, e.y, 18, new Color(0.45f, 0.4f, 0.38f, 1f), 260, 5);
                particles.smoke(e.x, e.y, 14, new Color(0.4f, 0.36f, 0.34f, 0.55f), e.size * 0.8f, 18, 0.8f);
                flash(e.x, e.y, e.size * 2.2f, new Color(1f, 0.5f, 0.25f, 1f), 0.3f);
                break;
            case MINE_FX: {
                Color oc = oreColor(e.ore);
                float dx = g.hero.x - e.x, dy = g.hero.centerY() - e.y, l = Math.max(1f, (float) Math.hypot(dx, dy));
                particles.spray(e.x, e.y + 6, dx / l, dy / l, 8, new Color(1f, 0.85f, 0.45f, 1f), 220, 0.9f, 0.25f, 2);
                particles.debris(e.x, e.y, 4, new Color(0.42f, 0.42f, 0.48f, 1f), 120, 3);
                particles.debris(e.x, e.y, 2, oc, 120, 3);
                flash(e.x, e.y, 60, oc, 0.1f);
                break;
            }
            case ORE_GAINED: {
                Color oc = oreColor(e.ore);
                particles.debris(e.x, e.y - T / 2f, 14, oc, 180, 4);
                particles.burst(e.x, e.y - T / 4f, 18, oc, 200, 0.6f, 3, true);
                ring(e.x, e.y - T / 2f, 6, 70, 0.35f, oc);
                flash(e.x, e.y, 160, oc, 0.4f);
                break;
            }
            case WARP:
                particles.burst(g.hero.x, g.hero.centerY(), 40, new Color(0.6f, 0.5f, 1f, 1f), 260, 0.8f, 3, true);
                flash(g.hero.x, g.hero.y, 260, new Color(0.6f, 0.5f, 1f, 1f), 0.6f);
                break;
            case HERO_DIED:
                particles.clear();
                corpses.clear();
                ghosts.clear();
                rings.clear();
                break;
            default:
                break;
        }
    }

    public void addFloat(GameEvent e) {
        FloatText f = new FloatText();
        f.x = e.x + MathUtils.random(-6f, 6f);
        f.y = e.y;
        f.text = e.text;
        switch (e.tone) {
            case HERO_HIT: f.color = Ui.RED; f.big = true; break;
            case MONSTER_HIT: f.color = Color.WHITE; f.big = true; break;
            case ORE: f.color = Ui.GOLD; break;
            case HEAL: f.color = Ui.GREEN; f.big = true; break;
            case BLOCKED: f.color = Ui.PURPLE; break;
            default: f.color = Ui.TEXT;
        }
        floats.add(f);
    }

    private void flash(float x, float y, float radius, Color c, float dur) {
        Flash f = new Flash();
        f.x = x;
        f.y = y;
        f.radius = radius;
        f.color = new Color(c);
        f.dur = dur;
        flashes.add(f);
    }

    private void ring(float x, float y, float r0, float r1, float dur, Color c) {
        Ring r = new Ring();
        r.x = x;
        r.y = y;
        r.r0 = r0;
        r.r1 = r1;
        r.dur = dur;
        r.color = new Color(c);
        rings.add(r);
    }

    // ───────────────────────── 매 프레임

    public void update(float dt) {
        time += dt;
        for (Iterator<FloatText> it = floats.iterator(); it.hasNext(); ) {
            FloatText f = it.next();
            f.t += dt;
            f.y += (f.t < 0.15f ? 90 : 26) * dt;
            if (f.t > 0.9f) it.remove();
        }
        particles.update(dt);
        for (Iterator<Corpse> it = corpses.iterator(); it.hasNext(); ) {
            Corpse c = it.next();
            c.t += dt;
            if (c.boss && c.t < c.dur * 0.8f && MathUtils.random() < dt * 14f) {
                float px = c.x + MathUtils.random(-c.w / 3f, c.w / 3f), py = c.y + MathUtils.random(c.h * 0.7f);
                particles.burst(px, py, 8, new Color(1f, 0.6f, 0.35f, 1f), 200, 0.4f, 3, true);
                flash(px, py, 120, new Color(1f, 0.7f, 0.4f, 1f), 0.15f);
            }
            if (c.t > c.dur) it.remove();
        }
        for (Iterator<Ghost> it = ghosts.iterator(); it.hasNext(); ) {
            Ghost gh = it.next();
            gh.t += dt;
            if (gh.t > 0.2f) it.remove();
        }
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring r = it.next();
            r.t += dt;
            if (r.t > r.dur) it.remove();
        }
        for (Iterator<Flash> it = flashes.iterator(); it.hasNext(); ) {
            Flash f = it.next();
            f.t += dt;
            if (f.t > f.dur) it.remove();
        }

        HeroActor h = g.hero;
        // 돌진 잔상
        if (h.rolling()) {
            ghostT -= dt;
            if (MathUtils.random() < dt * 40f) {
                particles.smoke(h.x, h.y + 2, 1, new Color(0.75f, 0.7f, 0.62f, 0.3f), 6, 7, 0.4f);
            }
            if (ghostT <= 0) {
                ghostT = 0.06f;
                Ghost gh = new Ghost();
                gh.region = heroRegion(h);
                gh.w = art.width(gh.region) * S;
                gh.h = art.height(gh.region) * S;
                gh.x = h.x;
                gh.y = h.y;
                gh.flip = h.facing == Facing.LEFT;
                ghosts.add(gh);
            }
        }
        // 발걸음 먼지
        int step = h.moving && !h.rolling() ? (int) (h.walkTime * 4f) : -1;
        if (step >= 0 && step != lastStep) {
            particles.smoke(h.x, h.y + 1, 2, new Color(0.75f, 0.7f, 0.62f, 0.28f), 4, 6, 0.45f);
        }
        lastStep = step;

        // 몬스터 등장
        for (Monster m : g.monsters) {
            if (m.dead || ages.containsKey(m)) continue;
            ages.put(m, 0f);
            particles.smoke(m.x, m.y + 8, m.boss ? 22 : 8, new Color(0.3f, 0.25f, 0.4f, 0.75f), m.boss ? 40 : 12, m.boss ? 22 : 12, 0.7f);
            particles.burst(m.x, m.y + 10, m.boss ? 30 : 8, new Color(0.7f, 0.5f, 1f, 1f), 160, 0.5f, 3, true);
        }
        for (Iterator<Map.Entry<Monster, Float>> it = ages.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Monster, Float> en = it.next();
            if (!g.monsters.contains(en.getKey())) it.remove();
            else en.setValue(en.getValue() + dt);
        }

        // 떠다니는 먼지 (던전), 모닥불 불똥 (캠프)
        Room room = g.currentRoom();
        boolean dungeon = room != null && room.dungeonId != null;
        moteT -= dt;
        if (moteT <= 0 && viewX1 > viewX0) {
            moteT = dungeon ? 0.07f : 0.15f;
            Color mc = dungeon ? themeMote(room.dungeonId) : new Color(1f, 0.85f, 0.6f, 0.3f);
            Particles.P p = particles.spawn(MathUtils.random(viewX0, viewX1), MathUtils.random(viewY0, viewY1),
                    MathUtils.random(3f, 5f), 2, mc);
            p.vx = MathUtils.random(-8f, 8f);
            p.vy = MathUtils.random(4f, 14f);
            p.glow = true;
            p.soft = false;
        }
        emberT -= dt;
        if (emberT <= 0) {
            emberT = 0.05f;
            for (Prop pr : map.props) {
                if (pr.kind != Prop.Kind.CAMPFIRE) continue;
                float cx = (pr.tileX + 0.5f) * T, cy = pr.tileY * T + 12;
                Particles.P p = particles.spawn(cx + MathUtils.random(-8f, 8f), cy, MathUtils.random(0.8f, 1.6f),
                        MathUtils.random(2f, 3f), new Color(1f, MathUtils.random(0.5f, 0.8f), 0.2f, 1f));
                p.vz = MathUtils.random(30f, 70f);
                p.vx = MathUtils.random(-12f, 12f);
                p.glow = true;
                p.endSize = 0;
            }
            for (float[] t : torches) {
                if (MathUtils.random() > 0.08f || !inView(t[0], t[1])) continue;
                Particles.P p = particles.spawn(t[0] + MathUtils.random(-2f, 2f), t[1] + 22, MathUtils.random(0.5f, 0.9f), 2,
                        new Color(1f, 0.7f, 0.3f, 1f));
                p.vz = MathUtils.random(20f, 40f);
                p.glow = true;
                p.endSize = 0;
            }
        }
        // 귀환 포탈 소용돌이
        Room hr = g.heroRoom();
        if (hr != null && g.hasPortal(hr) && MathUtils.random() < dt * 30f) {
            float px = g.portalX(hr), py = g.portalY(hr);
            float a = MathUtils.random(MathUtils.PI2);
            Particles.P p = particles.spawn(px + MathUtils.cos(a) * 38, py + MathUtils.sin(a) * 18, 0.7f, 3,
                    new Color(0.55f, 0.5f, 1f, 1f));
            p.vx = -MathUtils.cos(a) * 50;
            p.vy = -MathUtils.sin(a) * 25;
            p.vz = 40;
            p.glow = true;
            p.endSize = 0;
        }

        // 방 분위기에 맞춰 바탕 밝기를 천천히 바꾼다
        Color target = ambientFor(room);
        if (!ambientSet) {
            ambient.set(target);
            ambientSet = true;
        } else {
            float a = 1f - (float) Math.exp(-3f * dt);
            ambient.lerp(target, a);
        }
    }

    private boolean inView(float x, float y) {
        return x > viewX0 - 64 && x < viewX1 + 64 && y > viewY0 - 64 && y < viewY1 + 64;
    }

    private Color ambientFor(Room r) {
        String th = r == null || r.dungeonId == null ? "CAMP" : r.dungeonId;
        switch (th) {
            case "LEFT": return new Color(0.30f, 0.32f, 0.38f, 1f);
            case "RIGHT": return new Color(0.34f, 0.24f, 0.27f, 1f);
            case "TOP": return new Color(0.27f, 0.24f, 0.38f, 1f);
            default: return new Color(0.5f, 0.45f, 0.44f, 1f);
        }
    }

    private Color themeMote(String th) {
        switch (th) {
            case "RIGHT": return new Color(1f, 0.45f, 0.45f, 0.22f);
            case "TOP": return new Color(0.7f, 0.6f, 1f, 0.25f);
            default: return new Color(0.75f, 0.8f, 0.9f, 0.2f);
        }
    }

    // ───────────────────────── 빛 지도

    /** 맵을 그리기 전에 부른다 (빛 지도를 따로 그린다). */
    public void renderLights(OrthographicCamera cam) {
        computeView(cam);
        lighting.begin(ambient.r, ambient.g, ambient.b);
        HeroActor h = g.hero;
        lighting.add(h.x, h.y + 14, 250, 1f, 0.92f, 0.8f, 0.6f);
        if (h.swingT > 0) {
            Weapon w = g.state.equipped();
            Color c = swingColor(w == null ? "" : w.defId);
            lighting.add(h.x, h.centerY(), 170, c.r, c.g, c.b, 0.5f);
        }
        Weapon eq = g.state.equipped();
        if (eq != null && (eq.awakened || "holy_awakened".equals(eq.defId))) {
            Color c = swingColor(eq.defId);
            lighting.add(h.x, h.centerY(), 120, c.r, c.g, c.b, 0.3f + 0.08f * MathUtils.sin(time * 4f));
        }
        for (float[] t : torches) {
            if (!inView(t[0], t[1] - 100)) continue;
            float fl = 0.85f + 0.1f * MathUtils.sin(time * 11f + t[2]) + 0.05f * MathUtils.sin(time * 23f + t[2] * 2);
            lighting.add(t[0], t[1] + 10, 175 * fl, 1f, 0.62f, 0.32f, 0.75f);
        }
        for (Prop p : map.props) {
            float cx = (p.tileX + p.w / 2f) * T, cy = (p.tileY + p.h / 2f) * T;
            if (p.kind == Prop.Kind.CAMPFIRE) {
                float fl = 1f + 0.06f * MathUtils.sin(time * 9f) + 0.04f * MathUtils.sin(time * 17f);
                lighting.add(cx, cy, 360 * fl, 1f, 0.62f, 0.3f, 0.95f);
            } else if (p.kind == Prop.Kind.FORGE) {
                lighting.add(cx - 8, cy + 10, 150, 1f, 0.5f, 0.2f, 0.7f + 0.15f * MathUtils.sin(time * 7f));
            }
        }
        for (Room r : map.rooms) {
            if (r.type == RoomType.ORE) {
                List<Vein> veins = g.veinsOf(r);
                if (veins == null) continue;
                for (Vein v : veins) {
                    Color c = oreColor(v.ore);
                    float px = (v.tileX + 0.5f) * T, py = (v.tileY + 0.5f) * T;
                    if (!inView(px, py)) continue;
                    lighting.add(px, py, 70, c.r, c.g, c.b, 0.45f + 0.1f * MathUtils.sin(time * 3f + v.tileX));
                }
            } else if (g.hasPortal(r)) {
                float px = g.portalX(r), py = g.portalY(r);
                if (inView(px, py)) lighting.add(px, py, 230, 0.55f, 0.5f, 1f, 0.8f + 0.1f * MathUtils.sin(time * 3f));
            }
        }
        for (Link l : map.links) {
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float cx = d.tilesX[1] * T + T / 2f, cy = d.tilesY[1] * T + T / 2f;
                if (!inView(cx, cy)) continue;
                if (l.gateClosed) {
                    Color c = gateColor(l);
                    lighting.add(cx, cy, 130, c.r, c.g, c.b, 0.6f + 0.15f * MathUtils.sin(time * 3f));
                } else if (d.lockedByFight) {
                    lighting.add(cx, cy, 80, 1f, 0.3f, 0.2f, 0.45f);
                }
            }
        }
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.state == Monster.State.SLAM_TELEGRAPH) {
                lighting.add(m.slamX, m.slamY, m.slamRadius * 1.6f, 1f, 0.25f, 0.15f, 0.55f);
            }
            if (m.finalBoss) lighting.add(m.x, m.y + 40, 260, 0.7f, 0.3f, 1f, 0.6f);
        }
        for (Flash f : flashes) {
            float a = 1f - f.t / f.dur;
            lighting.add(f.x, f.y, f.radius, f.color.r, f.color.g, f.color.b, a);
        }
        lighting.render(batch, cam);
    }

    private void computeView(OrthographicCamera cam) {
        float vw = cam.viewportWidth * cam.zoom, vh = cam.viewportHeight * cam.zoom;
        viewX0 = cam.position.x - vw / 2f;
        viewY0 = cam.position.y - vh / 2f;
        viewX1 = viewX0 + vw;
        viewY1 = viewY0 + vh;
    }

    // ───────────────────────── 그리기

    public void render(OrthographicCamera cam) {
        computeView(cam);
        int tx0 = Math.max(0, (int) (viewX0 / T) - 1), ty0 = Math.max(0, (int) (viewY0 / T) - 2);
        int tx1 = Math.min(map.width - 1, (int) (viewX1 / T) + 1);
        int ty1 = Math.min(map.height - 1, (int) (viewY1 / T) + 1);

        batch.setProjectionMatrix(cam.combined);
        shapes.setProjectionMatrix(cam.combined);

        batch.begin();
        batch.setColor(Color.WHITE);
        drawTiles(tx0, ty0, tx1, ty1);
        drawTorches();
        batch.end();

        drawGroundEffects();

        batch.begin();
        drawGhosts();
        List<Sorted> list = new ArrayList<>();
        collect(list, tx0, ty0, tx1, ty1);
        list.sort((a, b) -> Float.compare(b.y, a.y));
        for (Sorted s : list) s.call.draw();
        particles.drawNormal(batch);
        batch.setColor(Color.WHITE);
        batch.end();

        lighting.composite(batch);

        batch.setProjectionMatrix(cam.combined);
        shapes.setProjectionMatrix(cam.combined);
        drawGlow();
        drawOverlays();

        batch.setProjectionMatrix(screenProj);
        batch.begin();
        drawTexts(cam);
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
                if (t == WorldMap.WALL) {
                    int below = map.tile(x, y - 1);
                    boolean front = below == WorldMap.FLOOR || below == WorldMap.DOOR;
                    batch.draw(art.get(front ? "wallfront_" + th : "walltop_" + th), px, py, T, T);
                    continue;
                }
                batch.draw(art.floor(th, x, y), px, py, T, T);
                if (t == WorldMap.FLOOR) {
                    TextureRegion d = art.decal(th, x, y);
                    if (d != null) batch.draw(d, px, py, T, T);
                }
                // 벽 옆 그늘
                if (map.tile(x, y + 1) == WorldMap.WALL) batch.draw(art.get("ao_top"), px, py, T, T);
                if (map.tile(x - 1, y) == WorldMap.WALL) batch.draw(art.get("ao_left"), px, py, T, T);
                if (map.tile(x + 1, y) == WorldMap.WALL) batch.draw(art.get("ao_right"), px, py, T, T);
                if (t == WorldMap.DOOR) {
                    Door d = map.door(x, y);
                    if (d.link != null && d.link.gateClosed) {
                        batch.setColor(0.05f, 0.03f, 0.08f, 0.65f);
                        batch.draw(art.white(), px, py, T, T);
                        batch.setColor(Color.WHITE);
                    } else if (d.lockedByFight) {
                        batch.draw(art.get("bars"), px, py, T, T);
                    }
                }
            }
        }
    }

    private void drawTorches() {
        for (float[] t : torches) {
            if (!inView(t[0], t[1])) continue;
            boolean alt = ((int) (time * 8 + t[2])) % 2 == 0;
            batch.draw(art.get(alt ? "torch_a" : "torch_b"), t[0] - 8, t[1], 16, 32);
        }
    }

    // ───────────────────────── 바닥 효과 (포탈 바닥, 보스 범위 공격 예고)

    private void drawGroundEffects() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Room r : map.rooms) {
            if (!g.hasPortal(r)) continue;
            float px = g.portalX(r), py = g.portalY(r);
            if (!inView(px, py)) continue;
            shapes.setColor(0.05f, 0.02f, 0.12f, 0.85f);
            shapes.ellipse(px - 44, py - 22, 88, 44, 40);
            shapes.setColor(0.18f, 0.1f, 0.38f, 0.9f);
            shapes.ellipse(px - 34, py - 16, 68, 32, 40);
        }
        for (Monster m : g.monsters) {
            if (m.dead || m.state != Monster.State.SLAM_TELEGRAPH) continue;
            float total = g.balance.monster.bossSlamTelegraph;
            float prog = 1f - Math.max(0, m.stateT) / total;
            shapes.setColor(0.9f, 0.1f, 0.05f, 0.16f + 0.08f * MathUtils.sin(time * 20f));
            shapes.circle(m.slamX, m.slamY, m.slamRadius, 56);
            shapes.setColor(1f, 0.2f, 0.1f, 0.3f);
            shapes.circle(m.slamX, m.slamY, m.slamRadius * prog, 56);
        }
        shapes.end();
    }

    // ───────────────────────── 깊이 정렬되는 것들

    private void collect(List<Sorted> list, int tx0, int ty0, int tx1, int ty1) {
        for (Prop p : map.props) {
            list.add(new Sorted(p.tileY * T + 2, () -> drawProp(p)));
        }
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
        for (Corpse c : corpses) list.add(new Sorted(c.y, () -> drawCorpse(c)));
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
                drawShadow(px + 28, py + 2, 64);
                batch.draw(r, px - 8, py - 6, art.width(r) * S, art.height(r) * S);
                break;
            }
            case BED: {
                TextureRegion r = art.get("bed");
                batch.draw(r, px - 4, py - 22, art.width(r) * S, art.height(r) * S);
                break;
            }
            default: {
                TextureRegion r = art.get(((int) (time * 6)) % 2 == 0 ? "campfire" : "campfire2");
                batch.draw(r, px, py - 4, art.width(r) * S, art.height(r) * S);
            }
        }
    }

    private void drawVein(Vein v) {
        TextureRegion r = art.vein(v.ore);
        float px = v.tileX * T, py = v.tileY * T;
        int maxHits = g.balance.mining.hitsToBreak;
        drawShadow(px + T / 2f, py + 4, 30);
        float shade = 0.7f + 0.3f * v.hitsLeft / (float) maxHits;
        batch.setColor(shade, shade, shade, 1f);
        float jiggle = v.hitsLeft < maxHits ? MathUtils.sin(time * 40f) * 0.6f : 0;
        batch.draw(r, px + jiggle, py, T, T);
        batch.setColor(Color.WHITE);
    }

    private TextureRegion heroRegion(HeroActor h) {
        boolean walk = h.moving && ((int) (h.walkTime * 8)) % 2 == 1;
        String name;
        switch (h.facing) {
            case UP: name = "hero_up"; break;
            case LEFT:
            case RIGHT: name = "hero_side"; break;
            default: name = "hero_down";
        }
        return art.get(walk ? name + "_walk" : name);
    }

    private void drawHero() {
        HeroActor h = g.hero;
        drawShadow(h.x, h.y, 26);
        if (h.rolling()) {
            drawRoll(h);
            return;
        }
        boolean blink = h.invuln > 0 && ((int) (h.invuln * 20)) % 2 == 0;
        float alpha = blink ? 0.35f : 1f;
        boolean walk = h.moving && ((int) (h.walkTime * 8)) % 2 == 1;
        boolean flip = h.facing == Facing.LEFT;
        TextureRegion r = heroRegion(h);
        float w = art.width(r) * S, hgt = art.height(r) * S;
        boolean swordBehind = h.facing == Facing.UP;
        if (swordBehind) drawSword(h, alpha);
        batch.setColor(1f, 1f, 1f, alpha);
        // 휘두를 때 몸을 살짝 찌그러뜨려 힘이 들어간 느낌을 준다
        float squash = h.swingT > 0 && h.swingKind == 0 ? 1f - 0.08f * (h.swingT / 0.18f) : 1f;
        float bob = h.moving ? (walk ? 2 : 0) : 0;
        float dw = w * (2f - squash), dh = hgt * squash;
        if (flip) batch.draw(r, h.x + dw / 2f, h.y - 2 + bob, -dw, dh);
        else batch.draw(r, h.x - dw / 2f, h.y - 2 + bob, dw, dh);
        if (h.flash > 0 && !blink) {
            Particles.additive(batch);
            batch.setColor(1f, 0.3f, 0.3f, Math.min(1f, h.flash * 3f) * 0.6f);
            if (flip) batch.draw(r, h.x + dw / 2f, h.y - 2 + bob, -dw, dh);
            else batch.draw(r, h.x - dw / 2f, h.y - 2 + bob, dw, dh);
            Particles.normal(batch);
        }
        if (!swordBehind) drawSword(h, alpha);
        batch.setColor(Color.WHITE);
    }

    /** 구르기: 몸을 한 바퀴 굴리고 살짝 웅크린다. 칼은 그리지 않는다. */
    private void drawRoll(HeroActor h) {
        float p = 1f - h.rollT / h.rollTotal;
        TextureRegion r = art.get(h.facing == Facing.UP ? "hero_up" : h.facing == Facing.DOWN ? "hero_down" : "hero_side");
        float w = art.width(r) * S, hgt = art.height(r) * S;
        float dir = h.rollX < -0.1f ? 1f : -1f;
        float angle = dir * 360f * p;
        float squash = 0.8f + 0.2f * Math.abs(MathUtils.cos(p * MathUtils.PI));
        float hop = MathUtils.sin(p * MathUtils.PI) * 6f;
        boolean flip = h.facing == Facing.LEFT;
        batch.setColor(Color.WHITE);
        batch.draw(r, h.x - w / 2f, h.y - 2 + hop, w / 2f, hgt / 2f, w, hgt, flip ? -squash : squash, squash, angle);
        batch.setColor(Color.WHITE);
    }

    private void drawSword(HeroActor h, float alpha) {
        Weapon w = g.state.equipped();
        if (w == null) return;
        TextureRegion r = art.sword(w.defId, w.awakened);
        float aim = MathUtils.atan2(h.aimY, h.aimX) * MathUtils.radDeg;
        float angle;
        float scale = 1f;
        if (h.mineSwingT > 0) {
            float p = 1f - h.mineSwingT / 0.22f;
            angle = aim + 60 - 100 * Math.min(1f, p * 1.6f);
        } else if (h.swingT > 0 && h.swingKind == 0) {
            float p = 1f - h.swingT / 0.18f;
            float e = 1f - (1f - p) * (1f - p) * (1f - p); // 처음에 빠르고 끝에 느리게
            angle = aim + 75 - 150 * e;
            scale = 1.15f;
        } else {
            switch (h.facing) {
                case LEFT: angle = 125; break;
                case RIGHT: angle = 55; break;
                case UP: angle = 100; break;
                default: angle = 75;
            }
            angle += MathUtils.sin(time * 2.5f) * 3f;
        }
        float hx = h.x + (h.facing == Facing.LEFT ? -8 : 8), hy = h.y + 16;
        if (h.facing == Facing.UP) hx = h.x + 8;
        batch.setColor(1f, 1f, 1f, alpha);
        // 그림 속 칼날은 45도로 누워 있으므로 그만큼 빼고 돌린다. 손잡이(왼쪽 아래)를 회전 중심으로.
        batch.draw(r, hx - 6, hy - 6, 6, 6, 32, 32, scale, scale, angle - 45);
        if ("holy_sealed".equals(w.defId) && !art.hasHd("sword_holy") && h.swingT <= 0) {
            batch.setColor(1f, 1f, 1f, alpha * 0.8f);
            batch.draw(art.get("chain"), hx - 4, hy, 4, 4, 18, 18, 1f, 1f, angle - 45);
        }
    }

    private void drawMonster(Monster m) {
        TextureRegion r = art.get(m.def.sprite);
        float scale = S * (m.finalBoss ? 2.0f : m.boss ? 1.7f : 1f);
        float age = ages.containsKey(m) ? ages.get(m) : 1f;
        float appear = Math.min(1f, age / 0.35f);
        float w = art.width(r) * scale, hgt = art.height(r) * scale;
        drawShadow(m.x, m.y, w * 0.8f * appear);
        float bob = m.state == Monster.State.CHASE ? Math.abs(MathUtils.sin(m.walkTime * 9f)) * 3f : 0;
        if ("bat".equals(m.def.sprite)) bob = 6 + MathUtils.sin(time * 8f + m.x) * 4f;
        // 공격 예고: 뒤로 살짝 움츠렸다가 친다
        float sx = 1f, sy = 1f;
        if (m.state == Monster.State.TELEGRAPH) {
            float p = 1f - Math.max(0, m.stateT) / g.balance.monster.telegraphSeconds;
            sx = 1f + 0.12f * p;
            sy = 1f - 0.1f * p;
        } else if (m.state == Monster.State.SLAM_TELEGRAPH) {
            float p = 1f - Math.max(0, m.stateT) / g.balance.monster.bossSlamTelegraph;
            sx = 1f - 0.08f * p;
            sy = 1f + 0.12f * p;
            bob += 10 * p;
        }
        float dw = w * sx * appear, dh = hgt * sy * appear;
        boolean flip = m.dirX < -0.2f;
        if (m.state == Monster.State.TELEGRAPH) batch.setColor(1f, 0.8f, 0.7f, appear);
        else batch.setColor(1f, 1f, 1f, appear);
        if (flip) batch.draw(r, m.x + dw / 2f, m.y - 2 + bob, -dw, dh);
        else batch.draw(r, m.x - dw / 2f, m.y - 2 + bob, dw, dh);
        if (m.flash > 0) {
            // 맞은 순간 하얗게 번쩍인다
            Particles.additive(batch);
            batch.setColor(1f, 1f, 1f, Math.min(1f, m.flash / 0.15f));
            for (int i = 0; i < 2; i++) {
                if (flip) batch.draw(r, m.x + dw / 2f, m.y - 2 + bob, -dw, dh);
                else batch.draw(r, m.x - dw / 2f, m.y - 2 + bob, dw, dh);
            }
            Particles.normal(batch);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawCorpse(Corpse c) {
        float p = c.t / c.dur;
        float a = 1f - p;
        float sy = 1f - 0.5f * p, sx = 1f + 0.25f * p;
        float dw = c.w * sx, dh = c.h * sy;
        batch.setColor(1f, 0.85f, 0.85f, a);
        if (c.flip) batch.draw(c.region, c.x + dw / 2f, c.y - 2, -dw, dh);
        else batch.draw(c.region, c.x - dw / 2f, c.y - 2, dw, dh);
        Particles.additive(batch);
        batch.setColor(1f, 1f, 1f, Math.max(0f, 1f - p * (c.boss ? 1.2f : 2.5f)));
        if (c.flip) batch.draw(c.region, c.x + dw / 2f, c.y - 2, -dw, dh);
        else batch.draw(c.region, c.x - dw / 2f, c.y - 2, dw, dh);
        Particles.normal(batch);
        batch.setColor(Color.WHITE);
    }

    private void drawGhosts() {
        Particles.additive(batch);
        for (Ghost gh : ghosts) {
            float a = 0.25f * (1f - gh.t / 0.2f);
            batch.setColor(0.45f, 0.7f, 1f, a);
            if (gh.flip) batch.draw(gh.region, gh.x + gh.w / 2f, gh.y - 2, -gh.w, gh.h);
            else batch.draw(gh.region, gh.x - gh.w / 2f, gh.y - 2, gh.w, gh.h);
        }
        Particles.normal(batch);
        batch.setColor(Color.WHITE);
    }

    // ───────────────────────── 빛나는 효과 (더하기)

    private void drawGlow() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        batch.begin();
        Particles.additive(batch);
        // 횃불, 모닥불, 화로의 불빛
        for (float[] t : torches) {
            if (!inView(t[0], t[1])) continue;
            float fl = 0.8f + 0.2f * MathUtils.sin(time * 13f + t[2]);
            batch.setColor(1f, 0.55f, 0.2f, 0.45f * fl);
            batch.draw(art.glow, t[0] - 22, t[1] + 2, 44, 44);
        }
        for (Prop p : map.props) {
            float px = p.tileX * T, py = p.tileY * T;
            if (p.kind == Prop.Kind.CAMPFIRE) {
                float fl = 0.85f + 0.15f * MathUtils.sin(time * 10f);
                batch.setColor(1f, 0.55f, 0.2f, 0.55f * fl);
                batch.draw(art.glow, px + 16 - 48, py + 8 - 40, 96, 80);
            } else if (p.kind == Prop.Kind.FORGE) {
                float fl = 0.7f + 0.3f * MathUtils.sin(time * 7f);
                batch.setColor(1f, 0.5f, 0.15f, 0.6f * fl);
                batch.draw(art.glow, px + 6, py + 26, 28, 28);
            }
        }
        // 광맥 반짝임
        for (Room r : map.rooms) {
            if (r.type != RoomType.ORE) continue;
            List<Vein> veins = g.veinsOf(r);
            if (veins == null) continue;
            for (Vein v : veins) {
                float px = (v.tileX + 0.5f) * T, py = (v.tileY + 0.5f) * T;
                if (!inView(px, py)) continue;
                Color c = oreColor(v.ore);
                float tw = MathUtils.sin(time * 4f + v.tileX * 1.7f + v.tileY);
                batch.setColor(c.r, c.g, c.b, 0.25f + 0.15f * tw);
                batch.draw(art.glow, px - 26, py - 22, 52, 52);
                if (tw > 0.85f) {
                    batch.setColor(1f, 1f, 1f, (tw - 0.85f) * 5f);
                    batch.draw(art.white(), px - 1 + (v.tileX % 3) * 4 - 4, py + 8, 2, 6);
                    batch.draw(art.white(), px - 3 + (v.tileX % 3) * 4 - 4, py + 10, 6, 2);
                }
            }
        }
        // 귀환 포탈 빛기둥
        for (Room r : map.rooms) {
            if (!g.hasPortal(r)) continue;
            float px = g.portalX(r), py = g.portalY(r);
            if (!inView(px, py)) continue;
            float pulse = 0.8f + 0.2f * MathUtils.sin(time * 3f);
            batch.setColor(0.5f, 0.45f, 1f, 0.5f * pulse);
            batch.draw(art.glow, px - 40, py - 20, 80, 150);
            batch.setColor(0.8f, 0.75f, 1f, 0.6f * pulse);
            batch.draw(art.glow, px - 24, py - 14, 48, 28);
        }
        // 잠긴 던전 입구의 봉인 장벽
        for (Link l : map.links) {
            if (!l.gateClosed) continue;
            Color c = gateColor(l);
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float cx = d.tilesX[1] * T + T / 2f, cy = d.tilesY[1] * T + T / 2f;
                if (!inView(cx, cy)) continue;
                float pulse = 0.7f + 0.3f * MathUtils.sin(time * 3f);
                for (int i = 0; i < 3; i++) {
                    float x = d.tilesX[i] * T, y = d.tilesY[i] * T;
                    batch.setColor(c.r, c.g, c.b, 0.22f * pulse);
                    batch.draw(art.white(), x, y, T, T);
                    // 위로 흐르는 빛줄기
                    for (int k = 0; k < 3; k++) {
                        float off = ((time * 30f + k * 11 + i * 7) % T);
                        batch.setColor(c.r, c.g, c.b, 0.5f);
                        if (d.horizontal()) batch.draw(art.white(), x + 4 + k * 10, y + off, 2, 8);
                        else batch.draw(art.white(), x + off, y + 4 + k * 10, 8, 2);
                    }
                }
                batch.setColor(c.r, c.g, c.b, 0.9f * pulse);
                TextureRegion seal = art.get("seal");
                batch.draw(seal, cx - 24, cy - 24, 24, 24, 48, 48, 1f, 1f, time * 20f);
                batch.setColor(c.r, c.g, c.b, 0.35f * pulse);
                batch.draw(art.glow, cx - 60, cy - 60, 120, 120);
            }
        }
        // 마왕의 기운
        for (Monster m : g.monsters) {
            if (m.dead || !m.finalBoss) continue;
            batch.setColor(0.6f, 0.2f, 1f, 0.35f + 0.1f * MathUtils.sin(time * 5f));
            batch.draw(art.glow, m.x - 90, m.y - 20, 180, 160);
        }
        particles.drawGlow(batch);
        batch.setColor(Color.WHITE);
        batch.end();

        // 칼 궤적과 충격파 (SpriteBatch.end 가 섞기를 꺼 두므로 다시 켠다)
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawSwingTrail();
        for (Ring r : rings) {
            float p = r.t / r.dur;
            float e = 1f - (1f - p) * (1f - p);
            float rad = r.r0 + (r.r1 - r.r0) * e;
            drawRing(r.x, r.y, rad, Math.max(2f, 8f * (1f - p)), r.color, 0.45f * (1f - p));
        }
        shapes.end();
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Particles.normal(batch);
    }

    /** 바닥에 납작하게 깔리는 고리 (세로를 줄여 비스듬히 보이게). */
    private void drawRing(float cx, float cy, float rad, float thick, Color c, float a) {
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            float a0 = MathUtils.PI2 * i / seg, a1 = MathUtils.PI2 * (i + 1) / seg;
            float ox0 = MathUtils.cos(a0), oy0 = MathUtils.sin(a0) * 0.6f;
            float ox1 = MathUtils.cos(a1), oy1 = MathUtils.sin(a1) * 0.6f;
            float ri = rad - thick;
            shapes.setColor(c.r, c.g, c.b, a);
            shapes.triangle(cx + ox0 * rad, cy + oy0 * rad, cx + ox1 * rad, cy + oy1 * rad, cx + ox0 * ri, cy + oy0 * ri);
            shapes.triangle(cx + ox1 * rad, cy + oy1 * rad, cx + ox1 * ri, cy + oy1 * ri, cx + ox0 * ri, cy + oy0 * ri);
        }
    }

    /** 칼이 지나간 자리에 남는 초승달 모양 잔상. 칼끝 쪽이 진하고 뒤로 갈수록 옅다. */
    private void drawSwingTrail() {
        HeroActor h = g.hero;
        if (h.swingT <= 0) return;
        Weapon w = g.state.equipped();
        Color c = swingColor(w == null ? "" : w.defId);
        float cx = h.x, cy = h.centerY();
        float aim = MathUtils.atan2(h.aimY, h.aimX) * MathUtils.radDeg;
        float start, end, outer, inner, alpha;
        if (h.swingKind == 0) {
            float p = 1f - h.swingT / 0.18f;
            float e = 1f - (1f - p) * (1f - p) * (1f - p);
            float arc = g.balance.attack.basicArcDegrees;
            start = aim + arc / 2f;
            end = aim + arc / 2f - arc * e;
            outer = g.basicRange();
            inner = outer * 0.5f;
            alpha = 0.85f * Math.min(1f, h.swingT / 0.08f);
        } else {
            return;
        }
        int seg = 22;
        for (int i = 0; i < seg; i++) {
            float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
            float a0 = (start + (end - start) * t0) * MathUtils.degRad, a1 = (start + (end - start) * t1) * MathUtils.degRad;
            float al0 = alpha * t0 * t0, al1 = alpha * t1 * t1;
            float o0x = cx + MathUtils.cos(a0) * outer, o0y = cy + MathUtils.sin(a0) * outer;
            float o1x = cx + MathUtils.cos(a1) * outer, o1y = cy + MathUtils.sin(a1) * outer;
            float i0x = cx + MathUtils.cos(a0) * inner, i0y = cy + MathUtils.sin(a0) * inner;
            float i1x = cx + MathUtils.cos(a1) * inner, i1y = cy + MathUtils.sin(a1) * inner;
            Color co0 = new Color(c.r, c.g, c.b, al0), co1 = new Color(c.r, c.g, c.b, al1);
            Color ci0 = new Color(c.r, c.g, c.b, 0f), ci1 = new Color(c.r, c.g, c.b, 0f);
            shapes.triangle(o0x, o0y, o1x, o1y, i0x, i0y, co0, co1, ci0);
            shapes.triangle(o1x, o1y, i1x, i1y, i0x, i0y, co1, ci1, ci0);
            // 칼끝을 따라 밝은 선
            Color w0 = new Color(1f, 1f, 1f, al0 * 0.9f), w1 = new Color(1f, 1f, 1f, al1 * 0.9f);
            float m0x = cx + MathUtils.cos(a0) * (outer - 5), m0y = cy + MathUtils.sin(a0) * (outer - 5);
            float m1x = cx + MathUtils.cos(a1) * (outer - 5), m1y = cy + MathUtils.sin(a1) * (outer - 5);
            shapes.triangle(o0x, o0y, o1x, o1y, m0x, m0y, w0, w1, w0);
            shapes.triangle(o1x, o1y, m1x, m1y, m0x, m0y, w1, w1, w0);
        }
    }

    // ───────────────────────── 체력 막대

    private void drawOverlays() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
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

    // ───────────────────────── 글자 (화면 좌표로 그려 글자가 흐려지지 않게 한다)

    private float sx(OrthographicCamera cam, float wx) {
        return (wx - viewX0) / cam.zoom;
    }

    private float sy(OrthographicCamera cam, float wy) {
        return (wy - viewY0) / cam.zoom;
    }

    private void drawTexts(OrthographicCamera cam) {
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.state == Monster.State.TELEGRAPH || m.state == Monster.State.SLAM_TELEGRAPH) {
                float top = m.y + m.hurtRadius * 2.4f + 18;
                float pop = 1f + 0.15f * MathUtils.sin(time * 30f);
                layout.setText(fonts.large, "!");
                outlined(fonts.large, "!", sx(cam, m.x) - layout.width / 2f, sy(cam, top) + layout.height * pop, Ui.RED, 1f);
            }
        }
        for (FloatText ft : floats) {
            BitmapFont font = ft.big ? fonts.large : fonts.normal;
            float a = 1f - Math.max(0, ft.t - 0.6f) / 0.3f;
            layout.setText(font, ft.text);
            outlined(font, ft.text, sx(cam, ft.x) - layout.width / 2f, sy(cam, ft.y) + layout.height, ft.color, a);
        }
        if (g.prompt != null) {
            layout.setText(fonts.normal, g.prompt);
            float w = layout.width + 24, hgt = layout.height + 16;
            float x = sx(cam, g.promptX) - w / 2f, y = sy(cam, g.promptY);
            batch.setColor(0.05f, 0.04f, 0.07f, 0.8f);
            batch.draw(art.white(), x, y, w, hgt);
            batch.setColor(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, 0.8f);
            batch.draw(art.white(), x, y, w, 2);
            batch.setColor(Color.WHITE);
            fonts.normal.setColor(Ui.GOLD);
            fonts.normal.draw(batch, g.prompt, x + 12, y + hgt - 8);
        }
        fonts.normal.setColor(Color.WHITE);
        fonts.large.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
    }

    private void outlined(BitmapFont font, String text, float x, float y, Color c, float a) {
        font.setColor(0, 0, 0, 0.85f * a);
        font.draw(batch, text, x - 2, y);
        font.draw(batch, text, x + 2, y);
        font.draw(batch, text, x, y - 2);
        font.draw(batch, text, x, y + 2);
        font.setColor(c.r, c.g, c.b, a);
        font.draw(batch, text, x, y);
    }

    // ───────────────────────── 색

    private Color swingColor(String defId) {
        switch (defId) {
            case "blackiron": return new Color(0.75f, 0.85f, 1f, 1f);
            case "bloodmoon": return new Color(1f, 0.35f, 0.45f, 1f);
            case "skyfall": return new Color(0.45f, 0.85f, 1f, 1f);
            case "holy_awakened": return new Color(1f, 0.92f, 0.5f, 1f);
            default: return new Color(0.95f, 0.95f, 1f, 1f);
        }
    }

    private static Color oreColor(OreType t) {
        if (t == null) return new Color(1f, 1f, 1f, 1f);
        switch (t) {
            case BLACKIRON: return new Color(0.62f, 0.72f, 0.95f, 1f);
            case BLOODMOON: return new Color(1f, 0.3f, 0.4f, 1f);
            default: return new Color(0.45f, 0.85f, 1f, 1f);
        }
    }

    private static Color gateColor(Link l) {
        return "FINAL".equals(l.gate) ? new Color(1f, 0.8f, 0.3f, 1f) : new Color(0.7f, 0.5f, 1f, 1f);
    }

    private static Color deathColor(String sprite) {
        switch (sprite) {
            case "bat": return new Color(0.75f, 0.5f, 1f, 1f);
            case "skeleton": case "knight": return new Color(1f, 0.4f, 0.4f, 1f);
            case "imp": case "warden": return new Color(0.6f, 0.5f, 1f, 1f);
            case "golem": return new Color(1f, 0.7f, 0.4f, 1f);
            case "demonlord": return new Color(0.9f, 0.4f, 1f, 1f);
            default: return new Color(1f, 0.8f, 0.6f, 1f);
        }
    }

    @Override
    public void dispose() {
        lighting.dispose();
    }
}
