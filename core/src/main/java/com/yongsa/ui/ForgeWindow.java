package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.yongsa.config.Balance;
import com.yongsa.config.Balance.WeaponDef;
import com.yongsa.gfx.Art;
import com.yongsa.model.EnhanceService;
import com.yongsa.model.EnhanceService.Odds;
import com.yongsa.model.GameSession;
import com.yongsa.model.GameState;
import com.yongsa.model.OreType;
import com.yongsa.model.Weapon;
import com.yongsa.screen.GameScreen;

import java.util.ArrayList;
import java.util.List;

/**
 * 대장간 창 (UI-04, 2026-11-27 개편).
 * 왼쪽 목록에서 검을 고르면(↑↓) 오른쪽에서 그 검으로 할 일(강화 · 흡수 · 분해, ←→)을 하고 Enter 로 실행한다.
 * 목록 아래 "새로 만들기" 칸을 고르면 제작. Q 는 고른 검 장착, Esc 는 닫기. 마우스로도 같은 일을 한다.
 * 가운데 작업대에는 고른 검이 올라간다. 파괴 확인과 실제 판정/저장 규칙은 그대로 유지한다.
 */
public class ForgeWindow extends Modal {
    private enum Act {
        ENHANCE("강화"), ABSORB("흡수"), DISMANTLE("분해");
        final String label;
        Act(String label) { this.label = label; }
    }

    /** 왼쪽 목록 한 칸: 가진 검이거나, 새로 만들 검. */
    private static final class Item {
        final Weapon weapon;
        final WeaponDef craft;
        Item(Weapon weapon, WeaponDef craft) { this.weapon = weapon; this.craft = craft; }
    }

    private static final Color LINE_DIM = new Color(0x4f5660ff);
    private static final Color GREEN = new Color(0x6fd38aff);
    private static final Color PROB_SUCCESS = new Color(0x3f8fb0ff);
    private static final Color PROB_KEEP = new Color(0x5d6470ff);
    private static final Color PROB_DOWN = new Color(0xc07a2aff);
    private static final Color PROB_DESTROY = new Color(0xc0392bff);
    private static final Color WARN_TEXT = new Color(0xff8a7eff);

    private final GameScreen screen;
    private final GameSession g;
    private final Art art;

    private final ForgeMotion motion = new ForgeMotion();
    private Runnable afterHammer;
    private int playedStrikes;
    private String workingDef;
    private boolean workingAwakened;
    private String resultText;
    private Color resultColor = Ui.TEXT;

    private final List<Item> items = new ArrayList<>();
    /** 고른 칸: 가진 검이면 uid, 새로 만들 검이면 정의 id. */
    private int selUid = -1;
    private String selCraft;
    private Act act = Act.ENHANCE;
    private ScrollPane listScroll;
    private Table selectedCard;
    /** 목록이 화면에 놓인 다음 고른 카드가 보이게 굴린다. */
    private boolean scrollPending = true;

    public ForgeWindow(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
        this.g = screen.session;
        this.art = screen.game.art;
        addCaptureListener(event -> {
            if (motion.busy() && event instanceof InputEvent) { event.cancel(); return true; }
            return false;
        });
        Weapon eq = g.state.equipped();
        if (eq != null) selUid = eq.uid;
        // 성검만 들었고 만들 수 있는 검이 있으면 처음부터 제작 칸을 고른다
        if (eq == null || !g.balance.weapon(eq.defId).enhanceable) {
            for (WeaponDef d : g.balance.weapons) {
                if (d.enhanceable && g.forge.craftBlockReason(g.state, d) == null) {
                    selUid = -1;
                    selCraft = d.id;
                    break;
                }
            }
        }
        refreshItems();
        act = firstEnabledAct();
    }

    private float speed() {
        return screen.game.settings.effectSpeed;
    }

    public boolean isWorking() { return motion.busy(); }

    // ───────────────────────── 키보드

    @Override
    public boolean keyDown(int keycode) {
        if (motion.busy()) return true;
        switch (keycode) {
            case Input.Keys.ESCAPE:
                onCancel();
                return true;
            case Input.Keys.UP:
            case Input.Keys.W:
                moveSelection(-1);
                return true;
            case Input.Keys.DOWN:
            case Input.Keys.S:
                moveSelection(1);
                return true;
            case Input.Keys.LEFT:
            case Input.Keys.A:
                moveAct(-1);
                return true;
            case Input.Keys.RIGHT:
            case Input.Keys.D:
            case Input.Keys.TAB:
                moveAct(1);
                return true;
            case Input.Keys.ENTER:
            case Input.Keys.NUMPAD_ENTER:
            case Input.Keys.SPACE:
                runPrimary();
                return true;
            case Input.Keys.Q:
                equipSelected();
                return true;
            default:
                return false;
        }
    }

    @Override public void close() {
        if (!motion.busy()) super.close();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (scrollPending && listScroll != null && selectedCard != null && listScroll.getHeight() > 0) {
            scrollPending = false;
            com.badlogic.gdx.math.Vector2 at = selectedCard.localToAscendantCoordinates(listScroll.getActor(), new com.badlogic.gdx.math.Vector2());
            listScroll.scrollTo(at.x, at.y, selectedCard.getWidth(), selectedCard.getHeight());
            listScroll.updateVisualScroll();
        }
        boolean wasBusy = motion.busy();
        boolean completed = motion.advance(delta);
        int strikes = motion.strikes();
        if (wasBusy && (motion.phase() == ForgeMotion.Phase.HAMMER || completed)) {
            if (strikes > playedStrikes) screen.game.sfx.play("mine");
            playedStrikes = strikes;
        }
        if (completed && afterHammer != null) {
            Runnable action = afterHammer;
            afterHammer = null;
            action.run();
        } else if (wasBusy && !motion.busy()) rebuild();
    }

    // ───────────────────────── 고르기

    private void refreshItems() {
        items.clear();
        for (Weapon w : g.state.weapons) items.add(new Item(w, null));
        for (WeaponDef d : g.balance.weapons) if (d.enhanceable && d.ore != null) items.add(new Item(null, d));
        // 고른 검이 사라졌으면(파괴 · 분해 · 흡수) 장착한 검으로
        if (selectedIndex() < 0) {
            Weapon eq = g.state.equipped();
            selCraft = null;
            selUid = eq != null ? eq.uid : items.isEmpty() || items.get(0).weapon == null ? -1 : items.get(0).weapon.uid;
        }
    }

    private int selectedIndex() {
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.weapon != null && it.weapon.uid == selUid && selCraft == null) return i;
            if (it.craft != null && it.craft.id.equals(selCraft)) return i;
        }
        return -1;
    }

    private Item selected() {
        int i = selectedIndex();
        return i < 0 ? null : items.get(i);
    }

    private void select(Item it) {
        if (motion.busy() || it == null) return;
        if (it.weapon != null) { selUid = it.weapon.uid; selCraft = null; }
        else { selUid = -1; selCraft = it.craft.id; }
        resultText = null;
        workingDef = null;
        motion.clearResult();
        act = firstEnabledAct();
        rebuild();
    }

    private void moveSelection(int d) {
        if (items.isEmpty()) return;
        int i = Math.max(0, selectedIndex());
        select(items.get(Math.floorMod(i + d, items.size())));
    }

    private boolean actEnabled(Act a) {
        Item it = selected();
        if (it == null || it.weapon == null) return false;
        Weapon w = it.weapon;
        switch (a) {
            case ENHANCE: return g.balance.weapon(w.defId).enhanceable && !g.enhance.isMax(w);
            case ABSORB: return g.absorb.skillAbsorbBlockReason(g.state, w) == null;
            default: return g.forge.dismantleBlockReason(g.state, w) == null;
        }
    }

    private Act firstEnabledAct() {
        for (Act a : Act.values()) if (actEnabled(a)) return a;
        return Act.ENHANCE;
    }

    private void moveAct(int d) {
        Item it = selected();
        if (it == null || it.weapon == null) return;
        Act[] all = Act.values();
        for (int k = 1; k < all.length; k++) {
            Act next = all[Math.floorMod(act.ordinal() + d * k, all.length)];
            if (actEnabled(next)) {
                setAct(next);
                return;
            }
        }
    }

    private void setAct(Act a) {
        if (motion.busy() || a == act) return;
        act = a;
        resultText = null;
        motion.clearResult();
        rebuild();
    }

    private void equipSelected() {
        Item it = selected();
        if (motion.busy() || it == null || it.weapon == null || it.weapon.uid == g.state.equippedUid) return;
        g.forge.equip(g.state, it.weapon);
        g.onWeaponsChanged();
        screen.save(false);
        resultText = g.rules.weaponName(it.weapon) + "을 들었다";
        resultColor = Ui.GOLD;
        rebuild();
    }

    /** Enter: 지금 고른 칸과 할 일의 큰 버튼. */
    private void runPrimary() {
        Item it = selected();
        if (it == null) return;
        if (it.craft != null) {
            craft(it.craft);
            return;
        }
        Weapon w = it.weapon;
        if (!actEnabled(act)) return;
        switch (act) {
            case ENHANCE:
                if (g.enhance.blockReason(g.state, w) != null) return;
                if (g.enhance.canDestroy(g.state, w)) confirmRisk(w); else doEnhance(w);
                break;
            case ABSORB:
                confirmAbsorb(w);
                break;
            default:
                confirmDismantle(w);
        }
    }

    // ───────────────────────── 화면

    @Override
    public void build() {
        refreshItems();
        Table p = panel("panelRound");
        p.pad(18, 24, 22, 24);

        Table head = new Table();
        head.add(label("대장간", "large", Ui.GOLD)).padRight(26);
        for (OreType ore : OreType.values()) {
            Table chip = new Table(skin);
            chip.setBackground(skin.getDrawable("forgeChip"));
            chip.add(Ui.artImage(art.gem(ore))).size(24).padRight(6);
            chip.add(label(String.valueOf(g.state.ore(ore)), "small", Ui.TEXT));
            head.add(chip).height(36).padRight(8);
        }
        head.add().expandX();
        Table mastery = new Table(skin);
        mastery.setBackground(skin.getDrawable("forgeChip"));
        mastery.add(new MasteryBar(g.enhance, g.state, screen.game.fonts.small, art.white())).width(300).height(40);
        head.add(mastery).padRight(14);
        TextButton close = new TextButton("닫기", skin, "round");
        close.getLabelCell().expand(false, false).fill(false);
        close.add(keycap("Esc")).padLeft(8);
        close.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) { close(); }
        });
        close.setDisabled(motion.busy());
        head.add(close).height(42);
        p.add(head).growX().height(52).row();
        p.add(new Image(skin.getDrawable("divider"))).growX().height(1).padTop(12).padBottom(14).row();

        Table body = new Table();
        body.add(buildList()).width(300).height(520).top().padRight(16);
        body.add(buildCenter()).width(440).height(520).top().padRight(16);
        body.add(buildRight()).width(396).height(520).top();
        p.add(body).row();
    }

    private Table keycap(String text) {
        Table k = new Table(skin);
        k.setBackground(skin.getDrawable("forgeKey"));
        Label l = label(text, "small", new Color(0xd9dde0ff));
        l.setFontScale(0.78f);
        k.add(l);
        return k;
    }

    private Label small(String text, Color color, float scale) {
        Label l = label(text, "small", color);
        l.setFontScale(scale);
        return l;
    }

    // 왼쪽: 내 무기 + 새로 만들기

    private Table buildList() {
        Table col = new Table(skin);
        col.setBackground(skin.getDrawable("forgeCol"));
        col.top();
        Table list = new Table();
        list.top();
        Table mineHead = new Table();
        mineHead.add(small("내 무기", Ui.GOLD, 0.85f)).left().expandX();
        mineHead.add(small(g.state.weapons.size() + "자루", Ui.DIM, 0.85f)).right();
        list.add(mineHead).growX().padBottom(6).row();
        boolean craftHeader = false;
        selectedCard = null;
        for (Item it : items) {
            if (it.craft != null && !craftHeader) {
                craftHeader = true;
                list.add(small("새로 만들기", Ui.GOLD, 0.85f)).left().padTop(10).padBottom(6).row();
            }
            Table card = card(it);
            list.add(card).width(268).height(62).padBottom(7).row();
        }
        listScroll = new ScrollPane(list);
        listScroll.setScrollingDisabled(true, false);
        listScroll.setFadeScrollBars(true);
        col.add(listScroll).grow();
        return col;
    }

    private Table card(Item it) {
        boolean sel = items.indexOf(it) == selectedIndex();
        Table card = new Table(skin);
        boolean awakened = it.weapon != null && it.weapon.awakened;
        card.setBackground(skin.getDrawable(sel ? "forgeCardSel" : awakened ? "forgeCardAwake" : "forgeCard"));
        card.setTouchable(Touchable.enabled);
        if (sel) selectedCard = card;

        Table icon = new Table(skin);
        icon.setBackground(skin.getDrawable("forgeIcon"));
        String defId = it.weapon != null ? it.weapon.defId : it.craft.id;
        icon.add(Ui.artImage(art.sword(defId, awakened))).size(38);
        card.add(icon).size(46).padRight(10);

        Table text = new Table();
        Label name;
        Table sub = new Table();
        if (it.weapon != null) {
            Weapon w = it.weapon;
            name = label(g.rules.weaponName(w), "small", awakened ? Ui.PURPLE : Ui.TEXT);
            sub.add(small(cardSub(w), Ui.DIM, 0.78f));
        } else {
            name = label(it.craft.name + " 만들기", "small", Ui.TEXT);
            sub.add(Ui.artImage(art.gem(OreType.valueOf(it.craft.ore)))).size(15).padRight(4);
            sub.add(small(String.valueOf(it.craft.craftCost), Ui.DIM, 0.78f));
        }
        text.add(name).left().row();
        text.add(sub).left().padTop(3);
        card.add(text).left().expandX();

        if (it.weapon != null) {
            String badge = null, style = null;
            Color c = null;
            if (it.weapon.uid == g.state.equippedUid) { badge = "장착 중"; style = "forgeBadgeEq"; c = Ui.GOLD; }
            else if (g.absorb.skillAbsorbBlockReason(g.state, it.weapon) == null) { badge = "흡수 가능"; style = "forgeBadgeSkill"; c = Ui.PURPLE; }
            if (badge != null) {
                Table b = new Table(skin);
                b.setBackground(skin.getDrawable(style));
                b.add(small(badge, c, 0.7f));
                card.add(b).top().right().padTop(2);
            }
        }
        card.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (motion.busy()) return;
                // 두 번 누르면 장착
                if (getTapCount() >= 2 && it.weapon != null) {
                    select(it);
                    equipSelected();
                } else select(it);
            }
        });
        return card;
    }

    private String cardSub(Weapon w) {
        WeaponDef d = g.balance.weapon(w.defId);
        String s = "공격력 " + g.rules.attack(g.state, w);
        if (!d.enhanceable) return d.holy && d.skill == null ? s + " · 강화 불가" : s;
        if (d.skill == null) return s;
        if (g.rules.skillUnlocked(w)) return s + " · " + d.skill.name + " 열림";
        return s + " · " + d.skill.name + " +" + g.balance.enhance.skillUnlockLevel;
    }

    // 가운데: 작업대 + 고른 검 + 최근 기록

    private Table buildCenter() {
        Table col = new Table(skin);
        col.setBackground(skin.getDrawable("forgeCol"));
        col.top();
        col.pad(1);
        Item it = selected();
        String id;
        boolean awakened;
        if (workingDef != null) { id = workingDef; awakened = workingAwakened; }
        else if (it != null && it.weapon != null) { id = it.weapon.defId; awakened = it.weapon.awakened; }
        else if (it != null) { id = it.craft.id; awakened = false; }
        else { id = g.state.equipped().defId; awakened = g.state.equipped().awakened; }
        col.add(new ForgeScene(art, motion, id, awakened)).width(438).height(289).row();

        String caption = resultText != null ? resultText
                : motion.busy() ? "불꽃을 다듬고, 한 번 더."
                : it != null && it.craft != null ? "새 검을 만듭니다."
                : act == Act.ABSORB ? "흡수한 스킬은 어떤 검을 들어도 쓸 수 있습니다."
                : act == Act.DISMANTLE ? "안 쓰는 검을 녹여 광석을 돌려받습니다."
                : "검을 올려두었습니다. Enter를 누르면 망치질을 시작합니다.";
        Label cap = label(caption, "small", resultText != null ? resultColor : Ui.DIM);
        if (resultText == null) cap.setFontScale(0.85f);
        cap.setAlignment(Align.center);
        cap.setWrap(true);
        col.add(cap).width(410).height(34).padTop(2).row();

        if (it != null) {
            Table name = new Table();
            if (it.weapon != null) {
                Weapon w = it.weapon;
                WeaponDef d = g.balance.weapon(w.defId);
                name.add(Ui.artImage(art.sword(w.defId, w.awakened))).size(60).padRight(14);
                Table t = new Table();
                t.add(label(g.rules.weaponName(w), "default", w.awakened ? Ui.PURPLE : Ui.TEXT)).left().row();
                String sub = "공격력 " + g.rules.attack(g.state, w);
                if (w.awakened && d.abilityName != null) sub += " · 각성 완료 · " + d.abilityName;
                sub += w.uid == g.state.equippedUid ? " · 장착 중" : " · Q 장착";
                t.add(small(sub, Ui.DIM, 0.85f)).left().padTop(4);
                name.add(t).left();
            } else {
                name.add(Ui.artImage(art.sword(it.craft.id, false))).size(60).padRight(14);
                Table t = new Table();
                t.add(label(it.craft.name, "default", Ui.TEXT)).left().row();
                t.add(small("공격력 " + it.craft.baseAttack, Ui.DIM, 0.85f)).left().padTop(4);
                name.add(t).left();
            }
            col.add(name).left().padLeft(18).padTop(4).row();
        }
        col.add().expandY().row();

        Table foot = new Table();
        foot.top().left();
        foot.defaults().left();
        if (it != null && it.craft != null) {
            OreType ore = OreType.valueOf(it.craft.ore);
            Table h = new Table();
            h.add(Ui.artImage(art.gem(ore))).size(16).padRight(4);
            h.add(small("얻는 곳", Ui.GOLD, 0.82f));
            foot.add(h).left().row();
            foot.add(small(dungeonOf(it.craft.ore) + " · 광물방 광맥, 보스 상자 "
                    + g.balance.reward.chestOreMin + "~" + g.balance.reward.chestOreMax + "개", Ui.DIM, 0.82f)).left().padTop(4).row();
        } else if (!g.state.enhanceLog.isEmpty()) {
            foot.add(small("최근 기록", Ui.GOLD, 0.82f)).left().padBottom(2).row();
            for (int i = 0; i < Math.min(3, g.state.enhanceLog.size()); i++)
                foot.add(small(g.state.enhanceLog.get(i), Ui.DIM, 0.82f)).left().padTop(3).row();
        }
        if (foot.hasChildren()) {
            col.add(new Image(skin.getDrawable("forgeLine"))).width(396).height(1).padBottom(8).row();
            col.add(foot).width(396).padBottom(14);
        }
        return col;
    }

    private String dungeonOf(String ore) {
        for (Balance.DungeonDef d : g.balance.dungeons) if (d.ore.equals(ore)) return d.name;
        return "";
    }

    // 오른쪽: 할 일

    private Table buildRight() {
        Table col = new Table(skin);
        col.setBackground(skin.getDrawable("forgeCol"));
        col.top();
        col.pad(14, 16, 16, 16);
        Item it = selected();
        if (it == null) return col;
        Table content = new Table();
        content.top();
        TextButton primary;
        if (it.craft != null) {
            primary = buildCraft(content, it.craft);
        } else {
            col.add(segments()).growX().height(46).padBottom(4).row();
            Weapon w = it.weapon;
            switch (act) {
                case ABSORB: primary = buildAbsorb(content, w); break;
                case DISMANTLE: primary = buildDismantle(content, w); break;
                default: primary = buildEnhance(content, w);
            }
        }
        col.add(content).width(364).top().row();
        col.add().expandY().row();
        if (primary != null) {
            primary.getLabelCell().expand(false, false).fill(false);
            primary.add(keycap("Enter")).padLeft(10);
            primary.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    if (!primary.isDisabled()) runPrimary();
                }
            });
            col.add(primary).growX().height(56);
        }
        return col;
    }

    private Table segments() {
        Table seg = new Table(skin);
        seg.setBackground(skin.getDrawable("forgeSeg"));
        for (Act a : Act.values()) {
            boolean on = a == act, enabled = actEnabled(a);
            Table cell = new Table(skin);
            if (on) cell.setBackground(skin.getDrawable("forgeSegOn"));
            cell.add(label(a.label, "small", on ? Ui.GOLD : enabled ? Ui.DIM : LINE_DIM));
            cell.setTouchable(Touchable.enabled);
            // 가상 테스트 · 마우스 막기 검사에서 찾을 수 있게 이름을 붙인다
            cell.setName("forgeAct:" + a.label);
            cell.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    if (enabled) setAct(a);
                }
            });
            seg.add(cell).grow().pad(2);
        }
        return seg;
    }

    private Table row(String key, Table value) {
        Table r = new Table();
        r.add(label(key, "small", Ui.DIM)).left().expandX();
        r.add(value).right();
        return r;
    }

    private Table row(String key, String value, Color color) {
        Table v = new Table();
        v.add(label(value, "small", color));
        return row(key, v);
    }

    private void addRow(Table content, Table r) {
        content.add(r).growX().height(36).row();
        content.add(new Image(skin.getDrawable("forgeLine"))).growX().height(1).row();
    }

    private Table oreAmount(OreType ore, String amount, Color amountColor, String rest) {
        Table v = new Table();
        v.add(Ui.artImage(art.gem(ore))).size(22).padRight(5);
        v.add(label(amount, "small", amountColor));
        if (rest != null) v.add(label(rest, "small", Ui.DIM));
        return v;
    }

    private Table attackLine(String before, String after, String note) {
        Table t = new Table();
        t.add(label("공격력", "small", Ui.DIM)).padRight(12).bottom().padBottom(4);
        if (before != null) {
            Label b = label(before, "large", Ui.TEXT);
            b.setFontScale(0.93f);
            t.add(b).bottom();
            t.add(label("→", "default", Ui.GOLD)).padLeft(12).padRight(12).bottom().padBottom(2);
        }
        Label a = label(after, "large", GREEN);
        a.setFontScale(0.93f);
        t.add(a).bottom();
        if (note != null) t.add(label(note, "small", Ui.DIM)).padLeft(12).bottom().padBottom(4);
        return t;
    }

    private Table warn(String first, OreType ore, String oreText, String second) {
        Table box = new Table(skin);
        box.setBackground(skin.getDrawable("forgeWarn"));
        Table l1 = new Table();
        l1.add(small(first, WARN_TEXT, 0.85f));
        if (ore != null) {
            l1.add(Ui.artImage(art.gem(ore))).size(16).padLeft(4).padRight(2);
            l1.add(small(oreText, WARN_TEXT, 0.85f));
        }
        box.add(l1).left().expandX().row();
        if (second != null) box.add(small(second, Ui.DIM, 0.85f)).left().padTop(4);
        return box;
    }

    private TextButton buildEnhance(Table content, Weapon w) {
        GameState s = g.state;
        WeaponDef d = g.balance.weapon(w.defId);
        EnhanceService es = g.enhance;
        if (!d.enhanceable || es.isMax(w)) {
            String why = !d.enhanceable ? (d.holy ? "성검은 강화할 수 없습니다." : "강화할 수 없는 검입니다.")
                    : "각성 완료 · 더 강화할 수 없습니다.";
            Label l = label(why, "small", Ui.DIM);
            l.setWrap(true);
            content.add(l).width(360).left().padTop(18).row();
            TextButton b = new TextButton("강화하기", skin, "roundFocused");
            b.setDisabled(true);
            return b;
        }
        OreType ore = es.oreOf(w);
        int cost = es.cost(w.level);
        int atk = g.rules.attack(s, w);
        content.add(attackLine(String.valueOf(atk), String.valueOf(atk + d.perLevel), null)).left().padTop(14).padBottom(6).row();
        boolean enough = s.ore(ore) >= cost;
        addRow(content, row("재료", oreAmount(ore, String.valueOf(cost), enough ? Ui.GOLD : Ui.ORANGE, " / " + s.ore(ore))));
        Table step = new Table();
        step.add(label("+" + w.level + " → +" + (w.level + 1), "small", Ui.TEXT));
        step.add(label("  ·  각성까지 " + (g.balance.enhance.maxLevel - w.level) + "단계", "small", Ui.PURPLE));
        addRow(content, row("단계", step));

        Odds o = es.odds(s, w.level);
        content.add(new OddsBar(o)).growX().height(22).padTop(14).padBottom(8).row();
        Table legend = new Table();
        legendItem(legend, "성공", o.success, PROB_SUCCESS, Ui.BLUE);
        legendItem(legend, "유지", o.keep, PROB_KEEP, Ui.GREY);
        legendItem(legend, "하락", o.down, PROB_DOWN, Ui.ORANGE);
        legendItem(legend, "파괴", o.destroy, PROB_DESTROY, Ui.RED);
        content.add(legend).width(364).left().row();

        boolean risk = es.canDestroy(s, w);
        if (risk) {
            com.yongsa.config.Balance.MasteryCfg m = g.balance.enhance.mastery;
            String second = s.masteryLevel < m.destroyZeroLevel ? "숙련도 Lv " + m.destroyZeroLevel + "부터 파괴되지 않습니다." : null;
            content.add(warn("파괴되면 검이 사라지고", ore, es.destroyRefund(w) + "만 돌려받습니다.", second))
                    .growX().padTop(12).row();
        }
        TextButton b = new TextButton("강화하기", skin, risk ? "roundDanger" : "roundFocused");
        b.setDisabled(motion.busy() || !enough);
        return b;
    }

    private void legendItem(Table legend, String name, double p, Color bar, Color text) {
        Table t = new Table();
        Image dot = new Image(art.white());
        dot.setColor(p > 0 ? bar : Ui.GREY);
        t.add(dot).size(10).padRight(6);
        t.add(small(name + " " + ProbBar.format(p) + "%", p > 0 ? text : Ui.DIM, 0.8f));
        legend.add(t).width(91).left();
    }

    private TextButton buildAbsorb(Table content, Weapon w) {
        WeaponDef d = g.balance.weapon(w.defId);
        Balance.SkillDef sk = d.skill;
        Table box = new Table(skin);
        box.setBackground(skin.getDrawable("forgeSkill"));
        box.add(label("「" + sk.name + "」" + (w.awakened ? " 각성" : ""), "default", Ui.PURPLE)).left().row();
        Label desc = small(sk.desc, new Color(0xd8d0e6ff), 0.85f);
        desc.setWrap(true);
        box.add(desc).width(330).left().padTop(8).row();
        if (w.awakened && sk.awakenedDesc != null) {
            Label ad = small(sk.awakenedDesc, Ui.PURPLE, 0.8f);
            ad.setWrap(true);
            box.add(ad).width(330).left().padTop(8).row();
        }
        content.add(box).growX().padTop(14).padBottom(6).row();
        addRow(content, row("흡수하면", "어떤 검을 들어도 " + sk.name + " 사용", Ui.TEXT));
        addRow(content, row("공격력", "오르지 않음", Ui.TEXT));
        content.add(warn("이 검은 사라지고 되돌릴 수 없습니다.", null, null, null)).growX().padTop(12).row();
        TextButton b = new TextButton("흡수하기", skin, "roundPurple");
        b.setDisabled(motion.busy());
        return b;
    }

    private TextButton buildDismantle(Table content, Weapon w) {
        OreType ore = OreType.valueOf(g.balance.weapon(w.defId).ore);
        content.add().height(8).row();
        addRow(content, row("돌려받는 광석", oreAmount(ore, "+" + g.balance.enhance.dismantleRefund, Ui.GOLD, null)));
        addRow(content, row("가진 광석", oreAmount(ore, String.valueOf(g.state.ore(ore)), Ui.TEXT, null)));
        content.add(warn("이 검은 사라지고 되돌릴 수 없습니다.", null, null, null)).growX().padTop(12).row();
        TextButton b = new TextButton("분해하기", skin, "roundDanger");
        b.setDisabled(motion.busy());
        return b;
    }

    private TextButton buildCraft(Table content, WeaponDef d) {
        GameState s = g.state;
        OreType ore = OreType.valueOf(d.ore);
        int diff = d.baseAttack - g.rules.attack(s, s.equipped());
        content.add(attackLine(null, String.valueOf(d.baseAttack),
                "지금 검보다 " + (diff >= 0 ? "+" : "") + diff)).left().padTop(14).padBottom(6).row();
        boolean enough = s.ore(ore) >= d.craftCost;
        addRow(content, row("재료", oreAmount(ore, String.valueOf(d.craftCost), enough ? Ui.GOLD : Ui.ORANGE, " / " + s.ore(ore))));
        addRow(content, row("강화마다", "공격력 +" + d.perLevel, Ui.TEXT));
        if (d.awakenedName != null)
            addRow(content, row("+" + g.balance.enhance.maxLevel + " 각성",
                    d.awakenedName + (d.skill != null ? " · " + d.skill.name + " 열림" : ""), Ui.PURPLE));
        TextButton b = new TextButton("제작하기", skin, "roundFocused");
        b.setDisabled(motion.busy() || g.forge.craftBlockReason(s, d) != null);
        return b;
    }

    /** 성공 · 유지 · 하락 · 파괴를 한 줄 막대로. */
    private final class OddsBar extends Widget {
        private final Odds o;
        OddsBar(Odds o) { this.o = o; }

        @Override public float getPrefHeight() { return 22; }

        @Override public void draw(Batch batch, float parentAlpha) {
            TextureRegion white = art.white();
            float x = getX(), y = getY(), w = getWidth(), h = getHeight();
            batch.setColor(0.11f, 0.13f, 0.17f, parentAlpha);
            batch.draw(white, x, y, w, h);
            double total = Math.max(1e-9, o.success + o.keep + o.down + o.destroy);
            double[] parts = {o.success, o.keep, o.down, o.destroy};
            Color[] colors = {PROB_SUCCESS, PROB_KEEP, PROB_DOWN, PROB_DESTROY};
            float at = x;
            for (int i = 0; i < parts.length; i++) {
                float pw = (float) (w * parts[i] / total);
                if (pw <= 0) continue;
                batch.setColor(colors[i].r, colors[i].g, colors[i].b, parentAlpha);
                batch.draw(white, at, y, pw, h);
                at += pw;
            }
            batch.setColor(Color.WHITE);
        }
    }

    // ───────────────────────── 실행

    private void craft(WeaponDef d) {
        if (motion.busy() || g.forge.craftBlockReason(g.state, d) != null) return;
        startHammer(d.id, false, () -> {
            Weapon w = g.forge.craft(g.state, d);
            screen.game.sfx.play("craft");
            resultText = d.name + "을 만들었다!" + (w.uid == g.state.equippedUid ? " 바로 장착했다." : "");
            resultColor = Ui.BLUE;
            motion.result(EnhanceService.Outcome.SUCCESS, false, 0.9f / speed());
            g.onWeaponsChanged();
            screen.save(false);
            // 만든 검을 고른다
            selUid = w.uid;
            selCraft = null;
            act = firstEnabledAct();
            rebuild();
        });
    }

    private void confirmRisk(Weapon w) {
        EnhanceService es = g.enhance;
        Odds o = es.odds(g.state, w.level);
        OreType ore = es.oreOf(w);
        ConfirmDialog.Line[] lines = {
                new ConfirmDialog.Line(g.rules.weaponName(w) + "을 강화합니다.", null),
                new ConfirmDialog.Line("성공 " + ProbBar.format(o.success)
                        + "%  ·  유지 " + ProbBar.format(o.keep)
                        + (o.down > 0 ? "%  ·  하락 " + ProbBar.format(o.down) : "") + "%", Ui.TEXT),
                new ConfirmDialog.Line("파괴 " + ProbBar.format(o.destroy) + "%", Ui.RED),
                new ConfirmDialog.Line("파괴되면 무기가 사라지고 " + ore.displayName + " " + es.destroyRefund(w) + "개만 돌려받습니다.", Ui.DIM),
        };
        screen.modals.push(new ConfirmDialog(skin, "파괴될 수 있습니다", lines, "그래도 강화", true, () -> doEnhance(w)));
    }

    private void doEnhance(Weapon w) {
        if (motion.busy()) return;
        if (g.enhance.blockReason(g.state, w) != null) return;
        resultText = null;
        startHammer(w.defId, w.awakened, () -> {
            EnhanceService.Result r = g.enhance.enhance(g.state, w, g.random);
            motion.result(r.outcome, r.awakened, (r.awakened ? g.balance.enhance.awakenSeconds : 1.05f) / speed());
            screen.save(false); // 강화 직후 자동 저장: 꺼도 결과를 되돌릴 수 없다 (FR-대장간-15)
            switch (r.outcome) {
                case SUCCESS:
                    resultText = "성공! +" + r.levelBefore + " → +" + r.levelAfter;
                    resultColor = Ui.BLUE;
                    screen.game.sfx.play(r.awakened ? "awaken" : "success");
                    if (r.awakened) resultText += " · " + g.balance.weapon(w.defId).awakenedName + " 각성!";
                    break;
                case KEEP:
                    resultText = "유지. 단계가 그대로다";
                    resultColor = Ui.GREY;
                    screen.game.sfx.play("keep");
                    break;
                case DOWN:
                    resultText = "하락… +" + r.levelBefore + " → +" + r.levelAfter;
                    resultColor = Ui.ORANGE;
                    screen.game.sfx.play("down");
                    break;
                default:
                    resultText = "용사가 실수했다! 무기가 파괴되었다";
                    resultColor = Ui.RED;
                    screen.game.sfx.play("destroy");
                    screen.hud.toast(r.refundOre.displayName + " " + r.refund + "개를 돌려받았다. 봉인된 성검을 들었다");
            }
            if (r.masteryGained > 0) resultText += "   (숙련 +" + r.masteryGained + ")";
            if (r.masteryLeveledUp()) {
                screen.hud.toast("강화 숙련도가 올랐다! Lv " + r.masteryAfter + masteryNote(r.masteryAfter));
                screen.game.sfx.play("awaken");
            }
            g.onWeaponsChanged();
            if (r.awakened || !actEnabled(act)) act = firstEnabledAct();
            rebuild();
        });
    }

    /** 숙련도 레벨이 올랐을 때 눈에 띄는 변화 한마디. */
    private String masteryNote(int lv) {
        com.yongsa.config.Balance.MasteryCfg m = g.balance.enhance.mastery;
        if (lv == m.destroyZeroLevel) return " · 이제 무기가 파괴되지 않는다";
        if (lv == m.downZeroLevel) return " · 이제 단계가 떨어지지 않는다";
        if (lv >= m.maxLevel) return " · 마스터! 성공 확률 +" + ProbBar.format(g.enhance.successBonus(lv)) + "%p";
        if (g.enhance.successBonus(lv) > g.enhance.successBonus(lv - 1)) return " · 성공 확률 +" + ProbBar.format(g.enhance.successBonus(lv)) + "%p";
        return " · 하락·파괴 확률이 줄었다";
    }

    private void confirmDismantle(Weapon w) {
        OreType ore = OreType.valueOf(g.balance.weapon(w.defId).ore);
        // 분해는 되돌릴 수 없으므로 흡수·파괴 위험과 같은 빨간 확인창
        screen.modals.push(new ConfirmDialog(skin, "분해",
                new ConfirmDialog.Line[]{new ConfirmDialog.Line(g.rules.weaponName(w) + "을 분해합니다. 되돌릴 수 없습니다.", null)},
                "분해하기", true, () -> {
                    int n = g.forge.dismantle(g.state, w);
                    resultText = ore.displayName + " " + n + "개를 돌려받았다";
                    resultColor = Ui.BLUE;
                    screen.game.sfx.play("down");
                    screen.save(false);
                    rebuild();
                }));
    }

    private void confirmAbsorb(Weapon w) {
        Balance.SkillDef sk = g.balance.weapon(w.defId).skill;
        ConfirmDialog.Line[] lines = {
                new ConfirmDialog.Line(g.rules.weaponName(w) + "을 흡수합니다.", null),
                new ConfirmDialog.Line("검은 사라지고, 「" + sk.name + "」" + (w.awakened ? " (각성)" : "") + "을 어떤 검을 들어도 쓸 수 있다.", Ui.PURPLE),
                new ConfirmDialog.Line("공격력은 오르지 않는다. 되돌릴 수 없다.", Ui.DIM),
        };
        screen.modals.push(new ConfirmDialog(skin, "검 흡수", lines, "흡수하기", true, () -> {
            g.absorb.absorbSkill(g.state, w);
            g.onWeaponsChanged();
            resultText = "「" + sk.name + "」을 익혔다! 이제 어떤 검을 들어도 쓸 수 있다";
            resultColor = Ui.PURPLE;
            screen.game.sfx.play("awaken");
            screen.save(false);
            rebuild();
        }));
    }

    // ───────────────────────── 연출

    private void startHammer(String weaponId, boolean awakened, Runnable then) {
        if (motion.busy()) return;
        workingDef = weaponId;
        workingAwakened = awakened;
        resultText = null;
        playedStrikes = 0;
        motion.start(Math.max(1.35f, g.balance.enhance.hammerSeconds) / speed());
        afterHammer = then;
        rebuild();
    }

    @Override
    public void rebuild() {
        clearChildren();
        nav.clear();
        build();
        scrollPending = true;
    }
}
