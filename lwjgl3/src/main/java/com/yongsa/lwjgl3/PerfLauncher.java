package com.yongsa.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.yongsa.YongsaGame;
import com.yongsa.map.Room;
import com.yongsa.model.GameState;
import com.yongsa.model.NewGame;
import com.yongsa.screen.GameScreen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 개발용 렉 측정: 용사를 맵 전체 방 순서대로 걸려 보내며 프레임마다 걸린 시간을 잰다.
 * 실행: gradlew lwjgl3:perf (결과: 방마다 가장 느린 프레임, 전체 평균·상위 1%).
 * 두 바퀴를 돌아 처음 들어갈 때(그림 불러오기)와 다시 들어갈 때를 나눠 본다.
 */
public final class PerfLauncher {
    public static void main(String[] args) {
        System.setProperty("yongsa.saveDir", new java.io.File(System.getProperty("java.io.tmpdir"), "yongsa-perf-save").getAbsolutePath());
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Yongsa perf");
        config.setWindowedMode(1280, 720);
        config.disableAudio(true);
        config.setForegroundFPS(0);
        config.setIdleFPS(0);
        config.useVsync(false);
        new Lwjgl3Application(new Run(args.length > 0 ? Integer.parseInt(args[0]) : 2), config);
    }

    private static final class Run extends YongsaGame {
        private final int laps;
        private GameScreen game;
        private final List<float[]> route = new ArrayList<>();
        private final List<String> names = new ArrayList<>();
        private int leg, lap, frame;
        private static final int FRAMES_PER_LEG = 45;
        /** 0 이면 빠른 순회(방마다 45프레임), 아니면 이 속도(칸/초)로 걷는다: -Dperf.walk=4 */
        private static final float WALK = Float.parseFloat(System.getProperty("perf.walk", "0"));
        private final List<Double> all = new ArrayList<>();
        private final List<Double>[] perLap;
        private double legWorst;
        private long last;

        @SuppressWarnings("unchecked")
        Run(int laps) {
            this.laps = laps;
            perLap = new List[laps];
            for (int i = 0; i < laps; i++) perLap[i] = new ArrayList<>();
        }

        @Override public void create() {
            super.create();
            GameState state = NewGame.create(balance);
            game = new GameScreen(this, state);
            // 캠프에서 가까운 방부터 차례로 (가장 가까운 다음 방을 고르는 순회)
            List<Room> left = new ArrayList<>(game.session.map.rooms);
            Room cur = game.session.map.room("CAMP");
            left.remove(cur);
            add(cur);
            while (!left.isEmpty()) {
                Room best = null; float bd = Float.MAX_VALUE;
                for (Room r : left) {
                    float d = Math.abs(cx(r) - cx(cur)) + Math.abs(cy(r) - cy(cur));
                    if (d < bd) { bd = d; best = r; }
                }
                left.remove(best); add(best); cur = best;
            }
            add(game.session.map.room("CAMP"));
            Screen previous = getScreen(); setScreen(game); if (previous != null) previous.dispose();
            last = System.nanoTime();
        }

        private void add(Room r) { route.add(new float[]{cx(r), cy(r)}); names.add(r.id); }
        private static float cx(Room r) { return r.originX + r.widthPx / 2f; }
        private static float cy(Room r) { return r.originY + r.heightPx / 2f; }

        @Override public void render() {
            if (game != null && getScreen() == game && leg + 1 < route.size()) {
                float[] a = route.get(leg), b = route.get(leg + 1);
                float t = Math.min(1f, frame / (FRAMES_PER_LEG * 0.7f));
                if (WALK > 0) { // 실제처럼 일정한 속도로 걷기 (칸/초, 60fps 기준)
                    float len = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
                    t = Math.min(1f, frame * WALK * balance.tileSize / 60f / Math.max(1f, len));
                }
                game.session.hero.x = a[0] + (b[0] - a[0]) * t;
                game.session.hero.y = a[1] + (b[1] - a[1]) * t;
                game.session.hero.invuln = 999f;
                game.session.hero.hp = game.session.hero.maxHp;
            }
            super.render();
            long now = System.nanoTime();
            double ms = (now - last) / 1e6;
            last = now;
            if (game == null || getScreen() != game) return;
            if (frame > 0 || leg > 0) { all.add(ms); perLap[lap].add(ms); legWorst = Math.max(legWorst, ms); }
            if (ms > 120) System.out.printf("SLOW frame %.0f ms (lap%d to %s, frame %d)%n", ms, lap + 1, names.get(leg + 1), frame);
            if (WALK > 0) {
                float[] a = route.get(leg), b = route.get(leg + 1);
                float len = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
                if (++frame * WALK * balance.tileSize / 60f < len + WALK * balance.tileSize) return; // 도착 후 1초 더
            } else if (++frame < FRAMES_PER_LEG) return;
            System.out.printf("PERF lap%d %-28s worst %7.1f ms%n", lap + 1, names.get(leg + 1), legWorst);
            legWorst = 0; frame = 0;
            if (++leg + 1 >= route.size()) {
                leg = 0;
                if (++lap >= laps) { report(); Gdx.app.exit(); }
            }
        }

        private void report() {
            if (com.yongsa.map.BakeDigest.ON) {
                try (var w = new java.io.PrintWriter(System.getProperty("yongsa.bakeDigestOut", "bake-digest.txt"), "UTF-8")) {
                    com.yongsa.map.BakeDigest.all().forEach((k, v) -> w.println(k + " " + v));
                } catch (java.io.IOException e) { throw new RuntimeException(e); }
            }
            for (int i = 0; i < laps; i++) summary("lap" + (i + 1), perLap[i]);
            summary("all", all);
            Runtime rt = Runtime.getRuntime();
            System.out.printf("PERF heap used %d MB%n", (rt.totalMemory() - rt.freeMemory()) / (1 << 20));
        }

        private static void summary(String label, List<Double> v) {
            double[] s = v.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            if (s.length == 0) return;
            double avg = Arrays.stream(s).average().orElse(0);
            long over50 = Arrays.stream(s).filter(x -> x > 50).count();
            System.out.printf("PERF %s frames %d avg %.1f ms  p50 %.1f  p99 %.1f  max %.1f  frames>50ms %d  sum>50ms %.0f ms%n",
                    label, s.length, avg, s[s.length / 2], s[(int) (s.length * 0.99)], s[s.length - 1], over50,
                    Arrays.stream(s).filter(x -> x > 50).sum());
        }
    }
}
