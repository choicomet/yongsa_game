package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;

/** 조작 키 보기 (명세서 4.2). */
public class ControlsWindow extends Modal {
    public static final String[][] KEYS = {
            {"이동", "W A S D 또는 방향키"},
            {"공격 (누르고 있으면 계속)", "J"},
            {"구르기 (잠깐 무적)", "스페이스, 왼쪽 Shift 또는 K"},
            {"상호작용 (채굴, 대장간, 침대)", "E"},
            {"가방", "I"},
            {"지도 열기 / 닫기", "M 또는 Tab"},
            {"화면 확대 / 축소 (보스방은 따로 기억)", "마우스 휠 또는 + / -"},
            {"메뉴 · 창 닫기 · 취소", "Esc"},
            {"창 안의 버튼 이동 · 선택", "방향키 또는 Tab, Enter (마우스도 가능)"},
    };

    public ControlsWindow(Skin skin) {
        super(skin);
    }

    @Override
    public void build() {
        Table p = panel();
        p.add(label("조작 키", "large", Ui.GOLD)).colspan(2).padBottom(16).row();
        for (String[] k : KEYS) {
            p.add(label(k[0], "default", Ui.DIM)).left().padRight(30).padBottom(8);
            p.add(label(k[1])).left().padBottom(8).row();
        }
        p.add(nav.add("닫기", this::close)).colspan(2).width(200).height(50).padTop(14);
    }
}
