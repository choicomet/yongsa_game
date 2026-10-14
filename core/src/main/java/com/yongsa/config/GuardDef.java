package com.yongsa.config;

/**
 * 막기 (Ctrl 누르고 있기). 게이지는 "막을 수 있는 시간(초)"이다.
 * 누르고 있으면 초당 1씩 줄고, 막을 때마다 hitCost(보스 큰 공격은 heavyHitCost)만큼 더 준다.
 * 0이 되면 막기가 풀리고 breakTime 동안 다시 막을 수 없다.
 */
public class GuardDef {
    public float max = 3.0f;
    public float regenPerSecond = 1.0f;
    /** 손을 뗀 뒤 이만큼 지나야 차오르기 시작한다. */
    public float regenDelay = 0.6f;
    public float hitCost = 0.35f;
    public float heavyHitCost = 0.7f;
    public float breakTime = 2.0f;
    /** 막는 동안 이동 속도 배율. */
    public float moveMultiplier = 0.45f;
    /** 바라보는 방향 기준 이 각도 안에서 온 공격만 막는다. */
    public float frontArcDegrees = 200;
}
