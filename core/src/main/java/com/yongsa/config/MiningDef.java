package com.yongsa.config;

public class MiningDef {
    public int hitsPerVein = 3;
    public int oreMin = 1;
    public int oreMax = 2;
    public int veinsMin = 6;
    public int veinsMax = 10;
    /** 보스를 잡은 던전의 광물방 (FR-채집-07). */
    public int clearedVeinsMin = 8;
    public int clearedVeinsMax = 12;
    /** 처음 들어가는 광물방은 합계 이만큼 보장 (FR-채집-09). */
    public int firstRoomGuarantee = 8;
}
