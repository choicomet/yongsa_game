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
import com.yongsa.enemy.Monster;
import com.yongsa.gfx.Art;
import com.yongsa.gfx.Fonts;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.OreType;
import com.yongsa.model.Vein;
import com.yongsa.model.Weapon;
import com.yongsa.map.Door;
import com.yongsa.map.Corridor;
import com.yongsa.map.Link;
import com.yongsa.map.Room;
import com.yongsa.map.RoomType;

import java.util.ArrayList;
import java.util.List;

/**
 * 플레이 화면 HUD (UI-03): 왼쪽 위 체력과 장착 무기, 오른쪽 위 광물 3종,
 * 아래 스킬 3개, 왼쪽 아래 “다음 할 일”, 보스방에서는 위 가운데 보스 체력.
 * 지도는 M 키로 열고 닫는다 (FR-맵-10).
 */
public class Hud {
    private final GameSession g;
    /** 칸 위에 적는 키 이름 (사용자가 조작 키를 바꾸면 GameScreen 이 바꿔 준다). */
    public String attackKey = "J", rollKey = "K", mapKey = "M", bagKey = "B", moveKeys = "W A S D";
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
    /** 공격 J, 구르기 K, 스킬 칸 넷 (L, U, I, O). 스킬 칸은 열렸을 때만 보인다. */
    private static final int SLOTS = 2 + com.yongsa.model.Rules.SKILL_SLOTS;
    private final float[] readyFlash = new float[SLOTS];
    private final boolean[] wasReady = {true, true, true, true, true, true};
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
        float[] out = new float[SLOTS];
        out[0] = Math.max(0, g.hero.attackCd);
        out[1] = Math.max(0, g.hero.rollCd);
        for (int i = 2; i < SLOTS; i++) out[i] = Math.max(0, g.hero.skillCd[i - 2]);
        return out;
    }

    /** 개발용 (F1 이동 영역 보기): 용사 발밑을 덮는 마스크 파일을 화면 아래에 적는다. */
    public boolean showWalkMasks;

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
            text(fonts.small, "공격력 " + g.rules.attack(s) + "  ·  보스의 힘 " + s.bossesDefeated.size() + "/3", 88, h - 101, Ui.DIM);
        }

        card(w - 320, h - 88, 296, 64);
        float ox = w - 306;
        for (OreType type : OreType.values()) {
            batch.setColor(Color.WHITE);
            batch.draw(art.gem(type), ox, h - 76, 40, 40);
            text(fonts.small, String.valueOf(s.ore(type)), ox + 43, h - 49, Ui.TEXT);
            ox += 94;
        }
        text(fonts.small, bagKey + "  가방     " + mapKey + "  지도     Esc  메뉴", w - 306, h - 103, Ui.DIM);
        if (showWalkMasks) {
            float my = 130;
            for (String m : g.map.walkMasksAt(g.hero.x, g.hero.y)) {
                text(fonts.small, m, 24, my, Ui.GOLD);
                my += 24;
            }
        }

        Room room = g.heroRoom();
        boolean village = room != null && room.type == RoomType.VILLAGE;
        String zone = room == null ? corridorZone() : village ? g.balance.village.name
                : room.dungeonId == null ? "용사의 캠프" : g.balance.dungeon(room.dungeonId).name;
        if (boss == null) {
            centered(fonts.small, banner == null ? zone : banner, w / 2, h - 35, Ui.GOLD);
            String sub = village ? (g.villagersFree() ? "모두가 돌아왔다" : "던전 밖 · 모두 수정 속에 잠들어 있다")
                    : g.inCamp() ? "정비하고, 다음 여정을 준비하세요"
                    : room != null && room.type == RoomType.TUNNEL ? "어두운 동굴 통로" : room == null ? "통로" : "탐험 중";
            centered(fonts.small, sub, w / 2, h - 59, Ui.DIM);
        } else {
            float bx = w / 2 - 210;
            card(bx - 16, h - 100, 452, 76);
            if (introT >= 0) rect(bx - 16, h - 25, 452 * Math.min(1, introT / 0.5f), 1, Ui.GOLD);
            centered(fonts.normal, boss.def.name, w / 2, h - 38, boss.finalBoss ? Ui.PURPLE : Ui.GOLD);
            rect(bx, h - 73, 420, 9, new Color(0x39232aff));
            rect(bx, h - 73, 420 * MathUtils.clamp(bossTrail / boss.maxHp, 0, 1), 9, Ui.GOLD);
            rect(bx, h - 73, 420 * MathUtils.clamp(bossShown / boss.maxHp, 0, 1), 9, boss.finalBoss ? Ui.PURPLE : Ui.RED);
            if (boss.shield > 0 && boss.shieldMax > 0) {
                // 방패: 체력바 위 밝은 막대. 다 깎기 전에는 체력이 닳지 않는다
                rect(bx, h - 61, 420, 4, new Color(0x2a2418ff));
                rect(bx, h - 61, 420 * MathUtils.clamp((float) boss.shield / boss.shieldMax, 0, 1), 4, new Color(1f, 0.82f, 0.55f, 1f));
            }
            centered(fonts.small, boss.shield > 0 ? "방패 " + boss.shield + "  ·  " + boss.hp + " / " + boss.maxHp
                    : boss.hp + " / " + boss.maxHp, w / 2, h - 80, Ui.DIM);
        }

        card(24, 24, 624, 88);
        rect(24, 24, 3, 88, Ui.GOLD);
        text(fonts.small, "다음 여정", 42, 95, Ui.GOLD);
        fonts.normal.setColor(Ui.TEXT);
        fonts.normal.draw(batch, g.nextTask(), 42, 69, 586, com.badlogic.gdx.utils.Align.left, true);
        text(fonts.small, moveKeys + "  이동    ·    " + g.interactKey + "  상호작용", 24, 136, Ui.DIM);

        float[] left = cooldowns();
        com.yongsa.model.Rules.SkillSlot[] skills = g.rules.skillSlots(g.state);
        java.util.List<Integer> shown = new java.util.ArrayList<>();
        shown.add(0);
        shown.add(1);
        for (int i = 0; i < skills.length; i++) if (skills[i] != null) shown.add(2 + i);
        for (int k = 0; k < shown.size(); k++) {
            int i = shown.get(k);
            float x = w - 132 - (shown.size() - 1 - k) * 120;
            com.yongsa.model.Rules.SkillSlot sk = i >= 2 ? skills[i - 2] : null;
            card(x, 24, 108, 88);
            if (readyFlash[i] > 0) rect(x, 111, 108, 2, Ui.GOLD);
            float total = i == 0 ? g.balance.attack.basicCooldown : i == 1 ? g.balance.attack.rollTime + g.balance.attack.rollCooldown
                    : sk.def.cooldown;
            if (left[i] > 0) rect(x + 1, 25, 106, 86 * Math.min(1, left[i] / total), new Color(0, 0, 0, 0.5f));
            batch.setColor(Color.WHITE);
            if (i == 0 && weapon != null) batch.draw(art.sword(weapon.defId, weapon.awakened), x + 35, 47, 58, 58);
            if (i == 1) batch.draw(art.get("hero_side_walk"), x + 48, 51, 34, 48);
            if (sk != null) {
                // 스킬 칸: 스킬 아이콘(sprites/skill/icon_<kind>.png, 없으면 그 검 그림) 뒤로 빛, 지금 못 쓰면 어둡게
                if (sk.usable) {
                    batch.setColor(sk.awakened ? new Color(0.85f, 0.6f, 1f, 0.55f + 0.2f * MathUtils.sin(time * 4f))
                            : new Color(1f, 0.85f, 0.5f, 0.5f + 0.2f * MathUtils.sin(time * 4f)));
                    batch.draw(art.glow, x + 24, 38, 72, 72);
                }
                batch.setColor(sk.usable ? Color.WHITE : new Color(0.35f, 0.35f, 0.4f, 1f));
                com.badlogic.gdx.graphics.Texture icon = skillIcon(sk.def.kind);
                if (icon != null) batch.draw(icon, x + 32, 44, 64, 64);
                else batch.draw(art.sword(sk.weaponId, sk.awakened), x + 35, 47, 58, 58);
                batch.setColor(Color.WHITE);
            }
            String key = i == 0 ? attackKey : i == 1 ? rollKey : g.skillKeys[i - 2];
            String name = i == 0 ? "베기" : i == 1 ? "구르기" : sk.def.name;
            text(fonts.small, key, x + 10, 100, Ui.GOLD);
            if (sk != null && sk.learned) text(fonts.small, "익힘", x + 62, 100, Ui.PURPLE);
            else if (sk != null && !sk.usable) text(fonts.small, "검 필요", x + 50, 100, Ui.DIM);
            text(fonts.small, name, x + 12, 47, sk != null && !sk.usable ? Ui.DIM : Ui.DIM);
            if (i >= 1 && left[i] > 0.1f) centered(fonts.small, String.format(java.util.Locale.ROOT, "%.1f", left[i]), x + 71, 77, Ui.TEXT);
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
        boolean inVillage = g.inVillage();
        for (Room r : g.map.rooms) {
            if (hiddenOnMap(r, inVillage)) continue;
            minX = Math.min(minX, r.x0);
            maxX = Math.max(maxX, r.x1);
            minY = Math.min(minY, r.y0);
            maxY = Math.max(maxY, r.y1);
        }
        for (Corridor c : g.map.corridors) {
            if (hiddenOnMap(c.link.a, inVillage) || hiddenOnMap(c.link.b, inVillage)) continue;
            for (int[] t : c.tiles) {
                minX = Math.min(minX, t[0]);
                maxX = Math.max(maxX, t[0]);
                minY = Math.min(minY, t[1]);
                maxY = Math.max(maxY, t[1]);
            }
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

        // 통로: 지나가 본 길만 보인다 (2026-10-14 해성: 가 본 곳만 지도에). 봉인된 길은 보랏빛.
        for (Corridor c : g.map.corridors) {
            Link l = c.link;
            if (hiddenOnMap(l.a, inVillage) || hiddenOnMap(l.b, inVillage)) continue;
            if (!g.state.seenCorridors.contains(c.key())) continue;
            if (l.gateClosed) shapes.setColor(0.65f, 0.45f, 1f, a);
            else shapes.setColor(0.42f, 0.4f, 0.46f, a);
            for (int[] t : c.tiles) shapes.rect(ox + t[0] * k, oy + t[1] * k, k + 0.6f, k + 0.6f);
        }
        // 방
        for (Room r : g.map.rooms) {
            boolean visited = known(r);
            if (hiddenOnMap(r, inVillage)) continue; // 심연은 길로 이어지지 않는다
            if (!visited) continue; // 가 본 방만 보인다
            Color c = roomColor(r, visited);
            float rx = ox + r.x0 * k, ry = oy + r.y0 * k, rw = (r.x1 - r.x0 + 1) * k, rh = (r.y1 - r.y0 + 1) * k;
            if (r.scene == null) {
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
            } else {
                // 그림 방은 실제 바닥 모양대로 칸을 칠한다
                float pad = r == here ? 3f : 1.2f;
                if (r == here) {
                    float pulse = 0.5f + 0.5f * MathUtils.sin(time * 5f);
                    shapes.setColor(1f, 0.85f, 0.35f, (0.6f + 0.4f * pulse) * a);
                } else {
                    shapes.setColor(0.03f, 0.03f, 0.05f, a);
                }
                for (int ty = r.y0; ty <= r.y1; ty++)
                    for (int tx = r.x0; tx <= r.x1; tx++)
                        if (r.floor(tx, ty)) shapes.rect(ox + tx * k - pad, oy + ty * k - pad, k + pad * 2, k + pad * 2);
                shapes.setColor(c.r, c.g, c.b, c.a * a);
                for (int ty = r.y0; ty <= r.y1; ty++)
                    for (int tx = r.x0; tx <= r.x1; tx++)
                        if (r.floor(tx, ty)) shapes.rect(ox + tx * k, oy + ty * k, k + 0.6f, k + 0.6f);
            }
            if (r.type == RoomType.ORE && visited) {
                List<Vein> veins = g.veinsOf(r);
                if (veins != null) {
                    for (Vein v : veins) {
                        shapes.setColor(oreColor(v.ore).r, oreColor(v.ore).g, oreColor(v.ore).b, a);
                        shapes.rect(ox + v.tileX * k - 1, oy + v.tileY * k - 1, Math.max(3, k) + 2, Math.max(3, k) + 2);
                    }
                }
            }
            float mcx = ox + (r.centerTileX() + 0.5f) * k, mcy = oy + (r.centerTileY() + 0.5f) * k;
            if (r.type == RoomType.BOSS && !g.state.bossDefeated(r.dungeonId)) {
                // 아직 살아 있는 보스: 굵은 X
                shapes.setColor(1f, 0.85f, 0.8f, a);
                shapes.rectLine(mcx - 9, mcy - 9, mcx + 9, mcy + 9, 4);
                shapes.rectLine(mcx - 9, mcy + 9, mcx + 9, mcy - 9, 4);
            } else if (g.hasChest(r)) {
                // 열지 않은 보물상자
                shapes.setColor(0.2f, 0.12f, 0.04f, a);
                shapes.rect(mcx - 10, mcy - 8, 20, 16);
                shapes.setColor(1f, 0.8f, 0.3f, a);
                shapes.rect(mcx - 8, mcy - 6, 16, 12);
                shapes.setColor(0.45f, 0.28f, 0.1f, a);
                shapes.rect(mcx - 8, mcy, 16, 2);
            }
            if (g.hasPortal(r)) {
                float cx = ox + g.portalX(r) / g.balance.tileSize * k, cy = oy + g.portalY(r) / g.balance.tileSize * k;
                shapes.setColor(0.55f, 0.45f, 1f, a);
                shapes.rect(cx - 8, cy - 7, 16, 18);
                shapes.setColor(0.85f, 0.8f, 1f, a);
                shapes.rect(cx - 4, cy - 7, 8, 13);
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
        String[] labels = {"캠프", "방", "광물방", "보스방", "보물상자", "봉인된 길"};
        Color[] colors = {new Color(0.85f, 0.6f, 0.3f, 1f), new Color(0.5f, 0.48f, 0.56f, 1f), new Color(0.35f, 0.5f, 0.75f, 1f),
                new Color(0.8f, 0.25f, 0.2f, 1f), new Color(1f, 0.8f, 0.3f, 1f), new Color(0.65f, 0.45f, 1f, 1f)};
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
        layout.setText(fonts.small, mapKey + "  닫기");
        fonts.small.draw(batch, mapKey + "  닫기", px + pw - 30 - layout.width, py + ph - 26);
        // 던전 이름
        String[] ids = {"LEFT", "RIGHT", "TOP"};
        for (String id : ids) {
            float sx = 0, sy = 0;
            int n = 0;
            for (Room r : g.map.rooms) {
                if (!id.equals(r.dungeonId) || r.type == RoomType.FINAL || hiddenOnMap(r, inVillage)) continue;
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

    /** 통로에 있을 때 위쪽 이름: 이어진 던전, 캠프와 마을 사이면 마을 가는 길. */
    private String corridorZone() {
        Corridor c = g.map.corridorAtWorld(g.hero.x, g.hero.y);
        if (c == null) return "던전 입구";
        if (c.link.a.type == RoomType.VILLAGE || c.link.b.type == RoomType.VILLAGE) return g.balance.village.name + " 가는 길";
        if ("CAMP".equals(c.theme)) return "용사의 캠프";
        return g.balance.dungeon(c.theme).name;
    }

    /** 심연은 땅 밑이라 그 안에 있을 때만 지도에 나온다. */
    private boolean hiddenOnMap(Room r, boolean inVillage) {
        // Cave interiors occupy a separate space; show a local map while inside.
        Room here = g.heroRoom();
        if (here != null && here.type == RoomType.TUNNEL) return r != here;
        if (r.type == RoomType.TUNNEL) return true;
        return r.type == RoomType.FINAL && r != g.heroRoom();
    }

    private boolean known(Room r) {
        return r.type == RoomType.CAMP || g.state.room(r.id).visited;
    }

    private static final Color UNSEEN = new Color(0.2f, 0.2f, 0.25f, 0.9f);

    private Color roomColor(Room r, boolean visited) {
        if (!visited && r.type != RoomType.BOSS && r.type != RoomType.FINAL) return UNSEEN;
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

    /** 스킬 아이콘 그림 (GPT 요청, docs/ART_REQUESTS.md 0-5). 없으면 null 이고 검 그림을 쓴다. 게임 내내 쓰므로 한 번만 읽는다. */
    private static final java.util.Map<String, com.badlogic.gdx.graphics.Texture> SKILL_ICONS = new java.util.HashMap<>();

    private static com.badlogic.gdx.graphics.Texture skillIcon(String kind) {
        if (kind == null) return null;
        if (SKILL_ICONS.containsKey(kind)) return SKILL_ICONS.get(kind);
        com.badlogic.gdx.files.FileHandle f = com.badlogic.gdx.Gdx.files.internal("sprites/skill/icon_" + kind + ".png");
        com.badlogic.gdx.graphics.Texture t = null;
        if (f.exists()) {
            t = new com.badlogic.gdx.graphics.Texture(f, true); // 큰 그림을 칸 크기로 줄여 그리므로 밉맵
            t.setFilter(com.badlogic.gdx.graphics.Texture.TextureFilter.MipMapLinearLinear, com.badlogic.gdx.graphics.Texture.TextureFilter.Linear);
        }
        SKILL_ICONS.put(kind, t);
        return t;
    }
}
