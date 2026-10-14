package com.yongsa.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;

/**
 * 나눔스퀘어라운드 (SIL OFL, 명세서 4.3). 한글 글자가 많아 incremental 모드로
 * 화면에 처음 나오는 글자만 그때그때 만든다. 가장 작은 글자는 16px (NFR-10).
 */
public class Fonts implements Disposable {

    private final FreeTypeFontGenerator regular, bold;
    public final BitmapFont small, body, large, heading, title, number;

    public Fonts() {
        regular = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NanumSquareRoundR.ttf"));
        bold = new FreeTypeFontGenerator(Gdx.files.internal("fonts/NanumSquareRoundB.ttf"));
        small = make(regular, 16, 0);
        body = make(regular, 19, 0);
        large = make(bold, 22, 0);
        heading = make(bold, 28, 0);
        title = make(bold, 56, 0);
        number = make(bold, 20, 2);
    }

    private static BitmapFont make(FreeTypeFontGenerator g, int size, int border) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.incremental = true;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        if (border > 0) {
            p.borderWidth = border;
            p.borderColor = new com.badlogic.gdx.graphics.Color(0.05f, 0.04f, 0.08f, 1f);
        }
        BitmapFont f = g.generateFont(p);
        f.setUseIntegerPositions(true);
        return f;
    }

    /** 폰트는 UiSkin(Skin)이 정리한다. Skin을 먼저 정리한 뒤 생성기를 정리할 것. */
    @Override
    public void dispose() {
        regular.dispose();
        bold.dispose();
    }
}
