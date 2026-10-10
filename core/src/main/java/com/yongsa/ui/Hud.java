package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.yongsa.combat.Monster;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Fonts;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.OreType;
import com.yongsa.model.Vein;
import com.yongsa.model.Weapon;
import com.yongsa.world.Door;
import com.yongsa.world.Link;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;

import java.util.ArrayList;
import java.util.List;

/**
 * 플레이 화면 HUD (UI-03): 왼쪽 위 체력과 장착 무기, 오른쪽 위 광물 3종,
 * 아래 스킬 3개, 왼쪽 아래 “다음 할 일”, 보스방에서는 위 가운데 보스 체력.
 * 지도는 M 키로 열고 닫는다 (FR-맵-10).
 */
public class Hud {
    private final GameSession g;
    private final Art art;
    private final Fonts fonts;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final GlyphLayout layout = new GlyphLayout();
    private final List<Toast> toasts = new ArrayList<>();
    private String banner;
    private float bannerT;
    private float shownHp = -1, trailHp = -1, trailHold;
    private float bossShown = -1, bossTrail = -1;
    private Monster lastBoss;
    private float hurtT;
    private float time;
    private boolean mapOpen;
    private float mapAnim;
    private float introT = -1;
    private static final int SLOTS = 2;
    private final float[] readyFlash = new float[SLOTS];
    private final boolean[] wasReady = {true, true};
    private Matrix4 projection;
    private float w, h;

    private static class Toast {
        String text;
        float t;
    }

    public Hud(GameSession g, Art art, Fonts fonts, SpriteBatch batch, ShapeRenderer shapes) {
        this.g = g;
        this.art = art;
        this.fonts = fonts;
        this.batch = batch;
        this.shapes = shapes;
    }

    public void toast(String text) {
        for (Toast t : toasts) if (t.text.equals(text)) {
            t.t = 0;
            return;
        }
        Toast t = new Toast();
        t.text = text;
        toasts.add(t);
        if (toasts.size() > 1) toasts.remove(0);
    }

    public void banner(String text) {
        banner = text;
        bannerT = 0;
    }

    public void toggleMap() {
        mapOpen = !mapOpen;
    }

    public boolean mapOpen() {
        return mapOpen;
    }

    /** 용사가 맞았을 때 화면 가장자리가 붉게 번쩍인다. */
    public void hurt() {
        hurtT = 0.35f;
    }

    /** 보스가 나타날 때 체력 패널을 강조한다. */
    public void bossIntro(String name, boolean finalBoss) {
        introT = 0;
        banner = null;
    }

    public void update(float dt) {
        time += dt;
        for (int i = toasts.size() - 1; i >= 0; i--) {
            toasts.get(i).t += dt;
            if (toasts.get(i).t > 3.2f) toasts.remove(i);
        }
        if (banner != null) {
            bannerT += dt;
            if (bannerT > 2.6f) banner = null;
        }
        if (introT >= 0) {
            introT += dt;
            if (introT > 2.6f) introT = -1;
        }
        hurtT -= dt;
        mapAnim += ((mapOpen ? 1f : 0f) - mapAnim) * Math.min(1f, dt * 14f);

        int maxHp = g.rules.maxHp(g.state);
        int hp = Math.max(0, g.state.hp);
        if (shownHp < 0) shownHp = trailHp = hp;
        shownHp += (hp - shownHp) * Math.min(1f, dt * 14f);
        // 깎인 만큼 하얀 막대가 잠깐 남았다가 따라 줄어든다
        if (hp < trailHp) {
            trailHold -= dt;
            if (trailHold <= 0) trailHp += (hp - trailHp) * Math.min(1f, dt * 5f);
        } else {
            trailHp = hp;
            trailHold = 0.45f;
        }
        if (hp >= trailHp - 0.5f) trailHold = 0.45f;
        shownHp = Math.max(0, Math.min(maxHp, shownHp));

        Monster boss = g.activeBoss();
        if (boss != lastBoss) {
            lastBoss = boss;
            bossShown = 0;
            bossTrail = boss == null ? 0 : boss.hp;
        }
        if (boss != null) {
            bossShown += (boss.hp - bossShown) * Math.min(1f, dt * 4f);
            bossTrail += (boss.hp - bossTrail) * Math.min(1f, dt * 2f);
            if (bossTrail < boss.hp) bossTrail = boss.hp;
        }

        float[] left = cooldowns();
        for (int i = 0; i < SLOTS; i++) {
            boolean ready = left[i] <= 0;
            if (ready && !wasReady[i] && i > 0) readyFlash[i] = 0.4f;
            wasReady[i] = ready;
            readyFlash[i] -= dt;
        }
    }

    private float[] cooldowns() {
        return new float[]{Math.max(0, g.hero.attackCd), Math.max(0, g.hero.rollCd)};
    }

    public void render(Matrix4 projection, float w, float h) {
        this.projection = projection;
        this.w = w;
        this.h = h;
        batch.setProjectionMatrix(projection);
        shapes.setProjectionMatrix(projection);
        GameState s = g.state;
        int maxHp = g.rules.maxHp(s);
        Weapon weapon = s.equipped();
        Monster boss = g.activeBoss();
        batch.begin();
        batch.setColor(0, 0, 0, 0.28f);
        batch.draw(art.vignette, 0, 0, w, h);
        float low = s.hp < maxHp * 0.3f ? 0.22f + 0.12f * MathUtils.sin(time * 6f) : 0f;
        float red = Math.max(low, Math.max(0, hurtT) / 0.35f * 0.65f);
        if (red > 0) {
            batch.setColor(0.8f, 0.05f, 0.05f, red);
            batch.draw(art.vignette, 0, 0, w, h);
        }
        card(24, h - 122, 326, 98);
        batch.setColor(Color.WHITE);
        batch.draw(art.get("hero_down"), 30, h - 113, 53, 80);
        text(fonts.small, "용사", 88, h - 40, Ui.GOLD);
        String hp = Math.max(0, s.hp) + " / " + maxHp;
        layout.setText(fonts.small, hp);
        text(fonts.small, hp, 330 - layout.width, h - 40, Ui.TEXT);
        rect(88, h - 67, 242, 9, new Color(0x40232aff));
        rect(88, h - 67, 242 * MathUtils.clamp(trailHp / maxHp, 0, 1), 9, Ui.GOLD);
        rect(88, h - 67, 242 * MathUtils.clamp(shownHp / maxHp, 0, 1), 9, new Color(0xd65958ff));
        if (weapon != null) {
            text(fonts.small, g.rules.weaponName(weapon), 88, h - 79, Ui.TEXT);
            text(fonts.small, "공격력 " + g.rules.attack(s) + "  ·  흡수 " + s.imprints.size(), 88, h - 101, Ui.DIM);
        }

        card(w - 320, h - 88, 296, 64);
        float ox = w - 306;
        for (OreType type : OreType.values()) {
            batch.setColor(Color.WHITE);
            batch.draw(art.gem(type), ox, h - 76, 40, 40);
            text(fonts.small, String.valueOf(s.ore(type)), ox + 43, h - 49, Ui.TEXT);
            ox += 94;
        }
        text(fonts.small, "I  가방     M  지도     Esc  메뉴", w - 306, h - 103, Ui.DIM);

        Room room = g.heroRoom();
        String zone = room == null ? "연결 통로" : room.dungeonId == null ? "용사의 캠프"
                : g.balance.dungeon(room.dungeonId).name;
        if (boss == null) {
            centered(fonts.small, banner == null ? zone : banner, w / 2, h - 35, Ui.GOLD);
            centered(fonts.small, g.inCamp() ? "정비하고, 다음 여정을 준비하세요" : "탐험 중", w / 2, h - 59, Ui.DIM);
        } else {
            float bx = w / 2 - 210;
            card(bx - 16, h - 100, 452, 76);
            if (introT >= 0) rect(bx - 16, h - 25, 452 * Math.min(1, introT / 0.5f), 1, Ui.GOLD);
            centered(fonts.normal, boss.def.name, w / 2, h - 38, boss.finalBoss ? Ui.PURPLE : Ui.GOLD);
            rect(bx, h - 73, 420, 9, new Color(0x39232aff));
            rect(bx, h - 73, 420 * MathUtils.clamp(bossTrail / boss.maxHp, 0, 1), 9, Ui.GOLD);
            rect(bx, h - 73, 420 * MathUtils.clamp(bossShown / boss.maxHp, 0, 1), 9, boss.finalBoss ? Ui.PURPLE : Ui.RED);
            centered(fonts.small, boss.hp + " / " + boss.maxHp, w / 2, h - 80, Ui.DIM);
        }

        card(24, 24, 624, 88);
        rect(24, 24, 3, 88, Ui.GOLD);
        text(fonts.small, "다음 여정", 42, 95, Ui.GOLD);
        fonts.normal.setColor(Ui.TEXT);
        fonts.normal.draw(batch, g.nextTask(), 42, 69, 586, com.badlogic.gdx.utils.Align.left, true);
        text(fonts.small, "W A S D  이동    ·    E  상호작용", 24, 136, Ui.DIM);

        float[] left = cooldowns();
        String[] keys = {"J", "Space"};
        String[] names = {"베기", "구르기"};
        for (int i = 0; i < SLOTS; i++) {
            float x = w - 252 + i * 120;
            card(x, 24, 108, 88);
            if (readyFlash[i] > 0) rect(x, 111, 108, 2, Ui.GOLD);
            float total = i == 0 ? g.balance.attack.basicCooldown : g.balance.attack.rollTime + g.balance.attack.rollCooldown;
            if (left[i] > 0) rect(x + 1, 25, 106, 86 * Math.min(1, left[i] / total), new Color(0, 0, 0, 0.5f));
            batch.setColor(Color.WHITE);
            if (i == 0 && weapon != null) batch.draw(art.sword(weapon.defId, weapon.awakened), x + 35, 47, 58, 58);
            if (i == 1) batch.draw(art.get("hero_side_walk"), x + 48, 51, 34, 48);
            text(fonts.small, keys[i], x + 10, 100, Ui.GOLD);
            text(fonts.small, names[i], x + 12, 47, Ui.DIM);
            if (i == 1 && left[i] > 0.1f) centered(fonts.small, String.format(java.util.Locale.ROOT, "%.1f", left[i]), x + 71, 77, Ui.TEXT);
        }
        for (Toast toast : toasts) {
            float alpha = Math.min(1, (3.2f - toast.t) / 0.5f);
            layout.setText(fonts.small, toast.text);
            float tw = Math.min(800, layout.width + 40);
            card((w - tw) / 2, 162, tw, 44);
            centered(fonts.small, toast.text, w / 2, 190, new Color(1, 1, 1, alpha));
        }
        batch.setColor(Color.WHITE);
        batch.end();
        if (mapAnim > 0.01f) drawMap();
        fonts.normal.setColor(Color.WHITE);
        fonts.small.setColor(Color.WHITE);
    }

    private void rect(float x, float y, float width, float height, Color color) {
        batch.setColor(color);
        batch.draw(art.white(), x, y, width, height);
    }

    private void card(float x, float y, float width, float height) {
        rect(x, y, width, height, new Color(0x101820ed));
        rect(x, y + height - 1, width, 1, new Color(0x806c4bca));
        rect(x, y, width, 1, new Color(0x39454fca));
    }

    private void text(BitmapFont font, String value, float x, float y, Color color) {
        font.setColor(color);
        batch.setColor(Color.WHITE);
        font.draw(batch, value, x, y);
    }

    private void centered(BitmapFont font, String value, float x, float y, Color color) {
        layout.setText(font, value);
        text(font, value, x - layout.width / 2, y, color);
    }


    /** 전체 화면 검은 막 (화면 전환용). */
    public void renderFade(float alpha) {
        if (alpha <= 0.001f) return;
        batch.setProjectionMatrix(projection);
        batch.begin();
        batch.setColor(0, 0, 0, alpha);
        batch.draw(art.white(), 0, 0, w, h);
        batch.setColor(Color.WHITE);
        batch.end();
    }

    private void shadowed(BitmapFont font, String text, float x, float y, Color c) {
        font.setColor(0, 0, 0, 0.8f);
        font.draw(batch, text, x + 2, y - 2);
        font.setColor(c);
        font.draw(batch, text, x, y);
    }


    // ───────────────────────── 지도 (M)

    private void drawMap() {
        float a = mapAnim;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Room r : g.map.rooms) {
            minX = Math.min(minX, r.x0);
            maxX = Math.max(maxX, r.x1);
            minY = Math.min(minY, r.y0);
            maxY = Math.max(maxY, r.y1);
        }
        float pw = 900, ph = 560;
        float px = (w - pw) / 2f, py = (h - ph) / 2f - 10 + (1f - a) * 20f;
        float k = Math.min((pw - 80) / (maxX - minX + 1), (ph - 130) / (maxY - minY + 1));
        float ox = px + (pw - (maxX - minX + 1) * k) / 2f - minX * k;
        float oy = py + 60 + (ph - 130 - (maxY - minY + 1) * k) / 2f - minY * k;

        Room here = g.heroRoom();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, 0.55f * a);
        shapes.rect(0, 0, w, h);
        shapes.setColor(0.08f, 0.07f, 0.1f, 0.94f * a);
        shapes.rect(px, py, pw, ph);
        shapes.setColor(0.55f, 0.46f, 0.27f, a);
        shapes.rect(px, py, pw, 2);
        shapes.rect(px, py + ph - 2, pw, 2);
        shapes.rect(px, py, 2, ph);
        shapes.rect(px + pw - 2, py, 2, ph);

        // 통로
        for (Link l : g.map.links) {
            boolean seen = known(l.a) || known(l.b);
            if (!seen) continue;
            Door da = l.doorA, db = l.doorB;
            float x0 = ox + (da.tilesX[1] + 0.5f) * k, y0 = oy + (da.tilesY[1] + 0.5f) * k;
            float x1 = ox + (db.tilesX[1] + 0.5f) * k, y1 = oy + (db.tilesY[1] + 0.5f) * k;
            if (l.gateClosed) shapes.setColor("FINAL".equals(l.gate) ? new Color(0.95f, 0.75f, 0.3f, a) : new Color(0.65f, 0.45f, 1f, a));
            else shapes.setColor(0.42f, 0.4f, 0.46f, a);
            shapes.rectLine(x0, y0, x1, y1, Math.max(4f, k * 2.2f));
        }
        // 방
        for (Room r : g.map.rooms) {
            boolean visited = known(r);
            boolean boss = r.type == RoomType.BOSS || r.type == RoomType.FINAL;
            if (!visited && !boss) continue;
            float rx = ox + r.x0 * k, ry = oy + r.y0 * k, rw = (r.x1 - r.x0 + 1) * k, rh = (r.y1 - r.y0 + 1) * k;
            Color c = roomColor(r, visited);
            if (r == here) {
                float pulse = 0.5f + 0.5f * MathUtils.sin(time * 5f);
                shapes.setColor(1f, 0.85f, 0.35f, (0.6f + 0.4f * pulse) * a);
                shapes.rect(rx - 4, ry - 4, rw + 8, rh + 8);
            }
            shapes.setColor(0.03f, 0.03f, 0.05f, a);
            shapes.rect(rx - 1, ry - 1, rw + 2, rh + 2);
            shapes.setColor(c.r, c.g, c.b, c.a * a);
            shapes.rect(rx, ry, rw, rh);
            shapes.setColor(1f, 1f, 1f, 0.07f * a);
            shapes.rect(rx, ry + rh - 4, rw, 4);
            if (r.type == RoomType.ORE && visited) {
                List<Vein> veins = g.veinsOf(r);
                if (veins != null) {
                    for (Vein v : veins) {
                        shapes.setColor(oreColor(v.ore).r, oreColor(v.ore).g, oreColor(v.ore).b, a);
                        shapes.rect(ox + v.tileX * k, oy + v.tileY * k, Math.max(3, k), Math.max(3, k));
                    }
                }
            }
            if (g.hasPortal(r)) {
                float cx = rx + rw / 2f, cy = ry + rh / 2f;
                shapes.setColor(0.55f, 0.45f, 1f, a);
                shapes.ellipse(cx - 12, cy - 7, 24, 14, 20);
                shapes.setColor(0.85f, 0.8f, 1f, a);
                shapes.ellipse(cx - 6, cy - 3, 12, 6, 16);
            }
        }
        // 용사 위치
        float hx = ox + g.hero.x / g.balance.tileSize * k, hy = oy + g.hero.y / g.balance.tileSize * k;
        float pulse = 0.5f + 0.5f * MathUtils.sin(time * 8f);
        shapes.setColor(1f, 1f, 1f, 0.35f * pulse * a);
        shapes.circle(hx, hy, 10 + 4 * pulse, 20);
        shapes.setColor(0.1f, 0.1f, 0.1f, a);
        shapes.circle(hx, hy, 6, 16);
        shapes.setColor(1f, 0.95f, 0.7f, a);
        shapes.circle(hx, hy, 4.5f, 16);

        // 범례
        String[] labels = {"캠프", "방", "광물방", "보스방", "귀환 포탈", "봉인된 길"};
        Color[] colors = {new Color(0.85f, 0.6f, 0.3f, 1f), new Color(0.5f, 0.48f, 0.56f, 1f), new Color(0.35f, 0.5f, 0.75f, 1f),
                new Color(0.8f, 0.25f, 0.2f, 1f), new Color(0.55f, 0.45f, 1f, 1f), new Color(0.65f, 0.45f, 1f, 1f)};
        float lx = px + 40;
        for (int i = 0; i < labels.length; i++) {
            shapes.setColor(colors[i].r, colors[i].g, colors[i].b, a);
            shapes.rect(lx, py + 26, 16, 16);
            layout.setText(fonts.small, labels[i]);
            lx += 16 + 10 + layout.width + 28;
        }
        shapes.end();

        batch.begin();
        fonts.large.setColor(1f, 0.85f, 0.4f, a);
        fonts.large.draw(batch, "지도", px + 30, py + ph - 20);
        fonts.small.setColor(Ui.DIM.r, Ui.DIM.g, Ui.DIM.b, a);
        layout.setText(fonts.small, "M  닫기");
        fonts.small.draw(batch, "M  닫기", px + pw - 30 - layout.width, py + ph - 26);
        // 던전 이름
        String[] ids = {"LEFT", "RIGHT", "TOP"};
        for (String id : ids) {
            float sx = 0, sy = 0;
            int n = 0;
            for (Room r : g.map.rooms) {
                if (!id.equals(r.dungeonId) || !known(r)) continue;
                sx += ox + (r.x0 + r.x1) / 2f * k;
                sy += oy + r.y1 * k;
                n++;
            }
            if (n == 0) continue;
            String name = g.balance.dungeon(id).name;
            layout.setText(fonts.small, name);
            fonts.small.setColor(0.9f, 0.85f, 0.75f, 0.9f * a);
            fonts.small.draw(batch, name, sx / n - layout.width / 2f, sy / n + 24);
        }
        lx = px + 40;
        for (String label : labels) {
            fonts.small.setColor(Ui.TEXT.r, Ui.TEXT.g, Ui.TEXT.b, a);
            fonts.small.draw(batch, label, lx + 26, py + 40);
            layout.setText(fonts.small, label);
            lx += 16 + 10 + layout.width + 28;
        }
        batch.end();
        fonts.large.setColor(Color.WHITE);
        fonts.small.setColor(Color.WHITE);
    }

    private boolean known(Room r) {
        return r.type == RoomType.CAMP || g.state.room(r.id).visited;
    }

    private Color roomColor(Room r, boolean visited) {
        switch (r.type) {
            case CAMP: return new Color(0.85f, 0.6f, 0.3f, 1f);
            case ORE: return new Color(0.35f, 0.5f, 0.75f, 1f);
            case BOSS:
            case FINAL:
                if (!visited) return new Color(0.45f, 0.14f, 0.12f, 0.75f);
                return r.type == RoomType.FINAL ? new Color(0.6f, 0.25f, 0.8f, 1f) : new Color(0.8f, 0.25f, 0.2f, 1f);
            default:
                return g.state.room(r.id).cleared ? new Color(0.5f, 0.48f, 0.56f, 1f) : new Color(0.4f, 0.36f, 0.42f, 1f);
        }
    }

    private static Color oreColor(OreType t) {
        switch (t) {
            case BLACKIRON: return new Color(0.75f, 0.82f, 1f, 1f);
            case BLOODMOON: return new Color(1f, 0.4f, 0.5f, 1f);
            default: return new Color(0.55f, 0.9f, 1f, 1f);
        }
    }
}
