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
import com.yongsa.gfx.ActorInk;
import com.yongsa.gfx.Fonts;
import com.yongsa.gfx.HeroMotion;
import com.yongsa.gfx.HeroPose;
import com.yongsa.gfx.HeroSword;
import com.yongsa.gfx.SpriteBlend;
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
    private static final float DEFAULT_MOB_SCALE = 1.25f;

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
    /** GPT 길·땅 그림. 그림이 있으면 통로 타일과 통로 횃불 대신 쓴다. */
    private final PassagePainter passages;
    private RoomGates roomGates;
    private final List<Corpse> corpses = new ArrayList<>();
    private final List<Ghost> ghosts = new ArrayList<>();
    private final List<Ring> rings = new ArrayList<>();
    private final List<Flash> flashes = new ArrayList<>();
    private final Map<Monster, Float> ages = new IdentityHashMap<>();
    /** 던전 방 배경 그림 (보이는 방만 읽어 두고, 오래 안 쓴 것은 버린다). */
    private final Map<String, Texture> sceneTex = new HashMap<>();
    private final Map<String, Float> sceneUsed = new HashMap<>();
    /** GPT 가 준 앞쪽 가림 레이어 (<장면>-front.png). 없는 장면은 null 로 기억한다. */
    private final Map<String, Texture> frontTex = new HashMap<>();
    private final SceneForegrounds sceneForegrounds;
    private final BossReturnDoors returnDoors;
    private final Color ambient = new Color(0.6f, 0.55f, 0.5f, 1f);
    private boolean ambientSet;
    private float time, ghostT, moteT, emberT;
    private float walkBlend;
    private final boolean heroMotionAvailable;
    private final SpriteBlend spriteBlend = new SpriteBlend();
    private final ActorInk actorInk = new ActorInk();
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
        String text, speaker;
    }

    private final List<Speech> speeches = new ArrayList<>();
    /** 천공 낙뢰가 떨어진 자리 (x, y, 지난 시간): 하늘에서 내리꽂히는 빛줄기를 잠깐 그린다. */
    private final List<float[]> bolts = new ArrayList<>();
    /** Presentation only: rewards are granted once by GameSession, independently of these animations. */
    private final List<ChestOpening> chestOpenings = new ArrayList<>();
    private final List<float[]> eyeFlashes = new ArrayList<>(); // x, y, radius, age

    private static class ChestOpening {
        String room, dungeon;
        float x, y, age;
    }

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
        /** 지용 버전 보스 동작 시트가 있으면 쓰러지는 줄을 돌린다. */
        String sheet;
        float sheetScale;
        boolean sheetFaceRight;
    }

    // ───────────────────────── 지용 버전 보스 동작 시트
    // 256px 칸 8열 x 5행, 왼쪽을 보는 그림, 발은 칸의 (128, 224).
    // 1행: 대기 4 + 피격 4 · 2행: 이동 8 · 3행: 기본 공격 8 (앞 4칸 준비) · 4행: 특수 공격 8 · 5행: 쓰러짐 8
    private static final int BOSS_CELL = 256, BOSS_FOOT = 224, BOSS_BODY = 128;
    private static final float BOSS_SHEET_SCALE = 0.75f;
    private final java.util.IdentityHashMap<Monster, float[]> bossAnim = new java.util.IdentityHashMap<>();

    /** 보스 그림 이름 → 동작 시트 (파일이 없으면 null: 한 장짜리 그림을 쓴다). */
    private static String bossSheet(String sprite, boolean phase2) {
        if ("demonlord".equals(sprite) && phase2) {
            String trueForm = "team/jiyong/boss_lich2.png";
            if (SHEET_EXISTS.computeIfAbsent(trueForm, k -> Gdx.files.internal(k).exists())) return trueForm;
        }
        String path;
        switch (sprite) {
            case "golem": path = "team/jiyong/boss_golem.png"; break;
            case "knight": path = "team/jiyong/boss_knight.png"; break;
            // 지용 버전 리치(해골 사제) 그림은 마왕으로 쓴다 (2026-10-14 해성 결정). 탑 보스는 원래 그림.
            case "demonlord": path = "team/jiyong/boss_lich.png"; break;
            default: return null;
        }
        return SHEET_EXISTS.computeIfAbsent(path, k -> Gdx.files.internal(k).exists()) ? path : null;
    }

    private static final java.util.Map<String, Boolean> SHEET_EXISTS = new java.util.HashMap<>();

    private TextureRegion bossCell(String sheet, int row, int col) {
        return art.cell(sheet, 8, 5, row * 8 + Math.min(7, Math.max(0, col)));
    }

    /**
     * 보스의 지금 자세 (행, 열). 공격은 준비 동안 앞 4칸, 친 뒤(RECOVER)에는 뒤 4칸.
     * 기본 공격(2행)과 특수 공격(4행 · 내려찍기·마법·긴 패턴)을 나눈다.
     */
    private int[] bossPose(Monster m) {
        if (bossAnim.size() > 16) bossAnim.keySet().removeIf(x -> x.dead);
        float[] a = bossAnim.computeIfAbsent(m, k -> new float[]{m.x, m.y, 2, 0, 0});
        // a: 지난 x, y, 마지막 공격 줄, 움직인 시간, 지난 상태
        boolean moved = Math.abs(m.x - a[0]) + Math.abs(m.y - a[1]) > 0.05f;
        a[0] = m.x;
        a[1] = m.y;
        if (moved) a[3] += Gdx.graphics.getDeltaTime();
        int four = Math.min(3, (int) (m.progress() * 4));
        switch (m.state) {
            case TELEGRAPH: case COMBO_TELEGRAPH: case CHARGE_TELEGRAPH:
                a[2] = 2;
                return new int[]{2, four};
            case SLAM_TELEGRAPH: case CAST_TELEGRAPH:
                a[2] = 3;
                return new int[]{3, four};
            case RECOVER:
                return new int[]{(int) a[2], 4 + four};
            case CHANNEL:
                return new int[]{3, 4 + ((int) (time * 8)) % 4};
            case CHARGING:
                return new int[]{1, ((int) (time * 14)) % 8};
            case STUNNED:
                return new int[]{0, 4 + ((int) (time * 5)) % 4};
            default:
                if (moved) return new int[]{1, ((int) (a[3] * 7)) % 8};
                return new int[]{0, ((int) (time * 5.5f)) % 4};
        }
    }

    /** 동작 시트 크기: 마왕은 다른 보스보다 크고, 2페이즈(본모습)에서 더 커진다. */
    private float sheetScale(Monster m) {
        return bossSheetScale(m.finalBoss, m.phase2);
    }

    private static float bossSheetScale(boolean finalBoss, boolean phase2) {
        return BOSS_SHEET_SCALE * (finalBoss ? (phase2 ? 1.35f : 1.18f) : 1f);
    }

    private void drawBossSheet(Monster m, String sheet) {
        int[] pose = bossPose(m);
        TextureRegion r = bossCell(sheet, pose[0], pose[1]);
        float appear = m.state == Monster.State.SPAWN ? Math.max(0f, (m.progress() - 0.35f) / 0.65f) : 1f;
        if (appear <= 0f) return;
        float k = sheetScale(m) * appear;
        float size = BOSS_CELL * k;
        drawActorShadow(m.x, m.y, BOSS_BODY * k * 0.8f, appear, false);
        float jx = m.state == Monster.State.STUNNED ? MathUtils.sin(time * 14f) * 1.5f
                : m.state == Monster.State.CHARGE_TELEGRAPH ? MathUtils.sin(time * 60f) * 2f * m.progress() : 0;
        boolean faceRight = m.dirX > 0.2f;
        float x0 = m.x + jx - size / 2f, y0 = m.y - (BOSS_CELL - BOSS_FOOT) * k;
        if (m.warning()) batch.setColor(1f, 0.8f, 0.7f, appear);
        else if (m.finalBoss && m.phase2 && sheet.endsWith("boss_lich.png")) batch.setColor(0.95f, 0.62f + 0.08f * MathUtils.sin(time * 6f), 0.95f, appear); // 시트가 없는 경우만 임시 색상을 쓴다.
        else if (m.enraged) batch.setColor(1f, 0.75f + 0.1f * MathUtils.sin(time * 8f), 0.7f, appear);
        else batch.setColor(1f, 1f, 1f, appear);
        actorInk.draw(batch, r, faceRight ? x0 + size : x0, y0, faceRight ? -size : size, size);
        if (m.flash > 0) {
            Particles.additive(batch);
            batch.setColor(1f, 1f, 1f, Math.min(1f, m.flash / 0.15f));
            for (int i = 0; i < 2; i++) {
                if (faceRight) batch.draw(r, x0 + size, y0, -size, size);
                else batch.draw(r, x0, y0, size, size);
            }
            Particles.normal(batch);
        }
        batch.setColor(Color.WHITE);
    }

    private static class Ghost {
        TextureRegion region;
        float x, y, w, h, t, footOffset;
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
        heroMotionAvailable = Gdx.files.internal(com.yongsa.gfx.HeroMotionAtlas.MANIFEST).exists()
                || (Gdx.files.internal("sprites/hero-motion/walk.png").exists()
                && Gdx.files.internal("sprites/hero-motion/roll.png").exists());
        this.particles = new Particles(art.white(), art.glow);
        this.theme = new String[map.width * map.height];
        for (Room r : map.rooms) {
            String th = r.dungeonId == null ? "CAMP" : r.dungeonId;
            for (int y = r.y0; y <= r.y1; y++) for (int x = r.x0; x <= r.x1; x++) theme[x + y * map.width] = th;
        }
        // 통로 바닥과 벽은 이어진 던전 테마. 앞벽(아래가 통로 바닥)에는 일정한 간격으로 횃불을 건다.
        for (int y = 0; y < map.height; y++) {
            for (int x = 0; x < map.width; x++) {
                com.yongsa.world.Corridor c = map.corridorAt(x, y);
                if (c == null) continue;
                theme[x + y * map.width] = c.theme;
                if (map.tile(x, y) == WorldMap.WALL && map.roomAt(x, y) == null && map.tile(x, y - 1) == WorldMap.CORRIDOR
                        && Math.floorMod(x * 7 + y * 3, 9) == 0)
                    torches.add(new float[]{x * T + T / 2f, y * T, MathUtils.random(10f), 1f});
            }
        }
        passages = new PassagePainter(map, T);
        sceneForegrounds = new SceneForegrounds(passages);
        returnDoors = new BossReturnDoors(g);
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
                if (e.dungeonId != null && e.opened != null) {
                    chestOpenings.removeIf(c -> e.opened.equals(c.room));
                    ChestOpening c = new ChestOpening();
                    c.room = e.opened;
                    c.dungeon = e.dungeonId;
                    c.x = e.x;
                    c.y = e.y - T;
                    chestOpenings.add(c);
                }
                Color gold = new Color(1f, 0.85f, 0.4f, 1f);
                particles.burst(e.x, e.y - T / 2f, 14, gold, 140, 0.45f, 1.8f, true);
                flash(e.x, e.y - T / 2f, 110, gold, 0.3f);
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
                float scale = S * e.size * (e.boss ? bossScale(e.sprite) : mobScale(e.sprite));
                c.w = art.width(c.region) * scale;
                c.h = art.height(c.region) * scale;
                c.x = e.x;
                c.y = e.y;
                c.flip = e.flip;
                c.boss = e.boss;
                c.dur = e.boss ? 1.4f : 0.4f;
                if (e.boss && bossSheet(e.sprite, e.phase2) != null) {
                    c.sheet = bossSheet(e.sprite, e.phase2);
                    c.sheetScale = bossSheetScale("demonlord".equals(e.sprite), e.phase2);
                    c.sheetFaceRight = e.dirX > 0.2f;
                    c.dur = 2.2f;
                    c.h = BOSS_BODY * c.sheetScale;
                    c.w = BOSS_BODY * c.sheetScale;
                }
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
                for (Monster m : g.monsters) {
                    if (m.boss && Math.abs(m.x - e.x) < .01f)
                        sp.y = Math.max(sp.y, monsterTop(m) + 16);
                }
                sp.text = e.text;
                sp.speaker = e.speaker;
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
                chestOpenings.clear();
                eyeFlashes.clear();
                speeches.clear();
                particles.clear();
                corpses.clear();
                ghosts.clear();
                rings.clear();
                break;
            case BED_USED:
                chestOpenings.clear();
                eyeFlashes.clear();
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
        for (ChestOpening c : chestOpenings) c.age += dt;
        chestOpenings.removeIf(c -> c.age >= 2.8f || g.hasChest(map.room(c.room)));
        for (float[] e : eyeFlashes) e[3] += dt;
        eyeFlashes.removeIf(e -> e[3] >= 0.45f);
        for (float[] b : bolts) b[2] += dt;
        bolts.removeIf(b -> b[2] > 0.3f);
        time += dt;
        if(roomGates==null)roomGates=new RoomGates(map);
        roomGates.update(dt);
        sceneForegrounds.expire(time);
        returnDoors.update(dt);
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
        float targetWalk = h.moving && !h.rolling() ? 1f : 0f;
        walkBlend += (targetWalk - walkBlend) * (1f - (float) Math.exp(-20f * dt));
        // 돌진 잔상
        if (h.rolling()) {
            ghostT -= dt;
            if (MathUtils.random() < dt * 40f) {
                particles.smoke(h.x, h.y + 2, 1, new Color(0.75f, 0.7f, 0.62f, 0.3f), 6, 7, 0.4f);
            }
            if (ghostT <= 0) {
                ghostT = 0.06f;
                Ghost gh = new Ghost();
                float p = HeroMotion.clamp(1 - h.rollT / h.rollTotal);
                gh.region = motionFrame("roll", h.rollFacing, HeroMotion.rollFrame(p));
                if (gh.region == null) gh.region = heroRegion(h);
                boolean motion = hasHeroMotion();
                gh.w = motion ? HeroMotion.WIDTH : art.width(gh.region) * S * HERO_SCALE;
                gh.h = motion ? HeroMotion.HEIGHT : art.height(gh.region) * S * HERO_SCALE;
                gh.footOffset = motion ? HeroMotion.FOOT_OFFSET : 2;
                gh.x = h.x;
                gh.y = h.y;
                gh.flip = h.rollFacing.leftward();
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
        Room hr = g.heroRoom();

        // 방 분위기에 맞춰 바탕 밝기를 천천히 바꾼다 (통로는 조금 더 어둡다)
        Color target = ambientFor(room);
        com.yongsa.world.Corridor walking = map.corridorAtWorld(g.hero.x, g.hero.y);
        if (hr == null && walking != null) target = corridorAmbient(walking.theme);
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
            // 마을: 마왕 전에는 보랏빛 저녁, 뒤에는 한낮
            if (r.type == RoomType.VILLAGE) return g.villagersFree() ? new Color(1f, 0.98f, 0.94f, 1f) : new Color(0.62f, 0.52f, 0.82f, 1f);
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

    private Color corridorAmbient(String th) {
        switch (th) {
            case "LEFT": return new Color(0.5f, 0.52f, 0.6f, 1f);
            case "RIGHT": return new Color(0.56f, 0.42f, 0.46f, 1f);
            case "TOP": return new Color(0.48f, 0.44f, 0.64f, 1f);
            default: return new Color(0.66f, 0.6f, 0.56f, 1f);
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
        // 실명 (해골 사제): 거의 캄캄해지고 용사 둘레만 조금 보인다. 바닥 예고와 사제의 눈은 빛난다.
        float bs = g.balance.monster.blindSeconds;
        float blind = g.blindT > 0 ? Math.min(1f, Math.min(g.blindT / 0.5f, (bs - g.blindT) / 0.3f)) : 0f;
        float amb = 1f - 0.92f * blind;
        lighting.begin(ambient.r * amb, ambient.g * amb, ambient.b * amb);
        HeroActor h = g.hero;
        lighting.add(h.x, h.y + 14, 250 * (1f - 0.55f * blind), 1f, 0.92f, 0.8f, 0.6f);
        if (blind > 0) {
            for (Monster m : g.monsters) {
                if (m.dead || !m.boss) continue;
                lighting.add(m.x, m.y + 70, 70, 1f, 0.15f, 0.2f, 0.9f * blind);
            }
        }
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
            lighting.add(t[0], t[1] + 10, 175 * fl, 1f, 0.62f, 0.32f, 0.75f * (1f - blind));
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
                    float px = veinX(v), py = veinY(v);
                    if (!inView(px, py)) continue;
                    // 그림 속 광석 덩어리는 더 넓게, 캐면 캘수록 빛이 줄어든다
                    float left = v.hitsLeft / (float) g.balance.mining.hitsToBreak;
                    float rad = v.painted() ? 80 + 40 * left : 70;
                    lighting.add(px, py, rad, c.r, c.g, c.b, (0.45f + 0.1f * MathUtils.sin(time * 3f + v.tileX)) * (0.6f + 0.4f * left));
                }
            } else if (g.hasPortal(r)) {
                float px = g.portalX(r), py = g.portalY(r);
                if (inView(px, py)) lighting.add(px, py, 145, 0.28f, 0.85f, 0.75f, 0.55f + 0.06f * MathUtils.sin(time * 2f));
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

    /** 개발용 (F1): 용사 발이 설 수 있는 곳을 초록으로 덮는다. 그림은 바닥인데 초록이 없으면 막힌 곳이다. */
    public boolean showWalkable;

    public void render(OrthographicCamera cam) {
        computeView(cam);
        int tx0 = Math.max(0, (int) (viewX0 / T) - 1), ty0 = Math.max(0, (int) (viewY0 / T) - 2);
        int tx1 = Math.min(map.width - 1, (int) (viewX1 / T) + 1);
        int ty1 = Math.min(map.height - 1, (int) (viewY1 / T) + 1);

        batch.setProjectionMatrix(cam.combined);
        shapes.setProjectionMatrix(cam.combined);

        batch.begin();
        batch.setColor(Color.WHITE);
        if (passages.active) {
            passages.drawGround(batch, viewX0, viewY0, viewX1, viewY1);
            passages.drawWalls(batch, viewX0, viewY0, viewX1, viewY1);
        }
        drawScenes();
        drawTiles(tx0, ty0, tx1, ty1);
        if (passages.active) passages.drawFloors(batch, viewX0, viewY0, viewX1, viewY1);
        passages.joints.draw(batch, viewX0, viewY0, viewX1, viewY1);
        if(roomGates==null)roomGates=new RoomGates(map);
        roomGates.drawThresholds(batch,viewX0,viewY0,viewX1,viewY1);
        drawSceneDoors();
        drawFloorProps();
        drawTorches();
        batch.end();

        drawGroundEffects();

        batch.begin();
        drawGhosts();
        List<Sorted> list = new ArrayList<>();
        collect(list, tx0, ty0, tx1, ty1);
        for (Room r : map.rooms) {
            if (r.scene == null || r.originX > viewX1 || r.originX + r.widthPx < viewX0
                    || r.originY > viewY1 || r.originY + r.heightPx < viewY0) continue;
            for (SceneForegrounds.Piece piece : sceneForegrounds.pieces(r, time)) {
                list.add(new Sorted(r.sceneY(piece.depth), () -> sceneForegrounds.draw(batch, r, piece)));
            }
        }
        for (var front : passages.joints.fronts(viewX0, viewY0, viewX1, viewY1))
            list.add(new Sorted(front.depth, () -> front.draw(batch)));
        if(roomGates==null)roomGates=new RoomGates(map);
        for(var gate:roomGates.parts(batch,viewX0,viewY0,viewX1,viewY1))
            list.add(new Sorted(gate.depth(),()->gate.draw().run()));
        for (Room r : map.rooms) if (r.type == RoomType.BOSS && inView(g.portalX(r), g.portalY(r)))
            list.add(new Sorted(g.portalY(r) + 8, () -> returnDoors.draw(batch, r)));
        list.sort((a, b) -> Float.compare(b.y, a.y));
        for (Sorted s : list) s.call.draw();
        drawSceneFronts();
        particles.drawNormal(batch);
        batch.setColor(Color.WHITE);
        batch.end();

        lighting.composite(batch);

        batch.setProjectionMatrix(cam.combined);
        shapes.setProjectionMatrix(cam.combined);
        drawGlow();
        drawOverlays();
        if (showWalkable) drawWalkable();

        batch.setProjectionMatrix(screenProj);
        batch.begin();
        drawTexts(cam);
        batch.end();
    }

    private void drawWalkable() {
        final float step = 6;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0.1f, 1f, 0.3f, 0.32f);
        for (float y = (float) Math.floor(viewY0 / step) * step; y < viewY1; y += step)
            for (float x = (float) Math.floor(viewX0 / step) * step; x < viewX1; x += step)
                if (map.free(x + step / 2, y + step / 2, 9, 6)) shapes.rect(x, y, step, step);
        shapes.end();
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
                if (tr != null && tr.scene != null && t != WorldMap.CORRIDOR) continue; // 배경 그림이 바닥과 벽이다 (통로 입구만 덧그린다)
                String th = themeAt(x, y);
                float px = x * T, py = y * T;
                if (map.corridorAt(x, y) != null && (passages.active || drawCorridorTile(t, th, x, y, px, py))) continue;
                if (t == WorldMap.WALL) {
                    int below = map.tile(x, y - 1);
                    boolean front = below == WorldMap.FLOOR || below == WorldMap.DOOR || below == WorldMap.CORRIDOR;
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

    // ───────────────────────── 통로: 방 그림의 바닥과 바위를 잘라 이어 붙인다

    /**
     * 테마별 통로 질감: 그 던전 방 그림에서 바닥 한 조각과 바위 벽 한 조각을 잘라 거울 반복으로 깐다
     * (u0, v0, u1, v1: 그림 왼쪽 위 기준 0~1). 통로 타일 그림(docs/ART_REQUESTS.md 0-9)이 오면 바꾼다.
     */
    private static final String[][] CORRIDOR_SKIN = {
            {"CAMP", "scenes/natural-v1/central-camp.png", ".30,.50,.45,.68", ".25,.88,.42,.99"},
            {"LEFT", "scenes/natural-v1/mine.png", ".30,.33,.50,.58", ".20,.85,.40,.99"},
            {"RIGHT", "scenes/natural-v1/citadel.png", ".62,.32,.78,.58", ".05,.82,.25,.98"},
            {"TOP", "scenes/natural-v1/tower.png", ".24,.40,.38,.62", ".26,.88,.44,.99"},
    };
    /** 방 그림 한 칸이 월드에서 차지하는 크기 (30칸 방 = 그림 1672 칸). */
    private static final float SKIN_SCALE = 30f * 32f / 1672f;
    private final Map<String, Texture[]> corridorSkin = new HashMap<>();

    private Texture[] corridorSkin(String th) {
        Texture[] t = corridorSkin.get(th);
        if (t != null) return t;
        String[] def = CORRIDOR_SKIN[0];
        for (String[] d : CORRIDOR_SKIN) if (d[0].equals(th)) def = d;
        com.badlogic.gdx.graphics.Pixmap src = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(def[1]));
        t = new Texture[]{crop(src, def[2]), crop(src, def[3])};
        src.dispose();
        corridorSkin.put(th, t);
        return t;
    }

    private static Texture crop(com.badlogic.gdx.graphics.Pixmap src, String rect) {
        String[] r = rect.split(",");
        int x0 = Math.round(Float.parseFloat(r[0]) * src.getWidth()), y0 = Math.round(Float.parseFloat(r[1]) * src.getHeight());
        int x1 = Math.round(Float.parseFloat(r[2]) * src.getWidth()), y1 = Math.round(Float.parseFloat(r[3]) * src.getHeight());
        com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(x1 - x0, y1 - y0, src.getFormat());
        p.drawPixmap(src, 0, 0, x0, y0, x1 - x0, y1 - y0);
        Texture t = new Texture(p);
        p.dispose();
        t.setWrap(Texture.TextureWrap.MirroredRepeat, Texture.TextureWrap.MirroredRepeat);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return t;
    }

    /** 통로 칸 하나: 바닥은 그림 바닥, 벽은 바위 (앞벽은 조금 밝게, 윗면은 어둡게). 월드 좌표에 맞춰 이어진다. */
    private boolean drawCorridorTile(int t, String th, int x, int y, float px, float py) {
        if (t != WorldMap.WALL && t != WorldMap.CORRIDOR) return false;
        Texture[] skin = corridorSkin(th);
        boolean wall = t == WorldMap.WALL;
        Texture tex = wall ? skin[1] : skin[0];
        float pw = tex.getWidth() * SKIN_SCALE, ph = tex.getHeight() * SKIN_SCALE;
        if (wall) {
            int below = map.tile(x, y - 1);
            boolean front = below == WorldMap.FLOOR || below == WorldMap.DOOR || below == WorldMap.CORRIDOR;
            float k = front ? 0.8f : 0.5f;
            batch.setColor(k, k, k, 1f);
        }
        batch.draw(tex, px, py, T, T, px / pw, -(py + T) / ph, (px + T) / pw, -py / ph);
        batch.setColor(Color.WHITE);
        if (!wall) {
            if (map.tile(x, y + 1) == WorldMap.WALL) batch.draw(art.world("ao_top"), px, py, T, T);
            if (map.tile(x - 1, y) == WorldMap.WALL) batch.draw(art.world("ao_left"), px, py, T, T);
            if (map.tile(x + 1, y) == WorldMap.WALL) batch.draw(art.world("ao_right"), px, py, T, T);
        }
        return true;
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

    /**
     * 앞쪽 가림 레이어: 그림 아래쪽 바위 테두리·난간·기둥을 용사와 몬스터 위에 한 번 더 그린다.
     * 걸을 수 있는 바닥(계단 포함) 위의 픽셀은 불러올 때 지워서, 서 있는 자리를 가리지 않는다.
     */
    private void drawSceneFronts() {
        for (Room r : map.rooms) {
            if (r.scene == null) continue;
            if (r.originX > viewX1 || r.originX + r.widthPx < viewX0 || r.originY > viewY1 || r.originY + r.heightPx < viewY0) continue;
            Texture tex = frontTexture(r);
            if (tex == null) continue;
            batch.setColor(Color.WHITE);
            batch.draw(tex, r.originX, r.originY, r.widthPx, r.heightPx, 0, 0, tex.getWidth(), tex.getHeight(), r.flip, false);
        }
        if (frontTex.size() > 4) {
            for (Iterator<Map.Entry<String, Texture>> it = frontTex.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Texture> en = it.next();
                if (en.getValue() == null || time - sceneUsed.getOrDefault(en.getKey(), 0f) < 20f) continue;
                en.getValue().dispose();
                it.remove();
            }
        }
    }

    private Texture frontTexture(Room r) {
        String file = r.scene.image.replace(".png", "-front.png");
        String key = r.id + ":" + file; // 출구 혼합 마스크는 방 배치마다 다르다.
        if (frontTex.containsKey(key)) {
            sceneUsed.put(key, time);
            return frontTex.get(key);
        }
        Texture t = null;
        if (Gdx.files.internal(file).exists()) {
            com.badlogic.gdx.graphics.Pixmap p = new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(file));
            eraseWalkable(p, r.scene.walkable);
            if (passages.active) p = passages.joinForeground(p, r);
            t = new Texture(p);
            t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            p.dispose();
        }
        frontTex.put(key, t);
        sceneUsed.put(key, time);
        return t;
    }

    /** 바닥 다각형(0~1 좌표) 안쪽 픽셀을 투명하게: 줄마다 다각형과 만나는 구간을 지운다. */
    private static void eraseWalkable(com.badlogic.gdx.graphics.Pixmap p, float[] poly) {
        int w = p.getWidth(), h = p.getHeight(), n = poly.length / 2;
        p.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);
        p.setColor(0, 0, 0, 0);
        float[] xs = new float[n];
        for (int y = 0; y < h; y++) {
            float v = (y + 0.5f) / h;
            int k = 0;
            for (int i = 0, j = n - 1; i < n; j = i++) {
                float vi = poly[i * 2 + 1], vj = poly[j * 2 + 1];
                if ((vi > v) != (vj > v)) {
                    float u = poly[j * 2] + (v - vj) / (vi - vj) * (poly[i * 2] - poly[j * 2]);
                    xs[k++] = u * w;
                }
            }
            java.util.Arrays.sort(xs, 0, k);
            for (int i = 0; i + 1 < k; i += 2) {
                int x0 = Math.max(0, Math.round(xs[i])), x1 = Math.min(w, Math.round(xs[i + 1]));
                if (x1 > x0) p.fillRectangle(x0, y, x1 - x0, 1);
            }
        }
    }

    private Texture sceneTexture(Room r) {
        String file = r.scene.image;
        String key = r.id + ":" + file;
        sceneUsed.put(key, time);
        Texture t = sceneTex.get(key);
        if (t == null) {
            if (passages.active) {
                // 방 그림 가장자리를 흐려서 둘레의 땅·길 그림과 이어지게 한다
                com.badlogic.gdx.graphics.Pixmap p = passages.joinRoom(new com.badlogic.gdx.graphics.Pixmap(Gdx.files.internal(file)), r);
                t = new Texture(p, true);
                p.dispose();
            } else {
                t = new Texture(Gdx.files.internal(file), true);
            }
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
                if(roomGates==null)roomGates=new RoomGates(map);
                if(roomGates.supports(d))continue;
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
                    if (drawGateArt(r, d)) continue;
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

    private final Map<String, Texture> gateTex = new HashMap<>();

    /**
     * 던전별 닫힌 출구 그림 (GPT 요청: sprites/gate_<던전>.png 정면, gate_<던전>_side.png 옆면).
     * 캠프의 던전 입구는 그 던전 것을 쓴다. 그림이 없으면 false (코드로 그린 창살).
     */
    private boolean drawGateArt(Room r, Door d) {
        String dungeon = r.dungeonId;
        if (r.type == RoomType.CAMP && d.link != null) dungeon = d.link.other(r).dungeonId;
        if (dungeon == null) return false;
        boolean side = !d.horizontal();
        String key = "sprites/gate_" + dungeon + (side ? "_side" : "") + ".png";
        Texture t = gateTex.get(key);
        if (t == null && !gateTex.containsKey(key)) {
            if (Gdx.files.internal(key).exists()) {
                t = new Texture(Gdx.files.internal(key), true); // 큰 그림을 작게 그리므로 밉맵
                t.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            }
            gateTex.put(key, t);
        }
        if (t == null) return false;
        float span = d.halfWidth * 2f + T * 0.5f;
        if (!side) {
            // 정면: 출구 폭에 맞춰 가로로 늘이고, 아래 끝이 출구 발 자리에 온다
            float w = span, h = w * t.getHeight() / t.getWidth();
            batch.draw(t, d.footX - w / 2f, d.footY - T * 0.35f, w, h);
        } else {
            // 옆면: 출구 폭이 세로 길이, 왼쪽 출구면 좌우를 뒤집어 그린다
            float h = span + T, w = h * t.getWidth() / t.getHeight();
            batch.draw(t, d.footX - w / 2f, d.footY - span / 2f, w, h, 0, 0, t.getWidth(), t.getHeight(), d.dirX < 0, false);
        }
        return true;
    }

    /** 캠프의 봉인석과 열린 심연. 바닥 원근은 원화에 있으므로 회전/찌그러뜨리지 않는다. */
    private void drawFloorProps() {
        for (Prop p : map.props) {
            if (p.kind != Prop.Kind.SEAL) continue;
            float cx = p.centerX(T), cy = p.centerY(T);
            if (!inView(cx, cy)) continue;
            TextureRegion stone = art.world(g.state.unsealed ? "abyss_open" : "abyss_sealed");
            batch.setColor(Color.WHITE);
            drawGroundSprite(stone, cx, cy);
        }
    }

    private void drawGroundSprite(TextureRegion sprite, float x, float y) {
        float w = art.width(sprite), h = art.height(sprite);
        batch.draw(sprite, x - w / 2f, y - h / 2f, w, h);
    }

    /** 세 보스에 대응하는 보석만 밝힌다. 좌표는 봉인석 원본 1536x1024 기준. */
    private void drawSealSockets(float cx, float cy, float pulse) {
        TextureRegion stone = art.world("abyss_sealed");
        float w = art.width(stone), h = art.height(stone);
        float[][] sockets = {{767,257}, {459,590}, {1075,590}};
        Color[] colors = {new Color(.5f,.75f,1f,1f), new Color(1f,.3f,.35f,1f), new Color(.4f,.95f,1f,1f)};
        for (int i = 0; i < 3; i++) {
            if (!g.state.bossDefeated(com.yongsa.model.AbsorbService.BOSS_ORDER[i])) continue;
            float x = cx + (sockets[i][0] / 1536f - .5f) * w;
            float y = cy + (.5f - sockets[i][1] / 1024f) * h;
            Color c = g.absorb.canUnseal(g.state) ? Ui.GOLD : colors[i];
            batch.setColor(c.r,c.g,c.b,.65f * pulse);
            batch.draw(art.glow,x-10,y-7,20,14);
            batch.setColor(c.r,c.g,c.b,.9f);
            batch.draw(art.glow,x-3,y-2,6,4);
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
            if (t.length > 3 && passages.active) continue; // 길 그림에 등불이 그려져 있다 (빛만 남긴다)
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
                case EYE: {
                    if (eyeRegion(0) != null) break;
                    // 감긴 눈이 천천히 떠진다
                    float r = h.radius, open = Math.max(0.08f, p * p);
                    shapes.setColor(0.12f, 0.02f, 0.08f, 0.85f);
                    shapes.ellipse(h.x - r * 1.15f, h.y - r * 0.62f, r * 2.3f, r * 1.24f, 32);
                    shapes.setColor(0.95f, 0.9f, 0.85f, 0.9f);
                    shapes.ellipse(h.x - r, h.y - r * 0.5f * open, r * 2f, r * open, 32);
                    shapes.setColor(EYE_RED.r, EYE_RED.g, EYE_RED.b, 0.95f);
                    float pr = r * 0.32f * open;
                    shapes.circle(h.x, h.y, pr, 20);
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
            case "bone":
                // 바닥에서 솟는 뼈 가시
                particles.debris(e.x, e.y + 10, 14, BONE, 260, 4);
                particles.burst(e.x, e.y + 8, 10, new Color(0.6f, 1f, 0.5f, 1f), 160, 0.4f, 3, true);
                ring(e.x, e.y, e.size * 0.3f, e.size, 0.3f, BONE);
                break;
            case "eye":
                eyeFlashes.add(new float[]{e.x, e.y, e.size, 0f});
                flash(e.x, e.y, 220, EYE_RED, 0.35f);
                ring(e.x, e.y, 8, 120, 0.4f, EYE_RED);
                break;
            case "whip":
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
            case "bone": return BONE;
            case "whip": return WHIP;
            case "eye": return EYE_RED;
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
            // 그림 속 광석 덩어리 앞 광맥은 그림이 곧 광석이라 따로 그리지 않는다
            for (Vein v : veins) if (!v.painted()) list.add(new Sorted(v.tileY * T + 4, () -> drawVein(v)));
        }
        if (g.inVillage()) {
            for (com.yongsa.config.Balance.Villager v : g.balance.village.villagers) {
                float vx = g.villagerX(v), vy = g.villagerY(v);
                list.add(new Sorted(vy, () -> drawVillager(v, vx, vy)));
            }
        }
        for (Room r : map.rooms) {
            if (!g.hasChest(r) || g.fighting()) continue;
            float cx = g.chestX(r), cy = g.chestY(r);
            if (inView(cx, cy)) list.add(new Sorted(cy, () -> drawChest(r.dungeonId, cx, cy, 0, 1f)));
        }
        for (ChestOpening c : chestOpenings) {
            if (!inView(c.x, c.y) || g.fighting()) continue;
            int frame = c.age < 0.18f ? 1 : 2;
            float alpha = Math.min(1f, (2.8f - c.age) / 0.5f);
            list.add(new Sorted(c.y, () -> drawChest(c.dungeon, c.x, c.y, frame, alpha)));
        }
        for (Hazard h : g.hazards) {
            if (h.kind != Hazard.Kind.EYE || h.dead || !inView(h.x, h.y)) continue;
            // The fully-open frame is reserved for the actual trigger, not the warning.
            int frame = Math.min(2, (int) (MathUtils.clamp(h.progress(), 0f, 1f) * 3f));
            list.add(new Sorted(h.y, () -> drawEye(h.x, h.y, h.radius, frame, 1f)));
        }
        for (float[] e : eyeFlashes) {
            if (!inView(e[0], e[1])) continue;
            list.add(new Sorted(e[1], () -> drawEye(e[0], e[1], e[2], 3, Math.min(1f, (0.45f - e[3]) / 0.18f))));
        }
        for (float[] pd : g.state.potionDrops) {
            if (!inView(pd[0], pd[1])) continue;
            list.add(new Sorted(pd[1], () -> drawPotionDrop(pd[0], pd[1])));
        }
        HeroActor h = g.hero;
        list.add(new Sorted(h.y, this::drawHero));
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            list.add(new Sorted(m.y, () -> drawMonster(m)));
        }
        for (Corpse c : corpses) list.add(new Sorted(c.y, () -> drawCorpse(c)));
    }

    /** Original-size fallback sheets remain usable; display atlases preserve source detail. */
    private TextureRegion storyFrame(String name, String fallback, int frames, int frame) {
        String high = "sprites/story/" + name + ".png";
        return SHEET_EXISTS.computeIfAbsent(high, k -> Gdx.files.internal(k).exists())
                ? art.cell(high, frames, 1, frame) : art.frame(fallback, frames, frame);
    }

    // Adult visible height ~58px, matching the hero; the original child proportions remain smaller.
    private static final float VILLAGER_SCALE = 1.65f;

    /**
     * 마을 사람 (서 있는 두 자세, 수정 속).
     * 마왕 전에는 눈을 감은 채 보랏빛 수정 속에 갇혀 있고, 뒤에는 수정이 사라지고 웃다가 팔을 들고 뛴다.
     */
    private void drawVillager(com.yongsa.config.Balance.Villager v, float x, float y) {
        String path = "team/sunwoo/villager_" + v.kind + ".png";
        float phase = x * 0.031f;
        float k = VILLAGER_SCALE, w = 32 * k, h = 40 * k;
        drawShadow(x, y, 22 * k);
        if (!g.villagersFree()) {
            TextureRegion f = storyFrame("villager_" + v.kind, path, 3, 2);
            if (f != null) {
                batch.setColor(0.82f, 0.78f, 1f, 1f);
                batch.draw(f, x - w / 2f, y - 2 * k, w, h);
            }
            TextureRegion c = storyFrame("crystal_prison", "team/sunwoo/crystal_prison.png", 2, (int) ((time + phase) / 0.6f));
            if (c != null) {
                batch.setColor(1, 1, 1, 0.9f + 0.1f * MathUtils.sin(time * 2f + phase * 10));
                batch.draw(c, x - 22 * k, y - 6 * k, 44 * k, 60 * k);
            }
        } else {
            float t = (time + phase * 7) % 4.8f;
            float p = HeroMotion.clamp((t - 3.8f) / 1f);
            float cheer = HeroMotion.smooth(p / .16f) * (1 - HeroMotion.smooth((p - .8f) / .2f));
            float hop = MathUtils.sin(p * MathUtils.PI);
            hop *= hop * 3;
            TextureRegion f = storyFrame("villager_" + v.kind, path, 3, 0);
            TextureRegion raised = storyFrame("villager_" + v.kind, path, 3, 1);
            if (f != null) {
                batch.setColor(Color.WHITE);
                spriteBlend.draw(batch, f, raised, cheer, x - w / 2f, y - 2 * k + hop, w, h);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /**
     * 바닥에 떨어진 회복 포션: 붉은 빛 위에 둥실 떠 있는 병. sprites/potion.png 그림이 있으면 그것을 쓰고,
     * 없으면 임시로 코드로 그린 병 (docs/ART_REQUESTS.md 0-7).
     */
    private void drawPotionDrop(float x, float y) {
        float bob = 3f * MathUtils.sin(time * 3f + x * 0.05f);
        batch.setColor(1f, 0.25f, 0.3f, 0.35f + 0.1f * MathUtils.sin(time * 4f));
        batch.draw(art.glow, x - 26, y - 14, 52, 40);
        batch.setColor(0, 0, 0, 0.35f);
        batch.draw(art.glow, x - 10, y - 5, 20, 8);
        float by = y + 4 + bob;
        Texture pic = potionArt();
        if (pic != null) {
            batch.setColor(Color.WHITE);
            float h = 30, w = h * pic.getWidth() / pic.getHeight();
            batch.draw(pic, x - w / 2, by, w, h);
        } else {
            rectW(x - 3, by + 22, 6, 4, new Color(0.55f, 0.38f, 0.22f, 1f));
            rectW(x - 2.5f, by + 17, 5, 6, new Color(0.75f, 0.8f, 0.85f, 0.9f));
            rectW(x - 8, by, 16, 18, new Color(0.75f, 0.8f, 0.85f, 0.9f));
            rectW(x - 7, by + 1, 14, 11, new Color(0.85f, 0.12f, 0.18f, 1f));
            rectW(x - 5, by + 13, 2, 4, new Color(1f, 1f, 1f, 0.5f));
        }
        batch.setColor(Color.WHITE);
    }

    private Texture potionTex;
    private boolean potionChecked;

    private Texture potionArt() {
        if (!potionChecked) {
            potionChecked = true;
            if (Gdx.files.internal("sprites/potion.png").exists()) {
                potionTex = new Texture(Gdx.files.internal("sprites/potion.png"), true);
                potionTex.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
            }
        }
        return potionTex;
    }

    private void rectW(float x, float y, float w, float h, Color c) {
        batch.setColor(c);
        batch.draw(art.white(), x, y, w, h);
    }

    /** Same 44x40 world footprint for all atlas resolutions and opening frames. */
    private void drawChest(String dungeonId, float x, float y, int frame, float alpha) {
        String name = "chest_" + dungeonId.toLowerCase(java.util.Locale.ROOT) + ".png";
        TextureRegion t = art.cell("sprites/rewards/" + name, 3, 1, frame);
        if (t == null) t = art.frame("team/sunwoo/" + name, 3, frame);
        if (t == null) return;
        float w = 44f, h = 40f;
        drawShadow(x, y, w * 0.9f * alpha);
        batch.setColor(1f, 1f, 1f, alpha);
        batch.draw(t, x - w / 2f, y - 4, w, h);
        if (frame == 0) {
            Particles.additive(batch);
            batch.setColor(1f, 0.9f, 0.5f, 0.05f + 0.04f * MathUtils.sin(time * 4f));
            batch.draw(t, x - w / 2f, y - 4, w, h);
            Particles.normal(batch);
        }
        batch.setColor(Color.WHITE);
    }

    private TextureRegion eyeRegion(int frame) {
        TextureRegion t = art.cell("sprites/rewards/demon_eye.png", 4, 1, frame);
        return t != null ? t : art.frame("sprites/demon_eye.png", 4, frame);
    }

    private void drawEye(float x, float y, float radius, int frame, float alpha) {
        TextureRegion t = eyeRegion(frame);
        if (t == null) return;
        float w = radius * 2.6f, h = w * 32f / 48f;
        batch.setColor(1f, 1f, 1f, MathUtils.clamp(alpha, 0f, 1f));
        batch.draw(t, x - w / 2f, y - h / 2f, w, h);
        batch.setColor(Color.WHITE);
    }

    private void drawShadow(float x, float y, float w) {
        batch.setColor(Color.WHITE);
        batch.draw(art.world("shadow"), x - w / 2f, y - w * 0.18f, w, w * 0.38f);
    }

    /** Broad low-contrast grounding, plus a smaller contact shadow under planted feet. */
    private void drawActorShadow(float x, float y, float width, float alpha, boolean flying) {
        TextureRegion shadow = art.world("shadow");
        float w = width * (flying ? .72f : 1.18f);
        batch.setColor(1, 1, 1, alpha * (flying ? .65f : .85f));
        batch.draw(shadow, x-w/2, y-w*.16f, w, w*.34f);
        if (!flying) {
            float contact = width*.58f;
            batch.setColor(1, 1, 1, alpha);
            batch.draw(shadow, x-contact/2, y-contact*.13f, contact, contact*.28f);
        }
        batch.setColor(Color.WHITE);
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

    private float veinX(Vein v) {
        return v.painted() ? v.spotX : (v.tileX + 0.5f) * T;
    }

    private float veinY(Vein v) {
        return v.painted() ? v.spotY : (v.tileY + 0.5f) * T;
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

    private boolean hasHeroMotion() {
        return heroMotionAvailable;
    }

    private TextureRegion motionFrame(String action, Facing facing, int frame) {
        TextureRegion authored = art.heroMotion(action, facing, frame);
        if (authored != null) return authored;
        if (!heroMotionAvailable) return null;
        return art.cell("sprites/hero-motion/" + action + ".png", 6, 3, HeroMotion.row(facing) * 6 + frame);
    }

    private TextureRegion heroRegion(HeroActor h) {
        TextureRegion animated = motionFrame("walk", h.facing,
                h.moving ? HeroMotion.walkFrame(h.walkTime, art.heroWalkCycle(h.facing)) : 2);
        if (animated != null) return animated;
        boolean walk = h.moving && ((int) (h.walkTime * 8)) % 2 == 1;
        String name;
        switch (HeroMotion.row(h.facing)) {
            case 1: name = "hero_up"; break;
            case 2: name = "hero_side"; break;
            default: name = "hero_down";
        }
        return art.world(walk ? name + "_walk" : name);
    }

    private void drawHero() {
        HeroActor h = g.hero;
        if (g.sleeping()) {
            drawSleepingHero(g.sleepBed());
            return;
        }
        drawActorShadow(h.x, h.y, 26 * HERO_SCALE, 1, false);
        if (h.rolling()) {
            drawRoll(h);
            return;
        }
        boolean blink = h.invuln > 0 && h.stunT <= 0 && ((int) (h.invuln * 20)) % 2 == 0;
        float alpha = blink ? 0.35f : 1f;
        boolean flip = h.facing.leftward();
        TextureRegion r = heroRegion(h);
        boolean motion = hasHeroMotion();
        float w = motion ? HeroMotion.WIDTH : art.width(r) * S * HERO_SCALE;
        float hgt = motion ? HeroMotion.HEIGHT : art.height(r) * S * HERO_SCALE;
        float foot = motion ? HeroMotion.FOOT_OFFSET : 2;
        boolean swordBehind = h.facing.sy > 0;
        // 휘두를 때 몸을 살짝 찌그러뜨려 힘이 들어간 느낌을 준다
        float squash = h.swingT > 0 && h.swingKind == 0
                ? 1f - .035f * MathUtils.sin(MathUtils.PI * HeroMotion.clamp(h.swingT / .18f)) : 1f;
        float bob = heroBob(h, r instanceof HeroPose);
        float dw = w * (2f - squash), dh = hgt * squash;
        if (swordBehind) drawSword(h, alpha, r, squash, bob);
        if (h.stunT > 0) batch.setColor(0.7f, 0.55f, 0.85f, alpha); // 눈알에 굳음: 잿빛 보라
        else batch.setColor(1f, 1f, 1f, alpha);
        // One discrete walk/idle pose: the sword uses this exact pose's hand anchor.
        actorInk.draw(batch, r, h.x + (flip ? dw/2 : -dw/2), h.y-foot*squash+bob, flip ? -dw : dw, dh);
        if (h.flash > 0 && !blink) {
            Particles.additive(batch);
            batch.setColor(1f, 0.3f, 0.3f, Math.min(1f, h.flash * 3f) * 0.6f);
            actorInk.draw(batch, r, h.x + (flip ? dw/2 : -dw/2), h.y-foot*squash+bob, flip ? -dw : dw, dh);
            Particles.normal(batch);
        }
        if (!swordBehind) drawSword(h, alpha, r, squash, bob);
        batch.setColor(Color.WHITE);
    }

    /**
     * 침대에 누운 용사. 그림(sprites/hero_sleep.png)이 오면 그걸 침대 가운데에 그리고,
     * 없으면 옆모습을 눕혀 이불을 덮어 둔다 (docs/ART_REQUESTS.md).
     */
    private void drawSleepingHero(com.yongsa.world.Prop bed) {
        float cx = Float.isNaN(bed.sleepX) ? bed.centerX(T) : bed.sleepX;
        float cy = Float.isNaN(bed.sleepY) ? bed.centerY(T) : bed.sleepY;
        if (Gdx.files.internal("sprites/hero_sleep.png").exists()) {
            TextureRegion r = storyFrame("hero_sleep", "sprites/hero_sleep.png", 1, 0);
            float w = 48 * HERO_SCALE, hgt = 56 * HERO_SCALE;
            batch.setColor(Color.WHITE);
            batch.draw(r, cx - w / 2f, cy - hgt / 2f, w, hgt);
            return;
        }
        // 임시: 앞모습을 베개에 눕히고 그림 속 이불 색으로 가슴 아래를 덮는다
        TextureRegion r = art.world("hero_down");
        float w = art.width(r) * S * HERO_SCALE, hgt = art.height(r) * S * HERO_SCALE;
        float breathe = 0.03f * MathUtils.sin(time * 2.4f);
        batch.setColor(Color.WHITE);
        batch.draw(r, cx - w / 2f, cy - hgt / 2f, w, hgt);
        float bw = w * 1.15f, bh = hgt * (0.55f + breathe);
        batch.setColor(62 / 255f, 88 / 255f, 172 / 255f, 1f);
        batch.draw(art.white(), cx - bw / 2f, cy - hgt / 2f - 2, bw, bh);
        batch.setColor(225 / 255f, 225 / 255f, 235 / 255f, 1f);
        batch.draw(art.white(), cx - bw / 2f, cy - hgt / 2f - 2 + bh - 3, bw, 3);
        batch.setColor(Color.WHITE);
    }

    /** Authored crouch/tuck/tumble/recovery poses; the roll follows travel, not auto-aim. */
    private void drawRoll(HeroActor h) {
        float p = HeroMotion.clamp(1f - h.rollT / h.rollTotal);
        TextureRegion r = motionFrame("roll", h.rollFacing, HeroMotion.rollFrame(p));
        TextureRegion upright = motionFrame("walk", h.rollFacing, 2);
        if (r == null || upright == null) {
            int row = HeroMotion.row(h.rollFacing);
            TextureRegion fallback = art.world(row == 1 ? "hero_up" : row == 0 ? "hero_down" : "hero_side");
            float w = art.width(fallback) * S * HERO_SCALE, height = art.height(fallback) * S * HERO_SCALE;
            boolean left = h.rollFacing.leftward();
            batch.setColor(Color.WHITE);
            actorInk.draw(batch, fallback, h.x + (left ? w/2 : -w/2), h.y - 2, left ? -w : w,
                    height * (1 - .23f * MathUtils.sin(p * MathUtils.PI)));
            return;
        }
        float weight = HeroMotion.rollWeight(p);
        float w = HeroMotion.WIDTH, height = HeroMotion.HEIGHT;
        boolean flip = h.rollFacing.leftward();
        float x = h.x + (flip ? w / 2 : -w / 2), signedW = flip ? -w : w;
        float y = h.y - HeroMotion.FOOT_OFFSET;
        batch.setColor(Color.WHITE);
        actorInk.draw(batch, upright, r, weight, x, y, signedW, height);
        // Hands are tucked/occluded during a roll; don't attach a sword to the walking pose.
        batch.setColor(Color.WHITE);
    }

    private void drawSword(HeroActor h, float alpha, TextureRegion pose, float squash, float bob) {
        Weapon w = g.state.equipped();
        if (w == null) return;
        HeroSword sword = pose instanceof HeroPose ? art.heroSword(w.defId, w.awakened) : null;
        TextureRegion r = sword != null ? sword : art.worldSword(w.defId, w.awakened);
        float aim = MathUtils.atan2(h.aimY, h.aimX) * MathUtils.radDeg;
        float rest = sword != null ? HeroMotion.attachedSwordAngle(h.facing) : HeroMotion.restAngle(h.facing);
        rest += MathUtils.sin(time * 2.5f) * 3f;
        float angle = rest;
        float scale = 1f;
        if (h.mineSwingT > 0) {
            float p = HeroMotion.clamp(1f - h.mineSwingT / .22f);
            angle = HeroMotion.swordAngle(rest, aim, p, 2);
        } else if (h.swingT > 0 && h.swingKind == 0) {
            float p = HeroMotion.clamp(1f - h.swingT / .18f);
            angle = HeroMotion.swordAngle(rest, aim, p, 0);
            scale = 1 + .15f * MathUtils.sin(p * MathUtils.PI);
        } else if (h.swingT > 0 && h.swingKind == 1) {
            float p = HeroMotion.clamp(1f - h.swingT / .25f);
            angle = HeroMotion.swordAngle(rest, aim, p, 1);
            scale = 1 + .3f * MathUtils.sin(p * MathUtils.PI);
        }
        batch.setColor(1f, 1f, 1f, alpha);
        if (sword != null && pose instanceof HeroPose heroPose) {
            var frame = heroPose.frame;
            if (!frame.handVisible()) return;
            var data = sword.data;
            float hx = h.x + frame.handOffsetX(h.facing.leftward(), squash);
            float hy = h.y + frame.handOffsetY(squash) + bob;
            batch.draw(sword, hx-data.originX(), hy-data.originY(), data.originX(), data.originY(),
                    data.worldWidth(), data.worldHeight(), scale, scale, angle-data.angleDegrees());
            return; // The painted sealed holy sword already includes its two bindings.
        }
        float hx = h.x + (h.facing.leftward() ? -8 : 8) * HERO_SCALE;
        float hy = h.y + 16 * HERO_SCALE + bob;
        float grip = 6 * HERO_SCALE;
        // 그림 속 칼날은 45도로 누워 있으므로 그만큼 빼고 돌린다. 손잡이(왼쪽 아래)를 회전 중심으로.
        batch.draw(r, hx - grip, hy - grip, grip, grip, 32 * HERO_SCALE, 32 * HERO_SCALE, scale, scale, angle - 45);
        if ("holy_sealed".equals(w.defId) && !art.hasHd("sword_holy") && h.swingT <= 0) {
            batch.setColor(1f, 1f, 1f, alpha * 0.8f);
            batch.draw(art.world("chain"), hx - 4 * HERO_SCALE, hy, 4 * HERO_SCALE, 4 * HERO_SCALE,
                    18 * HERO_SCALE, 18 * HERO_SCALE, 1f, 1f, angle - 45);
        }
    }

    private float heroBob(HeroActor h, boolean measuredWalk) {
        // The measured contact/passing poses already contain the rise of a step.
        // Large extra bobbing would lift the planted foot and look like hopping.
        float amplitude = measuredWalk ? .10f : .65f;
        return walkBlend * amplitude * (1 - MathUtils.cos(h.walkTime * MathUtils.PI2 * 4));
    }

    /** Species silhouette at rest: bat ~half hero height, imp ~three quarters, skeleton ~hero. */
    private float mobScale(String sprite) {
        if ("bat".equals(sprite)) return .70f;
        if ("imp".equals(sprite)) return 1.05f;
        if ("skeleton".equals(sprite)) return 1.30f;
        return DEFAULT_MOB_SCALE;
    }

    private float bossScale(String sprite) {
        return "golem".equals(sprite) ? 1.25f : 1.15f;
    }

    private float monsterScale(Monster m) {
        float presence = bossScale(m.def.sprite);
        if (m.state == Monster.State.PHASE_SHIFT) return S * (2f + .3f * m.progress()) * presence;
        return S * (m.finalBoss ? (m.phase2 ? 2.3f : 2f) * presence : m.boss ? 1.7f * presence : mobScale(m.def.sprite));
    }

    private float monsterBob(Monster m) {
        float bob = m.state == Monster.State.CHASE ? Math.abs(MathUtils.sin(m.walkTime * 9f)) * 3f : 0;
        if ("bat".equals(m.def.sprite)) bob = 6 + MathUtils.sin(time * 8f + m.x) * 4f;
        if (m.state == Monster.State.SLAM_TELEGRAPH || m.state == Monster.State.CAST_TELEGRAPH)
            bob += (m.state == Monster.State.SLAM_TELEGRAPH ? 10 : 4) * m.progress();
        return bob * (m.boss ? 1f : mobScale(m.def.sprite));
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
        if (m.boss && bossSheet(m.def.sprite, m.phase2) != null) return m.y + BOSS_BODY * sheetScale(m);
        return m.y - 2 + monsterBob(m) + art.height(art.world(m.def.sprite)) * monsterScale(m) * monsterStretchY(m);
    }

    /** Reserve the top HUD strip for a large boss, without zooming or losing the hero below. */
    public float cameraTargetY(float normalY, float viewHeight, float zoom) {
        Monster boss = g.activeBoss();
        if (boss == null || boss.dead) return normalY;
        // A steady envelope avoids making the camera bob with each animation frame.
        float top = boss.y + art.height(art.world(boss.def.sprite)) * monsterScale(boss) * 1.12f + 10;
        float required = top + 120 * zoom - viewHeight / 2;
        return MathUtils.clamp(required, normalY, normalY + viewHeight * .23f);
    }

    private void drawMonster(Monster m) {
        String sheet = m.boss ? bossSheet(m.def.sprite, m.phase2) : null;
        if (sheet != null) {
            drawBossSheet(m, sheet);
            return;
        }
        TextureRegion r = art.world(m.def.sprite);
        float scale = monsterScale(m);
        float appear = m.state == Monster.State.SPAWN ? Math.max(0f, (m.progress() - 0.35f) / 0.65f) : 1f;
        if (appear <= 0f) return;
        float w = art.width(r) * scale, hgt = art.height(r) * scale;
        drawActorShadow(m.x, m.y, w * 0.8f * appear, appear, "bat".equals(m.def.sprite));
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
        actorInk.draw(batch, r, mx + (flip ? dw/2 : -dw/2), m.y-2+bob, flip ? -dw : dw, dh);
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
        if (c.sheet != null) {
            // 쓰러지는 줄 8칸을 돌리고, 마지막 칸에서 천천히 사라진다
            TextureRegion r = bossCell(c.sheet, 4, (int) Math.min(7, p / 0.6f * 8));
            float size = BOSS_CELL * c.sheetScale;
            float x0 = c.x - size / 2f, y0 = c.y - (BOSS_CELL - BOSS_FOOT) * c.sheetScale;
            batch.setColor(1f, 1f, 1f, p < 0.7f ? 1f : 1f - (p - 0.7f) / 0.3f);
            if (c.sheetFaceRight) batch.draw(r, x0 + size, y0, -size, size);
            else batch.draw(r, x0, y0, size, size);
            batch.setColor(Color.WHITE);
            return;
        }
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
            float a = 0.12f * (1f - gh.t / 0.2f);
            batch.setColor(0.45f, 0.7f, 1f, a);
            actorInk.draw(batch, gh.region, gh.x + (gh.flip ? gh.w/2 : -gh.w/2),
                    gh.y-gh.footOffset, gh.flip ? -gh.w : gh.w, gh.h);
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
                float strength = g.state.finalBossDefeated ? .07f : .18f;
                batch.setColor(c.r, c.g, c.b, strength * sealPower() * pulse);
                batch.draw(art.glow, cx - 62, cy - 30, 124, 60);
                if (!g.state.unsealed) drawSealSockets(cx, cy, pulse);
            }
        }
        // 광맥 반짝임
        for (Room r : map.rooms) {
            if (r.type != RoomType.ORE) continue;
            List<Vein> veins = g.veinsOf(r);
            if (veins == null) continue;
            for (Vein v : veins) {
                float px = veinX(v), py = veinY(v);
                if (!inView(px, py)) continue;
                Color c = oreColor(v.ore);
                float tw = MathUtils.sin(time * 4f + v.tileX * 1.7f + v.tileY);
                float gs = v.painted() ? 84 : 52;
                batch.setColor(c.r, c.g, c.b, 0.25f + 0.15f * tw);
                batch.draw(art.glow, px - gs / 2, py - gs * 0.42f, gs, gs);
                if (tw > 0.85f) {
                    batch.setColor(1f, 1f, 1f, (tw - 0.85f) * 5f);
                    batch.draw(art.white(), px - 1 + (v.tileX % 3) * 4 - 4, py + 8, 2, 6);
                    batch.draw(art.white(), px - 3 + (v.tileX % 3) * 4 - 4, py + 10, 6, 2);
                }
            }
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
            float k = (m.phase2 || m.state == Monster.State.PHASE_SHIFT ? 1.4f : 1f) * bossScale(m.def.sprite);
            batch.setColor(0.6f, 0.2f, 1f, (0.35f + 0.1f * MathUtils.sin(time * 5f)) * k);
            batch.draw(art.glow, m.x - 90 * k, m.y - 20, 180 * k, 160 * k);
        }
        // 방패, 긴 패턴의 기운
        for (Monster m : g.monsters) {
            if (m.dead) continue;
            if (m.shield > 0) {
                float fr = m.shieldMax > 0 ? (float) m.shield / m.shieldMax : 1f;
                float r = m.hurtRadius * 2.6f * (m.boss ? bossScale(m.def.sprite) : mobScale(m.def.sprite));
                float pulse = 0.8f + 0.2f * MathUtils.sin(time * 4f);
                batch.setColor(SHIELD_COLOR.r, SHIELD_COLOR.g, SHIELD_COLOR.b, (0.18f + 0.25f * fr) * pulse);
                batch.draw(art.glow, m.x - r, m.y - r * 0.2f, r * 2, r * 1.9f);
            }
            if (m.state == Monster.State.CHANNEL) {
                Color c = "lightning".equals(m.channelKind) ? PROJ_SKY : "firespin".equals(m.channelKind) ? PROJ_FIRE
                        : "bloodpool".equals(m.channelKind) ? PROJ_BLOOD : new Color(1f, 0.6f, 0.35f, 1f);
                float r = m.hurtRadius * 2.4f * (m.boss ? bossScale(m.def.sprite) : mobScale(m.def.sprite)) * (0.9f + 0.1f * MathUtils.sin(time * 10f));
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
                float r = m.hurtRadius * 2.2f * (m.boss ? bossScale(m.def.sprite) : mobScale(m.def.sprite));
                batch.setColor(c.r, c.g, c.b, 0.55f * Math.min(1f, p * 2.5f));
                batch.draw(art.glow, m.x - r, m.y - r * 0.45f, r * 2, r * 0.9f);
            } else if (m.state == Monster.State.CAST_TELEGRAPH) {
                Color c = projectileColor(m.def.sprite);
                float p = m.progress();
                float sz = (m.boss ? 70 * bossScale(m.def.sprite) : 34 * mobScale(m.def.sprite)) * (0.4f + p);
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

    /** Painted skill bodies keep their color; only the surrounding light is additive. */
    private void drawSkillShots() {
        Particles.normal(batch);
        for (HeroShot h : g.shots) {
            if (h.dead) continue;
            if (h.strike()) {
                float p = MathUtils.clamp(h.age / Math.max(0.01f, h.age + h.delay), 0, 1);
                Color c = skillColor(h.kind);
                batch.setColor(c.r, c.g, c.b, 0.25f + 0.35f * p);
                batch.draw(art.glow, h.x - h.radius, h.y - h.radius * 0.6f,
                        h.radius * 2, h.radius * 1.2f);
                continue;
            }
            float speed = (float) Math.hypot(h.vx, h.vy);
            float ux = speed > 0.001f ? h.vx / speed : 1f;
            float uy = speed > 0.001f ? h.vy / speed : 0f;
            float angle = MathUtils.atan2(uy, ux) * MathUtils.radDeg;
            TextureRegion sprite = art.world("skill_" + h.kind);
            boolean ground = "cleave".equals(h.kind);
            float height = h.radius * (ground ? 1.1f : 2.2f);
            float width = height * sprite.getRegionWidth() / sprite.getRegionHeight();
            float alpha = Math.min(1f, h.age / 0.035f) * Math.min(1f, Math.max(0f, h.life) / 0.08f);
            // Trails never extend behind the position where the shot was born.
            for (int k = 2; k >= 1; k--) {
                float distance = Math.min(speed * h.age, h.radius * 0.32f * k);
                drawSkillSprite(sprite, h.x - ux * distance, h.y - uy * distance,
                        width, height, angle, alpha * (k == 1 ? 0.16f : 0.07f));
            }
            drawSkillSprite(sprite, h.x, h.y, width, height, angle, alpha);
        }
        TextureRegion lightning = art.world("skill_strike");
        for (float[] b : bolts) {
            float alpha = Math.max(0f, 1f - b[2] / 0.3f);
            float height = 300f;
            float width = height * lightning.getRegionWidth() / lightning.getRegionHeight();
            batch.setColor(1, 1, 1, alpha);
            // The impact star (not the center of the tall image) is the damage position.
            batch.draw(lightning, b[0] - width * 0.477f, b[1] - height * 0.046f, width, height);
        }
        batch.setColor(Color.WHITE);
        Particles.additive(batch);
    }

    private void drawSkillSprite(TextureRegion sprite, float x, float y, float width, float height,
                                 float angle, float alpha) {
        float originX = width * 0.78f, originY = height * 0.5f;
        batch.setColor(1, 1, 1, alpha);
        batch.draw(sprite, x - originX, y - originY, originX, originY,
                width, height, 1, 1, angle);
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
            float w = Math.max(24, 30 * mobScale(m.def.sprite)), y = monsterTop(m) + 5;
            shapes.setColor(0, 0, 0, 0.7f);
            shapes.rect(m.x - w / 2 - 1, y - 1, w + 2, 6);
            shapes.setColor(0.9f, 0.25f, 0.2f, 1f);
            shapes.rect(m.x - w / 2, y, w * m.hp / (float) m.maxHp, 4);
        }
        shapes.end();
    }

    /** 잠 (선우 버전): 천천히 어두워졌다가 밝아지고, 침대 위로 Zzz 가 올라간다. */
    private void drawSleepOverlay(OrthographicCamera cam) {
        float p = g.sleepProgress();
        batch.setColor(0.01f, 0.01f, 0.04f, MathUtils.sin(p * MathUtils.PI) * 0.85f);
        batch.draw(art.white(), 0, 0, cam.viewportWidth, cam.viewportHeight);
        batch.setColor(Color.WHITE);
        com.yongsa.world.Prop bed = g.sleepBed();
        float bx = Float.isNaN(bed.sleepX) ? bed.centerX(T) : bed.sleepX;
        float by = (Float.isNaN(bed.sleepY) ? bed.centerY(T) : bed.sleepY) + 20;
        for (int i = 0; i < 3; i++) {
            float ph = (p * 3 + i / 3f) % 1f;
            BitmapFont f = i == 0 ? fonts.large : fonts.normal;
            outlined(f, i == 0 ? "Z" : "z", sx(cam, bx + 14 + ph * 22), sy(cam, by + 10 + ph * 40), SLEEP_Z, 1f - ph);
        }
    }

    private static final Color SLEEP_Z = new Color(0.85f, 0.9f, 1f, 1f);

    // ───────────────────────── 글자 (화면 좌표로 그려 글자가 흐려지지 않게 한다)

    private float sx(OrthographicCamera cam, float wx) {
        return (wx - viewX0) / cam.zoom;
    }

    private float sy(OrthographicCamera cam, float wy) {
        return (wy - viewY0) / cam.zoom;
    }

    private void drawTexts(OrthographicCamera cam) {
        if (g.sleeping()) drawSleepOverlay(cam);
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
            boolean friend = sp.speaker != null; // 용사·마을 사람은 금빛 테두리와 이름
            batch.setColor(friend ? 0.05f : 0.06f, friend ? 0.04f : 0.02f, friend ? 0.07f : 0.1f, 0.85f * a);
            batch.draw(art.white(), x, y, w, hgt);
            batch.draw(art.white(), x + w / 2f - 6, y - 8, 12, 8);
            Color edge = friend ? Ui.GOLD : PROJ_DARK;
            batch.setColor(edge.r, edge.g, edge.b, 0.9f * a);
            batch.draw(art.white(), x, y + hgt - 2, w, 2);
            batch.setColor(Color.WHITE);
            if (friend) {
                fonts.small.setColor(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, a);
                fonts.small.draw(batch, sp.speaker, x + 10, y + hgt + 20);
                fonts.small.setColor(Color.WHITE);
            }
            fonts.normal.setColor(friend ? 1f : 1f, friend ? 0.97f : 0.9f, friend ? 0.9f : 1f, a);
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
    private static final Color BONE = new Color(0.9f, 0.95f, 0.75f, 1f);
    private static final Color WHIP = new Color(0.85f, 0.35f, 0.7f, 1f);
    private static final Color EYE_RED = new Color(1f, 0.15f, 0.2f, 1f);
    private static final Color PROJ_SKY = new Color(0.4f, 0.8f, 1f, 1f);
    private static final Color PROJ_DARK = new Color(0.7f, 0.3f, 1f, 1f);
    private static final Color SPAWN_BLUE = new Color(0.4f, 0.6f, 1f, 1f);
    private static final Color SHIELD_COLOR = new Color(1f, 0.82f, 0.55f, 1f);

    @Override
    public void dispose() {
        lighting.dispose();
        for (Texture t : sceneTex.values()) t.dispose();
        sceneTex.clear();
        for (Texture t : frontTex.values()) if (t != null) t.dispose();
        frontTex.clear();
        sceneForegrounds.dispose();
        returnDoors.dispose();
        spriteBlend.dispose();
        actorInk.dispose();
        for (Texture t : gateTex.values()) if (t != null) t.dispose();
        gateTex.clear();
        if(roomGates!=null)roomGates.dispose();
        for (Texture[] ts : corridorSkin.values()) for (Texture t : ts) t.dispose();
        if (potionTex != null) potionTex.dispose();
        corridorSkin.clear();
        passages.dispose();
    }
}
