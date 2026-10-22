package com.yongsa.world;

import com.badlogic.gdx.math.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * 충돌: 벽 타일, 잠긴 문, 봉인된 입구, 고정 물체(침대·대장간), 광맥.
 * 물리 엔진 없이 사각형 겹침으로 처리하고, 빠르게 움직일 때는 작은 걸음으로 나눠 벽을 뚫지 않게 한다.
 */
public class CollisionWorld {

    private final WorldMap map;
    private final List<Door> doors;
    private final List<Gate> gates;
    private final List<Rectangle> obstacles = new ArrayList<>();
    private final List<Room> rooms;
    private final Rectangle tmp = new Rectangle();

    public CollisionWorld(WorldMap map, List<Door> doors, List<Gate> gates, List<Room> rooms) {
        this.map = map;
        this.doors = doors;
        this.gates = gates;
        this.rooms = rooms;
    }

    public void addObstacle(Rectangle r) {
        if (r.width > 0 && r.height > 0) obstacles.add(new Rectangle(r));
    }

    public boolean blocked(Rectangle r) {
        int ts = map.tileSize;
        int tx0 = (int) Math.floor(r.x / ts), ty0 = (int) Math.floor(r.y / ts);
        int tx1 = (int) Math.floor((r.x + r.width - 0.001f) / ts), ty1 = (int) Math.floor((r.y + r.height - 0.001f) / ts);
        for (int ty = ty0; ty <= ty1; ty++)
            for (int tx = tx0; tx <= tx1; tx++)
                if (map.solidTile(tx, ty)) return true;
        for (Rectangle o : obstacles) if (o.overlaps(r)) return true;
        for (Door d : doors) if (d.locked() && d.bounds.overlaps(r)) return true;
        for (Gate g : gates) if (!g.open() && g.bounds.overlaps(r)) return true;
        for (Room room : rooms) {
            if (room.veins.isEmpty() || !room.bounds.overlaps(r)) continue;
            for (Vein v : room.veins) if (!v.depleted() && v.body.overlaps(r)) return true;
        }
        return false;
    }

    /** body를 (dx, dy)만큼 옮긴다. 막히면 그 축은 막히기 직전까지. 막혔으면 true. */
    public boolean move(Rectangle body, float dx, float dy) {
        boolean hit = false;
        int steps = Math.max(1, (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)) / 4f));
        float sx = dx / steps, sy = dy / steps;
        for (int i = 0; i < steps; i++) {
            if (sx != 0) {
                tmp.set(body).x += sx;
                if (blocked(tmp)) hit = true; else body.x = tmp.x;
            }
            if (sy != 0) {
                tmp.set(body).y += sy;
                if (blocked(tmp)) hit = true; else body.y = tmp.y;
            }
        }
        return hit;
    }
}
