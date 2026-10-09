package com.yongsa.gfx;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Disposable;

/**
 * 한글 픽셀 폰트(Galmuri11, OFL). 글자는 필요할 때 생성(incremental)하므로 모든 한글을 쓸 수 있다.
 * 논리 화면(960x540)에서 선명하도록 2배 크기로 만든 뒤 0.5배로 그린다.
 */
public class Fonts implements Disposable {
    private final FreeTypeFontGenerator generator;
    public final BitmapFont small;
    public final BitmapFont body;
    public final BitmapFont large;
    public final BitmapFont title;
    /** 월드 위에 띄우는 글자(피해 숫자 등)용 외곽선 폰트. */
    public final BitmapFont outlined;

    public Fonts(FileHandle ttf) {
        generator = new FreeTypeFontGenerator(ttf);
        small = make(22, 0);
        body = make(26, 0);
        large = make(40, 0);
        title = make(72, 0);
        outlined = make(26, 3);
    }

    private BitmapFont make(int size, int border) {
        FreeTypeFontGenerator.FreeTypeFontParameter p = new FreeTypeFontGenerator.FreeTypeFontParameter();
        p.size = size;
        p.incremental = true;
        p.minFilter = Texture.TextureFilter.Linear;
        p.magFilter = Texture.TextureFilter.Linear;
        p.packer = new PixmapPacker(1024, 1024, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888, 2, false);
        if (border > 0) {
            p.borderWidth = border;
            p.borderColor = new Color(0.05f, 0.04f, 0.06f, 1f);
        }
        p.characters = FreeTypeFontGenerator.DEFAULT_CHARS;
        BitmapFont f = generator.generateFont(p);
        f.getData().setScale(0.5f);
        f.setUseIntegerPositions(false);
        f.getData().markupEnabled = false;
        return f;
    }

    @Override
    public void dispose() {
        small.dispose();
        body.dispose();
        large.dispose();
        title.dispose();
        outlined.dispose();
        generator.dispose();
    }
}
