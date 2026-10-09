package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 통로 안의 문. 잠김 규칙은 Tiled 속성으로 정의한다.
 * rule: "zone:&lt;구역&gt;" | "weapon" | "cleared:&lt;방&gt;" | "" , combat: 전투 중 잠그는 방 목록
 */
public class DoorDef {
    public final String id;
    public final Rectangle bounds;
    public final String rule;
    public final List<String> combatRooms;

    public DoorDef(String id, Rectangle bounds, String rule, String combat) {
        this.id = id;
        this.bounds = bounds;
        this.rule = rule == null ? "" : rule;
        this.combatRooms = combat == null || combat.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(combat.split("\\s*,\\s*"));
    }

    public boolean horizontal() {
        return bounds.width >= bounds.height;
    }
}
