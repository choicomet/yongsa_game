package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.yongsa.combat.Boss;
import com.yongsa.config.DungeonDef;
import com.yongsa.config.OreDef;
import com.yongsa.model.Cutscene;
import com.yongsa.model.GameSession;
import com.yongsa.model.Weapon;
import com.yongsa.render.Assets;

import java.util.HashMap;
import java.util.Map;

/**
 * 플레이 화면 HUD (UI-03):
 * 왼쪽 위 체력·장착 검·구슬, 오른쪽 위 원형 미니맵과 광물 3종, 가운데 위 지역 이름·보스 체력,
 * 아래 가운데 스킬 3칸과 E 안내, 왼쪽 아래 "다음 할 일", 처음 한 번 조작 키 안내.
 */
public class Hud implements Disposable {

    private final GameSession s;
    private final Assets assets;
    private final Skin skin;

    private final HpBar hpBar;
    private final Label hpLabel, weaponLabel, attackLabel;
    private final Image weaponIcon;
    private final Table orbRow;
    private final Map<String, Label> oreLabels = new HashMap<>();
    private final Label areaLabel, bossName, goalLabel, hintLabel, toastLabel, captionLabel;
    private final Table bossTable, hintBox;
    private final HpBar bossBar;
    private final SkillSlot whirl, dash, dodge;
    public final Minimap minimap;
    private final Table keyGuide;
    private final Group root = new Group();
    private String lastIcon = "", lastArea = "";
    private int lastOrbs = -1;
    private float guideTime;

    public Hud(Stage stage, Skin skin, GameSession s, Assets assets) {
        this.s = s;
        this.assets = assets;
        this.skin = skin;
        root.setSize(stage.getWidth(), stage.getHeight());
        stage.addActor(root);

        // ---- 왼쪽 위
        Table status = new Table(skin);
        status.setBackground(skin.getDrawable("panel-hud"));
        hpBar = new HpBar(skin.getRegion("white"), new Color(0.88f, 0.2f, 0.26f, 1f));
        hpLabel = new Label("", skin, "small-white");
        weaponIcon = new Image(assets.region("weapon_sealed"));
        weaponLabel = new Label("", skin, "large");
        attackLabel = new Label("", skin, "small");
        orbRow = new Table();
        Table hpRow = new Table();
        hpRow.add(new Label("HP", skin, "gold")).padRight(8);
        hpRow.add(hpBar).width(240).height(16).padRight(10);
        hpRow.add(hpLabel).width(90).left();
        status.add(hpRow).left().colspan(2).row();
        status.add(weaponIcon).size(40).padTop(6).padRight(8);
        Table wt = new Table();
        wt.add(weaponLabel).left().row();
        wt.add(attackLabel).left();
        status.add(wt).left().padTop(6).row();
        status.add(orbRow).left().colspan(2).padTop(4);
        Table tl = new Table();
        tl.setFillParent(true);
        tl.top().left().pad(14);
        tl.add(status).left();
        root.addActor(tl);

        // ---- 오른쪽 위: 원형 미니맵 + 광물
        minimap = new Minimap(s);
        Table ores = new Table(skin);
        ores.setBackground(skin.getDrawable("panel-hud"));
        for (OreDef o : s.balance.ores) {
            Label count = new Label("0", skin, "large");
            oreLabels.put(o.id, count);
            ores.add(new Image(assets.region("ore_" + o.id))).size(24).padRight(6);
            ores.add(new Label(o.name, skin, "small-white")).left().padRight(10);
            ores.add(count).right().minWidth(36).row();
        }
        Table tr = new Table();
        tr.setFillParent(true);
        tr.top().right().pad(14);
        tr.add(minimap).size(200).row();
        tr.add(new Label("M  지도", skin, "small")).right().padTop(2).row();
        tr.add(ores).right().padTop(4);
        root.addActor(tr);

        // ---- 가운데 위: 지역 이름, 보스 체력
        areaLabel = new Label("", skin, "heading");
        bossName = new Label("", skin, "large-gold");
        bossBar = new HpBar(skin.getRegion("white"), new Color(0.75f, 0.15f, 0.6f, 1f));
        bossTable = new Table();
        bossTable.add(bossName).row();
        bossTable.add(bossBar).width(460).height(18).padTop(4);
        Table tc = new Table();
        tc.setFillParent(true);
        tc.top().padTop(18);
        tc.add(bossTable).row();
        tc.add(areaLabel).padTop(8);
        root.addActor(tc);
        areaLabel.getColor().a = 0;

        // ---- 왼쪽 아래: 다음 할 일 (FR-공통-07)
        Table goal = new Table(skin);
        goal.setBackground(skin.getDrawable("panel-hud"));
        goalLabel = new Label("", skin);
        goal.add(new Label("다음 할 일", skin, "gold")).left().row();
        goal.add(goalLabel).left();
        Table bl = new Table();
        bl.setFillParent(true);
        bl.bottom().left().pad(14);
        bl.add(goal).left();
        root.addActor(bl);

        // ---- 아래 가운데: E 안내 + 스킬
        hintLabel = new Label("", skin, "large-gold");
        hintBox = new Table(skin);
        hintBox.setBackground(skin.getDrawable("panel-hud"));
        hintBox.add(hintLabel);
        whirl = new SkillSlot(assets.region("skill_whirl"), skin.getRegion("white"), "Q", "회전 베기", skin.getFont("small"), skin.getFont("number"));
        dash = new SkillSlot(assets.region("skill_dash"), skin.getRegion("white"), "L", "돌진 베기", skin.getFont("small"), skin.getFont("number"));
        dodge = new SkillSlot(assets.region("skill_dodge"), skin.getRegion("white"), "Shift", "회피", skin.getFont("small"), skin.getFont("number"));
        Table skills = new Table();
        skills.add(whirl).size(64).padRight(18);
        skills.add(dash).size(64).padRight(18);
        skills.add(dodge).size(64);
        keyGuide = new Table(skin);
        keyGuide.setBackground(skin.getDrawable("panel-hud"));
        keyGuide.add(new Label("WASD 이동   J·Space 공격   Q 회전 베기   L 돌진 베기   Shift 회피", skin, "small-white")).row();
        keyGuide.add(new Label("E 캐기·상호작용   I 가방   M 지도   Esc 메뉴", skin, "small-white"));
        Table bc = new Table();
        bc.setFillParent(true);
        bc.bottom().padBottom(34);
        bc.add(keyGuide).padBottom(10).row();
        bc.add(hintBox).padBottom(14).row();
        bc.add(skills);
        root.addActor(bc);
        keyGuide.setVisible(!s.progress.tutorialDone());

        // ---- 알림·컷신 자막
        toastLabel = new Label("", skin, "large");
        toastLabel.setAlignment(Align.center);
        toastLabel.setWrap(true);
        captionLabel = new Label("", skin, "heading");
        captionLabel.setAlignment(Align.center);
        Table center = new Table();
        center.setFillParent(true);
        center.top().padTop(150);
        center.add(toastLabel).width(820);
        root.addActor(center);
        Table caption = new Table();
        caption.setFillParent(true);
        caption.bottom().padBottom(190);
        caption.add(captionLabel).width(1000);
        root.addActor(caption);
        toastLabel.getColor().a = 0;
    }

    public void resize(float w, float h) {
        root.setSize(w, h);
    }

    public void update(float delta) {
        hpBar.setRatio(s.hero.hp() / (float) s.hero.maxHp());
        hpLabel.setText(s.hero.hp() + " / " + s.hero.maxHp());
        Weapon w = s.inventory.equipped();
        weaponLabel.setText(w.displayName());
        String extra = s.hero.absorbedCount() > 0 ? "   (흡수한 힘 +" + (s.hero.absorbedCount() * 10) + "%)" : "";
        attackLabel.setText("공격력 " + s.attackOf(w) + extra);
        if (!w.icon().equals(lastIcon)) {
            lastIcon = w.icon();
            weaponIcon.setDrawable(new TextureRegionDrawable(assets.region(lastIcon)));
        }
        if (s.progress.orbs().size() != lastOrbs) {
            lastOrbs = s.progress.orbs().size();
            orbRow.clear();
            for (DungeonDef d : s.balance.dungeons) {
                Image orb = new Image(assets.region("orb_" + d.id));
                if (!s.progress.hasOrb(d.id)) orb.setColor(0.25f, 0.25f, 0.3f, 0.7f);
                orbRow.add(orb).size(22).padRight(4);
            }
            orbRow.add(new Label("보스의 구슬", skin, "small")).padLeft(4);
        }
        for (Map.Entry<String, Label> e : oreLabels.entrySet()) e.getValue().setText(String.valueOf(s.inventory.ore(e.getKey())));

        String area = s.areaName();
        if (!area.equals(lastArea)) {
            lastArea = area;
            areaLabel.setText(area);
            areaLabel.clearActions();
            areaLabel.getColor().a = 0;
            areaLabel.addAction(Actions.sequence(Actions.fadeIn(0.4f), Actions.delay(1.6f), Actions.fadeOut(0.8f)));
        }

        Boss boss = s.activeBoss();
        bossTable.setVisible(boss != null);
        if (boss != null) {
            bossName.setText(boss.def.name + (boss.phase2 ? "  (분노)" : ""));
            bossBar.setRatio(boss.hp() / (float) boss.maxHp());
        }

        goalLabel.setText(s.goal().text());
        String hint = s.interactHint();
        hintLabel.setText(hint);
        hintBox.setVisible(!hint.isEmpty() && s.cutscene() == null);

        whirl.setCooldown(s.unit.whirlCooldown, s.balance.skills.whirl.cooldown);
        dash.setCooldown(s.unit.dashCooldown, s.balance.skills.dash.cooldown);
        dodge.setCooldown(s.unit.dodgeCooldown, s.balance.skills.dodge.cooldown);

        Cutscene c = s.cutscene();
        captionLabel.setText(c != null ? c.caption() : "");

        // 처음 플레이 조작 안내 (FR-공통-02): 25초 보여 주고 끝
        if (keyGuide.isVisible()) {
            guideTime += delta;
            if (guideTime > 25f) {
                keyGuide.setVisible(false);
                s.progress.finishTutorial();
            }
        }
        root.setVisible(c == null || c.fall() < 0.5f);
    }

    public void toast(String msg) {
        toast(msg, Color.WHITE, 2.6f);
    }

    public void toast(String msg, Color color, float seconds) {
        toastLabel.setText(msg);
        toastLabel.setColor(color);
        toastLabel.clearActions();
        toastLabel.getColor().a = 1f;
        toastLabel.addAction(Actions.sequence(Actions.delay(seconds), Actions.fadeOut(0.6f)));
    }

    public void setVisible(boolean v) {
        root.setVisible(v);
    }

    @Override
    public void dispose() {
        minimap.dispose();
    }
}
