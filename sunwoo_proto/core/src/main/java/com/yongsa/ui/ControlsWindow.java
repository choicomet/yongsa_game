package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/** 조작 키 보기 (명세서 4.2 + 2026-10-10 결정). */
public class ControlsWindow extends GameWindow {

    private static final String[][] KEYS = {
            {"이동 (8방향)", "W A S D / 방향키"},
            {"기본 공격 (바라보는 방향)", "J / Space"},
            {"회전 베기 (쿨 6초)", "Q"},
            {"돌진 베기 (쿨 8초, 돌진 중 무적)", "L"},
            {"회피 (구르기, 무적)", "Shift"},
            {"캐기·대장간·침대·포탈·문양", "E"},
            {"가방", "I"},
            {"지도", "M"},
            {"메뉴·창 닫기·취소", "Esc"},
            {"창 안 버튼 이동·선택", "방향키 / Tab, Enter"},
    };

    public ControlsWindow(Skin skin) {
        super("조작 키", skin);
    }

    @Override
    public void refresh() {
        clearChildren();
        nav.clear();
        for (String[] k : KEYS) {
            add(new Label(k[0], skin)).width(360).left();
            add(new Label(k[1], skin, "large-gold")).width(260).left().padBottom(6).row();
        }
        add(button("닫기 (Esc)", this::close)).colspan(2).width(200).height(48).padTop(12);
        pack();
        centerOnStage();
    }
}
