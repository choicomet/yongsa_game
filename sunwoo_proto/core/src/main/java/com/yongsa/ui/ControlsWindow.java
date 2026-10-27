package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.KeyBindings;
import com.yongsa.KeyBindings.Action;
import com.yongsa.Settings;

/**
 * 조작 키 보기와 바꾸기 (명세서 4.2 + 2026-10-27 김선우 결정).
 * 동작마다 키 칸이 세 개다. 칸을 고르고 Enter(또는 클릭) → 바꿀 키를 누르면 그 키로 바뀐다.
 * 다른 동작이 쓰던 키면 그쪽에서 빠진다고 알려 준다. 기다리는 동안 Esc 는 취소, Backspace·Delete 는 칸 비우기.
 * Esc(메뉴), F1(개발자 도구), Enter(창 선택)는 바꿀 수 없다. 바꾸면 바로 저장된다.
 */
public class ControlsWindow extends GameWindow {

    /** 바꿀 수 없는 키 안내. */
    private static final String[][] FIXED = {
            {"메뉴 · 창 닫기 · 취소", "Esc"},
            {"창 안 버튼 이동 · 선택", "방향키 / Tab, Enter (마우스도 됨)"},
            {"개발자 도구", "F1"},
    };

    private final Settings settings;
    /** 키를 바꾼 뒤 할 일 (게임 화면의 안내 갱신). 없으면 null. */
    private final Runnable onChanged;
    private Action waitingAction;
    private int waitingSlot;
    private String notice;
    private boolean noticeWarn;
    private ScrollPane scroll;
    private float scrollY = -1;

    public ControlsWindow(Skin skin, Settings settings, Runnable onChanged) {
        super("조작 키", skin);
        this.settings = settings;
        this.onChanged = onChanged;
    }

    private KeyBindings keys() {
        return settings.keys();
    }

    @Override
    public void refresh() {
        int focus = nav.index();
        clearChildren();
        nav.clear();
        add(new Label("칸을 고르고 Enter(또는 클릭)한 뒤 바꿀 키를 누르세요. 한 동작에 키를 세 개까지 둘 수 있습니다.", skin, "small"))
                .left().padBottom(8).row();

        Table rows = new Table();
        rows.top().left();
        rows.add(new Label("동작", skin, "small")).width(360).left();
        for (int i = 0; i < KeyBindings.SLOTS; i++) rows.add(new Label("키 " + (i + 1), skin, "small")).width(150).left();
        rows.add().width(80).row();
        for (Action a : Action.values()) {
            Label title = new Label(a.title, skin);
            if (keys().unbound(a)) title.setColor(UiSkin.SHORT);
            rows.add(title).width(360).left().padBottom(5);
            for (int i = 0; i < KeyBindings.SLOTS; i++) {
                int slot = i;
                boolean waiting = waitingAction == a && waitingSlot == i;
                int k = keys().key(a, i);
                TextButton b = button(waiting ? "키를 누르세요…" : k == KeyBindings.NONE ? "·" : KeyBindings.keyName(k), () -> startWaiting(a, slot));
                if (waiting) b.getLabel().setColor(UiSkin.GOLD);
                else if (k == KeyBindings.NONE) b.getLabel().setColor(UiSkin.DIM);
                rows.add(b).width(142).height(38).left().padRight(8).padBottom(5);
            }
            rows.add(button("기본", () -> {
                keys().reset(a);
                changed("「" + a.title + "」을(를) 기본 키(" + keys().names(a) + ")로 되돌렸습니다.", false);
            })).width(76).height(38).left().padBottom(5).row();
        }
        rows.add(new Label("바꿀 수 없는 키", skin, "gold")).colspan(KeyBindings.SLOTS + 2).left().padTop(12).padBottom(4).row();
        for (String[] f : FIXED) {
            rows.add(new Label(f[0], skin, "small")).width(360).left().padBottom(4);
            rows.add(new Label(f[1], skin, "small-white")).colspan(KeyBindings.SLOTS + 1).left().padBottom(4).row();
        }
        scroll = new ScrollPane(rows, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        add(scroll).width(930).height(400).row();
        if (scrollY >= 0) {
            scroll.layout();
            scroll.setScrollY(scrollY);
            scroll.updateVisualScroll();
        }

        String text = notice != null ? notice
                : waitingAction != null ? "「" + waitingAction.title + "」 키 " + (waitingSlot + 1)
                + " — 바꿀 키를 누르세요.   Esc 취소 · Backspace/Delete 칸 비우기"
                : "Esc 닫기 · 방향키/Tab 이동 · Enter 선택";
        Label info = new Label(text, skin, "small");
        info.setWrap(true);
        if (noticeWarn) info.setColor(UiSkin.SHORT);
        else if (waitingAction != null || notice != null) info.setColor(UiSkin.GOLD);
        add(info).width(930).padTop(8).row();

        Table foot = new Table();
        foot.add(button("모두 기본값으로", () -> {
            keys().resetAll();
            changed("모든 조작 키를 기본값으로 되돌렸습니다.", false);
        })).width(240).height(48).padRight(12);
        foot.add(button("닫기 (Esc)", this::close)).width(200).height(48);
        add(foot).padTop(8);
        pack();
        centerOnStage();
        if (focus >= 0) nav.focusIndex(focus);
    }

    private void startWaiting(Action a, int slot) {
        waitingAction = a;
        waitingSlot = slot;
        notice = null;
        noticeWarn = false;
        rebuild();
    }

    @Override
    public boolean keyDown(int key) {
        if (waitingAction == null) {
            notice = null;
            return super.keyDown(key);
        }
        Action a = waitingAction;
        int slot = waitingSlot;
        waitingAction = null;
        if (key == Input.Keys.ESCAPE) {
            notice = "바꾸지 않았습니다.";
            noticeWarn = false;
            rebuild();
            return true;
        }
        if (key == Input.Keys.BACKSPACE || key == Input.Keys.FORWARD_DEL) {
            keys().clear(a, slot);
            changed("「" + a.title + "」 키 " + (slot + 1) + " 칸을 비웠습니다."
                    + (keys().unbound(a) ? " 이 동작은 이제 키가 없습니다!" : ""), keys().unbound(a));
            return true;
        }
        if (KeyBindings.isReserved(key)) {
            notice = KeyBindings.keyName(key) + " 키는 바꿀 수 없습니다. 다른 키를 골라 주세요.";
            noticeWarn = true;
            rebuild();
            return true;
        }
        Action previous = keys().bind(a, slot, key);
        String msg = "「" + a.title + "」 키 " + (slot + 1) + " → " + KeyBindings.keyName(key);
        boolean warn = false;
        if (previous != null) {
            msg += ".  「" + previous.title + "」에서 쓰던 키라 그쪽에서는 뺐습니다.";
            if (keys().unbound(previous)) {
                msg += " 「" + previous.title + "」은(는) 이제 키가 없습니다!";
                warn = true;
            }
        }
        changed(msg, warn);
        return true;
    }

    private void changed(String message, boolean warn) {
        notice = message;
        noticeWarn = warn;
        settings.saveKeys();
        if (onChanged != null) onChanged.run();
        rebuild();
    }

    private void rebuild() {
        scrollY = scroll != null ? scroll.getScrollY() : -1;
        refresh();
    }
}
