package com.yongsa.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;
import com.yongsa.YongsaGame;
import com.yongsa.model.GameSession;
import com.yongsa.ui.KeyNav;

/** 엔딩 (UI-11): 마무리 글, 플레이 기록, 계속하기(FR-던전-10) / 타이틀로. */
public class EndingScreen extends BaseScreen {

    private final KeyNav nav;
    private final Table root = new Table();

    public EndingScreen(YongsaGame game, GameSession s) {
        super(game);
        nav = new KeyNav(game.skin);
        root.setFillParent(true);
        stage.addActor(root);

        Label title = new Label("마왕이 쓰러졌다", game.skin, "title");
        Label body = new Label("깨어난 성검의 빛이 심연을 갈랐다.\n던전을 감싸던 어둠이 걷히고, 캠프의 불씨는 다시 따뜻해졌다.\n"
                + "이제 용사가 다 하지 않아도 되는 날이 올지도 모른다.", game.skin, "large");
        body.setAlignment(Align.center);
        int min = (int) (s.playTime() / 60), sec = (int) (s.playTime() % 60);
        Table stats = new Table(game.skin);
        stats.setBackground(game.skin.getDrawable("panel-light"));
        stat(stats, "플레이 시간", min + "분 " + sec + "초");
        stat(stats, "강화 횟수", s.progress.enhanceCount + "번");
        stat(stats, "파괴 횟수 (용사의 실수)", s.progress.destroyCount + "번");
        stat(stats, "각성한 무기", s.progress.awakenCount + "자루");
        stat(stats, "쓰러뜨린 몬스터", s.progress.kills + "마리");
        stat(stats, "쓰러진 횟수", s.progress.deaths + "번");

        root.add(title).padBottom(24).row();
        root.add(body).padBottom(28).row();
        root.add(stats).padBottom(28).row();
        Table buttons = new Table();
        buttons.add(button("계속하기", () -> game.resumeAfterEnding(s))).width(240).height(54).padRight(14);
        buttons.add(button("타이틀로", game::showTitle)).width(240).height(54);
        root.add(buttons);
        nav.focusFirstEnabled();
        root.getColor().a = 0;
        root.addAction(Actions.fadeIn(1.5f));
        game.sounds.playMusic("bgm_ending");
    }

    private void stat(Table t, String name, String value) {
        t.add(new Label(name, game.skin, "dim")).width(300).left();
        t.add(new Label(value, game.skin, "large")).width(160).right().row();
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
                return nav.keyDown(keycode);
            }
        }, stage));
    }
}
