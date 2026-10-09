package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.YongsaGame;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.save.SaveManager;
import com.yongsa.ui.ConfirmDialog;
import com.yongsa.ui.ControlsWindow;
import com.yongsa.ui.MessageWindow;
import com.yongsa.ui.Modal;
import com.yongsa.ui.SettingsWindow;
import com.yongsa.ui.Ui;

/** 타이틀 (UI-01): 이어하기, 새 게임, 설정, 조작 키, 종료. */
public class TitleScreen extends MenuScreen {

    public TitleScreen(YongsaGame game) {
        super(game);
        open(new TitleMenu());
    }

    private class TitleMenu extends Modal {
        TitleMenu() {
            super(game.skin);
        }

        @Override
        protected void onCancel() {
            // 타이틀에서 Esc 는 아무것도 하지 않는다
        }

        @Override
        public void build() {
            Table col = new Table();
            col.add(label("용사가 다한다", "title", Ui.GOLD)).padBottom(6).row();
            col.add(label("던전 한가운데 캠프에서, 용사가 다 한다", "default", Ui.DIM)).padBottom(40).row();
            boolean hasSave = game.saves.hasSave();
            TextButton cont = nav.add("이어하기", TitleScreen.this::continueGame);
            cont.setDisabled(!hasSave);
            col.add(cont).width(320).height(54).padBottom(10).row();
            col.add(nav.add("새 게임", TitleScreen.this::newGame)).width(320).height(54).padBottom(10).row();
            col.add(nav.add("설정", () -> modals.push(new SettingsWindow(game)))).width(320).height(54).padBottom(10).row();
            col.add(nav.add("조작 키", () -> modals.push(new ControlsWindow(game.skin)))).width(320).height(54).padBottom(10).row();
            col.add(nav.add("종료", () -> Gdx.app.exit())).width(320).height(54).row();
            add(col).expand().center().padBottom(80);
            nav.setFocusIndex(hasSave ? 0 : 1);
        }
    }

    private void continueGame() {
        SaveManager.LoadResult r = game.saves.load();
        if (r.state == null) {
            modals.push(new MessageWindow(game.skin, "저장을 읽지 못했습니다", Ui.RED, new ConfirmDialog.Line[]{
                    new ConfirmDialog.Line("저장 파일이 깨졌습니다. 새 게임을 시작해 주세요.", null)}, "확인"));
            return;
        }
        start(r.state, false);
    }

    private void newGame() {
        if (game.saves.hasSave()) {
            modals.push(new ConfirmDialog(game.skin, "새 게임", new ConfirmDialog.Line[]{
                    new ConfirmDialog.Line("저장된 게임이 있습니다.", null),
                    new ConfirmDialog.Line("새 게임을 시작하면 지금 저장은 지워집니다.", Ui.RED)},
                    "새로 시작", true, () -> start(NewGame.create(game.balance), true)));
        } else {
            start(NewGame.create(game.balance), true);
        }
    }

    private void start(GameState s, boolean intro) {
        if (intro) {
            game.save(s);
            game.setScreen(new IntroScreen(game, s));
        } else {
            game.setScreen(new GameScreen(game, s));
        }
        dispose();
    }
}
