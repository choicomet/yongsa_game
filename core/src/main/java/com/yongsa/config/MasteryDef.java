package com.yongsa.config;

/**
 * 강화 숙련도 (2026-10-27 김선우 결정). 검마다가 아니라 용사 한 명의 대장장이 실력이라 모든 검이 함께 쓴다.
 * 레벨이 오를수록 하락·파괴 확률이 줄어 그만큼 유지가 되고(성공 확률은 그대로), 마지막에는 대성공이 조금 는다.
 */
public class MasteryDef {
    public int maxLevel = 20;
    /** 이 레벨에서 하락·파괴 확률이 0이 된다. 그 전에는 레벨에 비례해 줄어든다. */
    public int downZeroLevel = 17, destroyZeroLevel = 15;
    /** 이 레벨부터 레벨마다 대성공 +greatBonusStep%p (유지에서 옮긴다). */
    public int greatBonusFromLevel = 18;
    public double greatBonusStep = 1;
    /** 결과마다 얻는 기본 경험치. */
    public int expSuccess = 20, expGreat = 30, expKeep = 0, expDown = 30, expDestroy = 60;
    /** 강화하기 전 단계(+0~+9)마다 경험치 배율. */
    public float[] expMultiplier = new float[0];
    /** 다음 레벨까지 필요 경험치 = needBase + needLinear×Lv + needSquare×Lv² (반올림). */
    public float needBase = 12, needLinear = 5, needSquare = 0.5f;
}
