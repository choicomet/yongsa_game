package com.yongsa.save;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.yongsa.model.AwakenAbility;
import com.yongsa.model.GameSession;
import com.yongsa.model.Weapon;
import com.yongsa.world.Room;
import com.yongsa.world.RoomKind;
import com.yongsa.world.RoomState;
import com.yongsa.world.Vein;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** GameSession ↔ SaveData ↔ JSON. 파일 입출력은 SaveManager가 한다. */
public final class SaveMapper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private SaveMapper() {
    }

    public static SaveData capture(GameSession s) {
        SaveData d = new SaveData();
        d.ores.putAll(s.inventory.oresView());
        for (Weapon w : s.inventory.weapons()) {
            SaveData.WeaponSave ws = new SaveData.WeaponSave();
            ws.uid = w.uid();
            ws.def = w.def().id;
            ws.level = w.level();
            ws.awakened = w.awakened();
            ws.enhanceSpent = w.enhanceSpent();
            d.weapons.add(ws);
        }
        d.equippedUid = s.inventory.equipped().uid();
        for (AwakenAbility a : s.hero.absorbed()) d.absorbed.add(a.name());
        d.absorbedSwords.putAll(s.hero.absorbedSwords());
        d.maxHp = s.hero.maxHp();
        d.hp = s.hero.hpExact();

        d.defeatedBosses.addAll(s.progress.defeatedBosses());
        d.orbs.addAll(s.progress.orbs());
        for (GameSession.OrbDrop o : s.orbDrops) {
            SaveData.OrbSave os = new SaveData.OrbSave();
            os.dungeon = o.dungeon();
            os.x = o.x();
            os.y = o.y();
            d.orbDrops.add(os);
        }
        d.sealBroken = s.progress.sealBroken();
        d.finalDefeated = s.progress.finalDefeated();
        d.firstOreRoomUsed = s.progress.firstOreRoomUsed();
        d.tutorialDone = s.progress.tutorialDone();
        d.enhanceCount = s.progress.enhanceCount;
        d.destroyCount = s.progress.destroyCount;
        d.awakenCount = s.progress.awakenCount;
        d.kills = s.progress.kills;
        d.deaths = s.progress.deaths;

        for (Room r : s.rooms) {
            SaveData.RoomSave rs = new SaveData.RoomSave();
            rs.id = r.id;
            // 문이 잠긴 전투 중이면 그 방은 처음 상태로 저장한다 (FR-저장-03)
            rs.cleared = r.state() == RoomState.CLEARED;
            rs.seen = r.seen && rs.cleared;
            rs.veinsGenerated = r.veinsGenerated;
            for (Vein v : r.veins) {
                SaveData.VeinSave vs = new SaveData.VeinSave();
                vs.x = v.x;
                vs.y = v.y;
                vs.hitsLeft = v.hitsLeft();
                vs.amount = v.amount();
                rs.veins.add(vs);
            }
            d.rooms.add(rs);
        }
        d.heroX = s.unit.footX();
        d.heroY = s.unit.footY();
        d.inCamp = s.inCamp();
        d.lastRoomId = s.lastRoomId();
        d.playTime = s.playTime();
        return d;
    }

    /** 새로 만든 GameSession에 저장 내용을 덮어쓴다. */
    public static void restore(SaveData d, GameSession s) {
        s.inventory.clear();
        for (Map.Entry<String, Integer> e : d.ores.entrySet()) s.inventory.addOre(e.getKey(), e.getValue());
        for (SaveData.WeaponSave ws : d.weapons) {
            s.inventory.restore(ws.uid, s.balance.weapon(ws.def), ws.level, ws.awakened, ws.enhanceSpent);
        }
        if (s.inventory.sword() == null) {
            s.inventory.add(d.sealBroken ? s.balance.holySword() : s.balance.sealedSword());
        }
        Weapon eq = s.inventory.find(d.equippedUid);
        s.inventory.equip(eq != null ? eq : s.inventory.sword());

        List<AwakenAbility> abs = new ArrayList<>();
        for (String a : d.absorbed) abs.add(AwakenAbility.parse(a));
        s.hero.restoreAbsorbed(abs, d.absorbedSwords);
        s.hero.setMaxHp(Math.max(d.maxHp, s.balance.hero.maxHp));
        s.hero.setHp(d.hp <= 0 ? s.hero.maxHp() : d.hp);

        s.progress.restore(d.defeatedBosses, d.orbs, d.sealBroken, d.finalDefeated, d.firstOreRoomUsed, d.tutorialDone);
        s.progress.enhanceCount = d.enhanceCount;
        s.progress.destroyCount = d.destroyCount;
        s.progress.awakenCount = d.awakenCount;
        s.progress.kills = d.kills;
        s.progress.deaths = d.deaths;

        for (SaveData.RoomSave rs : d.rooms) {
            Room r = s.roomById.get(rs.id);
            if (r == null) continue;
            r.monsters.clear();
            r.setState(rs.cleared ? RoomState.CLEARED : RoomState.IDLE);
            r.seen = rs.seen;
            r.veinsGenerated = rs.veinsGenerated;
            r.veins.clear();
            String ore = s.balance.dungeon(r.dungeon).ore;
            for (SaveData.VeinSave vs : rs.veins) {
                Vein v = new Vein(ore, vs.x, vs.y, s.balance.mining.hitsPerVein, vs.amount);
                v.setHitsLeft(vs.hitsLeft);
                r.veins.add(v);
            }
            // 보스방 포탈은 그 방을 비운 동안만 (침대로 쉬면 보스가 다시 생긴다)
            if (r.portal != null) r.portal.setActive(r.kind == RoomKind.BOSS && rs.cleared);
        }

        s.orbDrops.clear();
        for (SaveData.OrbSave os : d.orbDrops) s.orbDrops.add(new GameSession.OrbDrop(os.dungeon, os.x, os.y));
        // 보스는 잡았는데 구슬을 못 주운 채 저장했다면 보스방에 다시 떨어뜨린다
        for (String b : d.defeatedBosses) {
            boolean dropped = s.orbDrops.stream().anyMatch(o -> o.dungeon().equals(b));
            if (!s.progress.hasOrb(b) && !dropped) {
                Room boss = s.bossRoom(b);
                s.orbDrops.add(new GameSession.OrbDrop(b, boss.portal.x, boss.portal.y + 56));
            }
        }
        s.setLastRoomId(d.lastRoomId);
        s.setPlayTime(d.playTime);
        s.placeAfterLoad(d.heroX, d.heroY, d.inCamp);
    }

    public static String toJson(SaveData d) {
        return GSON.toJson(d);
    }

    /** 깨진 파일이면 예외. */
    public static SaveData fromJson(String json) {
        SaveData d = GSON.fromJson(json, SaveData.class);
        if (d == null || d.weapons == null || d.rooms == null) throw new IllegalStateException("저장 내용이 비어 있음");
        return d;
    }
}
