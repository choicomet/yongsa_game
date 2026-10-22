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
    /** 첫 처치 광물 보상. */
    public int bossOreReward;
    /** 다시 생긴 보스를 잡으면 광물만 이만큼 (흡수·구슬 없음). */
    public int respawnOreMin = 4;
    public int respawnOreMax = 5;
    /** 이 던전 광물로 만드는 무기 id ("다음 할 일" 안내용). */
    public String weapon;
    /** 보스 첫 처치 때 흡수하지 못한 힘이 던전에 스며들어 잡몹이 한 번 강해진다. */
    public float buffHpMultiplier = 1.3f;
    public float buffAttackMultiplier = 1.2f;
}
