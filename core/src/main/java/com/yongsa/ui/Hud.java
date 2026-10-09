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
    private String introName;
    private boolean introFinal;
    private float introT = -1;
    private final float[] readyFlash = new float[3];
    private final boolean[] wasReady = {true, true, true};
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
        if (toasts.size() > 3) toasts.remove(0);
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

    /** 보스가 나타날 때 위아래 검은 띠와 함께 이름을 크게 보여 준다. */
    public void bossIntro(String name, boolean finalBoss) {
        introName = name;
        introFinal = finalBoss;
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
        for (int i = 0; i < 3; i++) {
            boolean ready = left[i] <= 0;
            if (ready && !wasReady[i] && i > 0) readyFlash[i] = 0.4f;
            wasReady[i] = ready;
            readyFlash[i] -= dt;
        }
    }

    private float[] cooldowns() {
        return new float[]{Math.max(0, g.hero.attackCd), Math.max(0, g.hero.spinCd), Math.max(0, g.hero.dashCd)};
    }

    public void render(Matrix4 projection, float w, float h) {
        this.projection = projection;
        this.w = w;
        this.h = h;
        batch.setProjectionMatrix(projection);
        shapes.setProjectionMatrix(projection);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        GameState s = g.state;
        int maxHp = g.rules.maxHp(s);
        Monster boss = g.activeBoss();

        // ── 화면 가장자리 어둡게, 체력이 낮거나 맞으면 붉게
        batch.begin();
        batch.setColor(0, 0, 0, 0.55f);
        batch.draw(art.vignette, 0, 0, w, h);
        float low = s.hp < maxHp * 0.3f ? 0.35f + 0.25f * MathUtils.sin(time * 6f) : 0f;
        float red = Math.max(low, Math.max(0, hurtT) / 0.35f * 0.8f);
        if (red > 0) {
            batch.setColor(0.8f, 0.05f, 0.05f, red);
            batch.draw(art.vignette, 0, 0, w, h);
        }
        batch.setColor(Color.WHITE);
        batch.end();

        // ── 막대들
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        panel(16, h - 104, 360, 88);
        float barX = 36, barY = h - 52, barW = 320, barH = 22;
        shapes.setColor(0.1f, 0.08f, 0.12f, 1f);
        shapes.rect(barX - 2, barY - 2, barW + 4, barH + 4);
        shapes.setColor(0.2f, 0.12f, 0.14f, 1f);
        shapes.rect(barX, barY, barW, barH);
        shapes.setColor(1f, 0.95f, 0.85f, 0.85f);
        shapes.rect(barX, barY, barW * Math.min(1f, trailHp / maxHp), barH);
        float hpw = barW * Math.min(1f, shownHp / maxHp);
        shapes.rect(barX, barY, hpw, barH, new Color(0.62f, 0.1f, 0.12f, 1f), new Color(0.9f, 0.22f, 0.2f, 1f),
                new Color(0.95f, 0.32f, 0.28f, 1f), new Color(0.7f, 0.14f, 0.14f, 1f));
        shapes.setColor(1f, 1f, 1f, 0.18f);
        shapes.rect(barX, barY + barH - 7, hpw, 4);
        // 50 마다 눈금
        shapes.setColor(0, 0, 0, 0.35f);
        for (int v = 50; v < maxHp; v += 50) shapes.rect(barX + barW * v / maxHp - 1, barY, 2, barH);

        panel(w - 300, h - 70, 284, 54);

        if (boss != null) {
            float bw = 600, bx = (w - bw) / 2f, by = h - 74;
            float fill = introT >= 0 ? Math.min(1f, introT / 1.2f) : 1f;
            panel(bx - 18, by - 14, bw + 36, 68);
            shapes.setColor(0.1f, 0.08f, 0.12f, 1f);
            shapes.rect(bx - 2, by - 2, bw + 4, 22);
            shapes.setColor(1f, 0.95f, 0.85f, 0.8f);
            shapes.rect(bx, by, bw * fill * bossTrail / boss.maxHp, 18);
            Color c0 = boss.finalBoss ? new Color(0.45f, 0.12f, 0.6f, 1f) : new Color(0.6f, 0.2f, 0.1f, 1f);
            Color c1 = boss.finalBoss ? new Color(0.75f, 0.35f, 0.95f, 1f) : new Color(0.95f, 0.45f, 0.2f, 1f);
            shapes.rect(bx, by, bw * fill * bossShown / boss.maxHp, 18, c0, c1, c1, c0);
            shapes.setColor(1f, 1f, 1f, 0.15f);
            shapes.rect(bx, by + 12, bw * fill * bossShown / boss.maxHp, 3);
        }

        // 스킬 칸
        float sy = 18, sx = w - 16 - 2 * 90 - 76;
        for (int i = 0; i < 3; i++) panel(sx + i * 90, sy, 76, 76);
        float[] cd = {g.hero.attackCd / g.balance.attack.basicCooldown,
                g.hero.spinCd / spinCooldown(), g.hero.dashCd / g.balance.attack.dashCooldown};
        for (int i = 0; i < 3; i++) {
            if (cd[i] > 0) {
                shapes.setColor(0, 0, 0, 0.6f);
                shapes.rect(sx + i * 90 + 4, sy + 4, 68, 68 * Math.min(1, cd[i]));
            }
            if (readyFlash[i] > 0) {
                float a = readyFlash[i] / 0.4f;
                shapes.setColor(1f, 0.9f, 0.5f, 0.5f * a);
                shapes.rect(sx + i * 90 - 3, sy - 3, 82, 82);
            }
        }
        // 다음 할 일
        String task = "다음 할 일: " + g.nextTask();
        layout.setText(fonts.normal, task);
        panel(16, 16, layout.width + 36, 46);
        shapes.end();

        // ── 그림과 글자
        batch.begin();
        batch.setColor(Color.WHITE);
        BitmapFont f = fonts.normal;
        String hpText = Math.max(0, s.hp) + " / " + maxHp;
        layout.setText(fonts.small, hpText);
        shadowed(fonts.small, hpText, barX + (barW - layout.width) / 2f, barY + (barH + layout.height) / 2f, Ui.TEXT);
        Weapon wpn = s.equipped();
        if (wpn != null) {
            batch.draw(art.sword(wpn.defId, wpn.awakened), 32, h - 98, 40, 40);
            if ("holy_sealed".equals(wpn.defId)) batch.draw(art.get("chain"), 36, h - 94, 32, 32);
            f.setColor(wpn.awakened || "holy_awakened".equals(wpn.defId) ? Ui.GOLD : Ui.TEXT);
            f.draw(batch, g.rules.weaponName(wpn), 80, h - 60);
            fonts.small.setColor(Ui.DIM);
            String extra = "공격력 " + g.rules.attack(s);
            if (!s.imprints.isEmpty()) extra += "   흡수한 힘 " + s.imprints.size() + "개 (+" + (s.imprints.size() * 10) + "%)";
            fonts.small.draw(batch, extra, 80, h - 84);
        }
        // 광물
        float ox = w - 290;
        for (OreType t : OreType.values()) {
            batch.draw(art.gem(t), ox, h - 62, 36, 36);
            f.setColor(Ui.TEXT);
            f.draw(batch, String.valueOf(s.ore(t)), ox + 38, h - 34);
            ox += 94;
        }
        fonts.small.setColor(Ui.DIM);
        layout.setText(fonts.small, "M  지도");
        fonts.small.draw(batch, "M  지도", w - 16 - layout.width, h - 80);
        // 보스 이름
        if (boss != null) {
            String name = boss.def.name;
            layout.setText(fonts.normal, name);
            shadowed(fonts.normal, name, (w - layout.width) / 2f, h - 24, boss.finalBoss ? Ui.PURPLE : Ui.ORANGE);
            String hpv = boss.hp + " / " + boss.maxHp;
            layout.setText(fonts.small, hpv);
            shadowed(fonts.small, hpv, (w - layout.width) / 2f, h - 58, Ui.TEXT);
        }
        // 스킬 아이콘
        String[] icons = {null, "icon_spin", "icon_dash"};
        String[] keys = {"J", "K", "L"};
        String[] names = {"베기", "회전 베기", "돌진 베기"};
        float[] left = cooldowns();
        for (int i = 0; i < 3; i++) {
            float x = sx + i * 90;
            boolean ready = left[i] <= 0;
            batch.setColor(ready ? Color.WHITE : new Color(0.5f, 0.5f, 0.55f, 1f));
            if (icons[i] == null) {
                if (wpn != null) batch.draw(art.sword(wpn.defId, wpn.awakened), x + 14, sy + 18, 48, 48);
            } else {
                batch.draw(art.get(icons[i]), x + 14, sy + 18, 48, 48);
            }
            batch.setColor(Color.WHITE);
            fonts.small.setColor(Ui.GOLD);
            fonts.small.draw(batch, keys[i], x + 8, sy + 72);
            fonts.small.setColor(Ui.DIM);
            layout.setText(fonts.small, names[i]);
            fonts.small.draw(batch, names[i], x + 38 - layout.width / 2f, sy + 18);
            if (!ready && i > 0) {
                String sec = String.valueOf((int) Math.ceil(left[i]));
                layout.setText(fonts.large, sec);
                shadowed(fonts.large, sec, x + 38 - layout.width / 2f, sy + 54, Color.WHITE);
            }
        }
        // 다음 할 일
        f.setColor(Ui.GOLD);
        f.draw(batch, task, 34, 47);
        // 알림
        float ty = 150 + (toasts.size() - 1) * 46;
        for (Toast t : toasts) {
            float a = Math.min(1f, (3.2f - t.t) / 0.5f) * Math.min(1f, t.t / 0.15f);
            float slide = (1f - Math.min(1f, t.t / 0.15f)) * 12f;
            layout.setText(fonts.normal, t.text);
            float bx = (w - layout.width) / 2f - 20, bw = layout.width + 40, bh = layout.height + 22;
            batch.setColor(0.05f, 0.04f, 0.07f, 0.75f * a);
            batch.draw(art.white(), bx, ty - layout.height - 11 + slide, bw, bh);
            batch.setColor(Ui.GOLD.r, Ui.GOLD.g, Ui.GOLD.b, 0.6f * a);
            batch.draw(art.white(), bx, ty - layout.height - 11 + slide, 3, bh);
            batch.setColor(Color.WHITE);
            fonts.normal.setColor(1, 1, 1, a);
            fonts.normal.draw(batch, t.text, (w - layout.width) / 2f, ty + slide);
            ty -= 46;
        }
        if (banner != null) {
            float a = bannerT < 0.4f ? bannerT / 0.4f : Math.min(1f, (2.6f - bannerT) / 0.6f);
            float rise = (1f - Math.min(1f, bannerT / 0.4f)) * 14f;
            layout.setText(fonts.title, banner);
            float bx = (w - layout.width) / 2f, by = h * 0.7f - rise;
            batch.setColor(0, 0, 0, 0.35f * a);
            batch.draw(art.glow, bx - 80, by - layout.height - 40, layout.width + 160, layout.height + 80);
            batch.setColor(Color.WHITE);
            fonts.title.setColor(0, 0, 0, 0.7f * a);
            fonts.title.draw(batch, banner, bx + 3, by - 3);
            fonts.title.setColor(1f, 0.85f, 0.4f, a);
            fonts.title.draw(batch, banner, bx, by);
            // 이름 아래 가는 금색 선이 양옆으로 퍼진다
            float lw = (layout.width + 60) * Math.min(1f, bannerT / 0.6f);
            batch.setColor(1f, 0.85f, 0.4f, 0.8f * a);
            batch.draw(art.white(), w / 2f - lw / 2f, by - layout.height - 18, lw, 2);
            batch.setColor(Color.WHITE);
        }
        batch.end();

        if (introT >= 0) drawBossIntro();
        if (mapAnim > 0.01f) drawMap();
        f.setColor(Color.WHITE);
        fonts.small.setColor(Color.WHITE);
    }

    private void drawBossIntro() {
        float in = Math.min(1f, introT / 0.35f), out = Math.min(1f, Math.max(0f, (2.6f - introT) / 0.4f));
        float k = Math.min(in, out);
        float bar = 80 * k;
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(0, 0, 0, 0.92f);
        shapes.rect(0, h - bar, w, bar);
        shapes.rect(0, 0, w, bar);
        shapes.end();
        batch.begin();
        float ta = Math.min(1f, Math.max(0f, (introT - 0.25f) / 0.35f)) * out;
        String sub = introFinal ? "최종 보스" : "보스";
        layout.setText(fonts.normal, sub);
        float slide = (1f - Math.min(1f, Math.max(0f, (introT - 0.25f) / 0.35f))) * 40f;
        fonts.normal.setColor(0.9f, 0.3f, 0.25f, ta);
        fonts.normal.draw(batch, sub, (w - layout.width) / 2f, h * 0.5f + 70);
        layout.setText(fonts.title, introName);
        batch.setColor(0, 0, 0, 0.5f * ta);
        batch.draw(art.glow, (w - layout.width) / 2f - 100, h * 0.5f - 40, layout.width + 200, 120);
        batch.setColor(Color.WHITE);
        fonts.title.setColor(0, 0, 0, ta);
        fonts.title.draw(batch, introName, (w - layout.width) / 2f + 3 - slide, h * 0.5f + 37);
        Color c = introFinal ? Ui.PURPLE : Ui.ORANGE;
        fonts.title.setColor(c.r, c.g, c.b, ta);
        fonts.title.draw(batch, introName, (w - layout.width) / 2f - slide, h * 0.5f + 40);
        batch.end();
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

    private float spinCooldown() {
        float cd = g.balance.attack.spinCooldown;
        if (g.rules.hasAbility(g.state, "skyfall")) cd -= 2f;
        return cd;
    }

    private void shadowed(BitmapFont font, String text, float x, float y, Color c) {
        font.setColor(0, 0, 0, 0.8f);
        font.draw(batch, text, x + 2, y - 2);
        font.setColor(c);
        font.draw(batch, text, x, y);
    }

    private void panel(float x, float y, float pw, float ph) {
        shapes.setColor(0.07f, 0.06f, 0.09f, 0.82f);
        shapes.rect(x, y, pw, ph);
        shapes.setColor(0.55f, 0.46f, 0.27f, 0.9f);
        shapes.rect(x, y, pw, 2);
        shapes.rect(x, y + ph - 2, pw, 2);
        shapes.rect(x, y, 2, ph);
        shapes.rect(x + pw - 2, y, 2, ph);
        // 모서리 장식
        shapes.setColor(0.85f, 0.7f, 0.38f, 1f);
        shapes.rect(x - 1, y - 1, 5, 5);
        shapes.rect(x + pw - 4, y - 1, 5, 5);
        shapes.rect(x - 1, y + ph - 4, 5, 5);
        shapes.rect(x + pw - 4, y + ph - 4, 5, 5);
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
