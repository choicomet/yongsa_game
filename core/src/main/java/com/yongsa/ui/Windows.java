package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.yongsa.gfx.Textures;
import com.yongsa.gfx.Fonts;
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
    private Actor baseRoot;
    private final java.util.Map<Actor, Actor> selections = new java.util.IdentityHashMap<>();
    private final java.util.Map<Actor, Integer> selectionIndices = new java.util.IdentityHashMap<>();

    public void setBaseRoot(Actor root) { baseRoot = root; }

    private Actor scope() { return anyOpen() ? top().content : baseRoot; }

    private void controls(Actor a, java.util.List<Actor> out) {
        if (a == null || !a.isVisible()) return;
        if (a instanceof Button) {
            if (!((Button) a).isDisabled()) out.add(a);
        } else if (a instanceof Slider) {
            if (!((Slider) a).isDisabled()) out.add(a);
        } else if (a instanceof Group) {
            for (Actor child : ((Group) a).getChildren()) controls(child, out);
        }
    }

    private Actor selected(java.util.List<Actor> items) {
        if (items.isEmpty()) return null;
        Actor a = selections.get(scope());
        if (!items.contains(a)) {
            a = items.get(Math.min(items.size()-1, selectionIndices.getOrDefault(scope(), 0)));
            selections.put(scope(), a);
        }
        return a;
    }

    /** All nested menus, tabs, confirmation dialogs and sliders share these keys. */
    public boolean keyDown(int key) {
        if (scope() == null) return false;
        if (key == Input.Keys.K || key == Input.Keys.ESCAPE) return escape();
        java.util.List<Actor> items = new java.util.ArrayList<>();
        controls(scope(), items);
        Actor a = selected(items);
        if (a == null) return true;
        boolean up = key == Input.Keys.UP || key == Input.Keys.W;
        boolean down = key == Input.Keys.DOWN || key == Input.Keys.S;
        boolean left = key == Input.Keys.LEFT || key == Input.Keys.A;
        boolean right = key == Input.Keys.RIGHT || key == Input.Keys.D;
        if (a instanceof Slider && (left || right)) {
            Slider slider = (Slider) a;
            slider.setValue(slider.getValue() + (right ? 5 : -5));
            return true;
        }
        Actor next = null;
        if (up || down || left || right) next = neighbor(items, a, left ? -1 : right ? 1 : 0, up ? 1 : down ? -1 : 0);
        else if (key == Input.Keys.TAB) next = items.get(Math.floorMod(items.indexOf(a) + 1, items.size()));
        if (up || down || left || right || key == Input.Keys.TAB) {
            if (next == null) return true; // 그 방향에 더 갈 곳이 없으면 그대로 둔다
            a = next;
            selections.put(scope(), a);
            selectionIndices.put(scope(), items.indexOf(a));
            stage.setKeyboardFocus(a);
            for (Actor p = a.getParent(); p != null; p = p.getParent()) {
                if (p instanceof ScrollPane) {
                    ScrollPane pane = (ScrollPane) p;
                    Vector2 pos = a.localToAscendantCoordinates(pane.getActor(), new Vector2());
                    pane.scrollTo(pos.x, pos.y, a.getWidth(), a.getHeight(), false, true);
                    break;
                }
            }
            return true;
        }
        if (key == Input.Keys.J || key == Input.Keys.ENTER || key == Input.Keys.SPACE) {
            if (a instanceof CheckBox) ((CheckBox) a).setChecked(!((CheckBox) a).isChecked());
            else if (a instanceof Button) a.fire(new ChangeListener.ChangeEvent());
            return true;
        }
        return anyOpen();
    }

    /** 창이 직접 정한 이웃: [왼쪽, 오른쪽, 아래, 위]. 자리만으로는 정할 수 없는 이동(닫기 ← → 탭 등)에 쓴다. */
    private final java.util.Map<Actor, Actor[]> links = new java.util.IdentityHashMap<>();

    private static int side(int dx, int dy) {
        return dx < 0 ? 0 : dx > 0 ? 1 : dy < 0 ? 2 : 3;
    }

    /** from 에서 (dx, dy) 방향을 누르면 to 로 간다 (dy: 위 +1, 아래 -1). 자리로 찾는 규칙보다 먼저 적용된다. */
    public void link(Actor from, int dx, int dy, Actor to) {
        if (from == null || to == null) return;
        links.computeIfAbsent(from, k -> new Actor[4])[side(dx, dy)] = to;
    }

    public void clearLinks() {
        links.clear();
    }

    /**
     * (dx, dy) 방향으로 옮겨 갈 컨트롤. 창이 정한 이웃이 있으면 그곳으로, 없으면 자리로 찾는다:
     * 위·아래는 다른 줄에서, 좌·우는 같은 줄에서 가장 가까운 것. 그 방향에 없으면 움직이지 않는다.
     */
    private Actor neighbor(java.util.List<Actor> items, Actor from, int dx, int dy) {
        Actor[] l = links.get(from);
        Actor to = l == null ? null : l[side(dx, dy)];
        if (to != null && items.contains(to)) return to;
        return strictNeighbor(items, from, dx, dy);
    }

    private Actor strictNeighbor(java.util.List<Actor> items, Actor from, int dx, int dy) {
        // 방금 다시 그린 창은 아직 자리가 계산되지 않았을 수 있다
        if (scope() instanceof com.badlogic.gdx.scenes.scene2d.utils.Layout) ((com.badlogic.gdx.scenes.scene2d.utils.Layout) scope()).validate();
        Vector2 c = center(from);
        float fx = c.x, fy = c.y, rowTolerance = Math.max(10f, from.getHeight() * 0.6f);
        Actor best = null;
        float bestScore = Float.MAX_VALUE;
        for (Actor o : items) {
            if (o == from) continue;
            Vector2 p = center(o);
            float ox = p.x - fx, oy = p.y - fy;
            float score;
            if (dy != 0) {
                if (oy * dy <= rowTolerance) continue; // 같은 줄이거나 반대쪽
                score = Math.abs(oy) * 4f + Math.abs(ox);
            } else {
                if (ox * dx <= 1f || Math.abs(oy) > rowTolerance) continue;
                score = Math.abs(ox);
            }
            if (score < bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best;
    }

    private final Vector2 tmpCenter = new Vector2();

    private Vector2 center(Actor a) {
        return a.localToStageCoordinates(tmpCenter.set(a.getWidth() / 2f, a.getHeight() / 2f));
    }

    /** 키보드 선택을 이 컨트롤에 둔다 (창을 다시 그린 뒤 선택 위치를 정할 때). */
    public void select(Actor a) {
        if (a == null || scope() == null) return;
        selections.put(scope(), a);
        java.util.List<Actor> items = new java.util.ArrayList<>();
        controls(scope(), items);
        selectionIndices.put(scope(), Math.max(0, items.indexOf(a)));
    }

    /** Draw focus separately so tab selection/check state is never confused with focus. */
    public void drawKeyboardFocus(SpriteBatch batch, Textures tx, Fonts fonts) {
        if (scope() == null) return;
        java.util.List<Actor> items = new java.util.ArrayList<>();
        controls(scope(), items);
        Actor a = selected(items);
        if (a == null) return;
        Vector2 p = a.localToStageCoordinates(new Vector2());
        batch.begin();
        batch.setColor(1f, 0.78f, 0.32f, 1);
        float x = p.x - 3, y = p.y - 3, w = a.getWidth() + 6, h = a.getHeight() + 6;
        batch.draw(tx.white, x, y, w, 2); batch.draw(tx.white, x, y+h-2, w, 2);
        batch.draw(tx.white, x, y, 2, h); batch.draw(tx.white, x+w-2, y, 2, h);
        batch.setColor(0.035f, 0.04f, 0.06f, 0.95f);
        batch.draw(tx.white, 190, 5, 580, 25);
        batch.setColor(Color.WHITE);
        fonts.small.setColor(UiSkin.GOLD);
        fonts.small.draw(batch, "WASD / 방향키 선택   ·   J 확인   ·   K 뒤로   ·   음량 A/D 조절", 210, 22);
        fonts.small.setColor(Color.WHITE);
        batch.end();
    }

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
        selections.remove(m.content);
        selectionIndices.remove(m.content);
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
