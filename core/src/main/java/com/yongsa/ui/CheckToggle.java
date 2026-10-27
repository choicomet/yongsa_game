package com.yongsa.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Align;

/**
 * 체크박스 버튼: 누를 때마다 켜고 끈다. 왼쪽에 네모 칸을 직접 그린다 (글꼴에 기호가 없어도 되게).
 * TextButton이라서 키보드 이동(KeyNav)으로도 고르고 Enter·Space로 바꿀 수 있다.
 */
public class CheckToggle extends TextButton {

    private final TextureRegion white;

    public CheckToggle(String text, Skin skin) {
        super("      " + text, skin);
        this.white = skin.getRegion("white");
        setProgrammaticChangeEvents(false);
        getLabel().setAlignment(Align.left);
        getLabelCell().padLeft(10);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        float s = 18, x = getX() + 14, y = getY() + (getHeight() - s) / 2;
        Color old = batch.getColor().cpy();
        batch.setColor(0.9f, 0.82f, 0.55f, parentAlpha);
        batch.draw(white, x, y, s, s);
        batch.setColor(0.08f, 0.07f, 0.1f, parentAlpha);
        batch.draw(white, x + 2, y + 2, s - 4, s - 4);
        if (isChecked()) {
            // 체크 표시 (두 획)
            batch.setColor(1f, 0.85f, 0.35f, parentAlpha);
            batch.draw(white, x + 4, y + 7, 0, 0, 6, 3, 1, 1, -45);
            batch.draw(white, x + 7, y + 4, 0, 0, 11, 3, 1, 1, 45);
        }
        batch.setColor(old);
    }
}
