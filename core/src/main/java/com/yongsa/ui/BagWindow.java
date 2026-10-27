package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.config.DungeonDef;
import com.yongsa.config.OreDef;
import com.yongsa.model.AwakenAbility;
import com.yongsa.model.GameSession;
import com.yongsa.model.Weapon;
import com.yongsa.render.Assets;

/** 가방 (UI-06): 검 목록과 장착, 광물 개수, 보스의 구슬, 흡수한 힘. */
public class BagWindow extends GameWindow {

    private final GameSession s;
    private final Assets assets;
    private final Table content = new Table();
    private final TextButton closeBtn;

    public BagWindow(Skin skin, GameSession s, Assets assets) {
        super("가방", skin);
        this.s = s;
        this.assets = assets;
        add(content).row();
        closeBtn = button("닫기 (Esc / I)", this::close);
        add(closeBtn).right().width(190).height(46).padTop(10);
    }

    @Override
    public void refresh() {
        nav.clear();
        content.clear();
        Table weapons = new Table();
        weapons.top().left();
        for (Weapon w : s.inventory.weapons()) {
            boolean eq = w == s.inventory.equipped();
            weapons.add(new Image(assets.region(w.icon()))).size(40).padRight(10);
            weapons.add(new Label(w.displayName(), skin, eq ? "large-gold" : "large")).width(300).left();
            float ls = s.lifestealOf(w);
            weapons.add(new Label("공격력 " + s.attackOf(w) + (ls > 0 ? "\n흡혈 " + ls + "%" : ""), skin, "dim")).width(140).left();
            TextButton b = button(eq ? "장착 중" : "장착", () -> {
                s.equip(w);
                refresh();
            });
            b.setDisabled(eq);
            weapons.add(b).width(120).height(44).padBottom(6).row();
        }
        ScrollPane sp = new ScrollPane(weapons, skin);
        sp.setFadeScrollBars(false);
        sp.setScrollingDisabled(true, false);

        Table side = new Table(skin);
        side.setBackground(skin.getDrawable("panel-light"));
        side.top().left().pad(10);
        side.add(new Label("광물", skin, "gold")).left().colspan(3).row();
        for (OreDef o : s.balance.ores) {
            side.add(new Image(assets.region("ore_" + o.id))).size(26).padRight(8);
            side.add(new Label(o.name, skin)).width(110).left();
            side.add(new Label(s.inventory.oreText(o.id) + (s.inventory.infiniteOre() ? "" : "개"), skin, "large")).right().row();
        }
        side.add(new Label("보스의 정수", skin, "gold")).left().colspan(3).padTop(14).row();
        Table orbs = new Table();
        for (DungeonDef d : s.balance.dungeons) {
            Image orb = new Image(assets.region("orb_" + d.id));
            if (!s.progress.hasOrb(d.id)) orb.setColor(0.25f, 0.25f, 0.3f, 0.7f);
            orbs.add(orb).size(30).padRight(8);
        }
        side.add(orbs).left().colspan(3).row();
        side.add(new Label(s.progress.sealBroken() ? "정수는 캠프 문양에 박혔다." : s.progress.orbs().size() + " / 3  — 셋을 모으면 봉인된 검을 성검으로 해방할 수 있다.",
                skin, "small")).left().colspan(3).row();
        side.add(new Label("흡수한 힘", skin, "gold")).left().colspan(3).padTop(14).row();
        side.add(new Label("최대 체력 " + s.hero.maxHp() + "   공격력 +" + (s.hero.absorbedCount() * 10) + "%", skin)).left().colspan(3).row();
        if (s.hero.absorbed().isEmpty()) side.add(new Label("각성 능력 없음", skin, "small")).left().colspan(3).row();
        for (AwakenAbility a : s.hero.absorbed()) {
            side.add(new Label("· " + a.title + ": " + a.description, skin, "small-white")).left().colspan(3).row();
        }

        content.add(sp).width(640).height(420).top().padRight(14);
        content.add(side).width(380).height(420).top();
        nav.add(closeBtn);
        pack();
        centerOnStage();
    }

    /** 이 키를 누르면 닫는다 (가방 키, 사용자가 바꿀 수 있다). */
    public java.util.function.IntPredicate closeKey = k -> k == com.badlogic.gdx.Input.Keys.I;

    @Override
    public boolean keyDown(int key) {
        if (closeKey.test(key)) {
            close();
            return true;
        }
        return super.keyDown(key);
    }
}
