package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.world.MapDef;
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

    public static WorldMap map() {
        return new WorldMap(MapDef.parse(read("assets/data/map.json")));
    }
}
