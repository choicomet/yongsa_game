package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;

import java.util.ArrayDeque;
import java.util.Deque;

/** 창 쌓기: 위에 뜬 창이 키 입력을 먼저 받고, 창이 하나라도 있으면 뒤를 어둡게 한다. */
public class WindowHost {

    private final Stage stage;
    private final Image dim;
    private final Deque<GameWindow> stack = new ArrayDeque<>();
    private Runnable click = () -> { };

    public WindowHost(Stage stage, Skin skin) {
        this.stage = stage;
        dim = new Image(skin.newDrawable("white", new Color(0, 0, 0, 0.5f)));
        dim.setFillParent(true);
    }

    public void setClickSound(Runnable r) {
        click = r;
    }

    public void open(GameWindow w) {
        if (stack.contains(w)) return;
        stack.push(w);
        w.setClickSound(click);
        w.setOnClose(() -> {
            stack.remove(w);
            if (stack.isEmpty()) dim.remove();
            else stack.peek().toFront();
        });
        stage.addActor(dim);
        dim.toFront();
        w.refresh();
        stage.addActor(w);
        w.pack();
        w.centerOnStage();
        w.toFront();
    }

    public boolean any() {
        stack.removeIf(w -> w.getStage() == null);
        if (stack.isEmpty()) dim.remove();
        return !stack.isEmpty();
    }

    public GameWindow top() {
        any();
        return stack.peek();
    }

    public boolean keyDown(int key) {
        GameWindow w = top();
        return w != null && w.keyDown(key);
    }

    public void closeAll() {
        while (any()) stack.peek().close();
    }
}
