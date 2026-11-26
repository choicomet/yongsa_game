package com.yongsa.map;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.Disposable;

import java.util.ArrayList;
import java.util.List;

/** 미리 계산해 둔 그림 한 묶음: 큰 그림 한 장(없을 수 있음)과 잘라 낸 조각들, 조각마다의 위치 값. */
final class Baked implements Disposable {
    Pixmap main;
    final List<Pixmap> parts = new ArrayList<>();
    final List<float[]> partInfo = new ArrayList<>();

    @Override
    public void dispose() {
        if (main != null) main.dispose();
        main = null;
        for (Pixmap p : parts) p.dispose();
        parts.clear();
    }
}
