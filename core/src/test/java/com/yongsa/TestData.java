package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.Controls;
import com.yongsa.model.GameSession;
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

    public static WorldMap map() {
        return new WorldMap(MapDef.parse(read("assets/data/map.json")),
                RoomSceneLayout.parse(read("assets/data/natural-room-scenes.json")), balance().tileSize);
    }
}
