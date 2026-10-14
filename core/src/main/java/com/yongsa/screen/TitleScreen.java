package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.yongsa.YongsaGame;
import com.yongsa.ui.ConfirmDialog;
import com.yongsa.ui.KeyNav;
import com.yongsa.ui.SettingsWindow;

/** 타이틀 (UI-01): 이어하기, 새 게임, 설정, 종료. 키보드로도 고른다. */
public class TitleScreen extends BaseScreen {

    private final KeyNav nav;
    private final Table root = new Table();

    public TitleScreen(YongsaGame game) {
        super(game);
        nav = new KeyNav(game.skin);
        root.setFillParent(true);
        stage.addActor(root);

        Table art = new Table();
        art.add(new Image(game.assets.region("hero_down"))).size(96).padRight(10);
        art.add(new Image(game.assets.region("weapon_holy"))).size(80);
        root.add(art).padBottom(8).row();
        root.add(new Label("용사가 다한다", game.skin, "title")).padBottom(6).row();
        root.add(new Label("캐고, 벼리고, 싸우는 것까지 전부 혼자서", game.skin, "dim")).padBottom(44).row();

        TextButton cont = button("이어하기", game::continueGame);
        cont.setDisabled(!game.saves.hasSave());
        root.defaults().width(300).height(56).padBottom(10);
        root.add(cont).row();
        root.add(button("새 게임", this::newGame)).row();
        root.add(button("설정", () -> windows.open(new SettingsWindow(game.skin, game.settings, game.sounds::applyVolume)))).row();
        root.add(button("종료", () -> Gdx.app.exit())).row();
        root.add(new Label("방향키 + Enter 또는 마우스", game.skin, "small")).padTop(8);
        nav.focusFirstEnabled();

        root.getColor().a = 0;
        root.addAction(Actions.fadeIn(0.6f));
        game.sounds.playMusic("bgm_title");
    }

    /** 저장을 읽지 못했을 때 (FR-저장-05). */
    public void showBrokenSave() {
        windows.open(new ConfirmDialog(game.skin, "알림", "저장을 읽지 못했습니다.\n새 게임을 시작할까요?", "새 게임", false,
                game::startNewGame, "취소", () -> { }));
    }

    private void newGame() {
        if (!game.saves.hasSave()) {
            game.startNewGame();
            return;
        }
        // 저장이 있는데 새 게임 → 확인창 (FR-저장-04)
        windows.open(new ConfirmDialog(game.skin, "새 게임", "저장된 게임이 있다. 지우고 새로 시작할까?", "새로 시작", true,
                game::startNewGame, "취소", () -> { }));
    }

    private TextButton button(String text, Runnable action) {
        TextButton b = new TextButton(text, game.skin);
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                game.sounds.play("ui_click");
                action.run();
            }
        });
        return nav.add(b);
    }

    @Override
    public void render(float delta) {
        nav.update(root);
        super.render(delta);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputMultiplexer(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (windows.any()) return windows.keyDown(keycode);
                return nav.keyDown(keycode);
            }
        }, stage));
    }
}
