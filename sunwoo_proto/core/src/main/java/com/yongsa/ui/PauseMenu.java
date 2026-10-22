package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.model.GameSession;

/** Esc 메뉴 (UI-09): 계속하기, 가방, 저장, 설정, 조작 키, (던전 나가기), 타이틀로. */
public class PauseMenu extends GameWindow {

    public interface Actions {
        void bag();

        void save();

        void settings();

        void controls();

        void exitDungeon();

        void title();
    }

    private final GameSession s;
    private final Actions actions;

    public PauseMenu(Skin skin, GameSession s, Actions actions) {
        super("메뉴", skin);
        this.s = s;
        this.actions = actions;
    }

    @Override
    public void refresh() {
        clearChildren();
        nav.clear();
        defaults().width(340).height(52).padBottom(8);
        add(button("계속하기", this::close)).row();
        add(button("가방", actions::bag)).row();
        TextButton save = button(s.canSaveNow() ? "저장" : "저장 (지금은 불가)", actions::save);
        save.setDisabled(!s.canSaveNow());
        add(save).row();
        add(button("설정", actions::settings)).row();
        add(button("조작 키 보기", actions::controls)).row();
        if (!s.inCamp() && !s.inArena()) {
            TextButton exit = button(s.canExitDungeon() ? "던전 나가기" : "던전 나가기 (전투 중 불가)", actions::exitDungeon);
            exit.setDisabled(!s.canExitDungeon());
            add(exit).row();
        }
        add(button("타이틀로", actions::title)).row();
        nav.focusFirstEnabled();
        pack();
        centerOnStage();
    }
}
