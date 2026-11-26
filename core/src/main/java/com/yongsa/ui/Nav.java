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
        /** 지금 고른 탭처럼 “선택됨”을 따로 보여 줄 버튼 (포커스와 다른 모양). */
        boolean selected;
        /** rows 를 켠 창에서 이 버튼이 속한 줄 (newRow 로 넘긴다). */
        int row;

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
    /** 버튼 모양 이름. null 이면 "default"/"focused", 값이 있으면 그 이름과 이름+"Focused" (타이틀 메뉴용). */
    public String style;
    /** 버튼이 표처럼 한 줄에 columns 개씩 놓인 창 (조작 키). 1보다 크면 위/아래는 한 줄씩, 왼쪽/오른쪽은 한 칸씩 움직인다. */
    public int columns = 1;
    /**
     * 줄마다 버튼 수가 다른 창 (개발용 메뉴). 켜면 위/아래는 윗줄·아랫줄로, 왼쪽/오른쪽은 같은 줄 안에서만 움직인다.
     * 줄은 버튼을 더하기 전에 newRow() 로 나눈다.
     */
    public boolean rows;
    private int rowCounter;

    /** rows 를 켠 창에서 다음에 더하는 버튼부터 새 줄. */
    public void newRow() {
        rowCounter++;
    }

    public Nav(Skin skin) {
        this.skin = skin;
    }

    public TextButton add(String text, Runnable action) {
        return add(text, action, false);
    }

    public TextButton add(String text, Runnable action, boolean danger) {
        TextButton b = new TextButton(text, skin, danger ? "danger" : "default");
        Entry e = new Entry(b, action, danger);
        e.row = rowCounter;
        entries.add(e);
        b.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (b.isDisabled()) return;
                focus = entries.indexOf(e);
                refresh();
                e.action.run();
            }

            @Override
            public void enter(InputEvent event, float x, float y, int pointer, com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                // 마우스를 올린 버튼으로 키보드 포커스도 옮긴다: 강조된 버튼이 둘로 보이지 않게
                if (pointer != -1 || b.isDisabled()) return;
                int i = entries.indexOf(e);
                if (i >= 0 && i != focus) {
                    focus = i;
                    refresh();
                }
            }
        });
        refresh();
        return b;
    }

    public void clear() {
        entries.clear();
        focus = 0;
        rowCounter = 0;
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

    /** 이 버튼을 “선택됨”(예: 지금 탭)으로 표시한다. 포커스가 없을 때 tabOn 모양이 된다. */
    public void markSelected(TextButton b) {
        for (Entry e : entries) if (e.button == b) e.selected = true;
        refresh();
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
            String name = e.danger ? (f ? "dangerFocused" : "danger")
                    : style != null ? (f ? style + "Focused" : style) : (f ? "focused" : e.selected ? "tabOn" : "default");
            e.button.setStyle(skin.get(name, TextButton.TextButtonStyle.class));
            if (f) keepVisible(e.button);
        }
    }

    /** Keyboard focus remains visible in inventory and equipment scroll lists. */
    private void keepVisible(TextButton button) {
        com.badlogic.gdx.scenes.scene2d.Actor parent = button.getParent();
        while (parent != null) {
            if (parent instanceof com.badlogic.gdx.scenes.scene2d.ui.ScrollPane scroll) {
                scroll.validate();
                com.badlogic.gdx.math.Vector2 p = button.localToAscendantCoordinates(scroll.getActor(), new com.badlogic.gdx.math.Vector2());
                scroll.scrollTo(p.x, p.y, button.getWidth(), button.getHeight());
                scroll.updateVisualScroll();
            }
            parent = parent.getParent();
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

    /** 같은 줄에 있는 버튼 번호들 (entries 순서). */
    private List<Integer> rowOf(int row) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).row == row) out.add(i);
        return out;
    }

    /** rows 창의 위/아래: 버튼이 있는 윗줄·아랫줄로 간다 (끝을 넘으면 반대쪽 끝 줄). 같은 칸 순서에 가장 가까운 버튼을 고른다. */
    private void moveBetweenRows(int d) {
        if (entries.isEmpty()) return;
        Entry cur = entries.get(focus);
        List<Integer> here = rowOf(cur.row);
        int col = here.indexOf(focus);
        int minRow = entries.get(0).row, maxRow = entries.get(entries.size() - 1).row;
        int span = maxRow - minRow + 1;
        for (int k = 1; k <= span; k++) {
            int r = minRow + Math.floorMod(cur.row - minRow + d * k, span);
            List<Integer> line = rowOf(r);
            int best = -1, bestGap = Integer.MAX_VALUE;
            for (int c = 0; c < line.size(); c++) {
                if (entries.get(line.get(c)).button.isDisabled()) continue;
                // 화면에 놓였으면 가로 위치가 가장 가까운 버튼, 아니면 같은 칸 순서에 가까운 버튼
                float cx = centerX(cur.button), tx = centerX(entries.get(line.get(c)).button);
                int gap = !Float.isNaN(cx) && !Float.isNaN(tx) ? Math.round(Math.abs(tx - cx))
                        : Math.abs(c - Math.min(col, line.size() - 1));
                if (gap < bestGap) {
                    bestGap = gap;
                    best = line.get(c);
                }
            }
            if (best >= 0) {
                focus = best;
                break;
            }
        }
        refresh();
        if (onMove != null) onMove.run();
    }

    /** 버튼 가운데의 화면 x (아직 화면에 안 놓였으면 NaN). */
    private static float centerX(TextButton b) {
        if (b.getStage() == null) return Float.NaN;
        return b.localToStageCoordinates(new com.badlogic.gdx.math.Vector2(b.getWidth() / 2f, 0)).x;
    }

    /** rows 창의 왼쪽/오른쪽: 같은 줄 안에서만 돈다. */
    private void moveInRow(int d) {
        if (entries.isEmpty()) return;
        List<Integer> line = rowOf(entries.get(focus).row);
        int n = line.size(), at = line.indexOf(focus);
        for (int k = 1; k < n; k++) {
            int i = line.get(Math.floorMod(at + d * k, n));
            if (!entries.get(i).button.isDisabled()) {
                focus = i;
                break;
            }
        }
        refresh();
        if (onMove != null) onMove.run();
    }

    /** 표 모양 창에서 위/아래: 한 줄(columns 칸) 건너뛴다. 끝을 넘으면 맨 처음/맨 끝 버튼. */
    private void moveRow(int d) {
        if (rows) {
            moveBetweenRows(d);
            return;
        }
        if (columns <= 1) {
            move(d);
            return;
        }
        if (entries.isEmpty()) return;
        int target = Math.max(0, Math.min(entries.size() - 1, focus + d * columns));
        if (entries.get(target).button.isDisabled()) {
            move(d);
            return;
        }
        focus = target;
        refresh();
        if (onMove != null) onMove.run();
    }

    /** 처리했으면 true. */
    public boolean keyDown(int keycode) {
        switch (keycode) {
            case Input.Keys.UP:
            case Input.Keys.W:
                moveRow(-1);
                return true;
            case Input.Keys.LEFT:
            case Input.Keys.A:
                if (rows) moveInRow(-1);
                else move(-1);
                return true;
            case Input.Keys.DOWN:
            case Input.Keys.S:
                moveRow(1);
                return true;
            case Input.Keys.RIGHT:
            case Input.Keys.D:
                if (rows) moveInRow(1);
                else move(1);
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
