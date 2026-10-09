package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
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

/**
 * 대장간 창 (UI-04). 탭 3개: 제작, 강화, 분해.
 * 강화 탭: 검 그림, 비용과 보유량, 네 결과 확률(한 번 시도 기준), 강화 후 공격력,
 * 각성까지 남은 단계, 최근 기록. 파괴 위험이 있으면 빨간 버튼과 확인창 (FR-대장간-10, 11).
 */
public class ForgeWindow extends Modal {
    private enum Tab { CRAFT, ENHANCE, DISMANTLE }

    private final GameScreen screen;
    private final GameSession g;
    private final Art art;
    private Tab tab = Tab.ENHANCE;

    /** 망치질 연출 중이면 0보다 크다. 그동안 입력은 무시한다 (FR-대장간-13). */
    private float hammerT;
    private float awakenT;
    private Runnable afterHammer;
    private Label hammerLabel;
    private String resultText;
    private Color resultColor = Ui.TEXT;

    public ForgeWindow(GameScreen screen) {
        super(screen.game.skin);
        this.screen = screen;
        this.g = screen.session;
        this.art = screen.game.art;
        if (g.state.equipped() == null || !g.balance.weapon(g.state.equipped().defId).enhanceable) {
            boolean anyCraftable = false;
            for (WeaponDef d : g.balance.weapons) if (d.enhanceable && g.forge.craftBlockReason(g.state, d) == null) anyCraftable = true;
            if (anyCraftable || g.state.weapons.size() == 1) tab = Tab.CRAFT;
        }
    }

    private float speed() {
        return screen.game.settings.effectSpeed;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (hammerT > 0 || awakenT > 0) return true;
        return super.keyDown(keycode);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (hammerT > 0) {
            hammerT -= delta;
            if (hammerLabel != null) {
                int beat = (int) ((g.balance.enhance.hammerSeconds / speed() - hammerT) * 6);
                hammerLabel.setText(beat % 2 == 0 ? "탕!" : "  탕!");
            }
            if (hammerT <= 0 && afterHammer != null) {
                Runnable r = afterHammer;
                afterHammer = null;
                r.run();
            }
        } else if (awakenT > 0) {
            awakenT -= delta;
            if (awakenT <= 0) rebuild();
        }
    }

    @Override
    public void build() {
        Table p = panel();
        Table head = new Table();
        head.add(label("대장간", "large", Ui.GOLD)).padRight(40);
        tabButton(head, "제작", Tab.CRAFT);
        tabButton(head, "강화", Tab.ENHANCE);
        tabButton(head, "분해", Tab.DISMANTLE);
        p.add(head).left().padBottom(16).row();

        Table body = new Table();
        switch (tab) {
            case CRAFT: buildCraft(body); break;
            case ENHANCE: buildEnhance(body); break;
            default: buildDismantle(body);
        }
        p.add(body).width(900).height(470).top().row();
        Table foot = new Table();
        foot.add(nav.add("닫기", this::close)).width(180).height(46);
        foot.add(label("   Esc 닫기 · 방향키/Tab 이동 · Enter 선택", "small", Ui.DIM));
        p.add(foot).padTop(8);
    }

    private void tabButton(Table head, String text, Tab t) {
        TextButton b = nav.add(text, () -> {
            tab = t;
            resultText = null;
            clearChildren();
            nav.clear();
            build();
            nav.setFocusIndex(t.ordinal());
        });
        if (tab == t) b.getLabel().setColor(Ui.GOLD);
        head.add(b).width(120).height(46).padRight(8);
    }

    // ───────────────────────── 제작

    private void buildCraft(Table body) {
        GameState s = g.state;
        body.top();
        body.add(label("그 던전의 광물로 그 던전의 무기를 만든다. 만든 무기가 더 세면 바로 장착한다.", "small", Ui.DIM))
                .colspan(4).left().padBottom(14).row();
        for (WeaponDef d : g.balance.weapons) {
            if (!d.enhanceable) continue;
            OreType ore = OreType.valueOf(d.ore);
            String dungeon = dungeonOf(d.ore);
            body.add(new Image(art.sword(d.id, false))).size(56).padRight(12);
            Table info = new Table();
            info.add(label(d.name + "  (" + dungeon + ")", "default", Ui.TEXT)).left().row();
            info.add(label("기본 공격력 " + d.baseAttack + "  ·  강화 1단계당 +" + d.perLevel, "small", Ui.DIM)).left().row();
            body.add(info).width(380).left();
            boolean enough = s.ore(ore) >= d.craftCost;
            body.add(label(ore.displayName + " " + d.craftCost + "개 (보유 " + s.ore(ore) + ")", "default",
                    enough ? Ui.TEXT : Ui.ORANGE)).width(260).left();
            TextButton b = nav.add("제작", () -> craft(d));
            b.setDisabled(!enough);
            body.add(b).width(120).height(46).padBottom(12).row();
        }
        if (resultText != null) body.add(label(resultText, "default", resultColor)).colspan(4).padTop(14).row();
    }

    private String dungeonOf(String ore) {
        for (Balance.DungeonDef d : g.balance.dungeons) {
            if (d.ore.equals(ore)) return com.yongsa.model.NextTask.dungeonWord(d.id) + " 던전";
        }
        return "";
    }

    private void craft(WeaponDef d) {
        if (g.forge.craftBlockReason(g.state, d) != null) return;
        startHammer(() -> {
            Weapon w = g.forge.craft(g.state, d);
            screen.game.sfx.play("craft");
            resultText = d.name + "을 만들었다!" + (w.uid == g.state.equippedUid ? " 바로 장착했다." : "");
            resultColor = Ui.BLUE;
            screen.save(false);
            rebuild();
        });
    }

    // ───────────────────────── 강화

    private void buildEnhance(Table body) {
        GameState s = g.state;
        Weapon w = s.equipped();
        WeaponDef d = g.balance.weapon(w.defId);
        EnhanceService es = g.enhance;

        Table left = new Table();
        Image pic = new Image(art.sword(w.defId, w.awakened));
        left.add(pic).size(150).padBottom(8).row();
        Label name = label(g.rules.weaponName(w), "large", w.awakened || "holy_awakened".equals(w.defId) ? Ui.GOLD : Ui.TEXT);
        left.add(name).padBottom(4).row();
        left.add(label("공격력 " + g.rules.attack(s, w), "default", Ui.TEXT)).padBottom(10).row();
        if (d.enhanceable) {
            if (w.awakened) {
                left.add(label("각성 완료: " + d.abilityName, "default", Ui.GOLD)).row();
                Label desc = label(d.abilityDesc, "small", Ui.GOLD);
                desc.setWrap(true);
                desc.setAlignment(Align.center);
                left.add(desc).width(280).row();
            } else {
                left.add(label("각성까지 " + (g.balance.enhance.maxLevel - w.level) + "단계", "default", Ui.PURPLE)).row();
                left.add(label("각성한 모습: ???", "small", Ui.DIM)).row();
            }
        }
        if (hammerT > 0) {
            hammerLabel = label("탕!", "title", Ui.GOLD);
            left.add(hammerLabel).padTop(10).row();
        } else if (awakenT > 0) {
            left.add(label(d.awakenedName + "(으)로 각성했다!", "large", Ui.GOLD)).padTop(10).row();
        }
        body.add(left).width(320).top().padRight(20);

        Table right = new Table();
        right.top().left();
        if (!d.enhanceable) {
            Label msg = label(d.holy
                    ? ("holy_sealed".equals(d.id)
                    ? "봉인된 성검은 강화할 수 없다.\n던전 광물로 무기를 만들어 장착하면 그 무기를 강화할 수 있다."
                    : "깨어난 성검은 용사가 흡수한 힘으로 강해진다. 강화할 수 없다.")
                    : "강화할 수 없는 무기다.", "default", Ui.DIM);
            msg.setWrap(true);
            right.add(msg).width(520).left().padBottom(16).row();
        } else if (es.isMax(w)) {
            right.add(label("최대 단계(+" + g.balance.enhance.maxLevel + ")에 닿아 각성했다. 더 강화하지 않는다.", "default", Ui.GOLD))
                    .left().padBottom(14).row();
            String reason = g.absorb.weaponAbsorbBlockReason(s, w);
            Label hint = label("각성한 무기의 힘을 용사에게 흡수시키면 무기는 사라지지만, " + d.abilityName
                    + "과 모든 무기 공격력 +10%가 용사에게 남는다.", "small", Ui.DIM);
            hint.setWrap(true);
            right.add(hint).width(520).left().padBottom(10).row();
            TextButton absorb = nav.add("용사에게 힘 흡수", () -> confirmAbsorb(w));
            absorb.setDisabled(reason != null);
            right.add(absorb).width(260).height(50).left().padBottom(12).row();
        } else {
            OreType ore = es.oreOf(w);
            int cost = es.cost(w.level);
            boolean enough = s.ore(ore) >= cost;
            right.add(label("비용: " + ore.displayName + " " + cost + "개  (보유 " + s.ore(ore) + "개)", "default",
                    enough ? Ui.TEXT : Ui.ORANGE)).left().padBottom(10).row();
            Odds o = es.odds(w.level);
            right.add(label("한 번 시도할 때 (+" + w.level + " → +" + (w.level + 1) + ")", "small", Ui.DIM)).left().padBottom(4).row();
            right.add(new ProbBar("성공", o.success, Ui.BLUE, screen.game.fonts.small, art.white())).width(520).left().row();
            right.add(new ProbBar("유지", o.keep, Ui.GREY, screen.game.fonts.small, art.white())).width(520).left().row();
            right.add(new ProbBar("하락", o.down, Ui.ORANGE, screen.game.fonts.small, art.white())).width(520).left().row();
            right.add(new ProbBar("파괴", o.destroy, Ui.RED, screen.game.fonts.small, art.white())).width(520).left().padBottom(10).row();
            int after = g.rules.attack(s, w) + Math.round(d.perLevel * (1f + s.imprints.size() * g.balance.absorb.attackBonusPerAwakenedWeapon));
            right.add(label("성공하면 공격력 " + g.rules.attack(s, w) + " → " + after, "default", Ui.TEXT)).left().padBottom(4).row();
            boolean risk = es.canDestroy(w);
            if (risk) {
                right.add(label("파괴되면 무기가 사라지고 " + ore.displayName + " " + es.destroyRefund(w) + "개를 돌려받는다",
                        "small", Ui.RED)).left().padBottom(8).row();
            } else {
                right.add(label("+6부터는 파괴될 수 있다", "small", Ui.DIM)).left().padBottom(8).row();
            }
            TextButton b = nav.add(risk ? "강화하기 (파괴 위험)" : "강화하기", () -> {
                if (es.canDestroy(w)) confirmRisk(w);
                else doEnhance(w);
            }, risk);
            b.setDisabled(!enough || hammerT > 0);
            right.add(b).width(300).height(52).left().padBottom(10).row();
        }
        if (resultText != null) right.add(label(resultText, "large", resultColor)).left().padBottom(8).row();

        // 다른 무기 장착 (장착한 무기만 강화한다)
        if (s.weapons.size() > 1) {
            Table swap = new Table();
            swap.add(label("장착 바꾸기:", "small", Ui.DIM)).padRight(8);
            for (Weapon other : s.weapons) {
                if (other.uid == s.equippedUid) continue;
                swap.add(nav.add(g.rules.weaponName(other), () -> {
                    g.forge.equip(s, other);
                    resultText = null;
                    rebuild();
                })).height(40).padRight(6);
            }
            right.add(swap).left().padBottom(8).row();
        }
        if (!s.enhanceLog.isEmpty()) {
            right.add(label("최근 기록", "small", Ui.DIM)).left().row();
            for (int i = 0; i < Math.min(3, s.enhanceLog.size()); i++) {
                right.add(label(s.enhanceLog.get(i), "small", Ui.TEXT)).left().row();
            }
        }
        body.add(right).width(540).top();
    }

    private void confirmRisk(Weapon w) {
        EnhanceService es = g.enhance;
        Odds o = es.odds(w.level);
        OreType ore = es.oreOf(w);
        ConfirmDialog.Line[] lines = {
                new ConfirmDialog.Line(g.rules.weaponName(w) + "을 강화합니다.", null),
                new ConfirmDialog.Line("성공 " + ProbBar.format(o.success) + "%  ·  유지 " + ProbBar.format(o.keep)
                        + "%  ·  하락 " + ProbBar.format(o.down) + "%", Ui.TEXT),
                new ConfirmDialog.Line("파괴 " + ProbBar.format(o.destroy) + "%", Ui.RED),
                new ConfirmDialog.Line("파괴되면 무기가 사라지고 " + ore.displayName + " " + es.destroyRefund(w) + "개만 돌려받습니다.", Ui.DIM),
        };
        screen.modals.push(new ConfirmDialog(skin, "파괴될 수 있습니다", lines, "그래도 강화", true, () -> doEnhance(w)));
    }

    private void doEnhance(Weapon w) {
        if (g.enhance.blockReason(g.state, w) != null) return;
        resultText = null;
        startHammer(() -> {
            EnhanceService.Result r = g.enhance.enhance(g.state, w, g.random);
            screen.save(false); // 강화 직후 자동 저장: 꺼도 결과를 되돌릴 수 없다 (FR-대장간-15)
            switch (r.outcome) {
                case SUCCESS:
                    resultText = "성공! +" + r.levelBefore + " → +" + r.levelAfter;
                    resultColor = Ui.BLUE;
                    screen.game.sfx.play(r.awakened ? "awaken" : "success");
                    if (r.awakened) awakenT = g.balance.enhance.awakenSeconds / speed();
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
            g.onWeaponsChanged();
            rebuild();
        });
    }

    private void confirmAbsorb(Weapon w) {
        WeaponDef d = g.balance.weapon(w.defId);
        ConfirmDialog.Line[] lines = {
                new ConfirmDialog.Line(g.rules.weaponName(w) + "은 사라지고 그 힘이 용사에게 남습니다.", null),
                new ConfirmDialog.Line("남는 힘: " + d.abilityName + " (" + d.abilityDesc + ")", Ui.GOLD),
                new ConfirmDialog.Line("모든 무기 공격력 +10%", Ui.GOLD),
        };
        screen.modals.push(new ConfirmDialog(skin, "힘 흡수", lines, "흡수하기", false, () -> {
            g.absorb.absorbWeapon(g.state, w);
            g.onWeaponsChanged();
            screen.game.sfx.play("awaken");
            resultText = d.abilityName + "의 힘이 용사에게 스며들었다";
            resultColor = Ui.GOLD;
            screen.save(false);
            rebuild();
        }));
    }

    // ───────────────────────── 분해

    private void buildDismantle(Table body) {
        GameState s = g.state;
        body.top();
        body.add(label("안 쓰는 무기를 분해하면 광물 " + g.balance.enhance.dismantleRefund
                + "개를 돌려받는다. 장착한 무기와 성검은 분해할 수 없다.", "small", Ui.DIM)).colspan(3).left().padBottom(14).row();
        boolean any = false;
        for (Weapon w : s.weapons) {
            if (g.forge.dismantleBlockReason(s, w) != null) continue;
            any = true;
            OreType ore = OreType.valueOf(g.balance.weapon(w.defId).ore);
            body.add(new Image(art.sword(w.defId, w.awakened))).size(48).padRight(12);
            body.add(label(g.rules.weaponName(w) + "  →  " + ore.displayName + " " + g.balance.enhance.dismantleRefund + "개"))
                    .width(520).left();
            body.add(nav.add("분해", () -> screen.modals.push(ConfirmDialog.simple(skin, "분해",
                    g.rules.weaponName(w) + "을 분해합니다. 되돌릴 수 없습니다.", "분해하기", () -> {
                        int n = g.forge.dismantle(s, w);
                        resultText = ore.displayName + " " + n + "개를 돌려받았다";
                        resultColor = Ui.BLUE;
                        screen.game.sfx.play("down");
                        screen.save(false);
                        rebuild();
                    })))).width(120).height(46).padBottom(10).row();
        }
        if (!any) body.add(label("분해할 수 있는 무기가 없다", "default", Ui.DIM)).colspan(3).padTop(30).row();
        if (resultText != null) body.add(label(resultText, "default", resultColor)).colspan(3).padTop(14).row();
    }

    // ───────────────────────── 연출

    private void startHammer(Runnable then) {
        hammerT = g.balance.enhance.hammerSeconds / speed();
        afterHammer = then;
        screen.game.sfx.play("mine");
        rebuild();
    }

    @Override
    public void rebuild() {
        int f = nav.focusIndex();
        clearChildren();
        nav.clear();
        build();
        nav.setFocusIndex(f);
    }
}
