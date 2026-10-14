package com.yongsa.combat;

/** 잠깐 그렸다 사라지는 연출 (공격 범위, 충격파, 번개 등). 규칙에는 영향이 없다. */
public class Effect {
    public enum Type { SLASH, WHIRL, SHOCKWAVE, LIGHTNING, SLAM, HIT, DASH_TRAIL, BOUNCE, ORB_BURST, HEAL,
        BLOOD_BURST, BLOCK, GUARD_BREAK, FREEZE, PHASE_SHIFT,
        /** 소환·순간이동: 작은 보랏빛 고리. */
        SUMMON,
        /** 마왕 공간 절단: 잘린 경계를 따라 거대한 발톱 자국 (rect). */
        REND,
        /** 보물상자 열림: 빛기둥과 반짝이. */
        CHEST,
        /** 마룡 번개가 떨어진 자리: 큰 섬광·그을음. */
        THUNDER,
        /** 부서지는 보스: 먼지·재. */
        DEATH_DUST,
        /** 잡몹 공격 모션: 베기(부채꼴 궤적) / 찌르기(앞으로 뻗는 창끝 궤적). dirX·dirY·radius·arc 사용. */
        MOB_SLASH, MOB_THRUST,
        /** 해골 기사가 용사의 공격을 막음: 칼날 불꽃. */
        PARRY }

    public final Type type;
    public float x, y, x2, y2, radius, dirX, dirY, arc;
    /** 사각형 범위가 있는 연출 (공간 절단). */
    public com.badlogic.gdx.math.Rectangle rect;
    public float time;
    public final float duration;

    public Effect(Type type, float x, float y, float duration) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.duration = duration;
    }

    public float progress() {
        return Math.min(1f, time / duration);
    }

    public boolean done() {
        return time >= duration;
    }
}
