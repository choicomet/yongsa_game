package com.yongsa.config;

/** 각성 능력 수치. 셋 다 깨어난 성검(공격력 200)보다 약하게 잡았다. */
public class AbilityDef {
    /** 흑철 분쇄검: 기본 공격 n번째마다 넓은 충격파. */
    public int shockwaveEvery = 3;
    public float shockwaveMultiplier = 1.2f;
    public float shockwaveRange = 96;
    public float shockwaveArcDegrees = 150;
    /** 혈월 탐식검: 준 피해의 일부만큼 회복. */
    public float lifestealRatio = 0.04f;
    /** 천공 뇌격검: 기본 공격이 맞으면 일정 확률로 근처 적에게 번개. */
    public float chainChance = 0.25f;
    public float chainMultiplier = 0.6f;
    public int chainTargets = 2;
    public float chainRange = 150;
}
