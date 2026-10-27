package com.yongsa.combat;

import com.badlogic.gdx.math.Rectangle;

import java.util.HashSet;
import java.util.Set;

/**
 * 공격 한 번. 이미 때린 대상 목록을 가지고 있어서 같은 몬스터는 한 번만 맞는다 (FR-전투-14).
 */
public class Attack {

    public enum Shape { ARC, CIRCLE, SEGMENT }

    public enum Kind { BASIC, WHIRL, DASH, SHOCKWAVE }

    public final Kind kind;
    public final Shape shape;
    public float ox, oy, dirX, dirY, range, arcDeg, width;
    /** SEGMENT 끝점 (돌진 중 계속 갱신). */
    public float ex, ey;
    public final float multiplier;
    private final Set<Object> hit = new HashSet<>();

    private Attack(Kind kind, Shape shape, float multiplier) {
        this.kind = kind;
        this.shape = shape;
        this.multiplier = multiplier;
    }

    public static Attack arc(Kind kind, float ox, float oy, float dirX, float dirY, float range, float arcDeg, float mult) {
        Attack a = new Attack(kind, Shape.ARC, mult);
        a.ox = ox; a.oy = oy; a.dirX = dirX; a.dirY = dirY; a.range = range; a.arcDeg = arcDeg;
        return a;
    }

    public static Attack circle(Kind kind, float ox, float oy, float radius, float mult) {
        Attack a = new Attack(kind, Shape.CIRCLE, mult);
        a.ox = ox; a.oy = oy; a.range = radius;
        return a;
    }

    public static Attack segment(Kind kind, float x1, float y1, float width, float mult) {
        Attack a = new Attack(kind, Shape.SEGMENT, mult);
        a.ox = x1; a.oy = y1; a.ex = x1; a.ey = y1; a.width = width;
        return a;
    }

    /** 처음 맞는 대상이면 true를 돌려주고 목록에 넣는다. */
    public boolean tryHit(Object target, Rectangle body) {
        if (hit.contains(target)) return false;
        boolean inside = switch (shape) {
            case ARC -> HitTest.arc(ox, oy, dirX, dirY, range, arcDeg, body);
            case CIRCLE -> HitTest.circle(ox, oy, range, body);
            case SEGMENT -> HitTest.segment(ox, oy, ex, ey, width, body);
        };
        if (!inside) return false;
        hit.add(target);
        return true;
    }

    public int hitCount() {
        return hit.size();
    }
}
