package com.yongsa.combat;

/**
 * 바닥에 생기는 위험 지대. 모두 먼저 예고(바닥 표시)를 보여주고, 그 자리에서 터진다 (따라오지 않는다).
 * <ul>
 *   <li>BONE_SPIKE: 리치의 뼈 가시. 예고 뒤 솟아오른다.</li>
 *   <li>LIGHTNING: 마룡의 번개. 맞으면 감전.</li>
 *   <li>TENTACLE: 마왕 2페이즈의 촉수.</li>
 *   <li>POISON_CLOUD: 마룡의 독구름. 한동안 남아 있고 닿으면 중독(지속 시간 초기화).</li>
 * </ul>
 * 막기로 막을 수 없다 (발밑·하늘에서 오므로). 걸어서 피해야 한다.
 */
public class Hazard {

    public enum Type { BONE_SPIKE, LIGHTNING, TENTACLE, POISON_CLOUD }

    /** 터진 뒤 그림을 보여주는 시간. */
    public static final float STRIKE_TIME = 0.45f;
    /** 독구름이 사라지는 시간. */
    public static final float FADE_TIME = 0.8f;

    public final Type type;
    public final float x, y, radius;
    /** 나타나기 전 기다리는 시간. */
    public final float delay;
    /** 예고 시간 (이동해서 피할 시간). */
    public final float warning;
    /** 독구름이 남아 있는 시간 (나머지는 0). */
    public final float life;
    public final int damage;
    public float time;
    public boolean fired;

    public Hazard(Type type, float x, float y, float radius, float delay, float warning, float life, int damage) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.radius = radius;
        this.delay = delay;
        this.warning = warning;
        this.life = life;
        this.damage = damage;
    }

    /** 나타난 뒤 지난 시간 (음수면 아직 안 나타남). */
    public float local() {
        return time - delay;
    }

    public boolean visible() {
        return local() >= 0;
    }

    /** 예고 중이면 0~1 진행도, 아니면 -1. */
    public float warnProgress() {
        float t = local();
        if (t < 0 || t >= warning) return -1;
        return t / warning;
    }

    /** 터진 뒤(또는 구름이 퍼진 뒤) 지난 시간. */
    public float activeTime() {
        return local() - warning;
    }

    public boolean cloudActive() {
        return type == Type.POISON_CLOUD && activeTime() >= 0 && activeTime() < life;
    }

    public boolean done() {
        float a = activeTime();
        return type == Type.POISON_CLOUD ? a >= life + FADE_TIME : a >= STRIKE_TIME;
    }

    public boolean contains(float px, float py) {
        float dx = px - x, dy = py - y;
        return dx * dx + dy * dy <= radius * radius;
    }
}
