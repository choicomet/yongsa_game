package com.yongsa.model;

/**
 * 성검 해방 → 정수가 땅에 박힘 → 땅이 갈라져 추락 → 마왕전 (2026-10-10 결정).
 * 시간만 흐르고, 그리는 쪽은 각 값(0~1)을 읽어 연출한다. 연출 속도 설정이 speed에 들어온다.
 */
public class Cutscene {

    /** full: 처음 해방할 때의 긴 장면. 아니면 재도전용 짧은 장면(갈라짐부터). */
    public final boolean full;
    private float t;
    private float speed = 1f;

    // 시간표 (초, 보통 속도 기준)
    static final float SWORD_END = 2.0f, SIGIL_END = 3.4f, ORBS_END = 4.8f, CRACK_END = 6.2f, FALL_END = 7.4f;

    public Cutscene(boolean full) {
        this.full = full;
        if (!full) t = ORBS_END;
    }

    public void setSpeed(float s) {
        speed = Math.max(0.5f, s);
    }

    public void update(float delta) {
        t += delta * speed;
    }

    public boolean done() {
        return t >= FALL_END;
    }

    /** 성검이 깨어나는 빛 (0~1). */
    public float swordGlow() { return ramp(0.3f, SWORD_END); }
    /** 바닥 문양 빛 (0~1). */
    public float sigilGlow() { return ramp(SWORD_END - 0.4f, SIGIL_END); }
    /** 정수가 떠올랐다 박히는 진행 (0~1). */
    public float orbs() { return ramp(SIGIL_END, ORBS_END); }
    /** 땅 갈라짐 (0~1). */
    public float crack() { return ramp(ORBS_END, CRACK_END); }
    /** 추락: 용사가 작아지며 화면이 어두워짐 (0~1). */
    public float fall() { return ramp(CRACK_END, FALL_END); }

    public boolean shaking() {
        return t > ORBS_END && t < CRACK_END + 0.4f;
    }

    public String caption() {
        if (!full) return t < CRACK_END ? "땅이 다시 갈라진다…" : "";
        if (t < SWORD_END) return "봉인이 풀린다… 성검이 깨어났다!";
        if (t < SIGIL_END) return "캠프 바닥의 문양이 빛나기 시작한다.";
        if (t < ORBS_END) return "세 보스의 정수가 손을 떠나 땅에 박힌다!";
        if (t < CRACK_END) return "땅이 갈라진다!";
        return "";
    }

    private float ramp(float a, float b) {
        if (t <= a) return 0;
        if (t >= b) return 1;
        return (t - a) / (b - a);
    }
}
