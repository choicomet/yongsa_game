package com.yongsa.config;

/**
 * 성검(해방 뒤)이 용사가 만든 검들에서 얻는 힘.
 * 공격력 = 성검 기본 + (검 종류마다 가장 센 검의 공격력 합) × shareRatio.
 * 흡혈 = max(종류마다 가장 높은 흡혈의 합 × lifestealShare, 가장 높은 흡혈 하나).
 */
public class HolyDef {
    public float shareRatio = 0.4f;
    public float lifestealShare = 0.5f;
}
