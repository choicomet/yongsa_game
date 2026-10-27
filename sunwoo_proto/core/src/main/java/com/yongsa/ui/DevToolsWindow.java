package com.yongsa.ui;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.yongsa.config.DungeonDef;
import com.yongsa.model.GameSession;

import java.util.function.Consumer;

/**
 * 개발자 도구 (F1, 2026-10-11 결정): 예전 F1~F5 기능을 한 창에 모았다. 테스트·발표용.
 * <ul>
 *   <li>켜기/끄기: 광물 무한, 무적 — 다른 창(대장간 등) 위에서도 열 수 있고, 닫으면 아래 창을 새로 고친다.</li>
 *   <li>이동: 1·2·3번째 보스방 입구, 캠프, 마을 — 열린 창을 모두 닫은 뒤 이동한다.</li>
 * </ul>
 * 키보드: 방향키/Tab으로 고르고 Enter·Space, F1·Esc로 닫기.
 */
public class DevToolsWindow extends GameWindow {

    private final GameSession s;
    /** 다른 창을 모두 닫고 나서 할 일 (이동은 게임 화면에서 해야 하므로). */
    private final Consumer<Runnable> closeAllThen;
    /** 닫힌 뒤: 아래 창(대장간 등)이 광물 수를 다시 그리게. */
    private final Runnable afterClose;

    public DevToolsWindow(Skin skin, GameSession session, Consumer<Runnable> closeAllThen, Runnable afterClose) {
        super("개발자 도구", skin);
        this.s = session;
        this.closeAllThen = closeAllThen;
        this.afterClose = afterClose;
    }

    @Override
    public void close() {
        super.close();
        afterClose.run();
    }

    @Override
    public void refresh() {
        clearChildren();
        nav.clear();
        add(new Label("테스트·발표용 기능입니다. 저장 파일의 진행 상황은 바꾸지 않습니다.", skin, "small")).left().padBottom(10).row();

        add(new Label("켜기 / 끄기", skin, "large-gold")).left().padBottom(4).row();
        add(toggle("광물 무한  (제작·강화에 광물이 줄지 않음)", s.inventory.infiniteOre(), s::toggleInfiniteOre))
                .width(520).height(42).left().padBottom(4).row();
        add(toggle("무적  (어떤 공격·벌칙에도 피해를 받지 않음)", s.hero.invulnerable, s::toggleGodMode))
                .width(520).height(42).left().padBottom(4).row();
        add(toggle("강화 성공 100%  (하락·파괴 없이 언제나 성공)", s.enhanceService.alwaysSucceed(), s::toggleSureEnhance))
                .width(520).height(42).left().padBottom(8).row();

        // 강화 숙련도 (모든 검 공유): 한 단계 올리기 / 마스터 / 0
        Table mastery = new Table();
        mastery.add(new Label("강화 숙련도  Lv " + s.mastery.level() + (s.mastery.maxed() ? " (마스터)" : ""), skin)).width(220).left();
        com.badlogic.gdx.scenes.scene2d.ui.TextButton up = button("+1", () -> { s.devSetMastery(s.mastery.level() + 1); refresh(); });
        up.setDisabled(s.mastery.maxed());
        mastery.add(up).width(80).height(40).padRight(6);
        mastery.add(button("마스터", () -> { s.devSetMastery(s.mastery.maxLevel()); refresh(); })).width(110).height(40).padRight(6);
        mastery.add(button("0", () -> { s.devSetMastery(0); refresh(); })).width(80).height(40);
        add(mastery).width(520).left().padBottom(12).row();

        add(new Label("바로 가기", skin, "large-gold")).left().padBottom(4).row();
        DungeonDef[] ds = s.balance.dungeons;
        for (int i = 0; i < ds.length; i++) {
            int index = i;
            String text = (i + 1) + "번째 보스방 입구  —  " + ds[i].name + " (" + s.balance.boss(ds[i].boss).name + ")";
            add(button(text, () -> closeAllThen.accept(() -> s.warpToBossDoor(index)))).width(520).height(42).padBottom(4).row();
        }
        add(button("용사의 캠프", () -> closeAllThen.accept(() -> s.warpTo(false)))).width(520).height(42).padBottom(4).row();
        add(button("던전 밖 마을", () -> closeAllThen.accept(() -> s.warpTo(true)))).width(520).height(42).padBottom(12).row();

        add(button("닫기 (F1 / Esc)", this::close)).width(220).height(46);
        pack();
        centerOnStage();
    }

    private CheckToggle toggle(String text, boolean on, Runnable action) {
        CheckToggle c = new CheckToggle(text, skin);
        c.setChecked(on);
        c.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                click.run();
                action.run();
            }
        });
        return nav.add(c);
    }

    @Override
    public boolean keyDown(int key) {
        if (key == Input.Keys.F1) {
            close();
            return true;
        }
        return super.keyDown(key);
    }
}
