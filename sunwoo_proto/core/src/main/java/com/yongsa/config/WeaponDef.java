package com.yongsa.config;

/** 무기 종류. 실제로 가진 무기 한 자루는 model.Weapon. */
public class WeaponDef {
    public String id;
    public String name;
    public String icon;
    /** 제작·강화에 쓰는 광물 id. 성검은 빈 문자열. */
    public String ore = "";
    public int craftCost;
    public int attack;
    public int attackPerLevel;
    public boolean enhanceable;
    /** 봉인된 성검: 시작 무기, 파괴·분해되지 않음, 무기가 파괴되면 이 검으로 바뀐다. */
    public boolean starter;
    /** 깨어난 성검: 최종 보스에게 피해를 줄 수 있는 유일한 검. */
    public boolean holy;
    /** 각성(+10) 뒤 이름·그림·능력. */
    public String awakenedName;
    public String awakenedIcon;
    public String ability;

    public boolean isSword() {
        return !starter && !holy;
    }
}
