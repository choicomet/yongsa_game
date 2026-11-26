package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Graphics;
import com.badlogic.gdx.utils.Timer;
import com.yongsa.YongsaGame;
import org.lwjgl.system.APIUtil;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Platform;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.windows.User32;

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
        new Lwjgl3Application(new YongsaGame() {
            @Override
            public void create() {
                useWindowIconForTaskbar();
                refreshTaskbarIcon();
                super.create();
                // 게임을 불러오는 동안은 창이 바빠서 작업표시줄이 아이콘을 묻다가 포기하고 빈 칸을 둔다.
                // 다 불러오자마자 바로 다시 알려 주고, 놓칠 때를 대비해 0.3초 간격으로 몇 번 더 알려 준다.
                refreshTaskbarIcon();
                Timer.schedule(new Timer.Task() {
                    @Override
                    public void run() {
                        refreshTaskbarIcon();
                    }
                }, 0f, 0.3f, 4);
            }
        }, config);
    }

    /**
     * GLFW 는 창 아이콘(WM_SETICON)만 바꾸고 창 "클래스" 아이콘은 빈 기본 아이콘으로 둔다.
     * 작업표시줄이 클래스 아이콘을 쓰는 경우가 있어서, 창에 들어간 용사 아이콘을 클래스에도 넣는다.
     */
    private static void useWindowIconForTaskbar() {
        if (Platform.get() != Platform.WINDOWS) return;
        try (SharedLibrary user32 = APIUtil.apiCreateLibrary("user32")) {
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle());
            long sendMessage = user32.getFunctionAddress("SendMessageW");
            if (hwnd == 0 || sendMessage == 0) return;
            long big = JNI.callPPPP(hwnd, User32.WM_GETICON, 1L /* ICON_BIG */, 0L, sendMessage);
            long small = JNI.callPPPP(hwnd, User32.WM_GETICON, 0L /* ICON_SMALL */, 0L, sendMessage);
            if (big != 0) User32.SetClassLongPtr(hwnd, User32.GCL_HICON, big);
            if (small != 0) User32.SetClassLongPtr(hwnd, User32.GCL_HICONSM, small);
        } catch (Throwable ignored) {
            // 아이콘만 못 바꾼 것이니 그냥 넘어간다
        }
    }

    /**
     * 작업표시줄에 아이콘을 다시 그리게 한다. 아이콘을 잠깐 비웠다가 다시 넣으면 (WM_SETICON)
     * 작업표시줄이 아이콘을 새로 읽어 간다. 시작할 때 놓쳐서 빈 칸이 된 경우를 고친다.
     */
    private static void refreshTaskbarIcon() {
        if (Platform.get() != Platform.WINDOWS) return;
        try (SharedLibrary user32 = APIUtil.apiCreateLibrary("user32")) {
            long hwnd = GLFWNativeWin32.glfwGetWin32Window(((Lwjgl3Graphics) Gdx.graphics).getWindow().getWindowHandle());
            long sendMessage = user32.getFunctionAddress("SendMessageW");
            if (hwnd == 0 || sendMessage == 0) return;
            for (long which : new long[]{1L /* ICON_BIG */, 0L /* ICON_SMALL */}) {
                long icon = JNI.callPPPP(hwnd, User32.WM_GETICON, which, 0L, sendMessage);
                if (icon == 0) continue;
                JNI.callPPPP(hwnd, User32.WM_SETICON, which, 0L, sendMessage);
                JNI.callPPPP(hwnd, User32.WM_SETICON, which, icon, sendMessage);
            }
        } catch (Throwable ignored) {
            // 아이콘만 못 바꾼 것이니 그냥 넘어간다
        }
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
