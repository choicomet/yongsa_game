package com.yongsa.ui;

import com.badlogic.gdx.InputAdapter;
import java.util.HashSet;
import java.util.Set;
import java.util.function.IntPredicate;

/** One edge per physical press. Menus never receive held-key auto repeat or mouse input. */
public final class KeyboardInput extends InputAdapter {
    private final IntPredicate onPress;
    private final Set<Integer> down = new HashSet<>();

    public KeyboardInput(IntPredicate onPress) { this.onPress = onPress; }
    @Override public boolean keyDown(int key) {
        if (!down.add(key)) return true;
        return onPress.test(key);
    }
    @Override public boolean keyUp(int key) { down.remove(key); return true; }
    public void clear() { down.clear(); }
    @Override public boolean touchDown(int x, int y, int pointer, int button) { return true; }
    @Override public boolean touchUp(int x, int y, int pointer, int button) { return true; }
}
