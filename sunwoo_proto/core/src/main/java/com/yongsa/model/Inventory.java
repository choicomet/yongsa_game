package com.yongsa.model;

import com.yongsa.config.WeaponDef;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 광물·무기 보관과 장착 (FR-대장간-04). */
public class Inventory {

    private final Map<String, Integer> ores = new LinkedHashMap<>();
    private final List<Weapon> weapons = new ArrayList<>();
    private Weapon equipped;
    private int nextUid = 1;

    // ---------------------------------------------------------------- 광물

    public int ore(String id) {
        return ores.getOrDefault(id, 0);
    }

    public void addOre(String id, int n) {
        if (n < 0) throw new IllegalArgumentException("음수 추가 불가");
        if (n > 0) ores.merge(id, n, Integer::sum);
    }

    public boolean hasOre(String id, int n) {
        return ore(id) >= n;
    }

    /** 모자라면 아무것도 빼지 않고 false. */
    public boolean spendOre(String id, int n) {
        if (!hasOre(id, n)) return false;
        ores.put(id, ore(id) - n);
        return true;
    }

    public Map<String, Integer> oresView() {
        return Collections.unmodifiableMap(ores);
    }

    // ---------------------------------------------------------------- 무기

    public Weapon add(WeaponDef def) {
        Weapon w = new Weapon(nextUid++, def);
        weapons.add(w);
        if (equipped == null) equipped = w;
        return w;
    }

    /** 저장에서 복원. */
    public Weapon restore(int uid, WeaponDef def, int level, boolean awakened, int enhanceSpent) {
        Weapon w = new Weapon(uid, def, level, awakened, enhanceSpent);
        weapons.add(w);
        nextUid = Math.max(nextUid, uid + 1);
        return w;
    }

    /** 무기를 없앤다. 장착 중이었다면 성검으로 바꿔 든다. */
    void remove(Weapon w) {
        weapons.remove(w);
        if (equipped == w) equipped = sword();
    }

    public List<Weapon> weapons() {
        return Collections.unmodifiableList(weapons);
    }

    public Weapon find(int uid) {
        for (Weapon w : weapons) if (w.uid() == uid) return w;
        return null;
    }

    public Weapon equipped() {
        return equipped;
    }

    public void equip(Weapon w) {
        if (!weapons.contains(w)) throw new IllegalArgumentException("가지고 있지 않은 무기");
        equipped = w;
    }

    /** 봉인된 성검 또는 깨어난 성검. 항상 하나 있다. */
    public Weapon sword() {
        for (Weapon w : weapons) if (w.def().starter || w.def().holy) return w;
        return null;
    }

    public boolean owns(WeaponDef def) {
        for (Weapon w : weapons) if (w.def() == def) return true;
        return false;
    }

    /** 저장을 불러오기 전에 비운다. */
    public void clear() {
        ores.clear();
        weapons.clear();
        equipped = null;
        nextUid = 1;
    }

    public int nextUid() {
        return nextUid;
    }
}
