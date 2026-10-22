package com.yongsa.combat;

/** 보스 특수 공격 종류 (FR-전투-10). 모두 공격 전에 예고를 보여준다. */
public enum BossPattern {
    /** 용사 발밑에 빨간 원을 1초 보여준 뒤 내려친다. */
    SLAM,
    /** 돌진할 방향에 선을 보여준 뒤 빠르게 돌진한다. */
    CHARGE,
    /** 잠깐 힘을 모은 뒤 사방으로 투사체를 쏜다. */
    NOVA
}
