package com.yongsa.model;

/** 각성 능력. 무기를 들고 있거나, 흡수해서 용사에게 남았으면 쓴다. 셋 다 성검보다 약하게 잡았다. */
public enum AwakenAbility {
    SHOCKWAVE("흑철 충격파", "기본 공격 세 번째마다 앞쪽 넓은 범위에 충격파를 날린다."),
    BLOOD_BURST("혈월 폭주", "적을 쓰러뜨리면 그 자리에서 피의 폭발이 일어나 주변 적을 벤다."),
    CHAIN_LIGHTNING("천공 뇌격", "기본 공격이 맞으면 가끔 근처 적에게 번개가 튄다.");

    public final String title;
    public final String description;

    AwakenAbility(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /** 저장 파일 이름 → 값. 예전 저장의 LIFESTEAL은 BLOOD_BURST로 읽는다. */
    public static AwakenAbility parse(String name) {
        if ("LIFESTEAL".equals(name)) return BLOOD_BURST;
        return valueOf(name);
    }
}
