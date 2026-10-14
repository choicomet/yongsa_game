package com.yongsa.model;

import com.yongsa.config.Balance;

/**
 * 용사에게 걸리는 효과 묶음. 축복 같은 효과는 "무엇을 얼마나 바꾸는가"를 이 수치로만 표현하고,
 * 전투 코드는 효과의 이름을 모른 채 합쳐진 수치만 읽는다 (효과별 분기를 전투 코드에 넣지 않는다).
 * 새 효과가 필요하면 여기에 항목을 하나 더하고, 그 수치를 쓰는 곳 한 군데만 고친다.
 */
public class Modifiers {
    public int maxHpAdd;
    public float damageMul = 1f;
    public float moveSpeedMul = 1f;
    public float dodgeCooldownMul = 1f;
    public float miningTimeMul = 1f;
    /** 공격이 맞을 때 거는 상태 이상 (balance.json 의 statuses id). 비어 있으면 없음. */
    public String onHitStatus = "";
    public float onHitStatusAmount;

    public void add(Modifiers o) {
        maxHpAdd += o.maxHpAdd;
        damageMul *= o.damageMul;
        moveSpeedMul *= o.moveSpeedMul;
        dodgeCooldownMul *= o.dodgeCooldownMul;
        miningTimeMul *= o.miningTimeMul;
        if (o.onHitStatus != null && !o.onHitStatus.isEmpty()) {
            onHitStatus = o.onHitStatus;
            onHitStatusAmount = Math.max(onHitStatusAmount, o.onHitStatusAmount);
        }
    }

    /** 지금 받은 축복을 모두 합친 수치. 모르는 id 는 건너뛴다 (데이터가 바뀐 저장과의 호환). */
    public static Modifiers of(Balance b, GameState s) {
        Modifiers m = new Modifiers();
        for (String id : s.blessings) {
            Balance.BlessingDef d = b.blessingOrNull(id);
            if (d != null && d.mods != null) m.add(d.mods);
        }
        return m;
    }
}
