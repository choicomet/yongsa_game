package com.yongsa.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.maps.tiled.renderers.OrthogonalTiledMapRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.combat.Attack;
import com.yongsa.combat.Body;
import com.yongsa.combat.Boss;
import com.yongsa.combat.Effect;
import com.yongsa.combat.FloatingText;
import com.yongsa.combat.HeroUnit;
import com.yongsa.combat.Monster;
import com.yongsa.combat.Projectile;
import com.yongsa.model.Cutscene;
import com.yongsa.model.GameSession;
import com.yongsa.model.Weapon;
import com.yongsa.world.CameraFollow;
import com.yongsa.world.Door;
import com.yongsa.world.Gate;
import com.yongsa.world.Portal;
import com.yongsa.world.Room;
import com.yongsa.world.RoomState;
import com.yongsa.world.Vein;
import com.yongsa.world.WorldMap;

import java.util.ArrayList;
import java.util.List;

/**
 * 게임 화면 그리기 (2.5D, FR-맵-02).
 * 순서: 타일 → 바닥(문양·경고) → 문 → 그림자 → 물체(발밑 y가 큰 것부터) → 안개 → 공격 범위·연출 → 투사체 →
 * 컷신 → 화면 좌표 글자(피해 숫자).
 */
public class WorldRenderer implements Disposable {

    /** 게임 화면 크기(px). 1280×720 창에서 정확히 2배. */
    public static final float VIEW_W = 640, VIEW_H = 360;

    private final GameSession s;
    private final Assets assets;
    private final BitmapFont textFont;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final FitViewport viewport = new FitViewport(VIEW_W, VIEW_H, camera);
    public final CameraFollow follow = new CameraFollow(7f);
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final OrthogonalTiledMapRenderer mapRenderer;
    private final GlyphLayout layout = new GlyphLayout();
    private final List<Object> sortables = new ArrayList<>();
    private final Color tmp = new Color();
    private final Matrix4 screenMatrix = new Matrix4();
    private final Vector3 v3 = new Vector3();
    private float time;
    /** 최종 전장에 떨어진 직후 밝아지는 연출. */
    private float arenaFade;

    public WorldRenderer(GameSession session, Assets assets, BitmapFont textFont) {
        this.s = session;
        this.assets = assets;
        this.textFont = textFont;
        this.mapRenderer = new OrthogonalTiledMapRenderer(assets.tiledMap(), 1f, batch);
        snapCamera();
    }

    public void resize(int w, int h) {
        viewport.update(w, h);
        screenMatrix.setToOrtho2D(0, 0, w, h);
    }

    public void snapCamera() {
        follow.snapTo(s.unit.centerX(), s.unit.centerY() + 8);
    }

    public void startArenaFade() {
        arenaFade = 1f;
        snapCamera();
    }

    public void render(float delta) {
        time += delta;
        arenaFade = Math.max(0, arenaFade - delta * 0.8f);
        float tx = s.unit.centerX(), ty = s.unit.centerY() + 8;
        Boss boss = s.activeBoss();
        if (boss != null && boss.alive()) {
            // 보스전에서는 보스 쪽으로 조금 당겨서 둘 다 보이게
            // 보스 그림의 가운데를 기준으로 (큰 보스는 발밑보다 한참 위)
            float bossMid = boss.footY() + assets.region(boss.def.sprite).getRegionHeight() / 2f;
            float dx = boss.centerX() - tx, dy = bossMid - ty;
            float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
            float shift = Math.min(len * 0.5f, 115f);
            tx += dx / len * shift;
            ty += dy / len * shift;
        }
        follow.update(camera, tx, ty, delta, s.map.widthPx(), s.map.heightPx());

        Gdx.gl.glClearColor(0.055f, 0.047f, 0.078f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        mapRenderer.setView(camera);
        mapRenderer.render();

        blend();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawSigil();
        drawTelegraphs();
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawDoors();
        drawPortalsAndOrbs();
        collect();
        for (Object o : sortables) drawShadow(o);
        for (Object o : sortables) drawSortable(o);
        batch.setColor(Color.WHITE);
        batch.end();

        blend();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawFog();
        drawAttackShapes();
        drawEffects();
        shapes.end();

        batch.begin();
        drawProjectiles();
        drawBars();
        batch.setColor(Color.WHITE);
        batch.end();

        drawCutscene();
        drawScreenTexts();
    }

    private void blend() {
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private Rectangle view() {
        return new Rectangle(camera.position.x - VIEW_W / 2 - 64, camera.position.y - VIEW_H / 2 - 64, VIEW_W + 128, VIEW_H + 128);
    }

    // ------------------------------------------------------------------ 바닥: 캠프 문양, 보스 예고

    private void drawSigil() {
        float x = s.map.sigil.x, y = s.map.sigil.y;
        if (!view().contains(x, y)) return;
        Cutscene c = s.cutscene();
        float glow = c != null ? Math.max(c.sigilGlow(), c.swordGlow() * 0.3f) : 0f;
        boolean broken = s.progress.sealBroken();
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 2f);
        // 평소: 바닥에 희미하게 그려진 문양. 해방 중: 금빛으로 빛난다.
        float baseA = broken ? 0.35f + 0.15f * pulse : 0.22f;
        tmp.set(0.35f + 0.65f * glow, 0.3f + 0.6f * glow, 0.25f + 0.3f * glow, baseA + 0.6f * glow);
        if (glow > 0) {
            shapes.setColor(1f, 0.85f, 0.4f, 0.25f * glow);
            shapes.circle(x, y, 70 + 10 * pulse, 48);
        }
        shapes.setColor(tmp);
        ring(x, y, 58, 2.5f);
        ring(x, y, 44, 1.5f);
        ring(x, y, 20, 1.5f);
        for (int i = 0; i < 3; i++) {
            float a1 = MathUtils.PI2 * i / 3f + MathUtils.PI / 2, a2 = MathUtils.PI2 * (i + 1) / 3f + MathUtils.PI / 2;
            shapes.rectLine(x + 44 * MathUtils.cos(a1), y + 44 * MathUtils.sin(a1), x + 44 * MathUtils.cos(a2),
                    y + 44 * MathUtils.sin(a2), 1.5f);
        }
        for (int i = 0; i < 12; i++) {
            float a = MathUtils.PI2 * i / 12f;
            shapes.rectLine(x + 46 * MathUtils.cos(a), y + 46 * MathUtils.sin(a), x + 56 * MathUtils.cos(a), y + 56 * MathUtils.sin(a), 1.5f);
        }
        // 구슬 3개를 놓을 자리 (해방 뒤에는 박힌 구슬이 빛난다)
        String[] ids = {"left", "right", "top"};
        for (int i = 0; i < 3; i++) {
            float a = MathUtils.PI2 * i / 3f + MathUtils.PI / 2;
            float px = x + 44 * MathUtils.cos(a), py = y + 44 * MathUtils.sin(a);
            if (broken && (c == null || c.orbs() >= 1f)) {
                shapes.setColor(orbColor(ids[i], 0.5f + 0.3f * pulse));
                shapes.circle(px, py, 9, 20);
            }
            shapes.setColor(tmp);
            ring(px, py, 6, 1.5f);
        }
    }

    private void ring(float x, float y, float r, float w) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            float a1 = MathUtils.PI2 * i / n, a2 = MathUtils.PI2 * (i + 1) / n;
            shapes.rectLine(x + r * MathUtils.cos(a1), y + r * MathUtils.sin(a1), x + r * MathUtils.cos(a2), y + r * MathUtils.sin(a2), w);
        }
    }

    private Color orbColor(String dungeon, float a) {
        return switch (dungeon) {
            case "left" -> tmp.set(0.62f, 0.4f, 1f, a);
            case "right" -> tmp.set(1f, 0.28f, 0.38f, a);
            default -> tmp.set(0.45f, 0.88f, 1f, a);
        };
    }

    /** 보스 특수 공격 예고: 빨간 원(내려치기), 빨간 띠(돌진), 모이는 빛(사방 공격). */
    private void drawTelegraphs() {
        for (Monster m : s.activeMonsters()) {
            if (!(m instanceof Boss b)) continue;
            switch (b.pattern) {
                case SLAM_WARN -> {
                    float p = Math.min(1f, b.patternTime / b.def.slamWarning);
                    shapes.setColor(0.95f, 0.1f, 0.1f, 0.22f);
                    shapes.circle(b.targetX, b.targetY, b.def.slamRadius, 48);
                    shapes.setColor(1f, 0.2f, 0.1f, 0.35f);
                    shapes.circle(b.targetX, b.targetY, b.def.slamRadius * p, 48);
                }
                case CHARGE_WARN -> {
                    float p = Math.min(1f, b.patternTime / b.def.chargeWarning);
                    shapes.setColor(1f, 0.15f, 0.1f, 0.18f + 0.2f * p);
                    shapes.rectLine(b.centerX(), b.centerY(), b.centerX() + b.chargeDirX * 460, b.centerY() + b.chargeDirY * 460,
                            b.body.width + 8);
                }
                case NOVA_CHARGE -> {
                    float p = Math.min(1f, b.patternTime / 0.7f);
                    shapes.setColor(b.finalBoss ? 0.75f : 1f, 0.3f, b.finalBoss ? 1f : 0.4f, 0.35f * p);
                    shapes.circle(b.centerX(), b.centerY() + 20, 30 + 50 * (1 - p), 32);
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ 문·포탈·구슬

    private void drawDoors() {
        int ts = s.map.tileSize;
        TextureRegion door = assets.region("door"), gate = assets.region("gate");
        for (Door d : s.doors) if (d.locked()) fill(door, d.bounds, ts);
        for (Gate g : s.gates) if (!g.open()) fill(gate, g.bounds, ts);
    }

    private void fill(TextureRegion r, Rectangle b, int ts) {
        for (float y = b.y; y < b.y + b.height; y += ts)
            for (float x = b.x; x < b.x + b.width; x += ts) batch.draw(r, x, y, ts, ts);
    }

    private void drawPortalsAndOrbs() {
        for (Portal p : s.portals()) {
            if (!p.active()) continue;
            TextureRegion f = assets.frame("portal", time, 0.12f, 0, 3);
            batch.setColor(1, 1, 1, 0.9f);
            batch.draw(f, p.x - 24, p.y - 20, 48, 48);
        }
        batch.setColor(Color.WHITE);
        for (GameSession.OrbDrop o : s.orbDrops) {
            float bob = MathUtils.sin(time * 3f) * 3f;
            batch.draw(assets.region("orb_" + o.dungeon()), o.x() - 12, o.y() + 8 + bob, 24, 24);
        }
    }

    // ------------------------------------------------------------------ 정렬해서 그리는 것들

    private void collect() {
        sortables.clear();
        Rectangle v = view();
        sortables.add(s.unit);
        if (v.overlaps(s.map.bed)) sortables.add(s.map.bed);
        if (v.overlaps(s.map.forge)) sortables.add(s.map.forge);
        for (Room r : s.rooms) {
            if (!v.overlaps(r.bounds) || !s.visibility.room(r)) continue;
            for (Vein vein : r.veins) if (!vein.depleted()) sortables.add(vein);
            sortables.addAll(r.monsters);
        }
        if (s.demonKing != null) sortables.add(s.demonKing);
        sortables.sort((a, b) -> Float.compare(sortY(b), sortY(a)));
    }

    private float sortY(Object o) {
        if (o instanceof Body b) return b.footY();
        if (o instanceof Vein v) return v.y - 6;
        if (o instanceof Rectangle r) return r.y;
        return 0;
    }

    private void drawShadow(Object o) {
        TextureRegion sh = assets.region("shadow");
        if (o instanceof Body b) {
            if (b instanceof Monster m && m.state() == Monster.State.DYING) return;
            if (b == s.unit && s.cutscene() != null && s.cutscene().fall() > 0) return;
            float w = Math.max(b.body.width + 10, 18);
            batch.setColor(1, 1, 1, b instanceof Monster m && m.state() == Monster.State.SPAWNING ? m.stateTime() / Monster.SPAWN_TIME : 1f);
            batch.draw(sh, b.footX() - w / 2, b.footY() - 4, w, 9);
        } else if (o instanceof Vein v) {
            batch.setColor(Color.WHITE);
            batch.draw(sh, v.x - 14, v.y - 8, 28, 9);
        }
        batch.setColor(Color.WHITE);
    }

    private void drawSortable(Object o) {
        if (o instanceof HeroUnit h) drawHero(h);
        else if (o instanceof Monster m) drawMonster(m);
        else if (o instanceof Vein v) {
            float j = v.shake > 0 ? MathUtils.random(-1.5f, 1.5f) : 0;
            batch.setColor(Color.WHITE);
            batch.draw(assets.region("vein_" + v.ore), v.x - 16 + j, v.y - 10, 32, 32);
        } else if (o == s.map.bed) prop("bed", s.map.bed);
        else if (o == s.map.forge) prop("forge", s.map.forge);
    }

    private void prop(String name, Rectangle r) {
        TextureRegion t = assets.region(name);
        batch.setColor(Color.WHITE);
        batch.draw(t, r.x + (r.width - t.getRegionWidth()) / 2f, r.y - 4);
    }

    private void drawHero(HeroUnit h) {
        String sheet = switch (h.facing) {
            case UP -> "hero_up";
            case DOWN -> "hero_down";
            default -> "hero_side";
        };
        boolean flip = h.facing == HeroUnit.Facing.LEFT;
        TextureRegion f = h.moving ? assets.frame(sheet, h.animTime, 0.12f, 1, 2) : assets.frames(sheet)[0];
        Color c = tmp.set(Color.WHITE);
        if (h.invincible() && !h.dodging() && !h.dashing() && ((int) (time * 20)) % 2 == 0) c.a = 0.4f;
        if (h.flash > 0) c.set(1f, 0.45f, 0.45f, c.a);
        if (!s.hero.alive()) c.set(0.5f, 0.5f, 0.55f, 0.8f);
        float scale = 1f, x = h.footX(), y = h.footY() - 2;
        Cutscene cs = s.cutscene();
        if (cs != null && cs.fall() > 0) {
            // 갈라진 땅 속으로 떨어진다
            scale = 1f - 0.8f * cs.fall();
            c.a = 1f - cs.fall();
            y -= 30 * cs.fall();
        }
        // 회피·돌진 잔상
        if (h.dodging() || h.dashing()) {
            for (int i = 1; i <= 3; i++) {
                batch.setColor(0.6f, 0.85f, 1f, 0.25f / i);
                drawAt(f, x - h.dirX * 9 * i, y - h.dirY * 9 * i, flip, 1f);
            }
        }
        boolean weaponBehind = h.facing == HeroUnit.Facing.UP;
        if (weaponBehind) drawWeapon(h, c.a);
        batch.setColor(c);
        drawAt(f, x, y + (h.mineTime > 0 ? -1 : 0), flip, scale);
        if (!weaponBehind) drawWeapon(h, c.a);

        if (cs != null && cs.swordGlow() > 0 && cs.fall() == 0) {
            batch.setColor(1f, 0.95f, 0.7f, 0.5f * cs.swordGlow() * (0.7f + 0.3f * MathUtils.sin(time * 10)));
            TextureRegion w = assets.white();
            batch.draw(w, h.footX() - 2, h.footY() + 30, 4, 40 * cs.swordGlow());
        }
    }

    private void drawAt(TextureRegion f, float footX, float y, boolean flip, float scale) {
        float w = f.getRegionWidth() * scale, hgt = f.getRegionHeight() * scale;
        float x = Math.round(footX - w / 2);
        if (flip) batch.draw(f, x + w, Math.round(y), -w, hgt);
        else batch.draw(f, x, Math.round(y), w, hgt);
    }

    /** 손에 든 무기: 바라보는 방향으로 들고, 공격할 때 부채꼴로 휘두른다 (각성하면 그림이 바뀐다, FR-대장간-17). */
    private void drawWeapon(HeroUnit h, float alpha) {
        if (!s.hero.alive()) return;
        Cutscene cs = s.cutscene();
        if (cs != null && cs.fall() > 0) return;
        Weapon w = s.inventory.equipped();
        TextureRegion icon = assets.region(w.icon());
        float facingDeg = MathUtils.atan2Deg360(h.dirY, h.dirX);
        float angle = facingDeg;
        if (h.swingTime > 0 && h.swingKind == Attack.Kind.BASIC) {
            float p = 1f - h.swingTime / h.swingDuration;
            float sweep = h.swingArc;
            angle = MathUtils.atan2Deg360(h.swingDirY, h.swingDirX) + sweep / 2f - sweep * p;
        } else if (h.swingTime > 0 && h.swingKind == Attack.Kind.WHIRL) {
            angle = facingDeg + 360f * (1f - h.swingTime / h.swingDuration);
        } else if (h.mineTime > 0) {
            angle = facingDeg + 50 - 100 * (1f - h.mineTime / 0.2f);
        } else {
            angle = facingDeg - 40;
        }
        float hx = h.centerX() + MathUtils.cosDeg(angle) * 6, hy = h.centerY() + 10 + MathUtils.sinDeg(angle) * 4;
        batch.setColor(1, 1, 1, alpha);
        // 아이콘은 오른쪽 위(45°)를 향한 검, 손잡이는 왼쪽 아래 (6,6)
        batch.draw(icon, hx - 6, hy - 6, 6, 6, 28, 28, 1f, 1f, angle - 45f);
    }

    private void drawMonster(Monster m) {
        String sheet = m.def.sprite;
        TextureRegion f = assets.frame(sheet, m.animTime, m.moving ? 0.16f : 0.45f, 0, 1);
        Color c = tmp.set(Color.WHITE);
        float scaleY = 1f;
        switch (m.state()) {
            case SPAWNING -> {
                float t = Math.min(1f, m.stateTime() / Monster.SPAWN_TIME);
                c.a = t;
                scaleY = 0.4f + 0.6f * t;
            }
            case WINDUP -> {
                if (((int) (m.stateTime() * 14)) % 2 == 0) c.set(1f, 0.6f, 0.45f, 1f);
            }
            case DYING -> {
                float t = Math.min(1f, m.stateTime() / Monster.DYING_TIME);
                c.set(1f, 1f, 1f, 1f - t);
                scaleY = 1f - 0.5f * t;
            }
            default -> { }
        }
        if (m instanceof Boss b && b.phase2) c.mul(1f, 0.75f, 0.85f, 1f);
        if (m.buffed && m.state() != Monster.State.DYING) {
            // 보스의 힘이 깃든 잡몹: 붉은 기운
            batch.setColor(1f, 0.2f, 0.2f, 0.25f + 0.1f * MathUtils.sin(time * 5));
            batch.draw(assets.region("shadow"), m.footX() - 18, m.footY() - 5, 36, 12);
        }
        if (m.flash > 0) c.set(1f, 0.4f, 0.4f, c.a);
        batch.setColor(c);
        float w = f.getRegionWidth(), h = f.getRegionHeight();
        float x = Math.round(m.footX() - w / 2), y = Math.round(m.footY() - 3);
        if (m.faceLeft) batch.draw(f, x + w, y, -w, h * scaleY);
        else batch.draw(f, x, y, w, h * scaleY);
    }

    // ------------------------------------------------------------------ 안개

    private void drawFog() {
        if (s.inArena()) return;
        Rectangle v = view();
        int ts = s.map.tileSize;
        shapes.setColor(0.035f, 0.03f, 0.05f, 1f);
        for (Room r : s.rooms) {
            if (s.visibility.room(r)) continue;
            Rectangle b = r.bounds;
            if (!v.overlaps(b)) continue;
            shapes.rect(b.x - ts, b.y - ts, b.width + ts * 2, b.height + ts * 2);
        }
        for (WorldMap.CorridorDef c : s.map.corridors) {
            if (s.visibility.corridor(c) || !v.overlaps(c.bounds())) continue;
            Rectangle b = c.bounds();
            shapes.rect(b.x - ts, b.y - ts, b.width + ts * 2, b.height + ts * 2);
        }
    }

    // ------------------------------------------------------------------ 공격 범위 (공격할 때만)

    private void drawAttackShapes() {
        HeroUnit h = s.unit;
        if (h.swingTime <= 0) return;
        float a = h.swingTime / h.swingDuration;
        float cx = h.centerX(), cy = h.centerY() + 6;
        switch (h.swingKind) {
            case BASIC -> {
                shapes.setColor(1f, 1f, 0.92f, 0.28f * a);
                float dir = MathUtils.atan2Deg360(h.swingDirY, h.swingDirX);
                shapes.arc(cx, cy, h.swingRange, dir - h.swingArc / 2f, h.swingArc, 28);
            }
            case WHIRL -> {
                shapes.setColor(1f, 0.95f, 0.7f, 0.22f * a);
                shapes.circle(cx, cy, h.swingRange, 40);
            }
            default -> { }
        }
    }

    private void drawEffects() {
        for (Effect e : s.effects) {
            float p = e.progress(), a = 1f - p;
            switch (e.type) {
                case SHOCKWAVE -> {
                    shapes.setColor(0.65f, 0.45f, 1f, 0.45f * a);
                    float dir = MathUtils.atan2Deg360(e.dirY, e.dirX);
                    shapes.arc(e.x, e.y, e.radius * (0.4f + 0.6f * p), dir - e.arc / 2f, e.arc, 28);
                }
                case WHIRL -> {
                    shapes.setColor(1f, 1f, 1f, 0.5f * a);
                    ring(e.x, e.y, e.radius * (0.6f + 0.4f * p), 3f);
                }
                case DASH_TRAIL -> {
                    shapes.setColor(0.7f, 0.9f, 1f, 0.35f * a);
                    shapes.rectLine(e.x, e.y + 6, e.x + e.dirX * e.radius, e.y + 6 + e.dirY * e.radius, 18 * a + 2);
                }
                case LIGHTNING -> {
                    shapes.setColor(0.7f, 0.95f, 1f, a);
                    float px = e.x, py = e.y;
                    for (int i = 1; i <= 5; i++) {
                        float t = i / 5f;
                        float nx = MathUtils.lerp(e.x, e.x2, t) + (i < 5 ? MathUtils.random(-7f, 7f) : 0);
                        float ny = MathUtils.lerp(e.y, e.y2, t) + (i < 5 ? MathUtils.random(-7f, 7f) : 0);
                        shapes.rectLine(px, py, nx, ny, 2.5f);
                        px = nx;
                        py = ny;
                    }
                }
                case SLAM -> {
                    shapes.setColor(1f, 0.85f, 0.6f, 0.55f * a);
                    ring(e.x, e.y, e.radius * (0.8f + 0.3f * p), 4f);
                }
                case HIT -> {
                    shapes.setColor(1f, 1f, 0.8f, 0.8f * a);
                    for (int i = 0; i < 4; i++) {
                        float ang = 45 + 90 * i;
                        shapes.rectLine(e.x + MathUtils.cosDeg(ang) * 4, e.y + MathUtils.sinDeg(ang) * 4,
                                e.x + MathUtils.cosDeg(ang) * (6 + 10 * p), e.y + MathUtils.sinDeg(ang) * (6 + 10 * p), 2f);
                    }
                }
                case BOUNCE -> {
                    shapes.setColor(0.8f, 0.8f, 1f, 0.6f * a);
                    ring(e.x, e.y, 20 + 24 * p, 3f);
                }
                case ORB_BURST -> {
                    shapes.setColor(1f, 1f, 0.9f, 0.5f * a);
                    shapes.circle(e.x, e.y, 10 + 60 * p, 32);
                }
                case HEAL -> {
                    shapes.setColor(0.4f, 1f, 0.5f, 0.5f * a);
                    shapes.rect(e.x - 1.5f, e.y + 10 * p, 3, 9);
                    shapes.rect(e.x - 4.5f, e.y + 3 + 10 * p, 9, 3);
                }
                default -> { }
            }
        }
    }

    // ------------------------------------------------------------------ 투사체·체력바

    private void drawProjectiles() {
        for (Projectile p : s.projectiles) {
            batch.setColor(Color.WHITE);
            batch.draw(assets.region(p.sprite), p.x - 8, p.y - 8, 16, 16);
        }
    }

    private void drawBars() {
        TextureRegion px = assets.white();
        for (Monster m : s.activeMonsters()) {
            if (m.isBoss() || !m.alive()) continue;
            if (m.room != null && !s.visibility.room(m.room)) continue;
            TextureRegion f = assets.region(m.def.sprite);
            float top = m.footY() + f.getRegionHeight() + 2;
            if (m.hp() < m.maxHp()) {
                float w = 26, x = m.footX() - w / 2;
                batch.setColor(0, 0, 0, 0.7f);
                batch.draw(px, x - 1, top - 1, w + 2, 5);
                batch.setColor(m.buffed ? 1f : 0.9f, m.buffed ? 0.35f : 0.2f, 0.2f, 1f);
                batch.draw(px, x, top, w * m.hp() / (float) m.maxHp(), 3);
            }
        }
    }

    // ------------------------------------------------------------------ 컷신

    private void drawCutscene() {
        Cutscene c = s.cutscene();
        if (c == null && arenaFade <= 0) return;
        blend();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (c != null) {
            float x = s.map.sigil.x, y = s.map.sigil.y;
            // 구슬: 용사 머리 위로 떠올랐다가 문양의 세 자리로 박힌다
            float o = c.orbs();
            if (o > 0 && o < 1f) {
                String[] ids = {"left", "right", "top"};
                for (int i = 0; i < 3; i++) {
                    float a = MathUtils.PI2 * i / 3f + MathUtils.PI / 2;
                    float tx = x + 44 * MathUtils.cos(a), ty = y + 44 * MathUtils.sin(a);
                    float hx = s.unit.centerX() + MathUtils.cos(a + time * 4) * 18, hy = s.unit.centerY() + 52;
                    float px, py;
                    if (o < 0.5f) {
                        float t = o / 0.5f;
                        px = hx;
                        py = MathUtils.lerp(s.unit.centerY() + 20, hy, t);
                    } else {
                        float t = (o - 0.5f) / 0.5f;
                        t = t * t;
                        px = MathUtils.lerp(hx, tx, t);
                        py = MathUtils.lerp(hy, ty, t);
                    }
                    shapes.setColor(orbColor(ids[i], 0.35f));
                    shapes.circle(px, py, 12, 20);
                    shapes.setColor(orbColor(ids[i], 1f));
                    shapes.circle(px, py, 7, 20);
                }
            }
            // 땅이 갈라진다
            float cr = c.crack();
            if (cr > 0) {
                shapes.setColor(0.05f, 0.02f, 0.03f, 1f);
                MathUtils.random.setSeed(7);
                for (int i = 0; i < 9; i++) {
                    float ang = MathUtils.PI2 * i / 9f + MathUtils.random(-0.2f, 0.2f);
                    float px = x, py = y;
                    int segs = 5;
                    for (int k = 1; k <= segs; k++) {
                        float len = 110 * cr * k / segs;
                        float nx = x + MathUtils.cos(ang) * len + MathUtils.random(-8f, 8f);
                        float ny = y + MathUtils.sin(ang) * len * 0.7f + MathUtils.random(-6f, 6f);
                        shapes.rectLine(px, py, nx, ny, 5f * (1 - k / (float) (segs + 1)) + 1);
                        px = nx;
                        py = ny;
                    }
                }
                MathUtils.random.setSeed(System.nanoTime());
                shapes.setColor(1f, 0.5f, 0.25f, 0.35f * cr);
                shapes.circle(x, y, 22 * cr, 24);
            }
            float f = c.fall();
            if (f > 0) {
                shapes.setColor(0f, 0f, 0f, 1f);
                shapes.ellipse(x - 70 * f, y - 40 * f, 140 * f, 80 * f, 40);
            }
        }
        shapes.end();

        // 화면 전체 어둡게: 추락 끝 → 검은 화면, 전장 도착 → 밝아짐
        float dark = c != null ? Math.max(0, (c.fall() - 0.4f) / 0.6f) : arenaFade;
        if (dark > 0) {
            shapes.setProjectionMatrix(screenMatrix);
            blend();
            shapes.begin(ShapeRenderer.ShapeType.Filled);
            shapes.setColor(0, 0, 0, Math.min(1f, dark));
            shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            shapes.end();
        }
    }

    // ------------------------------------------------------------------ 화면 좌표 글자 (또렷하게)

    private void drawScreenTexts() {
        batch.setProjectionMatrix(screenMatrix);
        batch.begin();
        for (FloatingText t : s.texts) {
            Color c = switch (t.kind) {
                case DAMAGE -> Color.WHITE;
                case HERO_DAMAGE -> Color.SCARLET;
                case HEAL -> Color.LIME;
                case ORE -> Color.GOLD;
                case IMMUNE -> Color.LIGHT_GRAY;
                case INFO -> Color.SKY;
            };
            float alpha = 1f - Math.max(0f, (t.time - FloatingText.LIFE * 0.6f) / (FloatingText.LIFE * 0.4f));
            v3.set(t.x, t.y, 0);
            viewport.project(v3);
            float sx = v3.x, sy = v3.y;
            layout.setText(textFont, t.text);
            float iconW = t.icon != null ? 26 : 0;
            float x0 = Math.round(sx - (layout.width + iconW) / 2);
            if (t.icon != null) {
                batch.setColor(1, 1, 1, alpha);
                batch.draw(assets.region(t.icon), x0, sy - layout.height - 4, 24, 24);
            }
            textFont.setColor(c.r, c.g, c.b, alpha);
            textFont.draw(batch, t.text, x0 + iconW, Math.round(sy));
        }
        // 공격 예고 중인 몬스터 머리 위 "!"
        for (Monster m : s.activeMonsters()) {
            if (m.state() != Monster.State.WINDUP || (m.room != null && !s.visibility.room(m.room))) continue;
            TextureRegion f = assets.region(m.def.sprite);
            v3.set(m.footX(), m.footY() + f.getRegionHeight() + 8, 0);
            viewport.project(v3);
            textFont.setColor(Color.ORANGE);
            layout.setText(textFont, "!");
            textFont.draw(batch, "!", Math.round(v3.x - layout.width / 2), Math.round(v3.y + layout.height));
        }
        textFont.setColor(Color.WHITE);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    /** 세계 좌표 → 화면 좌표 (HUD 안내 위치 등에 쓴다). */
    public Vector3 project(float x, float y) {
        return viewport.project(new Vector3(x, y, 0));
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        mapRenderer.dispose();
    }

    public boolean hiddenRoom(Room r) {
        return !s.visibility.room(r) && r.state() != RoomState.CLEARED;
    }
}
