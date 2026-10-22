package com.yongsa.config;

/** 마룡 브레스 상태이상. 독·화상·감전은 지속 중 다시 맞으면 시간이 처음으로 돌아간다. */
public class StatusDef {
    public float poisonDuration = 5f;
    /** 독: 초당 최대 체력의 1%. */
    public float poisonMaxHpRatioPerSecond = 0.01f;
    public float burnDuration = 5f;
    public float burnPerSecond = 3f;
    /** 감전: 스킬(Q·L)을 못 쓴다. 이동·기본 공격·회피·막기는 된다. */
    public float shockDuration = 5f;
    /** 빙결: 움직이지도 행동하지도 못한다. 다시 맞아도 연장되지 않는다. */
    public float freezeDuration = 3f;
    /** 둔화: 늪. 걸려 있는 동안 다시 밟아도 늘어나지 않고, 풀린 뒤 늪 위에 slowRetouchTime초 계속 있으면 다시 걸린다. */
    public float slowDuration = 5f;
    public float slowRatio = 0.3f;
    public float slowRetouchTime = 2f;
}
