package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.config.Balance;
import com.yongsa.model.CraftService;
import com.yongsa.model.DamageCalc;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.GameState;
import com.yongsa.model.Ko;
import com.yongsa.model.Progress;
import com.yongsa.model.Rng;
import com.yongsa.model.WeaponState;

/**
 * 휴대용 대장간 (I 키: 용사가 주머니에서 꺼낸다). 제작·강화 탭이 있고, 가진 무기는 제작 탭에서 바로 장착한다.
 * 제작과 강화는 실행 후 결과창을 띄운다. 만든 장비는 없앨 수 없다.
 * 키보드: 위·아래는 줄 사이(탭 줄 ↔ 내용), 좌·우는 같은 줄 안에서 움직인다.
 */
public class ForgeWindow {
    public enum Tab { CRAFT, ENHANCE }

    private static Tab lastTab = Tab.CRAFT;
    private static String selected;

    private final Windows w;
    private final GameActions g;
    private Windows.Modal modal;
    private Table body;
    private Table tabs;
    private Label minerals;
    /** 창을 다시 그린 뒤 키보드 선택을 둘 곳. */
    private Actor focus;
    private Actor currentTab;
    private Actor closeButton, lastTabButton, pickedWeapon;

    public ForgeWindow(Windows w, GameActions g) {
        this.w = w;
        this.g = g;
    }

    /** 새 게임 시작 시 선택 기억을 지운다. */
    public static void reset() {
        lastTab = Tab.CRAFT;
        selected = null;
    }

    public void open() {
        GameState s = g.state();
        if (!s.hasAnyWeapon()) lastTab = Tab.CRAFT;
        else if (selected == null || !s.owns(selected)) selected = s.equipped;
        Table p = w.panel();
        Table head = new Table();
        head.add(w.label("대장간", "large")).left().expandX();
        minerals = w.label("", "gold");
        head.add(minerals).right().padRight(14);
        closeButton = w.button("닫기", () -> w.close(modal));
        head.add(closeButton).right();
        p.add(head).growX().row();
        tabs = new Table();
        p.add(tabs).left().padTop(10).row();
        body = new Table();
        p.add(body).width(600).height(330).padTop(8).top().left();
        modal = w.push(p, "forge");
        refresh(false);
    }

    /** @param keepTabFocus 탭을 눌러 바꾼 경우: 선택을 탭에 그대로 둔다 (아래 키로 내용에 들어간다) */
    private void refresh(boolean keepTabFocus) {
        GameState s = g.state();
        Balance b = g.balance();
        StringBuilder ores = new StringBuilder();
        for (int i = 0; i < b.zones.length; i++) {
            if (i > 0) ores.append("   ");
            ores.append(b.zones[i].oreName).append(' ').append(s.ore(i));
        }
        minerals.setText(ores);
        tabs.clear();
        tab("제작", Tab.CRAFT);
        tab("강화", Tab.ENHANCE);
        body.clear();
        body.top().left();
        focus = null;
        w.clearLinks();
        // 닫기에서 왼쪽은 바로 옆의 탭(강화)으로, 그 탭에서 오른쪽은 다시 닫기로
        w.link(closeButton, -1, 0, lastTabButton);
        w.link(lastTabButton, 1, 0, closeButton);
        switch (lastTab) {
            case CRAFT: buildCraft(); break;
            case ENHANCE: buildEnhance(); break;
        }
        w.select(keepTabFocus || focus == null ? currentTab : focus);
    }

    private void tab(String text, Tab t) {
        TextButton b = w.button(text, "tab", () -> {
            lastTab = t;
            refresh(true);
        });
        b.setChecked(lastTab == t);
        if (lastTab == t) currentTab = b;
        lastTabButton = b; // 탭 줄의 맨 오른쪽
        tabs.add(b).width(90).padRight(6);
    }

    private String oreName(String weaponId) {
        return g.balance().zone(g.balance().weapon(weaponId).ore).oreName;
    }

    // ---- 제작 ----

    private void buildCraft() {
        Balance b = g.balance();
        GameState s = g.state();
        for (Balance.WeaponDef def : b.weapons) {
            Table row = new Table(w.skin);
            row.setBackground(w.skin.getDrawable("panel-light"));
            row.pad(8, 12, 8, 12);
            // 무기 그림: 한눈에 구분되게
            com.badlogic.gdx.graphics.g2d.TextureRegion icon = g.weaponIcon(def.id);
            if (icon != null) {
                com.badlogic.gdx.scenes.scene2d.ui.Image img = new com.badlogic.gdx.scenes.scene2d.ui.Image(icon);
                img.setScaling(com.badlogic.gdx.utils.Scaling.fit);
                row.add(img).size(44, 44).padRight(12);
            }
            Table info = new Table();
            info.add(w.label(def.name, "large")).left().row();
            info.add(w.label("기본 공격력 " + def.baseAttack + " · 공격 주기 " + fmt(def.cycle()) + "초 · 거리 "
                    + (int) def.reach + (def.comboHits > 1 ? " · " + def.comboHits + "연타" : ""), "small")).left().row();
            info.add(w.label(b.zone(def.ore).name + "의 " + oreName(def.id) + "으로 만든다", "dim")).left();
            info.left();
            row.add(info).expandX().fillX().left();
            CraftService.Block block = CraftService.craftBlock(b, s, def.id);
            Table right = new Table();
            right.add(w.label(oreName(def.id) + " " + def.craftCost, "gold")).right().row();
            TextButton btn;
            if (block == CraftService.Block.OWNED) {
                // 가진 무기는 여기서 바로 바꿔 든다
                boolean equipped = def.id.equals(s.equipped);
                btn = w.button(equipped ? "장착 중" : "장착", () -> {
                    if (CraftService.equip(s, def.id, g.inCombat()).executed) {
                        g.save();
                        g.sfx("ui_click");
                    }
                    refresh(false);
                });
                btn.setDisabled(equipped || g.inCombat());
            } else {
                btn = w.button("제작", () -> craft(def.id));
                btn.setDisabled(block != CraftService.Block.NONE);
            }
            if (block == CraftService.Block.NONE && focus == null) focus = btn;
            right.add(btn).right().padTop(4).row();
            if (block == CraftService.Block.LOCKED) {
                right.add(w.label(Progress.weaponLockReason(b, s, def.id), "warn")).right();
            } else if (block == CraftService.Block.NOT_ENOUGH) {
                right.add(w.label(oreName(def.id) + "이 부족합니다", "warn")).right();
            }
            row.add(right).right();
            body.add(row).growX().padBottom(8).row();
        }
    }

    private void craft(String type) {
        Balance b = g.balance();
        GameState s = g.state();
        CraftService.Result r = CraftService.craft(b, s, type);
        if (!r.executed) return;
        boolean saved = g.save();
        selected = type;
        g.sfx("craft");
        String name = b.weapon(type).name;
        String detail = oreName(type) + " " + r.minerals + "개를 사용했습니다.\n"
                + (r.autoEquipped ? Ko.eul(name) + " 장착했습니다." : "제작 탭의 [장착] 또는 O 키로 바꿔 들 수 있습니다.")
                + "\n피해량 " + DamageCalc.weaponDamage(b, type, 0);
        result(name + " 제작 완료", "good", detail, saved);
    }

    // ---- 강화 ----

    private final java.util.List<Actor> pickers = new java.util.ArrayList<>();

    private void weaponPicker() {
        GameState s = g.state();
        pickers.clear();
        pickedWeapon = null;
        Table list = new Table();
        for (WeaponState ws : s.weapons) {
            String text = g.balance().weapon(ws.type).name + " +" + ws.level + (ws.type.equals(s.equipped) ? " (장착)" : "");
            TextButton b = w.button(text, "small", () -> {
                selected = ws.type;
                refresh(false);
            });
            b.setChecked(ws.type.equals(selected));
            if (ws.type.equals(selected)) pickedWeapon = b;
            pickers.add(b);
            list.add(b).width(150).padBottom(4).row();
        }
        body.add(list).top().left().padRight(12);
    }

    private void buildEnhance() {
        GameState s = g.state();
        Balance b = g.balance();
        if (!s.hasAnyWeapon()) {
            body.add(w.label("강화할 무기가 없습니다. 제작 탭에서 무기를 만드세요.", "default")).left();
            return;
        }
        if (selected == null || !s.owns(selected)) selected = s.equipped;
        weaponPicker();
        EnhanceService.Preview p = EnhanceService.preview(b, s, selected);
        Table d = new Table();
        d.top().left();
        String name = b.weapon(selected).name;
        if (p.block == EnhanceService.Block.MAX_LEVEL) {
            d.add(w.label(name + " +" + p.level, "large")).left().row();
            d.add(w.label("최대 단계에 도달했습니다. 피해량 " + p.currentDamage, "default")).left().padTop(8).row();
        } else {
            d.add(w.label(name + " +" + p.level + "  →  +" + p.targetLevel, "large")).left().row();
            d.add(w.label("피해량 " + p.currentDamage + "  →  " + p.nextDamage, "gold")).left().padTop(6).row();
            d.add(w.label("비용 " + oreName(selected) + " " + p.cost + "  (보유 " + p.minerals + ")", "default")).left().padTop(6).row();
            d.add(w.label("이번 성공 확률 " + Math.round(p.chance * 100) + "%" + (p.guaranteed ? "  · 확정 성공" : ""),
                    "default")).left().padTop(4).row();
            d.add(w.label("확정 성공까지 남은 시도 " + p.triesToGuarantee + "회 (이번 포함)", "default")).left().padTop(4).row();
            d.add(w.label("실패해도 단계는 내려가지 않습니다.", "dim")).left().padTop(4).row();
        }
        d.add(w.label("현재 진행 상한 +" + p.cap, "dim")).left().padTop(4).row();
        // 강화 단계가 여는 무기 형태
        Balance.FormDef form = b.form(selected, p.level), next = b.nextForm(selected, p.level);
        if (form != null) d.add(w.label("형태 · " + form.name + ": " + form.desc, "default")).left().padTop(4).row();
        if (next != null) d.add(w.label("+" + next.fromLevel + " 형태 · " + next.name + ": " + next.desc, "dim")).left().padTop(4).row();
        TextButton btn = w.button("강화", () -> enhance(selected));
        btn.setDisabled(p.block != EnhanceService.Block.NONE);
        if (p.block == EnhanceService.Block.NONE) focus = btn;
        d.add(btn).left().padTop(10).width(120).row();
        // 무기 목록에서 오른쪽은 바로 강화 버튼으로, 강화 버튼에서 왼쪽은 고른 무기로
        for (Actor pick : pickers) w.link(pick, 1, 0, btn);
        w.link(btn, -1, 0, pickedWeapon);
        if (p.block != EnhanceService.Block.NONE) {
            String reason = p.block == EnhanceService.Block.NOT_ENOUGH ? oreName(selected) + "이 부족합니다" : p.block.label;
            if (p.block == EnhanceService.Block.CAP) {
                Balance.ZoneDef z = Progress.currentZone(b, s);
                if (z != null) reason += " · " + b.boss(z.boss).name + " 처치 시 상한 증가";
            }
            d.add(w.label(reason, "warn")).left().padTop(4);
        }
        body.add(d).top().left().growX();
    }

    private void enhance(String type) {
        GameState s = g.state();
        Balance b = g.balance();
        // 한 번 눌러 한 번만 처리. 결과창을 닫기 전까지 다음 강화 입력을 받지 않는다.
        EnhanceService.Result r = EnhanceService.enhance(b, s, type, Rng.enhanceSource(s));
        if (!r.executed) {
            refresh(false);
            return;
        }
        boolean saved = g.save();
        String name = b.weapon(type).name;
        if (r.success) {
            g.sfx("enhance_success");
            Balance.FormDef form = b.form(type, r.newLevel);
            boolean newForm = form != null && form.fromLevel == r.newLevel;
            result("강화 성공!  " + name + " +" + r.newLevel, "good",
                    "피해량 " + r.oldDamage + " → " + r.newDamage + "\n" + oreName(type) + " " + r.cost + "개 사용"
                            + (r.guaranteed ? "\n(확정 성공 시도)" : "")
                            + (newForm ? "\n새 형태 · " + form.name + ": " + form.desc : ""), saved);
        } else {
            g.sfx("enhance_fail");
            result("강화 실패  " + name + " +" + r.newLevel + " 유지", "bad",
                    oreName(type) + " " + r.cost + "개 사용 · 단계는 내려가지 않습니다.\n확정 성공까지 남은 시도 " + r.triesToGuarantee + "회", saved);
        }
    }

    // ---- 결과창 ----

    private void result(String title, String style, String detail, boolean saved) {
        Table p = w.panel();
        Label t = w.label(title, "good".equals(style) || "bad".equals(style) ? style : "large");
        p.add(t).left().row();
        t.getColor().a = 0;
        t.addAction(Actions.sequence(Actions.fadeIn(0.25f)));
        p.add(w.wrap(detail, "default", 340)).width(340).padTop(10).padBottom(10).row();
        Windows.Modal[] holder = new Windows.Modal[1];
        if (!saved) {
            p.add(w.label("저장하지 못했습니다", "warn")).left().row();
            p.add(w.button("저장 다시 시도", () -> {
                if (g.save()) {
                    w.close(holder[0]);
                    refresh(false);
                }
            })).left().padBottom(8).row();
        }
        p.add(w.button("확인", () -> w.close(holder[0]))).right();
        holder[0] = w.push(p, "result");
        holder[0].onClose = () -> refresh(false);
    }

    private static String fmt(float v) {
        return String.format("%.2f", v);
    }
}
