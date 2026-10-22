package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

/** 조작 키 보기 (명세서 4.2 + 2026-10-10 결정). */
public class ControlsWindow extends GameWindow {

    private static final String[][] KEYS = {
            {"이동 (8방향)", "W A S D / 방향키"},
            {"기본 공격 (가까운 적을 자동으로 바라봄)", "J"},
            {"회전 베기 (쿨 6초)", "Q"},
            {"돌진 베기 (쿨 8초, 돌진 중 무적)", "L"},
            {"회피 (구르기, 무적)", "Space"},
            {"막기 (누르고 있기, 최대 3초, 앞쪽만)", "Ctrl / K"},
            {"캐기·대장간·침대·포탈·문양·대화", "E"},
            {"가방", "I"},
            {"지도", "M"},
            {"메뉴·창 닫기·취소", "Esc"},
            {"개발자 도구 (광물 무한·무적·바로 가기)", "F1"},
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
