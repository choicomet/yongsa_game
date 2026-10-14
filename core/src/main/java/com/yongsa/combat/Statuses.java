package com.yongsa.combat;

import com.yongsa.config.Balance;

import java.util.Iterator;

/**
 * 적에게 걸리는 상태 이상. 종류마다 코드를 따로 두지 않고 하나의 규칙으로 처리한다:
 * 맞을 때마다 게이지가 쌓이고(쌓이는 동안 느려짐), 가득 차면 holdTime 동안 발동한다.
 * 빙결·기절·저주의 차이는 balance.json 의 수치(StatusDef)뿐이다.
 */
public final class Statuses {
    private Statuses() {}

    /** 적 하나에 걸린 상태 하나. */
    public static class State {
        public final Balance.StatusDef def;
        /** 0~1. 가득 차면 발동한다. */
        public float gauge;
        /** 발동 후 남은 시간. */
        public float hold;

        State(Balance.StatusDef def) {
            this.def = def;
        }

        public boolean active() {
            return hold > 0;
        }
    }

    /** 게이지를 amount 만큼 쌓는다. 이번 타격으로 발동했으면 true. */
    public static boolean apply(Enemy e, Balance.StatusDef def, float amount) {
        if (def == null || amount <= 0 || !e.alive()) return false;
        if (e.isBoss() && !def.affectsBoss) return false;
        State s = find(e, def);
        if (s == null) {
            s = new State(def);
            e.statuses.add(s);
        }
        if (s.active()) return false; // 발동 중에는 다시 쌓이지 않는다
        s.gauge += amount;
        if (s.gauge < 1f) return false;
        s.gauge = 0;
        s.hold = def.holdTime;
        return true;
    }

    public static void update(Enemy e, float dt) {
        for (Iterator<State> it = e.statuses.iterator(); it.hasNext(); ) {
            State s = it.next();
            if (s.hold > 0) s.hold = Math.max(0, s.hold - dt);
            else s.gauge = Math.max(0, s.gauge - s.def.decayPerSec * dt);
            if (s.hold <= 0 && s.gauge <= 0) it.remove();
        }
    }

    /** 움직이거나 행동할 수 없는 상태인지. */
    public static boolean immobile(Enemy e) {
        for (State s : e.statuses) if (s.active() && s.def.immobile) return true;
        return false;
    }

    /** 이동 속도 배율: 게이지가 차오를수록 느려진다. */
    public static float speedMul(Enemy e) {
        float m = 1f;
        for (State s : e.statuses) if (!s.active()) m *= 1f - s.def.slowAtFull * Math.min(1f, s.gauge);
        return m;
    }

    public static float damageTakenMul(Enemy e) {
        float m = 1f;
        for (State s : e.statuses) if (s.active()) m *= s.def.damageTakenMul;
        return m;
    }

    /** 화면에 보여 줄 가장 강한 상태 (발동 중이 우선). 없으면 null. */
    public static State strongest(Enemy e) {
        State best = null;
        for (State s : e.statuses) {
            if (best == null || (s.active() && !best.active())
                    || (s.active() == best.active() && s.gauge > best.gauge)) best = s;
        }
        return best;
    }

    private static State find(Enemy e, Balance.StatusDef def) {
        for (State s : e.statuses) if (s.def == def) return s;
        return null;
    }
}
