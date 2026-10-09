package com.yongsa.model;

/**
 * 씨앗값과 사용 횟수만으로 재현 가능한 난수.
 * 같은 (seed, index) 쌍은 항상 같은 값을 돌려주므로, 저장된 사용 횟수로 다음 추첨을 정확히 이어갈 수 있다.
 */
public final class Rng {
    private Rng() {}

    /** [0, 1) 범위의 값. */
    public static double at(long seed, long index) {
        long z = seed + (index + 1) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z = z ^ (z >>> 31);
        return (z >>> 11) * 0x1.0p-53;
    }

    /** 강화 판정에 쓰는 난수 입력. 테스트에서 지정한 값을 주입할 수 있다. */
    public interface Source {
        double next();
    }

    /** 게임 상태의 강화 씨앗값과 사용 횟수를 사용하는 기본 입력. 하나를 뽑을 때마다 사용 횟수가 1 늘어난다. */
    public static Source enhanceSource(GameState s) {
        return () -> at(s.enhanceSeed, s.enhanceUses++);
    }

    /** 광맥 배치용 별도 입력. 강화 사용 횟수에는 영향을 주지 않는다. */
    public static Source veinSource(GameState s) {
        return () -> at(s.veinSeed, s.veinUses++);
    }
}
