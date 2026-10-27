package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;

/**
 * 확인창. 기본 선택은 "취소"이고 Esc도 취소다 (FR-대장간-11).
 * 내용 칸에 추가 정보(확률 막대 등)를 넣을 수 있다.
 */
public class ConfirmDialog extends GameWindow {

    private final Runnable onCancel;
    public final Table extra = new Table();

    public ConfirmDialog(Skin skin, String title, String message, String okText, boolean danger,
                         Runnable onOk, String cancelText, Runnable onCancel) {
        super(title, skin);
        this.onCancel = onCancel;
        Label msg = new Label(message, skin);
        msg.setWrap(true);
        msg.setAlignment(Align.center);
        add(msg).width(560).padBottom(12).row();
        add(extra).padBottom(16).row();
        Table row = new Table();
        TextButton ok = button(okText, danger ? "danger" : "default", () -> {
            close();
            onOk.run();
        });
        TextButton cancel = null;
        if (cancelText != null) {
            cancel = button(cancelText, () -> {
                close();
                onCancel.run();
            });
        }
        row.add(ok).width(220).height(52);
        if (cancel != null) row.add(cancel).width(220).height(52).padLeft(14);
        add(row);
        if (cancel != null) nav.focus(cancel);
        else nav.focus(ok);
        pack();
    }

    /** 버튼 위에 체크박스 한 줄을 넣는다 (키보드로도 고를 수 있다). */
    public CheckToggle check(String text) {
        CheckToggle c = new CheckToggle(text, skin);
        c.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                click.run();
            }
        });
        extra.add(c).width(460).height(40).padTop(12).row();
        nav.add(c);
        pack();
        return c;
    }

    @Override
    public void refresh() {
        pack();
    }

    /** 취소가 없는 창(사망 안내 등)은 Esc로 닫히지 않는다. */
    @Override
    public boolean onEscape() {
        if (onCancel == null) return false;
        close();
        onCancel.run();
        return true;
    }
}
