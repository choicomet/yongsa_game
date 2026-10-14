package com.yongsa.screen;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.config.Balance;
import com.yongsa.model.GameState;
import com.yongsa.model.WeaponState;
import com.yongsa.ui.Windows;
import com.yongsa.world.GameWorld;

/**
 * 개발자 모드 (F1). 시험 플레이용이며 켜고 끄는 설정은 저장되지 않는다.
 * 다만 여기서 받은 장비·광석·골드는 진행 상태에 들어가므로 저장에 남는다.
 */
public class DevMenu {
    /** 켜져 있으면 매 프레임 광석과 골드를 가득 채운다. */
    public boolean infinite;
    /** 모든 장비와 기술을 쓸 수 있다 (해금 조건 무시). */
    public boolean unlockAll;

    private final GameScreen screen;
    private final Windows w;
    private Windows.Modal modal;

    DevMenu(GameScreen screen, Windows w) {
        this.screen = screen;
        this.w = w;
    }

    public boolean anyOn() {
        return infinite || unlockAll || screen.world().devInvincible || screen.view().showTelegraphs;
    }

    /** 매 프레임: 무제한이 켜져 있으면 자원을 채운다. */
    public void apply() {
        if (!infinite) return;
        GameState s = screen.state();
        for (int i = 0; i < screen.balance().zones.length; i++) if (s.ore(i) < 999) s.addOre(i, 999 - s.ore(i));
        s.gold = Math.max(s.gold, 9999);
    }

    public void open() {
        if (w.isOpen("dev")) return;
        Table p = w.panel();
        p.add(w.label("개발자 모드", "large")).left().row();
        p.add(w.label("F1 로 열고 닫는다 · 켜고 끄는 설정은 저장되지 않는다", "dim")).left().padBottom(10).row();
        Table body = new Table();
        p.add(body).left().row();
        modal = w.push(p, "dev");
        build(body);
    }

    private void build(Table body) {
        body.clear();
        GameWorld world = screen.world();
        Balance b = screen.balance();
        toggle(body, "무적", world.devInvincible, () -> world.devInvincible = !world.devInvincible);
        toggle(body, "광석·골드 무제한", infinite, () -> {
            infinite = !infinite;
            apply();
        });
        toggle(body, "모든 장비·기술 사용", unlockAll, () -> {
            unlockAll = !unlockAll;
            world.devUnlockAll = unlockAll;
            if (unlockAll) giveAll();
        });
        toggle(body, "공격 범위 미리 보기", screen.view().showTelegraphs,
                () -> screen.view().showTelegraphs = !screen.view().showTelegraphs);
        body.add(w.label("이동", "dim")).left().padTop(8).row();
        for (Balance.ZoneDef z : b.zones) {
            body.add(w.button(b.boss(z.boss).name + " 앞으로", () -> go(world.map.point("shortcut_" + z.id + "_ante")))).width(300).padBottom(4).row();
        }
        body.add(w.button("중앙 캠프로", () -> go(world.spawnPoint()))).width(300).padBottom(4).row();
        body.add(w.button("닫기", () -> w.close(modal))).width(300).padTop(6).row();
    }

    private void toggle(Table body, String name, boolean on, Runnable flip) {
        TextButton btn = w.button(name + "  ·  " + (on ? "켜짐" : "꺼짐"), "tab", () -> {
            flip.run();
            build(body);
        });
        btn.setChecked(on);
        body.add(btn).width(300).padBottom(4).row();
    }

    /** 아직 없는 무기를 모두 가방에 넣는다 (강화 단계는 +0). */
    private void giveAll() {
        GameState s = screen.state();
        for (Balance.WeaponDef d : screen.balance().weapons) {
            if (!s.owns(d.id)) s.weapons.add(new WeaponState(d.id));
        }
        if (s.equippedWeapon() == null) s.equipped = s.weapons.get(0).type;
    }

    private void go(com.badlogic.gdx.math.Vector2 point) {
        screen.world().devTeleport(point);
        w.close(modal);
    }
}
