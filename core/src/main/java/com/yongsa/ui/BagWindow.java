package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.yongsa.config.Balance.WeaponDef;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.OreType;
import com.yongsa.model.Weapon;
import com.yongsa.screen.GameScreen;

/** 가방 (UI-06): 무기 목록과 장착, 광물 개수, 용사에게 흡수된 힘. */
public class BagWindow extends Modal {
    private final GameScreen screen;

    public BagWindow(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == com.badlogic.gdx.Input.Keys.I) {
            close();
            return true;
        }
        return super.keyDown(keycode);
    }

    @Override
    public void build() {
        GameSession g = screen.session;
        GameState s = g.state;
        Table p = panel();
        p.add(label("가방", "large", Ui.GOLD)).colspan(2).padBottom(14).row();

        Table weapons = new Table();
        weapons.add(label("무기 (눌러서 장착)", "default", Ui.DIM)).left().colspan(2).padBottom(8).row();
        for (Weapon w : s.weapons) {
            boolean equipped = w.uid == s.equippedUid;
            weapons.add(new Image(screen.game.art.sword(w.defId, w.awakened))).size(36).padRight(8);
            String text = g.rules.weaponName(w) + "   공격력 " + g.rules.attack(s, w) + (equipped ? "   [장착 중]" : "");
            TextButton b = nav.add(text, () -> {
                g.forge.equip(s, w);
                g.onWeaponsChanged();
                screen.game.sfx.play("select");
                rebuild();
            });
            b.getLabel().setColor(equipped ? Ui.GOLD : Ui.TEXT);
            weapons.add(b).width(470).height(46).left().padBottom(6).row();
        }
        p.add(weapons).top().padRight(30);

        Table side = new Table();
        side.add(label("광물", "default", Ui.DIM)).left().colspan(2).padBottom(8).row();
        for (OreType t : OreType.values()) {
            side.add(new Image(screen.game.art.gem(t))).size(30).padRight(8);
            side.add(label(t.displayName + "  " + s.ore(t) + "개")).left().padBottom(6).row();
        }
        side.add(label("용사에게 흡수된 힘", "default", Ui.DIM)).left().colspan(2).padTop(18).padBottom(8).row();
        if (s.bossesDefeated.isEmpty() && s.imprints.isEmpty()) {
            side.add(label("아직 없다", "small", Ui.DIM)).left().colspan(2).row();
        }
        for (String id : s.bossesDefeated) {
            side.add(label("· " + g.balance.dungeon(id).boss.name + "의 힘 (최대 체력)", "small", Ui.GREEN)).left().colspan(2).padBottom(4).row();
        }
        for (String id : s.imprints) {
            WeaponDef d = g.balance.weapon(id);
            side.add(label("· " + d.abilityName + ": " + d.abilityDesc, "small", Ui.GOLD)).left().colspan(2).padBottom(4).row();
        }
        if (!s.imprints.isEmpty()) {
            side.add(label("  모든 무기 공격력 +" + (s.imprints.size() * 10) + "%", "small", Ui.GOLD)).left().colspan(2).row();
        }
        p.add(side).top().width(380).row();
        p.add(nav.add("닫기", this::close)).colspan(2).width(200).height(50).padTop(16);
        nav.setFocusIndex(Math.max(0, s.weapons.indexOf(s.equipped())));
    }
}
