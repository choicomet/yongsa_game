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
        if (boss != null && boss.alive() && s.unit.tumble <= 0) {
            // 보스전에서는 보스 쪽으로 조금 당겨서 둘 다 보이게 (큰 보스는 그림 가운데 기준)
            float bossMid = boss.footY() + assets.region(boss.sprite()).getRegionHeight() / 2f;
            float dx = boss.centerX() - tx, dy = bossMid - ty;
            float len = Math.max(1f, (float) Math.sqrt(dx * dx + dy * dy));
            float shift = Math.min(len * 0.5f, 150f);
            tx += dx / len * shift;
            ty += dy / len * shift;
        }
        // 바람기둥에 날아가는 용사는 카메라가 빠르게 따라가 화면 밖으로 놓치지 않는다
        follow.update(camera, tx, ty, delta, s.map.widthPx(), s.map.heightPx(), s.unit.tumble > 0 ? 4f : 1f);
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
        drawBlindGlows();

        blend();
        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawFog();
        drawTelegraphs();
        drawHazards();
        drawAttackShapes();
        drawHeroStatus();
        drawEffects();
        drawDeathDust();
        drawVillageAir();
        drawWindStreaks();
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
        else if (s.inVillage()) {
            // 마을: 마왕 전에는 수정빛 보랏빛 저녁, 마왕 뒤에는 따뜻한 한낮
            if (s.villagersFree()) ambientColor.set(Math.min(1f, a * 1.04f), a, a * 0.94f, 1);
            else ambientColor.set(a * 0.9f, a * 0.78f, a * 1.18f, 1);
        }
        else if (s.inCamp() || s.overlapRoom() == null) ambientColor.set(a * 0.9f, a * 0.92f, a * 1.12f, 1);
        else {
            switch (s.overlapRoom().dungeon) {
                case "left" -> ambientColor.set(a * 1.0f, a * 0.9f, a * 0.8f, 1);
                case "right" -> ambientColor.set(a * 1.0f, a * 0.82f, a * 0.86f, 1);
                default -> ambientColor.set(a * 0.82f, a * 0.92f, a * 1.12f, 1);
            }
        }
        // 실명: 맵 전체가 거의 깜깜해지고, 용사 발밑 작은 빛만 남는다 (서서히 들어오고 빠진다)
        float blind = Math.min(1f, s.unit.blind / 0.4f);
        if (blind > 0) ambientColor.lerp(0.02f, 0.01f, 0.03f, 1f, blind);
        lights.begin(ambientColor);
        boolean dark = a < 0.95f || blind > 0;
        if (blind > 0) {
            lights.light(s.unit.centerX(), s.unit.centerY() + 10, MathUtils.lerp(150, 70, blind), 0.85f, 0.75f, 0.85f,
                    MathUtils.lerp(0.62f, 0.75f, blind));
        } else if (dark) {
            // 용사 둘레: 따뜻한 손불빛
            lights.light(s.unit.centerX(), s.unit.centerY() + 10, 150, 1f, 0.9f, 0.75f, 0.62f);
            for (WorldMap.DecorDef d : s.map.decor) {
                if (d.light() <= 0 || blind > 0.5f) continue;
                float flick = 0.86f + 0.1f * MathUtils.sin(time * 9f + d.pos().x * 0.37f) + 0.05f * MathUtils.sin(time * 23f + d.pos().y);
                int c = d.lightColor();
                float h = d.flat() ? 18 : 24;
                lights.light(d.pos().x, d.pos().y + h, d.light(), ((c >> 16) & 255) / 255f, ((c >> 8) & 255) / 255f,
                        (c & 255) / 255f, 0.8f * flick);
            }
        }
        for (Portal p : s.portals()) if (p.active()) lights.light(p.x, p.y + 4, 120, 0.6f, 0.5f, 1f, 0.9f);
        // 수정에 갇힌 마을 사람: 보랏빛이 숨 쉬듯 일렁인다
        if (!s.villagersFree() && blind <= 0.5f) {
            for (WorldMap.VillagerDef v : s.map.villagers) {
                lights.light(v.pos().x, v.pos().y + 24, 90, 0.75f, 0.5f, 1f, 0.55f + 0.15f * MathUtils.sin(time * 2f + v.pos().x));
            }
        }
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
            if (!(m instanceof Boss b) || !b.alive() || blind > 0.5f) continue;
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
                case SWAMP -> lights.light(h.x, h.y, h.radiusNow() * 1.6f, 0.4f, 0.6f, 0.25f, 0.3f * cloudAlpha(h));
                case EYE -> lights.light(h.x, h.y, 150, 0.8f, 1f, 0.4f, h.warnProgress() >= 0 ? 0.3f + 0.6f * h.warnProgress() : 1f);
                case CRACK -> lights.light(h.x, h.y, 50, 0.5f, 0.9f, 1f, h.used ? 0.1f : 0.6f);
                case VOID -> lights.light((h.x + h.x2) / 2, (h.y + h.y2) / 2, 140, 0.6f, 0.2f, 0.9f, 0.3f);
                case LASER -> lights.light((h.x + h.x2) / 2, (h.y + h.y2) / 2, 160, 1f, 0.2f, 0.25f, h.warnProgress() >= 0 ? 0.2f : 0.8f);
                case METEOR_FIELD -> {
                    if (h.warnProgress() >= 0) lights.light(h.x, h.y, 90, 1f, 0.85f, 0.4f, 0.8f);
                    else lights.light(h.area.x + h.area.width / 2, h.area.y + h.area.height / 2, 600, 1f, 0.6f, 0.2f,
                            Math.max(0, 1f - h.activeTime() / 0.8f));
                }
                case ABYSS_SIGIL -> lights.light(h.x, h.y, 90, 0.7f, 0.3f, 1f, 0.5f * cloudAlpha(h));
                case BONE_SPEAR -> lights.light((h.x + h.x2) / 2, (h.y + h.y2) / 2, 120, 0.9f, 0.85f, 0.7f,
                        h.warnProgress() >= 0 ? 0.25f : 0.5f);
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
                case THUNDER -> lights.light(e.x, e.y + 30, 300, 0.8f, 0.9f, 1f, 1.4f * k);
                case CHEST -> lights.light(e.x, e.y + 30, 160, 1f, 0.9f, 0.6f, 1.1f * k);
                case REND -> lights.light(e.x, e.y, 400, 0.8f, 0.3f, 1f, 0.9f * k);
                default -> { }
            }
        }
        for (com.yongsa.model.TreasureChest c : s.chests) {
            if (!c.opened) lights.light(c.x, c.y + 16, 70, 1f, 0.85f, 0.55f, 0.55f);
        }
        if (s.inArena()) {
            for (Rectangle r : s.rends) lights.light(r.x + r.width / 2, r.y + r.height / 2, Math.max(r.width, r.height) * 0.6f, 0.5f, 0.15f, 0.8f, 0.35f);
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

    /**
     * 실명 중: 어둠 속에서 리치(작은 리치 포함)의 눈과 날아오는 마법 탄만 붉은·보랏빛으로 빛난다.
     * 빛 지도를 곱한 뒤에 더해서(가산) 그리므로 어둠에 묻히지 않는다.
     */
    private void drawBlindGlows() {
        float blind = Math.min(1f, s.unit.blind / 0.4f);
        if (blind <= 0) return;
        TextureRegion glow = assets.light();
        batch.setProjectionMatrix(camera.combined);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        float pulse = 0.75f + 0.25f * MathUtils.sin(time * 6);
        for (Monster m : s.activeMonsters()) {
            if (!m.alive()) continue;
            float eyeY, eyeDx, size;
            if ("boss_right".equals(m.sprite())) { eyeY = 78; eyeDx = 3; size = 18; } // 혈월의 기사 투구 눈 (tools/team_art.py 그림)
            else if ("mob_minilich".equals(m.sprite())) { eyeY = 41; eyeDx = 3; size = 12; }
            else continue;
            float y = m.footY() - 3 + eyeY;
            for (int k = -1; k <= 1; k += 2) {
                float x = m.footX() + k * eyeDx;
                batch.setColor(1f, 0.15f, 0.35f, 0.9f * blind * pulse);
                batch.draw(glow, x - size / 2, y - size / 2, size, size);
                batch.setColor(1f, 0.6f, 0.8f, blind);
                batch.draw(glow, x - size / 6, y - size / 6, size / 3, size / 3);
            }
        }
        for (Projectile p : s.projectiles) {
            boolean red = p.fromBoss;
            batch.setColor(red ? 1f : 0.7f, red ? 0.15f : 0.25f, red ? 0.3f : 1f, 0.85f * blind);
            batch.draw(glow, p.x - 22, p.y - 22, 44, 44);
        }
        batch.end();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.setColor(Color.WHITE);
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
        String name = d.sprite();
        if (name == null) return null;
        TextureRegion[] f = assets.frames(name);
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
            if (r == null) continue;
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
        if (!s.sleeping()) sortables.add(s.unit);
        for (com.yongsa.model.TreasureChest c : s.chests) if (v.contains(c.x, c.y) && chestVisible(c)) sortables.add(c);
        if (v.overlaps(s.map.bed)) sortables.add(s.map.bed);
        if (v.overlaps(s.map.forge)) sortables.add(s.map.forge);
        for (WorldMap.DecorDef d : standDecor) if (v.contains(d.pos())) sortables.add(d);
        for (WorldMap.VillagerDef d : s.map.villagers) if (v.contains(d.pos())) sortables.add(d);
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
        if (o instanceof WorldMap.VillagerDef d) return d.pos().y;
        if (o instanceof com.yongsa.model.TreasureChest c) return c.y;
        return 0;
    }

    private void drawShadow(Object o) {
        TextureRegion sh = assets.region("shadow");
        if (o instanceof Body b) {
            if (b instanceof Monster m && m.state() == Monster.State.DYING) return;
            if (b == s.unit && s.cutscene() != null && s.cutscene().fall() > 0) return;
            if (b instanceof Boss boss && (boss.hidden() || boss.collapsed || boss.vanish > 0.9f)) return;
            if (b == s.unit && s.unit.tumble > 0) return;
            float w = Math.max(b.body.width + 12, 20);
            if (b instanceof Boss boss) w = Math.max(w, assets.region(boss.sprite()).getRegionWidth() * 0.55f);
            batch.setColor(1, 1, 1, b instanceof Monster m && m.state() == Monster.State.SPAWNING ? m.stateTime() / Monster.SPAWN_TIME : 1f);
            batch.draw(sh, b.footX() - w / 2, b.footY() - 5, w, Math.max(10, w * 0.22f));
        } else if (o instanceof Vein v) {
            batch.setColor(Color.WHITE);
            batch.draw(sh, v.x - 14, v.y - 8, 28, 9);
        } else if (o instanceof WorldMap.VillagerDef d) {
            batch.setColor(Color.WHITE);
            float w = s.villagersFree() ? 22 : 34;
            batch.draw(sh, d.pos().x - w / 2, d.pos().y - 5, w, 9);
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
            if (r == null) return;
            batch.setColor(Color.WHITE);
            batch.draw(r, Math.round(d.pos().x - r.getRegionWidth() / 2f), Math.round(d.pos().y - 3));
        } else if (o instanceof WorldMap.VillagerDef d) drawVillager(d);
        else if (o instanceof com.yongsa.model.TreasureChest c) drawChest(c);
        else if (o == s.map.bed) prop(s.sleeping() ? "bed_sleep" : "bed", s.map.bed, 0);
        else if (o == s.map.forge) prop("forge", s.map.forge, 0.16f);
    }

    /**
     * 마을 사람: 마왕 전에는 눈을 감은 채 보랏빛 수정 속에 갇혀 있고,
     * 마왕을 쓰러뜨린 뒤에는 수정이 사라지고 웃다가 팔을 들고 폴짝 뛴다.
     */
    private void drawVillager(WorldMap.VillagerDef d) {
        TextureRegion[] f = assets.frames("villager_" + d.kind());
        float x = d.pos().x, y = d.pos().y - 2;
        float phase = d.pos().x * 0.031f;
        if (!s.villagersFree()) {
            batch.setColor(0.82f, 0.78f, 1f, 1f);
            batch.draw(f[2], Math.round(x - f[2].getRegionWidth() / 2f), Math.round(y));
            TextureRegion c = assets.frame("crystal_prison", time + phase, 0.6f, 0, 1);
            batch.setColor(1, 1, 1, 0.9f + 0.1f * MathUtils.sin(time * 2f + phase * 10));
            batch.draw(c, Math.round(x - c.getRegionWidth() / 2f), Math.round(y - 4));
        } else {
            float t = (time + phase * 7) % 1.6f;
            boolean cheer = t > 0.8f;
            float hop = cheer ? Math.abs(MathUtils.sin((t - 0.8f) / 0.8f * MathUtils.PI * 2)) * 5 : 0;
            batch.setColor(Color.WHITE);
            TextureRegion r = f[cheer ? 1 : 0];
            batch.draw(r, Math.round(x - r.getRegionWidth() / 2f), Math.round(y + hop));
        }
        batch.setColor(Color.WHITE);
    }

    private boolean chestVisible(com.yongsa.model.TreasureChest c) {
        for (Room r : s.rooms) if (r.bounds.contains(c.x, c.y)) return s.visibility.room(r);
        return true;
    }

    /** 보스 보물상자 (던전 테마). 닫힘 → 뚜껑이 열리며 빛. */
    private void drawChest(com.yongsa.model.TreasureChest c) {
        TextureRegion[] f = assets.frames(c.sprite());
        int idx = !c.opened ? 0 : c.openTime < 0.25f ? 1 : 2;
        float bob = !c.opened ? 0 : (c.openTime < 0.25f ? MathUtils.random(-1f, 1f) : 0);
        TextureRegion r = f[Math.min(idx, f.length - 1)];
        batch.setColor(Color.WHITE);
        batch.draw(assets.region("shadow"), c.x - 22, c.y - 6, 44, 11);
        batch.draw(r, Math.round(c.x - r.getRegionWidth() / 2f + bob), Math.round(c.y - 3));
        if (!c.opened) {
            // 반짝이: 열어 달라고
            float tw = (time * 1.3f + c.x * 0.01f) % 1f;
            batch.setColor(1f, 0.95f, 0.7f, 1f - tw);
            batch.draw(assets.white(), c.x + 10 - tw * 4, c.y + 30 + tw * 8, 2, 2);
            batch.setColor(Color.WHITE);
        }
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
        if (h.tumble > 0) {
            // 바람기둥에 맞아 빙글빙글 구르며 날아간다
            float p = 1f - h.tumble / HeroUnit.TUMBLE_TIME;
            float lift = MathUtils.sin(p * MathUtils.PI) * 18;
            TextureRegion side = assets.frames("hero_side")[0];
            batch.setColor(c);
            float w = side.getRegionWidth(), hh = side.getRegionHeight();
            batch.draw(side, Math.round(x - w / 2), Math.round(y + lift), w / 2, hh / 2, w, hh, h.tumbleDir < 0 ? -1 : 1, 1,
                    -h.tumbleDir * p * 900f);
            return;
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
        if (h.tumble > 0) return;
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

    /**
     * 몬스터 프레임. 잡몹: 0·1 걷기, 2 공격 준비(무기를 치켜듦), 3 공격(휘두름).
     * 보스 (bosses.py 13프레임): 0·1 대기, 2 특수 동작, 3 위 포효, 4 꼬리, 5 공격 준비, 6 공격, 7 휘청·기절,
     * 8~10 쓰러짐, 11 뒷모습, 12 옆모습.
     */
    private TextureRegion monsterFrame(Monster m) {
        TextureRegion[] f = assets.frames(m.sprite());
        boolean recoverHit = m.state() == Monster.State.RECOVER && m.stateTime() < 0.2f;
        if (m instanceof Boss b && f.length >= 13) {
            if (b.state() == Monster.State.DYING) {
                float t = m.stateTime() / (b.finalBoss ? Boss.FINAL_DEATH_TIME : Boss.DEATH_TIME);
                return f[t < 0.3f ? 8 : t < 0.6f ? 9 : 10];
            }
            if (b.stagger > 0) return f[7];
            if (b.pose == Boss.Pose.SPIN) return f[spinFrame(b.spinAngle)];
            if (b.pose == Boss.Pose.BACK) return f[11];
            int pose = switch (b.pose) {
                case RUSH, CAST, HOWL_DOWN -> 2;
                case HOWL_UP -> 3;
                case TAIL_UP -> 4;
                default -> -1;
            };
            if (pose < 0 && b.state() == Monster.State.WINDUP) pose = 5;
            if (pose < 0 && (b.state() == Monster.State.ATTACK || recoverHit)) pose = 6;
            if (pose >= 0) return f[pose];
            return f[(int) (m.animTime / (m.moving ? 0.2f : 0.5f)) % 2];
        }
        if (m instanceof Boss) return f[(int) (m.animTime / 0.5f) % Math.min(2, f.length)];
        if (f.length >= 5 && m.blockTime > 0) return f[4];   // 해골 기사 막기 자세
        if (f.length >= 4) {
            if (m.state() == Monster.State.WINDUP) return f[2];
            if (m.state() == Monster.State.ATTACK || recoverHit) return f[3];
        }
        return f[(int) (m.animTime / (m.moving ? 0.16f : 0.45f)) % Math.min(2, f.length)];
    }

    /** 빙글빙글: 바라보는 각도(0 앞, 90 오른쪽, 180 뒤, 270 왼쪽)에 맞는 그림. */
    private static int spinFrame(float yaw) {
        float a = ((yaw % 360) + 360) % 360;
        if (a < 45 || a >= 315) return 0;
        if (a < 135 || a >= 225) return 12;
        return 11;
    }

    private static boolean spinFlip(float yaw) {
        float a = ((yaw % 360) + 360) % 360;
        return a >= 225 && a < 315;
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
                if (m instanceof Boss b) {
                    // 보스: 무너지는 그림(8→9→10)을 보여 주고 마지막에만 흐려진다
                    float dur = b.finalBoss ? Boss.FINAL_DEATH_TIME : Boss.DEATH_TIME;
                    float t = Math.min(1f, m.stateTime() / dur);
                    c.set(1f, 1f, 1f, t < 0.75f ? 1f : 1f - (t - 0.75f) / 0.25f);
                    if (t < 0.3f) jitter = MathUtils.random(-2.5f, 2.5f);
                } else {
                    float t = Math.min(1f, m.stateTime() / Monster.DYING_TIME);
                    c.set(1f, 1f - 0.4f * t, 1f - 0.4f * t, 1f - t);
                    scaleY = 1f - 0.5f * t;
                }
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
        float w = f.getRegionWidth(), h = f.getRegionHeight();
        float x = Math.round(m.footX() - w / 2 + jitter), y = Math.round(m.footY() - 3);
        if (m instanceof Boss b) {
            if (b.hidden()) return; // 군체로 흩어졌다
            if (b.collapsed) {
                // 뼈 무더기
                TextureRegion pile = assets.region("bones");
                batch.setColor(1, 1, 1, 1);
                batch.draw(pile, b.pileX - pile.getRegionWidth(), b.pileY - 4, pile.getRegionWidth() * 2, pile.getRegionHeight() * 2);
                return;
            }
            c.a *= 1f - b.vanish;
            if (b.cocoon) {
                // 바람의 고치: 공중에 떠 있고 반쯤 비친다
                y += 54 + MathUtils.sin(time * 2.2f) * 6;
                c.mul(0.85f, 1f, 0.95f, 0.8f);
            }
            if (b.headless && b.def.headSrcH > 0) {
                // 머리를 던진 몸통: 위쪽(머리)을 잘라 그린다
                int cut = b.def.headSrcY + b.def.headSrcH;
                TextureRegion body = new TextureRegion(f, 0, cut, (int) w, (int) h - cut);
                batch.setColor(c);
                if (m.faceLeft) batch.draw(body, x + w, y, -w, (h - cut) * scaleY);
                else batch.draw(body, x, y, w, (h - cut) * scaleY);
                drawThrownHead(b, f);
                return;
            }
        }
        batch.setColor(c);
        if (m instanceof Boss b && b.pose == Boss.Pose.SPIN) {
            // 화염 구슬: 제자리에서 빙글빙글 (앞 → 옆 → 뒤 → 옆), 몸이 살짝 들썩인다
            boolean fl = spinFlip(b.spinAngle);
            float hop = Math.abs(MathUtils.sinDeg(b.spinAngle * 2)) * 4;
            if (fl) batch.draw(f, x + w, y + hop, -w, h);
            else batch.draw(f, x, y + hop, w, h);
            return;
        }
        if (m.faceLeft) batch.draw(f, x + w, y, -w, h * scaleY);
        else batch.draw(f, x, y, w, h * scaleY);
    }

    /** 던진 머리: 몸에서 구석으로 포물선을 그리며 날아가 붉게 빛난다. */
    private void drawThrownHead(Boss b, TextureRegion frame) {
        TextureRegion head = new TextureRegion(frame, b.def.headSrcX, b.def.headSrcY, b.def.headSrcW, b.def.headSrcH);
        float t = b.headFly;
        float hx = MathUtils.lerp(b.headFromX, b.headX, t), hy = MathUtils.lerp(b.headFromY, b.headY, t) + MathUtils.sin(t * MathUtils.PI) * 60;
        float bob = t >= 1 ? MathUtils.sin(time * 3) * 2 : 0;
        float hw = head.getRegionWidth(), hh = head.getRegionHeight();
        batch.setColor(1f, 0.85f, 0.85f, 1f);
        batch.draw(head, hx - hw / 2, hy - hh / 2 + bob, hw / 2, hh / 2, hw, hh, 1, 1, t < 1 ? t * 720 : 0);
    }

    // ================================================================== 안개

    private void drawFog() {
        drawRends();
        drawZoneMasks();
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

    /**
     * 다른 맵 가리기 (2026-10-12 결정): 마을은 던전 밖으로 나갔을 때만, 마왕의 영역은 마왕과 싸울 때만 보인다.
     * 그 안에 있을 때는 바깥(던전)이 보이지 않는다.
     */
    private void drawZoneMasks() {
        Rectangle v = view();
        int ts = s.map.tileSize;
        shapes.setColor(0.035f, 0.03f, 0.05f, 1f);
        Rectangle village = grow(s.map.village, ts), arena = grow(s.map.arena, ts * 2);
        if (s.inVillage()) maskOutside(village, v);
        else if (v.overlaps(village)) shapes.rect(village.x, village.y, village.width, village.height);
        if (s.inArena()) maskOutside(arena, v);
        else if (v.overlaps(arena)) shapes.rect(arena.x, arena.y, arena.width, arena.height);
    }

    private static Rectangle grow(Rectangle r, float m) {
        return new Rectangle(r.x - m, r.y - m, r.width + m * 2, r.height + m * 2);
    }

    private void maskOutside(Rectangle r, Rectangle v) {
        float vx1 = v.x + v.width, vy1 = v.y + v.height, rx1 = r.x + r.width, ry1 = r.y + r.height;
        if (v.x < r.x) shapes.rect(v.x, v.y, r.x - v.x, v.height);
        if (vx1 > rx1) shapes.rect(rx1, v.y, vx1 - rx1, v.height);
        if (v.y < r.y) shapes.rect(r.x, v.y, r.width, r.y - v.y);
        if (vy1 > ry1) shapes.rect(r.x, ry1, r.width, vy1 - ry1);
    }

    /**
     * 공간 절단으로 찢겨 나간 곳: 별빛이 흐르는 어두운 공허 + 찢긴 가장자리의 보랏빛 이빨.
     * 예고 중에는 잘려 나갈 곳이 붉게 맥박치고 가장자리에 금이 번진다.
     */
    private void drawRends() {
        if (!s.inArena()) return;
        // 번지는 공허: 닿으면 피해를 입는 거리(찢길수록 넓어짐)를 보랏빛 아지랑이로
        float reach = s.rendReach();
        for (Rectangle r : s.rends) {
            for (int k = 3; k >= 1; k--) {
                float m = reach * k / 3f + 2 * MathUtils.sin(time * 3 + k);
                shapes.setColor(0.5f, 0.1f, 0.8f, 0.10f + 0.04f * (3 - k));
                shapes.rect(r.x - m, r.y - m, r.width + m * 2, r.height + m * 2);
            }
        }
        for (Rectangle r : s.rends) {
            shapes.setColor(0.02f, 0.0f, 0.05f, 1f);
            shapes.rect(r.x, r.y, r.width, r.height);
            MathUtils.random.setSeed((long) (r.x * 31 + r.y * 7 + r.width));
            for (int i = 0; i < (int) (r.width * r.height / 1400); i++) {
                float px = r.x + MathUtils.random(r.width), py = r.y + MathUtils.random(r.height);
                float tw = 0.4f + 0.6f * MathUtils.sin(time * 2.5f + i * 1.7f);
                shapes.setColor(0.75f, 0.7f, 1f, 0.5f * tw);
                shapes.rect(px, py, (i % 7 == 0) ? 2 : 1, (i % 7 == 0) ? 2 : 1);
            }
            MathUtils.random.setSeed(System.nanoTime());
            drawTornEdge(r, 1f);
        }
        Boss dk = s.demonKing;
        if (dk != null && dk.rendRect != null && dk.currentPattern() == com.yongsa.combat.BossPattern.SPACE_REND) {
            Rectangle r = dk.rendRect;
            float p = MathUtils.clamp(dk.warnProgress, 0, 1);
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * (8 + 14 * p));
            shapes.setColor(0.55f, 0.05f, 0.35f, 0.12f + 0.22f * p + 0.08f * pulse);
            shapes.rect(r.x, r.y, r.width, r.height);
            drawTornEdge(r, p);
        }
    }

    /** 찢긴 경계선: 남은 전장 쪽 가장자리를 따라 들쭉날쭉한 보랏빛 이빨. */
    private void drawTornEdge(Rectangle r, float k) {
        Rectangle f = s.field();
        float fcx = f.x + f.width / 2, fcy = f.y + f.height / 2;
        int n = 18;
        shapes.setColor(0.75f, 0.3f, 1f, 0.9f * k);
        if (r.height >= r.width) {
            float ex = Math.abs(r.x + r.width - fcx) < Math.abs(r.x - fcx) ? r.x + r.width : r.x;
            float dir = ex == r.x ? 1 : -1;   // 이빨은 남은 전장 쪽으로
            for (int i = 0; i < n; i++) {
                float y0 = MathUtils.lerp(r.y, r.y + r.height, i / (float) n), y1 = MathUtils.lerp(r.y, r.y + r.height, (i + 1) / (float) n);
                float tooth = (6 + 6 * MathUtils.sin(i * 2.3f + time * 3)) * k;
                shapes.triangle(ex, y0, ex, y1, ex - dir * tooth, (y0 + y1) / 2);
            }
            shapes.rectLine(ex, r.y, ex, r.y + r.height, 2f * k + 0.5f);
        } else {
            float ey = Math.abs(r.y + r.height - fcy) < Math.abs(r.y - fcy) ? r.y + r.height : r.y;
            float dir = ey == r.y ? 1 : -1;
            for (int i = 0; i < n; i++) {
                float x0 = MathUtils.lerp(r.x, r.x + r.width, i / (float) n), x1 = MathUtils.lerp(r.x, r.x + r.width, (i + 1) / (float) n);
                float tooth = (6 + 6 * MathUtils.sin(i * 2.3f + time * 3)) * k;
                shapes.triangle(x0, ey, x1, ey, (x0 + x1) / 2, ey - dir * tooth);
            }
            shapes.rectLine(r.x, ey, r.x + r.width, ey, 2f * k + 0.5f);
        }
    }

    /** 쓰러지는 보스: 먼지·재가 피어오른다. */
    private void drawDeathDust() {
        for (Monster m : s.activeMonsters()) {
            if (!(m instanceof Boss b) || b.state() != Monster.State.DYING) continue;
            float dur = b.finalBoss ? Boss.FINAL_DEATH_TIME : Boss.DEATH_TIME;
            float t = Math.min(1f, b.stateTime() / dur);
            float w = assets.region(b.sprite()).getRegionWidth() * 0.5f;
            for (int i = 0; i < 26; i++) {
                float ph = (t * 1.6f + i * 0.137f) % 1f;
                float px = b.centerX() + MathUtils.sin(i * 12.7f) * w + MathUtils.sin(time * 2 + i) * 6;
                float py = b.footY() + ph * 70 + (i % 5) * 4;
                if (b.finalBoss) shapes.setColor(0.6f, 0.35f, 0.9f, 0.6f * (1 - ph) * Math.min(1, t * 3));
                else shapes.setColor(0.55f, 0.5f, 0.48f, 0.55f * (1 - ph) * Math.min(1, t * 3));
                shapes.circle(px, py, 2 + (i % 3), 6);
            }
        }
    }

    // ================================================================== 예고 (보스 몸 기준)

    private void drawTelegraphs() {
        for (Monster m : s.activeMonsters()) {
            if (!(m instanceof Boss b) || !b.alive()) continue;
            float bh = assets.region(b.sprite()).getRegionHeight();
            if (b.armor > 0) {
                // 뼈갑옷: 몸을 감싼 희뿌연 막 + 둘레를 도는 뼈 조각
                float k = b.armor / (float) Math.max(1, b.armorMax);
                float dim = 1f - 0.75f * Math.min(1f, s.unit.blind / 0.4f); // 실명 중에는 거의 안 보인다
                float cx = b.centerX(), cy = b.footY() + bh * 0.45f, rx = 46, ry = bh * 0.55f;
                shapes.setColor(0.92f, 0.88f, 0.75f, (0.06f + 0.06f * k + (b.flash > 0 ? 0.15f : 0)) * dim);
                shapes.ellipse(cx - rx, cy - ry, rx * 2, ry * 2, 36);
                shapes.setColor(0.95f, 0.92f, 0.8f, (0.45f + 0.3f * k) * dim);
                ellipseRing(cx, cy, rx, ry, 1.5f);
                int plates = 3 + Math.round(5 * k);
                for (int i = 0; i < plates; i++) {
                    float ang = time * 70 + i * 360f / plates;
                    float px = cx + MathUtils.cosDeg(ang) * (rx + 4), py = cy + MathUtils.sinDeg(ang) * (ry * 0.5f);
                    shapes.setColor(0.9f, 0.86f, 0.72f, 0.95f * dim);
                    shapes.rect(px - 4, py - 2, 4, 2, 8, 4, 1, 1, ang + 90);
                }
            }
            if (b.stagger > 0) {
                // 휘청: 머리 위를 도는 별
                float hy = b.footY() + bh + 4;
                for (int i = 0; i < 3; i++) {
                    float ang = time * 300 + i * 120;
                    shapes.setColor(1f, 0.92f, 0.4f, 0.9f);
                    shapes.circle(b.centerX() + MathUtils.cosDeg(ang) * 16, hy + MathUtils.sinDeg(ang) * 5, 2.5f, 8);
                }
            }
            float p = MathUtils.clamp(b.warnProgress, 0, 1);
            drawBossForms(b, bh);
            if (b.currentPattern() == com.yongsa.combat.BossPattern.WARP_TENTACLE && b.warn == Boss.Warn.LINE) {
                drawDecoyTentacle(b, p);
                continue;
            }
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

    /** 보스 몸에 붙은 큰 연출: 척추 채찍, 바람의 고치, 군체. */
    private void drawBossForms(Boss b, float bh) {
        float px = b.centerX(), py = b.footY() + 40;
        if (b.whipWarn) {
            float from = Math.min(b.whipFrom, b.whipTo), span = Math.abs(b.whipTo - b.whipFrom);
            float r = b.def.whipRadius;
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * 18);
            shapes.setColor(1f, 0.6f, 0.35f, 0.07f + 0.1f * b.warnProgress + 0.04f * pulse);
            shapes.arc(px, py, r, from, span, 48);
            shapes.setColor(1f, 0.9f, 0.6f, 0.9f);
            // 시작 쪽 가장자리와 바깥 원호
            shapes.rectLine(px, py, px + MathUtils.cosDeg(b.whipFrom) * r, py + MathUtils.sinDeg(b.whipFrom) * r, 2.5f);
            int n = 36;
            for (int i = 0; i < n; i++) {
                float a1 = from + span * i / n, a2 = from + span * (i + 1) / n;
                shapes.rectLine(px + MathUtils.cosDeg(a1) * r, py + MathUtils.sinDeg(a1) * r,
                        px + MathUtils.cosDeg(a2) * r, py + MathUtils.sinDeg(a2) * r, 2.5f);
            }
            // 휩쓸 방향 화살표 (시작 쪽에서 끝 쪽으로)
            float dirSign = Math.signum(b.whipTo - b.whipFrom);
            for (int i = 0; i < 3; i++) {
                float a = b.whipFrom + dirSign * span * (0.2f + 0.3f * i), rr = r * 0.6f;
                float ax = px + MathUtils.cosDeg(a) * rr, ay = py + MathUtils.sinDeg(a) * rr;
                float tx = MathUtils.cosDeg(a + 90 * dirSign) * 10, ty = MathUtils.sinDeg(a + 90 * dirSign) * 10;
                shapes.rectLine(ax - tx, ay - ty, ax + tx, ay + ty, 3);
            }
        }
        if (b.whipSweep) {
            float r = b.def.whipRadius;
            // 지나간 자리 잔상
            float from = Math.min(b.whipFrom, b.whipAngle), span = Math.abs(b.whipAngle - b.whipFrom);
            shapes.setColor(1f, 0.95f, 0.85f, 0.12f);
            shapes.arc(px, py, r, from, span, 48);
            // 척추 마디
            int seg = 16;
            for (int i = 0; i <= seg; i++) {
                float k = i / (float) seg;
                float bend = MathUtils.sin(k * MathUtils.PI) * 8 * Math.signum(b.whipTo - b.whipFrom);
                float a = b.whipAngle - bend;
                float x = px + MathUtils.cosDeg(a) * r * k, y = py + MathUtils.sinDeg(a) * r * k;
                shapes.setColor(0.92f, 0.88f, 0.76f, 1f);
                shapes.circle(x, y, 7 - 4 * k, 10);
                shapes.setColor(0.45f, 0.38f, 0.3f, 1f);
                shapes.circle(x, y, 2.2f - 1.2f * k, 6);
            }
        }
        if (b.cocoon) {
            // 바람의 고치: 공중의 몸을 감싸는 회오리
            float cy = b.footY() + 54 + bh * 0.5f + MathUtils.sin(time * 2.2f) * 6;
            for (int i = 0; i < 5; i++) {
                float rr = 60 + i * 9, ph = time * (2.5f + i * 0.4f) + i;
                shapes.setColor(0.75f, 1f, 0.95f, 0.25f - i * 0.03f);
                ellipseRing(b.centerX() + MathUtils.sin(ph) * 4, cy, rr, rr * 0.55f, 2f);
            }
            for (int i = 0; i < 10; i++) {
                float a = time * 220 + i * 36;
                shapes.setColor(0.9f, 1f, 1f, 0.7f);
                shapes.circle(b.centerX() + MathUtils.cosDeg(a) * 70, cy + MathUtils.sinDeg(a) * 38, 2f, 6);
            }
            // 땅의 바람 그림자
            shapes.setColor(0.6f, 0.85f, 0.8f, 0.18f);
            shapes.ellipse(b.centerX() - 60, b.footY() - 14, 120, 28, 30);
        }
        if (b.vanish > 0 && b.vanish < 1) {
            shapes.setColor(0.8f, 1f, 0.95f, 0.4f * b.vanish);
            ring(b.centerX(), b.centerY() + 30, 30 + 60 * b.vanish, 2f);
        }
    }

    /** 공간 왜곡: 보이는 쪽(가짜)으로 뻗는 거대한 촉수. */
    private void drawDecoyTentacle(Boss b, float p) {
        float len = b.warnLength * Math.min(1f, p * 1.3f);
        int segs = 18;
        for (int i = 0; i < segs; i++) {
            float k = i / (float) segs;
            float x = b.warnX + b.warnDirX * len * k, y = b.warnY + MathUtils.sin(time * 9 + k * 6) * 7 * k;
            float r = 13 * (1 - k * 0.6f);
            shapes.setColor(0.22f + 0.2f * k, 0.06f, 0.28f + 0.1f * k, 1f);
            shapes.circle(x, y, r, 12);
        }
        // 벽에 생긴 구멍 (여기로 들어간다)
        float wx = b.warnX + b.warnDirX * b.warnLength;
        shapes.setColor(0.5f, 0.15f, 0.7f, 0.7f * p);
        shapes.ellipse(wx - 10, b.warnY - 30, 20, 60, 20);
    }

    // ================================================================== 위험 지대 (바닥)

    private void drawHazards() {
        for (Hazard h : s.hazards) {
            if (!h.visible()) continue;
            float wp = h.warnProgress();
            switch (h.type) {
                case POISON_CLOUD -> drawCloud(h);
                case LASER -> drawLaser(h, wp);
                case WARP -> drawWarp(h, wp);
                case METEOR_FIELD -> drawMeteor(h, wp);
                case EYE -> drawEye(h, wp);
                case ABYSS_SIGIL -> drawSigilHazard(h);
                case VOID -> drawVoid(h, wp);
                case BONE_STORM -> drawStorm(h, wp);
                case CRACK -> drawCrack(h);
                case FLESH_TIDE -> drawTide(h, wp);
                case SWARM_ROW -> drawSwarmRow(h, wp);
                case SWAMP -> drawSwamp(h);
                case BONE_SPEAR -> drawSpearLane(h, wp);
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
                        if (h.type == Hazard.Type.LIGHTNING) {
                            // 먹구름 그림자가 모이고 땅 위로 불꽃이 튄다
                            shapes.setColor(0.05f, 0.05f, 0.12f, 0.25f * wp);
                            shapes.ellipse(h.x - h.radius * 1.8f, h.y - h.radius, h.radius * 3.6f, h.radius * 2f, 30);
                            for (int i = 0; i < 4; i++) {
                                float a = time * 400 + i * 90 + h.x;
                                float rx = h.x + MathUtils.cosDeg(a) * h.radius * wp, ry = h.y + MathUtils.sinDeg(a) * h.radius * 0.6f * wp;
                                shapes.setColor(0.85f, 0.95f, 1f, 0.9f);
                                shapes.rectLine(rx, ry, rx + MathUtils.random(-5f, 5f), ry + MathUtils.random(2f, 9f), 1.5f);
                            }
                        }
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
                        // 하늘 끝에서 내리꽂히는 굵은 벼락: 바깥 푸른 빛기둥 + 흰 심지 + 갈래 번개
                        float k = 1f - h.activeTime() / Hazard.STRIKE_TIME;
                        float top = h.y + 520;
                        shapes.setColor(0.55f, 0.75f, 1f, 0.22f * k);
                        shapes.rect(h.x - 26 * k - 6, h.y, (26 * k + 6) * 2, top - h.y);
                        shapes.setColor(1f, 1f, 0.85f, 0.55f * k);
                        shapes.ellipse(h.x - h.radius * 1.4f, h.y - h.radius * 0.8f, h.radius * 2.8f, h.radius * 1.6f, 36);
                        drawBolt(h.x, h.y, h.x + MathUtils.sin(h.x) * 40, top, 10f * k + 2, k * 0.6f, 1);
                        drawBolt(h.x, h.y, h.x + MathUtils.sin(h.x) * 40, top, 4f * k + 1, k, 1);
                        shapes.setColor(1f, 1f, 1f, k);
                        drawBoltWhite(h.x, h.y, h.x + MathUtils.sin(h.x) * 40, top, 2f * k + 0.5f, 1);
                        for (int b = 0; b < 4; b++) {
                            float by = h.y + 60 + b * 90, bx = h.x + MathUtils.sin(h.x) * 40 * (by - h.y) / 520;
                            drawBolt(bx, by, bx + (b % 2 == 0 ? 1 : -1) * (40 + b * 10), by + 50, 2f * k, k * 0.7f, b + 3);
                        }
                        // 땅에 퍼지는 전기 고리
                        shapes.setColor(0.8f, 0.95f, 1f, 0.8f * k);
                        ellipseRing(h.x, h.y, h.radius * (1.6f - k * 0.6f), h.radius * (1.0f - k * 0.4f), 2f);
                        ellipseRing(h.x, h.y, h.radius * (2.4f - k * 1.2f), h.radius * (1.4f - k * 0.7f), 1.5f);
                        // 땅을 타고 사방으로 뻗는 방전 + 그을린 자국
                        for (int b = 0; b < 8; b++) {
                            float ang = b * 45 + h.x * 0.7f;
                            float len = h.radius * (1.3f + 0.9f * (1 - k));
                            drawBolt(h.x, h.y, h.x + MathUtils.cosDeg(ang) * len, h.y + MathUtils.sinDeg(ang) * len * 0.6f,
                                    2.2f * k + 0.6f, 0.9f * k, b + 11);
                        }
                        shapes.setColor(0.08f, 0.07f, 0.1f, 0.45f * (1 - k * 0.5f));
                        shapes.ellipse(h.x - h.radius * 0.7f, h.y - h.radius * 0.4f, h.radius * 1.4f, h.radius * 0.8f, 24);
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

    private void drawLaser(Hazard h, float wp) {
        if (wp >= 0) {
            shapes.setColor(1f, 0.15f, 0.2f, 0.25f + 0.4f * wp * (0.6f + 0.4f * MathUtils.sin(time * 30)));
            shapes.rectLine(h.x, h.y, h.x2, h.y2, 1.5f + 2 * wp);
        } else {
            float k = 1f - h.activeTime() / Hazard.STRIKE_TIME;
            shapes.setColor(1f, 0.2f, 0.25f, 0.6f * k);
            shapes.rectLine(h.x, h.y, h.x2, h.y2, h.radius * 2);
            shapes.setColor(1f, 0.85f, 0.85f, 0.9f * k);
            shapes.rectLine(h.x, h.y, h.x2, h.y2, h.radius * 0.7f);
        }
    }

    /** 왜곡 촉수 (진짜): 반대쪽 벽에 작은 균열이 생기고, 거기서 촉수가 튀어나온다. */
    private void drawWarp(Hazard h, float wp) {
        if (wp >= 0) {
            // 등 뒤 벽에 희미하게 열리는 틈 (눈치채야 피할 수 있다)
            shapes.setColor(0.45f, 0.1f, 0.6f, 0.25f + 0.35f * wp);
            shapes.ellipse(h.x - 7, h.y - 26 * wp - 4, 14, 52 * wp + 8, 18);
            shapes.setColor(0.8f, 0.4f, 1f, 0.5f * wp);
            ellipseRing(h.x, h.y, 7, 26 * wp + 4, 1.2f);
            return;
        }
        float t = h.activeTime() / Hazard.STRIKE_TIME;
        float reach = Math.min(1f, t / 0.25f), fade = t > 0.6f ? 1f - (t - 0.6f) / 0.4f : 1f;
        int segs = 22;
        for (int i = 0; i < segs; i++) {
            float k = i / (float) segs;
            float x = MathUtils.lerp(h.x, h.x2, k * reach), y = h.y + MathUtils.sin(time * 12 + k * 7) * 6 * k;
            float r = h.radius * (1 - k * 0.55f);
            shapes.setColor(0.25f + 0.15f * k, 0.05f, 0.3f, fade);
            shapes.circle(x, y, r, 14);
            if (i % 3 == 0) {
                shapes.setColor(0.9f, 0.55f, 1f, 0.8f * fade);
                shapes.circle(x, y + r * 0.4f, 2f, 6);
            }
        }
    }

    /** 메테오: 금빛 원만 안전. 예고 동안 나머지가 붉게 달아오른다. */
    private void drawMeteor(Hazard h, float wp) {
        Rectangle a = h.area;
        if (wp >= 0) {
            float pulse = 0.5f + 0.5f * MathUtils.sin(time * (8 + 10 * wp));
            shapes.setColor(1f, 0.25f, 0.1f, 0.10f + 0.18f * wp + 0.05f * pulse);
            shapes.rect(a.x, a.y, a.width, a.height);
            // 떨어지는 그림자들
            MathUtils.random.setSeed((long) (h.x * 13 + h.y));
            for (int i = 0; i < 18; i++) {
                float mx = a.x + MathUtils.random(a.width), my = a.y + MathUtils.random(a.height);
                if (h.inSafe(mx, my)) continue;
                shapes.setColor(0.2f, 0.05f, 0.02f, 0.35f * wp);
                shapes.ellipse(mx - 18 * wp, my - 9 * wp, 36 * wp, 18 * wp, 16);
            }
            MathUtils.random.setSeed(System.nanoTime());
            // 안전한 곳: 보호막(반투명 돔)이 펼쳐진다
            drawShieldDome(h.x, h.y, h.radius, Math.min(1f, wp * 3f), 0f);
            return;
        }
        float k = 1f - h.activeTime() / 0.8f;
        shapes.setColor(1f, 0.55f, 0.15f, 0.45f * k);
        shapes.rect(a.x, a.y, a.width, a.height);
        MathUtils.random.setSeed((long) (h.x * 7 + h.y));
        for (int i = 0; i < 26; i++) {
            float mx = a.x + MathUtils.random(a.width), my = a.y + MathUtils.random(a.height);
            if (h.inSafe(mx, my)) continue;
            float r = 14 + MathUtils.random(16f);
            shapes.setColor(1f, 0.8f, 0.3f, 0.8f * k);
            shapes.circle(mx, my, r * (1.2f - k * 0.4f), 14);
            shapes.setColor(1f, 0.4f, 0.1f, 0.6f * k);
            shapes.rectLine(mx, my, mx - 30, my + 90 * k, 4 * k + 1);
        }
        MathUtils.random.setSeed(System.nanoTime());
        drawShieldDome(h.x, h.y, h.radius, 1f, k);
    }

    /**
     * 메테오를 막는 보호막: 바닥 원 + 위로 볼록한 돔 + 육각 무늬가 흐르고, 메테오가 떨어질 때 번쩍인다 (hitFlash).
     */
    private void drawShieldDome(float x, float y, float r, float open, float hitFlash) {
        float rr = r * (0.3f + 0.7f * open);
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 5);
        // 바닥
        shapes.setColor(0.45f, 0.9f, 1f, 0.25f + 0.1f * pulse);
        shapes.ellipse(x - rr, y - rr * 0.55f, rr * 2, rr * 1.1f, 40);
        // 돔 (위로 볼록)
        shapes.setColor(0.55f, 0.92f, 1f, 0.16f + 0.3f * hitFlash);
        shapes.arc(x, y, rr, 0, 180, 36);
        shapes.setColor(0.85f, 1f, 1f, 0.75f + 0.25f * hitFlash);
        for (int i = 0; i < 36; i++) {
            float a1 = 180f * i / 36, a2 = 180f * (i + 1) / 36;
            shapes.rectLine(x + MathUtils.cosDeg(a1) * rr, y + MathUtils.sinDeg(a1) * rr, x + MathUtils.cosDeg(a2) * rr,
                    y + MathUtils.sinDeg(a2) * rr, 2f);
        }
        ellipseRing(x, y, rr, rr * 0.55f, 1.5f);
        // 흐르는 육각 무늬
        shapes.setColor(0.7f, 1f, 1f, 0.35f);
        for (int i = 0; i < 6; i++) {
            float a = (time * 40 + i * 30) % 180;
            float hx = x + MathUtils.cosDeg(a) * rr * 0.7f, hy = y + MathUtils.sinDeg(a) * rr * 0.7f;
            for (int k = 0; k < 6; k++) {
                float b1 = k * 60, b2 = (k + 1) * 60;
                shapes.rectLine(hx + MathUtils.cosDeg(b1) * 5, hy + MathUtils.sinDeg(b1) * 5, hx + MathUtils.cosDeg(b2) * 5,
                        hy + MathUtils.sinDeg(b2) * 5, 1f);
            }
        }
        // 꼭대기 반짝임
        shapes.setColor(1f, 1f, 1f, 0.5f + 0.4f * pulse);
        shapes.rectLine(x - rr * 0.35f, y + rr * 0.8f, x - rr * 0.1f, y + rr * 0.92f, 2f);
    }

    /** 바람에 날아가는 용사 뒤로 흩날리는 바람 줄기. */
    private void drawWindStreaks() {
        HeroUnit h = s.unit;
        if (h.tumble <= 0) return;
        float p = h.tumble / HeroUnit.TUMBLE_TIME;
        for (int i = 0; i < 6; i++) {
            float off = (time * 300 + i * 37) % 60;
            float y = h.centerY() + 4 + (i - 2.5f) * 7;
            float x = h.centerX() - h.tumbleDir * (14 + off);
            shapes.setColor(0.85f, 0.95f, 1f, 0.55f * p);
            shapes.rectLine(x, y, x - h.tumbleDir * (18 + i * 3), y + MathUtils.sin(time * 20 + i) * 2, 1.5f);
        }
    }

    /** 허공의 거대한 눈알: 예고 동안 뜨이며 빛을 모은다. 뿜는 순간 번쩍. */
    private void drawEye(Hazard h, float wp) {
        float open = wp >= 0 ? Math.min(1f, wp * 2.2f) : 1f;
        float flash = wp >= 0 ? 0 : Math.max(0, 1f - h.activeTime() / Hazard.STRIKE_TIME);
        float rx = 34, ry = 22 * open + 1;
        if (wp >= 0) {
            shapes.setColor(0.85f, 1f, 0.3f, 0.10f + 0.25f * wp * wp);
            shapes.circle(h.x, h.y, 60 + 40 * wp, 32);
        }
        shapes.setColor(0.25f, 0.05f, 0.08f, 1f);
        shapes.ellipse(h.x - rx - 4, h.y - ry - 4, (rx + 4) * 2, (ry + 4) * 2, 32);
        shapes.setColor(0.93f, 0.9f, 0.82f, 1f);
        shapes.ellipse(h.x - rx, h.y - ry, rx * 2, ry * 2, 32);
        // 핏줄
        shapes.setColor(0.8f, 0.15f, 0.15f, 0.8f * open);
        for (int i = 0; i < 6; i++) {
            float a = i * 60 + 15;
            shapes.rectLine(h.x + MathUtils.cosDeg(a) * rx * 0.95f, h.y + MathUtils.sinDeg(a) * ry * 0.9f,
                    h.x + MathUtils.cosDeg(a + 10) * rx * 0.55f, h.y + MathUtils.sinDeg(a + 10) * ry * 0.5f, 1f);
        }
        // 용사를 따라 움직이는 눈동자
        float dx = s.unit.centerX() - h.x, dy = s.unit.centerY() - h.y;
        float len = Math.max(1, (float) Math.sqrt(dx * dx + dy * dy));
        float ix = h.x + dx / len * 10, iy = h.y + dy / len * 6 * open;
        shapes.setColor(0.6f, 0.9f, 0.2f, open);
        shapes.circle(ix, iy, 11 * open, 20);
        shapes.setColor(0.05f, 0.02f, 0.02f, open);
        shapes.ellipse(ix - 3, iy - 8 * open, 6, 16 * open, 12);
        if (flash > 0) {
            shapes.setColor(0.9f, 1f, 0.5f, 0.55f * flash);
            shapes.circle(h.x, h.y, 120 + 300 * (1 - flash), 40);
        }
    }

    /** 반전 마법진: 기하학적인 보랏빛 문양이 천천히 돈다. */
    private void drawSigilHazard(Hazard h) {
        float a = cloudAlpha(h);
        if (a <= 0) return;
        float r = h.radius;
        shapes.setColor(0.25f, 0.05f, 0.35f, 0.45f * a);
        shapes.ellipse(h.x - r, h.y - r * 0.65f, r * 2, r * 1.3f, 40);
        shapes.setColor(0.85f, 0.4f, 1f, 0.85f * a);
        ellipseRing(h.x, h.y, r, r * 0.65f, 1.6f);
        ellipseRing(h.x, h.y, r * 0.72f, r * 0.72f * 0.65f, 1.2f);
        for (int t = 0; t < 2; t++) {
            float rot = time * (t == 0 ? 40 : -55) + t * 60;
            for (int i = 0; i < 3; i++) {
                float a1 = rot + i * 120, a2 = rot + (i + 1) * 120;
                shapes.rectLine(h.x + MathUtils.cosDeg(a1) * r * 0.72f, h.y + MathUtils.sinDeg(a1) * r * 0.47f,
                        h.x + MathUtils.cosDeg(a2) * r * 0.72f, h.y + MathUtils.sinDeg(a2) * r * 0.47f, 1.2f);
            }
        }
        for (int i = 0; i < 8; i++) {
            float ang = time * 25 + i * 45;
            shapes.rect(h.x + MathUtils.cosDeg(ang) * r * 0.86f - 2, h.y + MathUtils.sinDeg(ang) * r * 0.56f - 2, 4, 4);
        }
    }

    /** 공허: 찢긴 공간. 예고 동안은 금이 가고, 그 뒤로는 계속 남는다. */
    private void drawVoid(Hazard h, float wp) {
        if (wp >= 0) {
            shapes.setColor(0.7f, 0.3f, 1f, 0.3f + 0.5f * wp);
            float ex = MathUtils.lerp(h.x, h.x2, wp), ey = MathUtils.lerp(h.y, h.y2, wp);
            shapes.rectLine(h.x, h.y, ex, ey, 1.5f + wp * 2);
            return;
        }
        float w = h.radius * 2;
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 3 + h.x);
        shapes.setColor(0.55f, 0.15f, 0.85f, 0.35f + 0.2f * pulse);
        shapes.rectLine(h.x, h.y, h.x2, h.y2, w + 8);
        shapes.setColor(0.01f, 0f, 0.02f, 1f);
        shapes.rectLine(h.x, h.y, h.x2, h.y2, w);
        // 안쪽의 별빛
        for (int i = 0; i < 8; i++) {
            float k = ((time * 0.15f + i / 8f) % 1f);
            float x = MathUtils.lerp(h.x, h.x2, k), y = MathUtils.lerp(h.y, h.y2, k);
            shapes.setColor(0.8f, 0.6f, 1f, 0.7f);
            shapes.circle(x + MathUtils.sin(i * 3.1f) * w * 0.25f, y + MathUtils.cos(i * 2.3f) * w * 0.25f, 1.2f, 6);
        }
    }

    /** 뼛가루 폭풍: 바위가 떨어지고, 바람 방향으로 뼛가루가 휩쓴다. 바위 그늘은 살짝 밝게 보인다. */
    private void drawStorm(Hazard h, float wp) {
        Rectangle a = h.area;
        float rx = h.rock.x + h.rock.width / 2, ry = h.rock.y;
        if (!h.rockPlaced) {
            // 떨어질 자리
            float k = wp >= 0 ? wp : 1f;
            shapes.setColor(0.1f, 0.08f, 0.06f, 0.25f + 0.35f * k);
            shapes.ellipse(rx - h.rock.width / 2 * k, ry - 6 * k, h.rock.width * k, 16 * k, 20);
        }
        boolean active = h.cloudActive();
        float strength = active ? 1f : (wp >= 0 ? wp * 0.4f : Math.max(0, 1f - (h.activeTime() - h.life) / Hazard.FADE_TIME));
        if (strength <= 0) return;
        // 바위 그늘 (바람이 불어 가는 쪽)
        if (h.rockPlaced && active) {
            float half = h.rock.width / 2f + 4;
            float cx = rx, cy = ry + h.rock.height / 2;
            float ex = cx + h.dirX * h.radius, ey = cy + h.dirY * h.radius;
            shapes.setColor(0.6f, 0.85f, 1f, 0.12f);
            shapes.rectLine(cx, cy, ex, ey, half * 2);
        }
        // 뼛가루 줄기
        Rectangle v = view();
        MathUtils.random.setSeed(11);
        for (int i = 0; i < 90; i++) {
            float ox = MathUtils.random(v.width), oy = MathUtils.random(v.height);
            float sp = 260 + MathUtils.random(160f);
            float x = v.x + ((ox + h.dirX * time * sp) % v.width + v.width) % v.width;
            float y = v.y + ((oy + h.dirY * time * sp) % v.height + v.height) % v.height;
            if (!a.contains(x, y)) continue;
            if (h.rockPlaced && h.sheltered(x, y, h.radius)) continue;
            float len = 10 + MathUtils.random(12f);
            shapes.setColor(0.88f, 0.84f, 0.74f, (0.35f + MathUtils.random(0.3f)) * strength);
            shapes.rectLine(x, y, x - h.dirX * len, y - h.dirY * len, 1.4f);
        }
        MathUtils.random.setSeed(System.nanoTime());
        shapes.setColor(0.8f, 0.76f, 0.66f, 0.10f * strength);
        shapes.rect(a.x, a.y, a.width, a.height);
    }

    /** 균열: 숨을 수 있는 좁은 틈. 안쪽이 희미하게 빛난다. 한 번 쓰면 닫힌다. */
    private void drawCrack(Hazard h) {
        float a = h.used ? Math.max(0, 1f - (h.time - h.usedTime) / 0.6f) : cloudAlpha(h);
        if (a <= 0) return;
        float r = h.radius;
        float pulse = 0.6f + 0.4f * MathUtils.sin(time * 4 + h.x);
        shapes.setColor(0.4f, 0.9f, 1f, 0.25f * a * pulse);
        shapes.ellipse(h.x - r * 1.8f, h.y - r * 1.1f, r * 3.6f, r * 2.2f, 24);
        shapes.setColor(0.02f, 0.02f, 0.05f, a);
        shapes.ellipse(h.x - r, h.y - r * 0.55f, r * 2, r * 1.1f, 20);
        shapes.setColor(0.6f, 0.95f, 1f, 0.9f * a);
        ellipseRing(h.x, h.y, r, r * 0.55f, 1.3f);
        shapes.rectLine(h.x - r * 0.6f, h.y, h.x + r * 0.6f, h.y + 1, 1.2f);
    }

    /** 살점 해일: 화면 절반 이상을 덮는 덩어리가 한쪽에서 밀려온다. */
    private void drawTide(Hazard h, float wp) {
        Rectangle a = h.area;
        float L = h.sweepLength();
        if (wp >= 0) {
            // 시작 가장자리에서 꿈틀거림
            float w = 10 + 20 * wp;
            shapes.setColor(0.35f, 0.02f, 0.08f, 0.5f + 0.3f * wp);
            tideRect(h, -w, 0, a);
            return;
        }
        float front = h.front();
        float thick = L * 0.62f;
        float back = front - thick;
        shapes.setColor(0.34f, 0.03f, 0.09f, 0.9f);
        tideRect(h, back, front, a);
        // 살점 덩어리·핏줄 (앞줄과 함께 밀려온다)
        float span0 = Math.abs(h.dirX) > 0.5f ? a.height : a.width;
        MathUtils.random.setSeed((long) (h.delay * 1000) + 3);
        for (int i = 0; i < 70; i++) {
            float al = front - MathUtils.random(thick), ac = MathUtils.random(span0);
            if (al < -20) continue;
            float[] p = tidePoint(h, al, ac);
            float r = 6 + MathUtils.random(12f) + MathUtils.sin(time * 4 + i) * 2;
            shapes.setColor(0.55f + MathUtils.random(0.2f), 0.06f, 0.12f, 0.85f);
            shapes.circle(p[0], p[1], r, 10);
            if (i % 3 == 0) {
                shapes.setColor(0.1f, 0f, 0.02f, 0.9f);
                shapes.circle(p[0] + 2, p[1] - 1, r * 0.35f, 8);
            }
        }
        MathUtils.random.setSeed(System.nanoTime());
        // 울퉁불퉁한 앞줄
        int n = 22;
        float span = Math.abs(h.dirX) > 0.5f ? a.height : a.width;
        for (int i = 0; i <= n; i++) {
            float k = i / (float) n;
            float bump = 10 + 8 * MathUtils.sin(time * 6 + i * 1.7f);
            float[] p = tidePoint(h, front, k * span);
            shapes.setColor(0.4f, 0.04f, 0.1f, 0.95f);
            shapes.circle(p[0], p[1], bump, 12);
            shapes.setColor(0.75f, 0.2f, 0.25f, 0.8f);
            shapes.circle(p[0] + h.dirX * bump * 0.4f, p[1] + h.dirY * bump * 0.4f, bump * 0.3f, 8);
        }
    }

    /** 해일 진행 방향 기준 [from, to] 구간을 칠한다 (area 안으로 자른다). */
    private void tideRect(Hazard h, float from, float to, Rectangle a) {
        float L = h.sweepLength();
        float f = Math.max(-40, from), t = Math.min(L, to);
        if (t <= f) return;
        if (h.dirX > 0.5f) shapes.rect(a.x + f, a.y, t - f, a.height);
        else if (h.dirX < -0.5f) shapes.rect(a.x + a.width - t, a.y, t - f, a.height);
        else if (h.dirY > 0.5f) shapes.rect(a.x, a.y + f, a.width, t - f);
        else shapes.rect(a.x, a.y + a.height - t, a.width, t - f);
    }

    private float[] tidePoint(Hazard h, float along, float across) {
        Rectangle a = h.area;
        if (h.dirX > 0.5f) return new float[]{a.x + along, a.y + across};
        if (h.dirX < -0.5f) return new float[]{a.x + a.width - along, a.y + across};
        if (h.dirY > 0.5f) return new float[]{a.x + across, a.y + along};
        return new float[]{a.x + across, a.y + a.height - along};
    }

    /** 군체 한 줄: 꿈틀대는 벌레·촉수 띠, 빈틈이 있다. */
    private void drawSwarmRow(Hazard h, float wp) {
        float span = Math.abs(h.dirX) > 0.5f ? h.area.height : h.area.width;
        float along = wp >= 0 ? 0 : h.front();
        if (wp < 0 && along > h.sweepLength() + 20) return;
        float alpha = wp >= 0 ? 0.35f + 0.4f * wp : 1f;
        int n = (int) (span / 7);
        for (int i = 0; i <= n; i++) {
            float c = i * span / n;
            boolean gap = false;
            for (float g : h.gaps) if (Math.abs(c - g) <= h.gapWidth / 2f) gap = true;
            if (gap) continue;
            float wig = MathUtils.sin(time * 14 + i * 1.3f) * 6;
            float[] p = tidePoint(h, along + wig, c);
            if (wp >= 0) {
                shapes.setColor(0.7f, 0.3f, 0.9f, alpha);
                shapes.circle(p[0], p[1], 1.6f, 6);
                continue;
            }
            shapes.setColor(0.18f + 0.1f * (i % 3), 0.04f, 0.22f, 1f);
            shapes.circle(p[0], p[1], 6 + (i % 2) * 2, 10);
            shapes.setColor(0.85f, 0.5f, 0.95f, 0.8f);
            shapes.circle(p[0] + h.dirX * 3, p[1] + h.dirY * 3, 1.3f, 6);
        }
    }

    /** 늪: 검푸른 진흙이 점점 넓어지고 거품이 올라온다. */
    private void drawSwamp(Hazard h) {
        float a = cloudAlpha(h);
        if (a <= 0) return;
        float r = h.radiusNow();
        shapes.setColor(0.12f, 0.16f, 0.08f, 0.75f * a);
        shapes.ellipse(h.x - r, h.y - r * 0.65f, r * 2, r * 1.3f, 40);
        shapes.setColor(0.22f, 0.3f, 0.12f, 0.55f * a);
        shapes.ellipse(h.x - r * 0.75f, h.y - r * 0.45f, r * 1.5f, r * 0.9f, 32);
        // 일렁이는 테두리
        shapes.setColor(0.45f, 0.62f, 0.25f, 0.6f * a);
        int n = 36;
        for (int i = 0; i < n; i++) {
            float a1 = MathUtils.PI2 * i / n, a2 = MathUtils.PI2 * (i + 1) / n;
            float w1 = 1f + 0.05f * MathUtils.sin(time * 3 + i), w2 = 1f + 0.05f * MathUtils.sin(time * 3 + i + 1);
            shapes.rectLine(h.x + r * w1 * MathUtils.cos(a1), h.y + r * 0.65f * w1 * MathUtils.sin(a1),
                    h.x + r * w2 * MathUtils.cos(a2), h.y + r * 0.65f * w2 * MathUtils.sin(a2), 1.4f);
        }
        // 거품
        for (int i = 0; i < 7; i++) {
            float t = (time * 0.8f + i / 7f) % 1f;
            float bx = h.x + MathUtils.sin(i * 2.7f + h.x) * r * 0.6f, by = h.y + MathUtils.cos(i * 1.9f + h.y) * r * 0.35f;
            shapes.setColor(0.55f, 0.75f, 0.3f, 0.7f * a * (1 - t));
            ring(bx, by, 1.5f + 3f * t, 1f);
        }
    }

    /** 뼈창: 리치에서 뻗는 띠. 예고 중에는 바닥이 갈라지는 선, 터지면 먼지. */
    private void drawSpearLane(Hazard h, float wp) {
        float w = h.radius * 2;
        if (wp >= 0) {
            shapes.setColor(0.9f, 0.85f, 0.7f, 0.12f + 0.12f * wp);
            shapes.rectLine(h.x, h.y, h.x2, h.y2, w);
            shapes.setColor(1f, 0.35f, 0.25f, 0.55f);
            shapes.rectLine(h.x, h.y, MathUtils.lerp(h.x, h.x2, wp), MathUtils.lerp(h.y, h.y2, wp), 2.5f);
            float nx = -(h.y2 - h.y), ny = h.x2 - h.x;
            float len = (float) Math.sqrt(nx * nx + ny * ny);
            nx = nx / len * w / 2;
            ny = ny / len * w / 2;
            shapes.setColor(0.9f, 0.85f, 0.7f, 0.6f);
            shapes.rectLine(h.x + nx, h.y + ny, h.x2 + nx, h.y2 + ny, 1f);
            shapes.rectLine(h.x - nx, h.y - ny, h.x2 - nx, h.y2 - ny, 1f);
        } else {
            float k = 1f - h.activeTime() / Hazard.STRIKE_TIME;
            shapes.setColor(0.8f, 0.75f, 0.65f, 0.3f * k);
            shapes.rectLine(h.x, h.y, h.x2, h.y2, w * 1.3f);
        }
    }

    /** drawBolt와 같은 모양, 색은 미리 정해 둔 것 (흰 심지). */
    private void drawBoltWhite(float x1, float y1, float x2, float y2, float w, int seed) {
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
        TextureRegion rock = assets.region("rocks");
        for (Hazard h : s.hazards) {
            if (h.type == Hazard.Type.BONE_STORM) {
                // 바위: 예고 중에 하늘에서 떨어지고, 폭풍이 끝나면 부서진다
                float fall = h.rockPlaced ? 0 : Math.max(0, 1f - (h.activeTime() + h.warning) / (h.warning * 0.5f));
                if (!h.rockPlaced && h.activeTime() >= h.life) continue;
                float w = h.rock.width + 8, hh = rock.getRegionHeight() * (w / rock.getRegionWidth());
                batch.setColor(1, 1, 1, h.rockPlaced ? 1f : 1f - fall * 0.5f);
                batch.draw(rock, h.rock.x - 4, h.rock.y - 4 + fall * 220, w, hh);
                continue;
            }
            if (h.type == Hazard.Type.BONE_SPEAR && h.activeTime() >= 0) {
                drawSpears(spike, h);
                continue;
            }
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

    /** 뼈창: 띠를 따라 작은 뼈 가시가 리치 쪽부터 차례로 솟는다. */
    private void drawSpears(TextureRegion spike, Hazard h) {
        float len = (float) Math.hypot(h.x2 - h.x, h.y2 - h.y);
        int n = Math.max(2, (int) (len / 26));
        float w = spike.getRegionWidth() * 0.6f, hh = spike.getRegionHeight() * 0.7f;
        for (int i = n - 1; i >= 0; i--) {
            float k = (i + 0.5f) / n;
            float t = (h.activeTime() - k * 0.12f) / Hazard.STRIKE_TIME;
            if (t <= 0) continue;
            float rise = Math.min(1f, t / 0.15f);
            float a = t > 0.6f ? Math.max(0, 1f - (t - 0.6f) / 0.4f) : 1f;
            float x = MathUtils.lerp(h.x, h.x2, k), y = MathUtils.lerp(h.y, h.y2, k);
            batch.setColor(1, 1, 1, a);
            batch.draw(spike.getTexture(), x - w / 2, y - 4, w, hh * rise,
                    spike.getU(), spike.getV() + (spike.getV2() - spike.getV()) * rise, spike.getU2(), spike.getV());
        }
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
        if (h.stun > 0) {
            float hy = by + 34;
            for (int i = 0; i < 3; i++) {
                float ang = time * 320 + i * 120;
                shapes.setColor(1f, 0.92f, 0.4f, 0.95f);
                shapes.circle(cx + MathUtils.cosDeg(ang) * 10, hy + MathUtils.sinDeg(ang) * 3, 2f, 8);
            }
        }
        if (s.inverted()) {
            for (int i = 0; i < 2; i++) {
                float ang = -time * 260 + i * 180;
                shapes.setColor(0.85f, 0.4f, 1f, 0.9f);
                shapes.circle(cx + MathUtils.cosDeg(ang) * 13, by + 16 + MathUtils.sinDeg(ang) * 13, 2.2f, 8);
            }
        }
        if (h.slow > 0) {
            // 둔화: 발에 진흙이 엉겨 붙었다
            shapes.setColor(0.25f, 0.32f, 0.14f, 0.85f);
            shapes.ellipse(cx - 10, by - 3, 20, 7, 14);
            for (int i = 0; i < 3; i++) {
                float t = (time * 0.7f + i / 3f) % 1f;
                shapes.setColor(0.4f, 0.5f, 0.2f, 0.8f * (1 - t));
                shapes.circle(cx - 6 + i * 6, by + 2 - t * 4, 1.6f, 6);
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

    /** 마을 공기: 마왕 전에는 수정에서 보랏빛 알갱이가 떠오르고, 뒤에는 꽃잎·색종이가 흩날린다. */
    private void drawVillageAir() {
        Rectangle vr = s.map.village;
        Rectangle v = view();
        if (!v.overlaps(vr)) return;
        if (!s.villagersFree()) {
            for (WorldMap.VillagerDef d : s.map.villagers) {
                for (int i = 0; i < 4; i++) {
                    float t = (time * 0.5f + i * 0.25f + d.pos().x * 0.01f) % 1f;
                    float px = d.pos().x + MathUtils.sin(time * 1.3f + i * 2.1f) * 12;
                    float py = d.pos().y + 10 + t * 70;
                    shapes.setColor(0.8f, 0.6f, 1f, 0.7f * (1 - t));
                    shapes.rect(px, py, 2, 2);
                }
            }
            return;
        }
        float[][] cols = {{1f, 0.55f, 0.7f}, {1f, 0.86f, 0.3f}, {0.55f, 0.85f, 1f}, {1f, 1f, 1f}, {0.6f, 0.95f, 0.5f}};
        for (int i = 0; i < 90; i++) {
            float sx = (i * 97.3f) % vr.width, speed = 18 + (i * 13) % 22;
            float py = vr.y + vr.height + 20 - ((time * speed + i * 53f) % (vr.height + 40));
            float px = vr.x + sx + MathUtils.sin(time * 1.4f + i) * 14;
            if (!v.contains(px, py)) continue;
            float[] c = cols[i % cols.length];
            shapes.setColor(c[0], c[1], c[2], 0.9f);
            float w = (i % 3 == 0) ? 3 : 2, h = MathUtils.sin(time * 6 + i) > 0 ? 2 : 1;
            shapes.rect(px, py, w, h);
        }
    }

    private void drawEffects() {
        for (Effect e : s.effects) {
            float p = e.progress(), a = 1f - p;
            switch (e.type) {
                case MOB_SLASH -> {
                    // 해골 기사의 베기: 바라보는 쪽으로 크게 휘두른 초승달 궤적 (칼끝이 지나간 자리부터 옅어진다)
                    float base = MathUtils.atan2Deg360(e.dirY, e.dirX);
                    float sweep = e.arc, head = base + sweep / 2 - sweep * Math.min(1f, p * 1.6f);
                    for (int i = 0; i < 14; i++) {
                        float ang = head + sweep * i / 14f * 0.6f;
                        if (ang > base + sweep / 2) break;
                        float k = 1f - i / 14f;
                        shapes.setColor(0.85f, 0.85f, 1f, 0.75f * a * k);
                        float r0 = e.radius * 0.45f, r1 = e.radius;
                        shapes.rectLine(e.x + MathUtils.cosDeg(ang) * r0, e.y + MathUtils.sinDeg(ang) * r0,
                                e.x + MathUtils.cosDeg(ang) * r1, e.y + MathUtils.sinDeg(ang) * r1, 3f * k + 1);
                    }
                }
                case MOB_THRUST -> {
                    // 해골 병사의 찌르기: 앞으로 쭉 뻗는 날 끝 + 바람 줄기
                    float len = e.radius * Math.min(1f, p * 2.2f);
                    float nx = -e.dirY, ny = e.dirX;
                    shapes.setColor(1f, 0.92f, 0.75f, 0.85f * a);
                    shapes.rectLine(e.x, e.y, e.x + e.dirX * len, e.y + e.dirY * len, 3f);
                    shapes.setColor(1f, 1f, 1f, 0.5f * a);
                    for (int sgn = -1; sgn <= 1; sgn += 2) {
                        shapes.rectLine(e.x + nx * 7 * sgn, e.y + ny * 7 * sgn,
                                e.x + e.dirX * len * 0.7f + nx * 4 * sgn, e.y + e.dirY * len * 0.7f + ny * 4 * sgn, 1.2f);
                    }
                }
                case PARRY -> {
                    // 칼날끼리 부딪힌 불꽃
                    for (int i = 0; i < 8; i++) {
                        float ang = i * 45 + 22;
                        float d = 4 + p * 18;
                        shapes.setColor(1f, 0.9f - 0.3f * p, 0.5f, a);
                        shapes.rectLine(e.x + MathUtils.cosDeg(ang) * d * 0.4f, e.y + MathUtils.sinDeg(ang) * d * 0.4f,
                                e.x + MathUtils.cosDeg(ang) * d, e.y + MathUtils.sinDeg(ang) * d, 2f);
                    }
                    shapes.setColor(1f, 1f, 0.9f, a);
                    shapes.circle(e.x, e.y, 5 * a + 1, 10);
                }
                case REND -> {
                    // 기형적인 팔이 공간을 할퀸 자국: 찢긴 경계를 가로지르는 거대한 발톱 네 줄
                    Rectangle r = e.rect;
                    if (r == null) break;
                    boolean vert = r.height >= r.width;
                    for (int i = 0; i < 4; i++) {
                        float t = Math.min(1f, p * 3f - i * 0.15f);
                        if (t <= 0) continue;
                        float x0, y0, x1, y1;
                        if (vert) {
                            x0 = r.x - 40; x1 = r.x + r.width + 40;
                            y0 = r.y + r.height * (0.15f + i * 0.22f) + 60; y1 = y0 - 120;
                        } else {
                            y0 = r.y - 40; y1 = r.y + r.height + 40;
                            x0 = r.x + r.width * (0.15f + i * 0.22f) - 60; x1 = x0 + 120;
                        }
                        float ex = MathUtils.lerp(x0, x1, t), ey = MathUtils.lerp(y0, y1, t);
                        shapes.setColor(1f, 0.85f, 1f, 0.9f * a);
                        shapes.rectLine(x0, y0, ex, ey, 7f * a + 1);
                        shapes.setColor(0.7f, 0.2f, 1f, 0.7f * a);
                        shapes.rectLine(x0 + 3, y0 - 3, ex + 3, ey - 3, 3f * a + 1);
                    }
                }
                case CHEST -> {
                    // 상자에서 솟는 빛기둥과 반짝이
                    shapes.setColor(1f, 0.92f, 0.6f, 0.35f * a);
                    shapes.rect(e.x - 12 * a - 4, e.y + 10, (12 * a + 4) * 2, 120 * Math.min(1, p * 4));
                    for (int i = 0; i < 10; i++) {
                        float ang = i * 36 + p * 120;
                        float d = 10 + p * 46;
                        shapes.setColor(1f, 0.95f, 0.7f, a);
                        shapes.circle(e.x + MathUtils.cosDeg(ang) * d, e.y + 20 + MathUtils.sinDeg(ang) * d * 0.6f + p * 20, 2f, 6);
                    }
                }
                case THUNDER -> {
                    // 벼락이 떨어진 자리: 커지는 충격파 고리 + 튀는 불꽃 + 그을음
                    shapes.setColor(0.12f, 0.1f, 0.14f, 0.5f * a);
                    shapes.ellipse(e.x - e.radius, e.y - e.radius * 0.55f, e.radius * 2, e.radius * 1.1f, 28);
                    shapes.setColor(0.8f, 0.95f, 1f, 0.9f * a);
                    ellipseRing(e.x, e.y, e.radius * (0.8f + 1.8f * p), e.radius * (0.45f + 1.0f * p), 3f * a + 0.5f);
                    for (int i = 0; i < 8; i++) {
                        float ang = i * 45 + e.x;
                        float d = e.radius * (0.4f + 1.4f * p);
                        shapes.setColor(1f, 1f, 0.75f, a);
                        shapes.circle(e.x + MathUtils.cosDeg(ang) * d, e.y + MathUtils.sinDeg(ang) * d * 0.6f + 20 * p * (1 - p) * 4, 2f, 6);
                    }
                }
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
                case SUMMON -> {
                    // 어둠 속에서도 튀지 않게 어두운 보랏빛
                    shapes.setColor(0.35f, 0.1f, 0.45f, 0.45f * a);
                    shapes.ellipse(e.x - (8 + 20 * p), e.y - (5 + 12 * p), (8 + 20 * p) * 2, (5 + 12 * p) * 2, 24);
                    shapes.setColor(0.75f, 0.3f, 0.95f, 0.7f * a);
                    ellipseRing(e.x, e.y, 8 + 22 * p, 5 + 13 * p, 1.5f);
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
            if ("tornado".equals(p.sprite)) {
                r = assets.frame("tornado", p.age, 0.08f, 0, 2);
                w = r.getRegionWidth();
                h = r.getRegionHeight();
            }
            float rot = switch (p.sprite) {
                case "ice_chunk" -> p.age * 540f;
                case "fire_bolt" -> p.angleDeg();
                default -> 0f;
            };
            if (s.unit.blinded()) batch.setColor(p.fromBoss ? 1f : 0.85f, 0.4f, p.fromBoss ? 0.55f : 1f, 1f);
            else batch.setColor(Color.WHITE);
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
        if (c == null && arenaFade <= 0 && !s.sleeping()) return;
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

        // 화면 전체: 추락 끝 → 검은 화면, 전장 도착 → 밝아짐 / 잠
        drawScreenOverlay(c);
    }

    private void drawScreenOverlay(Cutscene c) {
        float r = 0, g = 0, b = 0, a = 0;
        a = c != null ? Math.max(0, (c.fall() - 0.4f) / 0.6f) : arenaFade;
        float sl = s.sleepProgress();
        if (sl > 0) {
            // 잠: 천천히 어두워졌다가 밝아진다
            r = 0.01f; g = 0.01f; b = 0.04f;
            a = Math.max(a, MathUtils.sin(sl * MathUtils.PI) * 0.85f);
        }
        if (a <= 0) return;
        shapes.setProjectionMatrix(screenMatrix);
        blend();
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(r, g, b, Math.min(1f, a));
        shapes.rect(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        shapes.end();
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
                case SHIELD -> tmp2.set(0.85f, 0.85f, 0.72f, 1f);
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
        // 잡몹 체력바 위에 남은 체력 (2026-10-12 결정)
        float oldScale = textFont.getData().scaleX;
        textFont.getData().setScale(oldScale * 0.62f);
        for (Monster m : s.activeMonsters()) {
            if (m.isBoss() || !m.alive() || m.hp() >= m.maxHp()) continue;
            if (m.room != null && !s.visibility.room(m.room)) continue;
            TextureRegion f = assets.region(m.sprite());
            v3.set(m.footX(), m.footY() + f.getRegionHeight() + 6, 0);
            viewport.project(v3);
            String hp = String.valueOf(m.hp());
            layout.setText(textFont, hp);
            textFont.setColor(0, 0, 0, 0.8f);
            textFont.draw(batch, hp, Math.round(v3.x - layout.width / 2) + 1, Math.round(v3.y + layout.height + 4) - 1);
            textFont.setColor(1f, 0.92f, 0.85f, 1f);
            textFont.draw(batch, hp, Math.round(v3.x - layout.width / 2), Math.round(v3.y + layout.height + 4));
        }
        // 자는 동안 침대 위로 Zzz
        if (s.sleeping()) {
            for (int i = 0; i < 3; i++) {
                float ph = (s.sleepProgress() * 3 + i / 3f) % 1f;
                v3.set(s.map.bed.x + s.map.bed.width / 2 + 10 + ph * 18, s.map.bed.y + s.map.bed.height + ph * 30, 0);
                viewport.project(v3);
                textFont.setColor(0.85f, 0.9f, 1f, 1f - ph);
                textFont.draw(batch, i == 0 ? "Z" : "z", Math.round(v3.x), Math.round(v3.y));
            }
        }
        textFont.getData().setScale(oldScale);
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
