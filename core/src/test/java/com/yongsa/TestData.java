package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.world.Corridor;
import com.yongsa.world.Door;
import com.yongsa.world.Room;
import com.yongsa.world.MapDef;
import com.yongsa.world.RoomSceneLayout;
import com.yongsa.world.WorldMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 테스트용: 실제 게임과 같은 assets/data 의 JSON 을 읽는다. */
public final class TestData {
    private TestData() {
    }

    private static String read(String path) {
        try {
            return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Balance balance() {
        return Balance.parse(read("assets/data/balance.json"));
    }

    /** 방의 첫 이어진 문 앞에 세우고, 싸움이 시작될 때까지 방 가운데 쪽으로 걷는다. */
    public static void walkIn(GameSession g, Room r) {
        Door d = null;
        for (Door x : r.doors) if (!x.dead()) { d = x; break; }
        g.hero.x = d.arriveX;
        g.hero.y = d.arriveY;
        int t = g.balance.tileSize;
        Controls c = new Controls();
        for (int i = 0; i < 240 && !g.fighting(); i++) {
            float dx = (r.centerTileX() + 0.5f) * t - g.hero.x, dy = (r.centerTileY() + 0.5f) * t - g.hero.y;
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            c.moveX = l > 1 ? dx / l : 0;
            c.moveY = l > 1 ? dy / l : 0;
            g.update(1 / 60f, c);
            g.pollEvents();
        }
    }

    /** 문 d 에서 통로 가운데 줄을 따라 짝 문 앞까지 가는 길목들 (월드 좌표). */
    public static List<float[]> waypoints(Door d, int tileSize) {
        Corridor c = d.link.corridor;
        List<int[]> p = new ArrayList<>(c.path);
        if (d == d.link.doorB) Collections.reverse(p);
        List<float[]> w = new ArrayList<>();
        for (int[] t : p) w.add(new float[]{(t[0] + 0.5f) * tileSize, (t[1] + 0.5f) * tileSize});
        Door to = d.partner();
        w.add(new float[]{to.arriveX, to.arriveY});
        return w;
    }

    /** 문 d 앞에 세우고 통로를 따라 짝 문 앞까지 걷는다 (게임 입력으로). 다 왔으면 true. */
    public static boolean walkThrough(GameSession g, Door d) {
        g.hero.x = d.arriveX;
        g.hero.y = d.arriveY;
        return walkThrough(g, d, new ArrayList<>(), () -> false);
    }

    /** 지금 자리에서 문 d 의 통로를 따라 걷는다. stop 이 참이 되거나 짝 문 앞에 닿으면 멈춘다. 다 왔으면 true. */
    public static boolean walkThrough(GameSession g, Door d, List<GameEvent> out, BooleanSupplier stop) {
        List<float[]> w = waypoints(d, g.balance.tileSize);
        int i = 0;
        Controls c = new Controls();
        for (int f = 0; f < 60 * 120 && i < w.size(); f++) {
            float dx = w.get(i)[0] - g.hero.x, dy = w.get(i)[1] - g.hero.y;
            float l = (float) Math.sqrt(dx * dx + dy * dy);
            if (l < 6) {
                i++;
                continue;
            }
            c.moveX = dx / l;
            c.moveY = dy / l;
            g.update(1 / 60f, c);
            out.addAll(g.pollEvents());
            if (stop.getAsBoolean()) return false;
        }
        return i >= w.size();
    }

    public static WorldMap map() {
        return new WorldMap(MapDef.parse(read("assets/data/map.json")),
                RoomSceneLayout.parse(read("assets/data/natural-room-scenes.json")), balance().tileSize);
    }
}
