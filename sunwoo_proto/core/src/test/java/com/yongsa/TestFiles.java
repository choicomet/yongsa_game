package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.GameSession;
import com.yongsa.world.WorldMap;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** 테스트는 assets 폴더(작업 폴더)의 실제 balance.json, world.tmx를 읽는다. */
public final class TestFiles {
    private TestFiles() {
    }

    public static String read(String path) {
        try {
            return Files.readString(Path.of(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Balance balance() {
        return Balance.fromJson(read("data/balance.json"));
    }

    public static WorldMap map() {
        return WorldMap.parse(read("maps/world.tmx"));
    }

    public static GameSession session(long seed) {
        return new GameSession(balance(), map(), new Random(seed));
    }
}
