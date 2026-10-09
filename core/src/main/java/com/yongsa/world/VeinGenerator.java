package com.yongsa.world;

import com.yongsa.config.Balance;
import com.yongsa.model.GameState;
import com.yongsa.model.MineRoomState;
import com.yongsa.model.Rng;
import com.yongsa.model.VeinState;

import java.util.ArrayList;
import java.util.List;

/**
 * 채굴 회차에서 광물방에 처음 들어갈 때 서로 다른 후보 지점을 뽑아 광맥을 생성한다.
 * 생성 결과는 GameState 에 저장되어, 다시 들어오거나 게임을 다시 켜도 바뀌지 않는다.
 */
public final class VeinGenerator {
    private VeinGenerator() {}

    /** 이미 생성되어 있으면 그대로 돌려주고, 없으면 새로 생성해 상태에 기록한다. */
    public static MineRoomState ensure(Balance b, GameState s, DungeonMap map, String roomId) {
        MineRoomState existing = s.mineRoom(roomId);
        if (existing != null) return existing;
        List<String> ids = new ArrayList<>();
        List<String> visible = new ArrayList<>();
        for (MapObject c : map.veinCandidates(roomId)) {
            ids.add(c.name);
            if ("true".equals(c.prop("visible"))) visible.add(c.name);
        }
        MineRoomState m = new MineRoomState(roomId);
        for (String id : pick(ids, visible, b.mining.veinsPerRoom, Rng.veinSource(s))) m.veins.add(new VeinState(id));
        s.mineRooms.add(m);
        return m;
    }

    /**
     * 후보 중 count 개를 중복 없이 고른다. 최소 한 개는 입구에서 보이는 후보에서 고른다.
     * 후보가 부족하면 맵 검증 단계에서 걸러져야 하므로 여기서는 예외로 처리한다.
     */
    public static List<String> pick(List<String> candidates, List<String> visible, int count, Rng.Source rng) {
        if (candidates.size() < count) throw new IllegalStateException("광맥 후보가 부족합니다: " + candidates.size());
        List<String> pool = new ArrayList<>(candidates);
        List<String> out = new ArrayList<>();
        if (!visible.isEmpty()) {
            String first = visible.get((int) (rng.next() * visible.size()));
            out.add(first);
            pool.remove(first);
        }
        while (out.size() < count) {
            int i = (int) (rng.next() * pool.size());
            out.add(pool.remove(i));
        }
        return out;
    }
}
