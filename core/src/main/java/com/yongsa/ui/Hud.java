package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.yongsa.combat.Monster;
import com.yongsa.config.Balance;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Fonts;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.OreType;
import com.yongsa.model.Weapon;
import com.yongsa.world.Room;
import com.yongsa.world.RoomType;

import java.util.ArrayList;
import java.util.List;

/**
 * 플레이 화면 HUD (UI-03): 왼쪽 위 체력과 장착 무기, 오른쪽 위 광물 3종과 작은 지도,
 * 아래 스킬 2개, 왼쪽 아래 “다음 할 일”, 보스방에서는 위 가운데 보스 체력.
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
    private float shownHp = -1;
    private float time;

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
        int maxHp = g.rules.maxHp(g.state);
        if (shownHp < 0) shownHp = g.state.hp;
        shownHp += (g.state.hp - shownHp) * Math.min(1f, dt * 8f);
        shownHp = Math.max(0, Math.min(maxHp, shownHp));
    }

    public void render(Matrix4 projection, float w, float h) {
        batch.setProjectionMatrix(projection);
        shapes.setProjectionMatrix(projection);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        GameState s = g.state;
        int maxHp = g.rules.maxHp(s);
        Monster boss = g.activeBoss();

        // ── 막대들
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        panel(16, h - 104, 360, 88);
        float barW = 320;
        shapes.setColor(0.12f, 0.1f, 0.14f, 1f);
        shapes.rect(36, h - 50, barW, 20);
        shapes.setColor(0.55f, 0.15f, 0.12f, 1f);
        shapes.rect(36, h - 50, barW * shownHp / maxHp, 20);
        shapes.setColor(0.86f, 0.22f, 0.2f, 1f);
        shapes.rect(36, h - 50, barW * Math.max(0, s.hp) / maxHp, 20);
        shapes.setColor(1f, 1f, 1f, 0.15f);
        shapes.rect(36, h - 38, barW * Math.max(0, s.hp) / maxHp, 6);

        panel(w - 300, h - 70, 284, 54);

        if (boss != null) {
            float bw = 560, bx = (w - bw) / 2f, by = h - 66;
            panel(bx - 16, by - 12, bw + 32, 62);
            shapes.setColor(0.12f, 0.1f, 0.14f, 1f);
            shapes.rect(bx, by, bw, 16);
            shapes.setColor(boss.finalBoss ? new Color(0.6f, 0.2f, 0.75f, 1f) : new Color(0.8f, 0.3f, 0.15f, 1f));
            shapes.rect(bx, by, bw * boss.hp / (float) boss.maxHp, 16);
        }

        // 스킬 칸
        float sy = 18, sx = w / 2f - 130;
        for (int i = 0; i < 3; i++) panel(sx + i * 90, sy, 76, 76);
        float[] cd = {g.hero.attackCd / g.balance.attack.basicCooldown,
                g.hero.spinCd / spinCooldown(), g.hero.dashCd / g.balance.attack.dashCooldown};
        for (int i = 0; i < 3; i++) {
            if (cd[i] > 0) {
                shapes.setColor(0, 0, 0, 0.6f);
                shapes.rect(sx + i * 90 + 4, sy + 4, 68, 68 * Math.min(1, cd[i]));
            }
        }
        // 다음 할 일
        String task = "다음 할 일: " + g.nextTask();
        layout.setText(fonts.normal, task);
        panel(16, 16, layout.width + 36, 46);
        drawMinimapShapes(w, h);
        shapes.end();

        // ── 그림과 글자
        batch.begin();
        batch.setColor(Color.WHITE);
        BitmapFont f = fonts.normal;
        f.setColor(Ui.TEXT);
        fonts.small.setColor(Ui.TEXT);
        fonts.small.draw(batch, "체력 " + Math.max(0, s.hp) + " / " + maxHp, 40, h - 33 + 0);
        Weapon wpn = s.equipped();
        if (wpn != null) {
            batch.draw(art.sword(wpn.defId, wpn.awakened), 32, h - 98, 40, 40);
            if ("holy_sealed".equals(wpn.defId)) batch.draw(art.get("chain"), 36, h - 94, 32, 32);
            f.setColor(wpn.awakened || "holy_awakened".equals(wpn.defId) ? Ui.GOLD : Ui.TEXT);
            f.draw(batch, g.rules.weaponName(wpn), 80, h - 56);
            fonts.small.setColor(Ui.DIM);
            String extra = "공격력 " + g.rules.attack(s);
            if (!s.imprints.isEmpty()) extra += "   흡수한 힘 " + s.imprints.size() + "개 (+" + (s.imprints.size() * 10) + "%)";
            fonts.small.draw(batch, extra, 80, h - 86);
        }
        // 광물
        float ox = w - 290;
        for (OreType t : OreType.values()) {
            batch.draw(art.gem(t), ox, h - 62, 36, 36);
            f.setColor(Ui.TEXT);
            f.draw(batch, String.valueOf(s.ore(t)), ox + 38, h - 34);
            ox += 94;
        }
        // 보스 이름
        if (boss != null) {
            fonts.normal.setColor(boss.finalBoss ? Ui.PURPLE : Ui.ORANGE);
            String name = boss.def.name + "  " + boss.hp + " / " + boss.maxHp;
            layout.setText(fonts.normal, name);
            fonts.normal.draw(batch, name, (w - layout.width) / 2f, h - 22);
        }
        // 스킬 아이콘
        String[] icons = {null, "icon_spin", "icon_dash"};
        String[] keys = {"J", "K", "L"};
        String[] names = {"베기", "회전 베기", "돌진 베기"};
        float[] left = {Math.max(0, g.hero.attackCd), Math.max(0, g.hero.spinCd), Math.max(0, g.hero.dashCd)};
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
                fonts.large.setColor(Color.WHITE);
                fonts.large.draw(batch, sec, x + 38 - layout.width / 2f, sy + 54);
            }
        }
        // 다음 할 일
        f.setColor(Ui.GOLD);
        f.draw(batch, task, 34, 47);
        // 알림
        float ty = h - 150;
        for (Toast t : toasts) {
            float a = Math.min(1f, (3.2f - t.t) / 0.5f);
            layout.setText(fonts.normal, t.text);
            batch.setColor(0, 0, 0, 0.6f * a);
            batch.draw(art.white(), (w - layout.width) / 2f - 16, ty - layout.height - 10, layout.width + 32, layout.height + 20);
            batch.setColor(Color.WHITE);
            fonts.normal.setColor(1, 1, 1, a);
            fonts.normal.draw(batch, t.text, (w - layout.width) / 2f, ty);
            ty -= 44;
        }
        if (banner != null) {
            float a = bannerT < 0.4f ? bannerT / 0.4f : Math.min(1f, (2.6f - bannerT) / 0.6f);
            layout.setText(fonts.title, banner);
            fonts.title.setColor(1f, 0.85f, 0.4f, a);
            fonts.title.draw(batch, banner, (w - layout.width) / 2f, h * 0.68f);
        }
        batch.end();
        f.setColor(Color.WHITE);
        fonts.small.setColor(Color.WHITE);
    }

    private float spinCooldown() {
        float cd = g.balance.attack.spinCooldown;
        if (g.rules.hasAbility(g.state, "skyfall")) cd -= 2f;
        return cd;
    }

    private void panel(float x, float y, float w, float h) {
        shapes.setColor(0.07f, 0.06f, 0.09f, 0.78f);
        shapes.rect(x, y, w, h);
        shapes.setColor(0.55f, 0.46f, 0.27f, 0.9f);
        shapes.rect(x, y, w, 2);
        shapes.rect(x, y + h - 2, w, 2);
        shapes.rect(x, y, 2, h);
        shapes.rect(x + w - 2, y, 2, h);
    }

    /** 가본 방과 보스방 위치를 보여주는 작은 지도 (FR-맵-10). */
    private void drawMinimapShapes(float w, float h) {
        Balance b = g.balance;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (Room r : g.map.rooms) {
            minX = Math.min(minX, r.cellX);
            maxX = Math.max(maxX, r.cellX);
            minY = Math.min(minY, r.cellY);
            maxY = Math.max(maxY, r.cellY);
        }
        float cell = 22, gap = 4;
        float mw = (maxX - minX + 1) * cell, mh = (maxY - minY + 1) * cell;
        float x0 = w - 16 - mw - 12, y0 = h - 96 - mh - 12;
        panel(x0 - 8, y0 - 8, mw + 16, mh + 16);
        Room here = g.heroRoom();
        for (Room r : g.map.rooms) {
            boolean visited = g.state.room(r.id).visited || r.type == RoomType.CAMP;
            boolean boss = r.type == RoomType.BOSS || r.type == RoomType.FINAL;
            if (!visited && !boss) continue;
            float rx = x0 + (r.cellX - minX) * cell, ry = y0 + (r.cellY - minY) * cell;
            if (r == here) {
                float pulse = 0.75f + 0.25f * (float) Math.sin(time * 6);
                shapes.setColor(1f, 0.85f * pulse, 0.3f, 1f);
            } else if (r.type == RoomType.CAMP) shapes.setColor(0.85f, 0.6f, 0.3f, 1f);
            else if (boss) shapes.setColor(visited ? new Color(0.85f, 0.25f, 0.2f, 1f) : new Color(0.5f, 0.15f, 0.12f, 0.8f));
            else if (r.type == RoomType.ORE) shapes.setColor(0.45f, 0.6f, 0.85f, 1f);
            else shapes.setColor(0.6f, 0.58f, 0.66f, 1f);
            shapes.rect(rx + gap / 2, ry + gap / 2, cell - gap, cell - gap);
        }
        b.hashCode();
    }
}
