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

    /** 강화 단계를 바로 정한다 (테스트 전용, 강화 규칙을 거치지 않음). */
    public static void setLevel(com.yongsa.model.Weapon w, int level) {
        try {
            var m = com.yongsa.model.Weapon.class.getDeclaredMethod("setLevel", int.class);
            m.setAccessible(true);
            m.invoke(w, level);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 세 보스를 잡고 구슬을 모은 뒤 성검을 해방해 마왕전으로 들어간다. */
    public static void toArena(GameSession s) {
        for (String d : new String[]{"left", "right", "top"}) {
            s.absorbService.absorbBoss(s.balance.dungeon(d), s.hero, s.progress, s.inventory, s.random);
            s.progress.addOrb(d);
        }
        s.unit.teleport(s.map.sigil.x, s.map.sigil.y - 10);
        var c = new com.yongsa.model.Controls();
        s.update(1f / 60f, c);
        c.interact = true;
        s.update(1f / 60f, c);
        c.interact = false;
        for (int i = 0; i < 600; i++) s.update(1f / 60f, c);
        if (!s.inArena()) throw new IllegalStateException("마왕전에 들어가지 못함");
    }
}
