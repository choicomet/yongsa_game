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
        ResultWindow w = new ResultWindow(skin, "던전 클리어!", onOk, true);
        Table t = new Table();
        t.add(new Image(assets.region("orb_" + r.dungeon().id))).size(48).padRight(14);
        t.add(new Label(s.balance.boss(r.dungeon().boss).name + "의 힘을 흡수했다!", skin, "heading")).left();
        w.add(t).padBottom(12).row();
        w.line("최대 체력 +" + r.hpGain() + "   (지금 " + s.hero.maxHp() + ")", Color.WHITE);
        w.line("보상: " + s.balance.ore(r.dungeon().ore).name + " " + r.oreReward() + "개, 보스의 구슬", Color.WHITE);
        if (r.opened() != null) {
            w.line("열린 던전: " + r.opened().name, UiSkin.GOLD);
            w.line("다음에 만들 수 있는 무기: " + s.balance.weapon(r.opened().weapon).name + " (" + s.balance.ore(r.opened().ore).name + "로 제작)", Color.WHITE);
        } else {
            w.line("구슬 세 개가 모였다면 캠프 바닥 문양에서 성검의 봉인을 풀 수 있다.", UiSkin.GOLD);
        }
        if (r.firstClear()) {
            Label story = new Label("…하지만 힘의 일부는 흡수하지 못했다.\n흩어진 보스의 힘이 " + r.dungeon().name
                    + "에 깃들어 몬스터들이 강해졌다!", skin, "large");
            story.setColor(1f, 0.45f, 0.4f, 1f);
            story.setAlignment(Align.center);
            w.add(story).padTop(14).padBottom(6).row();
        }
        w.line("보스방 가운데에 캠프로 돌아가는 포탈이 열렸다. 떨어진 구슬을 잊지 말고 줍자.", UiSkin.DIM);
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
