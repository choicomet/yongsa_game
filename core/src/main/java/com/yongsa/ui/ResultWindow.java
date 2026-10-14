package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.yongsa.model.AbsorbService;
import com.yongsa.model.GameSession;
import com.yongsa.render.Assets;

/**
 * 던전 결과창 (UI-08).
 * 클리어: 오른 최대 체력, 보상, 열린 던전, 다음에 만들 수 있는 무기 + 흡수하지 못한 힘 이야기(첫 처치 때만).
 * 사망: "캠프로 돌아갑니다".
 */
public class ResultWindow extends GameWindow {

    private final Runnable onOk;
    private final boolean escapable;

    private ResultWindow(Skin skin, String title, Runnable onOk, boolean escapable) {
        super(title, skin);
        this.onOk = onOk;
        this.escapable = escapable;
    }

    public static ResultWindow bossCleared(Skin skin, GameSession s, Assets assets, AbsorbService.BossAbsorb r, Runnable onOk) {
        if (!r.firstClear()) return bossAgain(skin, s, assets, r, onOk);
        ResultWindow w = new ResultWindow(skin, "던전 클리어!", onOk, true);
        Table t = new Table();
        t.add(new Image(assets.region("orb_" + r.dungeon().id))).size(48).padRight(14);
        t.add(new Label(s.balance.boss(r.dungeon().boss).name + "의 힘을 흡수했다!", skin, "heading")).left();
        w.add(t).padBottom(12).row();
        w.line("최대 체력 +" + r.hpGain() + "   (지금 " + s.hero.maxHp() + ")", Color.WHITE);
        w.line("보물상자: " + s.balance.ore(r.dungeon().ore).name + " " + r.oreReward() + "개 + 보스의 정수 (상자를 E로 열자)", Color.WHITE);
        if (r.opened() != null) {
            w.line("열린 던전: " + r.opened().name, UiSkin.GOLD);
            w.line("다음에 만들 수 있는 무기: " + s.balance.weapon(r.opened().weapon).name + " (" + s.balance.ore(r.opened().ore).name + "로 제작)", Color.WHITE);
        } else {
            w.line("정수 세 개가 모였다면 캠프 바닥 문양에서 성검을 해방할 수 있다.", UiSkin.GOLD);
        }
        if (r.firstClear()) {
            Label story = new Label("…하지만 힘의 일부는 흡수하지 못했다.\n흩어진 보스의 힘이 " + r.dungeon().name
                    + "에 깃들어 몬스터들이 강해졌다!", skin, "large");
            story.setColor(1f, 0.45f, 0.4f, 1f);
            story.setAlignment(Align.center);
            w.add(story).padTop(14).padBottom(6).row();
            Label camp = new Label("캠프를 덮고 있던 어둠이 조금 걷혔다.", skin);
            camp.setColor(1f, 0.85f, 0.55f, 1f);
            w.add(camp).padBottom(6).row();
        }
        w.line("보스방 가운데에 캠프로 돌아가는 포탈이 열렸다. 포탈 위 보물상자를 잊지 말고 열자.", UiSkin.DIM);
        w.add(w.button("확인", w::finish)).width(220).height(52).padTop(16);
        w.pack();
        return w;
    }

    /** 침대로 쉬어 다시 생긴 보스: 흡수·정수 없이 보물상자의 광물만. */
    private static ResultWindow bossAgain(Skin skin, GameSession s, Assets assets, AbsorbService.BossAbsorb r, Runnable onOk) {
        ResultWindow w = new ResultWindow(skin, "보스 처치", onOk, true);
        Table t = new Table();
        t.add(new Image(assets.region("ore_" + r.dungeon().ore))).size(40).padRight(12);
        t.add(new Label(s.balance.boss(r.dungeon().boss).name + "을(를) 다시 쓰러뜨렸다", skin, "heading")).left();
        w.add(t).padBottom(12).row();
        w.line("보물상자: " + s.balance.ore(r.dungeon().ore).name + " " + r.oreReward() + "개 (상자를 E로 열자)", Color.WHITE);
        w.line("이미 흡수한 힘이라 더 얻을 것은 없다. 광물을 모으는 데는 쓸 만하다.", UiSkin.DIM);
        w.line("보스방 가운데에 캠프로 돌아가는 포탈이 열렸다.", UiSkin.DIM);
        w.add(w.button("확인", w::finish)).width(220).height(52).padTop(16);
        w.pack();
        return w;
    }

    public static ResultWindow died(Skin skin, Runnable onOk) {
        ResultWindow w = new ResultWindow(skin, "쓰러졌다…", onOk, false);
        w.line("용사가 쓰러졌다. 캠프로 돌아갑니다.", Color.WHITE);
        w.line("무기와 광물은 그대로다. 싸우던 방만 처음 상태로 돌아간다.", UiSkin.DIM);
        w.add(w.button("캠프로 돌아가기", w::finish)).width(260).height(52).padTop(16);
        w.pack();
        return w;
    }

    private void line(String text, Color c) {
        Label l = new Label(text, skin);
        l.setColor(c);
        l.setWrap(true);
        l.setAlignment(Align.center);
        add(l).width(640).padBottom(6).row();
    }

    private void finish() {
        close();
        onOk.run();
    }

    @Override
    public void refresh() {
        pack();
    }

    @Override
    public boolean onEscape() {
        if (!escapable) return false;
        finish();
        return true;
    }
}
