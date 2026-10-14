package com.yongsa.model;

import com.yongsa.config.Balance;

/**
 * 힘 흡수를 한곳에서 처리한다. 힘은 무기가 아니라 용사에게 쌓인다 (FR-대장간-20, FR-던전-12).
 * 흡수 장소·시점(명세서 7.1)이 정해지면 여기만 바꾼다. 지금은 ‘바로 흡수’:
 * 보스는 잡는 순간 흡수한다. 각성 무기 흡수는 2026-10-14 팀 결정으로 없앴다.
 */
public class AbsorbService {
    public static final String[] BOSS_ORDER = {"LEFT", "RIGHT", "TOP"};

    private final Balance b;
    private final Rules rules;

    public AbsorbService(Balance b) {
        this.b = b;
        this.rules = new Rules(b);
    }

    /** 보스의 힘을 흡수한다. 오른 최대 체력을 돌려준다. 체력은 가득 찬다. */
    public int absorbBoss(GameState s, String dungeonId) {
        int before = rules.maxHp(s);
        if (!s.bossesDefeated.contains(dungeonId)) s.bossesDefeated.add(dungeonId);
        int after = rules.maxHp(s);
        s.hp = after;
        return after - before;
    }

    /** 세 보스의 힘을 모두 흡수했고 아직 봉인을 풀지 않았으면 풀 수 있다. */
    public boolean canUnseal(GameState s) {
        if (s.unsealed) return false;
        for (String id : BOSS_ORDER) if (!s.bossDefeated(id)) return false;
        return true;
    }

    /** 강해진 용사가 성검의 봉인을 푼다. 성검이 깨어나고 바로 장착된다. */
    public Weapon unseal(GameState s) {
        if (!canUnseal(s)) throw new IllegalStateException("아직 봉인을 풀 수 없습니다");
        Weapon sword = s.weaponByDef(Rules.HOLY_SEALED);
        if (sword == null) sword = s.newWeapon(Rules.HOLY_SEALED);
        sword.defId = Rules.HOLY_AWAKENED;
        s.unsealed = true;
        s.equippedUid = sword.uid;
        return sword;
    }
}
