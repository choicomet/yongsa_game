package com.yongsa.config;

public class EnhanceDef {
    public int maxLevel = 10;
    /** 각성 무기를 하나 흡수할 때마다 모든 무기 공격력 +10%. */
    public float absorbAttackBonus = 0.10f;
    /** 파괴되면 제작 비용의 절반을 돌려받는다. */
    public float destroyRefundRatio = 0.5f;
    /** 분해하면 4개 + 강화에 쓴 광물의 절반. */
    public int dismantleBase = 4;
    public float dismantleEnhanceRatio = 0.5f;
    /** 강화 단계별 흡혈: 용사 최대 체력의 %. 길이 maxLevel+1 (0~10강). */
    public float[] lifesteal = new float[0];
    public EnhanceRuleDef[] rules = new EnhanceRuleDef[0];
    /** 강화 숙련도. */
    public MasteryDef mastery = new MasteryDef();
}
