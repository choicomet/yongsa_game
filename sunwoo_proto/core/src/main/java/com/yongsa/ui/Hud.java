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
    private final HpBar bossBar, armorBar;
    /** 보스 체력바 위 남은 체력. */
    private final Label bossHpLabel;
    private final Label armorLabel;
    private final SkillSlot whirl, dash, dodge, guard;
    private final Table statusRow, speechBox;
    private final Label speechName, speechText;
    private final Map<String, Image> statusIcons = new HashMap<>();
    private final Map<String, Label> statusTimes = new HashMap<>();
    private Label godLabel;
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
        // 남은 체력은 체력바 위에 (2026-10-12 결정)
        Table hpCol = new Table();
        hpCol.add(hpLabel).left().padBottom(1).row();
        hpCol.add(hpBar).width(300).height(16);
        hpRow.add(new Label("HP", skin, "gold")).padRight(8).bottom();
        hpRow.add(hpCol).left();
        status.add(hpRow).left().colspan(2).row();
        status.add(weaponIcon).size(40).padTop(6).padRight(8);
        Table wt = new Table();
        wt.add(weaponLabel).left().row();
        wt.add(attackLabel).left();
        status.add(wt).left().padTop(6).row();
        status.add(orbRow).left().colspan(2).padTop(4).row();
        statusRow = new Table();
        for (String st : new String[]{"poison", "burn", "shock", "freeze", "blind", "slow", "stun", "invert"}) {
            Image icon = new Image(assets.region("status_" + st));
            Label t = new Label("", skin, "small-white");
            statusIcons.put(st, icon);
            statusTimes.put(st, t);
        }
        status.add(statusRow).left().colspan(2).padTop(4);
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
        bossHpLabel = new Label("", skin, "small-white");
        bossTable.add(bossName).row();
        bossTable.add(bossHpLabel).padTop(2).row();
        bossTable.add(bossBar).width(460).height(18).padTop(1).row();
        armorBar = new HpBar(skin.getRegion("white"), new Color(0.9f, 0.86f, 0.72f, 1f));
        armorLabel = new Label("뼈갑옷 — 깨기 전에는 체력이 닳지 않는다", skin, "small-white");
        Table armorRow = new Table();
        armorRow.add(armorBar).width(460).height(9).row();
        armorRow.add(armorLabel).padTop(1);
        bossTable.add(armorRow).padTop(3);
        armorBar.setVisible(false);
        armorLabel.setVisible(false);
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
        dodge = new SkillSlot(assets.region("skill_dodge"), skin.getRegion("white"), "Space", "회피", skin.getFont("small"), skin.getFont("number"));
        guard = new SkillSlot(assets.region("skill_guard"), skin.getRegion("white"), "Ctrl", "막기", skin.getFont("small"), skin.getFont("number"));
        Table skills = new Table();
        skills.add(whirl).size(64).padRight(18);
        skills.add(dash).size(64).padRight(18);
        skills.add(dodge).size(64).padRight(18);
        skills.add(guard).size(64);
        keyGuide = new Table(skin);
        keyGuide.setBackground(skin.getDrawable("panel-hud"));
        keyGuide.add(guideLine1 = new Label("", skin, "small-white")).row();
        keyGuide.add(guideLine2 = new Label("", skin, "small-white"));
        applyKeys(new com.yongsa.KeyBindings());
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

        // ---- 대사 (마왕·용사)
        speechBox = new Table(skin);
        speechBox.setBackground(skin.getDrawable("panel-hud"));
        speechName = new Label("", skin, "gold");
        speechText = new Label("", skin, "heading");
        speechText.setWrap(true);
        speechText.setAlignment(Align.center);
        speechBox.add(speechName).padBottom(4).row();
        speechBox.add(speechText).width(760);
        Table sp = new Table();
        sp.setFillParent(true);
        sp.bottom().padBottom(250);
        sp.add(speechBox);
        root.addActor(sp);
        speechBox.getColor().a = 0;
    }

    /** 대사 상자. 마왕은 붉게, 용사의 독백은 옅은 금색 기울임 느낌으로. */
    public void speech(String speaker, String text, float seconds) {
        boolean demon = "마왕".equals(speaker);
        speechName.setText(demon ? "마왕" : speaker);
        speechName.setColor(demon ? new Color(1f, 0.35f, 0.35f, 1f) : new Color(0.95f, 0.85f, 0.55f, 1f));
        speechText.setText(demon ? "「 " + text + " 」" : "( " + text + " )");
        speechText.setColor(demon ? new Color(1f, 0.85f, 0.85f, 1f) : new Color(0.92f, 0.92f, 0.95f, 1f));
        speechBox.clearActions();
        speechBox.getColor().a = 0;
        speechBox.addAction(Actions.sequence(Actions.fadeIn(0.25f), Actions.delay(seconds), Actions.fadeOut(0.6f)));
    }

    private Label guideLine1, guideLine2;

    /** 바꾼 조작 키를 스킬 칸 위 키와 처음 키 안내에 반영한다. */
    public void applyKeys(com.yongsa.KeyBindings k) {
        whirl.setKey(k.primaryShort(com.yongsa.KeyBindings.Action.WHIRL));
        dash.setKey(k.primaryShort(com.yongsa.KeyBindings.Action.DASH));
        dodge.setKey(k.primaryShort(com.yongsa.KeyBindings.Action.DODGE));
        guard.setKey(k.primaryShort(com.yongsa.KeyBindings.Action.GUARD));
        String move = k.primaryShort(com.yongsa.KeyBindings.Action.MOVE_UP) + k.primaryShort(com.yongsa.KeyBindings.Action.MOVE_LEFT)
                + k.primaryShort(com.yongsa.KeyBindings.Action.MOVE_DOWN) + k.primaryShort(com.yongsa.KeyBindings.Action.MOVE_RIGHT);
        guideLine1.setText(move + " 이동   " + k.primaryShort(com.yongsa.KeyBindings.Action.ATTACK) + " 공격(가까운 적을 자동으로 바라봄)   "
                + k.primaryShort(com.yongsa.KeyBindings.Action.WHIRL) + " 회전 베기   " + k.primaryShort(com.yongsa.KeyBindings.Action.DASH) + " 돌진 베기   "
                + k.primaryShort(com.yongsa.KeyBindings.Action.DODGE) + " 회피   " + k.primaryShort(com.yongsa.KeyBindings.Action.GUARD) + " 막기(누르고 있기)");
        guideLine2.setText(k.primaryShort(com.yongsa.KeyBindings.Action.INTERACT) + " 캐기·상호작용   " + k.primaryShort(com.yongsa.KeyBindings.Action.BAG)
                + " 가방   " + k.primaryShort(com.yongsa.KeyBindings.Action.MAP) + " 지도   Esc 메뉴");
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
            orbRow.add(new Label("보스의 정수", skin, "small")).padLeft(4);
        }
        for (Map.Entry<String, Label> e : oreLabels.entrySet()) e.getValue().setText(s.inventory.oreText(e.getKey()));

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
            bossName.setText(boss.def.name + (boss.phase == 2 ? "  — 본모습" : ""));
            bossBar.setRatio(boss.hp() / (float) boss.maxHp());
            bossHpLabel.setText(boss.hp() + " / " + boss.maxHp());
            boolean armored = boss.armor > 0;
            armorBar.setVisible(armored);
            armorLabel.setVisible(armored || boss.stagger > 0);
            if (armored) {
                armorBar.setRatio(boss.armor / (float) Math.max(1, boss.armorMax));
                armorLabel.setText("뼈갑옷 " + boss.armor + " — 깨기 전에는 체력이 닳지 않는다");
            } else if (boss.stagger > 0) {
                armorLabel.setText(boss.stunned ? "돌진 끝에 기절했다! 지금이 기회다" : "갑옷이 깨져 휘청인다! 지금이 기회다");
            }
        }

        goalLabel.setText(s.goal().text());
        String hint = s.interactHint();
        if (hint.isEmpty() && s.sheltered()) hint = "바위 뒤에 숨어 있다 — 폭풍을 막는 중";
        Boss cb = s.activeBoss();
        if (hint.isEmpty() && cb != null && cb.cocoon) hint = "바람의 고치 " + (int) Math.ceil(cb.cocoonTime) + "초  ·  회피 쿨타임 없음";
        hintLabel.setText(hint);
        hintBox.setVisible(!hint.isEmpty() && s.cutscene() == null);

        whirl.setCooldown(s.unit.whirlCooldown, s.balance.skills.whirl.cooldown);
        dash.setCooldown(s.unit.dashCooldown, s.balance.skills.dash.cooldown);
        dodge.setCooldown(s.unit.dodgeCooldown, s.balance.skills.dodge.cooldown);
        guard.setCooldown(s.unit.guardBroken, s.balance.guard.breakTime);
        guard.setMeter(s.unit.guardRatio(), s.unit.guardBroken > 0, s.unit.guarding);
        boolean sealed = s.unit.skillsSealed();
        whirl.setSealed(sealed);
        dash.setSealed(sealed);
        updateStatuses();

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

    private void updateStatuses() {
        statusRow.clearChildren();
        addStatus("poison", "중독", s.unit.poison);
        addStatus("burn", "화상", s.unit.burn);
        addStatus("shock", "감전", s.unit.shock);
        addStatus("freeze", "빙결", s.unit.freeze);
        addStatus("blind", "실명", s.unit.blind);
        addStatus("slow", "둔화", s.unit.slow);
        addStatus("stun", "기절", s.unit.stun);
        if (s.hero.invulnerable) {
            if (godLabel == null) godLabel = new Label("무적 (개발자 도구)", skin, "small-white");
            godLabel.setColor(1f, 0.85f, 0.35f, 1f);
            statusRow.add(godLabel).padRight(12);
        }
        if (s.inverted()) {
            Label l = statusTimes.get("invert");
            l.setText("키 뒤틀림");
            statusRow.add(statusIcons.get("invert")).size(24).padRight(4);
            statusRow.add(l).padRight(12);
        } else if (s.mirrorTime() > 0) {
            Label l = statusTimes.get("invert");
            l.setText(String.format("좌우 반전 %.1f", s.mirrorTime()));
            statusRow.add(statusIcons.get("invert")).size(24).padRight(4);
            statusRow.add(l).padRight(12);
        }
    }

    private void addStatus(String id, String name, float t) {
        if (t <= 0) return;
        Label l = statusTimes.get(id);
        l.setText(name + " " + (int) Math.ceil(t) + "초");
        statusRow.add(statusIcons.get(id)).size(24).padRight(4);
        statusRow.add(l).padRight(12);
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
