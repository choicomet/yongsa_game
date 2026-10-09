package com.yongsa;

import com.yongsa.config.Balance;
import com.yongsa.model.GameState;
import com.yongsa.model.Rng;
import com.yongsa.model.Settings;
import com.yongsa.world.DungeonGenerator;
import com.yongsa.world.DungeonMap;
import com.yongsa.world.RoomDef;

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
        if (map == null) map = DungeonGenerator.generate(12345L, balance());
        return map;
    }

    public static GameState newGame() {
        return GameState.newGame(12345L, balance().dataVersion, Settings.defaults(balance()));
    }

    /** 첫 구역 입구의 안전한 광물방. */
    public static RoomDef oreRoom() {
        return map().entranceRoom("mine");
    }

    /** 첫 구역 본 경로의 첫 전투방. */
    public static RoomDef firstBattle() {
        return map().firstCombatRoom("mine");
    }

    /** 구역 봉인 문 (캠프 쪽). */
    public static com.yongsa.world.DoorDef gate(String zone) {
        for (com.yongsa.world.DoorDef d : map().doors) if (d.rule.equals("zone:" + zone)) return d;
        throw new AssertionError("봉인 문 없음: " + zone);
    }

    /** 방의 입구 문 중 하나. */
    public static com.yongsa.world.DoorDef doorOf(RoomDef r) {
        return map().doorsOf(r.id).get(0);
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
