package com.yongsa.world;

/**
 * 렌더링 프레임 시간과 무관하게 일정한 간격으로 시뮬레이션을 진행시키는 누산기.
 * 긴 일시정지나 포커스 복귀 때 쌓인 시간을 한꺼번에 적용하지 않도록 한 프레임 최대 시간을 제한한다.
 */
public class FixedStepper {
    public final float step;
    public final float maxFrame;
    private float acc;

    public FixedStepper(float step, float maxFrame) {
        this.step = step;
        this.maxFrame = maxFrame;
    }

    /** 이번 프레임에 실행할 간격 수. */
    public int advance(float frameDelta) {
        acc += Math.min(Math.max(frameDelta, 0f), maxFrame);
        int n = 0;
        while (acc >= step) {
            acc -= step;
            n++;
        }
        return n;
    }

    /** 일시정지 중에는 시간을 버린다. */
    public void reset() {
        acc = 0;
    }
}
