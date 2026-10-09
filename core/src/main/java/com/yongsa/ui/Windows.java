package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Align;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 창 쌓기. 열린 창이 하나라도 있으면 게임 시간이 멈춘다(GameScreen 참고).
 * Esc 는 가장 위의 창만 닫는다.
 */
public class Windows {
    public static class Modal {
        public final Table root;
        public final Table content;
        public boolean closable = true;
        public Runnable onClose;
        public String tag = "";

        Modal(Table root, Table content) {
            this.root = root;
            this.content = content;
        }
    }

    public final Stage stage;
    public final Skin skin;
    private final Deque<Modal> stack = new ArrayDeque<>();
    /** 창이 닫힐 때 호출 (입력 비우기 등). */
    public Runnable onAnyClosed;
    public Runnable onClick;

    public Windows(Stage stage, Skin skin) {
        this.stage = stage;
        this.skin = skin;
    }

    public Modal push(Table content, String tag) {
        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.enabled);
        root.setBackground(skin.getDrawable("dim"));
        root.add(content);
        stage.addActor(root);
        content.getColor().a = 0;
        content.addAction(Actions.fadeIn(0.12f));
        Modal m = new Modal(root, content);
        m.tag = tag;
        stack.push(m);
        return m;
    }

    public boolean anyOpen() {
        return !stack.isEmpty();
    }

    public Modal top() {
        return stack.peek();
    }

    public boolean isOpen(String tag) {
        for (Modal m : stack) if (m.tag.equals(tag)) return true;
        return false;
    }

    public void close(Modal m) {
        if (m == null || !stack.contains(m)) return;
        stack.remove(m);
        m.root.remove();
        if (m.onClose != null) m.onClose.run();
        if (onAnyClosed != null) onAnyClosed.run();
    }

    public void closeAll() {
        while (!stack.isEmpty()) close(stack.peek());
    }

    /** Esc 처리. 닫을 창이 있었으면 true. */
    public boolean escape() {
        Modal m = stack.peek();
        if (m == null) return false;
        if (m.closable) close(m);
        return true;
    }

    // ---- 만들기 도우미 ----

    public Table panel() {
        Table t = new Table(skin);
        t.setBackground(skin.getDrawable("panel"));
        t.pad(14, 18, 14, 18);
        return t;
    }

    public TextButton button(String text, Runnable action) {
        return button(text, "default", action);
    }

    public TextButton button(String text, String style, Runnable action) {
        TextButton b = new TextButton(text, skin, style);
        b.setProgrammaticChangeEvents(false); // setChecked 로 상태만 바꿀 때 동작이 실행되지 않게
        b.getLabelCell().pad(4, 12, 4, 12);
        b.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (b.isDisabled()) return;
                if (onClick != null) onClick.run();
                action.run();
            }
        });
        return b;
    }

    public Label label(String text, String style) {
        Label l = new Label(text, skin, style);
        l.setAlignment(Align.left);
        return l;
    }

    public Label wrap(String text, String style, float width) {
        Label l = new Label(text, skin, style);
        l.setWrap(true);
        l.setWidth(width);
        return l;
    }

    /** 확인 창. confirm 은 확인 버튼을 눌렀을 때만 실행된다. */
    public Modal confirm(String title, String message, String ok, Runnable confirm) {
        Table p = panel();
        p.add(label(title, "large")).left().row();
        p.add(wrap(message, "default", 380)).width(380).padTop(10).padBottom(14).row();
        Table buttons = new Table();
        Modal[] holder = new Modal[1];
        buttons.add(button("취소", () -> close(holder[0]))).padRight(10);
        buttons.add(button(ok, () -> {
            close(holder[0]);
            confirm.run();
        }));
        p.add(buttons).right();
        holder[0] = push(p, "confirm");
        return holder[0];
    }

    public Modal message(String title, String message) {
        Table p = panel();
        p.add(label(title, "large")).left().row();
        p.add(wrap(message, "default", 380)).width(380).padTop(10).padBottom(14).row();
        Modal[] holder = new Modal[1];
        p.add(button("확인", () -> close(holder[0]))).right();
        holder[0] = push(p, "message");
        return holder[0];
    }
}
