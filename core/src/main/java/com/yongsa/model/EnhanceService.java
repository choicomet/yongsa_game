package com.yongsa.model;

import com.yongsa.config.Balance;

/**
 * 강화 규칙.
 * 처리 순서: 조건 확인 → 광물 차감 → 성공 보장 여부 또는 확률 판정 → 단계와 실패 누적 갱신.
 * 저장 시도와 결과창 표시는 호출한 쪽이 이 결과를 받은 뒤 수행한다.
 */
public final class EnhanceService {
    private EnhanceService() {}

    public enum Block {
        NONE("강화"),
        NO_WEAPON("무기가 없습니다"),
        MAX_LEVEL("최대 단계입니다"),
        CAP("현재 진행 상한입니다"),
        NOT_ENOUGH("광물이 부족합니다");

        public final String label;

        Block(String label) {
            this.label = label;
        }
    }

    /** 강화창에 보여줄 정보. 실제 상태는 바꾸지 않는다. */
    public static class Preview {
        public String weaponType;
        public int level;
        public int targetLevel;
        public int currentDamage;
        public int nextDamage;
        public int cost;
        public int minerals;
        /** 이번 시도의 성공 확률. 보장 시도면 1. */
        public float chance;
        /** 확정 성공까지 남은 시도 수 (이번 시도 포함). */
        public int triesToGuarantee;
        public boolean guaranteed;
        public int cap;
        public Block block = Block.NONE;
    }

    public static class Result {
        public boolean executed;
        public boolean success;
        public boolean guaranteed;
        public Block block = Block.NONE;
        public String weaponType;
        public int oldLevel;
        public int newLevel;
        public int cost;
        public float chance;
        public int failCount;
        public int oldDamage;
        public int newDamage;
        public int triesToGuarantee;
    }

    public static Preview preview(Balance b, GameState s, String weaponType) {
        Preview p = new Preview();
        p.weaponType = weaponType;
        p.minerals = weaponType == null ? 0 : s.ore(b.oreIndex(weaponType));
        p.cap = Progress.enhanceCap(b, s);
        WeaponState w = weaponType == null ? null : s.weapon(weaponType);
        if (w == null) {
            p.block = Block.NO_WEAPON;
            return p;
        }
        p.level = w.level;
        p.currentDamage = DamageCalc.weaponDamage(b, w);
        if (w.level >= b.maxLevel()) {
            p.block = Block.MAX_LEVEL;
            p.targetLevel = w.level;
            p.nextDamage = p.currentDamage;
            return p;
        }
        Balance.EnhanceStep step = b.enhanceStep(w.level + 1);
        p.targetLevel = step.target;
        p.nextDamage = DamageCalc.weaponDamage(b, w.type, step.target);
        p.cost = step.cost;
        p.guaranteed = w.failCount + 1 >= step.maxTries;
        p.chance = p.guaranteed ? 1f : step.chance;
        p.triesToGuarantee = Math.max(1, step.maxTries - w.failCount);
        if (w.level >= p.cap) p.block = Block.CAP;
        else if (p.minerals < step.cost) p.block = Block.NOT_ENOUGH;
        return p;
    }

    /** 한 번 호출에 한 번만 강화를 처리한다. 조건이 맞지 않으면 아무것도 바꾸지 않는다. */
    public static Result enhance(Balance b, GameState s, String weaponType, Rng.Source rng) {
        Preview p = preview(b, s, weaponType);
        Result r = new Result();
        r.weaponType = weaponType;
        r.block = p.block;
        r.oldLevel = p.level;
        r.newLevel = p.level;
        r.oldDamage = p.currentDamage;
        r.newDamage = p.currentDamage;
        if (p.block != Block.NONE) return r;

        WeaponState w = s.weapon(weaponType);
        Balance.EnhanceStep step = b.enhanceStep(w.level + 1);

        s.addOre(b.oreIndex(weaponType), -step.cost);
        r.executed = true;
        r.cost = step.cost;
        r.guaranteed = w.failCount + 1 >= step.maxTries;
        r.chance = r.guaranteed ? 1f : step.chance;

        boolean success;
        if (r.guaranteed) {
            success = true; // 확정 성공 시도는 난수를 소비하지 않는다
        } else {
            success = rng.next() < step.chance;
        }

        if (success) {
            w.level = step.target;
            w.failCount = 0;
        } else {
            w.failCount++;
        }
        s.firstEnhanceDone = true;

        r.success = success;
        r.newLevel = w.level;
        r.failCount = w.failCount;
        r.newDamage = DamageCalc.weaponDamage(b, w);
        if (!success) r.triesToGuarantee = Math.max(1, step.maxTries - w.failCount);
        return r;
    }
}
