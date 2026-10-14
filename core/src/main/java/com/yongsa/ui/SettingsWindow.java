package com.yongsa.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.yongsa.model.Settings;

import java.util.function.Consumer;

/** 설정: 전체·배경음·효과음 음량(0~100)과 전체 화면 여부. */
public final class SettingsWindow {
    private SettingsWindow() {}

    public static Windows.Modal open(Windows w, Settings current, Consumer<Settings> apply) {
        Settings s = current.copy();
        Table p = w.panel();
        p.add(w.label("설정", "large")).left().colspan(3).padBottom(12).row();
        slider(w, p, "전체 음량", s.masterVolume, v -> s.masterVolume = v);
        slider(w, p, "배경음 음량", s.musicVolume, v -> s.musicVolume = v);
        slider(w, p, "효과음 음량", s.sfxVolume, v -> s.sfxVolume = v);
        CheckBox full = new CheckBox(" 전체 화면", w.skin);
        full.setChecked(s.fullscreen);
        full.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                s.fullscreen = full.isChecked();
            }
        });
        p.add(full).left().colspan(3).padTop(8).row();
        p.add(w.label("이동 WASD/방향키 · J 공격 · K 회피 · L 상호작용 · U 충격파 · I 가방 · O 무기", "dim"))
                .left().colspan(3).padTop(12).row();
        Table buttons = new Table();
        Windows.Modal[] holder = new Windows.Modal[1];
        buttons.add(w.button("닫기", () -> w.close(holder[0]))).padRight(10);
        buttons.add(w.button("적용", () -> {
            s.clamp();
            apply.accept(s);
            w.close(holder[0]);
        }));
        p.add(buttons).right().colspan(3).padTop(14);
        holder[0] = w.push(p, "settings");
        return holder[0];
    }

    private static void slider(Windows w, Table p, String name, int value, Consumer<Integer> set) {
        Slider sl = new Slider(0, 100, 1, false, w.skin);
        sl.setValue(value);
        Label val = w.label(String.valueOf(value), "default");
        sl.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int v = Math.round(sl.getValue());
                val.setText(String.valueOf(v));
                set.accept(v);
            }
        });
        p.add(w.label(name, "default")).left().width(110).padBottom(6);
        p.add(sl).width(220).padBottom(6);
        p.add(val).width(40).padLeft(10).padBottom(6).row();
    }
}
