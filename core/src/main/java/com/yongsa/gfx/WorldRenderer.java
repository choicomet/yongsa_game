package com.yongsa.gfx;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.yongsa.combat.AttackInstance;
import com.yongsa.combat.Dir8;
import com.yongsa.combat.Enemy;
import com.yongsa.combat.Hero;
import com.yongsa.combat.HitShape;
import com.yongsa.combat.Projectile;
import com.yongsa.combat.Statuses;
import com.yongsa.config.Balance;
import com.yongsa.model.MineRoomState;
import com.yongsa.model.VeinState;
import com.yongsa.model.WeaponState;
import com.yongsa.world.DoorDef;
import com.yongsa.world.DungeonMap;
import com.yongsa.world.GameWorld;
import com.yongsa.world.MapObject;
import com.yongsa.world.RoomDef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 월드 그리기. 바닥과 벽 타일 → 발 위치 기준으로 정렬한 오브젝트 → 경고 → 조명 → 월드 위 글자 순서.
 * 전투 경고는 장식물 뒤에 가려지지 않도록 오브젝트 위에 한 번 더 테두리를 그린다.
 */
public class WorldRenderer implements Disposable {
    public static final float VIEW_W = 960, VIEW_H = 540;

    public final OrthographicCamera cam = new OrthographicCamera();
    public final FitViewport viewport = new FitViewport(VIEW_W, VIEW_H, cam);

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final TileLayer tiles;
    private final SpriteLibrary sprites;
    private final Textures tx;
    private final Fonts fonts;
    private final Lighting lighting;
    private final GlyphLayout layout = new GlyphLayout();
    public boolean debug;
    /** 개발자 모드: 공격 범위를 바닥에 미리 보여 준다. 꺼져 있으면(기본) 적 머리 위의 ! 만 뜬다. */
    public boolean showTelegraphs;

    private boolean snapCamera = true;
    /** 휴대용 대장간을 꺼내 놓았는지 (GameScreen 이 정한다)와 꺼내는 동작의 진행 정도. */
    public boolean forgeShown;
    private float forgeOut;
    private final Vector3 tmp3 = new Vector3();

    private static final Color WARN = new Color(1f, 0.32f, 0.18f, 1f);
    /** 테마별 기본 밝기: 캠프, 폐광, 요새, 신전. */
    private static final Color[] AMBIENTS = {
            new Color(0.30f, 0.27f, 0.31f, 1f), new Color(0.56f, 0.50f, 0.46f, 1f),
            new Color(0.49f, 0.54f, 0.64f, 1f), new Color(0.55f, 0.46f, 0.64f, 1f)};
    private final Color ambient = new Color(AMBIENTS[0]);

    private void updateAmbient(GameWorld w) {
        int t = w.map.theme((int) (cam.position.x / w.map.tileSize), (int) (cam.position.y / w.map.tileSize));
        Color target = AMBIENTS[Math.max(0, Math.min(3, t))];
        ambient.lerp(target, 0.05f);
    }

    private interface Draw {
        void draw();
    }

    private static class Item {
        final float y;
        final Draw draw;

        Item(float y, Draw draw) {
            this.y = y;
            this.draw = draw;
        }
    }

    private final List<Item> items = new ArrayList<>();

    public WorldRenderer(SpriteBatch batch, ShapeRenderer shapes, DungeonMap map, SpriteLibrary sprites, Textures tx, Fonts fonts) {
        this.batch = batch;
        this.shapes = shapes;
        this.tiles = new TileLayer(map, Gdx.files.internal("data/tileset.json"));
        this.sprites = sprites;
        this.tx = tx;
        this.fonts = fonts;
        this.lighting = new Lighting(tx, (int) VIEW_W, (int) VIEW_H);
    }

    public void resize(int w, int h) {
        viewport.update(w, h, false);
    }

    public void snap() {
        snapCamera = true;
    }

    /** 화면 좌표 → 월드 좌표. */
    public Vector2 unproject(float sx, float sy, Vector2 out) {
        tmp3.set(sx, sy, 0);
        viewport.unproject(tmp3);
        return out.set(tmp3.x, tmp3.y);
    }

    // =====================================================================
    // 카메라
    // =====================================================================

    /** 카메라가 목표에 거의 도착했는지 (전환 중에는 다음 공격 예고를 미룬다). */
    public boolean cameraSettled;

    public void updateCamera(GameWorld w, float dt) {
        float tx0, ty0;
        RoomDef r = w.room;
        if (w.combat != null && w.combat.boss) {
            tx0 = w.combat.room.centerX();
            ty0 = w.combat.room.centerY();
        } else if (r != null) {
            // Room-scale action: see every exit and attack instead of chasing the hero.
            tx0 = r.centerX();
            ty0 = r.centerY();
        } else {
            tx0 = w.hero.x;
            ty0 = w.hero.y + 12;
        }
        float halfW = VIEW_W / 2f, halfH = VIEW_H / 2f;
        tx0 = MathUtils.clamp(tx0, halfW, w.map.widthPx - halfW);
        ty0 = MathUtils.clamp(ty0, halfH, w.map.heightPx - halfH);
        if (snapCamera) {
            cam.position.set(tx0, ty0, 0);
            snapCamera = false;
        } else {
            float k = 1f - (float) Math.exp(-dt * 12f);
            cam.position.x += (tx0 - cam.position.x) * k;
            cam.position.y += (ty0 - cam.position.y) * k;
        }
        cameraSettled = Math.abs(tx0 - cam.position.x) < 6 && Math.abs(ty0 - cam.position.y) < 6;
    }

    // =====================================================================
    // 그리기
    // =====================================================================

    private float time;
    /** 문마다 열림 정도 (0 닫힘 ~ 1 열림). */
    private final java.util.Map<DoorDef, Float> doorOpen = new java.util.HashMap<>();

    public void render(GameWorld w, Effects fx, float shakeX, float shakeY) {
        time = w.time;
        viewport.apply();
        float cx = cam.position.x, cy = cam.position.y;
        cam.position.set(Math.round(cx + shakeX), Math.round(cy + shakeY), 0);
        cam.update();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        batch.setProjectionMatrix(cam.combined);
        shapes.setProjectionMatrix(cam.combined);

        // 1) 타일과 바닥 장식 (깔개, 봉인 문양, 뼈, 지름길, 귀환문)
        batch.begin();
        tiles.render(batch, cam);
        drawFloorDecals(w);
        batch.end();

        // 3) 바닥 경고 채우기
        drawTelegraphFill(w);

        // 4) 발 위치 기준 정렬
        batch.begin();
        collectItems(w);
        items.sort(Comparator.comparingDouble((Item i) -> -i.y));
        for (Item i : items) i.draw.draw();
        items.clear();
        drawHeroAttackFx(w);
        fx.drawParticles(batch, tx);
        fx.drawDust(batch, tx);
        batch.end();


        // 5) 경고 테두리는 장식물 위에
        drawTelegraphOutline(w);

        // 6) 조명
        updateAmbient(w);
        lighting.begin(batch, cam, ambient);
        drawLights(w);
        lighting.end(batch);
        viewport.apply(); // FBO.end resets the GL viewport; preserve letterboxing.
        lighting.apply(batch, cam);
        drawBladeTrail(w); // Luminous edge above bodies and lighting, tied to the blade angle.

        // 7) 불꽃(조명 후 밝게)과 월드 위 글자
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        drawFlames(w);
        drawProjectiles(w);
        drawAlerts(w);
        drawWorldLabels(w);
        drawAim(w);
        fx.drawIcons(batch);
        fx.drawTexts(batch, fonts.outlined);
        batch.end();

        if (debug) drawDebug(w);

        cam.position.set(cx, cy, 0);
        cam.update();
    }

    // ---- 바닥 ----

    private boolean visible(float x, float y, float margin) {
        return Math.abs(x - cam.position.x) < VIEW_W / 2f + margin && Math.abs(y - cam.position.y) < VIEW_H / 2f + margin;
    }

    private void drawFloorDecals(GameWorld w) {
        drawSpawnMarks(w);
        for (MapObject d : w.map.decor) {
            if (!isGroundDecal(d.type) || !visible(d.x(), d.y(), 220)) continue;
            if ("boss_seal".equals(d.type)) batch.setColor(1, 1, 1, 0.35f + 0.1f * MathUtils.sin(w.time * 1.5f));
            drawProp(d.type, d.x(), d.y());
            batch.setColor(Color.WHITE);
        }
        for (MapObject o : w.map.interacts) {
            if (!visible(o.x(), o.y(), 100)) continue;
            if ("shortcut".equals(o.type)) {
                // 바닥에 새겨진 구역 색 문양. 열리면 빛기둥이 선다, 아직 못 여는 것은 잿빛
                boolean open = w.state.hasShortcut(o.prop("zone"));
                float[] c = w.b.zone(o.prop("zone")).oreColor;
                if (open) batch.setColor(c[0], c[1], c[2], 0.8f + 0.2f * MathUtils.sin(w.time * 3f));
                else batch.setColor(0.5f, 0.5f, 0.55f, 0.55f);
                drawProp("rune_circle", o.x(), o.y() - 12);
                batch.setColor(Color.WHITE);
                if (open) {
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                    batch.setColor(c[0], c[1], c[2], 0.3f + 0.12f * MathUtils.sin(w.time * 3f));
                    batch.draw(tx.glow, o.x() - 26, o.y() - 14, 52, 96);
                    batch.setColor(Color.WHITE);
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
                }
            } else if ("return".equals(o.type)) {
                batch.setColor(1, 1, 1, 0.75f + 0.25f * MathUtils.sin(w.time * 2.5f));
                drawProp("rune_circle", o.x(), o.y() - 12);
                batch.setColor(Color.WHITE);
            }
        }
    }

    /** 소환 표식: 적이 나올 자리에 문양이 차오른다. 강화 개체는 구역 색으로 미리 알린다. */
    private void drawSpawnMarks(GameWorld w) {
        if (w.combat == null) return;
        for (GameWorld.SpawnMark m : w.combat.marks) {
            float p = 1f - m.t / Math.max(0.001f, m.total);
            float[] c = m.spawn.elite ? w.b.zone(w.combat.room.zone).eliteTint : MARK_COLOR;
            batch.setColor(c[0], c[1], c[2], 0.35f + 0.65f * p);
            drawProp("rune_circle", m.x, m.y - 8);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            batch.setColor(c[0], c[1], c[2], 0.5f * p);
            float gw = 20 + 26 * p, gh = 30 + 70 * p;
            batch.draw(tx.glow, m.x - gw / 2f, m.y - 6, gw, gh);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        batch.setColor(Color.WHITE);
    }

    private static final float[] MARK_COLOR = {1f, 0.45f, 0.4f};

    private static boolean isGroundDecal(String type) {
        switch (type) {
            case "rug":
            case "boss_seal":
            case "lich":
                return true;
            default:
                return false;
        }
    }

    private void drawArchDoor(GameWorld w, DoorDef d, float open) {
        SpriteLibrary.Entry e = sprites.get(w.isZoneGate(d) ? "door_wood" : "door_grate");
        com.badlogic.gdx.graphics.g2d.Animation<TextureRegion> a = e.anim("idle");
        int n = SpriteLibrary.frameCount(a);
        int idx = Math.min(n - 1, Math.round(open * (n - 1)));
        TextureRegion r = a.getKeyFrame(idx * a.getFrameDuration() + a.getFrameDuration() * 0.5f);
        float sw = r.getRegionWidth() * e.scale, sh = r.getRegionHeight() * e.scale;
        batch.draw(r, d.bounds.x + d.bounds.width / 2f - sw / 2f, d.bounds.y, sw, sh);
        if (w.isZoneGate(d) && open < 0.5f) {
            float pulse = 0.7f + 0.3f * MathUtils.sin(w.time * 3f);
            batch.setColor(1, 1, 1, pulse);
            drawProp("rune_circle", d.bounds.x + d.bounds.width / 2f, d.bounds.y + 20);
            batch.setColor(Color.WHITE);
        }
    }

    /** 잠긴 문: 창살. 세로 통로를 막는 문은 정면 창살, 가로 통로를 막는 문은 옆에서 본 창살. */
    private void drawDoor(GameWorld w, DoorDef d) {
        Rectangle b = d.bounds;
        if (d.horizontal()) {
            SpriteLibrary.Entry e = sprites.get("gate_h");
            if (e == null) return;
            TextureRegion r = e.frame("idle", 0);
            for (float x = b.x; x < b.x + b.width - 1; x += r.getRegionWidth()) {
                float ww = Math.min(r.getRegionWidth(), b.x + b.width - x);
                batch.draw(r.getTexture(), x, b.y + 4, ww, r.getRegionHeight(), r.getRegionX(), r.getRegionY(),
                        (int) ww, r.getRegionHeight(), false, false);
            }
        } else {
            SpriteLibrary.Entry e = sprites.get("gate_v");
            if (e == null) return;
            TextureRegion r = e.frame("idle", 0);
            float cx = b.x + b.width / 2f - r.getRegionWidth() / 2f;
            for (float y = b.y; y < b.y + b.height + 20; y += r.getRegionHeight()) {
                batch.draw(r, cx, y);
            }
        }
        if (w.isZoneGate(d)) {
            float pulse = 0.7f + 0.3f * MathUtils.sin(w.time * 3f);
            batch.setColor(1, 1, 1, pulse);
            drawProp("rune_circle", b.x + b.width / 2f, b.y + b.height / 2f - 12);
            batch.setColor(Color.WHITE);
        }
    }

    private void shadow(float x, float y, float width) {
        float h = width * 0.42f;
        batch.draw(tx.shadow, x - width / 2f, y - h / 2f, width, h);
    }

    // ---- 정렬되는 오브젝트 ----

    private void collectItems(GameWorld w) {
        for (MapObject d : w.map.decor) {
            float x = d.x(), y = d.y();
            if (isGroundDecal(d.type) || !visible(x, y, 120)) continue;
            String type = d.type;
            if (type.startsWith("torch_")) {
                items.add(new Item(y + 64, () -> drawProp("torch", x, y)));
                continue;
            }
            switch (type) {
                case "banner_red":
                case "banner_blue":
                    items.add(new Item(y + 64, () -> drawProp(type, x, y)));
                    break;
                case "campfire":
                    items.add(new Item(y, () -> drawProp("campfire_log", x, y - 8)));
                    break;
                case "pillar":
                    items.add(new Item(y, () -> {
                        shadow(x, y, 34);
                        drawProp(type, x, y - 6);
                    }));
                    break;
                default:
                    items.add(new Item(y, () -> {
                        SpriteLibrary.Entry pe = sprites.get(sprites.resolve(type, (int) (x * 7 + y * 13)));
                        if (pe != null && !pe.fromAsset) shadow(x, y, 30);
                        drawProp(type, x, y - 6);
                    }));
                    break;
            }
        }
        for (MapObject o : w.map.interacts) {
            if (!visible(o.x(), o.y(), 120)) continue;
            if ("shrine".equals(o.type)) items.add(new Item(o.y(), () -> drawShrine(w, o)));
        }
        for (RoomDef r : w.map.rooms) {
            boolean has = w.hasChest(r);
            if (!has && !chestSeen.containsKey(r.id)) continue;
            w.chestSpot(r, chestPos);
            if (!visible(chestPos.x, chestPos.y, 120)) continue;
            float x = chestPos.x, y = chestPos.y;
            if (has) {
                // 나타난 뒤 지난 시간 (통통 튀어 오르며 등장)
                float age = chestSeen.merge(r.id, Gdx.graphics.getDeltaTime(), (a, b) -> a < 0 ? a : a + b);
                if (age < 0) chestSeen.put(r.id, 0f);
                items.add(new Item(y, () -> drawChest(x, y, 0f, Math.max(0, chestSeen.get(r.id)))));
            } else {
                // 열린 뒤: 음수로 열림 진행을 센다
                float t = chestSeen.get(r.id);
                t = t >= 0 ? -0.0001f : t - Gdx.graphics.getDeltaTime();
                if (t < -1.3f) chestSeen.remove(r.id);
                else {
                    chestSeen.put(r.id, t);
                    float open = -t;
                    items.add(new Item(y, () -> drawChest(x, y, open, 9f)));
                }
            }
        }
        // 상자에서 나와 남아 있는 회복약 (상자가 사라진 뒤에도 그 자리에 떠 있다)
        if (w.room != null && w.pendingHeal(w.room) != null) {
            Balance.ChestHealDef heal = w.pendingHeal(w.room);
            w.chestSpot(w.room, chestPos);
            float px = chestPos.x, py = chestPos.y;
            items.add(new Item(py - 2, () -> drawPotion(heal.sprite, px, py)));
        }
        for (DoorDef d : w.map.doors) {
            if (!visible(d.bounds.x, d.bounds.y, 160)) continue;
            boolean locked = w.isDoorLocked(d);
            float o = doorOpen.getOrDefault(d, locked ? 0f : 1f);
            o = locked ? Math.max(0, o - Gdx.graphics.getDeltaTime() * 5f) : Math.min(1, o + Gdx.graphics.getDeltaTime() * 4f);
            doorOpen.put(d, o);
            final float open = o;
            if ("N".equals(d.wall) && sprites.get(w.isZoneGate(d) ? "door_wood" : "door_grate") != null) {
                // 위쪽 벽의 아치 문 (CraftPix): 닫힘 → 열림 애니메이션
                items.add(new Item(d.bounds.y, () -> drawArchDoor(w, d, open)));
            } else if (locked) {
                items.add(new Item(d.bounds.y + 2, () -> drawDoor(w, d)));
            }
        }
        // 광맥 (전투가 끝난 방에서 캘 수 있다)
        if (w.room != null && w.room.veins) {
            MineRoomState m = w.state.mineRoom(w.room.id);
            if (m != null) {
                for (VeinState v : m.veins) {
                    if (v.mined) continue;
                    MapObject o = w.map.vein(v.candidateId);
                    if (o == null) continue;
                    float x = o.x(), y = o.y();
                    String zone = w.room.zone;
                    items.add(new Item(y, () -> drawVein(w, zone, x, y, v.candidateId)));
                }
            }
        }
        // 적과 보스
        if (w.combat != null) {
            for (Enemy e : w.combat.enemies) {
                items.add(new Item(e.y, () -> drawEnemy(w, e)));
            }
        }
        // 용사
        items.add(new Item(w.hero.y, () -> drawHero(w, 1f)));
        // 주머니에서 꺼낸 대장간 (모루): 허리춤에서 튀어나와 발 앞에 내려앉는다
        forgeOut = MathUtils.clamp(forgeOut + (forgeShown ? 1 : -1) * Gdx.graphics.getDeltaTime() / 0.32f, 0, 1);
        if (forgeOut > 0) {
            float side = w.hero.faceLeft ? -1 : 1, k = forgeOut;
            float ax = w.hero.x + side * 34 * k, ay = w.hero.y - 6 + MathUtils.sin(k * MathUtils.PI) * 22;
            items.add(new Item(w.hero.y - 7, () -> {
                SpriteLibrary.Entry e = sprites.get("anvil");
                if (e == null) return;
                TextureRegion r = e.frame("idle", 0);
                float sc = e.scale * (0.35f + 0.65f * k);
                if (k >= 1) shadow(ax, ay + 2, 34);
                batch.draw(r, ax - r.getRegionWidth() * sc / 2f, ay, r.getRegionWidth() * sc, r.getRegionHeight() * sc);
            }));
        }
    }

    private void drawProp(String key, float x, float footY) {
        SpriteLibrary.Entry e = sprites.get(sprites.resolve(key, (int) (x * 7 + footY * 13)));
        if (e == null) return;
        TextureRegion r = e.frame("idle", e.animated ? time + (x * 0.013f) : 0);
        if (r == null) return;
        float sw = r.getRegionWidth() * e.scale, sh = r.getRegionHeight() * e.scale;
        batch.draw(r, x - e.originX * e.scale, footY - e.originY * e.scale, sw, sh);
    }

    /** 상자가 나타난 뒤 지난 시간(양수) 또는 열린 뒤 지난 시간(음수). */
    private final java.util.Map<String, Float> chestSeen = new java.util.HashMap<>();
    private final Vector2 chestPos = new Vector2();

    /** 방을 돌파하면 나타나는 상자. open 초 동안 뚜껑이 열리고 그 뒤 서서히 사라진다. */
    private void drawChest(float x, float y, float open, float age) {
        SpriteLibrary.Entry e = sprites.get("chest");
        if (e == null) return;
        Animation<TextureRegion> a = e.anim("idle");
        int n = SpriteLibrary.frameCount(a);
        int idx = Math.min(n - 1, (int) (open / 0.09f));
        TextureRegion r = a.getKeyFrame(idx * a.getFrameDuration() + a.getFrameDuration() * 0.5f);
        float pop = age < 0.35f ? MathUtils.sin(age / 0.35f * MathUtils.PI) * 10f : 0;
        float alpha = open > 0.9f ? Math.max(0, 1f - (open - 0.9f) / 0.4f) : 1f;
        batch.setColor(1, 1, 1, alpha);
        // 그림자는 상자 밑변에 걸치게 (튀어 오를 때는 그림자가 작아진다)
        shadow(x, y - 4, 40 - pop);
        float sw = r.getRegionWidth() * e.scale, sh = r.getRegionHeight() * e.scale;
        batch.draw(r, x - e.originX * e.scale, y - 6 - e.originY * e.scale + pop, sw, sh);
        batch.setColor(Color.WHITE);
    }

    private void drawPotion(String sprite, float x, float y) {
        SpriteLibrary.Entry e = sprites.get(sprite);
        if (e == null) return;
        TextureRegion r = e.frame("idle", 0);
        float k = 2f, bob = 6 + MathUtils.sin(time * 3f) * 2.5f;
        shadow(x, y - 2, 22);
        float sw = r.getRegionWidth() * k, sh = r.getRegionHeight() * k;
        batch.draw(r, x - sw / 2f, y + bob, sw, sh);
    }

    /** 제단: 구역 정의의 그림 + 바닥 문양. 이미 바친 제단은 어둡게 식는다. */
    private void drawShrine(GameWorld w, MapObject o) {
        Balance.ShrineDef sh = w.b.zone(o.prop("zone")).shrine;
        boolean used = w.state.usedShrines.contains(o.name);
        float x = o.x(), y = o.y();
        batch.setColor(1, 1, 1, used ? 0.25f : 0.7f + 0.3f * MathUtils.sin(w.time * 2.5f));
        drawProp("rune_circle", x, y - 12);
        if (used) batch.setColor(0.5f, 0.48f, 0.55f, 1f);
        else batch.setColor(Color.WHITE);
        drawProp(sh.sprite, x, y - 6);
        batch.setColor(Color.WHITE);
    }

    private void drawVein(GameWorld w, String zone, float x, float y, String id) {
        shadow(x, y - 2, 36);
        String key = sprites.get("vein_" + zone) != null ? "vein_" + zone : "vein";
        drawProp(key, x, y - 6);
        // 반짝임
        float s = (MathUtils.sin(w.time * 4f + x) + 1) / 2f;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.setColor(1f, 0.95f, 0.7f, 0.5f * s);
        float sx = x - 8 + (Math.abs((int) x) % 13), sy = y + 4 + (Math.abs((int) y) % 7);
        batch.draw(tx.white, sx - 1, sy, 3, 1);
        batch.draw(tx.white, sx, sy - 1, 1, 3);
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        // 채굴 진행
        if (id.equals(w.miningVein) && w.miningProgress > 0) {
            float p = Math.min(1, w.miningProgress / w.miningHoldTime());
            batch.setColor(0, 0, 0, 0.7f);
            batch.draw(tx.white, x - 18, y + 24, 36, 6);
            batch.setColor(1f, 0.85f, 0.3f, 1);
            batch.draw(tx.white, x - 17, y + 25, 34 * p, 4);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawEnemy(GameWorld w, Enemy e) {
        SpriteLibrary.Entry s = sprites.get(e.spriteKey());
        if (s == null) return;
        boolean boss = e.isBoss();
        float hover = "watcher".equals(e.id) ? 8 : "boss_temple".equals(e.id) ? 6 : 0;
        // 그림자: 보스는 몸 너비만큼 넓게, 떠 있는 적은 작고 옅게
        float sw0 = boss ? e.radius * 4.6f : e.radius * 2.4f;
        if (hover > 0) batch.setColor(1, 1, 1, 0.6f);
        shadow(e.x, e.y + (boss ? 3 : 0), hover > 0 ? sw0 * 0.75f : sw0);
        batch.setColor(Color.WHITE);
        boolean moving = e.state == Enemy.State.CHASE;
        float t = e.animTime;
        TextureRegion r = s.frame(moving ? "move" : "idle", t);
        // 보스는 공격마다 예고·타격 자세가 따로 있다 (공격 정의의 anim)
        if (boss && !e.attackId.isEmpty() && w.combat != null && !w.combat.dying) {
            String pose = w.b.bossAttack(e.attackId).anim;
            // 준비 동작은 예고 시간에 맞춰 늘이고, 타격 동작은 판정이 시작된 순간부터 끝까지 이어서 재생한다 (회복 시간으로 넘어가도 계속)
            Animation<TextureRegion> tele = pose.isEmpty() ? null : s.anims.get(pose + "_tele");
            Animation<TextureRegion> hit = pose.isEmpty() ? null : s.anims.get(pose + "_hit");
            if (e.state == Enemy.State.TELEGRAPH && tele != null) {
                r = tele.getKeyFrame(Math.min(0.999f, e.stateTime / Math.max(0.001f, e.telegraphTime)) * tele.getAnimationDuration());
            } else if (hit != null && e.state == Enemy.State.ATTACK) {
                // 돌진처럼 판정이 긴 공격은 타격 자세의 첫 장면을 유지한다
                r = hit.getKeyFrame(e.dashing ? 0 : Math.min(e.stateTime, hit.getAnimationDuration() - 0.001f));
            } else if (hit != null && e.state == Enemy.State.RECOVER) {
                float at = (e.dashing ? 0 : e.activeTime) + e.stateTime;
                if (at < hit.getAnimationDuration()) r = hit.getKeyFrame(at);
            }
        }
        // 쓰러지는 동작: 사망 연출 시간에 맞춰 한 번 재생하고 마지막 장면에서 멈춘다
        Animation<TextureRegion> death = boss && w.combat != null && w.combat.dying ? s.anims.get("death") : null;
        if (death != null) {
            float p = 1f - w.combat.outro / Math.max(0.001f, w.combat.outroTotal);
            r = death.getKeyFrame(Math.min(0.999f, p * 1.15f) * death.getAnimationDuration());
        }
        float k = s.scale * e.drawScale();
        float sw = r.getRegionWidth() * k, sh = r.getRegionHeight() * k;
        boolean flip = e.faceLeft != s.facesLeft;
        float ox = s.originX * k;
        boolean dying = boss && w.combat != null && w.combat.dying;
        // 떠 있는 적만 위아래로 흔들린다. 땅을 딛는 보스는 발이 바닥에서 떨어지지 않는다
        float bob = hover > 0 && death == null ? hover + MathUtils.sin(t * (boss ? 2f : 3f)) * (boss ? 4 : 3) : 0;
        // 예고 중에는 몸을 살짝 웅크리거나 떤다
        float shakeX = dying ? (death == null || w.combat.outro > w.combat.outroTotal * 0.7f ? MathUtils.sin(w.time * 95f) * 3f : 0)
                : e.state == Enemy.State.TELEGRAPH ? MathUtils.sin(e.stateTime * 60f) * (boss ? 2.4f : 1.2f) : 0;
        float x0 = e.x - (flip ? sw - ox : ox) + shakeX;
        float y0 = e.y - s.originY * k + bob;
        // 강화 개체는 구역 색조를 입는다 (그림은 같다)
        float[] tint = e.tint;
        if (tint != null) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            batch.setColor(tint[0], tint[1], tint[2], 0.35f + 0.1f * MathUtils.sin(w.time * 5f));
            batch.draw(tx.glow, e.x - e.radius * 2.2f, e.y - e.radius, e.radius * 4.4f, e.radius * 2.6f);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        if (e.state == Enemy.State.TELEGRAPH) batch.setColor(1f, 0.82f, 0.78f, 1);
        else if (tint != null) batch.setColor(tint[0], tint[1], tint[2], 1);
        batch.draw(r.getTexture(), x0, y0, sw, sh, r.getRegionX(), r.getRegionY(), r.getRegionWidth(), r.getRegionHeight(), flip, false);
        batch.setColor(Color.WHITE);
        // 덧칠: 피격 번쩍임, 상태 이상 색, 보스 사망 시 하얗게 타오름
        Statuses.State st = Statuses.strongest(e);
        float glowA = 0;
        float gr = 1, gg = 1, gb = 1;
        if (st != null) {
            gr = st.def.color[0];
            gg = st.def.color[1];
            gb = st.def.color[2];
            glowA = st.active() ? 0.75f : 0.55f * st.gauge;
        }
        if (e.flash > 0) {
            gr = gg = gb = 1;
            glowA = Math.max(glowA, Math.min(1, e.flash / 0.12f) * 0.85f);
        }
        if (dying) {
            float p = 1f - w.combat.outro / Math.max(0.001f, w.combat.outroTotal);
            gr = gg = gb = 1;
            // 쓰러지는 그림이 있으면 처음에만 번쩍이고, 없으면 예전처럼 하얗게 타오른다
            glowA = death != null ? Math.max(0f, 0.7f * (1f - p * 2.5f)) : 0.35f + 0.65f * p * (0.7f + 0.3f * MathUtils.sin(w.time * 40f));
        }
        if (glowA > 0) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            batch.setColor(gr, gg, gb, glowA);
            batch.draw(r.getTexture(), x0, y0, sw, sh, r.getRegionX(), r.getRegionY(), r.getRegionWidth(), r.getRegionHeight(), flip, false);
            batch.setColor(Color.WHITE);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        if (!boss && (e.hp < e.maxHp || e.isElite())) {
            float bw = 28 * e.drawScale(), p = e.hp / (float) e.maxHp;
            batch.setColor(0, 0, 0, 0.7f);
            batch.draw(tx.white, e.x - bw / 2 - 1, y0 + sh + 3, bw + 2, 5);
            if (tint != null) batch.setColor(tint[0], tint[1], tint[2], 1);
            else batch.setColor(0.9f, 0.25f, 0.2f, 1);
            batch.draw(tx.white, e.x - bw / 2, y0 + sh + 4, bw * p, 3);
            // 쌓이는 중인 상태 이상 게이지
            if (st != null && !st.active()) {
                batch.setColor(st.def.color[0], st.def.color[1], st.def.color[2], 1);
                batch.draw(tx.white, e.x - bw / 2, y0 + sh + 1, bw * Math.min(1, st.gauge), 2);
            }
            batch.setColor(Color.WHITE);
        }
    }

    private void drawHero(GameWorld w, float alpha) {
        Hero h = w.hero;
        SpriteLibrary.Entry s = sprites.get("hero");
        if (alpha >= 1f) shadow(h.x, h.y, 26);
        String dir = Dir8.name4(h.facing);
        TextureRegion r = s.frame(h.moving ? "walk" : "idle", dir, h.walkTime);
        float sw = r.getRegionWidth() * s.scale, sh = r.getRegionHeight() * s.scale;
        boolean flip = !s.directional && h.faceLeft != s.facesLeft;
        float ox = s.originX * s.scale;
        if (h.dodgeTime > 0 && alpha >= 1f) {
            for (int i = 3; i >= 1; i--) {
                batch.setColor(0.45f, 0.8f, 1f, 0.1f * (4-i));
                batch.draw(r, h.x - ox - h.dodgeDx * i * 10,
                        h.y - s.originY * s.scale - h.dodgeDy * i * 10, sw, sh);
            }
            batch.setColor(Color.WHITE);
        }
        float a = alpha;
        if (h.invuln > 0 && alpha >= 1f) a = (int) (h.invuln * 20) % 2 == 0 ? 0.35f : 1f;
        float gait = h.moving ? MathUtils.sin(h.walkTime * MathUtils.PI2 * 6f) : 0;
        float bob = h.moving ? Math.abs(gait) * 1.7f : MathUtils.sin(h.animTime * 3f) * 0.3f;
        float rot = h.moving ? gait * 3f : 0;
        float leanX = 0, leanY = 0;
        float stretchX = 1, stretchY = 1;
        if (h.attack != null) {
            AttackInstance atk = h.attack;
            float pose = AttackMotion.lean(atk);
            leanX = Dir8.DX[atk.dir] * pose;
            leanY = Dir8.DY[atk.dir] * pose;
            rot += -(atk.combo%2==0?-1:1) * pose * 1.4f;
            stretchY = 1 - Math.abs(pose) * 0.012f;
            stretchX = 1 + Math.abs(pose) * 0.01f;
        }
        if (h.dodgeTime > 0) {
            float p = 1 - h.dodgeTime / w.b.hero.dodgeDuration;
            rot = (h.dodgeDx < 0 ? 1 : -1) * 360 * p;
            bob = MathUtils.sin(p * MathUtils.PI) * 8;
            stretchY = 0.82f;
            a *= 0.75f;
        }
        if (forgeOut > 0 && forgeOut < 1) {
            stretchY = 1f - 0.14f * MathUtils.sin(forgeOut * MathUtils.PI);
            stretchX = 1f + 0.08f * MathUtils.sin(forgeOut * MathUtils.PI);
        }
        if (h.dead) {
            float k = Math.min(1, h.deathTimer / 0.4f);
            rot = (h.faceLeft ? 90 : -90) * k;
            a *= 1f - Math.max(0, h.deathTimer - 0.5f) * 1.6f;
        }
        boolean mining = w.miningVein != null && w.miningProgress > 0;
        WeaponState ws = w.state.equippedWeapon();
        String tool = mining && sprites.get("pickaxe") != null ? "pickaxe" : ws != null ? ws.type : null;
        // 위를 볼 때와 평소 들고 있을 때는 무기가 몸 뒤에 있다
        boolean behind = h.attack == null && ("up".equals(dir) || (!mining && !"down".equals(dir)));
        if (tool != null && behind && !h.dead) drawTool(w, tool, a, mining);
        batch.setColor(h.hurtFlash > 0 && alpha >= 1 ? new Color(1, 0.55f, 0.55f, a) : new Color(1, 1, 1, a));
        float x0 = Math.round(h.x - (flip ? sw - ox : ox) + leanX);
        float y0 = Math.round(h.y - s.originY * s.scale + bob + leanY);
        batch.draw(r.getTexture(), x0, y0, sw / 2f, sh * 0.3f, sw, sh, stretchX, stretchY, rot,
                r.getRegionX(), r.getRegionY(), r.getRegionWidth(), r.getRegionHeight(), flip, false);
        batch.setColor(Color.WHITE);
        if (tool != null && !behind && !h.dead) drawTool(w, tool, a, mining);
    }

    private void drawAim(GameWorld w) {
        Hero h = w.hero;
        if (h.dead || w.state.equippedWeapon() == null) return;
        if (h.target != null) {
            Enemy e = h.target;
            float r = e.radius + 6;
            batch.setColor(1f, 0.8f, 0.35f, 0.95f);
            for (int sign : new int[]{-1, 1}) {
                float x = e.x + sign * r;
                batch.draw(tx.white, x - 1, e.hurtY() - 5, 2, 10);
                batch.draw(tx.white, sign < 0 ? x : x - 5, e.hurtY() + 4, 5, 2);
            }
        } else {
            float x = h.x + Dir8.DX[h.aimDir] * 36;
            float y = h.hurtY() + Dir8.DY[h.aimDir] * 36;
            batch.setColor(1, 0.85f, 0.45f, 0.6f);
            batch.draw(tx.white, x - 2, y - 2, 4, 4);
        }
        batch.setColor(Color.WHITE);
    }

    private static final float[] SHOT_COLOR = {1f, 0.3f, 0.48f};

    /** 적 탄: 쏜 쪽이 정한 색의 빛나는 구슬 (마왕은 보랏빛). */
    private void drawProjectiles(GameWorld w) {
        if (w.combat == null) return;
        for (Projectile p : w.combat.projectiles) {
            float[] c = p.color != null ? p.color : SHOT_COLOR;
            float pulse = 1f + 0.12f * MathUtils.sin(w.time * 18f + p.x * 0.1f);
            batch.setColor(c[0], c[1], c[2], 0.25f);
            batch.draw(tx.glow, p.x - 20 * pulse, p.y - 20 * pulse, 40 * pulse, 40 * pulse);
            batch.setColor(c[0] * 0.3f, c[1] * 0.3f, c[2] * 0.3f, 1);
            batch.draw(tx.glow, p.x - 10, p.y - 10, 20, 20);
            batch.setColor(c[0], c[1], c[2], 1);
            batch.draw(tx.white, Math.round(p.x) - 5, Math.round(p.y) - 3, 10, 6);
            batch.draw(tx.white, Math.round(p.x) - 3, Math.round(p.y) - 5, 6, 10);
            batch.draw(tx.white, Math.round(p.x) - 4, Math.round(p.y) - 4, 8, 8);
            batch.setColor(Math.min(1, c[0] + 0.5f), Math.min(1, c[1] + 0.5f), Math.min(1, c[2] + 0.5f), 1);
            batch.draw(tx.white, Math.round(p.x) - 3, Math.round(p.y), 3, 3);
        }
        batch.setColor(Color.WHITE);
    }

    /** 무기/곡괭이: 평소에는 손에 들고, 공격 중에는 조준 방향으로 휘두르며, 채굴 중에는 광맥을 내리찍는다. */
    private void drawTool(GameWorld w, String key, float alpha, boolean mining) {
        Hero h = w.hero;
        SpriteLibrary.Entry s = sprites.get(key);
        if (s == null) return;
        TextureRegion r = s.frame("idle", 0);
        float sw = r.getRegionWidth() * s.scale, sh = r.getRegionHeight() * s.scale;
        float hx, hy, angle;
        AttackInstance a = h.attack;
        if (mining) {
            MapObject v = w.map.vein(w.miningVein);
            float base = v != null ? MathUtils.atan2(v.y() - h.hurtY(), v.x() - h.x) * MathUtils.radiansToDegrees : -90;
            float k = (w.miningProgress * 3f) % 1f; // 1초에 세 번 내리찍기
            float swing = k < 0.6f ? 70 - 70 * (k / 0.6f) : 70 * ((k - 0.6f) / 0.4f);
            boolean left = Math.abs(base) > 90;
            angle = base + (left ? -swing : swing);
            hx = h.x;
            hy = h.hurtY();
        } else if (a != null) {
            float base = MathUtils.atan2(Dir8.DY[a.dir], Dir8.DX[a.dir]) * MathUtils.radiansToDegrees;
            float swing = a.kind == AttackInstance.Kind.SHOCKWAVE ? 0 : AttackMotion.angle(a,a.t);
            angle = base + swing;
            float lean=AttackMotion.lean(a);
            hx = h.x + MathUtils.cosDeg(angle) * 15 + Dir8.DX[a.dir]*lean;
            hy = h.hurtY() + MathUtils.sinDeg(angle) * 12 + Dir8.DY[a.dir]*lean + 3 + AttackMotion.lift(a);
        } else {
            String d = Dir8.name4(h.facing);
            boolean left = "left".equals(d) || ("up".equals(d));
            angle = MathUtils.atan2(Dir8.DY[h.aimDir], Dir8.DX[h.aimDir]) * MathUtils.radiansToDegrees + 35;
            hx = h.x + (left ? -9 : 9);
            hy = h.y + 14 + (h.moving ? MathUtils.sin(h.walkTime * MathUtils.PI2 * 6) * 1.5f : 0);
        }
        batch.setColor(1, 1, 1, alpha);
        // 내려찍으려고 치켜든 무기는 화면 쪽으로 다가온 만큼 조금 크게 보인다
        float weaponScale = a != null && a.kind == AttackInstance.Kind.NORMAL ? 1.85f + AttackMotion.lift(a) * 0.022f : 1.4f;
        batch.draw(r, hx - s.originX * s.scale, hy - s.originY * s.scale, s.originX * s.scale, s.originY * s.scale,
                sw, sh, weaponScale, weaponScale, angle - s.pointAngle);
        if ("daggers".equals(key)) {
            // 다른 손의 단검: 반대쪽으로 벌려 든다
            float a2 = angle + (a != null ? -55 : 70);
            float ox2 = a != null ? MathUtils.cosDeg(a2) * 12 : (hx < h.x ? 16 : -16), oy2 = a != null ? MathUtils.sinDeg(a2) * 10 : 0;
            float bx = a != null ? h.x : hx, by2 = a != null ? h.hurtY() + 3 : hy;
            batch.draw(r, bx + ox2 - s.originX * s.scale, by2 + oy2 - s.originY * s.scale, s.originX * s.scale, s.originY * s.scale,
                    sw, sh, weaponScale, weaponScale, a2 - s.pointAngle);
        }
        batch.setColor(Color.WHITE);
    }

    /** 공격 이펙트는 실제 판정 영역 전체를 덮는다 (판정이 이펙트보다 넓지 않음). */
    private void drawHeroAttackFx(GameWorld w) {
        AttackInstance a = w.hero.attack;
        if (a == null) return;
        AttackInstance.Phase ph = a.phase();
        if (a.kind == AttackInstance.Kind.SHOCKWAVE) {
            if (ph == AttackInstance.Phase.WINDUP) {
                float k = a.t / a.windup;
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                batch.setColor(0.4f, 0.7f, 1f, 0.5f * k);
                float rad = 20 + 30 * k;
                batch.draw(tx.glow, w.hero.x - rad, w.hero.hurtY() - rad, rad * 2, rad * 2);
                batch.setColor(Color.WHITE);
                batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            }
            return;
        }
    }

    // ---- 경고 ----

    private void drawTelegraphFill(GameWorld w) {
        boolean any = w.combat != null || w.hero.attack != null;
        if (!any) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (w.combat != null) {
            for (Enemy e : w.combat.enemies) {
                if (e.shape == null) continue;
                if (e.state == Enemy.State.TELEGRAPH) {
                    if (!forecast(w, e)) continue;
                    float p = Math.min(1, e.stateTime / e.telegraphTime);
                    // 탄만 쏘는 공격의 조준선은 옅게 (닿아도 다치지 않는다. 다치게 하는 것은 탄이다)
                    float k = e.shotsOnly() ? 0.55f : 1f;
                    shapes.setColor(WARN.r, WARN.g, WARN.b, 0.16f * k);
                    fillShape(e.shape, 1f);
                    for (HitShape x : e.extraShapes) fillShape(x, 1f);
                    shapes.setColor(WARN.r, WARN.g, WARN.b, 0.30f * k);
                    fillShape(e.shape, p); // 채워지는 영역
                    for (HitShape x : e.extraShapes) fillShape(x, p);
                } else if (beamFx(w, e) && (e.state == Enemy.State.ATTACK || (e.state == Enemy.State.RECOVER && e.stateTime < BEAM_FADE))) {
                    // 마법 공격: 판정 자리에 광선과 빛기둥이 터지고, 판정이 끝난 뒤 잠깐 남아 사그라든다
                    float k = e.state == Enemy.State.ATTACK ? 1f : 1f - e.stateTime / BEAM_FADE;
                    float t = e.state == Enemy.State.ATTACK ? e.stateTime : e.activeTime + e.stateTime;
                    beam(e.shape, k, t);
                    for (HitShape x : e.extraShapes) beam(x, k, t);
                } else if (e.state == Enemy.State.ATTACK) {
                    shapes.setColor(1f, 0.85f, 0.6f, 0.45f);
                    HitShape hs = e.activeHit();
                    if (hs != null) fillShape(hs, 1f);
                    if (hs != null && !e.dashing) for (HitShape x : e.extraShapes) fillShape(x, 1f);
                    if (hs != null && e.isBoss() && "spikes".equals(w.b.bossAttack(e.attackId).fx)) {
                        spikes(hs, e.stateTime);
                        for (HitShape x : e.extraShapes) spikes(x, e.stateTime);
                    }
                    if (e.dashing && showTelegraphs) {
                        shapes.setColor(WARN.r, WARN.g, WARN.b, 0.12f);
                        fillShape(e.shape, 1f);
                    }
                }
            }
        }
        // 용사 공격 판정 영역
        AttackInstance a = w.hero.attack;
        if (a != null) {
            AttackInstance.Phase ph = a.phase();
            float ox = a.originLocked ? a.originX : w.hero.x, oy = a.originLocked ? a.originY : w.hero.hurtY();
            float dx = Dir8.DX[a.dir], dy = Dir8.DY[a.dir];
            if (a.kind == AttackInstance.Kind.NORMAL) {
                // Blade and trail are drawn together above the actors below.
                // 무기 형태가 더한 검기·원형 충격은 판정 영역 그대로 바닥에 그린다
                if (a.form != null && a.t >= a.windup) {
                    float k = ph == AttackInstance.Phase.ACTIVE ? 1f
                            : Math.max(0, 1f - (a.t - a.windup - a.active) / a.recovery);
                    Balance.StatusDef sd = w.b.statusOrNull(a.form.status);
                    float cr = sd == null ? 1f : sd.color[0], cg = sd == null ? 0.8f : sd.color[1], cb = sd == null ? 0.45f : sd.color[2];
                    if (a.wave != null) {
                        shapes.setColor(cr, cg, cb, 0.5f * k);
                        fillShape(a.wave, 1f);
                        shapes.setColor(1f, 1f, 1f, 0.55f * k);
                        fillShape(HitShape.box(a.wave.ox, a.wave.oy, a.wave.dx, a.wave.dy, a.wave.length, a.wave.width * 0.3f), 1f);
                    }
                    if (a.burst != null) {
                        shapes.setColor(cr, cg, cb, 0.32f * k);
                        shapes.circle(a.burst.cx, a.burst.cy, a.burst.radius, 40);
                        shapes.setColor(1f, 1f, 1f, 0.3f * k);
                        shapes.circle(a.burst.cx, a.burst.cy, a.burst.radius * (1f - 0.6f * k), 32);
                    }
                }
            } else if (a.effectiveLength >= 0) {
                // 충격파: 몸에서 퍼져 나가는 옅은 파동 고리 (바닥에 눕힌 타원 두 겹)
                float p = MathUtils.clamp((a.t - a.windup) / (a.active + a.recovery), 0, 1);
                float ease = 1f - (1f - p) * (1f - p);
                for (int ring = 0; ring < 2; ring++) {
                    float q = ring == 0 ? ease : Math.max(0, ease - 0.22f) / 0.78f;
                    if (q <= 0) continue;
                    float rx = a.effectiveLength * q, ry = rx * 0.7f, fade = (1f - p) * (ring == 0 ? 1f : 0.6f);
                    for (int i = 0; i < 48; i++) {
                        float a0 = i * 7.5f, a1 = (i + 1) * 7.5f;
                        float x0 = ox + MathUtils.cosDeg(a0) * rx, y0 = oy + MathUtils.sinDeg(a0) * ry;
                        float x1 = ox + MathUtils.cosDeg(a1) * rx, y1 = oy + MathUtils.sinDeg(a1) * ry;
                        shapes.setColor(0.45f, 0.75f, 1f, 0.22f * fade);
                        shapes.rectLine(x0, y0, x1, y1, 16);
                        shapes.setColor(0.85f, 0.95f, 1f, 0.55f * fade);
                        shapes.rectLine(x0, y0, x1, y1, 4);
                    }
                }
            }
        }
        shapes.end();
    }

    /**
     * 이 적의 예고 영역을 바닥에 그릴지. 기본은 그리지 않고 머리 위의 ! 로만 알린다.
     * 다만 몸에서 떨어진 바닥에서 터지는 공격(표식·낙석·균열·광선)은 그 자리를 보여 주지 않으면 피할 단서가 없으므로 항상 그린다.
     */
    private boolean forecast(GameWorld w, Enemy e) {
        if (showTelegraphs) return true;
        if (!e.isBoss()) return "mark".equals(e.def.kind);
        switch (w.b.bossAttack(e.attackId).shape) {
            case "mark":
            case "scatter":
            case "trail":
            case "cross":
                return true;
            default:
                return false;
        }
    }

    /** 공격을 준비하는 적 머리 위의 느낌표. 뜨는 순간 톡 튀어나온다. */
    private void drawAlerts(GameWorld w) {
        if (w.combat == null || !w.combat.started() || w.combat.dying) return;
        BitmapFont f = fonts.outlined;
        for (Enemy e : w.combat.enemies) {
            // 보스는 공격마다 준비 자세가 따로 있고 몸을 크게 떨므로 느낌표를 띄우지 않는다
            if (e.state != Enemy.State.TELEGRAPH || !e.alive() || e.isBoss()) continue;
            SpriteLibrary.Entry s = sprites.get(e.spriteKey());
            float top = s == null ? e.radius * 3 : s.frame("idle", 0).getRegionHeight() * s.scale * e.drawScale();
            float pop = Math.min(1f, e.stateTime / 0.1f);
            float size = 0.6f * (1.35f - 0.35f * pop);
            f.getData().setScale(size);
            f.setColor(1f, 0.25f + 0.2f * MathUtils.sin(e.stateTime * 30f), 0.2f, 1f);
            layout.setText(f, "!");
            f.draw(batch, layout, e.x - layout.width / 2f, e.y + top + 6 + layout.height + (1f - pop) * 6f);
        }
        f.getData().setScale(0.5f);
        f.setColor(Color.WHITE);
    }

    private void drawTelegraphOutline(GameWorld w) {
        if (w.combat == null) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Enemy e : w.combat.enemies) {
            if (e.shape == null || e.state != Enemy.State.TELEGRAPH || !forecast(w, e)) continue;
            float blink = 0.65f + 0.35f * MathUtils.sin(e.stateTime * 18f);
            shapes.setColor(WARN.r, WARN.g * 1.4f, WARN.b, blink);
            outlineShape(e.shape);
            for (HitShape x : e.extraShapes) outlineShape(x);
            // 두 겹 테두리로 두께감
            if (e.shape.type == HitShape.Type.CIRCLE) {
                shapes.circle(e.shape.cx, e.shape.cy, e.shape.radius - 1, 40);
            }
        }
        shapes.end();
    }

    /** A tapered ribbon follows the blade, with a bright cutting edge and a fading inner band. */
    private void drawBladeTrail(GameWorld w) {
        AttackInstance a=w.hero.attack;
        if(a==null||a.kind!=AttackInstance.Kind.NORMAL||a.t<a.windup)return;
        float age=Math.max(0,a.t-a.windup-a.active);
        float fade=Math.max(0,1-age/0.13f);
        if(fade<=0)return;
        float base=MathUtils.atan2(Dir8.DY[a.dir],Dir8.DX[a.dir])*MathUtils.radiansToDegrees;
        if(a.slam) {
            drawSlamTrail(a,w.hero.x,w.hero.hurtY(),fade);
            return;
        }
        float sign=a.combo%2==0?-1:1;
        float end=MathUtils.clamp(AttackMotion.angle(a,Math.min(a.t,a.windup+a.active)),-70,70);
        float start=sign*70;
        float ox=w.hero.x,oy=w.hero.hurtY();
        float outer=a.length;
        boolean heavy=a.finisher||"hammer".equals(w.state.equipped);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for(int layer=0;layer<3;layer++) {
            for(int i=0;i<28;i++) {
                float u0=i/28f,u1=(i+1)/28f;
                float a0=base+MathUtils.lerp(start,end,u0),a1=base+MathUtils.lerp(start,end,u1);
                float taper0=(float)Math.sin(u0*Math.PI),taper1=(float)Math.sin(u1*Math.PI);
                float thickness=layer==0?(heavy?32:23):layer==1?10:3;
                float in0=outer-thickness*taper0,in1=outer-thickness*taper1;
                float opacity=fade*(layer==0?0.24f:layer==1?0.66f:0.98f)*(0.4f+0.6f*u1);
                if(layer==2)shapes.setColor(1,1,0.95f,opacity);
                else if(heavy)shapes.setColor(1,0.68f,0.17f,opacity);
                else shapes.setColor(0.42f,0.88f,1,opacity);
                float x0=ox+MathUtils.cosDeg(a0)*outer,y0=oy+MathUtils.sinDeg(a0)*outer;
                float x1=ox+MathUtils.cosDeg(a1)*outer,y1=oy+MathUtils.sinDeg(a1)*outer;
                float ix0=ox+MathUtils.cosDeg(a0)*in0,iy0=oy+MathUtils.sinDeg(a0)*in0;
                float ix1=ox+MathUtils.cosDeg(a1)*in1,iy1=oy+MathUtils.sinDeg(a1)*in1;
                shapes.triangle(x0,y0,x1,y1,ix1,iy1);
                shapes.triangle(x0,y0,ix1,iy1,ix0,iy0);
            }
        }
        if(heavy) {
            float p=AttackMotion.progress(a);
            float cx=ox+Dir8.DX[a.dir]*outer*0.72f,cy=oy+Dir8.DY[a.dir]*outer*0.72f;
            float size=18*(1-p*0.5f)*fade;
            shapes.setColor(1,0.9f,0.5f,fade*0.8f);
            shapes.triangle(cx-size,cy,cx,cy+3,cx+size,cy);
            shapes.triangle(cx-size,cy,cx,cy-3,cx+size,cy);
            shapes.triangle(cx,cy-size,cx+3,cy,cx,cy+size);
            shapes.triangle(cx,cy-size,cx-3,cy,cx,cy+size);
        }
        shapes.end();
    }

    /** 내려찍기: 위에서 떨어지는 짧은 궤적과, 찍은 자리에서 퍼지는 충격 고리. */
    private void drawSlamTrail(AttackInstance a,float ox,float oy,float fade) {
        float p=AttackMotion.progress(a);
        float dx=Dir8.DX[a.dir],dy=Dir8.DY[a.dir];
        float cx=ox+dx*a.length*0.72f,cy=oy+dy*a.length*0.72f;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        // 떨어지는 궤적 (찍는 순간에만)
        float drop=Math.max(0,1-p/0.5f);
        if(drop>0) {
            shapes.setColor(1,0.68f,0.17f,0.5f*drop);
            shapes.rectLine(cx,cy+46*drop+10,cx,cy,16);
            shapes.setColor(1,1,0.95f,0.9f*drop);
            shapes.rectLine(cx,cy+46*drop+10,cx,cy,5);
        }
        // 충격 고리: 바닥에 눕힌 타원으로 퍼진다
        float grow=Math.min(1,(a.t-a.windup)/0.16f);
        float rx=a.width*0.5f*(0.35f+0.65f*grow),ry=rx*0.55f;
        for(int layer=0;layer<2;layer++) {
            float th=layer==0?9:3;
            if(layer==0)shapes.setColor(1,0.68f,0.17f,0.55f*fade*(1-grow*0.5f));
            else shapes.setColor(1,1,0.95f,0.9f*fade*(1-grow*0.5f));
            for(int i=0;i<32;i++) {
                float a0=i*11.25f,a1=(i+1)*11.25f;
                shapes.rectLine(cx+MathUtils.cosDeg(a0)*rx,cy+MathUtils.sinDeg(a0)*ry,cx+MathUtils.cosDeg(a1)*rx,cy+MathUtils.sinDeg(a1)*ry,th);
            }
        }
        float size=22*(1-p*0.5f)*fade;
        shapes.setColor(1,0.9f,0.5f,fade*0.85f);
        shapes.triangle(cx-size,cy,cx,cy+4,cx+size,cy);
        shapes.triangle(cx-size,cy,cx,cy-4,cx+size,cy);
        shapes.triangle(cx,cy-size,cx+4,cy,cx,cy+size);
        shapes.triangle(cx,cy-size,cx-4,cy,cx,cy+size);
        shapes.end();
    }

    private static final float BEAM_FADE = 0.28f;

    private boolean beamFx(GameWorld w, Enemy e) {
        return e.isBoss() && e.shape != null && !e.attackId.isEmpty() && "beam".equals(w.b.bossAttack(e.attackId).fx);
    }

    /** 보랏빛 마법: 띠 모양 판정은 광선, 원 모양 판정은 바닥의 폭발과 솟는 빛기둥. k 는 밝기(1 → 0). */
    private void beam(HitShape s, float k, float t) {
        float flick = 0.88f + 0.12f * MathUtils.sin(t * 70f);
        if (s.type == HitShape.Type.CIRCLE) {
            float grow = Math.min(1f, t / 0.06f);
            shapes.setColor(0.6f, 0.3f, 1f, 0.55f * k);
            shapes.circle(s.cx, s.cy, s.radius * grow, 40);
            shapes.setColor(0.86f, 0.66f, 1f, 0.7f * k);
            shapes.circle(s.cx, s.cy, s.radius * 0.62f * grow * flick, 32);
            shapes.setColor(1f, 1f, 1f, 0.85f * k);
            shapes.circle(s.cx, s.cy, s.radius * 0.3f * grow, 24);
            // 위로 솟는 빛기둥
            float h = s.radius * 3.4f * grow, w0 = s.radius * 0.8f * flick;
            shapes.setColor(0.7f, 0.42f, 1f, 0.42f * k);
            shapes.rect(s.cx - w0 / 2f, s.cy, w0, h);
            shapes.setColor(1f, 0.95f, 1f, 0.6f * k);
            shapes.rect(s.cx - w0 * 0.2f, s.cy, w0 * 0.4f, h);
        } else {
            shapes.setColor(0.6f, 0.3f, 1f, 0.6f * k);
            fillShape(HitShape.box(s.ox, s.oy, s.dx, s.dy, s.length, s.width * flick), 1f);
            shapes.setColor(0.86f, 0.66f, 1f, 0.8f * k);
            fillShape(HitShape.box(s.ox, s.oy, s.dx, s.dy, s.length, s.width * 0.55f * flick), 1f);
            shapes.setColor(1f, 1f, 1f, 0.95f * k);
            fillShape(HitShape.box(s.ox, s.oy, s.dx, s.dy, s.length, s.width * 0.22f), 1f);
        }
    }

    /** 원형 판정 안에서 솟아오르는 바위 가시 (판정 영역을 넘지 않는다). */
    private void spikes(HitShape c, float time) {
        if (c.type != HitShape.Type.CIRCLE) return;
        float rise = Math.min(1f, time / 0.07f);
        for (int i = 0; i < 6; i++) {
            float ang = i * 60f + (c.cx * 0.7f) % 60f, rr = i == 0 ? 0 : c.radius * 0.55f;
            float x = c.cx + MathUtils.cosDeg(ang) * rr, y = c.cy + MathUtils.sinDeg(ang) * rr * 0.6f - c.radius * 0.2f;
            float hw = c.radius * (i == 0 ? 0.26f : 0.17f), hh = c.radius * (i == 0 ? 1.15f : 0.75f) * rise;
            shapes.setColor(0.36f, 0.3f, 0.27f, 1f);
            shapes.triangle(x - hw, y, x + hw, y, x, y + hh);
            shapes.setColor(0.62f, 0.54f, 0.47f, 1f);
            shapes.triangle(x - hw, y, x - hw * 0.1f, y, x, y + hh);
            shapes.setColor(1f, 0.6f, 0.2f, 0.8f);
            shapes.rectLine(x - hw, y, x + hw, y, 2f);
        }
    }

    private void fillShape(HitShape s, float progress) {
        if (s.type == HitShape.Type.CIRCLE) {
            shapes.circle(s.cx, s.cy, s.radius * progress, 40);
        } else if (s.type == HitShape.Type.SECTOR) {
            float angle=MathUtils.atan2(s.dy,s.dx)*MathUtils.radiansToDegrees;
            shapes.arc(s.ox,s.oy,s.length*progress,angle-s.halfAngle,s.halfAngle*2,32);
        } else {
            HitShape p = HitShape.box(s.ox, s.oy, s.dx, s.dy, s.length * progress, s.width);
            float[] c = p.corners();
            shapes.triangle(c[0], c[1], c[2], c[3], c[4], c[5]);
            shapes.triangle(c[0], c[1], c[4], c[5], c[6], c[7]);
        }
    }

    private void outlineShape(HitShape s) {
        if (s.type == HitShape.Type.CIRCLE) {
            shapes.circle(s.cx, s.cy, s.radius, 40);
        } else if (s.type == HitShape.Type.SECTOR) {
            float angle=MathUtils.atan2(s.dy,s.dx)*MathUtils.radiansToDegrees;
            shapes.arc(s.ox,s.oy,s.length,angle-s.halfAngle,s.halfAngle*2,32);
        } else {
            float[] c = s.corners();
            shapes.polygon(c);
        }
    }

    // ---- 조명 ----

    private void drawLights(GameWorld w) {
        float t = w.time;
        float left = cam.position.x - VIEW_W / 2f - 300, right = cam.position.x + VIEW_W / 2f + 300;
        float bottom = cam.position.y - VIEW_H / 2f - 300, top = cam.position.y + VIEW_H / 2f + 300;
        for (MapObject d : w.map.decor) {
            float x = d.x(), y = d.y();
            if (x < left || x > right || y < bottom || y > top) continue;
            float flick = 0.9f + 0.1f * MathUtils.sin(t * 9f + x * 0.37f) + 0.05f * MathUtils.sin(t * 23f + y);
            switch (d.type) {
                case "torch_warm":
                    lighting.light(batch, x, y + 18, 230 * flick, 1f, 0.62f, 0.3f, 0.95f);
                    break;
                case "torch_blue":
                    lighting.light(batch, x, y + 18, 230 * flick, 0.4f, 0.6f, 1f, 0.95f);
                    break;
                case "torch_violet":
                    lighting.light(batch, x, y + 18, 230 * flick, 0.75f, 0.45f, 1f, 0.95f);
                    break;
                case "campfire":
                    lighting.light(batch, x, y, 360 * flick, 1f, 0.62f, 0.3f, 1.15f);
                    break;
                case "brazier":
                    lighting.light(batch, x, y + 10, 160 * flick, 1f, 0.6f, 0.3f, 0.9f);
                    break;
                case "brazier_blue":
                    lighting.light(batch, x, y + 10, 170 * flick, 0.4f, 0.62f, 1f, 0.95f);
                    break;
                case "brazier_violet":
                    lighting.light(batch, x, y + 10, 170 * flick, 0.75f, 0.45f, 1f, 0.95f);
                    break;
                case "furnace":
                    lighting.light(batch, x, y + 4, 170 * flick, 1f, 0.5f, 0.2f, 1f);
                    break;
                case "lantern_table":
                    lighting.light(batch, x, y + 12, 110 * flick, 1f, 0.8f, 0.45f, 0.8f);
                    break;
                case "candle":
                    lighting.light(batch, x, y + 10, 100 * flick, 1f, 0.75f, 0.45f, 0.75f);
                    break;
                case "cauldron":
                    lighting.light(batch, x, y + 24, 220 * flick, 1f, 0.55f, 0.25f, 1f);
                    break;
                case "fire_bowl":
                    lighting.light(batch, x, y + 20, 320 * flick, 1f, 0.6f, 0.28f, 1.1f);
                    break;
                default:
                    break;
            }
        }
        for (MapObject o : w.map.interacts) {
            if ("return".equals(o.type)) {
                lighting.light(batch, o.x(), o.y(), 110, 0.65f, 0.4f, 1f, 0.9f);
            } else if ("shortcut".equals(o.type)) {
                boolean open = w.state.hasShortcut(o.prop("zone"));
                float[] c = w.b.zone(o.prop("zone")).oreColor;
                lighting.light(batch, o.x(), o.y(), open ? 130 : 70, c[0], c[1], c[2], open ? 0.95f : 0.45f);
            }
        }
        for (MapObject o : w.map.interacts) {
            if ("shrine".equals(o.type) && !w.state.usedShrines.contains(o.name)) {
                lighting.light(batch, o.x(), o.y() + 14, 170, 0.75f, 0.5f, 1f, 0.9f);
            }
        }
        if (w.hasChest(w.room) || (w.room != null && w.pendingHeal(w.room) != null)) {
            w.chestSpot(w.room, chestPos);
            lighting.light(batch, chestPos.x, chestPos.y + 6, 120, 1f, 0.85f, 0.45f, 0.85f);
        }
        for (DoorDef d : w.map.doors) {
            if (w.isZoneGate(d) && w.isDoorLocked(d)) {
                lighting.light(batch, d.bounds.x + d.bounds.width / 2, d.bounds.y + d.bounds.height / 2, 120, 0.6f, 0.35f, 1f, 0.8f);
            }
        }
        if (w.combat != null) {
            for (GameWorld.SpawnMark m : w.combat.marks) {
                lighting.light(batch, m.x, m.y + 10, 90, 1f, 0.5f, 0.45f, 0.7f * (1f - m.t / Math.max(0.001f, m.total)));
            }
        }
        // 용사 주변은 항상 보이게
        Hero h = w.hero;
        float lf = 0.95f + 0.05f * MathUtils.sin(t * 7f);
        lighting.light(batch, h.x, h.y + 12, 260 * lf, 1f, 0.82f, 0.6f, 0.8f); // 허리의 랜턴
        if (h.attack != null && h.attack.kind == AttackInstance.Kind.SHOCKWAVE && h.attack.effectiveLength > 0) {
            AttackInstance sw = h.attack;
            float k = Math.max(0, 1f - (sw.t - sw.windup) / (sw.active + sw.recovery));
            lighting.light(batch, sw.originX, sw.originY, sw.effectiveLength * 1.1f, 0.5f, 0.75f, 1f, 0.9f * k);
        }
        if (w.combat != null) {
            for (Enemy e : w.combat.enemies) {
                if ("watcher".equals(e.id) || "boss_temple".equals(e.id)) {
                    lighting.light(batch, e.x, e.y + 16, 90, 0.7f, 0.45f, 1f, 0.6f);
                }
                // 경고 영역은 어둠 속에서도 보이게
                boolean casting = beamFx(w, e) && (e.state == Enemy.State.ATTACK || (e.state == Enemy.State.RECOVER && e.stateTime < BEAM_FADE));
                if ((e.state == Enemy.State.TELEGRAPH && e.shape != null && forecast(w, e)) || casting) {
                    for (HitShape x : e.extraShapes) {
                        if (x.type == HitShape.Type.CIRCLE) lighting.light(batch, x.cx, x.cy, x.radius * 1.6f, 1f, 0.5f, 0.4f, 0.6f);
                        else lighting.light(batch, x.ox + x.dx * x.length / 2f, x.oy + x.dy * x.length / 2f, x.length * 0.7f, 1f, 0.5f, 0.4f, 0.5f);
                    }
                    if (e.shape.type == HitShape.Type.CIRCLE) {
                        lighting.light(batch, e.shape.cx, e.shape.cy, e.shape.radius * 1.6f, 1f, 0.5f, 0.4f, 0.6f);
                    } else {
                        float mx = e.shape.ox + e.shape.dx * e.shape.length / 2f;
                        float my = e.shape.oy + e.shape.dy * e.shape.length / 2f;
                        lighting.light(batch, mx, my, Math.max(e.shape.length, e.shape.width) * 0.8f, 1f, 0.5f, 0.4f, 0.6f);
                    }
                }
            }
        }
        if (w.room != null && w.room.veins) {
            MineRoomState m = w.state.mineRoom(w.room.id);
            if (m != null) {
                for (VeinState v : m.veins) {
                    if (v.mined) continue;
                    MapObject o = w.map.vein(v.candidateId);
                    if (o != null) lighting.light(batch, o.x(), o.y() + 4, 70, 1f, 0.85f, 0.55f, 0.5f);
                }
            }
        }
    }

    private void drawFlames(GameWorld w) {
        float t = w.time;
        float left = cam.position.x - VIEW_W / 2f - 40, right = cam.position.x + VIEW_W / 2f + 40;
        float bottom = cam.position.y - VIEW_H / 2f - 40, top = cam.position.y + VIEW_H / 2f + 80;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (MapObject d : w.map.decor) {
            float x = d.x(), y = d.y();
            if (x < left || x > right || y < bottom || y > top) continue;
            boolean animTorch = sprites.get("torch") != null && sprites.get("torch").animated;
            boolean animBrazier = sprites.get("brazier") != null && sprites.get("brazier").animated;
            switch (d.type) {
                case "torch_warm":
                    if (!animTorch) flame(x, y + 16, 0.6f, t, 1f, 0.55f, 0.2f);
                    break;
                case "torch_blue":
                    if (!animTorch) flame(x, y + 16, 0.6f, t, 0.35f, 0.6f, 1f);
                    else flame(x, y + 30, 0.35f, t, 0.35f, 0.6f, 1f);
                    break;
                case "torch_violet":
                    if (!animTorch) flame(x, y + 16, 0.6f, t, 0.75f, 0.4f, 1f);
                    else flame(x, y + 30, 0.35f, t, 0.75f, 0.4f, 1f);
                    break;
                case "campfire":
                    flame(x - 6, y - 4, 1.3f, t + 0.3f, 1f, 0.45f, 0.12f);
                    flame(x + 6, y - 4, 1.2f, t + 0.9f, 1f, 0.45f, 0.12f);
                    flame(x, y - 2, 1.7f, t, 1f, 0.6f, 0.2f);
                    flame(x, y - 2, 0.9f, t + 0.5f, 1f, 0.9f, 0.5f);
                    break;
                case "brazier":
                    if (!animBrazier) flame(x, y + 14, 1.1f, t, 1f, 0.55f, 0.18f);
                    break;
                case "furnace":
                    batch.setColor(1f, 0.5f, 0.15f, 0.35f + 0.15f * MathUtils.sin(t * 7f));
                    batch.draw(tx.glow, x - 22, y - 8, 44, 30);
                    break;
                default:
                    break;
            }
        }
        batch.setColor(Color.WHITE);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void flame(float x, float y, float scale, float t, float r, float g, float b) {
        float flick = 1f + 0.12f * MathUtils.sin(t * 13f + x) + 0.08f * MathUtils.sin(t * 29f + y);
        float fw = 12 * scale, fh = 18 * scale * flick;
        batch.setColor(r, g, b, 0.9f);
        batch.draw(tx.flame, x - fw / 2f, y, fw, fh);
        batch.setColor(1f, Math.min(1, g + 0.35f), Math.min(1, b + 0.3f), 0.8f);
        batch.draw(tx.flame, x - fw / 4f, y, fw / 2f, fh * 0.6f);
        batch.setColor(r, g, b, 0.25f);
        batch.draw(tx.glow, x - 20 * scale, y - 8 * scale, 40 * scale, 40 * scale);
    }

    // ---- 월드 위 글자 ----

    private void drawWorldLabels(GameWorld w) {
        BitmapFont f = fonts.small;
        // 잠긴 구역 문의 구체적인 조건
        for (DoorDef d : w.map.doors) {
            if (!w.isZoneGate(d) || !w.isDoorLocked(d)) continue;
            // 화면에 보이는 문에만 표시한다
            float l = cam.position.x - VIEW_W / 2f, b0 = cam.position.y - VIEW_H / 2f;
            if (d.bounds.x + d.bounds.width < l || d.bounds.x > l + VIEW_W
                    || d.bounds.y + d.bounds.height < b0 || d.bounds.y > b0 + VIEW_H) continue;
            String reason = w.ruleLockReason(d);
            if (reason == null) continue;
            float x = d.bounds.x + d.bounds.width / 2f;
            float y = d.bounds.y + d.bounds.height + 26;
            if (!d.horizontal()) y = d.bounds.y + d.bounds.height + 18;
            labelBox(f, "봉인 · " + reason, x, y, new Color(0.85f, 0.75f, 1f, 1f));
        }
        // 가까운 잠긴 문 안내
        if (w.hintDoor != null && !w.isZoneGate(w.hintDoor)) {
            String reason = w.doorLockReason(w.hintDoor);
            if (reason != null) {
                Rectangle b = w.hintDoor.bounds;
                labelBox(f, reason, b.x + b.width / 2f, b.y + b.height + 24, new Color(1f, 0.85f, 0.7f, 1f));
            }
        }
        // 상호작용 안내 (안내 대상 = 실행 대상)
        if (w.focus != null && !w.hero.dead) {
            Color c = w.focus.enabled ? Color.WHITE : new Color(0.7f, 0.7f, 0.75f, 1f);
            boolean vein = w.focus.kind == GameWorld.FocusKind.VEIN;
            // 광맥 안내는 용사 머리 위에 띄워 용사를 가리지 않는다
            labelBox(f, w.focus.label, vein ? w.hero.x : w.focus.x, vein ? w.hero.y + 58 : w.focus.y + 14, c);
        }
    }

    private void labelBox(BitmapFont f, String text, float cx, float y, Color color) {
        layout.setText(f, text);
        float pw = layout.width + 12, ph = layout.height + 10;
        // 화면 밖으로 잘리지 않게 안쪽으로 당긴다
        float minX = cam.position.x - VIEW_W / 2f + 8, maxX = cam.position.x + VIEW_W / 2f - 8 - pw;
        float x0 = MathUtils.clamp(cx - pw / 2f, minX, Math.max(minX, maxX)), y0 = y - ph / 2f;
        batch.setColor(0.07f, 0.06f, 0.09f, 0.88f);
        batch.draw(tx.white, x0, y0, pw, ph);
        batch.setColor(0.72f, 0.68f, 0.62f, 1f);
        batch.draw(tx.white, x0, y0, pw, 1);
        batch.draw(tx.white, x0, y0 + ph - 1, pw, 1);
        batch.draw(tx.white, x0, y0, 1, ph);
        batch.draw(tx.white, x0 + pw - 1, y0, 1, ph);
        // 꼬리
        batch.draw(tx.white, MathUtils.clamp(cx, x0 + 4, x0 + pw - 4) - 2, y0 - 3, 4, 3);
        batch.setColor(Color.WHITE);
        f.setColor(color);
        f.draw(batch, text, x0 + 6, y0 + ph - 5);
        f.setColor(Color.WHITE);
    }

    private void drawDebug(GameWorld w) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.GREEN);
        Rectangle b = w.hero.body();
        shapes.rect(b.x, b.y, b.width, b.height);
        shapes.circle(w.hero.hurtX(), w.hero.hurtY(), w.hero.hurtRadius(), 16);
        if (w.combat != null) {
            shapes.setColor(Color.YELLOW);
            for (Enemy e : w.combat.enemies) shapes.circle(e.x, e.hurtY(), e.radius, 16);
        }
        if (w.hero.attack != null && w.hero.attack.phase() == AttackInstance.Phase.ACTIVE) {
            shapes.setColor(Color.CYAN);
            outlineShape(w.hero.attack.shape);
        }
        shapes.setColor(Color.MAGENTA);
        for (Rectangle s : w.map.solids) shapes.rect(s.x, s.y, s.width, s.height);
        for (RoomDef r : w.map.rooms) shapes.rect(r.bounds.x, r.bounds.y, r.bounds.width, r.bounds.height);
        shapes.end();
    }

    @Override
    public void dispose() {
        tiles.dispose();
        lighting.dispose();
    }

    /** 다른 화면에서 쓰는 애니메이션 조회 도우미. */
    public static TextureRegion frame(SpriteLibrary lib, String key, String anim, float t) {
        SpriteLibrary.Entry e = lib.get(key);
        if (e == null) return null;
        Animation<TextureRegion> a = e.anim(anim);
        return a == null ? null : a.getKeyFrame(t);
    }
}
