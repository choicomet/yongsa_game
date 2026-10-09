package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowAdapter;
import com.yongsa.YongsaGame;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Windows PC 실행 진입점. 기본 창 크기 1280x720, 창 모드. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        YongsaGame game = new YongsaGame(saveDirectory());
        String showcase = System.getProperty("yongsa.showcase");
        if (showcase != null && !showcase.isEmpty()) game.afterRender = new Showcase(game, showcase);
        Lwjgl3ApplicationConfiguration cfg = new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("용사가 다한다");
        cfg.setWindowedMode(1280, 720);
        cfg.useVsync(true);
        cfg.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        cfg.setIdleFPS(30);
        cfg.setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                // 앱이 포커스를 잃으면 자동으로 일시정지한다
                if (Gdx.app != null) Gdx.app.postRunnable(game::focusLost);
            }
        });
        new Lwjgl3Application(game, cfg);
    }

    /** 사용자에게 쓰기 권한이 있는 게임 저장 폴더. */
    static Path saveDirectory() {
        String override = System.getProperty("yongsa.saveDir");
        if (override != null && !override.isEmpty()) return Paths.get(override);
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            return Paths.get(appData != null ? appData : home, "YongsaGame");
        }
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support", "YongsaGame");
        String xdg = System.getenv("XDG_DATA_HOME");
        return Paths.get(xdg != null ? xdg : Paths.get(home, ".local", "share").toString(), "yongsagame");
    }
}
