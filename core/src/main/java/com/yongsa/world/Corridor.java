package com.yongsa.world;

import java.util.ArrayList;
import java.util.List;

/**
 * 두 방의 출구를 잇는 3칸 폭 통로 (맵이 끊기지 않고 하나로 이어진다).
 * 이음(link)의 문이 닫히면(던전 봉인, 방 잠금) 통로도 막힌다.
 */
public class Corridor {
    public final Link link;
    /** 바닥·벽 그림 테마: 이어진 던전 (캠프·마을끼리면 CAMP). */
    public final String theme;
    /** 통로 바닥 칸들 (x, y). */
    public final List<int[]> tiles = new ArrayList<>();
    /** 가운데 줄 칸들: doorA 출구에서 doorB 출구까지 차례대로. */
    public final List<int[]> path = new ArrayList<>();

    Corridor(Link link, String theme) {
        this.link = link;
        this.theme = theme;
    }

    public boolean closed() {
        return link.doorA.isClosed() || link.doorB.isClosed();
    }
}
