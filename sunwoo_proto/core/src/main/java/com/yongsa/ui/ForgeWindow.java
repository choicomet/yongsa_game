package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;
import com.yongsa.config.WeaponDef;
import com.yongsa.model.AwakenAbility;
import com.yongsa.model.EnhanceOdds;
import com.yongsa.model.EnhancePreview;
import com.yongsa.model.EnhanceResult;
import com.yongsa.model.GameSession;
import com.yongsa.model.Weapon;
import com.yongsa.render.Assets;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 대장간 창 (UI-04): 탭 3개 (제작, 강화, 분해).
 * 강화 탭: 검 그림, 비용과 보유량, 네 결과 확률 막대(한 번 시도 기준), 강화 후 공격력, 강화 버튼,
 * 각성까지 남은 단계, 최근 기록. 파괴 위험이 있으면 빨간 버튼 + 확인창 (FR-대장간-10~13, 16~20).
 */
public class ForgeWindow extends GameWindow {

    private enum Tab { CRAFT, ENHANCE, DISMANTLE }

    private final GameSession s;
    private final Assets assets;
    private final Consumer<GameWindow> openDialog;
    private final Supplier<Float> effectSpeed;
    private final Consumer<String> sound;

    private Tab tab = Tab.ENHANCE;
    private final Table tabs = new Table();
    private final Table content = new Table();
    private final Label result;
    private final Deque<String[]> log = new ArrayDeque<>();
    /** 망치질·각성 연출 중에는 입력을 무시한다. */
    private float busy;
    private final Image flash;
    private final TextButton closeBtn;

    public ForgeWindow(Skin skin, GameSession s, Assets assets, Consumer<GameWindow> openDialog,
                       Supplier<Float> effectSpeed, Consumer<String> sound) {
        super("대장간", skin);
        this.s = s;
        this.assets = assets;
        this.openDialog = openDialog;
        this.effectSpeed = effectSpeed;
        this.sound = sound;
        result = new Label("", skin, "large");
        result.setAlignment(Align.center);
        result.setWrap(true);
        flash = new Image(skin.newDrawable("white", new Color(1f, 0.95f, 0.75f, 1f)));
        flash.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);

        add(tabs).left().padBottom(10).row();
        add(content).width(1000).height(440).row();
        add(result).width(1000).height(44).padTop(2).row();
        closeBtn = button("닫기 (Esc)", this::close);
        add(closeBtn).right().width(170).height(46);
    }

    @Override
    public void refresh() {
        nav.clear();
        tabs.clear();
        for (Tab t : Tab.values()) {
            String name = switch (t) {
                case CRAFT -> "제작";
                case ENHANCE -> "강화";
                case DISMANTLE -> "분해";
            };
            TextButton b = button(name, "tab", () -> {
                if (busy > 0) return;
                tab = t;
                result.setText("");
                refresh();
            });
            b.setChecked(t == tab);
            tabs.add(b).width(150).height(48).padRight(8);
        }
        content.clear();
        content.top();
        switch (tab) {
            case CRAFT -> buildCraft();
            case ENHANCE -> buildEnhance();
            case DISMANTLE -> buildDismantle();
        }
        nav.add(closeBtn);
        pack();
        centerOnStage();
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (busy > 0) busy -= delta;
    }

    @Override
    public boolean keyDown(int key) {
        if (busy > 0) return true; // 연출 중 입력 무시 (FR-대장간-13)
        return super.keyDown(key);
    }

    // ================================================================== 제작

    private void buildCraft() {
        Table list = new Table();
        list.top().left();
        list.add(new Label("던전 광물로 그 던전의 무기를 만든다. 다음 던전의 무기는 +0으로도 앞 무기 +10보다 조금 세다.", skin, "small"))
                .left().colspan(5).padBottom(12).row();
        for (WeaponDef d : s.balance.weapons) {
            if (!d.isSword()) continue;
            String ore = s.balance.ore(d.ore).name;
            int have = s.inventory.ore(d.ore);
            boolean open = s.progress.isOpen(s.balance.dungeon(s.balance.ore(d.ore).dungeon));
            long owned = s.inventory.weapons().stream().filter(w -> w.def() == d).count();
            Table row = new Table(skin);
            row.setBackground(skin.getDrawable("panel-light"));
            row.add(new Image(assets.region(d.icon))).size(56).padRight(14);
            Table info = new Table();
            info.add(new Label(d.name + (owned > 0 ? "   (가진 것 " + owned + "자루)" : ""), skin, "large")).left().row();
            info.add(new Label("공격력 " + d.attack + "  (강화 1단계당 +" + d.attackPerLevel + ")", skin, "small")).left();
            row.add(info).width(420).left();
            Label cost = new Label(ore + " " + d.craftCost + "개   (보유 " + have + ")", skin);
            if (have < d.craftCost) cost.setColor(UiSkin.SHORT);
            row.add(cost).width(250).left();
            TextButton make = button(open ? "만들기" : "던전 봉인됨", () -> craft(d));
            make.setDisabled(!s.craftService.canCraft(d, s.inventory));
            row.add(make).width(150).height(48);
            list.add(row).width(960).padBottom(10).row();
        }
        content.add(list).grow().top();
    }

    private void craft(WeaponDef d) {
        Weapon w = s.craft(d);
        if (w == null) return;
        sound.accept("craft");
        String msg = com.yongsa.model.Josa.eul(d.name) + " 만들었다!";
        if (s.inventory.equipped() != w) {
            ConfirmDialog ask = new ConfirmDialog(skin, "제작 완료", msg + "\n바로 장착할까?", "장착", false,
                    () -> { s.equip(w); refresh(); }, "나중에", this::refresh);
            ask.setClickSound(click);
            openDialog.accept(ask);
        }
        showResult(msg, UiSkin.GOOD);
        refresh();
    }

    // ================================================================== 강화

    private void buildEnhance() {
        // 왼쪽: 장착할 무기 고르기 (장착한 무기만 강화한다, FR-대장간-04)
        Table left = new Table(skin);
        left.setBackground(skin.getDrawable("panel-light"));
        left.top().pad(8);
        left.add(new Label("장착 무기 (강화 대상)", skin, "gold")).left().padBottom(8).row();
        for (Weapon w : s.inventory.weapons()) {
            TextButton b = button(w.displayName(), () -> {
                if (busy > 0) return;
                s.equip(w);
                result.setText("");
                refresh();
            });
            b.setChecked(w == s.inventory.equipped());
            b.getLabel().setAlignment(Align.left);
            left.add(b).width(270).height(46).padBottom(6).row();
        }
        left.add(new Label("최근 기록", skin, "gold")).left().padTop(14).row();
        if (log.isEmpty()) left.add(new Label("아직 없음", skin, "small")).left().row();
        for (String[] l : log) {
            Label line = new Label(l[0], skin, "small-white");
            line.setColor(color(l[1]));
            line.setWrap(true);
            left.add(line).width(270).left().padBottom(2).row();
        }
        ScrollPane scroll = new ScrollPane(left, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        Table right = new Table(skin);
        right.setBackground(skin.getDrawable("panel-light"));
        right.top().pad(14);
        Weapon w = s.inventory.equipped();
        EnhancePreview p = s.previewEnhance();

        Table head = new Table();
        head.add(new Image(assets.region(w.icon()))).size(96).padRight(16);
        Table names = new Table();
        names.add(new Label(w.displayName(), skin, "heading")).left().row();
        if (p.enhanceable() && !p.maxed()) {
            names.add(new Label("+" + w.level() + "  →  +" + (w.level() + 1) + "      공격력 " + p.attackNow() + "  →  " + p.attackAfter(),
                    skin, "large")).left().row();
            String awaken = w.def().awakenedName != null ? "???" : "";
            names.add(new Label("각성까지 " + p.levelsToAwaken() + "단계  ·  각성하면: " + awaken, skin, "small")).left().row();
        } else {
            names.add(new Label("공격력 " + p.attackNow(), skin, "large")).left().row();
        }
        names.add(new Label(lifestealText(p.lifestealNow(), p.lifestealAfter(), p.enhanceable() && !p.maxed()), skin, "small")).left();
        head.add(names).left();
        right.add(head).left().colspan(2).padBottom(14).row();

        if (!w.def().enhanceable) {
            String why = w.def().starter ? "봉인 때문에 힘이 약하다. 강화·분해할 수 없다.\n세 보스의 힘을 흡수하면 봉인이 풀린다."
                    : "성검은 강화하지 않는다. 용사가 만든 검들의 힘을 나눠 받아 강해진다.";
            right.add(new Label(why, skin, "dim")).left().colspan(2).row();
            if (w.def().holy) buildHolyBreakdown(right);
            right.add(new Label("왼쪽에서 다른 무기를 골라 장착하면 그 무기를 강화한다.", skin, "small")).left().colspan(2).padTop(12).row();
        } else if (p.maxed()) {
            buildAwakened(right, w);
        } else {
            EnhanceOdds o = p.odds();
            right.add(oddsBar("성공", o.success(), UiSkin.SUCCESS)).left().colspan(2).row();
            right.add(oddsBar("유지", o.keep(), UiSkin.KEEP)).left().colspan(2).row();
            right.add(oddsBar("하락", o.down(), UiSkin.DOWN)).left().colspan(2).row();
            right.add(oddsBar("파괴", o.destroy(), UiSkin.DESTROY)).left().colspan(2).padBottom(10).row();
            String oreName = s.balance.ore(w.ore()).name;
            Label cost = new Label("비용: " + oreName + " " + p.cost() + "개   (보유 " + p.have() + "개)", skin, "large");
            if (!p.enoughOre()) cost.setColor(UiSkin.SHORT);
            right.add(cost).left().colspan(2).row();
            Label refund = new Label(o.risky() ? "파괴되면 " + oreName + " " + p.refundIfDestroyed() + "개를 돌려받고 성검을 든다."
                    : "이 단계에서는 파괴되지 않는다.", skin, "small");
            right.add(refund).left().colspan(2).padTop(4).row();
            TextButton go = button(o.risky() ? "강화하기 (파괴 위험)" : "강화하기", o.risky() ? "danger" : "default", this::askEnhance);
            go.setDisabled(!p.canEnhance());
            right.add(go).width(300).height(56).left().padTop(14).colspan(2).row();
            nav.focus(go);
        }

        content.add(scroll).width(300).growY().padRight(12);
        content.add(right).width(688).growY();
    }

    private static String pct(float v) {
        return fmt(v) + "%";
    }

    private String lifestealText(float now, float after, boolean preview) {
        if (now <= 0 && (!preview || after <= 0)) return "흡혈 없음 (+6강부터 생긴다)";
        String t = "흡혈: 적 최대 체력의 " + pct(now);
        if (preview && after != now) t += "  →  " + pct(after);
        return t;
    }

    /** 성검: 만든 검들에게서 나눠 받는 힘. */
    private void buildHolyBreakdown(Table right) {
        var shares = s.power.shares(s.inventory, s.hero);
        right.add(new Label("성검이 나눠 받는 힘 (종류마다 가장 센 검)", skin, "gold")).left().colspan(2).padTop(12).row();
        if (shares.isEmpty()) right.add(new Label("아직 만든 검이 없다. 검을 만들고 강화할수록 성검이 강해진다.", skin, "small")).left().colspan(2).row();
        for (var sh : shares.values()) {
            String line = sh.def().name + " +" + sh.level() + (sh.absorbed() ? " (흡수)" : "") + "  ·  공격력 " + sh.attack()
                    + " × " + fmt(s.balance.holy.shareRatio) + "  ·  흡혈 " + pct(sh.lifesteal());
            right.add(new Label(line, skin, "small-white")).left().colspan(2).row();
        }
        right.add(new Label("기본 " + s.balance.holySword().attack + " + 나눠 받은 공격력 = " + s.power.holyBase(s.inventory, s.hero)
                + "   (흡수한 힘 보너스 전)", skin, "small")).left().colspan(2).padTop(4).row();
    }

    private void buildAwakened(Table right, Weapon w) {
        AwakenAbility a = w.ability();
        right.add(new Label("각성한 무기다! 더 강화하지 않으므로 하락·파괴되지 않는다.", skin, "gold")).left().colspan(2).row();
        if (a != null) {
            right.add(new Label("능력: " + a.title + " — " + a.description, skin)).left().colspan(2).padTop(8).row();
            boolean already = s.hero.absorbed().contains(a);
            right.add(new Label(already ? "이 능력은 이미 용사에게 흡수되어 있다."
                    : "힘을 흡수하면 무기는 사라지고, 능력과 모든 무기 공격력 +10%가 용사에게 남는다.", skin, "small"))
                    .left().colspan(2).padTop(8).row();
            TextButton absorb = button("힘 흡수", () -> {
                ConfirmDialog d = new ConfirmDialog(skin, "힘 흡수", com.yongsa.model.Josa.eun(w.name()) + " 사라지고 그 힘이 용사에게 남습니다.",
                        "흡수한다", false, () -> {
                            if (s.absorbWeapon(w)) {
                                sound.accept("absorb");
                                showResult(a.title + "의 힘이 용사에게 스며들었다! 이제 어떤 무기를 들어도 쓴다.", UiSkin.GOLD);
                            }
                            refresh();
                        }, "취소", () -> { });
                d.setClickSound(click);
                openDialog.accept(d);
            });
            absorb.setDisabled(already);
            right.add(absorb).width(240).height(52).left().padTop(14).colspan(2).row();
        }
    }

    /** 확률 막대 한 줄. 0%도 회색으로 남긴다 (FR-대장간-10). */
    private Table oddsBar(String name, double pct, Color color) {
        Table t = new Table();
        Label n = new Label(name, skin);
        t.add(n).width(56).left();
        Bar bar = new Bar(skin.getRegion("white"), pct > 0 ? color : UiSkin.KEEP, (float) pct / 100f);
        t.add(bar).width(380).height(18).padRight(12);
        Label v = new Label(fmt(pct) + "%", skin);
        if (pct <= 0) {
            n.setColor(0.45f, 0.45f, 0.5f, 1f);
            v.setColor(0.45f, 0.45f, 0.5f, 1f);
        } else v.setColor(color);
        t.add(v).width(90).left();
        return t;
    }

    private static String fmt(double v) {
        if (Math.abs(v - Math.round(v)) < 1e-6) return String.valueOf(Math.round(v));
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0$", "");
    }

    private void askEnhance() {
        if (busy > 0) return;
        EnhancePreview p = s.previewEnhance();
        if (!p.canEnhance()) return;
        if (!p.odds().risky()) {
            doEnhance();
            return;
        }
        // 파괴 위험: 누를 때마다 확인창 (기본 선택 "취소", FR-대장간-11, UI-05)
        EnhanceOdds o = p.odds();
        Weapon w = s.inventory.equipped();
        ConfirmDialog d = new ConfirmDialog(skin, "파괴 위험",
                com.yongsa.model.Josa.eul(w.displayName()) + " 강화할까?\n실패하면 무기가 부서질 수 있다.", "강화한다", true,
                this::doEnhance, "취소", () -> { });
        d.extra.add(oddsBar("성공", o.success(), UiSkin.SUCCESS)).row();
        d.extra.add(oddsBar("유지", o.keep(), UiSkin.KEEP)).row();
        d.extra.add(oddsBar("하락", o.down(), UiSkin.DOWN)).row();
        d.extra.add(oddsBar("파괴", o.destroy(), UiSkin.DESTROY)).row();
        d.extra.add(new Label("파괴되면 " + s.balance.ore(w.ore()).name + " " + p.refundIfDestroyed() + "개를 돌려받는다.", skin, "small")).padTop(6);
        d.pack();
        d.setClickSound(click);
        openDialog.accept(d);
    }

    private void doEnhance() {
        EnhanceResult r = s.enhanceEquipped();
        if (!r.attempted()) return;
        float hammer = 0.6f / effectSpeed.get();
        busy = hammer;
        sound.accept("hammer");
        result.clearActions();
        result.setColor(Color.WHITE);
        result.setText("깡!  깡!  깡!");
        result.addAction(Actions.sequence(Actions.delay(hammer), Actions.run(() -> reveal(r))));
    }

    private void reveal(EnhanceResult r) {
        String text;
        String kind;
        switch (r.outcome()) {
            case SUCCESS -> {
                text = "강화 성공!  +" + r.levelBefore() + " → +" + r.levelAfter();
                kind = "success";
                sound.accept("enhance_success");
            }
            case KEEP -> {
                text = "강화 실패 — 단계 유지 (+" + r.levelAfter() + ")";
                kind = "keep";
                sound.accept("enhance_fail");
            }
            case DOWN -> {
                text = "강화 실패 — 단계 하락  +" + r.levelBefore() + " → +" + r.levelAfter();
                kind = "down";
                sound.accept("enhance_fail");
            }
            default -> {
                text = "용사가 실수했다!  " + com.yongsa.model.Josa.i(r.weaponName()) + " 부서졌다… (광물 " + r.refund() + "개 돌려받음)";
                kind = "destroy";
                sound.accept("destroy");
            }
        }
        log.addFirst(new String[]{r.weaponName() + ": " + text, kind});
        while (log.size() > 5) log.removeLast();
        showResult(text, color(kind));
        if (r.awakened()) awaken();
        refresh();
    }

    /** 각성 연출 1.5초: 빛 + "○○이 각성했다!" + 새 능력 설명 (FR-대장간-17). */
    private void awaken() {
        Weapon w = s.inventory.equipped();
        float t = 1.5f / effectSpeed.get();
        busy = t;
        sound.accept("awaken");
        flash.setBounds(0, 0, getWidth(), getHeight());
        flash.getColor().a = 0.9f;
        addActor(flash);
        flash.clearActions();
        flash.addAction(Actions.sequence(Actions.fadeOut(t), Actions.removeActor()));
        AwakenAbility a = w.ability();
        showResult(com.yongsa.model.Josa.i(w.name()) + " 각성했다!" + (a != null ? "  새 능력: " + a.title + " — " + a.description : ""), UiSkin.GOLD);
    }

    // ================================================================== 분해

    private void buildDismantle() {
        Table list = new Table();
        list.top().left();
        list.add(new Label("안 쓰는 무기를 분해하면 광물 4개 + 강화에 쓴 광물의 절반을 돌려받는다. 장착한 무기와 성검은 분해할 수 없다.",
                skin, "small")).left().padBottom(12).row();
        boolean any = false;
        for (Weapon w : s.inventory.weapons()) {
            if (!w.def().isSword()) continue;
            any = true;
            Table row = new Table(skin);
            row.setBackground(skin.getDrawable("panel-light"));
            row.add(new Image(assets.region(w.icon()))).size(48).padRight(12);
            row.add(new Label(w.displayName(), skin, "large")).width(360).left();
            int refund = s.dismantleService.refund(w);
            row.add(new Label("돌려받음: " + s.balance.ore(w.ore()).name + " " + refund + "개", skin)).width(300).left();
            TextButton b = button(w == s.inventory.equipped() ? "장착 중" : "분해", () -> {
                ConfirmDialog d = new ConfirmDialog(skin, "분해", com.yongsa.model.Josa.eul(w.displayName()) + " 분해할까?\n"
                        + s.balance.ore(w.ore()).name + " " + refund + "개를 돌려받는다.", "분해한다", false, () -> {
                            int got = s.dismantle(w);
                            if (got >= 0) {
                                sound.accept("dismantle");
                                showResult(com.yongsa.model.Josa.eul(w.displayName()) + " 분해했다. 광물 " + got + "개를 돌려받았다.", Color.WHITE);
                            }
                            refresh();
                        }, "취소", () -> { });
                d.setClickSound(click);
                openDialog.accept(d);
            });
            b.setDisabled(!s.dismantleService.canDismantle(w, s.inventory));
            row.add(b).width(150).height(46);
            list.add(row).width(960).padBottom(8).row();
        }
        if (!any) list.add(new Label("분해할 무기가 없다.", skin, "dim")).left().row();
        ScrollPane sp = new ScrollPane(list, skin);
        sp.setFadeScrollBars(false);
        content.add(sp).grow().top();
    }

    // ================================================================== 공통

    private void showResult(String text, Color c) {
        result.clearActions();
        result.setText(text);
        result.setColor(c);
        result.getColor().a = 0;
        result.addAction(Actions.fadeIn(0.15f));
    }

    private static Color color(String kind) {
        return switch (kind) {
            case "success" -> UiSkin.SUCCESS;
            case "keep" -> UiSkin.KEEP;
            case "down" -> UiSkin.DOWN;
            case "destroy" -> UiSkin.DESTROY;
            default -> Color.WHITE;
        };
    }

    /** 확률 막대. */
    private static class Bar extends Actor {
        private final TextureRegion white;
        private final Color color;
        private final float ratio;

        Bar(TextureRegion white, Color color, float ratio) {
            this.white = white;
            this.color = new Color(color);
            this.ratio = ratio;
        }

        @Override
        public void draw(Batch b, float a) {
            b.setColor(0.08f, 0.07f, 0.1f, a);
            b.draw(white, getX(), getY(), getWidth(), getHeight());
            b.setColor(color.r, color.g, color.b, a);
            b.draw(white, getX(), getY(), getWidth() * ratio, getHeight());
            b.setColor(Color.WHITE);
        }
    }
}
