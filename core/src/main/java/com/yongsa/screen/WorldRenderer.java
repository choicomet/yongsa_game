package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
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
import com.yongsa.combat.Hazard;
import com.yongsa.combat.HeroActor;
import com.yongsa.combat.HeroShot;
import com.yongsa.combat.Monster;
import com.yongsa.combat.Projectile;
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
import java.util.HashMap;
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
    // Presentation only: keep feet/collision, attack ranges and camera in world coordinates.
    private static final float HERO_SCALE = 1.25f;
    private static final float MOB_SCALE = 1.25f;

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
    /** 던전 방 배경 그림 (보이는 방만 읽어 두고, 오래 안 쓴 것은 버린다). */
    private final Map<String, Texture> sceneTex = new HashMap<>();
    private final Map<String, Float> sceneUsed = new HashMap<>();
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

    /** 보스 말풍선. */
    private static class Speech {
        float x, y, t;
        String text;
    }

    private final List<Speech> speeches = new ArrayList<>();
    /** 천공 낙뢰가 떨어진 자리 (x, y, 지난 시간): 하늘에서 내리꽂히는 빛줄기를 잠깐 그린다. */
    private final List<float[]> bolts = new ArrayList<>();

    /** 검 스킬 색: 흑철 흙빛, 혈월 핏빛, 천공 하늘빛, 성검 금빛. */
    private static Color skillColor(String kind) {
        if (kind == null) return Color.WHITE;
        switch (kind) {
            case "cleave": return new Color(1f, 0.62f, 0.3f, 1f);
            case "crescent": return new Color(1f, 0.18f, 0.3f, 1f);
            case "strike": return new Color(0.55f, 0.85f, 1f, 1f);
            default: return new Color(1f, 0.93f, 0.6f, 1f);
        }
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
        // 타일 방(캠프) 윗벽에 일정한 간격으로 횃불을 건다. 문 바로 옆은 비운다. 그림 방은 그림에 불이 있다.
        for (Room r : map.rooms) {
            if (r.scene != null) continue;
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
            case SKILL_CAST: {
                Color c = skillColor(e.text);
                flash(e.x, e.y, 140, c, 0.25f);
                if ("cleave".equals(e.text)) {
                    ring(e.x + e.dirX * 20, e.y - 10 + e.dirY * 20, 10, 70, 0.35f, c);
                    particles.debris(e.x + e.dirX * 24, e.y - 8, 12, new Color(0.45f, 0.38f, 0.3f, 1f), 160, 4);
                } else if ("beam".equals(e.text)) {
                    ring(e.x, e.y - 10, 10, 90, 0.4f, c);
                }
                particles.spray(e.x, e.y, e.dirX, e.dirY, 14, c, 300, 0.8f, 0.3f, 3);
                break;
            }
            case SKILL_HIT: {
                Color c = skillColor(e.text);
                if ("strike".equals(e.text)) {
                    flash(e.x, e.y, 220, c, 0.35f);
                    ring(e.x, e.y, 6, e.size * 1.3f, 0.4f, c);
                    particles.burst(e.x, e.y + 10, 26, c, 300, 0.6f, 3, true);
                    bolts.add(new float[]{e.x, e.y, 0f});
                } else {
                    particles.burst(e.x, e.y, 12, c, 220, 0.4f, 3, true);
                    flash(e.x, e.y, 90, c, 0.15f);
                }
                break;
            }
            case CHEST_OPENED: {
                Color gold = new Color(1f, 0.85f, 0.4f, 1f);
                particles.burst(e.x, e.y - T / 2f, 40, gold, 260, 0.8f, 3, true);
                flash(e.x, e.y - T / 2f, 160, gold, 0.4f);
                break;
            }
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
                c.region = art.world(e.sprite);
                float scale = S * e.size * (e.boss ? 1f : MOB_SCALE);
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
            case BLINK_FX:
                particles.burst(e.x, e.y, 18, new Color(0.75f, 0.4f, 1f, 1f), 180, 0.45f, 3, true);
                particles.smoke(e.x, e.y - 8, 5, new Color(0.3f, 0.15f, 0.4f, 0.6f), 14, 12, 0.5f);
                flash(e.x, e.y, 90, new Color(0.7f, 0.35f, 1f, 1f), 0.2f);
                break;
            case SHIELD_UP:
                ring(e.x, e.y - 10, e.size * 1.4f, e.size * 0.6f, 0.5f, SHIELD_COLOR);
                particles.burst(e.x, e.y, 24, SHIELD_COLOR, 160, 0.6f, 3, true);
                flash(e.x, e.y, e.size * 2.5f, SHIELD_COLOR, 0.4f);
                break;
            case SHIELD_BREAK:
                ring(e.x, e.y - 10, e.size * 0.5f, e.size * 2.2f, 0.6f, SHIELD_COLOR);
                particles.debris(e.x, e.y, 26, SHIELD_COLOR, 300, 5);
                particles.burst(e.x, e.y, 30, Color.WHITE, 260, 0.5f, 3, true);
                flash(e.x, e.y, e.size * 3f, SHIELD_COLOR, 0.5f);
                break;
            case HAZARD_FX:
                hazardFx(e);
                break;
            case BOSS_PHASE2:
                ring(e.x, e.y - 20, 20, 420, 1.2f, PROJ_DARK);
                ring(e.x, e.y - 20, 10, 260, 0.9f, new Color(1f, 0.3f, 0.6f, 1f));
                particles.burst(e.x, e.y, 70, PROJ_DARK, 380, 0.9f, 3, true);
                particles.smoke(e.x, e.y, 30, new Color(0.25f, 0.08f, 0.35f, 0.75f), 80, 28, 1.6f);
                flash(e.x, e.y, 520, PROJ_DARK, 1.0f);
                break;
            case BOSS_SPEECH: {
                Speech sp = new Speech();
                sp.x = e.x;
                sp.y = e.y;
                sp.text = e.text;
                speeches.clear();
                speeches.add(sp);
                break;
            }
            case BOSS_ENRAGED:
                ring(e.x, e.y - 20, 20, 300, 0.8f, new Color(1f, 0.25f, 0.15f, 1f));
                particles.burst(e.x, e.y, 40, new Color(1f, 0.3f, 0.2f, 1f), 320, 0.7f, 3, true);
                flash(e.x, e.y, 360, new Color(1f, 0.2f, 0.1f, 1f), 0.6f);
                break;
            case WARP:
                particles.burst(g.hero.x, g.hero.centerY(), 40, new Color(0.6f, 0.5f, 1f, 1f), 260, 0.8f, 3, true);
                flash(g.hero.x, g.hero.y, 260, new Color(0.6f, 0.5f, 1f, 1f), 0.6f);
                break;
            case HERO_DIED:
                speeches.clear();
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
            case SHIELD: f.color = SHIELD_COLOR; f.big = true; break;
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
        for (float[] b : bolts) b[2] += dt;
        bolts.removeIf(b -> b[2] > 0.3f);
        time += dt;
        for (Iterator<FloatText> it = floats.iterator(); it.hasNext(); ) {
            FloatText f = it.next();
            f.t += dt;
            f.y += (f.t < 0.15f ? 90 : 26) * dt;
            if (f.t > 0.9f) it.remove();
        }
        for (Iterator<Speech> it = speeches.iterator(); it.hasNext(); ) {
            Speech sp = it.next();
            sp.t += dt;
            if (sp.t > 3.6f) it.remove();
        }
        // 피웅덩이에서 피어오르는 거품
        for (Hazard h : g.hazards) {
            if (h.kind == Hazard.Kind.POOL && h.triggered && MathUtils.random() < dt * 6f) {
                float a = MathUtils.random(MathUtils.PI2), r = MathUtils.random(h.radius * 0.8f);
                particles.burst(h.x + MathUtils.cos(a) * r, h.y + MathUtils.sin(a) * r * 0.6f, 2, PROJ_BLOOD, 40, 0.5f, 2, false);
            }
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
                gh.w = art.width(gh.region) * S * HERO_SCALE;
                gh.h = art.height(gh.region) * S * HERO_SCALE;
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
            if (m.state == Monster.State.SPAWN) {
                // 마법진 위로 빛 알갱이가 모인다
                if (MathUtils.random() < 0.5f) {
                    Color c = spawnColor(m);
                    Particles.P p = particles.spawn(m.x + MathUtils.random(-m.hurtRadius, m.hurtRadius), m.y + MathUtils.random(-4f, 4f), 0.5f, 2, c);
                    p.vz = 60f;
                    p.glow = true;
                }
                continue;
            }
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
                float cx = pr.centerX(T), cy = pr.centerY(T) + (pr.baked ? 20 : -4);
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
        if (r != null && r.scene != null) {
            // 그림에 이미 빛과 그늘이 그려져 있어서 조금만 어둡게 한다
            if (r.type == RoomType.FINAL) return new Color(0.74f, 0.68f, 0.82f, 1f);
            switch (th) {
                case "CAMP": return new Color(0.84f, 0.81f, 0.78f, 1f);
                case "LEFT": return new Color(0.8f, 0.8f, 0.84f, 1f);
                case "RIGHT": return new Color(0.84f, 0.76f, 0.78f, 1f);
                default: return new Color(0.78f, 0.76f, 0.88f, 1f);
            }
        }
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
            float cx = p.centerX(T), cy = p.centerY(T);
            if (p.kind == Prop.Kind.CAMPFIRE) {
                float fl = 1f + 0.06f * MathUtils.sin(time * 9f) + 0.04f * MathUtils.sin(time * 17f);
                lighting.add(cx, cy, 360 * fl, 1f, 0.62f, 0.3f, 0.95f);
            } else if (p.kind == Prop.Kind.FORGE) {
                lighting.add(cx - 8, cy + 10, 150, 1f, 0.5f, 0.2f, 0.7f + 0.15f * MathUtils.sin(time * 7f));
            } else if (p.kind == Prop.Kind.SEAL) {
                Color c = sealColor();
                lighting.add(cx, cy, 150 + 60 * sealPower(), c.r, c.g, c.b, 0.35f + 0.4f * sealPower() + 0.08f * MathUtils.sin(time * 3f));
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
        for (Hazard hz : g.hazards) {
            if (!inView(hz.x, hz.y)) continue;
            Color c = hazardColor(hz.style);
            if (hz.kind == Hazard.Kind.POOL) lighting.add(hz.x, hz.y, hz.radius * 1.6f, c.r, c.g, c.b, 0.35f);
            else lighting.add(hz.x, hz.y, 60 + 60 * hz.progress(), c.r, c.g, c.b, 0.25f + 0.3f * hz.progress());
        }
        for (Link l : map.links) {
            for (Door d : new Door[]{l.doorA, l.doorB}) {
                float cx = d.footX, cy = d.footY;
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
            if (m.enraged) lighting.add(m.x, m.y + 30, 200, 1f, 0.2f, 0.15f, 0.35f + 0.15f * MathUtils.sin(time * 8f));
            if (m.state == Monster.State.SPAWN) {
                Color c = spawnColor(m);
                lighting.add(m.x, m.y, 110, c.r, c.g, c.b, 0.6f * m.progress());
            }
        }
        for (Projectile p : g.projectiles) {
            Color c = projectileColor(p.source);
            lighting.add(p.x, p.y, 90, c.r, c.g, c.b, 0.7f);
        }
        for (HeroShot hs : g.shots) {
            Color c = skillColor(hs.kind);
            lighting.add(hs.x, hs.y, hs.strike() ? 70 : 130, c.r, c.g, c.b, 0.8f);
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
        drawScenes();
        drawTiles(tx0, ty0, tx1, ty1);
        drawSceneDoors();
        drawFloorProps();
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
                Room tr = map.roomAt(x, y);
                if (tr != null && tr.scene != null) continue; // 배경 그림이 바닥과 벽이다
                String th = themeAt(x, y);
                float px = x * T, py = y * T;
                if (t == WorldMap.WALL) {
                    int below = map.tile(x, y - 1);
                    boolean front = below == WorldMap.FLOOR || below == WorldMap.DOOR;
                    batch.draw(art.world(front ? "wallfront_" + th : "walltop_" + th), px, py, T, T);
                    continue;
                }
                batch.draw(art.floor(th, x, y), px, py, T, T);
                if (t == WorldMap.FLOOR) {
                    TextureRegion d = art.decal(th, x, y);
                    if (d != null) batch.draw(d, px, py, T, T);
                }
                // 벽 옆 그늘
                if (map.tile(x, y + 1) == WorldMap.WALL) batch.draw(art.world("ao_top"), px, py, T, T);
                if (map.tile(x - 1, y) == WorldMap.WALL) batch.draw(art.world("ao_left"), px, py, T, T);
                if (map.tile(x + 1, y) == WorldMap.WALL) batch.draw(art.world("ao_right"), px, py, T, T);
                if (t == WorldMap.DOOR) {
                    Door d = map.door(x, y);
                    if (d.link != null && d.link.gateClosed) {
                        batch.setColor(0.05f, 0.03f, 0.08f, 0.65f);
                        batch.draw(art.white(), px, py, T, T);
                        batch.setColor(Color.WHITE);
                    } else if (d.lockedByFight) {
                        batch.draw(art.world("bars"), px, py, T, T);
                    }
                }
            }
        }
    }

    // ───────────────────────── 던전 방 배경 그림

    private void drawScenes() {
        for (Room r : map.rooms) {
            if (r.scene == null) continue;
            if (r.originX > viewX1 || r.originX + r.widthPx < viewX0 || r.originY > viewY1 || r.originY + r.heightPx < viewY0) continue;
            Texture tex = sceneTexture(r);
            batch.draw(tex, r.originX, r.originY, r.widthPx, r.heightPx, 0, 0, tex.getWidth(), tex.getHeight(), r.flip, false);
        }
        // 오래 안 보인 그림은 메모리에서 내린다
        if (sceneTex.size() > 5) {
            for (Iterator<Map.Entry<String, Texture>> it = sceneTex.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Texture> en = it.next();
                if (time - sceneUsed.getOrDefault(en.getKey(), 0f) < 20f) continue;
                en.getValue().dispose();
                sceneUsed.remove(en.getKey());
                it.remove();
            }
        }
    }

    private Texture sceneTexture(Room r) {
        String key = r.scene.image;
        sceneUsed.put(key, time);
        Texture t = sceneTex.get(key);
        if (t == null) {
            t = new Texture(Gdx.files.internal(key), true);
            t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            sceneTex.put(key, t);
        }
        return t;
    }

    /** 그림 방의 출구: 잠기면 창살, 이어진 곳이 없으면 어둠으로 막혀 있다. */
    private void drawSceneDoors() {
        for (Room r : map.rooms) {
            if (r.scene == null) continue;
            for (Door d : r.doors) {
                if (!inView(d.footX, d.footY)) continue;
                float px = -d.dirY, py = d.dirX;
                if (d.dead()) {
                    float size = d.halfWidth * 2f + 2.5f * T;
                    for (int k = 0; k < 3; k++) {
                        float cx = d.footX + d.dirX * T * (0.6f + k * 0.9f), cy = d.footY + d.dirY * T * (0.6f + k * 0.9f);
                        batch.setColor(0.01f, 0.01f, 0.02f, 0.8f);
                        batch.draw(art.glow, cx - size / 2f, cy - size / 2f, size, size);
                    }
                    batch.setColor(Color.WHITE);
                } else if (d.lockedByFight || (d.link != null && d.link.gateClosed)) {
                    int n = Math.max(2, Math.round(d.halfWidth * 2f / T));
                    for (int i = 0; i < n; i++) {
                        float o = -d.halfWidth + (i + 0.5f) * (d.halfWidth * 2f / n);
                        float cx = d.footX + px * o, cy = d.footY + py * o;
                        batch.draw(art.world("bars"), cx - T / 2f, cy - T / 2f, T, T);
                    }
                }
            }
        }
    }

    /** 캠프 바닥의 봉인 문양: 힘이 모일수록 밝아지고, 봉인이 풀리면 갈라진 채 보랏빛으로 빛난다. */
    private void drawFloorProps() {
        for (Prop p : map.props) {
            if (p.kind != Prop.Kind.SEAL) continue;
            float cx = p.centerX(T), cy = p.centerY(T);
            if (!inView(cx, cy)) continue;
            Color c = sealColor();
            float pw = sealPower();
            float size = p.width(T) * 1.5f;
            batch.setColor(0.05f, 0.03f, 0.07f, 0.55f);
            batch.draw(art.glow, cx - size * 0.6f, cy - size * 0.35f, size * 1.2f, size * 0.7f);
            batch.setColor(c.r, c.g, c.b, 0.45f + 0.5f * pw);
            batch.draw(art.world("seal"), cx - size / 2f, cy - size * 0.32f, size / 2f, size * 0.32f, size, size * 0.64f, 1f, 1f,
                    g.state.unsealed ? time * 12f : 0f);
            batch.setColor(Color.WHITE);
        }
    }

    /** 문양의 밝기 0~1: 흡수한 보스 수, 봉인이 풀리면 1. */
    private float sealPower() {
        if (g.state.unsealed) return 1f;
        int n = 0;
        for (String id : com.yongsa.model.AbsorbService.BOSS_ORDER) if (g.state.bossDefeated(id)) n++;
        return n / 3f;
    }

    private Color sealColor() {
        if (g.state.unsealed) return PROJ_DARK;
        return g.absorb.canUnseal(g.state) ? Ui.GOLD : new Color(0.55f, 0.45f, 0.8f, 1f);
    }

    private void drawTorches() {
        for (float[] t : torches) {
            if (!inView(t[0], t[1])) continue;
            boolean alt = ((int) (time * 8 + t[2])) % 2 == 0;
            batch.draw(art.world(alt ? "torch_a" : "torch_b"), t[0] - 8, t[1], 16, 32);
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
        drawHazards();
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            float prog = m.progress();
            float blink = 0.16f + 0.08f * MathUtils.sin(time * 20f);
            switch (m.state) {
                case SLAM_TELEGRAPH:
                    shapes.setColor(0.9f, 0.1f, 0.05f, blink);
                    shapes.circle(m.slamX, m.slamY, m.slamRadius, 56);
                    shapes.setColor(1f, 0.2f, 0.1f, 0.3f);
                    shapes.circle(m.slamX, m.slamY, m.slamRadius * prog, 56);
                    break;
                case CHARGE_TELEGRAPH:
                case SWOOP_TELEGRAPH: {
                    // 돌진할 길을 바닥에 미리 보여 준다
                    boolean charge = m.state == Monster.State.CHARGE_TELEGRAPH;
                    float len = charge ? g.balance.monster.chargeMaxTiles * T : g.balance.monster.swoopDistanceTiles * T;
                    float wid = m.hurtRadius * 1.7f;
                    float ex = m.x + m.lockX * len, ey = m.y + m.lockY * len;
                    shapes.setColor(0.9f, 0.1f, 0.05f, blink);
                    shapes.rectLine(m.x, m.y, ex, ey, wid);
                    shapes.setColor(1f, 0.25f, 0.1f, 0.32f);
                    shapes.rectLine(m.x, m.y, m.x + m.lockX * len * prog, m.y + m.lockY * len * prog, wid);
                    break;
                }
                case TELEGRAPH:
                case COMBO_TELEGRAPH: {
                    // 휘두를 앞쪽 부채꼴
                    float reach = g.balance.monster.attackReachTiles * T + m.hw + 10
                            + (m.state == Monster.State.COMBO_TELEGRAPH ? g.balance.monster.comboStepTiles * T + 0.6f * T : 0);
                    if ("brute".equals(m.ai())) reach += g.balance.monster.lungeTiles * T;
                    float ang = MathUtils.atan2(m.dirY, m.dirX) * MathUtils.radDeg;
                    shapes.setColor(0.9f, 0.1f, 0.05f, blink * 0.9f);
                    shapes.arc(m.x, m.y, reach, ang - 78, 156, 24);
                    shapes.setColor(1f, 0.25f, 0.1f, 0.25f);
                    shapes.arc(m.x, m.y, reach * prog, ang - 78, 156, 24);
                    break;
                }
                case SPAWN: {
                    Color c = spawnColor(m);
                    float r = m.hurtRadius * 1.6f;
                    shapes.setColor(c.r * 0.3f, c.g * 0.3f, c.b * 0.3f, 0.5f * Math.min(1f, prog * 3f));
                    shapes.ellipse(m.x - r, m.y - r * 0.5f, r * 2, r, 32);
                    break;
                }
                default:
                    break;
            }
        }
        shapes.end();
    }

    /** 바닥 공격 예고: 원(낙석·낙뢰·촉수)과 선(창)은 차오르는 예고, 피웅덩이는 넓어지는 웅덩이. */
    private void drawHazards() {
        float blink = 0.16f + 0.08f * MathUtils.sin(time * 20f);
        for (Hazard h : g.hazards) {
            if (!inView(h.x, h.y) && h.kind != Hazard.Kind.LINE) continue;
            Color c = hazardColor(h.style);
            float p = h.progress();
            switch (h.kind) {
                case POOL: {
                    float r = h.radius;
                    float fade = Math.min(1f, h.life / 0.8f) * Math.min(1f, h.age / 0.35f);
                    shapes.setColor(0.25f, 0.02f, 0.05f, 0.75f * fade);
                    shapes.ellipse(h.x - r, h.y - r * 0.6f, r * 2, r * 1.2f, 40);
                    shapes.setColor(0.6f, 0.05f, 0.1f, (0.45f + 0.1f * MathUtils.sin(time * 4f + h.x)) * fade);
                    shapes.ellipse(h.x - r * 0.8f, h.y - r * 0.48f, r * 1.6f, r * 0.96f, 40);
                    break;
                }
                case LINE: {
                    float w = h.radius * 2f;
                    shapes.setColor(c.r * 0.9f, c.g * 0.3f, c.b * 0.3f, blink + 0.05f);
                    shapes.rectLine(h.x, h.y, h.x2, h.y2, w);
                    shapes.setColor(c.r, c.g * 0.5f, c.b * 0.5f, 0.3f);
                    shapes.rectLine(h.x, h.y, h.x + (h.x2 - h.x) * p, h.y + (h.y2 - h.y) * p, w);
                    break;
                }
                default: {
                    float r = h.radius;
                    shapes.setColor(c.r * 0.9f, c.g * 0.35f, c.b * 0.35f, blink);
                    shapes.ellipse(h.x - r, h.y - r * 0.6f, r * 2, r * 1.2f, 40);
                    shapes.setColor(c.r, c.g * 0.6f, c.b * 0.6f, 0.32f);
                    shapes.ellipse(h.x - r * p, h.y - r * 0.6f * p, r * 2 * p, r * 1.2f * p, 40);
                }
            }
        }
    }

    private void hazardFx(GameEvent e) {
        String style = e.text == null ? "rock" : e.text;
        Color c = hazardColor(style);
        switch (style) {
            case "bolt":
                // 하늘에서 내리꽂는 번개 줄기
                for (int i = 0; i < 6; i++) {
                    float y = e.y + i * 60;
                    particles.burst(e.x + MathUtils.random(-6f, 6f), y, 3, c, 60, 0.25f, 2, true);
                }
                particles.burst(e.x, e.y, 22, Color.WHITE, 260, 0.35f, 3, true);
                ring(e.x, e.y, e.size * 0.3f, e.size * 1.1f, 0.35f, c);
                flash(e.x, e.y + 60, e.size * 4f, c, 0.25f);
                break;
            case "tentacle":
                particles.debris(e.x, e.y, 14, new Color(0.35f, 0.12f, 0.4f, 1f), 240, 5);
                particles.burst(e.x, e.y + 10, 18, c, 220, 0.5f, 3, true);
                ring(e.x, e.y, e.size * 0.3f, e.size, 0.4f, c);
                flash(e.x, e.y, e.size * 2.5f, c, 0.3f);
                break;
            case "spear": {
                float dx = e.dirX - e.x, dy = e.dirY - e.y;
                for (int i = 0; i <= 10; i++) {
                    float px = e.x + dx * i / 10f, py = e.y + dy * i / 10f;
                    if (!inView(px, py)) continue;
                    particles.burst(px, py, 3, c, 140, 0.35f, 3, true);
                }
                break;
            }
            default:
                particles.debris(e.x, e.y + 30, 16, new Color(0.45f, 0.4f, 0.38f, 1f), 220, 5);
                particles.smoke(e.x, e.y, 10, new Color(0.4f, 0.36f, 0.34f, 0.55f), e.size * 0.8f, 16, 0.7f);
                ring(e.x, e.y, e.size * 0.3f, e.size * 1.1f, 0.4f, c);
                flash(e.x, e.y, e.size * 2f, c, 0.25f);
        }
    }

    private static Color hazardColor(String style) {
        switch (style) {
            case "bolt": return PROJ_SKY;
            case "tentacle": return PROJ_DARK;
            case "spear": case "blood": return PROJ_BLOOD;
            default: return new Color(1f, 0.6f, 0.35f, 1f);
        }
    }

    // ───────────────────────── 깊이 정렬되는 것들

    private void collect(List<Sorted> list, int tx0, int ty0, int tx1, int ty1) {
        for (Prop p : map.props) {
            if (p.walkable() || p.baked) continue; // 바닥 문양은 타일과 함께 먼저 그린다
            list.add(new Sorted(p.y(T) + 2, () -> drawProp(p)));
        }
        for (Room r : map.rooms) {
            if (r.type != RoomType.ORE) continue;
            if (r.x1 < tx0 || r.x0 > tx1 || r.y1 < ty0 || r.y0 > ty1) continue;
            List<Vein> veins = g.veinsOf(r);
            if (veins == null) continue;
            for (Vein v : veins) list.add(new Sorted(v.tileY * T + 4, () -> drawVein(v)));
        }
        for (Room r : map.rooms) {
            if (!g.hasChest(r) || g.fighting()) continue;
            float cx = g.chestX(r), cy = g.chestY(r);
            list.add(new Sorted(cy, () -> drawChest(r, cx, cy)));
        }
        HeroActor h = g.hero;
        list.add(new Sorted(h.y, this::drawHero));
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            list.add(new Sorted(m.y, () -> drawMonster(m)));
        }
        for (Corpse c : corpses) list.add(new Sorted(c.y, () -> drawCorpse(c)));
    }

    /** 보스 보물상자 (선우 버전 그림 team/sunwoo/chest_*.png 의 닫힌 칸). 살짝 반짝인다. */
    private void drawChest(Room r, float x, float y) {
        TextureRegion t = art.frame("team/sunwoo/chest_" + r.dungeonId.toLowerCase() + ".png", 3, 0);
        if (t == null) return;
        float w = t.getRegionWidth(), h = t.getRegionHeight();
        drawShadow(x, y, w * 0.9f);
        float bob = MathUtils.sin(time * 3f) * 1.2f;
        batch.setColor(Color.WHITE);
        batch.draw(t, x - w / 2f, y - 4 + bob, w, h);
        Particles.additive(batch);
        batch.setColor(1f, 0.9f, 0.5f, 0.18f + 0.12f * MathUtils.sin(time * 4f));
        batch.draw(t, x - w / 2f, y - 4 + bob, w, h);
        Particles.normal(batch);
        batch.setColor(Color.WHITE);
    }

    private void drawShadow(float x, float y, float w) {
        batch.setColor(Color.WHITE);
        batch.draw(art.world("shadow"), x - w / 2f, y - w * 0.18f, w, w * 0.38f);
    }

    private void drawProp(Prop p) {
        batch.setColor(Color.WHITE);
        float px = p.x(T), py = p.y(T);
        switch (p.kind) {
            case FORGE: {
                TextureRegion r = art.world("forge");
                drawShadow(px + 28, py + 2, 64);
                batch.draw(r, px - 8, py - 6, art.width(r) * S, art.height(r) * S);
                break;
            }
            case BED: {
                TextureRegion r = art.world("bed");
                batch.draw(r, px - 4, py - 22, art.width(r) * S, art.height(r) * S);
                break;
            }
            default: {
                TextureRegion r = art.world(((int) (time * 6)) % 2 == 0 ? "campfire" : "campfire2");
                batch.draw(r, px, py - 4, art.width(r) * S, art.height(r) * S);
            }
        }
    }

    private void drawVein(Vein v) {
        TextureRegion r = art.world("vein_" + v.ore.name());
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
        return art.world(walk ? name + "_walk" : name);
    }

    private void drawHero() {
        HeroActor h = g.hero;
        drawShadow(h.x, h.y, 26 * HERO_SCALE);
        if (h.rolling()) {
            drawRoll(h);
            return;
        }
        boolean blink = h.invuln > 0 && ((int) (h.invuln * 20)) % 2 == 0;
        float alpha = blink ? 0.35f : 1f;
        boolean flip = h.facing == Facing.LEFT;
        TextureRegion r = heroRegion(h);
        float w = art.width(r) * S * HERO_SCALE, hgt = art.height(r) * S * HERO_SCALE;
        boolean swordBehind = h.facing == Facing.UP;
        if (swordBehind) drawSword(h, alpha);
        batch.setColor(1f, 1f, 1f, alpha);
        // 휘두를 때 몸을 살짝 찌그러뜨려 힘이 들어간 느낌을 준다
        float squash = h.swingT > 0 && h.swingKind == 0 ? 1f - 0.08f * (h.swingT / 0.18f) : 1f;
        float bob = heroBob(h);
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
        TextureRegion r = art.world(h.facing == Facing.UP ? "hero_up" : h.facing == Facing.DOWN ? "hero_down" : "hero_side");
        float w = art.width(r) * S * HERO_SCALE, hgt = art.height(r) * S * HERO_SCALE;
        float dir = h.rollX < -0.1f ? 1f : -1f;
        float angle = dir * 360f * p;
        float squash = 0.8f + 0.2f * Math.abs(MathUtils.cos(p * MathUtils.PI));
        float hop = MathUtils.sin(p * MathUtils.PI) * 6f * HERO_SCALE;
        boolean flip = h.facing == Facing.LEFT;
        batch.setColor(Color.WHITE);
        batch.draw(r, h.x - w / 2f, h.y - 2 + hop, w / 2f, hgt / 2f, w, hgt, flip ? -squash : squash, squash, angle);
        batch.setColor(Color.WHITE);
    }

    private void drawSword(HeroActor h, float alpha) {
        Weapon w = g.state.equipped();
        if (w == null) return;
        TextureRegion r = art.worldSword(w.defId, w.awakened);
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
        } else if (h.swingT > 0 && h.swingKind == 1) {
            // 검 스킬: 검을 높이 들었다가 바라보는 쪽으로 내리꽂는다
            float p = 1f - h.swingT / 0.25f;
            angle = p < 0.35f ? 95f : aim;
            scale = 1.3f;
        } else {
            switch (h.facing) {
                case LEFT: angle = 125; break;
                case RIGHT: angle = 55; break;
                case UP: angle = 100; break;
                default: angle = 75;
            }
            angle += MathUtils.sin(time * 2.5f) * 3f;
        }
        float hx = h.x + (h.facing == Facing.LEFT ? -8 : 8) * HERO_SCALE;
        float hy = h.y + 16 * HERO_SCALE + heroBob(h);
        float grip = 6 * HERO_SCALE;
        batch.setColor(1f, 1f, 1f, alpha);
        // 그림 속 칼날은 45도로 누워 있으므로 그만큼 빼고 돌린다. 손잡이(왼쪽 아래)를 회전 중심으로.
        batch.draw(r, hx - grip, hy - grip, grip, grip, 32 * HERO_SCALE, 32 * HERO_SCALE, scale, scale, angle - 45);
        if ("holy_sealed".equals(w.defId) && !art.hasHd("sword_holy") && h.swingT <= 0) {
            batch.setColor(1f, 1f, 1f, alpha * 0.8f);
            batch.draw(art.world("chain"), hx - 4 * HERO_SCALE, hy, 4 * HERO_SCALE, 4 * HERO_SCALE,
                    18 * HERO_SCALE, 18 * HERO_SCALE, 1f, 1f, angle - 45);
        }
    }

    private float heroBob(HeroActor h) {
        return h.moving && ((int) (h.walkTime * 8)) % 2 == 1 ? 2 * HERO_SCALE : 0;
    }

    private float monsterScale(Monster m) {
        if (m.state == Monster.State.PHASE_SHIFT) return S * (2f + .3f * m.progress());
        return S * (m.finalBoss ? (m.phase2 ? 2.3f : 2f) : m.boss ? 1.7f : MOB_SCALE);
    }

    private float monsterBob(Monster m) {
        float bob = m.state == Monster.State.CHASE ? Math.abs(MathUtils.sin(m.walkTime * 9f)) * 3f : 0;
        if ("bat".equals(m.def.sprite)) bob = 6 + MathUtils.sin(time * 8f + m.x) * 4f;
        if (m.state == Monster.State.SLAM_TELEGRAPH || m.state == Monster.State.CAST_TELEGRAPH)
            bob += (m.state == Monster.State.SLAM_TELEGRAPH ? 10 : 4) * m.progress();
        return bob * (m.boss ? 1f : MOB_SCALE);
    }

    private float monsterStretchY(Monster m) {
        if (m.state == Monster.State.SLAM_TELEGRAPH || m.state == Monster.State.CAST_TELEGRAPH) return 1f + .12f * m.progress();
        if (m.warning()) return 1f - .1f * m.progress();
        if (m.state == Monster.State.CHARGING || m.state == Monster.State.SWOOPING) return .9f;
        if (m.state == Monster.State.STUNNED) return .92f;
        if (m.state == Monster.State.PHASE_SHIFT) return 1f + .05f * MathUtils.sin(time * 9f);
        if (m.state == Monster.State.CHANNEL) return 1f + .04f * MathUtils.sin(time * 12f);
        return 1f;
    }

    /** Overhead UI follows the same pose as the sprite, not the lower-body combat circle. */
    private float monsterTop(Monster m) {
        return m.y - 2 + monsterBob(m) + art.height(art.world(m.def.sprite)) * monsterScale(m) * monsterStretchY(m);
    }

    private void drawMonster(Monster m) {
        TextureRegion r = art.world(m.def.sprite);
        float scale = monsterScale(m);
        float appear = m.state == Monster.State.SPAWN ? Math.max(0f, (m.progress() - 0.35f) / 0.65f) : 1f;
        if (appear <= 0f) return;
        float w = art.width(r) * scale, hgt = art.height(r) * scale;
        drawShadow(m.x, m.y, w * 0.8f * appear);
        float bob = monsterBob(m);
        // 공격 예고: 뒤로 살짝 움츠렸다가 친다
        float sx = 1f, sy = monsterStretchY(m);
        float jx = 0;
        if (m.state == Monster.State.SLAM_TELEGRAPH || m.state == Monster.State.CAST_TELEGRAPH) {
            float p = m.progress();
            sx = 1f - 0.08f * p;
        } else if (m.warning()) {
            float p = m.progress();
            sx = 1f + 0.12f * p;
            if (m.state == Monster.State.CHARGE_TELEGRAPH) jx = MathUtils.sin(time * 60f) * 2f * p;
        } else if (m.state == Monster.State.CHARGING || m.state == Monster.State.SWOOPING) {
            sx = 1.15f;
        } else if (m.state == Monster.State.STUNNED) {
            jx = MathUtils.sin(time * 14f) * 1.5f;
        } else if (m.state == Monster.State.PHASE_SHIFT) {
            jx = MathUtils.sin(time * 50f) * 3f * (1f - m.progress());
        } else if (m.state == Monster.State.CHANNEL) {
            if ("firespin".equals(m.channelKind)) jx = MathUtils.sin(time * 30f) * 1.5f;
        }
        float dw = w * sx * appear, dh = hgt * sy * appear;
        boolean flip = m.dirX < -0.2f;
        float mx = m.x + jx;
        if (m.state == Monster.State.PHASE_SHIFT) batch.setColor(0.7f + 0.3f * MathUtils.sin(time * 20f), 0.4f, 1f, appear);
        else if (m.phase2) batch.setColor(0.8f, 0.6f + 0.08f * MathUtils.sin(time * 6f), 1f, appear);
        else if (m.warning()) batch.setColor(1f, 0.8f, 0.7f, appear);
        else if (m.enraged) batch.setColor(1f, 0.75f + 0.1f * MathUtils.sin(time * 8f), 0.7f, appear);
        else batch.setColor(1f, 1f, 1f, appear);
        if (flip) batch.draw(r, mx + dw / 2f, m.y - 2 + bob, -dw, dh);
        else batch.draw(r, mx - dw / 2f, m.y - 2 + bob, dw, dh);
        if (m.flash > 0) {
            // 맞은 순간 하얗게 번쩍인다
            Particles.additive(batch);
            batch.setColor(1f, 1f, 1f, Math.min(1f, m.flash / 0.15f));
            for (int i = 0; i < 2; i++) {
                if (flip) batch.draw(r, mx + dw / 2f, m.y - 2 + bob, -dw, dh);
                else batch.draw(r, mx - dw / 2f, m.y - 2 + bob, dw, dh);
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
            float px = p.x(T), py = p.y(T);
            if (p.kind == Prop.Kind.CAMPFIRE) {
                float fl = 0.85f + 0.15f * MathUtils.sin(time * 10f);
                batch.setColor(1f, 0.55f, 0.2f, 0.55f * fl);
                batch.draw(art.glow, p.centerX(T) - 48, p.centerY(T) - 32, 96, 80);
            } else if (p.kind == Prop.Kind.FORGE && !p.baked) {
                float fl = 0.7f + 0.3f * MathUtils.sin(time * 7f);
                batch.setColor(1f, 0.5f, 0.15f, 0.6f * fl);
                batch.draw(art.glow, px + 6, py + 26, 28, 28);
            } else if (p.kind == Prop.Kind.SEAL && sealPower() > 0) {
                Color c = sealColor();
                float cx = p.centerX(T), cy = p.centerY(T);
                float pulse = 0.75f + 0.25f * MathUtils.sin(time * 3f);
                batch.setColor(c.r, c.g, c.b, 0.35f * sealPower() * pulse);
                batch.draw(art.glow, cx - 70, cy - 40, 140, 80);
                if (g.state.unsealed) {
                    batch.setColor(c.r, c.g, c.b, 0.3f * pulse);
                    batch.draw(art.glow, cx - 30, cy - 10, 60, 140);
                }
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
                float cx = d.footX, cy = d.footY;
                if (!inView(cx, cy)) continue;
                float pulse = 0.7f + 0.3f * MathUtils.sin(time * 3f);
                for (int i = 0; i < d.tilesX.length; i++) {
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
                TextureRegion seal = art.world("seal");
                batch.draw(seal, cx - 24, cy - 24, 24, 24, 48, 48, 1f, 1f, time * 20f);
                batch.setColor(c.r, c.g, c.b, 0.35f * pulse);
                batch.draw(art.glow, cx - 60, cy - 60, 120, 120);
            }
        }
        // 마왕의 기운
        for (Monster m : g.monsters) {
            if (m.dead || !m.finalBoss) continue;
            float k = m.phase2 || m.state == Monster.State.PHASE_SHIFT ? 1.4f : 1f;
            batch.setColor(0.6f, 0.2f, 1f, (0.35f + 0.1f * MathUtils.sin(time * 5f)) * k);
            batch.draw(art.glow, m.x - 90 * k, m.y - 20, 180 * k, 160 * k);
        }
        // 방패, 긴 패턴의 기운
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.shield > 0) {
                float fr = m.shieldMax > 0 ? (float) m.shield / m.shieldMax : 1f;
                float r = m.hurtRadius * 2.6f;
                float pulse = 0.8f + 0.2f * MathUtils.sin(time * 4f);
                batch.setColor(SHIELD_COLOR.r, SHIELD_COLOR.g, SHIELD_COLOR.b, (0.18f + 0.25f * fr) * pulse);
                batch.draw(art.glow, m.x - r, m.y - r * 0.2f, r * 2, r * 1.9f);
            }
            if (m.state == Monster.State.CHANNEL) {
                Color c = "lightning".equals(m.channelKind) ? PROJ_SKY : "firespin".equals(m.channelKind) ? PROJ_FIRE
                        : "bloodpool".equals(m.channelKind) ? PROJ_BLOOD : new Color(1f, 0.6f, 0.35f, 1f);
                float r = m.hurtRadius * 2.4f * (0.9f + 0.1f * MathUtils.sin(time * 10f));
                batch.setColor(c.r, c.g, c.b, 0.4f);
                batch.draw(art.glow, m.x - r, m.centerY() - r, r * 2, r * 2);
            }
        }
        // 낙뢰가 떨어지기 직전 하늘에서 모이는 빛
        for (Hazard h : g.hazards) {
            if (h.kind != Hazard.Kind.CIRCLE || !inView(h.x, h.y)) continue;
            Color c = hazardColor(h.style);
            float p = h.progress();
            if ("bolt".equals(h.style)) {
                batch.setColor(c.r, c.g, c.b, 0.15f + 0.45f * p);
                batch.draw(art.glow, h.x - 10, h.y, 20, 260 * p);
            }
            batch.setColor(c.r, c.g, c.b, 0.2f * p);
            batch.draw(art.glow, h.x - h.radius, h.y - h.radius * 0.6f, h.radius * 2, h.radius * 1.2f);
        }
        // 몬스터 투사체와 모으는 빛, 기절 별
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.state == Monster.State.SPAWN) {
                Color c = spawnColor(m);
                float p = m.progress();
                float r = m.hurtRadius * 2.2f * (m.boss ? 1f : MOB_SCALE);
                batch.setColor(c.r, c.g, c.b, 0.55f * Math.min(1f, p * 2.5f));
                batch.draw(art.glow, m.x - r, m.y - r * 0.45f, r * 2, r * 0.9f);
            } else if (m.state == Monster.State.CAST_TELEGRAPH) {
                Color c = projectileColor(m.def.sprite);
                float p = m.progress();
                float sz = (m.boss ? 70 : 34 * MOB_SCALE) * (0.4f + p);
                batch.setColor(c.r, c.g, c.b, 0.35f + 0.5f * p);
                batch.draw(art.glow, m.x - sz / 2f, m.centerY() + m.hurtRadius * 0.4f - sz / 2f, sz, sz);
            } else if (m.state == Monster.State.STUNNED) {
                float top = monsterTop(m) + 4;
                for (int i = 0; i < 3; i++) {
                    float a = time * 5f + i * MathUtils.PI2 / 3f;
                    float px = m.x + MathUtils.cos(a) * m.hurtRadius * 0.7f, py = top + MathUtils.sin(a) * 5f;
                    batch.setColor(1f, 0.9f, 0.4f, 0.9f);
                    batch.draw(art.glow, px - 7, py - 7, 14, 14);
                    batch.draw(art.white(), px - 1, py - 1, 3, 3);
                }
            }
        }
        for (Projectile p : g.projectiles) {
            Color c = projectileColor(p.source);
            float r = p.radius;
            float pulse = 1f + 0.15f * MathUtils.sin(time * 25f + p.x);
            batch.setColor(c.r, c.g, c.b, 0.75f);
            batch.draw(art.glow, p.x - r * 3.2f * pulse, p.y - r * 3.2f * pulse, r * 6.4f * pulse, r * 6.4f * pulse);
            // 꼬리
            float vl = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
            for (int k = 1; k <= 3; k++) {
                float tx = p.x - p.vx / vl * r * 1.4f * k, ty = p.y - p.vy / vl * r * 1.4f * k;
                float ts = r * (2.2f - k * 0.45f);
                batch.setColor(c.r, c.g, c.b, 0.45f - k * 0.1f);
                batch.draw(art.glow, tx - ts, ty - ts, ts * 2, ts * 2);
            }
            batch.setColor(1f, 0.95f, 0.85f, 1f);
            batch.draw(art.glow, p.x - r, p.y - r, r * 2, r * 2);
        }
        drawSkillShots();
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

    /**
     * 검 스킬 (더하기 섞기 안에서 그린다). 지금은 빛 덩어리를 겹쳐 모양만 낸 임시 효과다.
     * 그림이 오면 바꾼다 (docs/ART_REQUESTS.md "검 스킬 효과").
     */
    private void drawSkillShots() {
        for (HeroShot h : g.shots) {
            Color c = skillColor(h.kind);
            if (h.strike()) {
                // 떨어질 자리 예고: 바닥에 납작한 빛
                float p = Math.min(1f, h.age / Math.max(0.01f, h.age + h.delay));
                batch.setColor(c.r, c.g, c.b, 0.25f + 0.35f * p);
                batch.draw(art.glow, h.x - h.radius, h.y - h.radius * 0.6f, h.radius * 2, h.radius * 1.2f);
                continue;
            }
            float vl = (float) Math.sqrt(h.vx * h.vx + h.vy * h.vy);
            float ux = h.vx / vl, uy = h.vy / vl;
            float r = h.radius;
            if ("cleave".equals(h.kind)) {
                // 땅을 가르는 충격파: 지나간 자리를 따라 흙빛 조각이 남는다
                for (int k = 0; k < 6; k++) {
                    float tx = h.x - ux * r * 0.7f * k, ty = h.y - uy * r * 0.7f * k - 6;
                    float a = 0.7f - k * 0.1f;
                    batch.setColor(c.r, c.g, c.b, a);
                    batch.draw(art.glow, tx - r * 1.1f, ty - r * 0.5f, r * 2.2f, r);
                }
                if (MathUtils.random() < 0.6f) particles.debris(h.x, h.y - 8, 1, new Color(0.45f, 0.38f, 0.3f, 1f), 90, 3);
            } else {
                // 초승달·빛의 검기: 진행 방향에 수직으로 길쭉한 빛을 여러 겹
                float px = -uy, py = ux;
                int n = "beam".equals(h.kind) ? 9 : 7;
                for (int k = 0; k < n; k++) {
                    float t = (k - (n - 1) / 2f) / ((n - 1) / 2f);
                    float bend = "crescent".equals(h.kind) ? (1f - t * t) * r * 0.9f : 0f;
                    float sx = h.x + px * t * r * 1.6f + ux * bend, sy = h.y + py * t * r * 1.6f + uy * bend;
                    float sz = r * (1.1f - 0.5f * Math.abs(t));
                    batch.setColor(c.r, c.g, c.b, 0.75f);
                    batch.draw(art.glow, sx - sz, sy - sz, sz * 2, sz * 2);
                }
                batch.setColor(1f, 1f, 1f, 0.8f);
                batch.draw(art.glow, h.x - r * 0.6f, h.y - r * 0.6f, r * 1.2f, r * 1.2f);
            }
        }
        for (float[] b : bolts) {
            float a = 1f - b[2] / 0.3f;
            Color c = skillColor("strike");
            batch.setColor(c.r, c.g, c.b, 0.8f * a);
            batch.draw(art.white(), b[0] - 5, b[1], 10, 520);
            batch.setColor(1f, 1f, 1f, a);
            batch.draw(art.white(), b[0] - 2, b[1], 4, 520);
        }
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
            float w = 30 * MOB_SCALE, y = monsterTop(m) + 5;
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
            if (m.warning()) {
                float top = monsterTop(m) + 16;
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
        for (Speech sp : speeches) {
            float a = Math.min(1f, sp.t * 4f) * Math.min(1f, (3.6f - sp.t) * 2f);
            layout.setText(fonts.normal, sp.text);
            float w = layout.width + 28, hgt = layout.height + 20;
            float x = sx(cam, sp.x) - w / 2f, y = sy(cam, sp.y);
            batch.setColor(0.06f, 0.02f, 0.1f, 0.85f * a);
            batch.draw(art.white(), x, y, w, hgt);
            batch.draw(art.white(), x + w / 2f - 6, y - 8, 12, 8);
            batch.setColor(PROJ_DARK.r, PROJ_DARK.g, PROJ_DARK.b, 0.9f * a);
            batch.draw(art.white(), x, y + hgt - 2, w, 2);
            batch.setColor(Color.WHITE);
            fonts.normal.setColor(1f, 0.9f, 1f, a);
            fonts.normal.draw(batch, sp.text, x + 14, y + hgt - 10);
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
        return new Color(0.7f, 0.5f, 1f, 1f);
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

    /** 투사체 색: 쏜 몬스터마다 다르다. */
    private static Color projectileColor(String source) {
        if (source == null) return PROJ_FIRE;
        switch (source) {
            case "knight": return PROJ_BLOOD;
            case "warden": return PROJ_SKY;
            case "demonlord": return PROJ_DARK;
            case "fire": return new Color(1f, 0.42f, 0.12f, 1f);
            default: return PROJ_FIRE;
        }
    }

    private static Color spawnColor(Monster m) {
        if (m.finalBoss) return PROJ_DARK;
        String th = m.def.sprite;
        if ("bat".equals(th) || "golem".equals(th)) return SPAWN_BLUE;
        if ("skeleton".equals(th) || "knight".equals(th)) return PROJ_BLOOD;
        return PROJ_DARK;
    }

    private static final Color PROJ_FIRE = new Color(1f, 0.5f, 0.2f, 1f);
    private static final Color PROJ_BLOOD = new Color(1f, 0.2f, 0.25f, 1f);
    private static final Color PROJ_SKY = new Color(0.4f, 0.8f, 1f, 1f);
    private static final Color PROJ_DARK = new Color(0.7f, 0.3f, 1f, 1f);
    private static final Color SPAWN_BLUE = new Color(0.4f, 0.6f, 1f, 1f);
    private static final Color SHIELD_COLOR = new Color(1f, 0.82f, 0.55f, 1f);

    @Override
    public void dispose() {
        lighting.dispose();
        for (Texture t : sceneTex.values()) t.dispose();
        sceneTex.clear();
    }
}
