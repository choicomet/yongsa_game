package com.yongsa.config;

/** 각성 능력 수치. 셋 다 성검보다 약하게 잡았다. */
public class AbilityDef {
    /** 흑철 분쇄검: 기본 공격 n번째마다 넓은 충격파. */
    public int shockwaveEvery = 3;
    public float shockwaveMultiplier = 1.2f;
    public float shockwaveRange = 96;
    public float shockwaveArcDegrees = 150;
    /** 혈월 폭주검: 적을 쓰러뜨리면 그 자리에서 피의 폭발. */
    public float burstRadius = 90;
    public float burstMultiplier = 0.8f;
    /** 천공 뇌격검: 기본 공격이 맞으면 일정 확률로 근처 적에게 번개. */
    public float chainChance = 0.25f;
    public float chainMultiplier = 0.6f;
    public int chainTargets = 2;
    public float chainRange = 150;
}
