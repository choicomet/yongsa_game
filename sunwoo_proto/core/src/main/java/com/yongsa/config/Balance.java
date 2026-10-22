package com.yongsa.config;

import com.google.gson.Gson;
import com.yongsa.model.EnhanceTable;

/** balance.json 전체 (NFR-06). libGDX 그리기 코드에 의존하지 않는다. */
public class Balance {
    public HeroDef hero = new HeroDef();
    public GuardDef guard = new GuardDef();
    public SkillDefs skills = new SkillDefs();
    public StatusDef statuses = new StatusDef();
    public HolyDef holy = new HolyDef();
    public OreDef[] ores = new OreDef[0];
    public WeaponDef[] weapons = new WeaponDef[0];
    public AbilityDef abilities = new AbilityDef();
    public EnhanceDef enhance = new EnhanceDef();
    public MiningDef mining = new MiningDef();
    public MonsterDef[] monsters = new MonsterDef[0];
    public MonsterDef[] bosses = new MonsterDef[0];
    public MonsterDef finalBoss = new MonsterDef();
    public DungeonDef[] dungeons = new DungeonDef[0];
    /** 캠프 밝기: 보스(마왕 포함)를 처음 잡은 수 0,1,2,3,4일 때. */
    public float[] campLight = {0.3f, 0.48f, 0.66f, 0.84f, 1f};
    public float dungeonLight = 0.42f;
    public float arenaLight = 0.5f;

    private transient EnhanceTable enhanceTable;

    public static Balance fromJson(String json) {
        Balance b = new Gson().fromJson(json, Balance.class);
        b.validate();
        return b;
    }

    public WeaponDef weapon(String id) {
        for (WeaponDef w : weapons) if (w.id.equals(id)) return w;
        throw new IllegalArgumentException("없는 무기 id: " + id);
    }

    public WeaponDef sealedSword() {
        for (WeaponDef w : weapons) if (w.starter) return w;
        throw new IllegalStateException("봉인된 검(starter)이 없음");
    }

    public WeaponDef holySword() {
        for (WeaponDef w : weapons) if (w.holy) return w;
        throw new IllegalStateException("성검(holy)이 없음");
    }

    public OreDef ore(String id) {
        for (OreDef o : ores) if (o.id.equals(id)) return o;
        throw new IllegalArgumentException("없는 광물 id: " + id);
    }

    public MonsterDef monster(String id) {
        for (MonsterDef m : monsters) if (m.id.equals(id)) return m;
        throw new IllegalArgumentException("없는 몬스터 id: " + id);
    }

    public MonsterDef boss(String id) {
        for (MonsterDef m : bosses) if (m.id.equals(id)) return m;
        throw new IllegalArgumentException("없는 보스 id: " + id);
    }

    public DungeonDef dungeon(String id) {
        for (DungeonDef d : dungeons) if (d.id.equals(id)) return d;
        throw new IllegalArgumentException("없는 던전 id: " + id);
    }

    public EnhanceTable enhanceTable() {
        if (enhanceTable == null) enhanceTable = new EnhanceTable(enhance.rules);
        return enhanceTable;
    }

    /** 서로 가리키는 id가 실제로 있는지 시작할 때 확인한다. */
    public void validate() {
        sealedSword();
        holySword();
        for (WeaponDef w : weapons) if (!w.ore.isEmpty()) ore(w.ore);
        for (DungeonDef d : dungeons) {
            monster(d.monster);
            boss(d.boss);
            ore(d.ore);
            weapon(d.weapon);
            if (!d.requires.isEmpty()) dungeon(d.requires);
        }
        if (enhance.lifesteal.length != enhance.maxLevel + 1) {
            throw new IllegalStateException("흡혈 표는 0~" + enhance.maxLevel + "강까지 " + (enhance.maxLevel + 1) + "칸이어야 함");
        }
        if (campLight.length < dungeons.length + 2) {
            throw new IllegalStateException("campLight는 보스 수 + 마왕 + 1칸 이상이어야 함");
        }
        for (MonsterDef b : bosses) if (b.summonId != null) monster(b.summonId);
        if (enhance.rules.length != enhance.maxLevel) {
            throw new IllegalStateException("강화 표는 +0~+" + (enhance.maxLevel - 1) + "까지 " + enhance.maxLevel + "줄이어야 함");
        }
        enhanceTable();
    }
}
