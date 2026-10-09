package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.yongsa.combat.Enemy;
import com.yongsa.config.Balance;
import com.yongsa.gfx.Fonts;
import com.yongsa.gfx.SpriteLibrary;
import com.yongsa.gfx.Textures;
import com.yongsa.model.DamageCalc;
import com.yongsa.model.Progress;
import com.yongsa.model.WeaponState;
import com.yongsa.world.GameWorld;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 게임 화면 정보 표시.
 * 왼쪽 위: 체력과 현재 목표 / 오른쪽 위: 광물과 현재 위치 / 아래: 장착 무기·강화 단계·충격파 / 보스전: 위쪽 중앙 보스 체력.
 */
public class Hud {
    private final Fonts fonts;
    private final Textures tx;
    private final SpriteLibrary sprites;
    private final GlyphLayout layout = new GlyphLayout();

    private static class Toast {
        String text;
        float life;
    }

    private final Deque<Toast> toasts = new ArrayDeque<>();
    private String banner;
    private float bannerLife;
    private String saveError;

    public Hud(Fonts fonts, Textures tx, SpriteLibrary sprites) {
        this.fonts = fonts;
        this.tx = tx;
        this.sprites = sprites;
    }

    public void toast(String text) {
        for (Toast t : toasts) {
            if (t.text.equals(text)) {
                t.life = 3f;
                return;
            }
        }
        Toast t = new Toast();
        t.text = text;
        t.life = 3f;
        toasts.addLast(t);
        while (toasts.size() > 3) toasts.removeFirst();
    }

    public void banner(String text, float seconds) {
        banner = text;
        bannerLife = seconds;
    }

    public void setSaveError(String msg) {
        saveError = msg;
    }

    public void update(float dt) {
        for (Toast t : toasts) t.life -= dt;
        toasts.removeIf(t -> t.life <= 0);
        if (bannerLife > 0) bannerLife -= dt;
    }

    public void draw(SpriteBatch batch, GameWorld w, Balance b) {
        BitmapFont body = fonts.body, small = fonts.small;

        // ---- 왼쪽 위: 체력 ----
        float x = 12, y = 540 - 12;
        panel(batch, x, y - 30, 250, 30);
        TextureRegion heart = sprites.placeholder("icon_heart");
        batch.draw(heart, x + 8, y - 24, 16, 16);
        float hpP = w.hero.hp / (float) w.hero.maxHp;
        batch.setColor(0.18f, 0.06f, 0.07f, 1f);
        batch.draw(tx.white, x + 30, y - 22, 140, 14);
        batch.setColor(0.82f, 0.16f, 0.16f, 1f);
        batch.draw(tx.white, x + 30, y - 22, 140 * hpP, 14);
        batch.setColor(1f, 0.45f, 0.4f, 1f);
        batch.draw(tx.white, x + 30, y - 11, 140 * hpP, 3);
        batch.setColor(Color.WHITE);
        body.setColor(UiSkin.TEXT);
        body.draw(batch, w.hero.hp + " / " + w.hero.maxHp, x + 178, y - 9);

        // 현재 목표
        com.yongsa.world.RoomDef fc = w.map.firstCombatRoom(b.zones[0].id);
        String obj = Progress.objective(b, w.state, fc == null ? null : fc.id);
        layout.setText(small, obj);
        float ow = layout.width + 34;
        panel(batch, x, y - 64, ow, 26);
        diamond(batch, x + 13, y - 51, 5);
        small.setColor(UiSkin.TEXT);
        small.draw(batch, obj, x + 24, y - 45);

        // ---- 오른쪽 위: 광물 | 위치 ----
        String minerals = String.valueOf(w.state.minerals);
        String loc = w.locationName();
        layout.setText(body, minerals);
        float mw = layout.width;
        layout.setText(body, loc);
        float lw = layout.width;
        float pw = 30 + mw + 22 + lw + 14;
        float px = 960 - 12 - pw;
        panel(batch, px, y - 30, pw, 30);
        batch.draw(sprites.placeholder("icon_mineral"), px + 8, y - 24, 16, 16);
        body.draw(batch, minerals, px + 30, y - 9);
        batch.setColor(0.6f, 0.56f, 0.52f, 1f);
        batch.draw(tx.white, px + 30 + mw + 10, y - 24, 1, 18);
        batch.setColor(Color.WHITE);
        body.draw(batch, loc, px + 30 + mw + 20, y - 9);

        drawMinimap(batch, w, 960 - 12 - 196, y - 30 - 8 - 100, 196, 100);

        // ---- 아래 왼쪽: 무기 ----
        WeaponState ws = w.state.equippedWeapon();
        float by = 12;
        panel(batch, 12, by, 196, 40);
        if (ws != null) {
            TextureRegion icon = sprites.get(ws.type).frame("idle", 0);
            float k = Math.min(28f / icon.getRegionHeight(), 28f / icon.getRegionWidth());
            batch.draw(icon, 34 - icon.getRegionWidth() * k / 2f, by + 6, icon.getRegionWidth() * k, icon.getRegionHeight() * k);
            String name = b.weapon(ws.type).name + " +" + ws.level;
            body.draw(batch, name, 54, by + 30);
            small.setColor(UiSkin.DIM);
            small.draw(batch, "피해 " + DamageCalc.weaponDamage(b, ws), 54, by + 15);
            small.setColor(UiSkin.TEXT);
        } else {
            small.setColor(UiSkin.DIM);
            small.draw(batch, "무기 없음 · 대장간에서 제작", 22, by + 25);
            small.setColor(UiSkin.TEXT);
        }

        // 충격파 칸
        float sx = 216;
        boolean unlocked = Progress.isAwakeningUnlocked(b, w.state);
        String lockText = b.boss(b.zones[0].boss).name + " 처치 후 해금";
        if (!unlocked) {
            layout.setText(small, lockText);
            panel(batch, sx, by, 46 + layout.width + 10, 40);
        } else {
            panel(batch, sx, by, 132, 40);
        }
        batch.setColor(unlocked ? Color.WHITE : new Color(0.4f, 0.4f, 0.45f, 1f));
        batch.draw(sprites.placeholder("icon_shock"), sx + 8, by + 8, 24, 24);
        batch.setColor(Color.WHITE);
        small.setColor(UiSkin.DIM);
        small.draw(batch, "Q", sx + 30, by + 34);
        if (!unlocked) {
            small.draw(batch, lockText, sx + 46, by + 25);
        } else if (w.hero.shockCooldown > 0) {
            float p = w.hero.shockCooldown / b.shockwave.cooldown;
            batch.setColor(0, 0, 0, 0.6f);
            batch.draw(tx.white, sx + 8, by + 8, 24, 24 * p);
            batch.setColor(Color.WHITE);
            small.setColor(UiSkin.TEXT);
            small.draw(batch, "충격파 " + String.format("%.1f", w.hero.shockCooldown) + "초", sx + 46, by + 25);
        } else {
            small.setColor(new Color(0.6f, 0.85f, 1f, 1f));
            small.draw(batch, "충격파 준비", sx + 46, by + 25);
        }
        small.setColor(UiSkin.TEXT);

        // ---- 보스 체력 ----
        if (w.combat != null && w.combat.boss) {
            Enemy boss = w.combat.bossEnemy();
            if (boss != null) {
                float bw = 300, bx = 480 - bw / 2f + 20, byy = 540 - 58;
                panel(batch, bx - 8, byy - 8, bw + 16, 42);
                layout.setText(body, boss.name());
                body.setColor(UiSkin.GOLD);
                body.draw(batch, boss.name(), bx + bw / 2f - layout.width / 2f, byy + 30);
                body.setColor(UiSkin.TEXT);
                float p = boss.hp / (float) boss.maxHp;
                batch.setColor(0.15f, 0.05f, 0.06f, 1);
                batch.draw(tx.white, bx, byy, bw, 10);
                batch.setColor(0.75f, 0.15f, 0.2f, 1);
                batch.draw(tx.white, bx, byy, bw * p, 10);
                batch.setColor(Color.WHITE);
            }
        }

        // ---- 안내 메시지 ----
        float ty = 540 - 110;
        for (Toast t : toasts) {
            float a = Math.min(1, t.life * 2);
            layout.setText(small, t.text);
            batch.setColor(0, 0, 0, 0.65f * a);
            batch.draw(tx.white, 480 - layout.width / 2f - 10, ty - layout.height - 7, layout.width + 20, layout.height + 12);
            batch.setColor(Color.WHITE);
            small.setColor(1, 0.95f, 0.85f, a);
            small.draw(batch, t.text, 480 - layout.width / 2f, ty);
            ty -= 26;
        }
        small.setColor(UiSkin.TEXT);

        // ---- 큰 알림 (보스 등장, 클리어) ----
        if (bannerLife > 0 && banner != null) {
            float a = Math.min(1, bannerLife * 2);
            BitmapFont lf = fonts.large;
            layout.setText(lf, banner);
            batch.setColor(0, 0, 0, 0.55f * a);
            batch.draw(tx.white, 0, 300, 960, 54);
            batch.setColor(Color.WHITE);
            lf.setColor(1f, 0.85f, 0.5f, a);
            lf.draw(batch, banner, 480 - layout.width / 2f, 300 + 27 + layout.height / 2f);
            lf.setColor(Color.WHITE);
        }

        // ---- 사망 ----
        if (w.hero.dead) {
            float k = Math.min(1, w.hero.deathTimer / 0.6f);
            batch.setColor(0.25f, 0f, 0f, 0.45f * k);
            batch.draw(tx.white, 0, 0, 960, 540);
            batch.setColor(Color.WHITE);
            BitmapFont lf = fonts.large;
            String t = "쓰러졌습니다 · 중앙 캠프로 돌아갑니다";
            layout.setText(lf, t);
            lf.setColor(1, 0.8f, 0.75f, k);
            lf.draw(batch, t, 480 - layout.width / 2f, 290);
            lf.setColor(Color.WHITE);
        }

        // ---- 저장 실패 ----
        if (saveError != null) {
            String t = "저장하지 못했습니다 · Esc 메뉴에서 다시 시도";
            layout.setText(small, t);
            batch.setColor(0.4f, 0.05f, 0.05f, 0.85f);
            batch.draw(tx.white, 480 - layout.width / 2f - 10, 64, layout.width + 20, 24);
            batch.setColor(Color.WHITE);
            small.setColor(1, 0.85f, 0.8f, 1);
            small.draw(batch, t, 480 - layout.width / 2f, 82);
            small.setColor(UiSkin.TEXT);
        }

        // 조작 도움 (첫 화면 근처에서만)
        if (!w.state.hasAnyWeapon() && w.state.minerals == 0 && w.time < 25) {
            String help = "이동 WASD · 공격 마우스 왼쪽 · 상호작용 E · 가방 I · 메뉴 Esc";
            layout.setText(small, help);
            small.setColor(1, 1, 1, MathUtils.clamp(25 - w.time, 0, 1) * 0.85f);
            small.draw(batch, help, 480 - layout.width / 2f, 104);
            small.setColor(UiSkin.TEXT);
        }
    }

    private static final Color[] THEME = {
            new Color(0.75f, 0.62f, 0.45f, 1), new Color(0.62f, 0.5f, 0.38f, 1),
            new Color(0.45f, 0.55f, 0.7f, 1), new Color(0.58f, 0.45f, 0.75f, 1)};

    /** 미니맵: 용사 주변 구역을 보여준다. 들어가 본 방만 보인다. 현재 방은 금색 테두리, 보스방은 붉게. */
    private void drawMinimap(SpriteBatch batch, GameWorld w, float x, float y, float mw, float mh) {
        com.yongsa.world.DungeonMap m = w.map;
        panel(batch, x, y, mw, mh);
        float pad = 4;
        float viewW = 4200f; // 미니맵이 보여주는 월드 폭(px)
        float k = (mw - pad * 2) / viewW;
        float cxw = w.hero.x, cyw = w.hero.y;
        float left = x + pad, bottom = y + pad, right = x + mw - pad, top = y + mh - pad;
        float ox = x + mw / 2f - cxw * k, oy = y + mh / 2f - cyw * k;
        for (com.yongsa.world.RoomDef r : m.rooms) {
            boolean visited = r.kind == com.yongsa.world.RoomDef.Kind.CAMP || w.state.visitedRooms.contains(r.id);
            if (!visited) continue;
            Color c;
            int theme = m.theme((int) (r.centerX() / m.tileSize), (int) (r.centerY() / m.tileSize));
            if (r.kind == com.yongsa.world.RoomDef.Kind.BOSS) {
                c = w.state.isBossDefeated(w.b.zone(r.zone).boss) ? new Color(0.5f, 0.3f, 0.3f, 1) : new Color(0.9f, 0.2f, 0.2f, 1);
            } else if (r.isCombat() && !w.state.isRoomCleared(r.id)) {
                c = new Color(0.75f, 0.32f, 0.28f, 1);
            } else {
                c = THEME[Math.max(0, Math.min(3, theme))];
            }
            float rx = ox + r.bounds.x * k, ry = oy + r.bounds.y * k;
            float rx2 = rx + r.bounds.width * k, ry2 = ry + r.bounds.height * k;
            // 패널 밖은 잘라낸다
            float cx0 = Math.max(rx, left), cy0 = Math.max(ry, bottom), cx1 = Math.min(rx2, right), cy1 = Math.min(ry2, top);
            if (cx1 <= cx0 || cy1 <= cy0) continue;
            batch.setColor(c.r, c.g, c.b, 0.85f);
            batch.draw(tx.white, cx0, cy0, cx1 - cx0, cy1 - cy0);
            if (r == w.room) {
                batch.setColor(UiSkin.GOLD);
                batch.draw(tx.white, cx0, cy0, cx1 - cx0, 1);
                batch.draw(tx.white, cx0, cy1 - 1, cx1 - cx0, 1);
                batch.draw(tx.white, cx0, cy0, 1, cy1 - cy0);
                batch.draw(tx.white, cx1 - 1, cy0, 1, cy1 - cy0);
            }
            // 미처치 보스방 표시
            if (r.kind == com.yongsa.world.RoomDef.Kind.BOSS && !w.state.isBossDefeated(w.b.zone(r.zone).boss)) {
                batch.setColor(1, 0.9f, 0.9f, 1);
                float bx = (cx0 + cx1) / 2f, by = (cy0 + cy1) / 2f;
                batch.draw(tx.white, bx - 2, by - 1, 4, 3);
            }
        }
        float blink = (w.time * 3) % 1 < 0.6f ? 1 : 0.4f;
        batch.setColor(1, 1, 1, blink);
        batch.draw(tx.white, x + mw / 2f - 2, y + mh / 2f - 2, 4, 4);
        batch.setColor(Color.WHITE);
    }

    private void panel(SpriteBatch batch, float x, float y, float w, float h) {
        batch.setColor(0.07f, 0.06f, 0.09f, 0.86f);
        batch.draw(tx.white, x, y, w, h);
        batch.setColor(0.73f, 0.69f, 0.63f, 1f);
        batch.draw(tx.white, x, y, w, 2);
        batch.draw(tx.white, x, y + h - 2, w, 2);
        batch.draw(tx.white, x, y, 2, h);
        batch.draw(tx.white, x + w - 2, y, 2, h);
        batch.setColor(Color.WHITE);
    }

    private void diamond(SpriteBatch batch, float cx, float cy, float r) {
        batch.setColor(UiSkin.GOLD);
        for (int i = 0; i <= r; i++) {
            float half = r - i;
            batch.draw(tx.white, cx - half, cy + i, half * 2 + 1, 1);
            batch.draw(tx.white, cx - half, cy - i, half * 2 + 1, 1);
        }
        batch.setColor(0.07f, 0.06f, 0.09f, 1f);
        for (int i = 0; i <= r - 2; i++) {
            float half = r - 2 - i;
            batch.draw(tx.white, cx - half, cy + i, half * 2 + 1, 1);
            batch.draw(tx.white, cx - half, cy - i, half * 2 + 1, 1);
        }
        batch.setColor(Color.WHITE);
    }
}
