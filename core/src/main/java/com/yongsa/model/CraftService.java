package com.yongsa.model;

import com.yongsa.config.Balance;

/** 제작·장착 규칙. UI는 이 결과를 표시만 한다. 만든 장비는 없앨 수 없다 (분해 없음). */
public final class CraftService {
    private CraftService() {}

    public enum Block {
        NONE(""),
        LOCKED("아직 해금되지 않았습니다"),
        OWNED("보유 중"),
        NOT_ENOUGH("광물이 부족합니다"),
        NOT_OWNED("보유하지 않은 무기입니다"),
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
        /** 사용한 광물 수 (무기마다 정해진 색의 광물). */
        public int minerals;
        public boolean autoEquipped;
        public int level;
    }

    public static Block craftBlock(Balance b, GameState s, String type) {
        if (!Progress.isWeaponUnlocked(b, s, type)) return Block.LOCKED;
        if (s.owns(type)) return Block.OWNED;
        if (s.ore(b.oreIndex(type)) < b.weapon(type).craftCost) return Block.NOT_ENOUGH;
        return Block.NONE;
    }

    public static Result craft(Balance b, GameState s, String type) {
        Result r = new Result();
        r.weaponType = type;
        r.block = craftBlock(b, s, type);
        if (r.block != Block.NONE) return r;
        int cost = b.weapon(type).craftCost;
        s.addOre(b.oreIndex(type), -cost);
        s.weapons.add(new WeaponState(type));
        if (s.equippedWeapon() == null) {
            s.equipped = type;
            r.autoEquipped = true;
        }
        r.executed = true;
        r.minerals = cost;
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
