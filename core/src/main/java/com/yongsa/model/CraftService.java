package com.yongsa.model;

import com.yongsa.config.Balance;

/** 제작·분해·장착 규칙. UI는 이 결과를 표시만 한다. */
public final class CraftService {
    private CraftService() {}

    public enum Block {
        NONE(""),
        LOCKED("아직 해금되지 않았습니다"),
        OWNED("보유 중"),
        NOT_ENOUGH("광물이 부족합니다"),
        NOT_OWNED("보유하지 않은 무기입니다"),
        EQUIPPED("장착 중인 무기는 분해할 수 없습니다"),
        LAST_WEAPON("마지막 남은 무기는 분해할 수 없습니다"),
        IN_COMBAT("전투 중에는 할 수 없습니다"),
        ALREADY_EQUIPPED("이미 장착 중입니다");

        public final String label;

        Block(String label) {
            this.label = label;
        }
    }

    public static class Result {
        public boolean executed;
        public Block block = Block.NONE;
        public String weaponType;
        public int minerals;
        public boolean autoEquipped;
        public int level;
    }

    public static Block craftBlock(Balance b, GameState s, String type) {
        if (!Progress.isWeaponUnlocked(b, s, type)) return Block.LOCKED;
        if (s.owns(type)) return Block.OWNED;
        if (s.minerals < b.weapon(type).craftCost) return Block.NOT_ENOUGH;
        return Block.NONE;
    }

    public static Result craft(Balance b, GameState s, String type) {
        Result r = new Result();
        r.weaponType = type;
        r.block = craftBlock(b, s, type);
        if (r.block != Block.NONE) return r;
        int cost = b.weapon(type).craftCost;
        s.minerals -= cost;
        s.weapons.add(new WeaponState(type));
        if (s.equippedWeapon() == null) {
            s.equipped = type;
            r.autoEquipped = true;
        }
        r.executed = true;
        r.minerals = cost;
        return r;
    }

    public static int refund(Balance b, String type) {
        return (int) Math.floor(b.weapon(type).craftCost * b.dismantleRefundRate);
    }

    public static Block dismantleBlock(GameState s, String type, boolean inCombat) {
        if (inCombat) return Block.IN_COMBAT;
        WeaponState w = s.weapon(type);
        if (w == null) return Block.NOT_OWNED;
        if (s.weapons.size() <= 1) return Block.LAST_WEAPON;
        if (type.equals(s.equipped)) return Block.EQUIPPED;
        return Block.NONE;
    }

    /** 분해: 제작 비용의 50%(내림)를 돌려준다. 강화 단계와 실패 누적은 사라진다. */
    public static Result dismantle(Balance b, GameState s, String type, boolean inCombat) {
        Result r = new Result();
        r.weaponType = type;
        r.block = dismantleBlock(s, type, inCombat);
        if (r.block != Block.NONE) return r;
        WeaponState w = s.weapon(type);
        r.level = w.level;
        s.weapons.remove(w);
        int refund = refund(b, type);
        s.minerals += refund;
        r.minerals = refund;
        r.executed = true;
        return r;
    }

    public static Block equipBlock(GameState s, String type, boolean inCombat) {
        if (inCombat) return Block.IN_COMBAT;
        if (!s.owns(type)) return Block.NOT_OWNED;
        if (type.equals(s.equipped)) return Block.ALREADY_EQUIPPED;
        return Block.NONE;
    }

    public static Result equip(GameState s, String type, boolean inCombat) {
        Result r = new Result();
        r.weaponType = type;
        r.block = equipBlock(s, type, inCombat);
        if (r.block != Block.NONE) return r;
        s.equipped = type;
        r.executed = true;
        return r;
    }
}
