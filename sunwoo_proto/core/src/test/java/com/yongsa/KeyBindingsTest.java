package com.yongsa;

import com.badlogic.gdx.Input;
import com.yongsa.KeyBindings.Action;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 사용자가 바꾸는 조작 키 (2026-10-27). */
class KeyBindingsTest {

    @Test
    void 기본_키는_예전_조작과_같다() {
        KeyBindings k = new KeyBindings();
        assertEquals(Action.ATTACK, k.owner(Input.Keys.J));
        assertEquals(Action.WHIRL, k.owner(Input.Keys.Q));
        assertEquals(Action.DASH, k.owner(Input.Keys.L));
        assertEquals(Action.DODGE, k.owner(Input.Keys.SPACE));
        assertEquals(Action.GUARD, k.owner(Input.Keys.CONTROL_LEFT));
        assertEquals(Action.GUARD, k.owner(Input.Keys.K));
        assertEquals(Action.INTERACT, k.owner(Input.Keys.E));
        assertEquals(Action.BAG, k.owner(Input.Keys.I));
        assertEquals(Action.BAG, k.owner(Input.Keys.TAB));
        assertEquals(Action.MAP, k.owner(Input.Keys.M));
        assertEquals(Action.MOVE_LEFT, k.owner(Input.Keys.LEFT));
        Map<Integer, Action> seen = new HashMap<>();
        for (Action a : Action.values())
            for (int i = 0; i < KeyBindings.SLOTS; i++) {
                int key = k.key(a, i);
                if (key != KeyBindings.NONE) assertNull(seen.put(key, a), "키가 두 동작에");
            }
    }

    @Test
    void 다른_동작의_키를_고르면_그쪽에서_빠진다() {
        KeyBindings k = new KeyBindings();
        assertEquals(Action.DODGE, k.bind(Action.ATTACK, 1, Input.Keys.SPACE));
        assertTrue(k.is(Action.ATTACK, Input.Keys.SPACE));
        assertTrue(k.unbound(Action.DODGE), "회피는 이제 키가 없다");
        k.reset(Action.DODGE);
        assertEquals(Action.DODGE, k.owner(Input.Keys.SPACE), "기본으로 되돌리면 다시 가져온다");
        assertFalse(k.is(Action.ATTACK, Input.Keys.SPACE));
    }

    @Test
    void 바꿀_수_없는_키와_잘못된_저장값() {
        KeyBindings k = new KeyBindings();
        assertThrows(IllegalArgumentException.class, () -> k.bind(Action.ATTACK, 0, Input.Keys.ESCAPE));
        assertThrows(IllegalArgumentException.class, () -> k.bind(Action.ATTACK, 0, Input.Keys.F1));
        Map<String, int[]> bad = new HashMap<>();
        bad.put("ATTACK", new int[]{Input.Keys.ESCAPE, Input.Keys.F});
        bad.put("WHIRL", new int[]{Input.Keys.F});
        bad.put("NOPE", new int[]{Input.Keys.Z});
        KeyBindings f = KeyBindings.fromSaved(bad);
        assertEquals(Input.Keys.F, f.key(Action.ATTACK, 0));
        assertFalse(f.is(Action.WHIRL, Input.Keys.F));
        assertEquals(Action.DODGE, f.owner(Input.Keys.SPACE), "빠진 동작은 기본 키");
        KeyBindings back = KeyBindings.fromSaved(f.toSaved());
        assertEquals(Input.Keys.F, back.key(Action.ATTACK, 0));
    }

    @Test
    void 안내용_키_이름() {
        KeyBindings k = new KeyBindings();
        assertEquals("왼쪽 Ctrl / 오른쪽 Ctrl / K", k.names(Action.GUARD));
        assertEquals("L-Ctrl", k.primaryShort(Action.GUARD));
        assertEquals("Space", k.primaryShort(Action.DODGE));
        assertEquals("E", k.primaryName(Action.INTERACT));
    }

    @Test
    void 상호작용_안내는_바꾼_키로() {
        var s = TestFiles.session(3);
        s.interactKey = "G";
        s.unit.teleport(s.map.forge.x + s.map.forge.width / 2, s.map.forge.y - 10);
        s.update(1 / 60f, new com.yongsa.model.Controls());
        assertTrue(s.interactHint().startsWith("G  "), s.interactHint());
    }
}
