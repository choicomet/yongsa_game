package com.yongsa.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.yongsa.gfx.Art;
import com.yongsa.model.Achievement;
import com.yongsa.model.OreType;
import com.yongsa.model.Rules;

/** 업적마다 쓰는 그림: 게임 안의 검·광석·포션 그림을 그대로 쓴다. */
public final class AchievementArt {
    private static Texture potion;

    private AchievementArt() {
    }

    public static TextureRegion icon(Art art, Achievement a) {
        switch (a) {
            case HAMMER: return art.sword("blackiron", false);
            case COLLECTOR: return art.sword("bloodmoon", false);
            case MINER: return art.gem(OreType.BLACKIRON);
            case FLAWLESS: return art.sword(Rules.HOLY_AWAKENED, true);
            case UNYIELDING: return potion(art);
            default: return art.sword("skyfall", true);
        }
    }

    private static TextureRegion potion(Art art) {
        if (potion == null && Gdx.files.internal("sprites/potion.png").exists()) {
            potion = new Texture(Gdx.files.internal("sprites/potion.png"), true);
            potion.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
        }
        return potion != null ? new TextureRegion(potion) : art.white();
    }
}
