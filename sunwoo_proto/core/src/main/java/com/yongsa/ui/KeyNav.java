package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 키보드로 창 안 버튼 이동·선택 (명세서 4.2, NFR-09): 방향키·Tab으로 옮기고 Enter·Space로 누른다.
 * 지금 고른 버튼 위에 금색 테두리를 그린다. 마우스로도 그대로 누를 수 있다.
 */
public class KeyNav {

    private final List<Button> buttons = new ArrayList<>();
    private final Image frame;
    private int index = -1;

    public KeyNav(Skin skin) {
        frame = new Image(skin.getDrawable("focus"));
        frame.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
    }

    public void clear() {
        buttons.clear();
        index = -1;
        frame.remove();
    }

    public <T extends Button> T add(T b) {
        buttons.add(b);
        return b;
    }

    /** 처음에 고를 버튼 (예: 확인창의 "취소"). */
    public void focus(Button b) {
        index = buttons.indexOf(b);
    }

    public void focusFirstEnabled() {
        index = -1;
        for (int i = 0; i < buttons.size(); i++) {
            if (!buttons.get(i).isDisabled()) {
                index = i;
                return;
            }
        }
    }

    /** 처리한 키면 true. */
    public boolean keyDown(int key) {
        if (buttons.isEmpty()) return false;
        switch (key) {
            case Input.Keys.DOWN, Input.Keys.RIGHT, Input.Keys.TAB -> {
                move(1);
                return true;
            }
            case Input.Keys.UP, Input.Keys.LEFT -> {
                move(-1);
                return true;
            }
            case Input.Keys.ENTER, Input.Keys.NUMPAD_ENTER, Input.Keys.SPACE -> {
                if (index < 0) focusFirstEnabled();
                if (index >= 0) press(buttons.get(index));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void move(int d) {
        int n = buttons.size();
        int i = index < 0 ? (d > 0 ? -1 : n) : index;
        for (int k = 0; k < n; k++) {
            i = (i + d + n) % n;
            Button b = buttons.get(i);
            if (!b.isDisabled() && b.getStage() != null && b.isVisible()) {
                index = i;
                return;
            }
        }
    }

    private static void press(Button b) {
        if (b.isDisabled()) return;
        ChangeListener.ChangeEvent e = new ChangeListener.ChangeEvent();
        b.fire(e);
    }

    /** 매 프레임: 테두리를 고른 버튼 위치로. */
    public void update(Group layer) {
        if (index < 0 || index >= buttons.size() || buttons.get(index).getStage() == null) {
            frame.remove();
            return;
        }
        Actor b = buttons.get(index);
        Vector2 p = b.localToStageCoordinates(new Vector2(0, 0));
        Vector2 q = layer.stageToLocalCoordinates(p);
        frame.setBounds(q.x - 3, q.y - 3, b.getWidth() + 6, b.getHeight() + 6);
        if (frame.getParent() != layer) layer.addActor(frame);
        frame.toFront();
    }
}
