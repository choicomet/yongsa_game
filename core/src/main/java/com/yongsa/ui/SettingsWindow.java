package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.yongsa.YongsaGame;

/** 설정 (UI-10): 소리 크기, 창 모드, 연출 속도. 바로 적용되고 저장된다. */
public class SettingsWindow extends Modal {
    private final YongsaGame game;

    public SettingsWindow(YongsaGame game) {
        super(game.skin);
        this.game = game;
    }

    @Override
    public void build() {
        Table p = panel();
        p.add(label("설정", "large", Ui.GOLD)).colspan(3).padBottom(18).row();

        p.add(label("소리 크기", "default", Ui.DIM)).left().padRight(24);
        Table vol = new Table();
        vol.add(nav.add("−", () -> setVolume(game.settings.volume - 0.1f))).width(56).height(46);
        vol.add(label(Math.round(game.settings.volume * 100) + "%")).width(90).center();
        vol.add(nav.add("+", () -> setVolume(game.settings.volume + 0.1f))).width(56).height(46);
        p.add(vol).left().padBottom(10).row();

        p.add(label("화면", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(game.settings.fullscreen ? "전체 화면" : "창 모드", () -> {
            game.settings.fullscreen = !game.settings.fullscreen;
            game.saveSettings();
            rebuild();
        })).width(220).height(46).left().padBottom(10).row();

        p.add(label("연출 속도", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(game.settings.effectSpeed > 1 ? "빠르게" : "보통", () -> {
            game.settings.effectSpeed = game.settings.effectSpeed > 1 ? 1f : 2f;
            game.saveSettings();
            rebuild();
        })).width(220).height(46).left().padBottom(10).row();

        p.add(label("화면 흔들림", "default", Ui.DIM)).left().padRight(24);
        p.add(nav.add(game.settings.screenShake ? "켜기" : "끄기", () -> {
            game.settings.screenShake = !game.settings.screenShake;
            game.saveSettings();
            rebuild();
        })).width(220).height(46).left().padBottom(10).row();

        p.add(nav.add("닫기", this::close)).colspan(2).width(200).height(50).padTop(14);
    }

    private void setVolume(float v) {
        game.settings.volume = Math.max(0, Math.min(1, Math.round(v * 10) / 10f));
        game.saveSettings();
        game.sfx.play("select");
        rebuild();
    }
}
