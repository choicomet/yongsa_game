package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.config.Balance;
import com.yongsa.model.CraftService;
import com.yongsa.model.DamageCalc;
import com.yongsa.model.GameState;
import com.yongsa.model.Progress;
import com.yongsa.model.WeaponState;

/** 가방: 보유 무기 정보와 장착 변경. 전투 중에는 정보만 볼 수 있다. */
public class BagWindow {
    private final Windows w;
    private final GameActions g;
    private Windows.Modal modal;
    private Table body;

    public BagWindow(Windows w, GameActions g) {
        this.w = w;
        this.g = g;
    }

    public void open() {
        Table p = w.panel();
        Table head = new Table();
        head.add(w.label("가방", "large")).left().expandX();
        head.add(w.button("닫기", () -> w.close(modal))).right();
        p.add(head).growX().row();
        body = new Table();
        p.add(body).width(520).padTop(10).top().left();
        modal = w.push(p, "bag");
        refresh();
    }

    private void refresh() {
        Balance b = g.balance();
        GameState s = g.state();
        body.clear();
        body.top().left();
        body.add(w.label("광물 " + s.minerals, "gold")).left().row();
        if (s.weapons.isEmpty()) {
            body.add(w.label("보유한 무기가 없습니다. 중앙 캠프 대장간에서 제작하세요.", "default")).left().padTop(10).row();
        }
        for (WeaponState ws : s.weapons) {
            Balance.WeaponDef def = b.weapon(ws.type);
            Table row = new Table(w.skin);
            row.setBackground(w.skin.getDrawable("panel-light"));
            row.pad(8, 12, 8, 12);
            Table info = new Table();
            boolean eq = ws.type.equals(s.equipped);
            info.add(w.label(def.name + " +" + ws.level + (eq ? "  · 장착 중" : ""), eq ? "gold" : "default")).left().row();
            String fails = ws.level < b.maxLevel() ? " · +" + (ws.level + 1) + " 실패 누적 " + ws.failCount : "";
            info.add(w.label("피해 " + DamageCalc.weaponDamage(b, ws) + " · 공격 주기 " + String.format("%.2f", def.cycle())
                    + "초 · 거리 " + (int) def.reach + " · 폭 " + (int) def.width + fails, "small")).left();
            info.left();
            row.add(info).expandX().fillX().left();
            if (!eq) {
                CraftService.Block block = CraftService.equipBlock(s, ws.type, g.inCombat());
                TextButton btn = w.button("장착", () -> {
                    CraftService.Result r = CraftService.equip(s, ws.type, g.inCombat());
                    if (r.executed) {
                        g.save();
                        g.sfx("ui_click");
                    }
                    refresh();
                });
                btn.setDisabled(block != CraftService.Block.NONE);
                Table right = new Table();
                right.add(btn).right().row();
                if (block == CraftService.Block.IN_COMBAT) right.add(w.label("전투 중 교체 불가", "warn")).right();
                row.add(right).right();
            }
            body.add(row).growX().padTop(8).row();
        }
        String shock = Progress.isAwakeningUnlocked(b, s)
                ? "각성 충격파 (Q): 정면 직선 " + (int) b.shockwave.length + "px, 무기 피해의 " + b.shockwave.damageMultiplier
                + "배, 재사용 " + (int) b.shockwave.cooldown + "초"
                : "각성 충격파: " + b.boss(b.zones[0].boss).name + " 처치 후 해금";
        body.add(w.label(shock, "dim")).left().padTop(12).row();
        body.add(w.label("강화 상한 +" + Progress.enhanceCap(b, s), "dim")).left().padTop(4);
    }
}
