package com.yongsa.config;

public class DungeonDef {
    public String id;
    public String name;
    public String ore;
    public String monster;
    public String boss;
    /** 앞 던전 id. 그 보스를 잡아야 열린다. */
    public String requires = "";
    /** 보스 힘을 흡수할 때 오르는 최대 체력. */
    public int maxHpGain;
    /**
     * 보스 보물상자에 든 이 던전 광물 (2026-10-12 결정: 처치할 때마다 chestOreMin~chestOreMax 무작위).
     * 첫 처치 상자에는 보스의 정수도 들어 있다.
     */
    public int chestOreMin = 30;
    public int chestOreMax = 40;
    /** 이 던전 광물로 만드는 무기 id ("다음 할 일" 안내용). */
    public String weapon;
    /** 보스 첫 처치 때 흡수하지 못한 힘이 던전에 스며들어 잡몹이 한 번 강해진다. */
    public float buffHpMultiplier = 1.3f;
    public float buffAttackMultiplier = 1.2f;
}
