package com.yongsa.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 용사의 영구 능력치. 힘은 무기가 아니라 용사에게 쌓인다 (FR-대장간-20, FR-던전-12).
 * 위치·움직임은 combat.HeroUnit이 맡는다.
 */
public class Hero {

    private int maxHp;
    private float hp;
    /** 무적 (F2, 테스트·발표용): 어떤 피해도 받지 않는다. 저장하지 않는다. */
    public boolean invulnerable;
    /** 흡수한 각성 능력. 어떤 무기를 들어도 쓴다. */
    private final List<AwakenAbility> absorbed = new ArrayList<>();
    /** 흡수한 검의 종류 → 강화 단계. 검은 사라져도 성검이 그 힘을 나눠 갖는다. */
    private final Map<String, Integer> absorbedSwords = new LinkedHashMap<>();

    public Hero(int maxHp) {
        this.maxHp = maxHp;
        this.hp = maxHp;
    }

    public int maxHp() { return maxHp; }
    public int hp() { return (int) Math.ceil(hp); }
    public float hpExact() { return hp; }
    public boolean alive() { return hp > 0; }

    public void damage(int amount) {
        if (invulnerable) return;
        hp = Math.max(0, hp - amount);
    }

    /** 독·화상처럼 조금씩 닳는 피해. */
    public void damageExact(float amount) {
        if (invulnerable) return;
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

    void absorb(AwakenAbility a, String swordId, int level) {
        if (a != null && !absorbed.contains(a)) absorbed.add(a);
        if (swordId != null) absorbedSwords.merge(swordId, level, Math::max);
    }

    public Map<String, Integer> absorbedSwords() {
        return Collections.unmodifiableMap(absorbedSwords);
    }

    public void restoreAbsorbed(List<AwakenAbility> list, Map<String, Integer> swords) {
        absorbed.clear();
        absorbed.addAll(list);
        absorbedSwords.clear();
        if (swords != null) absorbedSwords.putAll(swords);
    }

    /** 흡수한 각성 무기 수. 공격력 보너스 = 수 × 10%. */
    public int absorbedCount() {
        return absorbed.size();
    }
}
