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
import com.yongsa.combat.Hazard;
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
 * <ol>
 *   <li>타일 → 바닥 문양 → 벽 장식·문·포탈 → 그림자 → 물체(발밑 y가 큰 것부터)</li>
 *   <li>빛 지도를 곱한다 (캠프는 보스를 처음 잡을 때마다 밝아진다)</li>
 *   <li>안개 → 예고·위험 지대·공격 범위·연출 → 투사체·체력바 → 컷신 → 화면 좌표 글자</li>
 * </ol>
 * 예고와 연출은 빛보다 나중에 그려서 어두운 곳에서도 또렷하게 보인다.
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
    private final LightMap lights;
    private final GlyphLayout layout = new GlyphLayout();
    private final List<Object> sortables = new ArrayList<>();
    private final List<WorldMap.DecorDef> flatDecor = new ArrayList<>(), standDecor = new ArrayList<>();
    private final Color tmp = new Color(), tmp2 = new Color(), ambientColor = new Color();
    private final Matrix4 screenMatrix = new Matrix4();
    private final Vector3 v3 = new Vector3();
    private float time;
    /** 최종 전장에 떨어진 직후 밝아지는 연출. */
    private float arenaFade;
    /** 지금 밝기 (천천히 따라간다). */
    private float ambient = -1;

    public WorldRenderer(GameSession session, Assets assets, BitmapFont textFont) {
        this.s = session;
        this.assets = assets;
        this.textFont = textFont;
        this.mapRenderer = new OrthogonalTiledMapRenderer(assets.tiledMap(), 1f, batch);
        this.lights = new LightMap(batch, assets.light(), camera, (int) VIEW_W, (int) VIEW_H);
        for (WorldMap.DecorDef d : s.map.decor) (d.flat() ? flatDecor : standDecor).add(d);
        snapCamera();
    }

    public void resize(int w, int h) {
        viewport.update(w, h);
        screenMatrix.setToOrtho2D(0, 0, w, h);
    }

    public void snapCamera() {
        follow.snapTo(s.unit.centerX(), s.unit.centerY() + 8);
        ambient = -1;
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
            // 보스전에서는 보스 쪽으로 조금 당겨서 둘 다 보이게 (큰 보스는 그림 가운데 기준)
            float bossMid = boss.footY() + assets.region(boss.sprite()).getRegionHeight() / 2f;
            float dx = boss.centerX() - tx, dy = bossMid - ty;
            float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
            float shift = Math.min(len * 0.5f, 150f);
            tx += dx / len * shift;
            ty += dy / len * shift;
        }
        follow.update(camera, tx, ty, delta, s.map.widthPx(), s.map.heightPx());
        float target = s.ambientLight();
        ambient = ambient < 0 ? target : ambient + (target - ambient) * Math.min(1f, delta * 1.6f);

        Gdx.gl.glClearColor(0.035f, 0.03f, 0.05f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        viewport.apply();
        mapRenderer.setView(camera);
        mapRenderer.render();

        blend();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawSigil();
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawFlatDecor();
        drawDoors();
        drawPortalsAndOrbs();
        collect();
        for (Object o : sortables) drawShadow(o);
        for (Object o : sortables) drawSortable(o);
        batch.setColor(Color.WHITE);
        batch.end();

        drawLights();
        viewport.apply();

        blend();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawFog();
        drawTelegraphs();
        drawHazards();
        drawAttackShapes();
        drawHeroStatus();
        drawEffects();
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawHazardSprites();
        drawBossExtras();
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
        return new Rectangle(camera.position.x - VIEW_W / 2 - 96, camera.position.y - VIEW_H / 2 - 96, VIEW_W + 192, VIEW_H + 192);
    }

    // ================================================================== 빛

    private void drawLights() {
        // 주변광: 캠프는 밤하늘 푸른빛, 던전은 테마색, 심연은 보랏빛
        float a = ambient;
        if (s.inArena()) ambientColor.set(a * 0.95f, a * 0.72f, a * 1.05f, 1);
        else if (s.inCamp() || s.overlapRoom() == null) ambientColor.set(a * 0.9f, a * 0.92f, a * 1.12f, 1);
        else {
            switch (s.overlapRoom().dungeon) {
                case "left" -> ambientColor.set(a * 1.0f, a * 0.9f, a * 0.8f, 1);
                case "right" -> ambientColor.set(a * 1.0f, a * 0.82f, a * 0.86f, 1);
                default -> ambientColor.set(a * 0.82f, a * 0.92f, a * 1.12f, 1);
            }
        }
        lights.begin(ambientColor);
        boolean dark = a < 0.95f;
        if (dark) {
            // 용사 둘레: 따뜻한 손불빛
            lights.light(s.unit.centerX(), s.unit.centerY() + 10, 150, 1f, 0.9f, 0.75f, 0.62f);
            for (WorldMap.DecorDef d : s.map.decor) {
                if (d.light() <= 0) continue;
                float flick = 0.86f + 0.1f * MathUtils.sin(time * 9f + d.pos().x * 0.37f) + 0.05f * MathUtils.sin(time * 23f + d.pos().y);
                int c = d.lightColor();
                float h = d.flat() ? 18 : 24;
                lights.light(d.pos().x, d.pos().y + h, d.light(), ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f,
                        (c & 255) / 255f, 0.8f * flick);
            }
        }
        for (Portal p : s.portals()) if (p.active()) lights.light(p.x, p.y + 4, 120, 0.6f, 0.5f, 1f, 0.9f);
        for (GameSession.OrbDrop o : s.orbDrops) {
            Color c = orbColor(o.dungeon(), 1f);
            lights.light(o.x(), o.y() + 18, 80, c, 0.9f);
        }
        for (Room r : s.rooms) {
            if (!s.visibility.room(r)) continue;
            for (Vein v : r.veins) {
                if (v.depleted()) continue;
                Color c = oreColor(v.ore);
                lights.light(v.x, v.y + 6, 46, c, 0.45f + 0.1f * MathUtils.sin(time * 2 + v.x));
            }
        }
        // 성검 문양
        Cutscene cs = s.cutscene();
        float sig = cs != null ? Math.max(cs.sigilGlow(), cs.swordGlow()) : s.progress.sealBroken() ? 0.35f : 0f;
        if (sig > 0) lights.light(s.map.sigil.x, s.map.sigil.y, 150, 1f, 0.85f, 0.5f, sig);
        if (cs != null && cs.crack() > 0) lights.light(s.map.sigil.x, s.map.sigil.y, 120, 1f, 0.45f, 0.2f, cs.crack());
        // 보스: 몸에서 은은한 빛 (어두워도 보이게)
        for (Monster m : s.activeMonsters()) {
            if (!(m instanceof Boss b) || !b.alive()) continue;
            float mid = b.footY() + assets.region(b.sprite()).getRegionHeight() * 0.45f;
            if (b.finalBoss) lights.light(b.centerX(), mid, 220, b.phase == 2 ? 0.55f : 0.9f, 0.3f, b.phase == 2 ? 0.9f : 0.35f, 0.55f);
            else lights.light(b.centerX(), mid, 170, 0.8f, 0.75f, 0.9f, 0.35f);
        }
        for (Projectile p : s.projectiles) {
            Color c = projectileColor(p.sprite);
            lights.light(p.x, p.y, 44, c, 0.75f);
        }
        for (Hazard h : s.hazards) {
            if (!h.visible()) continue;
            float at = h.activeTime();
            switch (h.type) {
                case LIGHTNING -> {
                    if (at >= 0) lights.light(h.x, h.y + 20, 190, 0.85f, 0.9f, 1f, 1.2f * (1 - at / Hazard.STRIKE_TIME));
                    else lights.light(h.x, h.y, 50, 1f, 1f, 0.6f, 0.4f);
                }
                case POISON_CLOUD -> lights.light(h.x, h.y + 10, h.radius * 1.8f, 0.35f, 0.95f, 0.3f,
                        0.4f * cloudAlpha(h));
                case TENTACLE -> lights.light(h.x, h.y, 70, 0.7f, 0.25f, 0.9f, 0.5f);
                case BONE_SPIKE -> lights.light(h.x, h.y, 56, 0.6f, 1f, 0.6f, h.warnProgress() >= 0 ? 0.35f : 0.2f);
            }
        }
        for (Effect e : s.effects) {
            float k = 1f - e.progress();
            switch (e.type) {
                case SLAM -> lights.light(e.x, e.y, e.radius * 1.6f, 1f, 0.7f, 0.4f, 0.8f * k);
                case BLOOD_BURST -> lights.light(e.x, e.y, e.radius * 1.4f, 1f, 0.25f, 0.25f, 0.8f * k);
                case LIGHTNING -> lights.light(e.x2, e.y2, 90, 0.7f, 0.9f, 1f, 0.9f * k);
                case ORB_BURST -> lights.light(e.x, e.y, 120, 1f, 1f, 0.85f, 0.9f * k);
                case WHIRL, SHOCKWAVE -> lights.light(e.x, e.y, 90, 1f, 0.95f, 0.8f, 0.45f * k);
                case PHASE_SHIFT -> lights.light(e.x, e.y, 260, 0.7f, 0.2f, 1f, 0.9f * MathUtils.sin(e.progress() * MathUtils.PI));
                default -> { }
            }
        }
        // 성검을 든 용사는 스스로 빛난다
        if (s.inventory.equipped().def().holy) lights.light(s.unit.centerX(), s.unit.centerY() + 16, 90, 1f, 0.95f, 0.7f, 0.4f);
        lights.end();
        viewport.apply();
        lights.apply();
    }

    private static float cloudAlpha(Hazard h) {
        float wp = h.warnProgress();
        if (wp >= 0) return wp * 0.6f;
        float at = h.activeTime();
        if (at > h.life) return Math.max(0, 1f - (at - h.life) / Hazard.FADE_TIME);
        return 1f;
    }

    private Color oreColor(String ore) {
        return switch (ore) {
            case "blackiron" -> tmp2.set(0.62f, 0.45f, 1f, 1f);
            case "bloodmoon" -> tmp2.set(1f, 0.3f, 0.35f, 1f);
            default -> tmp2.set(0.45f, 0.85f, 1f, 1f);
        };
    }

    private Color projectileColor(String sprite) {
        return switch (sprite) {
            case "fire_bolt" -> tmp2.set(1f, 0.55f, 0.2f, 1f);
            case "ice_chunk" -> tmp2.set(0.55f, 0.85f, 1f, 1f);
            case "bolt_green" -> tmp2.set(0.45f, 1f, 0.5f, 1f);
            case "bolt_purple" -> tmp2.set(0.75f, 0.35f, 1f, 1f);
            case "bolt_red" -> tmp2.set(1f, 0.3f, 0.3f, 1f);
            default -> tmp2.set(0.45f, 0.65f, 1f, 1f);
        };
    }

    // ================================================================== 바닥: 캠프 문양

    private void drawSigil() {
        float x = s.map.sigil.x, y = s.map.sigil.y;
        if (!view().contains(x, y)) return;
        Cutscene c = s.cutscene();
        float glow = c != null ? Math.max(c.sigilGlow(), c.swordGlow() * 0.3f) : 0f;
        boolean broken = s.progress.sealBroken();
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 2f);
        // 평소: 바닥에 희미하게 새겨진 문양. 해방 중: 금빛으로 빛난다.
        if (broken) tmp.set(0.85f, 0.7f, 0.35f, 0.45f + 0.2f * pulse);
        else tmp.set(0.16f, 0.11f, 0.08f, 0.55f);
        if (glow > 0) tmp.set(MathUtils.lerp(tmp.r, 1f, glow), MathUtils.lerp(tmp.g, 0.9f, glow), MathUtils.lerp(tmp.b, 0.55f, glow),
                Math.min(1f, tmp.a + 0.5f * glow));
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
        int n = Math.max(16, Math.min(64, (int) (r * 0.7f)));
        for (int i = 0; i < n; i++) {
            float a1 = MathUtils.PI2 * i / n, a2 = MathUtils.PI2 * (i + 1) / n;
            shapes.rectLine(x + r * MathUtils.cos(a1), y + r * MathUtils.sin(a1), x + r * MathUtils.cos(a2), y + r * MathUtils.sin(a2), w);
        }
    }

    private void ellipseRing(float x, float y, float rx, float ry, float w) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            float a1 = MathUtils.PI2 * i / n, a2 = MathUtils.PI2 * (i + 1) / n;
            shapes.rectLine(x + rx * MathUtils.cos(a1), y + ry * MathUtils.sin(a1), x + rx * MathUtils.cos(a2), y + ry * MathUtils.sin(a2), w);
        }
    }

    private Color orbColor(String dungeon, float a) {
        return switch (dungeon) {
            case "left" -> tmp.set(0.62f, 0.4f, 1f, a);
            case "right" -> tmp.set(1f, 0.28f, 0.38f, a);
            default -> tmp.set(0.45f, 0.88f, 1f, a);
        };
    }

    // ================================================================== 장식·문·포탈·구슬

    private TextureRegion decorFrame(WorldMap.DecorDef d) {
        TextureRegion[] f = assets.frames(d.sprite());
        if (f.length == 1) return f[0];
        float phase = (d.pos().x * 0.13f + d.pos().y * 0.07f) % 1f;
        return f[(int) ((time + phase) / 0.13f) % f.length];
    }

    private void drawFlatDecor() {
        Rectangle v = view();
        batch.setColor(Color.WHITE);
        for (WorldMap.DecorDef d : flatDecor) {
            if (!v.contains(d.pos())) continue;
            TextureRegion r = decorFrame(d);
            batch.draw(r, Math.round(d.pos().x - r.getRegionWidth() / 2f), Math.round(d.pos().y - 4));
        }
    }

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
            batch.setColor(1, 1, 1, 0.95f);
            batch.draw(f, p.x - 32, p.y - 24, 64, 64);
        }
        batch.setColor(Color.WHITE);
        for (GameSession.OrbDrop o : s.orbDrops) {
            float bob = MathUtils.sin(time * 3f) * 3f;
            batch.draw(assets.region("orb_" + o.dungeon()), o.x() - 12, o.y() + 8 + bob, 24, 24);
        }
    }

    // ================================================================== 정렬해서 그리는 것들

    private void collect() {
        sortables.clear();
        Rectangle v = view();
        sortables.add(s.unit);
        if (v.overlaps(s.map.bed)) sortables.add(s.map.bed);
        if (v.overlaps(s.map.forge)) sortables.add(s.map.forge);
        for (WorldMap.DecorDef d : standDecor) if (v.contains(d.pos())) sortables.add(d);
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
        if (o instanceof WorldMap.DecorDef d) return d.pos().y;
        return 0;
    }

    private void drawShadow(Object o) {
        TextureRegion sh = assets.region("shadow");
        if (o instanceof Body b) {
            if (b instanceof Monster m && m.state() == Monster.State.DYING) return;
            if (b == s.unit && s.cutscene() != null && s.cutscene().fall() > 0) return;
            float w = Math.max(b.body.width + 12, 20);
            if (b instanceof Boss boss) w = Math.max(w, assets.region(boss.sprite()).getRegionWidth() * 0.55f);
            batch.setColor(1, 1, 1, b instanceof Monster m && m.state() == Monster.State.SPAWNING ? m.stateTime() / Monster.SPAWN_TIME : 1f);
            batch.draw(sh, b.footX() - w / 2, b.footY() - 5, w, Math.max(10, w * 0.22f));
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
        } else if (o instanceof WorldMap.DecorDef d) {
            TextureRegion r = decorFrame(d);
            batch.setColor(Color.WHITE);
            batch.draw(r, Math.round(d.pos().x - r.getRegionWidth() / 2f), Math.round(d.pos().y - 3));
        } else if (o == s.map.bed) prop("bed", s.map.bed, 0);
        else if (o == s.map.forge) prop("forge", s.map.forge, 0.16f);
    }

    private void prop(String name, Rectangle r, float frameTime) {
        TextureRegion t = frameTime > 0 ? assets.frame(name, time, frameTime, 0, 9) : assets.region(name);
        batch.setColor(Color.WHITE);
        batch.draw(t, Math.round(r.x + (r.width - t.getRegionWidth()) / 2f), Math.round(r.y - 6));
    }

    private void drawHero(HeroUnit h) {
        String sheet = switch (h.facing) {
            case UP -> "hero_up";
            case DOWN -> "hero_down";
            default -> "hero_side";
        };
        boolean flip = h.facing == HeroUnit.Facing.LEFT;
        TextureRegion f = h.moving && !h.frozen() ? assets.frame(sheet, h.animTime, h.guarding ? 0.2f : 0.12f, 1, 2) : assets.frames(sheet)[0];
        Color c = tmp.set(Color.WHITE);
        if (h.invincible() && !h.dodging() && !h.dashing() && ((int) (time * 20)) % 2 == 0) c.a = 0.4f;
        if (h.frozen()) c.set(0.6f, 0.85f, 1f, c.a);
        else if (h.poison > 0 && ((int) (time * 4)) % 2 == 0) c.set(0.7f, 1f, 0.6f, c.a);
        else if (h.burn > 0 && ((int) (time * 5)) % 2 == 0) c.set(1f, 0.75f, 0.55f, c.a);
        if (h.flash > 0) c.set(1f, 0.45f, 0.45f, c.a);
        if (!s.hero.alive()) c.set(0.5f, 0.5f, 0.55f, 0.8f);
        float scale = 1f, x = h.footX(), y = h.footY() - 2;
        if (h.frozen()) x += MathUtils.sin(time * 40) * 0.6f;
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
        float alpha = c.a;
        boolean weaponBehind = h.facing == HeroUnit.Facing.UP;
        if (weaponBehind) drawWeapon(h, alpha);
        batch.setColor(c);
        drawAt(f, x, y + (h.mineTime > 0 ? -1 : 0), flip, scale);
        if (!weaponBehind) drawWeapon(h, alpha);

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

    /** 손에 든 무기: 바라보는 방향으로 들고, 공격할 때 부채꼴로 휘두른다. 막을 때는 가로로 든다. */
    private void drawWeapon(HeroUnit h, float alpha) {
        if (!s.hero.alive()) return;
        Cutscene cs = s.cutscene();
        if (cs != null && cs.fall() > 0) return;
        Weapon w = s.inventory.equipped();
        TextureRegion icon = assets.region(w.icon());
        float facingDeg = MathUtils.atan2Deg360(h.dirY, h.dirX);
        float angle;
        if (h.swingTime > 0 && h.swingKind == Attack.Kind.BASIC) {
            float p = 1f - h.swingTime / h.swingDuration;
            float sweep = h.swingArc;
            angle = MathUtils.atan2Deg360(h.swingDirY, h.swingDirX) + sweep / 2f - sweep * p;
        } else if (h.swingTime > 0 && h.swingKind == Attack.Kind.WHIRL) {
            angle = facingDeg + 360f * (1f - h.swingTime / h.swingDuration);
        } else if (h.mineTime > 0) {
            angle = facingDeg + 50 - 100 * (1f - h.mineTime / 0.2f);
        } else if (h.guarding) {
            angle = facingDeg + 90;
        } else {
            angle = facingDeg - 40;
        }
        float reach = h.guarding ? 9 : 6;
        float hx = h.centerX() + MathUtils.cosDeg(h.guarding ? facingDeg : angle) * reach,
                hy = h.centerY() + 10 + MathUtils.sinDeg(h.guarding ? facingDeg : angle) * 4;
        if (h.guarding) {
            hx -= MathUtils.cosDeg(angle) * 10;
            hy -= MathUtils.sinDeg(angle) * 6;
        }
        batch.setColor(1, 1, 1, alpha);
        // 아이콘은 오른쪽 위(45°)를 향한 검, 손잡이는 왼쪽 아래 (6,6)
        batch.draw(icon, hx - 6, hy - 6, 6, 6, 28, 28, 1f, 1f, angle - 45f);
    }

    private TextureRegion monsterFrame(Monster m) {
        TextureRegion[] f = assets.frames(m.sprite());
        if (m instanceof Boss b) {
            int pose = switch (b.pose) {
                case RUSH, CAST, HOWL_DOWN -> 2;
                case HOWL_UP -> 3;
                case TAIL_UP -> 4;
                default -> -1;
            };
            if (b.state() == Monster.State.WINDUP && f.length > 2 && !b.def.isRanged()) pose = 2;
            if (b.state() == Monster.State.ATTACK && f.length > 2) pose = 2;
            if (pose >= 0 && pose < f.length) return f[pose];
            return f[(int) (m.animTime / (m.moving ? 0.2f : 0.5f)) % Math.min(2, f.length)];
        }
        return f[(int) (m.animTime / (m.moving ? 0.16f : 0.45f)) % Math.min(2, f.length)];
    }

    private void drawMonster(Monster m) {
        TextureRegion f = monsterFrame(m);
        Color c = tmp.set(Color.WHITE);
        float scaleY = 1f, jitter = 0;
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
                float dur = m instanceof Boss b ? (b.finalBoss ? 2.6f : 1.1f) : Monster.DYING_TIME;
                float t = Math.min(1f, m.stateTime() / dur);
                c.set(1f, 1f - 0.4f * t, 1f - 0.4f * t, 1f - t);
                scaleY = m.isBoss() ? 1f - 0.25f * t : 1f - 0.5f * t;
                if (m.isBoss()) jitter = MathUtils.random(-2f, 2f) * (1 - t);
            }
            default -> { }
        }
        if (m instanceof Boss b) {
            if (b.transitioning()) {
                // 모습이 바뀌는 중: 깜빡이며 검붉게 일그러진다
                float p = b.transitionProgress();
                jitter = MathUtils.random(-3f, 3f) * (1 - Math.abs(p - 0.5f) * 2);
                if (((int) (time * (8 + 20 * p))) % 2 == 0) c.set(0.55f, 0.2f, 0.75f, 1f);
            }
            if (b.pose == Boss.Pose.CAST && ((int) (time * 10)) % 2 == 0) c.mul(1f, 0.9f, 1.15f, 1f);
        }
        if (m.buffed && m.state() != Monster.State.DYING) {
            // 보스의 힘이 깃든 잡몹: 붉은 기운
            batch.setColor(1f, 0.2f, 0.2f, 0.3f + 0.12f * MathUtils.sin(time * 5));
            batch.draw(assets.region("shadow"), m.footX() - 20, m.footY() - 6, 40, 13);
        }
        if (m.flash > 0) c.set(1f, 0.4f, 0.4f, c.a);
        batch.setColor(c);
        float w = f.getRegionWidth(), h = f.getRegionHeight();
        float x = Math.round(m.footX() - w / 2 + jitter), y = Math.round(m.footY() - 3);
        if (m instanceof Boss b && b.pose == Boss.Pose.SPIN) {
            // 화염 회전: 몸 전체가 빙글빙글
            batch.draw(f, x, y, w / 2, h * 0.4f, w, h, m.faceLeft ? -1 : 1, scaleY, b.spinAngle);
            return;
        }
        if (m.faceLeft) batch.draw(f, x + w, y, -w, h * scaleY);
        else batch.draw(f, x, y, w, h * scaleY);
    }

    // ================================================================== 안개

    private void drawFog() {
        if (s.inArena()) return;
        Rectangle v = view();
        int ts = s.map.tileSize;
        shapes.setColor(0.025f, 0.02f, 0.035f, 1f);
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

    // ================================================================== 예고 (보스 몸 기준)

    private void drawTelegraphs() {
        for (Monster m : s.activeMonsters()) {
            if (!(m instanceof Boss b) || !b.alive()) continue;
            float p = MathUtils.clamp(b.warnProgress, 0, 1);
            switch (b.warn) {
                case CIRCLE -> {
                    shapes.setColor(0.95f, 0.1f, 0.1f, 0.2f);
                    shapes.circle(b.warnX, b.warnY, b.warnRadius, 48);
                    shapes.setColor(1f, 0.25f, 0.1f, 0.35f);
                    shapes.circle(b.warnX, b.warnY, b.warnRadius * p, 48);
                    shapes.setColor(1f, 0.4f, 0.3f, 0.8f);
                    ring(b.warnX, b.warnY, b.warnRadius, 1.5f);
                }
                case LINE -> {
                    float ex = b.warnX + b.warnDirX * b.warnLength, ey = b.warnY + b.warnDirY * b.warnLength;
                    shapes.setColor(1f, 0.15f, 0.1f, 0.14f + 0.18f * p);
                    shapes.rectLine(b.warnX, b.warnY, ex, ey, b.body.width + 14);
                    shapes.setColor(1f, 0.35f, 0.2f, 0.55f);
                    float mx = MathUtils.lerp(b.warnX, ex, p), my = MathUtils.lerp(b.warnY, ey, p);
                    shapes.rectLine(b.warnX, b.warnY, mx, my, 3);
                }
                default -> { }
            }
            // 힘을 모으는 자세: 빛이 모인다
            if (b.currentPattern() != null) {
                switch (b.currentPattern()) {
                    case NOVA, SUMMON, BONE_SPIKE, TENTACLE -> {
                        float k = 0.5f + 0.5f * MathUtils.sin(time * 14);
                        float sh = assets.region(b.sprite()).getRegionHeight();
                        float hx = b.centerX(), hy = b.footY() + sh * 0.55f;
                        if ("boss_right".equals(b.sprite())) {
                            // 리치: 지팡이 끝
                            hx += b.faceLeft ? -30 : 30;
                            hy = b.footY() + sh * 0.86f;
                        } else if (b.finalBoss && b.phase == 1) {
                            hy = b.footY() + sh * 0.62f;
                        }
                        Color col = switch (b.currentPattern()) {
                            case SUMMON, BONE_SPIKE -> tmp.set(0.4f, 1f, 0.55f, 0.25f + 0.15f * k);
                            case TENTACLE -> tmp.set(0.6f, 0.2f, 0.85f, 0.25f + 0.15f * k);
                            default -> tmp.set(b.finalBoss ? 0.75f : 1f, 0.3f, b.finalBoss ? 1f : 0.4f, 0.3f + 0.15f * k);
                        };
                        col.a *= 0.7f;
                        shapes.setColor(col);
                        shapes.circle(hx, hy, 12 + 6 * k, 24);
                        col.a *= 0.5f;
                        shapes.setColor(col);
                        shapes.circle(hx, hy, 22 + 8 * k, 24);
                    }
                    default -> { }
                }
            }
        }
    }

    // ================================================================== 위험 지대 (바닥)

    private void drawHazards() {
        for (Hazard h : s.hazards) {
            if (!h.visible()) continue;
            float wp = h.warnProgress();
            switch (h.type) {
                case POISON_CLOUD -> drawCloud(h);
                case BONE_SPIKE, TENTACLE, LIGHTNING -> {
                    if (wp >= 0) {
                        Color base = switch (h.type) {
                            case BONE_SPIKE -> tmp.set(0.85f, 0.95f, 0.75f, 1f);
                            case TENTACLE -> tmp.set(0.65f, 0.2f, 0.85f, 1f);
                            default -> tmp.set(1f, 0.95f, 0.45f, 1f);
                        };
                        // 바닥 금: 바깥 고리 + 안쪽에서 차오르는 원
                        shapes.setColor(base.r, base.g, base.b, 0.16f + 0.1f * wp);
                        shapes.ellipse(h.x - h.radius, h.y - h.radius * 0.6f, h.radius * 2, h.radius * 1.2f, 36);
                        shapes.setColor(base.r, base.g, base.b, 0.35f);
                        shapes.ellipse(h.x - h.radius * wp, h.y - h.radius * 0.6f * wp, h.radius * 2 * wp, h.radius * 1.2f * wp, 36);
                        shapes.setColor(base.r, base.g, base.b, 0.85f);
                        ellipseRing(h.x, h.y, h.radius, h.radius * 0.6f, 1.5f);
                        if (h.type == Hazard.Type.BONE_SPIKE) {
                            // 갈라지는 바닥 틈
                            shapes.setColor(0.15f, 0.1f, 0.1f, 0.8f);
                            for (int i = 0; i < 5; i++) {
                                float a = i * 72 + h.x;
                                shapes.rectLine(h.x, h.y, h.x + MathUtils.cosDeg(a) * h.radius * wp * 0.8f,
                                        h.y + MathUtils.sinDeg(a) * h.radius * 0.5f * wp, 1.5f);
                            }
                        }
                    } else if (h.type == Hazard.Type.LIGHTNING) {
                        float k = 1f - h.activeTime() / Hazard.STRIKE_TIME;
                        shapes.setColor(1f, 1f, 0.8f, 0.5f * k);
                        shapes.ellipse(h.x - h.radius, h.y - h.radius * 0.6f, h.radius * 2, h.radius * 1.2f, 30);
                        drawBolt(h.x, h.y, h.x + MathUtils.sin(h.x) * 30, h.y + 280, 4f * k + 1, k, 1);
                        drawBolt(h.x, h.y, h.x + MathUtils.cos(h.y) * 40, h.y + 280, 2f * k, k * 0.7f, 2);
                    } else if (h.type == Hazard.Type.TENTACLE) {
                        drawTentacle(h);
                    } else {
                        // 뼈 가시 주변 먼지
                        float k = 1f - h.activeTime() / Hazard.STRIKE_TIME;
                        shapes.setColor(0.8f, 0.75f, 0.65f, 0.35f * k);
                        shapes.ellipse(h.x - h.radius * 1.2f, h.y - h.radius * 0.5f, h.radius * 2.4f, h.radius, 30);
                    }
                }
            }
        }
    }

    private void drawCloud(Hazard h) {
        float a = cloudAlpha(h);
        if (a <= 0) return;
        float r = h.radius;
        // 겹친 원 여러 개가 천천히 일렁인다
        for (int i = 0; i < 7; i++) {
            float ang = i * 51.4f + time * (i % 2 == 0 ? 20 : -14);
            float d = i == 0 ? 0 : r * 0.45f;
            float cx = h.x + MathUtils.cosDeg(ang) * d, cy = h.y + 8 + MathUtils.sinDeg(ang) * d * 0.55f;
            float rr = (i == 0 ? r * 0.75f : r * 0.55f) * (0.92f + 0.08f * MathUtils.sin(time * 2 + i));
            shapes.setColor(0.2f + 0.05f * (i % 3), 0.75f, 0.2f, 0.18f * a);
            shapes.ellipse(cx - rr, cy - rr * 0.65f, rr * 2, rr * 1.3f, 24);
        }
        shapes.setColor(0.55f, 1f, 0.4f, 0.5f * a);
        ellipseRing(h.x, h.y, r, r * 0.6f, 1.2f);
        // 올라오는 독 거품
        for (int i = 0; i < 6; i++) {
            float t = (time * 0.6f + i / 6f) % 1f;
            float bx = h.x + MathUtils.sin(i * 2.3f + h.x) * r * 0.6f, by = h.y + t * 40;
            shapes.setColor(0.6f, 1f, 0.5f, 0.5f * a * (1 - t));
            shapes.circle(bx, by, 2.2f, 8);
        }
    }

    private void drawBolt(float x1, float y1, float x2, float y2, float w, float a, int seed) {
        shapes.setColor(0.85f, 0.95f, 1f, a);
        float px = x2, py = y2;
        int n = 7;
        for (int i = 1; i <= n; i++) {
            float t = i / (float) n;
            float jitter = i < n ? MathUtils.sin(seed * 12.9f + i * 7.7f + time * 30) * 12 : 0;
            float nx = MathUtils.lerp(x2, x1, t) + jitter, ny = MathUtils.lerp(y2, y1, t);
            shapes.rectLine(px, py, nx, ny, w);
            px = nx;
            py = ny;
        }
    }

    private void drawTentacle(Hazard h) {
        float t = h.activeTime() / Hazard.STRIKE_TIME;
        float rise = t < 0.35f ? t / 0.35f : 1f;
        float fade = t > 0.6f ? 1f - (t - 0.6f) / 0.4f : 1f;
        float len = 70 * rise;
        int segs = 9;
        for (int i = 0; i < segs; i++) {
            float k = i / (float) segs;
            float sway = MathUtils.sin(time * 10 + k * 4 + h.x) * 8 * k;
            float cx = h.x + sway, cy = h.y + len * k;
            float r = 11 * (1 - k * 0.8f);
            shapes.setColor(0.25f + 0.2f * k, 0.08f, 0.3f + 0.15f * k, fade);
            shapes.circle(cx, cy, r, 14);
            if (i % 2 == 1) {
                shapes.setColor(0.85f, 0.6f, 0.9f, 0.8f * fade);
                shapes.circle(cx - r * 0.45f, cy, 1.6f, 6);
            }
        }
        shapes.setColor(0.1f, 0.03f, 0.12f, 0.6f * fade);
        shapes.ellipse(h.x - 16, h.y - 6, 32, 12, 16);
    }

    private void drawHazardSprites() {
        TextureRegion spike = assets.region("bone_spike");
        for (Hazard h : s.hazards) {
            if (h.type != Hazard.Type.BONE_SPIKE || h.activeTime() < 0) continue;
            float t = h.activeTime() / Hazard.STRIKE_TIME;
            float rise = Math.min(1f, t / 0.18f);
            float a = t > 0.65f ? 1f - (t - 0.65f) / 0.35f : 1f;
            batch.setColor(1, 1, 1, a);
            float w = spike.getRegionWidth(), hh = spike.getRegionHeight();
            // 땅에서 솟아오른다: 그림 위쪽부터 보이게 (아래 왼쪽 = 보이는 부분의 아래 끝)
            batch.draw(spike.getTexture(), h.x - w / 2, h.y - 4, w, hh * rise,
                    spike.getU(), spike.getV() + (spike.getV2() - spike.getV()) * rise, spike.getU2(), spike.getV());
        }
        batch.setColor(Color.WHITE);
    }

    /** 얼음 꼬리 준비: 꼬리 주변에 얼음 덩이가 맺힌다. */
    private void drawBossExtras() {
        TextureRegion ice = assets.region("ice_chunk");
        for (Monster m : s.activeMonsters()) {
            if (!(m instanceof Boss b) || b.pose != Boss.Pose.TAIL_UP) continue;
            for (int i = 0; i < 5; i++) {
                float a = time * 160 + i * 72;
                float x = b.tailX() + MathUtils.cosDeg(a) * 18, y = b.tailY() + MathUtils.sinDeg(a) * 10;
                batch.setColor(1, 1, 1, 0.9f);
                batch.draw(ice, x - 10, y - 10, 10, 10, 20, 20, 1, 1, a);
            }
        }
        batch.setColor(Color.WHITE);
    }

    // ================================================================== 공격 범위 (공격할 때만)·막기

    private void drawAttackShapes() {
        HeroUnit h = s.unit;
        if (h.guarding) {
            float dir = MathUtils.atan2Deg360(h.dirY, h.dirX);
            float k = h.blockFlash > 0 ? 1f : 0.6f;
            shapes.setColor(0.5f, 0.85f, 1f, (0.18f + 0.15f * h.guardRatio()) * k + (h.blockFlash > 0 ? 0.25f : 0));
            shapes.arc(h.centerX(), h.centerY() + 8, 28, dir - 80, 160, 20);
            // 방패막 테두리
            shapes.setColor(0.75f, 0.95f, 1f, 0.55f * k + (h.blockFlash > 0 ? 0.4f : 0));
            for (int i = 0; i < 8; i++) {
                float a1 = dir - 80 + 20 * i, a2 = a1 + 20;
                shapes.rectLine(h.centerX() + MathUtils.cosDeg(a1) * 28, h.centerY() + 8 + MathUtils.sinDeg(a1) * 28,
                        h.centerX() + MathUtils.cosDeg(a2) * 28, h.centerY() + 8 + MathUtils.sinDeg(a2) * 28, 1.6f);
            }
        }
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

    /** 용사 상태이상 연출: 빙결 얼음, 감전 불꽃, 화상 불씨, 중독 거품. */
    private void drawHeroStatus() {
        HeroUnit h = s.unit;
        if (!s.hero.alive()) return;
        float cx = h.centerX(), by = h.footY();
        if (h.frozen()) {
            shapes.setColor(0.65f, 0.9f, 1f, 0.45f);
            shapes.rect(cx - 13, by - 2, 26, 34);
            shapes.setColor(0.9f, 0.98f, 1f, 0.7f);
            shapes.rectLine(cx - 13, by + 32, cx + 13, by + 32, 1.5f);
            shapes.rectLine(cx - 8, by + 4, cx - 2, by + 24, 1.2f);
            shapes.rectLine(cx + 6, by + 8, cx + 10, by + 20, 1.2f);
        }
        if (h.shock > 0) {
            for (int i = 0; i < 3; i++) {
                float a = time * 400 + i * 120;
                float x1 = cx + MathUtils.cosDeg(a) * 10, y1 = by + 16 + MathUtils.sinDeg(a) * 12;
                if (((int) (time * 12 + i)) % 3 == 0) continue;
                shapes.setColor(1f, 0.95f, 0.4f, 0.9f);
                shapes.rectLine(x1, y1, x1 + MathUtils.random(-5f, 5f), y1 + MathUtils.random(-6f, 6f), 1.4f);
            }
        }
        if (h.burn > 0) {
            for (int i = 0; i < 4; i++) {
                float t = (time * 1.4f + i / 4f) % 1f;
                shapes.setColor(1f, 0.55f - 0.3f * t, 0.15f, 0.8f * (1 - t));
                shapes.circle(cx - 8 + i * 5 + MathUtils.sin(time * 6 + i) * 2, by + 6 + t * 30, 1.8f * (1 - t) + 0.6f, 6);
            }
        }
        if (h.poison > 0) {
            for (int i = 0; i < 3; i++) {
                float t = (time * 0.9f + i / 3f) % 1f;
                shapes.setColor(0.5f, 1f, 0.35f, 0.7f * (1 - t));
                shapes.circle(cx - 6 + i * 6, by + 26 + t * 14, 1.8f, 6);
            }
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
                case BLOOD_BURST -> {
                    shapes.setColor(0.85f, 0.08f, 0.15f, 0.35f * a);
                    shapes.circle(e.x, e.y, e.radius * (0.3f + 0.7f * p), 36);
                    shapes.setColor(1f, 0.3f, 0.35f, 0.8f * a);
                    ring(e.x, e.y, e.radius * (0.3f + 0.7f * p), 2.5f);
                    for (int i = 0; i < 8; i++) {
                        float ang = i * 45 + 20;
                        float d = e.radius * p;
                        shapes.circle(e.x + MathUtils.cosDeg(ang) * d, e.y + MathUtils.sinDeg(ang) * d * 0.7f, 2.5f * a + 0.5f, 8);
                    }
                }
                case BLOCK -> {
                    shapes.setColor(0.75f, 0.95f, 1f, 0.9f * a);
                    float dir = MathUtils.atan2Deg360(e.dirY, e.dirX);
                    for (int i = -2; i <= 2; i++) {
                        float ang = dir + i * 22;
                        shapes.rectLine(e.x, e.y, e.x + MathUtils.cosDeg(ang) * (6 + 14 * p), e.y + MathUtils.sinDeg(ang) * (6 + 14 * p), 2f);
                    }
                }
                case GUARD_BREAK -> {
                    shapes.setColor(1f, 0.55f, 0.25f, 0.9f * a);
                    for (int i = 0; i < 7; i++) {
                        float ang = i * 51.4f + 10;
                        float d = 6 + 22 * p;
                        shapes.rect(e.x + MathUtils.cosDeg(ang) * d - 1.5f, e.y + MathUtils.sinDeg(ang) * d - 1.5f, 3, 3);
                    }
                    ring(e.x, e.y, 10 + 16 * p, 2f);
                }
                case FREEZE -> {
                    if (p < 0.12f) {
                        shapes.setColor(0.8f, 0.95f, 1f, 0.7f * (1 - p / 0.12f));
                        ring(e.x, e.y, 14 + 120 * p, 2.5f);
                    }
                }
                case PHASE_SHIFT -> {
                    float k = MathUtils.sin(p * MathUtils.PI);
                    shapes.setColor(0.2f, 0.02f, 0.25f, 0.35f * k);
                    shapes.circle(e.x, e.y, e.radius * (0.6f + 0.4f * k), 48);
                    for (int i = 0; i < 3; i++) {
                        float rr = ((time * 90 + i * 60) % 180);
                        shapes.setColor(0.7f, 0.25f, 0.95f, 0.6f * k * (1 - rr / 180f));
                        ring(e.x, e.y, rr, 2f);
                    }
                }
                default -> { }
            }
        }
    }

    // ================================================================== 투사체·체력바

    private void drawProjectiles() {
        for (Projectile p : s.projectiles) {
            TextureRegion r = assets.region(p.sprite);
            float w = r.getRegionWidth(), h = r.getRegionHeight();
            float rot = switch (p.sprite) {
                case "ice_chunk" -> p.age * 540f;
                case "fire_bolt" -> p.angleDeg();
                default -> 0f;
            };
            batch.setColor(Color.WHITE);
            batch.draw(r, p.x - w / 2, p.y - h / 2, w / 2, h / 2, w, h, 1, 1, rot);
        }
    }

    private void drawBars() {
        TextureRegion px = assets.white();
        for (Monster m : s.activeMonsters()) {
            if (m.isBoss() || !m.alive()) continue;
            if (m.room != null && !s.visibility.room(m.room)) continue;
            TextureRegion f = assets.region(m.sprite());
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

    // ================================================================== 컷신

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

    // ================================================================== 화면 좌표 글자 (또렷하게)

    private void drawScreenTexts() {
        batch.setProjectionMatrix(screenMatrix);
        batch.begin();
        for (FloatingText t : s.texts) {
            Color c = switch (t.kind) {
                case DAMAGE -> Color.WHITE;
                case HERO_DAMAGE -> Color.SCARLET;
                case HEAL -> Color.LIME;
                case ORE -> Color.GOLD;
                case IMMUNE -> Color.ORANGE;
                case INFO -> Color.SKY;
                case POISON -> tmp2.set(0.55f, 1f, 0.35f, 1f);
                case BURN -> tmp2.set(1f, 0.6f, 0.25f, 1f);
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
            TextureRegion f = assets.region(m.sprite());
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
        lights.dispose();
    }

    public boolean hiddenRoom(Room r) {
        return !s.visibility.room(r) && r.state() != RoomState.CLEARED;
    }
}
