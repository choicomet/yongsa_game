package com.yongsa.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.yongsa.YongsaGame;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;

/** Windows 데스크톱 창으로 실행한다. 기본 1280×720, 크기를 바꿔도 비율을 유지한다 (NFR-01). */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (args.length == 2 && "--visual-check".equals(args[0])) {
            VisualSmokeLauncher.main(new String[]{args[1]});
            return;
        }
        useOwnTaskbarIcon();
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("용사가 다한다");
        config.setWindowIcon("launcher/hero-256.png", "launcher/hero-128.png", "launcher/hero-48.png",
                "launcher/hero-32.png", "launcher/hero-16.png");
        config.setWindowedMode(YongsaGame.WIDTH, YongsaGame.HEIGHT);
        config.setForegroundFPS(60);
        config.useVsync(true);
        new Lwjgl3Application(new YongsaGame(), config);
    }

    /**
     * Windows 작업표시줄은 기본으로 javaw.exe 의 커피잔 아이콘으로 묶는다.
     * 이 프로세스에 고유 앱 ID 를 주면 따로 묶이고, 창에 넣은 용사 아이콘(setWindowIcon)이 작업표시줄에도 뜬다.
     * 창을 만들기 전에 불러야 한다. 실패해도 게임은 그대로 켜진다.
     */
    private static void useOwnTaskbarIcon() {
        if (Platform.get() != Platform.WINDOWS) return;
        try (SharedLibrary shell32 = APIUtil.apiCreateLibrary("shell32");
             MemoryStack stack = MemoryStack.stackPush()) {
            long fn = shell32.getFunctionAddress("SetCurrentProcessExplicitAppUserModelID");
            if (fn != 0) JNI.callPI(MemoryUtil.memAddress(stack.UTF16("Yongsa.HeroDoesItAll")), fn);
        } catch (Throwable ignored) {
            // 아이콘만 못 바꾼 것이니 그냥 넘어간다
        }
    }
}
