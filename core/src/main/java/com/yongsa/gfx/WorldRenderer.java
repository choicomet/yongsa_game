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

    private boolean snapCamera = true;
    private final Vector3 tmp3 = new Vector3();

    private static final Color WARN = new Color(1f, 0.32f, 0.18f, 1f);
    /** 테마별 기본 밝기: 캠프, 폐광, 요새, 신전. */
    private static final Color[] AMBIENTS = {
            new Color(0.52f, 0.46f, 0.45f, 1f), new Color(0.46f, 0.41f, 0.39f, 1f),
            new Color(0.38f, 0.43f, 0.52f, 1f), new Color(0.43f, 0.37f, 0.52f, 1f)};
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
        } else if (r != null && (r.kind == RoomDef.Kind.CAMP || r.kind == RoomDef.Kind.BOSS)) {
            // 캠프와 보스방은 방 중심 고정 시야
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
            float k = 1f - (float) Math.exp(-dt * 7f);
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
        cam.position.add(shakeX, shakeY, 0);
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
        // 가려진 용사를 위한 반투명 실루엣
        if (!w.hero.dead) drawHero(w, 0.28f);
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
        lighting.apply(batch, cam);

        // 7) 불꽃(조명 후 밝게)과 월드 위 글자
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        drawFlames(w);
        drawWorldLabels(w);
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
        for (MapObject d : w.map.decor) {
            if (!isGroundDecal(d.type) || !visible(d.x(), d.y(), 220)) continue;
            if ("boss_seal".equals(d.type)) batch.setColor(1, 1, 1, 0.35f + 0.1f * MathUtils.sin(w.time * 1.5f));
            drawProp(d.type, d.x(), d.y());
            batch.setColor(Color.WHITE);
        }
        for (MapObject o : w.map.interacts) {
            if (!visible(o.x(), o.y(), 100)) continue;
            if ("shortcut".equals(o.type)) {
                boolean open = w.state.hasShortcut(o.prop("zone"));
                batch.setColor(open ? Color.WHITE : new Color(0.6f, 0.58f, 0.62f, 1f));
                drawProp("shortcut_door", o.x(), o.bounds.y);
                batch.setColor(Color.WHITE);
                if (open) {
                    batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
                    batch.setColor(0.6f, 0.35f, 1f, 0.35f + 0.15f * MathUtils.sin(w.time * 3f));
                    batch.draw(tx.glow, o.x() - 22, o.bounds.y, 44, 50);
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

    private static boolean isGroundDecal(String type) {
        switch (type) {
            case "rug":
            case "boss_seal":
            case "bones":
            case "skull":
            case "rubble":
            case "cobweb":
            case "cobweb_r":
            case "gold":
            case "ubones":
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
                case "banner":
                    items.add(new Item(y + 64, () -> drawProp("banner", x, y + 46)));
                    break;
                case "banner_red":
                case "banner_blue":
                    items.add(new Item(y + 64, () -> drawProp(type, x, y)));
                    break;
                case "campfire":
                    items.add(new Item(y, () -> drawProp("campfire_log", x, y - 8)));
                    break;
                case "pillar":
                case "statue":
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
    }

    private void drawProp(String key, float x, float footY) {
        SpriteLibrary.Entry e = sprites.get(sprites.resolve(key, (int) (x * 7 + footY * 13)));
        if (e == null) return;
        TextureRegion r = e.frame("idle", e.animated ? time + (x * 0.013f) : 0);
        if (r == null) return;
        float sw = r.getRegionWidth() * e.scale, sh = r.getRegionHeight() * e.scale;
        batch.draw(r, x - e.originX * e.scale, footY - e.originY * e.scale, sw, sh);
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
            float p = Math.min(1, w.miningProgress / w.b.mining.holdTime);
            batch.setColor(0, 0, 0, 0.7f);
            batch.draw(tx.white, x - 18, y + 24, 36, 6);
            batch.setColor(1f, 0.85f, 0.3f, 1);
            batch.draw(tx.white, x - 17, y + 25, 34 * p, 4);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawEnemy(GameWorld w, Enemy e) {
        SpriteLibrary.Entry s = sprites.get(e.id);
        if (s == null) return;
        boolean boss = e.isBoss();
        shadow(e.x, e.y, e.radius * 2.4f);
        boolean moving = e.state == Enemy.State.CHASE;
        float t = e.animTime;
        TextureRegion r = s.frame(moving ? "move" : "idle", t);
        float sw = r.getRegionWidth() * s.scale, sh = r.getRegionHeight() * s.scale;
        boolean flip = e.faceLeft != s.facesLeft;
        float ox = s.originX * s.scale;
        float bob = "watcher".equals(e.id) ? 8 + MathUtils.sin(t * 3f) * 3 : "boss_temple".equals(e.id) ? 6 + MathUtils.sin(t * 2f) * 3 : 0;
        // 예고 중에는 몸을 살짝 웅크리거나 떤다
        float shakeX = e.state == Enemy.State.TELEGRAPH ? MathUtils.sin(e.stateTime * 60f) * 1.2f : 0;
        float x0 = e.x - (flip ? sw - ox : ox) + shakeX;
        float y0 = e.y - s.originY * s.scale + bob;
        if (e.state == Enemy.State.TELEGRAPH) batch.setColor(1f, 0.82f, 0.78f, 1);
        batch.draw(r.getTexture(), x0, y0, sw, sh, r.getRegionX(), r.getRegionY(), r.getRegionWidth(), r.getRegionHeight(), flip, false);
        batch.setColor(Color.WHITE);
        if (e.flash > 0) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            batch.setColor(1, 1, 1, Math.min(1, e.flash / 0.12f) * 0.85f);
            batch.draw(r.getTexture(), x0, y0, sw, sh, r.getRegionX(), r.getRegionY(), r.getRegionWidth(), r.getRegionHeight(), flip, false);
            batch.setColor(Color.WHITE);
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
        if (!boss && e.hp < e.maxHp) {
            float bw = 28, p = e.hp / (float) e.maxHp;
            batch.setColor(0, 0, 0, 0.7f);
            batch.draw(tx.white, e.x - bw / 2 - 1, y0 + sh + 3, bw + 2, 5);
            batch.setColor(0.9f, 0.25f, 0.2f, 1);
            batch.draw(tx.white, e.x - bw / 2, y0 + sh + 4, bw * p, 3);
            batch.setColor(Color.WHITE);
        }
    }

    private void drawHero(GameWorld w, float alpha) {
        Hero h = w.hero;
        SpriteLibrary.Entry s = sprites.get("hero");
        if (alpha >= 1f) shadow(h.x, h.y, 26);
        String dir = Dir8.name4(h.facing);
        TextureRegion r = s.frame(h.moving ? "walk" : "idle", dir, h.animTime);
        float sw = r.getRegionWidth() * s.scale, sh = r.getRegionHeight() * s.scale;
        boolean flip = !s.directional && h.faceLeft != s.facesLeft;
        float ox = s.originX * s.scale;
        float a = alpha;
        if (h.invuln > 0 && alpha >= 1f) a = (int) (h.invuln * 20) % 2 == 0 ? 0.35f : 1f;
        float rot = 0;
        if (h.dead) {
            float k = Math.min(1, h.deathTimer / 0.4f);
            rot = (h.faceLeft ? 90 : -90) * k;
            a *= 1f - Math.max(0, h.deathTimer - 0.5f) * 1.6f;
        }
        boolean mining = w.miningVein != null && w.miningProgress > 0;
        WeaponState ws = w.state.equippedWeapon();
        String tool = mining && sprites.get("pickaxe") != null ? "pickaxe" : ws != null ? ws.type : null;
        // 위를 볼 때와 평소 들고 있을 때는 무기가 몸 뒤에 있다
        boolean behind = "up".equals(dir) || (h.attack == null && !mining && !"down".equals(dir));
        if (tool != null && behind && !h.dead) drawTool(w, tool, a, mining);
        batch.setColor(h.hurtFlash > 0 && alpha >= 1 ? new Color(1, 0.55f, 0.55f, a) : new Color(1, 1, 1, a));
        float x0 = h.x - (flip ? sw - ox : ox);
        float y0 = h.y - s.originY * s.scale;
        batch.draw(r.getTexture(), x0, y0, sw / 2f, 0, sw, sh, 1, 1, rot,
                r.getRegionX(), r.getRegionY(), r.getRegionWidth(), r.getRegionHeight(), flip, false);
        batch.setColor(Color.WHITE);
        if (tool != null && !behind && !h.dead) drawTool(w, tool, a, mining);
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
            float swing;
            float t = a.t;
            if (a.kind == AttackInstance.Kind.SHOCKWAVE) {
                swing = 0;
            } else if (t < a.windup) {
                swing = 75 * (t / a.windup);
            } else if (t < a.windup + a.active) {
                swing = 75 - 150 * ((t - a.windup) / a.active);
            } else {
                swing = -75;
            }
            angle = base + swing;
            hx = h.x + Dir8.DX[a.dir] * 6;
            hy = h.hurtY() + Dir8.DY[a.dir] * 4;
        } else {
            String d = Dir8.name4(h.facing);
            boolean left = "left".equals(d) || ("up".equals(d));
            angle = left ? 115 : 65;
            hx = h.x + (left ? -9 : 9);
            hy = h.y + 11;
        }
        batch.setColor(1, 1, 1, alpha);
        batch.draw(r, hx - s.originX * s.scale, hy - s.originY * s.scale, s.originX * s.scale, s.originY * s.scale,
                sw, sh, 1, 1, angle - s.pointAngle);
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
                    float p = Math.min(1, e.stateTime / e.telegraphTime);
                    shapes.setColor(WARN.r, WARN.g, WARN.b, 0.16f);
                    fillShape(e.shape, 1f);
                    shapes.setColor(WARN.r, WARN.g, WARN.b, 0.30f);
                    fillShape(e.shape, p); // 채워지는 영역
                } else if (e.state == Enemy.State.ATTACK) {
                    shapes.setColor(1f, 0.85f, 0.6f, 0.45f);
                    HitShape hs = e.activeHit();
                    if (hs != null) fillShape(hs, 1f);
                    if ("charge".equals(e.attackId)) {
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
            float ox = w.hero.x, oy = w.hero.hurtY();
            float dx = Dir8.DX[a.dir], dy = Dir8.DY[a.dir];
            if (a.kind == AttackInstance.Kind.NORMAL) {
                if (ph == AttackInstance.Phase.ACTIVE) {
                    shapes.setColor(1f, 0.97f, 0.85f, 0.08f);
                    fillShape(HitShape.box(ox, oy, dx, dy, a.length, a.width), 1f);
                    slashArc(ox, oy, dx, dy, a.length, a.width, (a.t - a.windup) / a.active, 1f);
                } else if (ph == AttackInstance.Phase.RECOVERY) {
                    float k = 1f - (a.t - a.windup - a.active) / 0.12f;
                    if (k > 0) slashArc(ox, oy, dx, dy, a.length, a.width, 1f, k);
                }
            } else if (a.effectiveLength >= 0) {
                float k = ph == AttackInstance.Phase.ACTIVE ? 1f
                        : Math.max(0, 1f - (a.t - a.windup - a.active) / a.recovery);
                shapes.setColor(0.45f, 0.75f, 1f, 0.55f * k);
                fillShape(HitShape.box(ox, oy, dx, dy, a.effectiveLength, a.width), 1f);
                shapes.setColor(0.85f, 0.95f, 1f, 0.6f * k);
                fillShape(HitShape.box(ox, oy, dx, dy, a.effectiveLength, a.width * 0.35f), 1f);
            }
        }
        shapes.end();
    }

    private void drawTelegraphOutline(GameWorld w) {
        if (w.combat == null) return;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (Enemy e : w.combat.enemies) {
            if (e.shape == null || e.state != Enemy.State.TELEGRAPH) continue;
            float blink = 0.65f + 0.35f * MathUtils.sin(e.stateTime * 18f);
            shapes.setColor(WARN.r, WARN.g * 1.4f, WARN.b, blink);
            outlineShape(e.shape);
            // 두 겹 테두리로 두께감
            if (e.shape.type == HitShape.Type.CIRCLE) {
                shapes.circle(e.shape.cx, e.shape.cy, e.shape.radius - 1, 40);
            }
        }
        shapes.end();
    }

    /** 판정 상자 안에 그리는 초승달 모양 베기 궤적 (바깥쪽이 밝고 안쪽으로 흐려진다). */
    private void slashArc(float ox, float oy, float dx, float dy, float length, float width, float progress, float alpha) {
        float px = -dy, py = dx;
        int n = 14;
        float half = width / 2f * 0.95f;
        int shown = Math.max(2, Math.round(n * Math.min(1f, progress + 0.3f)));
        for (int band = 0; band < 4; band++) {
            float r = length * (0.95f - band * 0.12f);
            float a = alpha * (0.9f - band * 0.22f);
            float prevX = 0, prevY = 0;
            for (int i = 0; i <= shown; i++) {
                float k = i / (float) n;
                float across = half - 2 * half * k;
                float along = (float) Math.sqrt(Math.max(0, r * r - across * across)) ;
                along = Math.max(6, Math.min(along, length - 2));
                float x = ox + dx * along + px * across, y = oy + dy * along + py * across;
                if (i > 0) {
                    float thick = (band == 0 ? 3.5f : 3f) * (0.4f + 0.6f * (float) Math.sin(Math.PI * k));
                    if (band == 0) shapes.setColor(1f, 1f, 0.95f, a);
                    else shapes.setColor(0.85f, 0.92f, 1f, a);
                    shapes.rectLine(prevX, prevY, x, y, thick);
                }
                prevX = x;
                prevY = y;
            }
        }
    }

    private void fillShape(HitShape s, float progress) {
        if (s.type == HitShape.Type.CIRCLE) {
            shapes.circle(s.cx, s.cy, s.radius * progress, 40);
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
                    lighting.light(batch, x, y, 300 * flick, 1f, 0.6f, 0.28f, 1.1f);
                    break;
                case "brazier":
                    lighting.light(batch, x, y + 10, 160 * flick, 1f, 0.6f, 0.3f, 0.9f);
                    break;
                case "furnace":
                    lighting.light(batch, x, y + 4, 170 * flick, 1f, 0.5f, 0.2f, 1f);
                    break;
                case "lantern_table":
                    lighting.light(batch, x, y + 12, 110 * flick, 1f, 0.8f, 0.45f, 0.8f);
                    break;
                case "candles":
                case "candle":
                    lighting.light(batch, x, y + 10, 100 * flick, 1f, 0.75f, 0.45f, 0.75f);
                    break;
                case "cauldron":
                case "fire_bowl":
                    lighting.light(batch, x, y + 24, 220 * flick, 1f, 0.55f, 0.25f, 1f);
                    break;
                case "crystal_mine":
                    lighting.light(batch, x, y + 8, 70, 1f, 0.8f, 0.3f, 0.5f);
                    break;
                case "crystal_temple":
                    lighting.light(batch, x, y + 8, 70, 0.7f, 0.4f, 1f, 0.5f);
                    break;
                default:
                    break;
            }
        }
        for (MapObject o : w.map.interacts) {
            if ("return".equals(o.type) || ("shortcut".equals(o.type) && w.state.hasShortcut(o.prop("zone")))) {
                lighting.light(batch, o.x(), o.y(), 110, 0.65f, 0.4f, 1f, 0.9f);
            }
        }
        for (DoorDef d : w.map.doors) {
            if (w.isZoneGate(d) && w.isDoorLocked(d)) {
                lighting.light(batch, d.bounds.x + d.bounds.width / 2, d.bounds.y + d.bounds.height / 2, 120, 0.6f, 0.35f, 1f, 0.8f);
            }
        }
        // 용사 주변은 항상 보이게
        Hero h = w.hero;
        float lf = 0.95f + 0.05f * MathUtils.sin(t * 7f);
        lighting.light(batch, h.x, h.y + 12, 260 * lf, 1f, 0.82f, 0.6f, 0.8f); // 허리의 랜턴
        if (h.attack != null && h.attack.kind == AttackInstance.Kind.SHOCKWAVE && h.attack.effectiveLength > 0) {
            float ex = h.x + Dir8.DX[h.attack.dir] * h.attack.effectiveLength / 2f;
            float ey = h.hurtY() + Dir8.DY[h.attack.dir] * h.attack.effectiveLength / 2f;
            lighting.light(batch, ex, ey, h.attack.effectiveLength * 0.7f, 0.5f, 0.75f, 1f, 1f);
        }
        if (w.combat != null) {
            for (Enemy e : w.combat.enemies) {
                if ("watcher".equals(e.id) || "boss_temple".equals(e.id)) {
                    lighting.light(batch, e.x, e.y + 16, 90, 0.7f, 0.45f, 1f, 0.6f);
                }
                // 경고 영역은 어둠 속에서도 보이게
                if (e.state == Enemy.State.TELEGRAPH && e.shape != null) {
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
                case "candles":
                    flame(x - 4, y + 2, 0.22f, t, 1f, 0.7f, 0.3f);
                    flame(x + 1, y + 5, 0.22f, t + 0.4f, 1f, 0.7f, 0.3f);
                    flame(x + 6, y, 0.22f, t + 0.8f, 1f, 0.7f, 0.3f);
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
