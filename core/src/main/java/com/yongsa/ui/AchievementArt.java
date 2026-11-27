package com.yongsa.ui;

import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.yongsa.gfx.Art;
import com.yongsa.model.Achievement;
import java.util.Locale;

/** Small fixed ornaments and stretchable skins used by the achievement and record screens. */
public final class AchievementArt {
    private AchievementArt() {}

    public static TextureRegion icon(Art art, Achievement a) {
        return art.frame("ui/achievement/icon_" + a.name().toLowerCase(Locale.ROOT) + ".png", 1, 0);
    }

    static void install(Skin skin, Art art) {
        patch(skin, art, "achievement/card_on", 24, 24, 24, 14, 12);
        patch(skin, art, "achievement/card_off", 24, 24, 24, 14, 12);
        patch(skin, art, "achievement/ribbon", 16, 20, 0, 12, 5);
        patch(skin, art, "achievement/effect_on", 10, 10, 10, 8, 5);
        patch(skin, art, "achievement/effect_off", 10, 10, 10, 8, 5);
        patch(skin, art, "record/paper", 48, 48, 48, 0, 0);
        patch(skin, art, "record/career_card", 24, 24, 24, 14, 10);
        patch(skin, art, "record/grade_badge", 12, 12, 12, 8, 1);
        patch(skin, art, "record/skill_chip", 12, 12, 12, 9, 2);
        patch(skin, art, "record/skill_chip_gold", 12, 12, 12, 9, 2);
        patch(skin, art, "record/quote", 20, 20, 20, 14, 10);
        for (String name : new String[]{"achievement/ring_on", "achievement/ring_off",
                "record/photo_frame", "record/bar_back", "record/bar_fill", "record/row_line"}) {
            var image = new TextureRegionDrawable(art.frame("ui/" + name + ".png", 1, 0));
            image.setMinSize(0, 0);
            skin.add(name, image, Drawable.class);
        }
    }

    private static void patch(Skin skin, Art art, String name, int left, int right, int vertical,
                              int padX, int padY) {
        var texture = art.tex("ui/" + name + ".png");
        var drawable = new NinePatchDrawable(new NinePatch(texture, left, right, vertical, vertical));
        drawable.setMinSize(0, 0);
        drawable.setLeftWidth(padX);
        drawable.setRightWidth(padX);
        drawable.setTopHeight(padY);
        drawable.setBottomHeight(padY);
        skin.add(name, drawable, Drawable.class);
    }
}
