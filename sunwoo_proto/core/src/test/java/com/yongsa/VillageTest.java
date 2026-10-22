package com.yongsa;

import com.yongsa.model.Controls;
import com.yongsa.model.GameEvent;
import com.yongsa.model.GameSession;
import com.yongsa.save.SaveData;
import com.yongsa.save.SaveMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 던전 출구와 마을 (2026-10-11 결정). */
class VillageTest {

    private static final float DT = 1f / 60f;

    private static List<GameEvent> speeches(GameSession s) {
        List<GameEvent> list = new ArrayList<>();
        s.addListener(e -> {
            if (e.type() == GameEvent.Type.SPEECH) list.add(e);
        });
        return list;
    }

    /** 출구 바로 위에 세우고 아래로 걸어간다. 대사가 나오거나 마을에 들어가면 멈춘다. */
    private static void walkDown(GameSession s, List<GameEvent> said) {
        s.unit.teleport(s.map.exit.x + s.map.exit.width / 2, s.map.exit.y + s.map.exit.height + 40);
        Controls c = new Controls();
        s.update(DT, c);
        int before = said.size();
        c.moveY = -1;
        for (int i = 0; i < 240 && said.size() == before && !s.inVillage(); i++) s.update(DT, c);
        c.moveY = 0;
        for (int i = 0; i < 30; i++) s.update(DT, c);
    }

    @Test
    void 마왕_전에는_다섯_번_되돌아서고_여섯_번째에_체념하고_마을로_나간다() {
        GameSession s = TestFiles.session(1);
        List<GameEvent> said = speeches(s);
        for (int n = 1; n <= GameSession.EXIT_REFUSALS; n++) {
            walkDown(s, said);
            assertFalse(s.inVillage(), n + "번째 시도에 나가면 안 된다");
            assertEquals(n, said.size());
            assertEquals("용사", said.get(n - 1).id());
            assertFalse(s.map.exit.contains(s.unit.footX(), s.unit.footY()), "되돌아서서 출구 밖에 있어야 한다");
        }
        assertTrue(said.get(0).text().contains("마왕을 물리치기 전까지는 나갈 수 없어"));

        walkDown(s, said);
        assertTrue(s.inVillage(), "여섯 번째에는 나간다");
        assertTrue(said.stream().anyMatch(e -> e.text().contains("복수심을 불태워")));
        assertFalse(s.villagersFree(), "마왕 전이라 마을 사람들은 수정 속");
        assertEquals("수정에 잠든 마을", s.areaName());

        // 계단으로 돌아갔다가 다시 나가면 이번엔 바로 나간다
        s.returnFromVillage();
        assertFalse(s.inVillage());
        int before = said.size();
        walkDown(s, said);
        assertTrue(s.inVillage());
        assertTrue(said.stream().skip(before).noneMatch(e -> e.text().contains("나갈 수 없어")));
    }

    @Test
    void 마왕을_쓰러뜨리면_바로_나가고_마을_사람들이_풀려나_말을_건다() {
        GameSession s = TestFiles.session(2);
        s.progress.markFinalDefeated();
        List<GameEvent> said = speeches(s);
        walkDown(s, said);
        assertTrue(s.inVillage());
        assertTrue(s.villagersFree());
        assertEquals("되찾은 마을", s.areaName());

        // 마을 사람 앞에서 E: 그 사람의 대사
        var v = s.map.villagers.get(0);
        s.unit.teleport(v.pos().x, v.pos().y - 24);
        Controls c = new Controls();
        s.update(DT, c);
        assertEquals(GameSession.Interact.TALK, s.focus());
        c.interact = true;
        s.update(DT, c);
        assertEquals(GameSession.villagerName(v.kind()), said.get(said.size() - 1).id());

        // 계단: E로 던전 출구 통로로
        s.unit.teleport(s.map.villageReturn.x, s.map.villageReturn.y - 20);
        c.interact = false;
        s.update(DT, c);
        assertEquals(GameSession.Interact.VILLAGE_RETURN, s.focus());
        c.interact = true;
        s.update(DT, c);
        assertFalse(s.inVillage());
        assertTrue(s.inSafeArea());
    }

    @Test
    void 마을에서_저장하면_마을에서_다시_시작하고_거절_횟수도_남는다() {
        GameSession a = TestFiles.session(3);
        List<GameEvent> said = speeches(a);
        walkDown(a, said);
        walkDown(a, said);
        assertEquals(2, a.progress.exitRefusals);
        a.warpTo(true);
        assertTrue(a.inVillage());
        SaveData d = SaveMapper.fromJson(SaveMapper.toJson(SaveMapper.capture(a)));
        GameSession b = TestFiles.session(4);
        SaveMapper.restore(d, b);
        assertTrue(b.inVillage());
        assertEquals(2, b.progress.exitRefusals);
    }

    @Test
    void 마을_장식과_마을_사람은_벽이나_서로와_겹치지_않는다() {
        GameSession s = TestFiles.session(5);
        assertEquals(6, s.map.villagers.size());
        for (var v : s.map.villagers) {
            assertTrue(s.map.village.contains(v.pos()));
            int tx = (int) (v.pos().x / s.map.tileSize), ty = (int) (v.pos().y / s.map.tileSize);
            assertFalse(s.map.solidTile(tx, ty));
            for (var d : s.map.decor) {
                if (d.solid()) assertFalse(d.footprint().contains(v.pos()), v.kind() + "가 " + d.sprite() + "에 묻힘");
            }
        }
        // 도착 지점과 돌아가는 계단 앞은 막혀 있지 않다
        s.unit.teleport(s.map.villageLand.x, s.map.villageLand.y);
        Controls c = new Controls();
        c.moveY = -1;
        float y0 = s.unit.footY();
        for (int i = 0; i < 30; i++) s.update(DT, c);
        assertTrue(s.unit.footY() < y0 - 20, "도착한 뒤 아래로 걸어갈 수 있어야 한다");
    }

    @Test
    void 개발자_도구_이동과_켜기_끄기() {
        GameSession s = TestFiles.session(6);
        assertTrue(s.warpTo(true));
        assertTrue(s.inVillage());
        assertTrue(s.warpTo(false));
        assertTrue(s.inCamp());
        assertTrue(s.warpToBossDoor(1));
        assertFalse(s.inCamp());
        assertTrue(s.toggleGodMode());
        assertFalse(s.toggleGodMode());
        assertTrue(s.toggleInfiniteOre());
        assertFalse(s.toggleInfiniteOre());
    }
}
