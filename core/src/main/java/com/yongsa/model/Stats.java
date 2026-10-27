package com.yongsa.model;

/** 엔딩에 보여줄 플레이 기록. */
public class Stats {
    public int enhanceTries;
    public int enhanceSuccess;
    /** 그중 대성공 (+2). enhanceSuccess 에도 함께 센다. */
    public int enhanceGreat;
    public int enhanceDown;
    public int destroyed;
    public int crafted;
    public int deaths;
    public int monstersKilled;
    public int oreMined;
    public float playSeconds;
}
