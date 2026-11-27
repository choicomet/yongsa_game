package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.Weapon;
import com.yongsa.screen.GameScreen;

/**
 * 가방 (UI-06): 가진 검만 세로 목록으로 보여 주고 장착한다.
 * ↑↓(W·S) 검 고르기, Space·Enter 장착, Esc·가방 키 닫기. 마우스는 한 번 눌러 고르고, 고른 검을 다시 누르면 장착.
 */
public class BagWindow extends Modal {
    private static final int VISIBLE_CARDS = 5;
    private static final float CARD_H = 80, CARD_GAP = 12;

    private final GameScreen screen;
    private int selected = -1;
    private ScrollPane listScroll;
    private Table selectedCard;
    private boolean scrollPending;

    public BagWindow(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
        ensureDrawables(skin);
    }

    /** 가방 전용 둥근 판 그림. 스킨에 한 번만 만든다. */
    private static void ensureDrawables(com.badlogic.gdx.scenes.scene2d.ui.Skin skin) {
        if (skin.has("bagCard", Drawable.class)) return;
        skin.add("bagCard", Ui.rounded(0x16202aff, 0x2d3842ff, 2, 12, 16, 8), Drawable.class);
        skin.add("bagCardSel", Ui.rounded(0x2c271fff, 0xe3b875ff, 2, 12, 16, 8), Drawable.class);
        skin.add("bagIcon", Ui.rounded(0x0b1016ff, 0x2d3842ff, 1, 10, 3, 3), Drawable.class);
        skin.add("bagKey", Ui.rounded(0x0b1016ff, 0x5a636cff, 1, 7, 7, 2), Drawable.class);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (screen.game.settings.keys().is(com.yongsa.save.KeyBindings.Action.BAG, keycode)) {
            close();
            return true;
        }
        switch (keycode) {
            case Input.Keys.UP:
            case Input.Keys.W:
                move(-1);
                return true;
            case Input.Keys.DOWN:
            case Input.Keys.S:
            case Input.Keys.TAB:
                move(1);
                return true;
            case Input.Keys.SPACE:
            case Input.Keys.ENTER:
            case Input.Keys.NUMPAD_ENTER:
                equipSelected();
                return true;
            default:
                return super.keyDown(keycode);
        }
    }

    private void move(int d) {
        int n = screen.session.state.weapons.size();
        if (n == 0) return;
        selected = Math.floorMod(selected + d, n);
        screen.game.sfx.play("select");
        scrollPending = true;
        rebuild();
    }

    private void equipSelected() {
        GameSession g = screen.session;
        GameState s = g.state;
        if (selected < 0 || selected >= s.weapons.size()) return;
        Weapon w = s.weapons.get(selected);
        if (w.uid == s.equippedUid) return;
        g.forge.equip(s, w);
        g.onWeaponsChanged();
        screen.game.sfx.play("select");
        rebuild();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (scrollPending && listScroll != null && selectedCard != null && listScroll.getHeight() > 0) {
            scrollPending = false;
            Vector2 at = selectedCard.localToAscendantCoordinates(listScroll.getActor(), new Vector2());
            listScroll.scrollTo(at.x, at.y, selectedCard.getWidth(), selectedCard.getHeight());
            listScroll.updateVisualScroll();
        }
    }

    @Override
    public void build() {
        GameState s = screen.session.state;
        if (selected < 0) selected = Math.max(0, s.weapons.indexOf(s.equipped()));
        selected = Math.min(selected, Math.max(0, s.weapons.size() - 1));
        Table p = panel("panelRound");
        p.pad(24, 26, 26, 26);

        Table head = new Table();
        head.add(label("가방", "large", Ui.GOLD)).left().expandX();
        TextButton close = new TextButton("닫기", skin, "round");
        close.getLabelCell().expand(false, false).fill(false);
        close.add(keycap("Esc")).padLeft(8);
        close.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) { close(); }
        });
        head.add(close).height(42);
        p.add(head).growX().padBottom(22).row();

        Table list = new Table();
        list.top();
        selectedCard = null;
        for (int i = 0; i < s.weapons.size(); i++) {
            list.add(card(i)).width(388).height(CARD_H).padBottom(i == s.weapons.size() - 1 ? 0 : CARD_GAP).row();
        }
        listScroll = new ScrollPane(list);
        listScroll.setScrollingDisabled(true, false);
        listScroll.setFadeScrollBars(true);
        int rows = Math.min(VISIBLE_CARDS, Math.max(1, s.weapons.size()));
        float h = rows * CARD_H + (rows - 1) * CARD_GAP;
        p.add(listScroll).width(388).height(h);
        scrollPending = true;
    }

    private Table card(int index) {
        GameSession g = screen.session;
        GameState s = g.state;
        Weapon w = s.weapons.get(index);
        boolean sel = index == selected;
        boolean equipped = w.uid == s.equippedUid;

        Table card = new Table(skin);
        card.setBackground(skin.getDrawable(sel ? "bagCardSel" : "bagCard"));
        card.setTouchable(Touchable.enabled);
        if (sel) selectedCard = card;

        Table icon = new Table(skin);
        icon.setBackground(skin.getDrawable("bagIcon"));
        icon.add(Ui.artImage(screen.game.art.sword(w.defId, w.awakened))).size(46);
        card.add(icon).size(56).padRight(16);

        Table text = new Table();
        text.add(label(g.rules.weaponName(w), "default", w.awakened ? Ui.PURPLE : Ui.TEXT)).left().row();
        text.add(small("공격력 " + g.rules.attack(s, w), Ui.DIM, 0.85f)).left().padTop(4);
        card.add(text).left().expandX();

        if (equipped) {
            card.add(small("장착 중", Ui.GOLD, 0.8f)).right();
        } else if (sel) {
            Table hint = new Table();
            hint.add(keycap("Space")).padRight(6);
            hint.add(small("장착", Ui.DIM, 0.8f));
            card.add(hint).right();
        }

        card.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (selected == index) {
                    equipSelected();
                } else {
                    selected = index;
                    screen.game.sfx.play("select");
                    rebuild();
                }
            }
        });
        return card;
    }

    private Table keycap(String text) {
        Table k = new Table(skin);
        k.setBackground(skin.getDrawable("bagKey"));
        Label l = label(text, "small", new Color(0xd9dde0ff));
        l.setFontScale(0.7f);
        k.add(l);
        return k;
    }

    private Label small(String text, Color color, float scale) {
        Label l = label(text, "small", color);
        l.setFontScale(scale);
        return l;
    }
}
