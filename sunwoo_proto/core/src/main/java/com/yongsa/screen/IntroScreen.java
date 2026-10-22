package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.yongsa.YongsaGame;

/** 시작 소개 (UI-02, FR-공통-01): 짧은 글 4장, 아무 키나 다음, Esc 건너뛰기. */
public class IntroScreen extends BaseScreen {

    private static final String[] PAGES = {
            "던전 한가운데, 작은 캠프에 용사가 홀로 남았다.",
            "대장장이도, 광부도, 함께 싸울 동료도 없다.\n그래서 용사가 다 한다. 직접 캐고, 직접 벼리고, 직접 싸운다.",
            "용사는 쓰러뜨린 상대의 힘을 흡수할 수 있다.\n그리고 가보로 물려받은, 봉인된 성검을 지니고 있다.",
            "세 던전의 주인을 쓰러뜨려 그 힘을 모으면\n성검의 봉인이 풀리고, 마왕에게 닿을 수 있으리라.",
    };

    private final Label text, page;
    private int index = -1;

    public IntroScreen(YongsaGame game) {
        super(game);
        Table root = new Table();
        root.setFillParent(true);
        text = new Label("", game.skin, "large");
        text.setAlignment(Align.center);
        text.setWrap(true);
        page = new Label("", game.skin, "small");
        root.add(text).width(960).row();
        root.add(page).padTop(60).row();
        root.add(new Label("아무 키나 누르면 다음   ·   Esc 건너뛰기", game.skin, "small")).padTop(10);
        stage.addActor(root);
        next();
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.ESCAPE) index = PAGES.length;
                next();
                return true;
            }

            @Override
            public boolean touchDown(int x, int y, int p, int b) {
                next();
                return true;
            }
        });
    }

    private void next() {
        index++;
        if (index >= PAGES.length) {
            game.startGame(game.createSession());
            index = Integer.MIN_VALUE / 2;
            return;
        }
        if (index < 0) return;
        text.setText(PAGES[index]);
        page.setText((index + 1) + " / " + PAGES.length);
        text.clearActions();
        text.getColor().a = 0;
        text.addAction(Actions.fadeIn(0.5f));
    }
}
