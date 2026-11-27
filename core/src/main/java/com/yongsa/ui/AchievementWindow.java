package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.yongsa.YongsaGame;
import com.yongsa.model.Achievement;
import com.yongsa.model.Achievements;

/**
 * 업적 창 (타이틀 → 업적). 카드 여섯 장: 달성한 업적은 금테 원 안에 그림, 오른쪽 위 보라색 “적용 중”,
 * 무엇을 해서 달성했는지와 효과가 보인다. 아직인 업적은 그림이 어두운 실루엣이고 이름은 ???, 힌트와 효과만 보인다.
 */
public class AchievementWindow extends Modal {
    private static final Color CARD_TEXT = new Color(0xb9b2a4ff);
    private static final Color LOCK_TEXT = new Color(0xaaa69cff);
    private static final Color LOCK_NAME = new Color(0x96918aff);
    private static final Color SILHOUETTE = new Color(0.03f, 0.035f, 0.045f, 1f);
    private static final Color EFFECT = new Color(0xffcf7aff);

    private final YongsaGame game;
    public AchievementWindow(YongsaGame game) {
        super(game.skin);
        this.game = game;
    }

    @Override
    public void build() {
        nav.style = "round";
        Achievements got = new Achievements(game.settings.achievements);
        Table p = panel("achievement/card_off");
        p.pad(24, 32, 26, 32);
        Table head = new Table();
        head.add(label("업적", "large", Ui.GOLD));
        head.add(label(got.count() + " / " + Achievement.values().length, "default", Ui.DIM)).padLeft(12).padTop(6);
        p.add(head).padBottom(10).row();
        p.add(new Image(skin.getDrawable("divider"))).width(828).height(1).padBottom(16).row();

        Table grid = new Table();
        int i = 0;
        for (Achievement a : Achievement.values()) {
            grid.add(card(a, got.has(a))).width(268).height(226).pad(6);
            if (++i % 3 == 0) grid.row();
        }
        p.add(grid).padBottom(14).row();
        p.add(nav.add("닫기", this::close)).width(200).height(48);
    }

    private Table card(Achievement a, boolean on) {
        Table c = new Table();
        c.setBackground(skin.getDrawable(on ? "achievement/card_on" : "achievement/card_off"));
        c.top().left();

        Image icon = Ui.artImage(AchievementArt.icon(game.art, a));
        if (!on) icon.setColor(SILHOUETTE);
        Stack ring = new Stack();
        if (!on) {
            Table lining = new Table();
            lining.add(new Image(game.art.frame("ui/achievement/medal_lining.png", 1, 0))).size(52);
            ring.add(lining);
        }
        ring.add(new Image(skin.getDrawable(on ? "achievement/ring_on" : "achievement/ring_off")));
        Table iconBox = new Table();
        iconBox.add(icon).size(40);
        ring.add(iconBox);

        Table top = new Table();
        top.add(ring).size(64).left().top();
        top.add().expandX();
        if (on) {
            Label tag = label("적용 중", "small", Ui.TEXT);
            Table t = new Table();
            t.setBackground(skin.getDrawable("achievement/ribbon"));
            t.add(tag);
            // 카드 오른쪽 끝에 붙는 제비꼬리 리본
            top.add(t).top().right().padRight(-12).padTop(2);
        }
        c.add(top).growX().row();

        c.add(label(on ? a.title : "???", "default", on ? Ui.TEXT : LOCK_NAME)).left().padTop(8).row();
        Label body = label(on ? a.done : a.hint, "small", on ? CARD_TEXT : LOCK_TEXT);
        body.setWrap(true);
        body.setAlignment(Align.topLeft);
        c.add(body).width(236).left().padTop(4).expandY().top().row();

        Table eff = new Table();
        eff.setBackground(skin.getDrawable(on ? "achievement/effect_on" : "achievement/effect_off"));
        eff.add(label("효과", "small", Ui.DIM)).padRight(6);
        eff.add(label(a.effect, "small", on ? EFFECT : LOCK_TEXT)).left().expandX();
        c.add(eff).growX().height(30);
        return c;
    }
}
