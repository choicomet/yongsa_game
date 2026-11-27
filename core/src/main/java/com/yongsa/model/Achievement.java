package com.yongsa.model;

/**
 * 업적 여섯 가지. 조건은 모두 한 판(새 게임 하나) 안에서 센다.
 * 한 번 달성하면 게임을 새로 시작해도 남고, 달성한 업적의 효과는 모두 함께 적용된다.
 * 조건은 업적 창에 달성한 뒤에만 보이고, 그 전에는 힌트와 효과만 보인다.
 */
public enum Achievement {
    HAMMER("망치를 놓지 않는 자", "한 판에서 강화를 50번 시도했다",
            "성공할 때까지 두드리면\n실패한 게 아니다.", "강화 성공률 +2%"),
    COLLECTOR("검 수집가", "한 판에서 검을 15자루 만들었다",
            "대장간 벽에 걸 자리가\n모자라기 시작했다.", "검 만들 때 광물 -3개"),
    MINER("갱도의 주인", "한 판에서 광물을 200개 캤다",
            "갱도가 텅 빌 만큼\n곡괭이를 휘둘러야 한다.", "광맥을 캘 때 광물 +5개"),
    FLAWLESS("쓰러진 적 없는 자", "한 번도 쓰러지지 않고 마왕을 쓰러뜨렸다",
            "한 번도 무릎 꿇지 않은\n용사에게만 열린다고 한다.", "최대 체력 +5"),
    UNYIELDING("불굴의 용사", "10번 이상 쓰러지고도 마왕을 쓰러뜨렸다",
            "쓰러져도, 또 쓰러져도\n다시 일어난 자의 이름.", "일반 몬스터에게 공격력 +10%"),
    SWIFT("번개 같은 용사", "30분 안에 마왕을 쓰러뜨렸다",
            "모닥불이 다 타기 전에\n모든 걸 끝낸 자.", "구르기 대기 시간 -5%");

    public static final int ENHANCE_TRIES = 50;
    public static final int CRAFTED = 15;
    public static final int ORE_MINED = 200;
    public static final int MANY_DEATHS = 10;
    public static final float SWIFT_SECONDS = 30 * 60;

    public final String title;
    /** 달성한 뒤 카드에 보이는 조건. */
    public final String done;
    /** 달성 전 카드에 보이는 힌트. */
    public final String hint;
    public final String effect;

    Achievement(String title, String done, String hint, String effect) {
        this.title = title;
        this.done = done;
        this.hint = hint;
        this.effect = effect;
    }

    /** 한 판 도중에 채워지는 업적인가 (강화·제작·채굴). */
    public boolean duringRun(Stats s) {
        switch (this) {
            case HAMMER: return s.enhanceTries >= ENHANCE_TRIES;
            case COLLECTOR: return s.crafted >= CRAFTED;
            case MINER: return s.oreMined >= ORE_MINED;
            default: return false;
        }
    }

    /** 마왕을 쓰러뜨린 순간에 판정하는 업적인가. */
    public boolean atClear(Stats s) {
        switch (this) {
            case FLAWLESS: return s.deaths == 0;
            case UNYIELDING: return s.deaths >= MANY_DEATHS;
            case SWIFT: return s.playSeconds <= SWIFT_SECONDS;
            default: return false;
        }
    }
}
