package com.yongsa.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.yongsa.model.GameSession;
import com.yongsa.model.Goal;
import com.yongsa.world.Gate;
import com.yongsa.world.Portal;
import com.yongsa.world.Room;
import com.yongsa.world.RoomKind;
import com.yongsa.world.WorldMap;

import java.util.List;

/**
 * 지도 그리기 (원형 미니맵과 M 지도가 함께 쓴다, FR-맵-10).
 * 보이는 방(지금 방·비운 방)은 채워서, 열린 던전의 모르는 방은 테두리만(큰 지도), 보스방 위치는 항상 표시.
 * "다음 할 일" 목적지까지 길을 노란 선으로 그린다.
 */
public class MapPainter {

    public enum Mode { MINI, FULL }

    private final GameSession s;
    private float scale, ox, oy;

    public MapPainter(GameSession s) {
        this.s = s;
    }

    /** 세계 좌표 (wx, wy)가 화면 (sx, sy)에 오도록, 1px당 scale. */
    public void setTransform(float wx, float wy, float sx, float sy, float scale) {
        this.scale = scale;
        this.ox = sx - wx * scale;
        this.oy = sy - wy * scale;
    }

    public float tx(float x) { return ox + x * scale; }
    public float ty(float y) { return oy + y * scale; }

    private void rect(ShapeRenderer sh, Rectangle r, float pad) {
        sh.rect(tx(r.x - pad), ty(r.y - pad), (r.width + pad * 2) * scale, (r.height + pad * 2) * scale);
    }

    public static Color dungeonColor(String d, Color out) {
        return switch (d) {
            case "left" -> out.set(0.55f, 0.45f, 0.38f, 1f);
            case "right" -> out.set(0.62f, 0.36f, 0.44f, 1f);
            case "top" -> out.set(0.42f, 0.48f, 0.78f, 1f);
            default -> out.set(0.6f, 0.55f, 0.48f, 1f);
        };
    }

    private boolean dungeonOpen(String d) {
        return s.progress.isOpen(s.balance.dungeon(d));
    }

    /** ShapeRenderer가 Filled로 begin 된 상태에서 부른다. */
    public void paint(ShapeRenderer sh, Mode mode, float time) {
        Color c = new Color();
        if (s.inArena()) {
            sh.setColor(0.35f, 0.12f, 0.18f, 1f);
            rect(sh, s.map.arena, 0);
        } else {
            // 통로
            for (WorldMap.CorridorDef cd : s.map.corridors) {
                String d = "camp".equals(cd.a()) ? s.roomById.get(cd.b()).dungeon : s.roomById.get(cd.a()).dungeon;
                boolean vis = s.visibility.corridor(cd);
                if (!vis && (mode == Mode.MINI || !dungeonOpen(d))) continue;
                dungeonColor(d, c);
                sh.setColor(c.r * (vis ? 0.8f : 0.35f), c.g * (vis ? 0.8f : 0.35f), c.b * (vis ? 0.8f : 0.35f), 1f);
                rect(sh, cd.bounds(), 0);
            }
            // 캠프
            sh.setColor(0.72f, 0.64f, 0.5f, 1f);
            rect(sh, s.map.camp, 0);
            // 방
            for (Room r : s.rooms) {
                boolean vis = s.visibility.room(r);
                boolean open = dungeonOpen(r.dungeon);
                if (vis) {
                    dungeonColor(r.dungeon, c);
                    if (r == s.overlapRoom()) c.mul(1.25f, 1.25f, 1.25f, 1f);
                    sh.setColor(c);
                    rect(sh, r.bounds, 0);
                } else if (mode == Mode.FULL && open) {
                    // 모르는 방: 어두운 칸 + 테두리
                    sh.setColor(0.13f, 0.12f, 0.17f, 1f);
                    rect(sh, r.bounds, 0);
                    dungeonColor(r.dungeon, c);
                    sh.setColor(c.r * 0.5f, c.g * 0.5f, c.b * 0.5f, 1f);
                    outline(sh, r.bounds, 1.2f);
                }
                if ((vis || (mode == Mode.FULL && open)) && r.kind == RoomKind.BOSS) {
                    float cx = tx(r.bounds.x + r.bounds.width / 2), cy = ty(r.bounds.y + r.bounds.height / 2);
                    boolean done = s.progress.defeated(r.dungeon);
                    sh.setColor(done ? 0.5f : 0.9f, done ? 0.5f : 0.15f, done ? 0.5f : 0.2f, 1f);
                    float k = Math.max(3f, 8f * scale * 12f);
                    sh.triangle(cx - k, cy + k * 0.6f, cx + k, cy + k * 0.6f, cx, cy - k);
                }
                if (vis && r.kind == RoomKind.ORE) {
                    for (com.yongsa.world.Vein v : r.veins) {
                        if (v.depleted()) continue;
                        sh.setColor(0.95f, 0.85f, 0.35f, 1f);
                        sh.circle(tx(v.x), ty(v.y), Math.max(1.5f, 10 * scale), 8);
                    }
                }
            }
            // 봉인된 입구
            for (Gate g : s.gates) {
                if (g.open()) continue;
                sh.setColor(0.95f, 0.2f, 0.2f, 1f);
                rect(sh, g.bounds, 6);
            }
            // 캠프 문양
            sh.setColor(1f, 0.85f, 0.4f, s.progress.allOrbs(s.balance) ? 0.6f + 0.4f * MathUtils.sin(time * 4) : 0.5f);
            sh.circle(tx(s.map.sigil.x), ty(s.map.sigil.y), Math.max(2.5f, 40 * scale), 16);
        }

        // 포탈·구슬
        for (Portal p : s.portals()) {
            if (!p.active()) continue;
            sh.setColor(0.4f, 0.85f, 1f, 1f);
            sh.circle(tx(p.x), ty(p.y), Math.max(3f, 22 * scale), 12);
        }
        for (GameSession.OrbDrop o : s.orbDrops) {
            sh.setColor(1f, 1f, 1f, 0.6f + 0.4f * MathUtils.sin(time * 6));
            sh.circle(tx(o.x()), ty(o.y()), Math.max(3f, 16 * scale), 12);
        }

        drawRoute(sh, time);

        // 용사 (바라보는 방향 화살표)
        float hx = tx(s.unit.centerX()), hy = ty(s.unit.centerY());
        float a = MathUtils.atan2(s.unit.dirY, s.unit.dirX), k = mode == Mode.MINI ? 7f : 8f;
        sh.setColor(0.1f, 0.1f, 0.12f, 1f);
        sh.circle(hx, hy, k + 1.5f, 16);
        sh.setColor(1f, 1f, 1f, 1f);
        sh.triangle(hx + MathUtils.cos(a) * k, hy + MathUtils.sin(a) * k,
                hx + MathUtils.cos(a + 2.4f) * k * 0.8f, hy + MathUtils.sin(a + 2.4f) * k * 0.8f,
                hx + MathUtils.cos(a - 2.4f) * k * 0.8f, hy + MathUtils.sin(a - 2.4f) * k * 0.8f);
    }

    private void outline(ShapeRenderer sh, Rectangle r, float w) {
        float x = tx(r.x), y = ty(r.y), x2 = tx(r.x + r.width), y2 = ty(r.y + r.height);
        sh.rectLine(x, y, x2, y, w);
        sh.rectLine(x2, y, x2, y2, w);
        sh.rectLine(x2, y2, x, y2, w);
        sh.rectLine(x, y2, x, y, w);
    }

    /** 다음 할 일 목적지까지 길: 방 가운데 → 통로 가운데 → 다음 방 가운데 … */
    private void drawRoute(ShapeRenderer sh, float time) {
        List<String> path = s.routeToGoal();
        if (path.isEmpty()) return;
        float px = s.unit.centerX(), py = s.unit.centerY();
        float alpha = 0.75f + 0.25f * MathUtils.sin(time * 5);
        sh.setColor(1f, 0.85f, 0.2f, alpha);
        float w = 3f;
        for (int i = 1; i < path.size(); i++) {
            WorldMap.CorridorDef c = corridor(path.get(i - 1), path.get(i));
            if (c != null) {
                float cx = c.bounds().x + c.bounds().width / 2, cy = c.bounds().y + c.bounds().height / 2;
                sh.rectLine(tx(px), ty(py), tx(cx), ty(cy), w);
                px = cx;
                py = cy;
            }
            float[] n = center(path.get(i));
            sh.rectLine(tx(px), ty(py), tx(n[0]), ty(n[1]), w);
            px = n[0];
            py = n[1];
        }
        Goal g = s.goal();
        float[] end = target(g, px, py);
        sh.rectLine(tx(px), ty(py), tx(end[0]), ty(end[1]), w);
        sh.circle(tx(end[0]), ty(end[1]), 6f, 16);
    }

    private float[] target(Goal g, float px, float py) {
        return switch (g.target()) {
            case FORGE -> new float[]{s.map.forge.x + s.map.forge.width / 2, s.map.forge.y};
            case SIGIL -> new float[]{s.map.sigil.x, s.map.sigil.y};
            case ORB -> s.orbDrops.isEmpty() ? new float[]{px, py} : new float[]{s.orbDrops.get(0).x(), s.orbDrops.get(0).y()};
            default -> new float[]{px, py};
        };
    }

    private WorldMap.CorridorDef corridor(String a, String b) {
        for (WorldMap.CorridorDef c : s.map.corridors) {
            if ((c.a().equals(a) && c.b().equals(b)) || (c.a().equals(b) && c.b().equals(a))) return c;
        }
        return null;
    }

    private float[] center(String node) {
        Rectangle r = "camp".equals(node) ? s.map.camp : s.roomById.get(node).bounds;
        return new float[]{r.x + r.width / 2, r.y + r.height / 2};
    }
}
