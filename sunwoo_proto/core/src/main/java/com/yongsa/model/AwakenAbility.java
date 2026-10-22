package com.yongsa.model;

/** 각성 능력. 무기를 들고 있거나, 흡수해서 용사에게 남았으면 쓴다. */
public enum AwakenAbility {
    SHOCKWAVE("흑철 충격파", "기본 공격 세 번째마다 앞쪽 넓은 범위에 충격파를 날린다."),
    LIFESTEAL("혈월 탐식", "준 피해의 일부만큼 체력을 회복한다."),
    CHAIN_LIGHTNING("천공 뇌격", "기본 공격이 맞으면 가끔 근처 적에게 번개가 튄다.");

    public final String title;
    public final String description;

    AwakenAbility(String title, String description) {
        this.title = title;
        this.description = description;
    }
}
