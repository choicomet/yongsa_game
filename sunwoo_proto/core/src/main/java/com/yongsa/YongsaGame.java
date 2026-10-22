package com.yongsa;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.yongsa.config.Balance;
import com.yongsa.model.GameSession;
import com.yongsa.render.Assets;
import com.yongsa.render.Fonts;
import com.yongsa.render.SoundBank;
import com.yongsa.save.SaveManager;
import com.yongsa.save.SaveMapper;
import com.yongsa.screen.EndingScreen;
import com.yongsa.screen.GameScreen;
import com.yongsa.screen.IntroScreen;
import com.yongsa.screen.StoryScreen;
import com.yongsa.screen.TitleScreen;
import com.yongsa.ui.UiSkin;
import com.yongsa.world.WorldMap;

import java.util.Random;

/** 게임 전체: 공용 자원을 들고 화면을 바꾼다. 타이틀 → (새 게임) 소개 → 게임 → 엔딩. */
public class YongsaGame extends Game {

    public Settings settings;
    public SoundBank sounds;
    public Balance balance;
    public WorldMap worldMap;
    public Assets assets;
    public Fonts fonts;
    public Skin skin;
    public SaveManager saves;

    @Override
    public void create() {
        settings = new Settings();
        settings.applyDisplay();
        sounds = new SoundBank(settings);
        balance = Balance.fromJson(Gdx.files.internal("data/balance.json").readString("UTF-8"));
        worldMap = WorldMap.parse(Gdx.files.internal("maps/world.tmx").readString("UTF-8"));
        assets = new Assets();
        assets.load();
        fonts = new Fonts();
        skin = UiSkin.create(fonts);
        saves = new SaveManager();
        switchTo(new TitleScreen(this));
    }

    public GameSession createSession() {
        return new GameSession(balance, worldMap, new Random());
    }

    public void showTitle() {
        switchTo(new TitleScreen(this));
    }

    public void startNewGame() {
        saves.delete();
        switchTo(IntroScreen.create(this));
    }

    public void startGame(GameSession s) {
        switchTo(new GameScreen(this, s));
    }

    /** 이어하기. 저장이 깨졌으면 타이틀에서 알린다 (FR-저장-05). */
    public void continueGame() {
        SaveManager.Loaded loaded = saves.load();
        if (loaded.broken() || loaded.data() == null) {
            if (getScreen() instanceof TitleScreen t) t.showBrokenSave();
            return;
        }
        GameSession s = createSession();
        try {
            SaveMapper.restore(loaded.data(), s);
        } catch (RuntimeException e) {
            Gdx.app.error("YongsaGame", "저장 복원 실패", e);
            if (getScreen() instanceof TitleScreen t) t.showBrokenSave();
            return;
        }
        startGame(s);
    }

    /** 마왕을 쓰러뜨림: 이야기 그림 3장(속박된 사람들의 해방) → 플레이 기록. */
    public void showEnding(GameSession s) {
        switchTo(new StoryScreen(this, EndingScreen.STORY, "bgm_ending", () -> switchTo(new EndingScreen(this, s))));
    }

    /** 엔딩 뒤 "계속하기": 같은 판으로 돌아간다 (FR-던전-10). */
    public void resumeAfterEnding(GameSession s) {
        startGame(s);
    }

    /** 버튼 처리 도중 그 버튼의 Stage를 정리하지 않도록 다음 프레임에 바꾼다. */
    public void switchTo(Screen next) {
        Gdx.app.postRunnable(() -> {
            Screen old = getScreen();
            setScreen(next);
            if (old != null) old.dispose();
        });
    }

    @Override
    public void dispose() {
        if (getScreen() != null) getScreen().dispose();
        sounds.dispose();
        assets.dispose();
        skin.dispose();
        fonts.dispose();
    }
}
