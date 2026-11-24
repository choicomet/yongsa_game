package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.yongsa.screen.GameScreen;

/**
 * Esc 메뉴 (UI-09): 계속하기 · 설정 · 타이틀로 세 가지만 둔다.
 * 가방은 B 키, 저장은 자동 저장(강화·보스 처치)과 타이틀로 갈 때 저장으로 대신한다.
 * 조작 키는 설정 창 안에서 연다.
 */
public class PauseMenu extends Modal {
    private final GameScreen screen;

    public PauseMenu(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
    }

    @Override
    public void build() {
        nav.style = "round";
        Table p = panel("panelRound");
        p.pad(26, 36, 30, 36);
        p.add(label("일시정지", "large", Ui.GOLD)).padBottom(10).row();
        p.add(new Image(skin.getDrawable("divider"))).width(220).height(1).padBottom(20).row();
        float w = 280, h = 50;
        p.add(nav.add("계속하기", this::close)).width(w).height(h).padBottom(10).row();
        p.add(nav.add("설정", () -> screen.modals.push(new SettingsWindow(screen.game,
                () -> screen.modals.push(new ControlsWindow(screen.game, screen::applyKeyNames,
                        screen.session.rules.skillKeyNames(screen.session.state))))))).width(w).height(h).padBottom(10).row();
        p.add(nav.add("타이틀로", () -> screen.modals.push(ConfirmDialog.simple(skin, "타이틀로",
                "저장하고 타이틀 화면으로 갑니다.", "타이틀로", () -> {
                    screen.save(false);
                    screen.toTitle();
                })))).width(w).height(h).row();
    }
}
