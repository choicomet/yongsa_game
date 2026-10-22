package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

/**
 * 게임 화면 위에 뜨는 창의 공통 부분. 창이 열려 있으면 게임 시간이 멈추고 맵 조작은 막힌다 (명세서 4.3).
 * 키 입력은 GameScreen이 먼저 받아 keyDown으로 넘긴다 (모달 창이 키보드를 가져가는 문제 때문).
 */
public abstract class GameWindow extends Window {

    protected final Skin skin;
    protected final KeyNav nav;
    private Runnable onClose = () -> { };
    /** 버튼 소리 등. */
    protected Runnable click = () -> { };

    protected GameWindow(String title, Skin skin) {
        super(title, skin);
        this.skin = skin;
        this.nav = new KeyNav(skin);
        setModal(true);
        setMovable(false);
        getTitleTable().padLeft(10).padTop(6);
        pad(52, 20, 14, 20);
    }

    public void setOnClose(Runnable r) {
        onClose = r;
    }

    public void setClickSound(Runnable r) {
        click = r;
    }

    /** 창이 열릴 때마다 내용을 새로 채운다. */
    public abstract void refresh();

    public void close() {
        nav.clear();
        remove();
        onClose.run();
    }

    /** Esc. 닫혔으면 true. */
    public boolean onEscape() {
        close();
        return true;
    }

    /** 창이 처리한 키면 true. */
    public boolean keyDown(int key) {
        if (key == Input.Keys.ESCAPE) return onEscape();
        return nav.keyDown(key);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        nav.update(this);
    }

    protected TextButton button(String text, Runnable action) {
        return button(text, "default", action);
    }

    protected TextButton button(String text, String style, Runnable action) {
        TextButton b = new TextButton(text, skin, style);
        // 코드에서 setChecked해도 클릭으로 치지 않게 (목록을 다시 만들 때 무한 반복 방지)
        b.setProgrammaticChangeEvents(false);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                click.run();
                action.run();
            }
        });
        return nav.add(b);
    }

    public void centerOnStage() {
        if (getStage() == null) return;
        setPosition(Math.round((getStage().getWidth() - getWidth()) / 2f), Math.round((getStage().getHeight() - getHeight()) / 2f));
    }
}
