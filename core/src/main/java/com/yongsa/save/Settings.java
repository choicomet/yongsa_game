package com.yongsa.save;

/** 설정 (UI-10). 바로 적용되고 따로 저장된다. */
public class Settings {
    public float volume = 0.7f;
    public boolean fullscreen = false;
    /** 연출 속도: 1 보통, 2 빠르게. */
    public float effectSpeed = 1f;
    /** 타격·보스 공격 때 화면 흔들림. */
    public boolean screenShake = true;
    /** 화면 배율 (기존 화면 대비 %). 휠이나 +/- 로 바꾸며, 보스방은 따로 기억한다. */
    public int zoomNormal = 115;
    public int zoomBoss = 100;
    /** 개발용 (F1): 광물이 줄지 않는다. */
    public boolean devInfiniteOre = false;
    /** 개발용 (F1): 맞아도 체력이 줄지 않는다. */
    public boolean devGodMode = false;
    /** 개발용 (F1): 걸을 수 있는 곳을 초록으로 표시한다. 저장하지 않아도 되지만 다른 개발 설정과 같이 둔다. */
    public boolean devShowWalkable = false;
}
