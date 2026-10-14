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

    /** 큰 사건 알림: 아이콘 + 제목 + 설명. 한 번에 하나씩 차례로 보여 준다. */
    private static class Notice {
        String icon, title, desc;
    }

    private final Deque<Toast> toasts = new ArrayDeque<>();
    private final Deque<Notice> notices = new ArrayDeque<>();
    private Notice notice;
    private float noticeLife;
    public float noticeTime = 3.2f;
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

    /** icon 은 스프라이트 키(무기 등) 또는 임시 그림 이름(icon_*). */
    public void notice(String icon, String title, String desc) {
        Notice n = new Notice();
        n.icon = icon;
        n.title = title;
        n.desc = desc;
        notices.addLast(n);
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
        if (notice != null) {
            noticeLife -= dt;
            if (noticeLife <= 0) notice = null;
        }
        if (notice == null && !notices.isEmpty()) {
            notice = notices.pollFirst();
            noticeLife = noticeTime;
        }
    }

    /** 아이콘: "<키>_icon" 그림이 있으면 그것을, 없으면 그 키의 그림을 쓴다. */
    public static TextureRegion icon(SpriteLibrary sprites, String key) {
        SpriteLibrary.Entry e = sprites.get(key + "_icon");
        if (e == null) e = sprites.get(key);
        return e != null ? e.frame("idle", 0) : sprites.placeholder(key);
    }

    private TextureRegion icon(String key) {
        return icon(sprites, key);
    }

    /** 개발자 모드가 켜져 있으면 화면에 표시한다. */
    public boolean devOn;

    private void drawNotice(SpriteBatch batch) {
        if (notice == null) return;
        // 오른쪽 아래에서 미끄러져 들어온다
        float in = Math.min(1, (noticeTime - noticeLife) / 0.2f), out = Math.min(1, noticeLife / 0.3f);
        float nw = 300, nh = 46, nx = 960 - 12 - nw + (1 - in) * 40, ny = 12;
        batch.setColor(1, 1, 1, out);
        panel(batch, nx, ny, nw, nh);
        TextureRegion ic = icon(notice.icon);
        if (ic != null) {
            float k = Math.min(28f / ic.getRegionHeight(), 28f / ic.getRegionWidth());
            batch.setColor(1, 1, 1, out);
            batch.draw(ic, nx + 24 - ic.getRegionWidth() * k / 2f, ny + 9, ic.getRegionWidth() * k, ic.getRegionHeight() * k);
            batch.setColor(Color.WHITE);
        }
        fonts.body.setColor(UiSkin.GOLD.r, UiSkin.GOLD.g, UiSkin.GOLD.b, out);
        fonts.body.draw(batch, notice.title, nx + 48, ny + 37);
        fonts.body.setColor(UiSkin.TEXT);
        fonts.small.setColor(UiSkin.TEXT.r, UiSkin.TEXT.g, UiSkin.TEXT.b, out);
        fonts.small.draw(batch, notice.desc, nx + 48, ny + 17);
        fonts.small.setColor(UiSkin.TEXT);
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

        // 받은 축복 (이번 목숨 동안)
        if (!w.state.blessings.isEmpty()) {
            StringBuilder sb = new StringBuilder("축복  ");
            for (int i = 0; i < w.state.blessings.size(); i++) {
                Balance.BlessingDef bd = b.blessingOrNull(w.state.blessings.get(i));
                if (bd == null) continue;
                if (i > 0) sb.append(" · ");
                sb.append(bd.name);
            }
            layout.setText(small, sb);
            panel(batch, x, y - 94, layout.width + 20, 26);
            small.setColor(0.85f, 0.75f, 1f, 1f);
            small.draw(batch, sb, x + 10, y - 75);
            small.setColor(UiSkin.TEXT);
        }

        // ---- 오른쪽 위: 구역별 광물 · 골드 ----
        {
            float pw = 14;
            String[] nums = new String[b.zones.length + 1];
            for (int i = 0; i < b.zones.length; i++) nums[i] = String.valueOf(w.state.ore(i));
            nums[b.zones.length] = String.valueOf(w.state.gold);
            for (String n : nums) {
                layout.setText(small, n);
                pw += 16 + layout.width + 12;
            }
            float px = 960 - 12 - pw;
            panel(batch, px, y - 26, pw, 26);
            float cx = px + 10;
            for (int i = 0; i < nums.length; i++) {
                boolean gold = i == b.zones.length;
                float[] c = gold ? GOLD_COIN : b.zones[i].oreColor;
                // 광물은 마름모, 골드는 동전
                batch.setColor(c[0] * 0.55f, c[1] * 0.55f, c[2] * 0.55f, 1);
                if (gold) batch.draw(tx.white, cx, y - 19, 10, 12);
                else gem(batch, cx + 5, y - 13, 6);
                batch.setColor(c[0], c[1], c[2], 1);
                if (gold) batch.draw(tx.white, cx + 2, y - 17, 6, 8);
                else gem(batch, cx + 5, y - 13, 4);
                batch.setColor(Color.WHITE);
                small.setColor(UiSkin.TEXT);
                small.draw(batch, nums[i], cx + 15, y - 8);
                layout.setText(small, nums[i]);
                cx += 16 + layout.width + 12;
            }
        }

        // 미니맵 (정사각형)과 현재 위치 이름
        float mm = 150;
        drawMinimap(batch, w, 960 - 12 - mm, y - 26 - 6 - mm, mm);
        {
            String loc = w.locationName();
            layout.setText(small, loc);
            small.setColor(UiSkin.DIM);
            small.draw(batch, loc, 960 - 12 - layout.width - 4, y - 26 - 6 - mm - 5);
            small.setColor(UiSkin.TEXT);
        }

        // ---- 아래 왼쪽: 무기 · 회피 · 충격파 (작은 칸 세 개. 방 아래쪽을 가리지 않게) ----
        float by = 10, slot = 34;
        WeaponState ws = w.state.equippedWeapon();
        slot(batch, 12, by, slot);
        if (ws != null) {
            TextureRegion icon = icon(ws.type);
            float k = Math.min(24f / icon.getRegionHeight(), 24f / icon.getRegionWidth());
            batch.draw(icon, 12 + slot / 2f - icon.getRegionWidth() * k / 2f, by + 6, icon.getRegionWidth() * k, icon.getRegionHeight() * k);
            small.setColor(UiSkin.GOLD);
            small.draw(batch, "+" + ws.level, 12 + 2, by + 13);
        }
        key(batch, small, "J", 12, by, slot);
        // 회피
        float dx = 12 + slot + 4;
        slot(batch, dx, by, slot);
        float dodgeTotal = b.hero.dodgeCooldown * w.mods.dodgeCooldownMul;
        batch.setColor(0.45f, 0.8f, 1f, w.hero.dodgeCooldown > 0 ? 0.35f : 1f);
        batch.draw(tx.white, dx + 9, by + 14, 16, 3);
        batch.draw(tx.white, dx + 19, by + 11, 3, 9);
        batch.draw(tx.white, dx + 22, by + 13, 3, 5);
        batch.setColor(Color.WHITE);
        cooldown(batch, dx, by, slot, dodgeTotal > 0 ? w.hero.dodgeCooldown / dodgeTotal : 0);
        key(batch, small, "K", dx, by, slot);
        // 충격파
        float sx = dx + slot + 4;
        slot(batch, sx, by, slot);
        boolean unlocked = Progress.isAwakeningUnlocked(b, w.state);
        batch.setColor(unlocked ? Color.WHITE : new Color(0.35f, 0.35f, 0.4f, 1f));
        batch.draw(sprites.placeholder("icon_shock"), sx + 5, by + 5, 24, 24);
        batch.setColor(Color.WHITE);
        if (unlocked) cooldown(batch, sx, by, slot, w.hero.shockCooldown / b.shockwave.cooldown);
        key(batch, small, "U", sx, by, slot);
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

        drawNotice(batch);
        if (devOn) {
            small.setColor(1f, 0.5f, 0.5f, 1f);
            small.draw(batch, "개발자 모드 (F1)", 130, 30);
            small.setColor(UiSkin.TEXT);
        }

        // 조작 안내는 시작하고 잠깐만 보여 준다 (방 아래쪽을 가리지 않게). 전체 조작은 Esc 메뉴에 있다.
        if (w.time < HELP_TIME) {
            float a = Math.min(1, (HELP_TIME - w.time) / 2f);
            String help = "WASD 이동   J 공격   K 회피   L 채굴/사용   U 충격파   I 대장간   O 무기 교체   Esc 메뉴";
            layout.setText(small, help);
            batch.setColor(0, 0, 0, 0.55f * a);
            batch.draw(tx.white, 480 - layout.width / 2f - 10, 12, layout.width + 20, 22);
            batch.setColor(Color.WHITE);
            small.setColor(0.85f, 0.83f, 0.77f, a);
            small.draw(batch, help, 480 - layout.width / 2f, 28);
            small.setColor(UiSkin.TEXT);
        }
    }

    private static final float HELP_TIME = 25f;
    private static final float[] GOLD_COIN = {1f, 0.82f, 0.3f};

    /** 아래쪽 작은 칸. */
    private void slot(SpriteBatch batch, float x, float y, float size) {
        batch.setColor(0.07f, 0.06f, 0.09f, 0.8f);
        batch.draw(tx.white, x, y, size, size);
        batch.setColor(0.73f, 0.69f, 0.63f, 0.9f);
        batch.draw(tx.white, x, y, size, 1);
        batch.draw(tx.white, x, y + size - 1, size, 1);
        batch.draw(tx.white, x, y, 1, size);
        batch.draw(tx.white, x + size - 1, y, 1, size);
        batch.setColor(Color.WHITE);
    }

    /** 칸 오른쪽 위의 키 글자. */
    private void key(SpriteBatch batch, BitmapFont f, String k, float x, float y, float size) {
        f.setColor(UiSkin.DIM);
        f.draw(batch, k, x + size - 10, y + size - 3);
    }

    /** 재사용 대기: 아래에서 위로 걷히는 어둠. p 는 남은 비율. */
    private void cooldown(SpriteBatch batch, float x, float y, float size, float p) {
        if (p <= 0) return;
        batch.setColor(0, 0, 0, 0.62f);
        batch.draw(tx.white, x + 1, y + 1, size - 2, (size - 2) * Math.min(1, p));
        batch.setColor(Color.WHITE);
    }

    private void gem(SpriteBatch batch, float cx, float cy, int r) {
        for (int i = 0; i <= r; i++) {
            float half = r - i;
            batch.draw(tx.white, cx - half, cy + i, half * 2 + 1, 1);
            batch.draw(tx.white, cx - half, cy - i, half * 2 + 1, 1);
        }
    }

    private static final Color[] THEME = {
            new Color(0.75f, 0.62f, 0.45f, 1), new Color(0.62f, 0.5f, 0.38f, 1),
            new Color(0.45f, 0.55f, 0.7f, 1), new Color(0.58f, 0.45f, 0.75f, 1)};

    private TextureRegion face;

    /** 용사 얼굴 (정면 그림의 머리 부분). */
    private TextureRegion face() {
        if (face == null) {
            TextureRegion r = sprites.get("hero").frame("idle", "down", 0);
            face = new TextureRegion(r, 5, 0, Math.min(16, r.getRegionWidth() - 5), Math.min(15, r.getRegionHeight()));
        }
        return face;
    }

    /**
     * 미니맵: 용사 주변을 정사각형으로 보여 준다. 방은 모두 표시한다 —
     * 아직 안 가 본 방은 어두운 윤곽, 가 본 방은 구역 색, 돌파하지 못한 전투방은 붉게.
     * 보스방과 제단 방에는 표식을 찍고, 용사 자리에는 얼굴을 그린다.
     */
    private void drawMinimap(SpriteBatch batch, GameWorld w, float x, float y, float size) {
        com.yongsa.world.DungeonMap m = w.map;
        panel(batch, x, y, size, size);
        float pad = 3;
        float viewW = 4300f; // 미니맵이 보여주는 월드 폭(px): 좌우로 방 두 개쯤
        float k = (size - pad * 2) / viewW;
        float left = x + pad, bottom = y + pad, right = x + size - pad, top = y + size - pad;
        float ox = x + size / 2f - w.hero.x * k, oy = y + size / 2f - w.hero.y * k;
        // 문으로 이어진 길 (방과 방 사이를 가는 선으로)
        batch.setColor(0.5f, 0.48f, 0.46f, 0.7f);
        for (com.yongsa.world.DoorDef d : m.doors) {
            float dx = ox + (d.bounds.x + d.bounds.width / 2f) * k, dy = oy + (d.bounds.y + d.bounds.height / 2f) * k;
            if (dx < left + 1 || dx > right - 2 || dy < bottom + 1 || dy > top - 2) continue;
            batch.draw(tx.white, dx - 1, dy - 1, 3, 3);
        }
        for (com.yongsa.world.RoomDef r : m.rooms) {
            boolean visited = r.kind == com.yongsa.world.RoomDef.Kind.CAMP || w.state.visitedRooms.contains(r.id);
            boolean boss = r.kind == com.yongsa.world.RoomDef.Kind.BOSS;
            boolean bossAlive = boss && !w.state.isBossDefeated(w.b.zone(r.zone).boss);
            float rx = ox + r.bounds.x * k, ry = oy + r.bounds.y * k;
            float rx2 = rx + r.bounds.width * k, ry2 = ry + r.bounds.height * k;
            // 패널 밖은 잘라낸다
            float cx0 = Math.max(rx, left), cy0 = Math.max(ry, bottom), cx1 = Math.min(rx2, right), cy1 = Math.min(ry2, top);
            if (cx1 <= cx0 || cy1 <= cy0) continue;
            if (!visited) {
                batch.setColor(0.2f, 0.2f, 0.24f, 0.9f);
            } else if (boss) {
                batch.setColor(bossAlive ? 0.9f : 0.5f, bossAlive ? 0.2f : 0.3f, bossAlive ? 0.2f : 0.3f, 0.9f);
            } else if (r.isCombat() && !w.state.isRoomCleared(r.id)) {
                batch.setColor(0.75f, 0.32f, 0.28f, 0.9f);
            } else {
                Color c = THEME[Math.max(0, Math.min(3, m.theme((int) (r.centerX() / m.tileSize), (int) (r.centerY() / m.tileSize))))];
                batch.setColor(c.r, c.g, c.b, 0.9f);
            }
            batch.draw(tx.white, cx0, cy0, cx1 - cx0, cy1 - cy0);
            // 윤곽: 안 가 본 방은 흐리게, 지금 있는 방은 금색
            if (r == w.room) batch.setColor(UiSkin.GOLD);
            else batch.setColor(0.55f, 0.53f, 0.5f, visited ? 0f : 0.9f);
            batch.draw(tx.white, cx0, cy0, cx1 - cx0, 1);
            batch.draw(tx.white, cx0, cy1 - 1, cx1 - cx0, 1);
            batch.draw(tx.white, cx0, cy0, 1, cy1 - cy0);
            batch.draw(tx.white, cx1 - 1, cy0, 1, cy1 - cy0);
            float mx = (rx + rx2) / 2f, my = (ry + ry2) / 2f;
            if (mx < left + 3 || mx > right - 3 || my < bottom + 3 || my > top - 3) continue;
            if (bossAlive) {
                // 보스: 붉은 해골 표식 대신 굵은 X
                batch.setColor(1f, 0.85f, 0.85f, 1);
                for (int i = -3; i <= 3; i++) {
                    batch.draw(tx.white, mx + i, my + i, 1, 1);
                    batch.draw(tx.white, mx + i, my - i, 1, 1);
                }
            } else if ("shrine".equals(r.feature)) {
                // 제단: 보랏빛 마름모
                batch.setColor(0.85f, 0.6f, 1f, 1);
                gem(batch, mx, my, 3);
            }
        }
        batch.setColor(Color.WHITE);
        TextureRegion f = face();
        float fw = 14, fh = fw * f.getRegionHeight() / f.getRegionWidth();
        batch.setColor(0, 0, 0, 0.8f);
        batch.draw(tx.white, x + size / 2f - fw / 2f - 1, y + size / 2f - fh / 2f - 1, fw + 2, fh + 2);
        batch.setColor(Color.WHITE);
        batch.draw(f, x + size / 2f - fw / 2f, y + size / 2f - fh / 2f, fw, fh);
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
