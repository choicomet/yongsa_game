package com.yongsa.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 용사의 영구 능력치. 힘은 무기가 아니라 용사에게 쌓인다 (FR-대장간-20, FR-던전-12).
 * 위치·움직임은 combat.HeroUnit이 맡는다.
 */
public class Hero {

    private int maxHp;
    private float hp;
    /** 흡수한 각성 능력. 어떤 무기를 들어도 쓴다. */
    private final List<AwakenAbility> absorbed = new ArrayList<>();

    public Hero(int maxHp) {
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public int maxHp() { return maxHp; }
    public int hp() { return (int) Math.ceil(hp); }
    public float hpExact() { return hp; }
    public boolean alive() { return hp > 0; }

    public void damage(int amount) {
        hp = Math.max(0, hp - amount);
    }

    public void heal(float amount) {
        hp = Math.min(maxHp, hp + amount);
    }

    public void fullHeal() {
        hp = maxHp;
    }

    public void setHp(float hp) {
        this.hp = Math.max(0, Math.min(maxHp, hp));
    }

    void raiseMaxHp(int gain) {
        maxHp += gain;
        hp += gain;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public List<AwakenAbility> absorbed() {
        return Collections.unmodifiableList(absorbed);
    }

    void absorb(AwakenAbility a) {
        if (!absorbed.contains(a)) absorbed.add(a);
    }

    public void restoreAbsorbed(List<AwakenAbility> list) {
        absorbed.clear();
        absorbed.addAll(list);
    }

    /** 흡수한 각성 무기 수. 공격력 보너스 = 수 × 10%. */
    public int absorbedCount() {
        return absorbed.size();
    }
}
