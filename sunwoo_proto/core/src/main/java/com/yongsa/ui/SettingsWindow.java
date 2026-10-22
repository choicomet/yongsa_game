package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.yongsa.Settings;

/** 설정 (UI-10, FR-공통-05): 소리 크기, 창 모드/전체 화면, 연출 속도. 바로 적용·저장된다. */
public class SettingsWindow extends GameWindow {

    private final Settings settings;
    private final Runnable onVolume;

    public SettingsWindow(Skin skin, Settings settings, Runnable onVolume) {
        super("설정", skin);
        this.settings = settings;
        this.onVolume = onVolume;
    }

    @Override
    public void refresh() {
        clearChildren();
        nav.clear();
        add(row("효과음", Math.round(settings.sfx() * 100) + "%",
                () -> settings.setSfx(settings.sfx() - 0.1f), () -> settings.setSfx(settings.sfx() + 0.1f))).row();
        add(row("배경음", Math.round(settings.music() * 100) + "%",
                () -> { settings.setMusic(settings.music() - 0.1f); onVolume.run(); },
                () -> { settings.setMusic(settings.music() + 0.1f); onVolume.run(); })).row();
        add(row("화면", settings.fullscreen() ? "전체 화면" : "창 모드",
                () -> settings.setFullscreen(!settings.fullscreen()), () -> settings.setFullscreen(!settings.fullscreen()))).row();
        add(row("연출 속도", Settings.EFFECT_SPEED_NAMES[settings.effectSpeedIndex()],
                () -> settings.setEffectSpeedIndex(settings.effectSpeedIndex() - 1),
                () -> settings.setEffectSpeedIndex(settings.effectSpeedIndex() + 1))).row();
        add(new Label("방향키로 고르고 Enter로 바꾼다. 바뀐 설정은 바로 저장된다.", skin, "small")).padTop(10).row();
        add(button("닫기 (Esc)", this::close)).width(200).height(48).padTop(12);
        nav.focusFirstEnabled();
        pack();
        centerOnStage();
    }

    private Table row(String name, String value, Runnable minus, Runnable plus) {
        Table t = new Table();
        t.add(new Label(name, skin, "large")).width(150).left();
        t.add(button("◀", () -> { minus.run(); refresh(); })).size(54, 46);
        t.add(new Label(value, skin, "large")).width(170).center();
        t.add(button("▶", () -> { plus.run(); refresh(); })).size(54, 46);
        t.padBottom(8);
        return t;
    }
}
