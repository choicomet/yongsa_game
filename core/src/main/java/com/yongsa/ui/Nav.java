package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 창 안의 버튼을 키보드로 고르게 한다: 방향키·Tab 으로 이동, Enter·Space 로 누름 (NFR-09).
 * 마우스로 눌러도 같은 동작을 한다.
 */
public class Nav {
    private static class Entry {
        final TextButton button;
        final Runnable action;
        final boolean danger;

        Entry(TextButton button, Runnable action, boolean danger) {
            this.button = button;
            this.action = action;
            this.danger = danger;
        }
    }

    private final Skin skin;
    private final List<Entry> entries = new ArrayList<>();
    private int focus;
    public Runnable onMove;

    public Nav(Skin skin) {
        this.skin = skin;
    }

    public TextButton add(String text, Runnable action) {
        return add(text, action, false);
    }

    public TextButton add(String text, Runnable action, boolean danger) {
        TextButton b = new TextButton(text, skin, danger ? "danger" : "default");
        Entry e = new Entry(b, action, danger);
        entries.add(e);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (b.isDisabled()) return;
                focus = entries.indexOf(e);
                refresh();
                e.action.run();
            }
        });
        refresh();
        return b;
    }

    public void clear() {
        entries.clear();
        focus = 0;
    }

    public void setFocus(TextButton b) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).button == b) focus = i;
        refresh();
    }

    /** from 번째부터 눌 수 있는 첫 버튼 번호 (없으면 from). */
    public int firstEnabledFrom(int from) {
        for (int i = from; i < entries.size(); i++) if (!entries.get(i).button.isDisabled()) return i;
        return Math.min(from, entries.size() - 1);
    }

    public void setFocusIndex(int i) {
        focus = Math.max(0, Math.min(entries.size() - 1, i));
        refresh();
    }

    public int focusIndex() {
        return focus;
    }

    public void refresh() {
        if (entries.isEmpty()) return;
        focus = Math.max(0, Math.min(focus, entries.size() - 1));
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            boolean f = i == focus;
            String style = e.danger ? (f ? "dangerFocused" : "danger") : (f ? "focused" : "default");
            e.button.setStyle(skin.get(style, TextButton.TextButtonStyle.class));
        }
    }

    private void move(int d) {
        if (entries.isEmpty()) return;
        int n = entries.size();
        for (int k = 0; k < n; k++) {
            focus = (focus + d + n) % n;
            if (!entries.get(focus).button.isDisabled()) break;
        }
        refresh();
        if (onMove != null) onMove.run();
    }

    /** 처리했으면 true. */
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:
            case Input.Keys.LEFT:
            case Input.Keys.W:
            case Input.Keys.A:
                move(-1);
                return true;
            case Input.Keys.DOWN:
            case Input.Keys.RIGHT:
            case Input.Keys.S:
            case Input.Keys.D:
                move(1);
                return true;
            case Input.Keys.TAB:
                boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
                move(shift ? -1 : 1);
                return true;
            case Input.Keys.ENTER:
            case Input.Keys.NUMPAD_ENTER:
            case Input.Keys.SPACE:
                if (entries.isEmpty()) return true;
                Entry e = entries.get(focus);
                if (!e.button.isDisabled()) e.action.run();
                return true;
            default:
                return false;
        }
    }
}
