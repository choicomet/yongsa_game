package com.yongsa.ui;

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
 * 대장간: 제작·강화·분해 탭. 탭을 바꿔도 선택한 보유 무기는 유지한다.
 * 제작과 강화는 실행 후 결과창, 분해는 실행 전 확인창을 띄운다.
 */
public class ForgeWindow {
    public enum Tab { CRAFT, ENHANCE, DISMANTLE }

    private static Tab lastTab = Tab.CRAFT;
    private static String selected;

    private final Windows w;
    private final GameActions g;
    private Windows.Modal modal;
    private Table body;
    private Table tabs;
    private Label minerals;

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
        head.add(w.button("닫기", () -> w.close(modal))).right();
        p.add(head).growX().row();
        tabs = new Table();
        p.add(tabs).left().padTop(10).row();
        body = new Table();
        p.add(body).width(560).height(300).padTop(8).top().left();
        modal = w.push(p, "forge");
        refresh();
    }

    private void refresh() {
        GameState s = g.state();
        minerals.setText("광물 " + s.minerals);
        tabs.clear();
        tab("제작", Tab.CRAFT);
        tab("강화", Tab.ENHANCE);
        tab("분해", Tab.DISMANTLE);
        body.clear();
        body.top().left();
        switch (lastTab) {
            case CRAFT: buildCraft(); break;
            case ENHANCE: buildEnhance(); break;
            case DISMANTLE: buildDismantle(); break;
        }
    }

    private void tab(String text, Tab t) {
        TextButton b = w.button(text, "tab", () -> {
            lastTab = t;
            refresh();
        });
        b.setChecked(lastTab == t);
        tabs.add(b).width(90).padRight(6);
    }

    // ---- 제작 ----

    private void buildCraft() {
        Balance b = g.balance();
        GameState s = g.state();
        for (Balance.WeaponDef def : b.weapons) {
            Table row = new Table(w.skin);
            row.setBackground(w.skin.getDrawable("panel-light"));
            row.pad(8, 12, 8, 12);
            Table info = new Table();
            info.add(w.label(def.name, "large")).left().row();
            info.add(w.label("기본 공격력 " + def.baseAttack + " · 공격 주기 " + fmt(def.cycle()) + "초 · 거리 "
                    + (int) def.reach + " · 폭 " + (int) def.width, "small")).left().row();
            info.add(w.label(def.id.equals("sword") ? "빠른 반격과 위치 조정" : "넓은 범위로 여러 적 공격", "dim")).left();
            info.left();
            row.add(info).expandX().fillX().left();
            CraftService.Block block = CraftService.craftBlock(b, s, def.id);
            Table right = new Table();
            right.add(w.label("비용 광물 " + def.craftCost, "gold")).right().row();
            TextButton btn = w.button(block == CraftService.Block.OWNED ? "보유 중" : "제작", () -> craft(def.id));
            btn.setDisabled(block != CraftService.Block.NONE);
            right.add(btn).right().padTop(4).row();
            if (block == CraftService.Block.LOCKED) {
                right.add(w.label(Progress.weaponLockReason(b, s, def.id), "warn")).right();
            } else if (block == CraftService.Block.NOT_ENOUGH) {
                right.add(w.label(block.label, "warn")).right();
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
        String detail = "광물 " + r.minerals + "개를 사용했습니다.\n"
                + (r.autoEquipped ? Ko.eul(name) + " 장착했습니다." : "가방에 추가했습니다. 가방(I)에서 장착할 수 있습니다.")
                + "\n피해량 " + DamageCalc.weaponDamage(b, type, 0);
        result(name + " 제작 완료", "good", detail, saved);
    }

    // ---- 강화 ----

    private void weaponPicker() {
        GameState s = g.state();
        Table list = new Table();
        for (WeaponState ws : s.weapons) {
            String text = g.balance().weapon(ws.type).name + " +" + ws.level + (ws.type.equals(s.equipped) ? " (장착)" : "");
            TextButton b = w.button(text, "small", () -> {
                selected = ws.type;
                refresh();
            });
            b.setChecked(ws.type.equals(selected));
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
            d.add(w.label("비용 광물 " + p.cost + "  (보유 " + p.minerals + ")", "default")).left().padTop(6).row();
            d.add(w.label("이번 성공 확률 " + Math.round(p.chance * 100) + "%" + (p.guaranteed ? "  · 확정 성공" : ""),
                    "default")).left().padTop(4).row();
            d.add(w.label("확정 성공까지 남은 시도 " + p.triesToGuarantee + "회 (이번 포함)", "default")).left().padTop(4).row();
            d.add(w.label("실패해도 단계는 내려가지 않습니다.", "dim")).left().padTop(4).row();
        }
        d.add(w.label("현재 진행 상한 +" + p.cap, "dim")).left().padTop(4).row();
        TextButton btn = w.button("강화", () -> enhance(selected));
        btn.setDisabled(p.block != EnhanceService.Block.NONE);
        d.add(btn).left().padTop(10).width(120).row();
        if (p.block != EnhanceService.Block.NONE) {
            String reason = p.block.label;
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
            refresh();
            return;
        }
        boolean saved = g.save();
        String name = b.weapon(type).name;
        if (r.success) {
            g.sfx("enhance_success");
            result("강화 성공!  " + name + " +" + r.newLevel, "good",
                    "피해량 " + r.oldDamage + " → " + r.newDamage + "\n광물 " + r.cost + "개 사용"
                            + (r.guaranteed ? "\n(확정 성공 시도)" : ""), saved);
        } else {
            g.sfx("enhance_fail");
            result("강화 실패  " + name + " +" + r.newLevel + " 유지", "bad",
                    "광물 " + r.cost + "개 사용 · 단계는 내려가지 않습니다.\n확정 성공까지 남은 시도 " + r.triesToGuarantee + "회", saved);
        }
    }

    // ---- 분해 ----

    private void buildDismantle() {
        GameState s = g.state();
        Balance b = g.balance();
        if (!s.hasAnyWeapon()) {
            body.add(w.label("분해할 무기가 없습니다.", "default")).left();
            return;
        }
        if (selected == null || !s.owns(selected)) selected = s.equipped;
        weaponPicker();
        WeaponState ws = s.weapon(selected);
        Table d = new Table();
        d.top().left();
        String name = b.weapon(selected).name;
        int refund = CraftService.refund(b, selected);
        d.add(w.label(name + " +" + ws.level, "large")).left().row();
        d.add(w.label("반환 광물 " + refund + " (제작 비용의 50%, 내림)", "gold")).left().padTop(6).row();
        d.add(w.label("강화 비용은 반환되지 않습니다.", "default")).left().padTop(4).row();
        d.add(w.label("분해한 뒤 같은 종류를 다시 제작하면 +0 입니다.", "dim")).left().padTop(4).row();
        CraftService.Block block = CraftService.dismantleBlock(s, selected, g.inCombat());
        TextButton btn = w.button("분해", () -> confirmDismantle(selected));
        btn.setDisabled(block != CraftService.Block.NONE);
        d.add(btn).left().padTop(10).width(120).row();
        if (block != CraftService.Block.NONE) d.add(w.label(block.label, "warn")).left().padTop(4);
        body.add(d).top().left().growX();
    }

    private void confirmDismantle(String type) {
        Balance b = g.balance();
        WeaponState ws = g.state().weapon(type);
        if (ws == null) return;
        String name = b.weapon(type).name;
        int refund = CraftService.refund(b, type);
        w.confirm("분해 확인", name + " +" + ws.level + "\n반환 광물 " + refund + "\n강화 비용은 반환되지 않습니다.", "분해", () -> {
            CraftService.Result r = CraftService.dismantle(b, g.state(), type, g.inCombat());
            if (!r.executed) {
                refresh();
                return;
            }
            boolean saved = g.save();
            g.sfx("dismantle");
            selected = g.state().equipped;
            result(name + " 분해 완료", "default", "광물 " + r.minerals + "개를 돌려받았습니다.", saved);
        });
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
                    refresh();
                }
            })).left().padBottom(8).row();
        }
        p.add(w.button("확인", () -> w.close(holder[0]))).right();
        holder[0] = w.push(p, "result");
        holder[0].onClose = this::refresh;
    }

    private static String fmt(float v) {
        return String.format("%.2f", v);
    }
}
