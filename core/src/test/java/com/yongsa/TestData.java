package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.GameState;
import com.yongsa.model.Rng;
import com.yongsa.model.Settings;
import com.yongsa.world.DungeonMap;

/** 테스트 공용 데이터. 작업 폴더는 assets 이다 (core/build.gradle 참고). */
public final class TestData {
    private TestData() {}

    private static Balance balance;
    private static DungeonMap map;

    public static Balance balance() {
        if (balance == null) balance = Balance.loadFromPath("data/balance.json");
        return balance;
    }

    public static DungeonMap map() {
        if (map == null) map = DungeonMap.load("maps/dungeon.tmx");
        return map;
    }

    public static GameState newGame() {
        return GameState.newGame(12345L, balance().dataVersion, Settings.defaults(balance()));
    }

    /** 지정한 값을 차례대로 돌려주는 난수 입력. */
    public static Rng.Source fixed(double... values) {
        int[] i = {0};
        return () -> {
            if (i[0] >= values.length) throw new AssertionError("준비한 난수보다 많이 사용했습니다");
            return values[i[0]++];
        };
    }

    /** 사용되면 실패하는 난수 입력 (보장 시도에서 난수를 소비하지 않는지 확인). */
    public static Rng.Source forbidden() {
        return () -> {
            throw new AssertionError("난수를 소비하면 안 됩니다");
        };
    }
}
